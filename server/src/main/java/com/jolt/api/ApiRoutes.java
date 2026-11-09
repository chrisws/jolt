package com.jolt.api;

import com.jolt.index.LuceneIndexer;
import com.jolt.model.*;
import com.jolt.nav.SymbolTable;
import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Wires up all REST endpoints on the Javalin instance. */  
public class ApiRoutes {

  private static final Logger log = LoggerFactory.getLogger(ApiRoutes.class);

  private final SymbolTable symbolTable;
  private final LuceneIndexer indexer;
  private final Path homeDir; // root of the codebase being indexed

  public ApiRoutes(SymbolTable symbolTable, LuceneIndexer indexer, Path homeDir) {
    this.symbolTable = symbolTable;
    this.indexer = indexer;
    this.homeDir = homeDir.toAbsolutePath().normalize();
  }

  /** Register all routes on the given Javalin app. */  
  public void register(Javalin app) {

    // ─── Status ────────────────────────────────────────────── 
    app.get("/api/status", ctx -> {
      Map<String, Object> status = Map.of(
          "classesIndexed", symbolTable.size(),
          "status", "ok"
      );
      ctx.json(status);
    });

    // ─── Search ────────────────────────────────────────────── 
    app.get("/api/search", ctx -> {
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

      List<SearchHit> hits;
      try {
        hits = indexer.search(q, kindFilter, maxResults);
      } catch (Exception e) {
        log.error("Search failed: {}", e.getMessage());
        ctx.status(500).json(Map.of("error", e.getMessage()));
        return;
      }

      ctx.json(hits);
    });

    // ─── File Content ──────────────────────────────────────── 
    app.get("/api/file", ctx -> {
      String path = ctx.queryParam("path");
      if (path == null || path.isBlank()) { ctx.status(400).result("Missing 'path' parameter"); return; }

      Path filePath = homeDir.resolve(path).normalize();

      // Security: ensure resolved path is still under homeDir  
      if (!filePath.startsWith(homeDir)) { ctx.status(403).result("Access denied"); return; }

      if (!Files.exists(filePath)) { ctx.status(404).result("File not found: " + path); return; }

      int startLine = 1;
      int endLine = Integer.MAX_VALUE;
      String startStr = ctx.queryParam("start");  
      if (startStr != null) { try { startLine = Math.max(1, Integer.parseInt(startStr)); } catch (NumberFormatException ignored) {} }  
      String endStr = ctx.queryParam("end");  
      if (endStr != null) { try { endLine = Integer.parseInt(endStr); } catch (NumberFormatException ignored) {} } 

      List<String> allLines = Files.readAllLines(filePath);
      
      // Apply line range
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

    // ─── Go-to-Definition ──────────────────────────────────── 
    app.get("/api/definition", ctx -> {
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

    // ─── Class Hierarchy ───────────────────────────────────── 
    app.get("/api/hierarchy", ctx -> {
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

    // ─── File Tree ──────────────────────────────────────────── 
    app.get("/api/file/tree", ctx -> {
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

    // ─── Find References ────────────────────────────────────── 
    app.get("/api/references", ctx -> {
      String symbol = ctx.queryParam("symbol");  
      if (symbol == null || symbol.isBlank()) { ctx.status(400).result("Missing 'symbol' parameter"); return; }

      var refs = symbolTable.findReferences(symbol);

      List<Map<String, Object>> refList = refs.stream()
          .map(r -> Map.<String, Object>of("filePath", r.filePath(), "line", r.line()))
          .toList();

      ctx.json(refList);
    });

    log.info("API routes registered");
  }

  private static String detectLanguage(String path) {
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
    return "";
  }

}

