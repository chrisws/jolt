/*
 * This file is part of Jolt
 *
 * Copyright (C) 2026 Chris Warren-Smith. All rights reserved.
*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
*
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
 * limitations under the License.
*/
package com.jolt.parse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * J2EE-aware resolution service. Parses web.xml, persistence.xml, EJB annotations,
 * and Spring DI wiring to provide cross-reference navigation in legacy codebases.
 */
public class J2eeService {

  private static final Logger log = LoggerFactory.getLogger(J2eeService.class);

  public record ServletMapping(
      String servletName, String className, String urlPattern, int line
  ) {}

  public record FilterMapping(
      String filterName, String className, List<String> urlPatterns, List<String> servletNames
  ) {}

  public record EntityMapping(
      String entityName, String tableName, String persistenceUnit, List<String> fields
  ) {}

  public record EjbComponent(
      String className, String type, // Stateless, Stateful, Singleton, LocalBean
      String filePath
  ) {}

  public record WiringEdge(
      String fromClass,   // the class with the @Autowired/@Inject field
      String fieldName,   // the field name
      String toType       // the type being injected (simple name or FQN)
  ) {}

  private final Path homeDir;

  public J2eeService(Path homeDir) {
    this.homeDir = homeDir.toAbsolutePath().normalize();
  }

  /** Parse web.xml and extract servlet/filter mappings. */
  public Map<String, Object> parseWebXml() {
    Path webXml = findWebXml();
    if (webXml == null) {
      return Map.of("found", false);
    }

    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      DocumentBuilder builder = factory.newDocumentBuilder();
      var doc = builder.parse(webXml.toFile());

      List<ServletMapping> servlets = new ArrayList<>();
      List<FilterMapping> filters = new ArrayList<>();

      // Extract servlets: <servlet><servlet-name>X</servlet-name><servlet-class>Y</servlet-class></servlet>
      var servletNodes = doc.getElementsByTagName("servlet");
      Map<String, String> servletNameToClass = new HashMap<>();
      for (int i = 0; i < servletNodes.getLength(); i++) {
        var node = servletNodes.item(i);
        if (!(node instanceof org.w3c.dom.Element el)) continue;

        String name = getFirstChildText(el, "servlet-name");
        String cls = getFirstChildText(el, "servlet-class");
        if (name != null && cls != null) {
          servletNameToClass.put(name, cls);
          servlets.add(new ServletMapping(name, cls, "", 0));
        }
      }

      // Extract url-mappings: <url-pattern>X</url-pattern> linked to servlet-name or filter-name
      var mappingNodes = doc.getElementsByTagName("url-mapping");
      for (int i = 0; i < mappingNodes.getLength(); i++) {
        var node = mappingNodes.item(i);
        if (!(node instanceof org.w3c.dom.Element el)) continue;

        String pattern = getFirstChildText(el, "url-pattern");
        String servletName = getFirstChildText(el, "servlet-name");
        String filterName = getFirstChildText(el, "filter-name");

        if (pattern == null) continue;

        if (servletName != null) {
          // Update the servlet mapping with its URL pattern
          for (int j = 0; j < servlets.size(); j++) {
            if (servlets.get(j).servletName().equals(servletName)) {
              servlets.set(j, new ServletMapping(servletName, servlets.get(j).className(), pattern, 0));
              break;
            }
          }
        }
        // Filter mappings collected separately below
      }

      // Extract filters: <filter><filter-name>X</filter-name><filter-class>Y</filter-class></filter>
      var filterNodes = doc.getElementsByTagName("filter");
      Map<String, String> filterNameToClass = new HashMap<>();
      for (int i = 0; i < filterNodes.getLength(); i++) {
        var node = filterNodes.item(i);
        if (!(node instanceof org.w3c.dom.Element el)) continue;

        String name = getFirstChildText(el, "filter-name");
        String cls = getFirstChildText(el, "filter-class");
        if (name != null && cls != null) {
          filterNameToClass.put(name, cls);
        }
      }

      // Link filters to their URL patterns from url-mapping elements that have filter-name
      Map<String, List<String>> filterUrlPatterns = new HashMap<>();
      for (int i = 0; i < mappingNodes.getLength(); i++) {
        var node = mappingNodes.item(i);
        if (!(node instanceof org.w3c.dom.Element el)) continue;

        String pattern = getFirstChildText(el, "url-pattern");
        String filterName = getFirstChildText(el, "filter-name");

        if (pattern != null && filterName != null) {
          filterUrlPatterns.computeIfAbsent(filterName, k -> new ArrayList<>()).add(pattern);
        }
      }

      for (var entry : filterNameToClass.entrySet()) {
        filters.add(new FilterMapping(
            entry.getKey(), entry.getValue(),
            filterUrlPatterns.getOrDefault(entry.getKey(), List.of()), List.of()
        ));
      }

      return Map.of(
          "found", true,
          "path", webXml.toString(),
          "servlets", servlets.stream().map(s -> Map.<String, Object>of(
              "name", s.servletName(), "class", s.className(), "urlPattern", s.urlPattern()
          )).toList(),
          "filters", filters.stream().map(f -> Map.<String, Object>of(
              "name", f.filterName(), "class", f.className(), "urlPatterns", f.urlPatterns()
          )).toList()
      );

    } catch (Exception e) {
      log.debug("web.xml parse error: {}", e.getMessage());
      return Map.of("found", false);
    }
  }

  /** Parse persistence.xml and extract entity mappings. */
  public Map<String, Object> parsePersistenceXml() {
    Path persXml = findPersistenceXml();
    if (persXml == null) {
      return Map.of("found", false);
    }

    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      DocumentBuilder builder = factory.newDocumentBuilder();
      var doc = builder.parse(persXml.toFile());

      List<EntityMapping> entities = new ArrayList<>();

      var entityNodes = doc.getElementsByTagName("entity");
      for (int i = 0; i < entityNodes.getLength(); i++) {
        var node = entityNodes.item(i);
        if (!(node instanceof org.w3c.dom.Element el)) continue;

        String entityName = el.getAttribute("name"); // e.g., "com.foo.MyEntity"
        String tableName = getFirstChildText(el, "table-name");

        // Get fields from <basic>, <id>, etc.
        List<String> fields = new ArrayList<>();
        var fieldNodes = el.getChildNodes();
        for (int j = 0; j < fieldNodes.getLength(); j++) {
          var child = fieldNodes.item(j);
          if (child instanceof org.w3c.dom.Element childEl) {
            String colName = childEl.getAttribute("name"); // column name attr on basic/id elements
            if (!colName.isEmpty()) fields.add(colName);
          }
        }

        if (entityName != null && !entityName.isEmpty()) {
          entities.add(new EntityMapping(entityName, tableName != null ? tableName : "", "", fields));
        }
      }

      return Map.of(
          "found", true,
          "path", persXml.toString(),
          "entities", entities.stream().map(e -> Map.<String, Object>of(
              "entity", e.entityName(), "table", e.tableName(), "fields", e.fields()
          )).toList()
      );

    } catch (Exception e) {
      log.debug("persistence.xml parse error: {}", e.getMessage());
      return Map.of("found", false);
    }
  }

  /** Scan Java files for EJB annotations and DI wiring. */
  public Map<String, Object> scanAnnotations(List<Path> javaFiles) {
    List<EjbComponent> ejbs = new ArrayList<>();
    List<WiringEdge> wiring = new ArrayList<>();

    for (Path file : javaFiles) {
      try {
        String content = Files.readString(file);
        String relPath = homeDir.relativize(file).toString();

        // Detect EJB annotations
        if (content.contains("@Stateless")) {
          ejbs.add(new EjbComponent(extractClassName(content), "Stateless", relPath));
        } else if (content.contains("@Stateful")) {
          ejbs.add(new EjbComponent(extractClassName(content), "Stateful", relPath));
        } else if (content.contains("@Singleton") && content.contains("javax.ejb") || content.contains("jakarta.ejb")) {
          ejbs.add(new EjbComponent(extractClassName(content), "Singleton", relPath));
        } else if (content.contains("@LocalBean")) {
          ejbs.add(new EjbComponent(extractClassName(content), "LocalBean", relPath));
        }

        // Detect DI wiring: @Autowired, @Inject, @Resource fields
        var fieldMatcher = java.util.regex.Pattern.compile(
            "(?:@Autowired|@Inject|@Resource)\\s+(?:private|protected|public)?\\s*(\\w+(?:<[^>]*>)?(?:\\[\\])?)\\s+(\\w+)\\s*;"
        ).matcher(content);

        while (fieldMatcher.find()) {
          String type = fieldMatcher.group(1);
          String fieldName = fieldMatcher.group(2);
          String className = extractClassName(content);
          wiring.add(new WiringEdge(className, fieldName, type));
        }

      } catch (Exception e) {
        log.debug("Annotation scan error for {}: {}", file, e.getMessage());
      }
    }

    return Map.of(
        "ejbs", ejbs.stream().map(e -> Map.<String, Object>of(
            "class", e.className(), "type", e.type(), "file", e.filePath()
        )).toList(),
        "wiring", wiring.stream().map(w -> Map.<String, Object>of(
            "from", w.fromClass(), "field", w.fieldName(), "to", w.toType()
        )).toList()
    );
  }

  /** Build a wiring graph from DI annotations. Returns adjacency list. */
  public Map<String, List<String>> buildWiringGraph(List<Path> javaFiles) {
    Map<String, List<String>> graph = new LinkedHashMap<>();

    for (Path file : javaFiles) {
      try {
        String content = Files.readString(file);
        String className = extractClassName(content);

        var fieldMatcher = java.util.regex.Pattern.compile(
            "(?:@Autowired|@Inject|@Resource)\\s+(?:private|protected|public)?\\s*(\\w+(?:<[^>]*>)?(?:\\[\\])?)\\s+\\w+\\s*;"
        ).matcher(content);

        while (fieldMatcher.find()) {
          String depType = fieldMatcher.group(1).split("<")[0]; // strip generics
          graph.computeIfAbsent(className, k -> new ArrayList<>()).add(depType);
        }

      } catch (Exception ignored) {}
    }

    return graph;
  }

  // ─── Private helpers ────────────────────────────────────────────

  private Path findWebXml() {
    // Common locations for web.xml
    List<Path> candidates = List.of(
        homeDir.resolve("src/main/webapp/WEB-INF/web.xml"),
        homeDir.resolve("webapp/WEB-INF/web.xml"),
        homeDir.resolve("WEB-INF/web.xml"),
        homeDir.resolve("src/webapp/WEB-INF/web.xml")
    );
    for (Path p : candidates) {
      if (Files.exists(p)) return p;
    }
    return null;
  }

  private Path findPersistenceXml() {
    List<Path> candidates = List.of(
        homeDir.resolve("src/main/resources/META-INF/persistence.xml"),
        homeDir.resolve("src/resources/META-INF/persistence.xml"),
        homeDir.resolve("META-INF/persistence.xml")
    );
    for (Path p : candidates) {
      if (Files.exists(p)) return p;
    }
    return null;
  }

  private static String getFirstChildText(org.w3c.dom.Element parent, String childTag) {
    var children = parent.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      var child = children.item(i);
      if (child instanceof org.w3c.dom.Element el && el.getTagName().equals(childTag)) {
        return el.getTextContent().trim();
      }
    }
    return null;
  }

  /** Extract the primary class name from Java source (first public class declaration). */
  private static String extractClassName(String source) {
    var matcher = java.util.regex.Pattern.compile(
        "(?:public|abstract|final)?\\s*(?:class|interface|enum)\\s+(\\w+)"
    ).matcher(source);
    if (matcher.find()) return matcher.group(1);

    // Fallback: use package + first type-like token
    var pkgMatcher = java.util.regex.Pattern.compile("package\\s+([\\w.]+);").matcher(source);
    if (pkgMatcher.find()) return pkgMatcher.group(1) + ".Unknown";

    return "Unknown";
  }

}
