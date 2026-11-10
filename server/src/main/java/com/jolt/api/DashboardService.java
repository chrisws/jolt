package com.jolt.api;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Aggregates project stats, git context, and index state for the dashboard. */
public class DashboardService {

  private static final Logger log = LoggerFactory.getLogger(DashboardService.class);
  private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

  private final Path homeDir;

  public DashboardService(Path homeDir) {
    this.homeDir = homeDir.toAbsolutePath().normalize();
  }

  /** Build the full dashboard payload. */
  public Map<String, Object> buildDashboard(int classCount, int methodCount, int filesIndexed) {
    Map<String, Object> result = new LinkedHashMap<>();

    // Stats
    result.put("stats", Map.of(
        "classes", classCount,
        "methods", methodCount,
        "filesIndexed", filesIndexed
    ));

    // Git info (best-effort — return empty map if no repo)
    result.put("git", buildGitInfo());

    // Recent changes from git log (last 5)
    result.put("recentChanges", buildRecentChanges());

    // Index age — we don't track index creation time yet, so report 0
    result.put("indexAgeMinutes", 0);

    return result;
  }

  private Map<String, Object> buildGitInfo() {
    Map<String, Object> gitInfo = new LinkedHashMap<>();
    try (Git git = Git.open(homeDir.toFile())) {
      Repository repo = git.getRepository();

      String branch = repo.getBranch();
      gitInfo.put("branch", branch);

      // Last commit
      var headRef = repo.exactRef("HEAD");
      ObjectId headId = (headRef != null) ? headRef.getObjectId() : null;
      if (headId != null) {
        RevCommit head = repo.parseCommit(headId);
        String date = ISO_FMT.format(Instant.ofEpochSecond(head.getCommitTime()).atZone(ZoneOffset.UTC));
        String authorName = extractAuthorName(repo, headId);
        gitInfo.put("lastCommit", Map.of(
            "hash", head.getName().substring(0, Math.min(7, head.getName().length())),
            "message", head.getShortMessage(),
            "author", authorName,
            "date", date
        ));
      }

      // Total commits (walk all)
      int totalCommits = 0;
      try (var revWalk = new org.eclipse.jgit.revwalk.RevWalk(repo)) {
        revWalk.markStart(repo.parseCommit(headId));
        for (ObjectId id : revWalk) { totalCommits++; }
      }
      gitInfo.put("totalCommits", totalCommits);

    } catch (Exception e) {
      log.debug("No git repo at {}: {}", homeDir, e.getMessage());
      gitInfo.put("branch", "");
      gitInfo.put("totalCommits", 0);
    }

    return gitInfo;
  }

  private List<Map<String, Object>> buildRecentChanges() {
    List<Map<String, Object>> changes = new ArrayList<>();
    try (Git git = Git.open(homeDir.toFile())) {
      Repository repo = git.getRepository();
      var headRef = repo.exactRef("HEAD");
      ObjectId headId = (headRef != null) ? headRef.getObjectId() : null;

      try (var revWalk = new org.eclipse.jgit.revwalk.RevWalk(repo)) {
        revWalk.markStart(repo.parseCommit(headId));

        int count = 0;
        for (ObjectId id : revWalk) {
          if (count >= 5) break;
          RevCommit commit = repo.parseCommit(id);

          String desc = commit.getParentCount() > 0
              ? commit.getShortMessage()
              : commit.getShortMessage() + " (initial)";

          changes.add(Map.of(
              "file", desc,
              "action", "modified",
              "hash", commit.getName().substring(0, Math.min(7, commit.getName().length()))
          ));
          count++;
        }
      }

    } catch (Exception e) {
      log.debug("No git history: {}", e.getMessage());
    }

    return changes;
  }

  /** Extract author name from raw commit bytes (JGit RevCommit doesn't expose this directly). */
  private String extractAuthorName(Repository repo, ObjectId commitId) {
    try {
      var obj = repo.open(commitId);
      String raw = new String(obj.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
      for (String line : raw.split("\n")) {
        if (line.startsWith("author ")) {
          int start = 7; // skip "author "
          int end = line.indexOf(' ', start);
          return end > start ? line.substring(start, end) : "unknown";
        }
      }
    } catch (Exception e) {
      log.debug("Could not extract author: {}", e.getMessage());
    }
    return "unknown";
  }

}
