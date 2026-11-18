package com.jolt.lint;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jolt.model.Problem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lints Maven pom.xml files by checking declared dependencies against the
 * OSV (Open Source Vulnerabilities) database for known CVEs.
 */
public class MavenLinter {

  private static final Logger log = LoggerFactory.getLogger(MavenLinter.class);
  private static final String OSV_API_URL = "https://api.osv.dev/v1/query";
  private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(15);

  private static final Duration CACHE_TTL = Duration.ofHours(1);

  private final OsvClient osvClient;

  public MavenLinter() {
    this(new CachingOsvClient(new HttpOsvClient()));
  }

  /** Package-private constructor for test injection. */
  MavenLinter(OsvClient osvClient) {
    this.osvClient = osvClient;
  }

  /** Check a pom.xml for dependencies with known CVEs. */
  public List<Problem> lint(Path filePath) {
    List<Problem> problems = new ArrayList<>();

    List<String> lines;
    try {
      lines = Files.readAllLines(filePath);
    } catch (IOException e) {
      log.debug("Cannot read {}: {}", filePath, e.getMessage());
      return problems;
    }

    Map<String, String> properties = extractProperties(lines);
    List<Dependency> deps = extractDependencies(lines, properties);

    for (Dependency dep : deps) {
      List<OsvVuln> vulns = osvClient.query(dep.groupId(), dep.artifactId(), dep.version());
      for (OsvVuln vuln : vulns) {
        problems.add(Problem.of(
          dep.line(), 1, Problem.Severity.WARNING,
          vuln.id() + ": " + vuln.summary(),
          vuln.id(), "osv"
        ));
      }
    }

    log.debug("Maven lint {} → {} CVE warning(s)", filePath, problems.size());
    return problems;
  }

  // ── pom.xml parsing ──────────────────────────────────────────────

  record Dependency(String groupId, String artifactId, String version, int line) {}

  private static final Pattern DEP_BLOCK = Pattern.compile(
    "<dependency>(.*?)</dependency>", Pattern.DOTALL);
  private static final Pattern GROUP_ID = Pattern.compile(
    "<groupId>([^<]+)</groupId>");
  private static final Pattern ARTIFACT_ID = Pattern.compile(
    "<artifactId>([^<]+)</artifactId>");
  private static final Pattern VERSION = Pattern.compile(
    "<version>([^<]+)</version>");

  private List<Dependency> extractDependencies(List<String> lines, Map<String, String> props) {
    List<Dependency> deps = new ArrayList<>();
    String fullText = String.join("\n", lines);

    Matcher blockMatcher = DEP_BLOCK.matcher(fullText);
    while (blockMatcher.find()) {
      String block = blockMatcher.group(1);

      String groupId = extractTag(block, GROUP_ID);
      String artifactId = extractTag(block, ARTIFACT_ID);
      String version = extractTag(block, VERSION);

      if (groupId == null || artifactId == null || version == null) {
        continue;
      }

      version = resolveProperty(version, props);

      int lineNum = findLineNumber(lines, blockMatcher.start());
      deps.add(new Dependency(groupId, artifactId, version, lineNum));
    }

    return deps;
  }

  private static final Pattern PROPERTY_ENTRY = Pattern.compile(
    "<([\\w.-]+)>([^<]+)</\\1>");

  private Map<String, String> extractProperties(List<String> lines) {
    Map<String, String> props = new HashMap<>();
    String fullText = String.join("\n", lines);

    int propsStart = fullText.indexOf("<properties>");
    int propsEnd = fullText.indexOf("</properties>");
    if (propsStart < 0 || propsEnd < 0) {
      return props;
    }

    String propsBlock = fullText.substring(propsStart + "<properties>".length(), propsEnd);
    Matcher m = PROPERTY_ENTRY.matcher(propsBlock);
    while (m.find()) {
      props.put(m.group(1), m.group(2));
    }

    return props;
  }

  private String resolveProperty(String value, Map<String, String> props) {
    if (value.startsWith("${") && value.endsWith("}")) {
      String key = value.substring(2, value.length() - 1);
      return props.getOrDefault(key, value);
    }
    return value;
  }

  private String extractTag(String block, Pattern pattern) {
    Matcher m = pattern.matcher(block);
    return m.find() ? m.group(1).trim() : null;
  }

  /** Convert a character offset in the joined text to a 1-based line number. */
  private int findLineNumber(List<String> lines, int charOffset) {
    int pos = 0;
    for (int i = 0; i < lines.size(); i++) {
      pos += lines.get(i).length() + 1; // +1 for \n
      if (charOffset < pos) {
        return i + 1;
      }
    }
    return lines.size();
  }

  // ── OSV client ───────────────────────────────────────────────────

  record OsvVuln(String id, String summary) {}

  interface OsvClient {
    List<OsvVuln> query(String groupId, String artifactId, String version);
  }

  static class HttpOsvClient implements OsvClient {

    private final HttpClient http = HttpClient.newBuilder()
      .connectTimeout(HTTP_TIMEOUT)
      .build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public List<OsvVuln> query(String groupId, String artifactId, String version) {
      String pkg = groupId + ":" + artifactId;
      log.info("CVE check: querying OSV for {} v{}", pkg, version);

      try {
        String body = mapper.writeValueAsString(Map.of(
          "package", Map.of(
            "name", pkg,
            "ecosystem", "Maven"
          ),
          "version", version
        ));

        HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(OSV_API_URL))
          .header("Content-Type", "application/json")
          .timeout(HTTP_TIMEOUT)
          .POST(HttpRequest.BodyPublishers.ofString(body))
          .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
          log.warn("OSV API returned HTTP {} for {}:{}", response.statusCode(), pkg, version);
          return List.of();
        }

        JsonNode root = mapper.readTree(response.body());
        JsonNode vulns = root.path("vulns");

        List<OsvVuln> result = new ArrayList<>();
        if (vulns.isArray()) {
          for (JsonNode vuln : vulns) {
            String id = vuln.path("id").asText("");
            String summary = vuln.path("summary").asText("");
            if (!id.isEmpty()) {
              result.add(new OsvVuln(id, summary));
            }
          }
        }

        log.info("CVE check: {} v{} → {} vulnerability(ies) found", pkg, version, result.size());
        return result;

      } catch (IOException e) {
        log.warn("OSV query failed for {} v{}: {}", pkg, version, e.getMessage());
        return List.of();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.warn("OSV query interrupted for {} v{}", pkg, version);
        return List.of();
      }
    }
  }

  /**
   * Decorator that caches OSV query results for a fixed TTL to avoid
   * repeated network calls when linting the same pom.xml multiple times.
   */
  static class CachingOsvClient implements OsvClient {

    private record CacheEntry(List<OsvVuln> vulns, Instant expiresAt) {}

    private final OsvClient delegate;
    private final Duration ttl;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    CachingOsvClient(OsvClient delegate) {
      this(delegate, CACHE_TTL);
    }

    CachingOsvClient(OsvClient delegate, Duration ttl) {
      this.delegate = delegate;
      this.ttl = ttl;
    }

    @Override
    public List<OsvVuln> query(String groupId, String artifactId, String version) {
      String key = groupId + ":" + artifactId + ":" + version;

      CacheEntry entry = cache.get(key);
      if (entry != null && entry.expiresAt().isAfter(Instant.now())) {
        log.debug("CVE check: cache hit for {} v{}", groupId + ":" + artifactId, version);
        return entry.vulns();
      }

      List<OsvVuln> result = delegate.query(groupId, artifactId, version);

      // Evict stale entries opportunistically
      if (cache.size() > 500) {
        cache.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(Instant.now()));
      }

      cache.put(key, new CacheEntry(result, Instant.now().plus(ttl)));
      return result;
    }
  }
}
