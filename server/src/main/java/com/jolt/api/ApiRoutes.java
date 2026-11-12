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

package com.jolt.api;

 import com.jolt.debug.DebugManager;
import com.jolt.debug.DebugSession;
import com.jolt.git.BlameService;
import com.jolt.git.GitService;
import com.jolt.index.EmbeddingService;
import com.jolt.index.LuceneIndexer;
import com.jolt.model.*;
import com.jolt.nav.CallGraph;
import com.jolt.nav.SymbolTable;
import com.jolt.parse.FileWalker;
import com.jolt.parse.JavaParser;
import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Wires up all REST endpoints on the Javalin instance. */
public class ApiRoutes {

  private static final Logger log = LoggerFactory.getLogger(ApiRoutes.class);

  private final SymbolTable symbolTable;
  private final LuceneIndexer indexer;
  private final Path homeDir;
  private final FeedbackService feedback;
  private final BlameService blameSvc;
  private final DashboardService dashboard;
private EmbeddingService embedder; // nullable — null if model file missing
  private final CallGraph callGraph;
  private final GitService gitSvc;
  private final SettingsService settingsSvc;
  private final KeybindingsService keybindingsSvc;
  private DebugManager debugMgr; // nullable — set via setDebugManager()

  public void setDebugManager(DebugManager mgr) { this.debugMgr = mgr; }

  public ApiRoutes(SymbolTable symbolTable, LuceneIndexer indexer, Path homeDir, CallGraph callGraph, EmbeddingService embedder) {
    this.symbolTable = symbolTable;
    this.indexer = indexer;
    this.homeDir = homeDir.toAbsolutePath().normalize();
    this.feedback = new FeedbackService(this.homeDir);
    this.blameSvc = new BlameService(this.homeDir);
    this.dashboard = new DashboardService(this.homeDir);
    this.callGraph = callGraph;
    this.gitSvc = new GitService(this.homeDir);
    this.settingsSvc = new SettingsService(this.homeDir);
    this.keybindingsSvc = new KeybindingsService();
    this.embedder = embedder; // nullable — passed from ServerBootstrap
  }

  /** Register all routes on the given Javalin app. */
  public void register(Javalin app) {
    var routes = app.unsafe.routes;

    // --- Status ---
    routes.get("/api/status", ctx -> {
      Map<String, Object> status = Map.of(
          "classesIndexed", symbolTable.size(),
          "status", "ok"
      );
      ctx.json(status);
    });

    // --- Dashboard ---
    routes.get("/api/dashboard", ctx -> {
      int classes = symbolTable.size();
      int methods = 0;
      for (var cs : symbolTable.allClassSymbols()) {
        methods += cs.methods.size();
      }
      Map<String, Object> dash = dashboard.buildDashboard(classes, methods, classes);
      ctx.json(dash);
    });

    // --- Search ---
    routes.get("/api/search", ctx -> {
      String q = ctx.queryParam("q");
      if (q == null || q.isBlank()) { ctx.status(400).result("Missing 'q' parameter"); return; }

      SymbolKind kindFilter = null;
      String kindStr = ctx.queryParam("kind");
      if (kindStr != null && !kindStr.isBlank()) {
        try { kindFilter = SymbolKind.valueOf(kindStr.toUpperCase()); }
        catch (IllegalArgumentException e) { /* ignore invalid kind */ }
      }

      int maxResults = 50;
      try { maxResults = Integer.parseInt(ctx.queryParam("limit")); } catch (Exception ignored) {}

      // Read fuzzy maxEdits from settings (default 2)
      int maxEdits = 2;
      Object cfgEdits = settingsSvc.getSetting("search.fuzzy.maxEdits");
      if (cfgEdits instanceof Number n) { maxEdits = Math.max(0, Math.min(3, n.intValue())); }

      boolean caseSensitive = "1".equals(ctx.queryParam("cs"));
      boolean wholeWord = "1".equals(ctx.queryParam("ww"));

      List<SearchHit> hits;
      try {
        hits = indexer.search(q, kindFilter, maxResults, maxEdits, caseSensitive, wholeWord);
      } catch (Exception e) {
        log.error("Search failed: {}", e.getMessage());
        ctx.status(500).json(Map.of("error", e.getMessage()));
        return;
      }

      // Relativize absolute filePaths against homeDir for consistent tab matching
      List<SearchHit> relativized = hits.stream().map(h -> {
        Path fp = Path.of(h.filePath());
        if (fp.isAbsolute() && fp.startsWith(homeDir)) {
          String rel = homeDir.relativize(fp).toString();
          return new SearchHit(h.id(), h.kind(), h.name(), h.qualifiedName(), rel, h.lineStart(), h.score());
        }
        return h;
      }).toList();

      ctx.json(relativized);
    });

    // --- Search Suggestions (typeahead) ---
    routes.get("/api/search/suggest", ctx -> {
      String q = ctx.queryParam("q");
      if (q == null || q.isBlank()) { ctx.json(List.of()); return; }

      int limit = 10; // default, overridable via settings
      try { limit = Integer.parseInt(ctx.queryParam("limit")); } catch (Exception ignored) {}

      // Read limit from settings if no explicit param
      if (ctx.queryParam("limit") == null) {
        Object cfgLimit = settingsSvc.getSetting("search.suggest.limit");
        if (cfgLimit instanceof Number n) { limit = n.intValue(); }
      }

      try {
        List<SearchHit> hits = indexer.suggest(q, limit);
        ctx.json(hits);
      } catch (Exception e) {
        log.error("Suggest failed: {}", e.getMessage());
        ctx.status(500).json(Map.of("error", e.getMessage()));
      }
    });

    // --- Semantic Search ---
    routes.get("/api/search/semantic", ctx -> {
      String q = ctx.queryParam("q");
      if (q == null || q.isBlank()) { ctx.status(400).result("Missing 'q' parameter"); return; }

      if (embedder == null) {
        ctx.status(503).json(Map.of(
            "error", "Semantic search unavailable — embedding model not loaded",
            "hint", "Place all-MiniLM-L6-v2.onnx in .jolt/models/"
        ));
        return;
      }

      int limit = 20;
      try { limit = Integer.parseInt(ctx.queryParam("limit")); } catch (Exception ignored) {}

      try {
        float[] queryVec = embedder.embed(q);
        List<SearchHit> hits = indexer.searchSemantic(queryVec, limit);
        ctx.json(hits);
      } catch (Exception e) {
        log.error("Semantic search failed: {}", e.getMessage());
        ctx.status(500).json(Map.of("error", e.getMessage()));
      }
    });

    // --- File Content ---
    routes.get("/api/file", ctx -> {
      String path = ctx.queryParam("path");
      if (path == null || path.isBlank()) { ctx.status(400).result("Missing 'path' parameter"); return; }

      Path filePath = homeDir.resolve(path).normalize();

      if (!filePath.startsWith(homeDir)) { ctx.status(403).result("Access denied"); return; }

      if (!Files.exists(filePath)) { ctx.status(404).result("File not found: " + path); return; }
      if (!Files.isRegularFile(filePath)) { ctx.status(400).result("Not a file: " + path); return; }

      int startLine = 1;
      int endLine = Integer.MAX_VALUE;
      String startStr = ctx.queryParam("start");
      if (startStr != null) { try { startLine = Math.max(1, Integer.parseInt(startStr)); } catch (NumberFormatException ignored) {} }
      String endStr = ctx.queryParam("end");
      if (endStr != null) { try { endLine = Integer.parseInt(endStr); } catch (NumberFormatException ignored) {} }

      List<String> allLines = Files.readAllLines(filePath);

      int fromIdx = Math.min(startLine - 1, allLines.size());
      int toIdx = Math.min(endLine, allLines.size());

      Map<String, Object> response = Map.of(
          "path", path,
          "totalLines", allLines.size(),
          "content", String.join("\n", allLines.subList(fromIdx, toIdx)),
          "language", detectLanguage(path)
      );

      ctx.json(response);
    });

    // --- File Structure (outline) ---
    routes.get("/api/file/structure", ctx -> {
      String path = ctx.queryParam("path");
      if (path == null || path.isBlank()) { ctx.status(400).result("Missing 'path' parameter"); return; }

      Path filePath = homeDir.resolve(path).normalize();
      if (!filePath.startsWith(homeDir)) { ctx.status(403).result("Access denied"); return; }
      if (!Files.exists(filePath)) { ctx.status(404).result("File not found: " + path); return; }

      try {
        var svc = new com.jolt.parse.JavaParserService(filePath);
        var types = svc.parseFile();

        List<Map<String, Object>> outline = new ArrayList<>();
        for (var cs : types) {
          Map<String, Object> cls = new LinkedHashMap<>();
          cls.put("name", cs.name);
          cls.put("kind", cs.kind.name());
          cls.put("line", cs.lineStart);

          List<Map<String, Object>> members = new ArrayList<>();
          for (var fs : cs.fields.values()) {
            members.add(Map.of("name", fs.name(), "kind", "FIELD", "line", 0));
          }
          for (var ms : cs.methods.values()) {
            members.add(Map.of("name", ms.name() + "()", "kind", "METHOD", "line", ms.lineStart()));
          }
          cls.put("members", members);
          outline.add(cls);
        }

        ctx.json(outline);
      } catch (Exception e) {
        log.error("File structure failed: {}", e.getMessage());
        ctx.status(500).json(Map.of("error", e.getMessage()));
      }
    });

    // --- Go-to-Definition ---
    routes.get("/api/definition", ctx -> {
      String symbol = ctx.queryParam("symbol");
      if (symbol == null || symbol.isBlank()) { ctx.status(400).result("Missing 'symbol' parameter"); return; }

      var optCs = symbolTable.findDefinition(symbol);
      if (optCs.isEmpty()) { ctx.status(404).json(Map.of("error", "Symbol not found: " + symbol)); return; }

      ClassSymbol cs = optCs.get();

      List<Map<String, Object>> members = new java.util.ArrayList<>();
      for (var entry : cs.methods.entrySet()) {
        MethodSymbol ms = entry.getValue();
        members.add(Map.of(
            "name", ms.name(), "kind", "METHOD",
            "lineStart", ms.lineStart(), "signature", ms.signature()
        ));
      }
      for (var entry : cs.fields.entrySet()) {
        FieldSymbol fs = entry.getValue();
        members.add(Map.of(
            "name", fs.name(), "kind", "FIELD",
            "lineStart", fs.lineStart(), "type", fs.type()
        ));
      }

      Map<String, Object> response = Map.of(
          "symbol", cs.fqn,
          "kind", cs.kind.name(),
          "filePath", cs.filePath,
          "lineStart", cs.lineStart,
          "superClass", cs.superClass != null ? cs.superClass : "",
          "members", members
      );

      ctx.json(response);
    });

    // --- Class Hierarchy ---
    routes.get("/api/hierarchy", ctx -> {
      String cls = ctx.queryParam("class");
      if (cls == null || cls.isBlank()) { ctx.status(400).result("Missing 'class' parameter"); return; }

      var optCs = symbolTable.findDefinition(cls);
      if (optCs.isEmpty()) { ctx.status(404).json(Map.of("error", "Class not found: " + cls)); return; }

      ClassSymbol target = optCs.get();

      List<String> superChain = symbolTable.getSuperChain(cls);
      List<ClassSymbol> subTypes = symbolTable.getSubTypes(cls);

      Map<String, Object> response = Map.of(
          "class", cls,
          "superClasses", superChain,
          "interfaces", target.interfaces,
          "subTypes", subTypes.stream().map(cs -> cs.fqn).toList()
      );

      ctx.json(response);
    });

    // --- File Tree ---
    routes.get("/api/file/tree", ctx -> {
      String path = ctx.queryParam("path");
      Path dirPath = homeDir.resolve(path != null ? path : "").normalize();

      if (!dirPath.startsWith(homeDir)) { ctx.status(403).result("Access denied"); return; }

      if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
        ctx.status(404).json(Map.of("error", "Directory not found")); return;
      }

      try (var stream = Files.list(dirPath)) {
        var entries = stream.sorted((a, b) -> {
          boolean aDir = Files.isDirectory(a);
          boolean bDir = Files.isDirectory(b);
          if (aDir && !bDir) return -1;
          if (!aDir && bDir) return 1;
          return a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString());
        }).map(p -> Map.<String, Object>of(
            "name", p.getFileName().toString(),
            "type", Files.isDirectory(p) ? "directory" : "file",
            "path", homeDir.relativize(p).toString()
        )).toList();

        ctx.json(entries);
      } catch (Exception e) {
        log.error("File tree failed for {}: {}", path, e.getMessage());
        ctx.status(500).json(Map.of("error", e.getMessage()));
      }
    });

    // --- Find References ---
    routes.get("/api/references", ctx -> {
      String symbol = ctx.queryParam("symbol");
      if (symbol == null || symbol.isBlank()) { ctx.status(400).result("Missing 'symbol' parameter"); return; }

      var refs = symbolTable.findReferences(symbol);

      List<Map<String, Object>> refList = refs.stream()
          .map(r -> Map.<String, Object>of("filePath", r.filePath(), "line", r.line()))
          .toList();

      ctx.json(refList);
    });

    // --- Call Graph ---
    routes.get("/api/call-graph", ctx -> {
      String method = ctx.queryParam("method");
      if (method == null || method.isBlank()) { ctx.status(400).result("Missing 'method' parameter"); return; }

      int depth = 3; // default depth limit
      try { depth = Integer.parseInt(ctx.queryParam("depth")); } catch (Exception ignored) {}
      depth = Math.min(depth, 10); // cap at 10 to prevent runaway traversal

      List<CallEdge> edges = callGraph.traverse(method, depth);

      List<Map<String, Object>> edgeList = edges.stream()
          .map(e -> Map.<String, Object>of(
              "caller", e.callerFqn(),
              "callee", e.calleeName(),
              "receiver", e.calleeReceiver(),
              "line", e.line()
          ))
          .toList();

      Map<String, Object> response = Map.of(
          "method", method,
          "depth", depth,
          "edgeCount", edgeList.size(),
          "edges", edgeList
      );

      ctx.json(response);
    });

    // --- Call Graph: Who Calls Me ---
    routes.get("/api/call-graph/callers", ctx -> {
      String method = ctx.queryParam("method");
      if (method == null || method.isBlank()) { ctx.status(400).result("Missing 'method' parameter"); return; }

      // Extract just the method name (after # if present)
      String methodName = method.contains("#") ? method.substring(method.indexOf('#') + 1) : method;

      Set<String> callers = callGraph.getCallers(methodName);

      ctx.json(Map.of(
          "method", method,
          "callers", callers.stream().toList()
      ));
    });

    // --- JSP Parse ---
    routes.get("/api/jsp/parse", ctx -> {
      String path = ctx.queryParam("path");
      if (path == null || path.isBlank()) { ctx.status(400).result("Missing 'path' parameter"); return; }

      Path filePath = homeDir.resolve(path).normalize();
      if (!filePath.startsWith(homeDir)) { ctx.status(403).result("Access denied"); return; }
      if (!Files.exists(filePath)) { ctx.status(404).result("File not found: " + path); return; }

      var jspParser = new com.jolt.parse.JspParser();
      var result = jspParser.parse(filePath);

      List<Map<String, Object>> elemList = result.elements().stream()
          .map(e -> Map.<String, Object>of(
              "type", e.type(), "content", e.content(), "line", e.line(), "name", e.name()
          ))
          .toList();

      Map<String, Object> response = Map.of(
          "path", path,
          "elements", elemList,
          "includedFiles", result.includedFiles(),
          "tagLibUris", result.tagLibUris()
      );

      ctx.json(response);
    });

    // --- Spring XML Beans ---
    routes.get("/api/spring/beans", ctx -> {
      String path = ctx.queryParam("path");
      if (path == null || path.isBlank()) { ctx.status(400).result("Missing 'path' parameter"); return; }

      Path filePath = homeDir.resolve(path).normalize();
      if (!filePath.startsWith(homeDir)) { ctx.status(403).result("Access denied"); return; }
      if (!Files.exists(filePath)) { ctx.status(404).result("File not found: " + path); return; }

      var springParser = new com.jolt.parse.SpringXmlParser();
      var config = springParser.parse(filePath);

      List<Map<String, Object>> beanList = config.beans().stream()
          .map(b -> Map.<String, Object>of(
              "id", b.id(), "class", b.className(), "scope", b.scope(),
              "dependsOn", b.dependsOn()
          ))
          .toList();

      Map<String, Object> response = Map.of(
          "path", path,
          "beans", beanList,
          "componentScanPaths", config.componentScanPaths(),
          "imports", config.importedFiles()
      );

      ctx.json(response);
    });

    // --- Git Blame ---
    routes.get("/api/blame", ctx -> {
      String path = ctx.queryParam("path");
      if (path == null || path.isBlank()) { ctx.status(400).result("Missing 'path' parameter"); return; }

      int startLine = 1;
      int endLine = Integer.MAX_VALUE;
      String startStr = ctx.queryParam("start");
      if (startStr != null) { try { startLine = Math.max(1, Integer.parseInt(startStr)); } catch (NumberFormatException ignored) {} }
      String endStr = ctx.queryParam("end");
      if (endStr != null) { try { endLine = Integer.parseInt(endStr); } catch (NumberFormatException ignored) {} }

      Path filePath = homeDir.resolve(path).normalize();
      if (!filePath.startsWith(homeDir)) { ctx.status(403).result("Access denied"); return; }
      if (!Files.exists(filePath)) { ctx.status(404).result("File not found: " + path); return; }

      try {
        List<BlameLine> lines = blameSvc.blame(path, startLine, endLine);
        ctx.json(lines);
      } catch (IOException e) {
        log.error("Blame failed for {}: {}", path, e.getMessage());
        ctx.status(500).json(Map.of("error", e.getMessage()));
      }
    });

    // --- J2EE: web.xml ---
    routes.get("/api/j2ee/webxml", ctx -> {
      var j2ee = new com.jolt.parse.J2eeService(homeDir);
      Map<String, Object> result = j2ee.parseWebXml();
      ctx.json(result);
    });

    // --- J2EE: persistence.xml ---
    routes.get("/api/j2ee/persistence", ctx -> {
      var j2ee = new com.jolt.parse.J2eeService(homeDir);
      Map<String, Object> result = j2ee.parsePersistenceXml();
      ctx.json(result);
    });

    // --- J2EE: Annotations (EJB + DI) ---
    routes.get("/api/j2ee/annotations", ctx -> {
      var j2ee = new com.jolt.parse.J2eeService(homeDir);
      try {
        List<Path> javaFiles = com.jolt.parse.FileWalker.findJavaFiles(homeDir);
        Map<String, Object> result = j2ee.scanAnnotations(javaFiles);
        ctx.json(result);
      } catch (Exception e) {
        ctx.status(500).json(Map.of("error", e.getMessage()));
      }
    });

    // --- J2EE: Wiring Graph ---
    routes.get("/api/j2ee/wiring-graph", ctx -> {
      var j2ee = new com.jolt.parse.J2eeService(homeDir);
      try {
        List<Path> javaFiles = com.jolt.parse.FileWalker.findJavaFiles(homeDir);
        Map<String, List<String>> graph = j2ee.buildWiringGraph(javaFiles);

        // Convert to list format for JSON serialization
        List<Map<String, Object>> edges = new java.util.ArrayList<>();
        for (var entry : graph.entrySet()) {
          for (String dep : entry.getValue()) {
            edges.add(Map.of("from", entry.getKey(), "to", dep));
          }
        }

        ctx.json(Map.of(
            "nodeCount", graph.size(),
            "edgeCount", edges.size(),
            "edges", edges
        ));
      } catch (Exception e) {
        ctx.status(500).json(Map.of("error", e.getMessage()));
      }
    });


    routes.get("/api/git/diff", ctx -> {
      String from = ctx.queryParam("from"); // optional — if null, diff commit vs parent
      String to = ctx.queryParam("to");     // required — the commit to diff at

      if (to == null || to.isBlank()) { ctx.status(400).result("Missing 'to' parameter"); return; }

      List<Map<String, Object>> diffs = gitSvc.diff(from, to);
      ctx.json(diffs);
    });

    // --- Git File Diff (content) ---
    routes.get("/api/git/file-diff", ctx -> {
      String from = ctx.queryParam("from");
      String to = ctx.queryParam("to");
      String path = ctx.queryParam("path");

      if (to == null || to.isBlank()) { ctx.status(400).result("Missing 'to' parameter"); return; }
      if (path == null || path.isBlank()) { ctx.status(400).result("Missing 'path' parameter"); return; }

      String diffContent = gitSvc.fileDiff(from, to, path);
      ctx.json(Map.of("path", path, "diff", diffContent));
    });

    // --- Git Branches ---
    routes.get("/api/git/branches", ctx -> {
      List<Map<String, Object>> branches = gitSvc.branches();
      ctx.json(branches);
    });

    // --- Git Checkout Branch ---
    routes.post("/api/git/checkout", ctx -> {
String branch = ctx.body();
      if (branch == null || branch.isBlank()) { ctx.status(400).result("Missing branch name"); return; }
      try {
        String current = gitSvc.checkoutBranch(branch);
        ctx.json(Map.of("branch", current));
      } catch (Exception e) {
        ctx.status(409).json(Map.of("error", e.getMessage()));
      }
    });
// --- Model Management ---
    routes.get("/api/models", ctx -> {
      try {
        Path joltDir = homeDir.resolve(".jolt");
        if (!Files.exists(joltDir)) { ctx.json(List.of()); return; }
        List<String> models = new ArrayList<>();
        Files.walk(joltDir, 3).filter(p -> p.toString().endsWith(".onnx"))
          .forEach(p -> models.add(joltDir.relativize(p).toString()));
        ctx.json(models);
      } catch (Exception e) { ctx.status(500).json(Map.of("error", e.getMessage())); }
    });

    routes.post("/api/models/select", ctx -> {
      String file = ctx.body();
      if (file == null || file.isBlank()) { ctx.status(400).result("Missing model path"); return; }
      try {
        Path modelPath = homeDir.resolve(".jolt").resolve(file);
        if (!Files.exists(modelPath)) { ctx.status(404).json(Map.of("error", "File not found: " + file)); return; }
         if (embedder != null) embedder.close();
        this.embedder = new EmbeddingService(modelPath);

        // Trigger async re-index so the HTTP response returns immediately
        Thread reindexThread = new Thread(() -> {
          try {
            log.info("Re-indexing all files with model: {}", file);
            reindexAll();
            log.info("Re-index complete");
          } catch (Exception e) {
            log.error("Re-index failed: {}", e.getMessage(), e);
          }
        }, "jolt-reindex");
        reindexThread.setDaemon(true);
        reindexThread.start();

        ctx.json(Map.of("model", file, "status", "loaded", "reindexing", true));
} catch (Exception e) { ctx.status(500).json(Map.of("error", e.getMessage())); }
    });


    // --- Bookmarks ---
    var bookmarksSvc = new BookmarksService(homeDir);

    routes.get("/api/bookmarks", ctx -> {
      ctx.json(bookmarksSvc.getBookmarks());
    });

    routes.post("/api/bookmarks", ctx -> {
      try {
        var body = ctx.bodyAsClass(Map.class);
        String path = (String) body.get("path");
        int line = ((Number) body.getOrDefault("line", 0)).intValue();
        String label = (String) body.get("label");

        if (path == null || path.isBlank()) { ctx.status(400).result("Missing 'path'"); return; }

        Map<String, Object> bm = bookmarksSvc.addBookmark(path, line, label);
        ctx.status(201).json(bm);
      } catch (Exception e) {
        ctx.status(500).json(Map.of("error", e.getMessage()));
      }
    });

    routes.delete("/api/bookmarks", ctx -> {
      String path = ctx.queryParam("path");
      int line = 0;
      try { line = Integer.parseInt(ctx.queryParam("line")); } catch (Exception ignored) {}

      if (path == null) { ctx.status(400).result("Missing 'path'"); return; }

      boolean removed = bookmarksSvc.removeBookmark(path, line);
      if (removed) { ctx.json(Map.of("ok", true)); }
      else { ctx.status(404).json(Map.of("ok", false)); }
    });

    // --- Recent Files ---
    routes.get("/api/recent", ctx -> {
      ctx.json(bookmarksSvc.getRecentFiles());
    });

    // --- Search History ---
    routes.get("/api/search/history", ctx -> {
      ctx.json(bookmarksSvc.getSearchHistory());
    });

    // --- Feedback ---
    routes.post("/api/feedback", ctx -> {
      try {
        var body = ctx.bodyAsClass(Map.class);
        String page = (String) body.getOrDefault("page", "unknown");
        String file = (String) body.get("file");
        String message = (String) body.get("message");

        if (message == null || message.isBlank()) {
          ctx.status(400).json(Map.of("error", "Message is required")); return;
        }

        Map<String, Object> entry = feedback.record(page, file, message);
        ctx.status(201).json(entry);

       } catch (Exception e) {
        log.error("Feedback write failed: {}", e.getMessage());
        ctx.status(500).json(Map.of("error", e.getMessage()));
      }
    });

    routes.post("/api/feedback/complete", ctx -> {
      try {
        var body = ctx.bodyAsClass(Map.class);
        String page = (String) body.getOrDefault("page", "unknown");
        String file = (String) body.get("file");
        String message = (String) body.get("message");

        if (message == null || message.isBlank()) {
          ctx.status(400).json(Map.of("error", "Message is required")); return;
        }

        Map<String, Object> entry = feedback.markCompleted(page, file, message);
        ctx.status(201).json(entry);

      } catch (Exception e) {
        log.error("Feedback complete write failed: {}", e.getMessage());
        ctx.status(500).json(Map.of("error", e.getMessage()));
      }
    });


    // --- Settings ---
    routes.get("/api/settings", ctx -> {
      ctx.json(settingsSvc.getSettings());
    });

    routes.put("/api/settings", ctx -> {
      try {
        @SuppressWarnings("unchecked")
        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        Map<String, Object> updated = settingsSvc.updateSettings(body);
        ctx.json(updated);
      } catch (Exception e) {
        log.error("Settings update failed: {}", e.getMessage());
        ctx.status(500).json(Map.of("error", e.getMessage()));
      }
    });

    // --- Keybindings ---
    routes.get("/api/keybindings", ctx -> {
      String mode = ctx.queryParam("mode");
      if (mode == null || mode.isBlank()) { mode = "default"; }

      Map<String, Object> result = keybindingsSvc.getKeybindings(mode);

      // Check for error response (unknown mode)
      if (result.containsKey("error")) {
        ctx.status(400).json(result);
        return;
      }

      // Include available modes for convenience
      Map<String, Object> response = new java.util.LinkedHashMap<>(result);
      response.put("availableModes", keybindingsSvc.availableModes());
      ctx.json(response);
    });

    // --- Git Stats (Stage 3a) ---
    routes.get("/api/git/stats", ctx -> {
      Map<String, Object> stats = gitSvc.stats();
      ctx.json(stats);
    });

    // --- Git Working Tree Status (dirty files) ---
    routes.get("/api/git/status", ctx -> {
      Set<String> dirty = gitSvc.workingTreeStatus();
      ctx.json(dirty);
    });

    // --- Git Working Tree Diff (single file) ---
    routes.get("/api/git/dirty-diff", ctx -> {
      String path = ctx.queryParam("path");
      if (path == null || path.isBlank()) { ctx.status(400).result("Missing 'path' parameter"); return; }
      String diff = gitSvc.workingTreeDiff(path);
      ctx.contentType("text/plain").result(diff);
    });

    // --- Git Commits Paginated (Stage 3b) ---
    routes.get("/api/git/commits", ctx -> {
      String branch = ctx.queryParam("branch");
      String author = ctx.queryParam("author");
      String from = ctx.queryParam("from");
      String to = ctx.queryParam("to");

      int page = 0, size = 25;
      try { page = Math.max(0, Integer.parseInt(ctx.queryParam("page"))); } catch (Exception ignored) {}
      try { size = Math.min(100, Math.max(1, Integer.parseInt(ctx.queryParam("size")))); } catch (Exception ignored) {}

      Map<String, Object> result = gitSvc.commitsPaginated(branch, author, from, to, page, size);
      ctx.json(result);
    });

    // --- Git Activity Heatmap (Stage 6a) ---
    routes.get("/api/git/activity", ctx -> {
      int year = java.time.Year.now().getValue();
      try { year = Integer.parseInt(ctx.queryParam("year")); } catch (Exception ignored) {}

      List<Map<String, Object>> cells = gitSvc.activity(year);
      ctx.json(Map.of("year", year, "cells", cells));
    });

    // --- Git Commit Enhanced Detail (Stage 4a) ---
    routes.get("/api/git/commit/enhanced", ctx -> {
      String hash = ctx.queryParam("hash");
      if (hash == null || hash.isBlank()) { ctx.status(400).result("Missing 'hash' parameter"); return; }

      Map<String, Object> detail = gitSvc.commitDetailEnhanced(hash);
      if (detail.containsKey("error")) { ctx.status(404).json(detail); return; }
      ctx.json(detail);
    });

    // --- Git Raw File at Commit (Stage 5b) ---
    routes.get("/api/git/commit/raw", ctx -> {
      String hash = ctx.queryParam("hash");
      String path = ctx.queryParam("path");

      if (hash == null || hash.isBlank()) { ctx.status(400).result("Missing 'hash' parameter"); return; }
      if (path == null || path.isBlank()) { ctx.status(400).result("Missing 'path' parameter"); return; }

      String content = gitSvc.rawFileAtCommit(hash, path);
      if (content == null) { ctx.status(404).result("File not found at commit: " + path); return; }

      // Set content type based on extension
      String ct = "text/plain";
      String lower = path.toLowerCase();
      if (lower.endsWith(".png")) ct = "image/png";
      else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) ct = "image/jpeg";
      else if (lower.endsWith(".gif")) ct = "image/gif";
      else if (lower.endsWith(".svg")) ct = "image/svg+xml";

      ctx.contentType(ct);
      ctx.result(content);
    });

    // --- Git Commit Patch Download (Stage 5d) ---
    routes.get("/api/git/commit/patch", ctx -> {
      String hash = ctx.queryParam("hash");
      if (hash == null || hash.isBlank()) { ctx.status(400).result("Missing 'hash' parameter"); return; }

      String patch = gitSvc.generatePatch(hash);
      ctx.contentType("text/plain");
      ctx.header("Content-Disposition", "attachment; filename=" + hash.substring(0, Math.min(8, hash.length())) + ".patch");
      ctx.result(patch);
    });


    // --- Debug ---
    registerDebugRoutes(app);

    log.info("API routes registered");
  }

  private void registerDebugRoutes(Javalin app) {
    var routes = app.unsafe.routes;
    // Start a debug session (launch or attach)
    routes.post("/api/debug/start", ctx -> {
      if (debugMgr == null) { ctx.status(503).json(Map.of("error", "Debug not available")); return; }

      Map<String, Object> body = ctx.bodyAsClass(Map.class);
      String language = body.getOrDefault("language", "java").toString();
      String target = body.getOrDefault("target", "").toString();
      int pid = body.get("pid") instanceof Number n ? n.intValue() : -1;
      @SuppressWarnings("unchecked")
      List<String> args = (List<String>) body.getOrDefault("args", List.of());

      // Attach mode: pid required, target optional
      // Launch mode: target required, pid ignored
      if (pid <= 0 && target.isBlank()) {
        ctx.status(400).json(Map.of("error", "Missing 'target' (or 'pid' for attach mode)"));
        return;
      }

      try {
        DebugSession session = debugMgr.startSession(language, target, args, pid);
        ctx.json(session.stateMap());
      } catch (Exception e) {
        log.error("Debug start failed: {}", e.getMessage(), e);
        ctx.status(500).json(Map.of("error", e.getMessage()));
      }
    });

    // Toggle breakpoint
    routes.post("/api/debug/breakpoint", ctx -> {
      if (debugMgr == null) { ctx.status(503).json(Map.of("error", "Debug not available")); return; }

      Map<String, Object> body = ctx.bodyAsClass(Map.class);
      String sessionId = body.getOrDefault("sessionId", "").toString();
      String filePath = body.getOrDefault("filePath", "").toString();
      int line = ((Number) body.getOrDefault("line", 0)).intValue();

      DebugSession session = debugMgr.getSession(sessionId);
      if (session == null) { ctx.status(404).json(Map.of("error", "Session not found")); return; }

      try {
        boolean ok = session.setBreakpoint(filePath, line);
        ctx.json(Map.of("ok", ok, "breakpoints", session.breakpoints()));
      } catch (Exception e) {
        ctx.status(500).json(Map.of("error", e.getMessage()));
      }
    });

    // Step over/into/out
    routes.post("/api/debug/step", ctx -> {
      if (debugMgr == null) { ctx.status(503).json(Map.of("error", "Debug not available")); return; }

      Map<String, Object> body = ctx.bodyAsClass(Map.class);
      String sessionId = body.getOrDefault("sessionId", "").toString();
      String mode = body.getOrDefault("mode", "over").toString();

      DebugSession session = debugMgr.getSession(sessionId);
      if (session == null) { ctx.status(404).json(Map.of("error", "Session not found")); return; }

      try {
        switch (mode) {
          case "into" -> session.stepInto();
          case "out" -> session.stepOut();
          default -> session.stepOver();
        }
        ctx.json(Map.of("ok", true));
      } catch (Exception e) {
        ctx.status(500).json(Map.of("error", e.getMessage()));
      }
    });

    // Continue execution
    routes.post("/api/debug/continue", ctx -> {
      if (debugMgr == null) { ctx.status(503).json(Map.of("error", "Debug not available")); return; }

      String sessionId = ctx.bodyAsClass(Map.class).getOrDefault("sessionId", "").toString();
      DebugSession session = debugMgr.getSession(sessionId);
      if (session == null) { ctx.status(404).json(Map.of("error", "Session not found")); return; }

      try {
        session.continueExecution();
        ctx.json(Map.of("ok", true));
      } catch (Exception e) {
        ctx.status(500).json(Map.of("error", e.getMessage()));
      }
    });

    // Pause execution
    routes.post("/api/debug/pause", ctx -> {
      if (debugMgr == null) { ctx.status(503).json(Map.of("error", "Debug not available")); return; }

      String sessionId = ctx.bodyAsClass(Map.class).getOrDefault("sessionId", "").toString();
      DebugSession session = debugMgr.getSession(sessionId);
      if (session == null) { ctx.status(404).json(Map.of("error", "Session not found")); return; }

      try {
        session.pause();
        ctx.json(Map.of("ok", true));
      } catch (Exception e) {
        ctx.status(500).json(Map.of("error", e.getMessage()));
      }
    });

    // Inspect variables
    routes.get("/api/debug/variables", ctx -> {
      if (debugMgr == null) { ctx.status(503).json(Map.of("error", "Debug not available")); return; }

      String sessionId = ctx.queryParam("sessionId");
      int frame = 0;
      try { frame = Integer.parseInt(ctx.queryParam("frame")); } catch (Exception ignored) {}

      DebugSession session = debugMgr.getSession(sessionId);
      if (session == null) { ctx.status(404).json(Map.of("error", "Session not found")); return; }

      try {
        ctx.json(session.getVariables(frame));
      } catch (Exception e) {
        ctx.status(500).json(Map.of("error", e.getMessage()));
      }
    });

    // Get session state
    routes.get("/api/debug/state", ctx -> {
      if (debugMgr == null) { ctx.status(503).json(Map.of("error", "Debug not available")); return; }

      String sessionId = ctx.queryParam("sessionId");
      DebugSession session = debugMgr.getSession(sessionId);
      if (session == null) { ctx.status(404).json(Map.of("error", "Session not found")); return; }

      ctx.json(session.stateMap());
    });

    // Stop / terminate session
    routes.post("/api/debug/stop", ctx -> {
      if (debugMgr == null) { ctx.status(503).json(Map.of("error", "Debug not available")); return; }

      String sessionId = ctx.bodyAsClass(Map.class).getOrDefault("sessionId", "").toString();
      debugMgr.stopSession(sessionId);
      ctx.json(Map.of("ok", true));
    });
  }

  static String detectLanguage(String path) {
    if (path.endsWith(".java")) return "java";
    if (path.endsWith(".xml")) return "xml";
    if (path.endsWith(".jsp")) return "jsp";
    if (path.endsWith(".sql")) return "sql";
    if (path.endsWith(".yml") || path.endsWith(".yaml")) return "yaml";
    if (path.endsWith(".properties")) return "properties";
    if (path.endsWith(".sh") || path.endsWith(".bash")) return "shell";
    if (path.endsWith(".kt") || path.endsWith(".kts")) return "kotlin";
    if (path.endsWith(".groovy") || path.endsWith(".gvy")) return "groovy";
    if (path.endsWith(".md")) return "markdown";
    if (path.endsWith(".cpp") || path.endsWith(".cc") || path.endsWith(".cxx")
        || path.endsWith(".hpp") || path.endsWith(".hxx")) {
      return "cpp";
    }
    if (path.endsWith(".h")) return "cpp";
    if (path.endsWith(".c")) return "c";
    if (path.endsWith(".py")) return "python";
    if (path.endsWith(".js") || path.endsWith(".mjs")) return "javascript";
    if (path.endsWith(".ts") || path.endsWith(".tsx")) return "typescript";
    if (path.endsWith(".html") || path.endsWith(".htm")) return "html";
    if (path.endsWith(".css")) return "css";
    if (path.endsWith(".json")) return "json";
    if (path.endsWith(".toml")) return "toml";
    String fileName = path.substring(path.lastIndexOf('/') + 1).toLowerCase();
    if (fileName.startsWith("dockerfile")) return "dockerfile";
    return "";
  }

  /** Re-index all Java files under homeDir (mirrors ServerBootstrap indexing loop). */
  private void reindexAll() throws Exception {
    log.info("Re-index starting under {}", homeDir);

    List<Path> javaFiles = FileWalker.findJavaFiles(homeDir);
    log.info("Re-index: found {} Java files", javaFiles.size());

    JavaParser parser = new JavaParser();
    parser.setCallGraph(callGraph);

    List<ClassSymbol> allSymbols = new ArrayList<>();

    for (int i = 0; i < javaFiles.size(); i++) {
      Path file = javaFiles.get(i);

      List<ClassSymbol> symbols = parser.parse(file);
      for (ClassSymbol cs : symbols) {
        allSymbols.add(cs);
        indexer.indexClass(cs);
      }

      String relPath = homeDir.relativize(file).toString();
      try {
        String content = Files.readString(file);
        indexer.indexFile(relPath, content);
      } catch (Exception e) {
        log.debug("Re-index: skipping raw index for {}: {}", relPath, e.getMessage());
      }

      if ((i + 1) % 100 == 0) {
        log.info("Re-index: {}/{} files", i + 1, javaFiles.size());
      }
    }

    symbolTable.addAll(allSymbols);
    indexer.commit();

    log.info("Re-index complete: {} classes", allSymbols.size());
  }

}
