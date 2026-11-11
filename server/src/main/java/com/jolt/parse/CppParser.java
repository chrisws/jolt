package com.jolt.parse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jolt.model.ClassSymbol;
import com.jolt.model.FieldSymbol;
import com.jolt.model.MethodSymbol;
import com.jolt.model.SymbolKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses C/C++ source files by shelling out to {@code clang -Xclang -ast-dump=json}
 * and extracting symbols into Jolt's model.
 */
public class CppParser {

  private static final Logger log = LoggerFactory.getLogger(CppParser.class);
  private static final int TIMEOUT_SECONDS = 30;

  private final CompileDb compileDb;

  public CppParser(CompileDb compileDb) {
    this.compileDb = compileDb;
  }

  /** Parse a C/C++ file and extract all top-level type symbols. */
  public List<ClassSymbol> parse(Path filePath) {
    List<String> flags = compileDb.getFlags(filePath.toString());

    String astJson = runClang(filePath, flags);
    if (astJson == null) {
      return List.of();
    }

    return parseAst(astJson, filePath.toString());
  }

  /** Parse a raw clang AST JSON string into symbols. Package-private for testing. */
  List<ClassSymbol> parseAst(String astJson, String filePath) {
    try {
      ObjectMapper mapper = new ObjectMapper();
      JsonNode root = mapper.readTree(astJson);

      List<ClassSymbol> results = new ArrayList<>();
      walkNode(root, "", results, filePath);

      log.debug("Extracted {} symbols from {}", results.size(), filePath);
      return results;

    } catch (IOException e) {
      log.warn("Failed to parse clang AST JSON for {}: {}", filePath, e.getMessage());
      return List.of();
    }
  }

  // ─── Clang subprocess ────────────────────────────────────────────────────────

  /** Spawn clang and capture its stdout (the JSON AST). Returns null on failure. */
  private String runClang(Path filePath, List<String> flags) {
    List<String> cmd = new ArrayList<>();
    cmd.add("clang");
    cmd.add("-Xclang");
    cmd.add("-ast-dump=json");
    cmd.add("-fsyntax-only");

    // Add flags from compile database (include paths, defines, std version, etc.)
    cmd.addAll(flags);

    // The source file last
    cmd.add(filePath.toAbsolutePath().toString());

    try {
      ProcessBuilder pb = new ProcessBuilder(cmd);
      pb.redirectErrorStream(false);

      // Set working directory if available from compile db
      String dir = compileDb.getDirectory(filePath.toString());
      if (dir != null && !dir.isEmpty()) {
        pb.directory(new java.io.File(dir));
      }

      Process process = pb.start();

      // Read stdout (AST JSON) and stderr (diagnostics) concurrently to avoid deadlock
      StringBuilder stdout = new StringBuilder();
      StringBuilder stderr = new StringBuilder();

      Thread stdoutReader = readStream(process.getInputStream(), stdout);
      Thread stderrReader = readStream(process.getErrorStream(), stderr);

      boolean finished = process.waitFor(TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS);

      if (!finished) {
        process.destroyForcibly();
        log.warn("clang timed out after {}s for {}", TIMEOUT_SECONDS, filePath);
        return null;
      }

      stdoutReader.join(5000);
      stderrReader.join(5000);

      int exitCode = process.exitValue();
      if (exitCode != 0) {
        log.debug("clang exited with {} for {}: {}", exitCode, filePath, stderr.toString().trim());
        // Still try to parse partial AST if stdout has content
        if (stdout.length() == 0) {
          return null;
        }
      }

      return stdout.toString();

    } catch (IOException e) {
      log.warn("Failed to spawn clang for {}: {}", filePath, e.getMessage());
      return null;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("Interrupted while waiting for clang on {}", filePath);
      return null;
    }
  }

  private static Thread readStream(java.io.InputStream is, StringBuilder target) {
    Thread t = new Thread(() -> {
      try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
        char[] buf = new char[8192];
        int n;
        while ((n = br.read(buf)) != -1) {
          target.append(buf, 0, n);
        }
      } catch (IOException ignored) {}
    });
    t.setDaemon(true);
    t.start();
    return t;
  }

  // ─── AST Walking ─────────────────────────────────────────────────────────────

  /**
   * Recursively walk the clang JSON AST. {@code namespacePath} accumulates the dot-separated
   * namespace prefix (e.g. "foo.bar"). Results are appended to {@code out}.
   */
  private void walkNode(JsonNode node, String namespacePath, List<ClassSymbol> out, String filePath) {
    if (node == null || !node.isObject()) return;

    String kind = text(node, "kind");

    switch (kind) {
      case "NamespaceDecl" -> {
        String nsName = text(node, "name");
        String childNs = namespacePath.isEmpty() ? nsName : namespacePath + "." + nsName;

        // A namespace itself can be treated as a "class" container for free functions/vars
        JsonNode inner = node.get("inner");
        if (inner != null && inner.isArray()) {
          for (JsonNode child : inner) {
            walkNode(child, childNs, out, filePath);
          }
        }
      }

      case "ClassDecl", "StructDecl", "UnionDecl" -> {
        extractClass(node, namespacePath, out, filePath);
      }

      case "EnumDecl" -> {
        extractEnum(node, namespacePath, out, filePath);
      }

      case "CXXMethodDecl", "FunctionDecl" -> {
        // Free function — attach to the current namespace as a synthetic class entry
        extractFreeFunction(node, namespacePath, out, filePath);
      }

      case "VarDecl" -> {
        // Global variable — attach to current namespace synthetic class
        extractGlobalVar(node, namespacePath, out, filePath);
      }

      default -> {
        // Recurse into unknown containers (e.g. CXXRecordDecl wrappers)
        JsonNode inner = node.get("inner");
        if (inner != null && inner.isArray()) {
          for (JsonNode child : inner) {
            walkNode(child, namespacePath, out, filePath);
          }
        }
      }
    }
  }

  // ─── Symbol Extraction ───────────────────────────────────────────────────────

  private void extractClass(JsonNode node, String nsPath, List<ClassSymbol> out, String filePath) {
    String name = text(node, "name");
    if (name == null || name.isEmpty()) return;

    String fqn = nsPath.isEmpty() ? name : nsPath + "." + name;

    // Determine kind: StructDecl → CLASS (we don't have a STRUCT kind), UnionDecl → CLASS  
    String kindStr = text(node, "kind");  
    SymbolKind symKind = SymbolKind.CLASS;  
    if ("EnumDecl".equals(kindStr)) symKind = SymbolKind.ENUM;  

    ClassSymbol cs = new ClassSymbol(fqn, name, symKind, filePath);

    // Line info from "loc"  
    JsonNode loc = node.get("loc");  
    if (loc != null) {  
      cs.lineStart = loc.path("line").asInt(0);  
      cs.lineEnd = loc.path("line").asInt(0); // will be updated by members  
    }  

    // Superclass: look for "bases" array  
    JsonNode bases = node.get("bases");  
    if (bases != null && bases.isArray() && bases.size() > 0) {  
      String baseType = qualType(bases.get(0));  
      if (baseType != null) cs.superClass = baseType;  
    }  

    // Walk members  
    JsonNode inner = node.get("inner");  
    if (inner != null && inner.isArray()) {
      int maxLine = cs.lineStart;
      for (JsonNode member : inner) {
        String mKind = text(member, "kind");

        if ("CXXMethodDecl".equals(mKind)) {
          MethodSymbol ms = extractMethod(member, fqn);
          if (ms != null) {
            String key = ms.name() + "(" + String.join(",", ms.params()) + ")";
            cs.methods.put(key, ms);
            maxLine = Math.max(maxLine, ms.lineEnd());
          }
        } else if ("VarDecl".equals(mKind)) {
          FieldSymbol fs = extractField(member);
          if (fs != null) {
            cs.fields.put(fs.name(), fs);
            maxLine = Math.max(maxLine, fs.lineEnd());
          }
        } else if ("CXXRecordDecl".equals(mKind) || "ClassDecl".equals(mKind) 
                   || "StructDecl".equals(mKind) || "EnumDecl".equals(mKind)) {
          // Nested type — recurse with this class as pseudo-namespace  
          extractClass(member, fqn, out, filePath);  
        } else if ("FunctionDecl".equals(mKind)) {  
          // Static method or free function inside class body  
          MethodSymbol ms = extractMethod(member, fqn);  
          if (ms != null) {  
            String key = ms.name() + "(" + String.join(",", ms.params()) + ")";  
            cs.methods.put(key, ms);  
            maxLine = Math.max(maxLine, ms.lineEnd());  
          }  
        }
      }
      cs.lineEnd = maxLine;
    }

    out.add(cs);
  }

  private void extractEnum(JsonNode node, String nsPath, List<ClassSymbol> out, String filePath) {
    String name = text(node, "name");
    if (name == null || name.isEmpty()) return;

    String fqn = nsPath.isEmpty() ? name : nsPath + "." + name;
    ClassSymbol cs = new ClassSymbol(fqn, name, SymbolKind.ENUM, filePath);

    JsonNode loc = node.get("loc");
    if (loc != null) cs.lineStart = loc.path("line").asInt(0);
    cs.lineEnd = cs.lineStart;

    // Enum constants as fields
    JsonNode inner = node.get("inner");
    if (inner != null && inner.isArray()) {
      int maxLine = cs.lineStart;
      for (JsonNode member : inner) {
        if ("EnumConstantDecl".equals(text(member, "kind"))) {
          String constName = text(member, "name");
          if (constName != null) {
            int line = member.path("loc").path("line").asInt(0);
            FieldSymbol fs = new FieldSymbol(constName, "enumerator", line, line, "public");
            cs.fields.put(constName, fs);
            maxLine = Math.max(maxLine, line);
          }
        }
      }
      cs.lineEnd = maxLine;
    }

    out.add(cs);
  }

  /** Free function at namespace scope — create a synthetic ClassSymbol to hold it. */  
  private void extractFreeFunction(JsonNode node, String nsPath, List<ClassSymbol> out, String filePath) {  
    String name = text(node, "name");  
    if (name == null || name.isEmpty()) return;  

    // Use the namespace as a synthetic class name (or "<global>" for file scope)  
    String containerFqn = nsPath.isEmpty() ? "<global>" : nsPath;  
    String containerName = nsPath.isEmpty() ? "<global>" : nsPath.substring(nsPath.lastIndexOf('.') + 1);  

    // Find or create the synthetic class in the output list  
    ClassSymbol container = findOrCreate(out, containerFqn, containerName, filePath);  

    MethodSymbol ms = extractMethod(node, containerFqn);  
    if (ms != null) {  
      String key = ms.name() + "(" + String.join(",", ms.params()) + ")";  
      container.methods.put(key, ms);  
      JsonNode loc = node.get("loc");  
      if (loc != null) {
        int line = loc.path("line").asInt(0);
        container.lineEnd = Math.max(container.lineEnd, line);
      }
    }
  }

  /** Global variable at namespace scope. */
  private void extractGlobalVar(JsonNode node, String nsPath, List<ClassSymbol> out, String filePath) {
    String name = text(node, "name");
    if (name == null || name.isEmpty()) return;

    String containerFqn = nsPath.isEmpty() ? "<global>" : nsPath;
    String containerName = nsPath.isEmpty() ? "<global>" : nsPath.substring(nsPath.lastIndexOf('.') + 1);

    ClassSymbol container = findOrCreate(out, containerFqn, containerName, filePath);

    FieldSymbol fs = extractField(node);
    if (fs != null) {
      container.fields.put(fs.name(), fs);
      container.lineEnd = Math.max(container.lineEnd, fs.lineEnd());
    }
  }

  private MethodSymbol extractMethod(JsonNode node, String classFqn) {
    String name = text(node, "name");
    if (name == null || name.isEmpty()) return null;

    // Return type from "type" → "qualType"  
    String returnType = qualType(node.get("type"));  
    if (returnType == null) returnType = "void";  

    // Parameters  
    List<String> params = new ArrayList<>();  
    JsonNode paramsNode = node.get("params");  
    if (paramsNode != null && paramsNode.isArray()) {  
      for (JsonNode p : paramsNode) {  
        String pType = qualType(p.get("type"));  
        params.add(pType != null ? pType : "?");  
      }  
    }  

    // Line info  
    int lineStart = node.path("loc").path("line").asInt(0);  
    int lineEnd = lineStart; // clang JSON doesn't give end line directly; use start as approximation  

    // Access: C++ methods are public by default in classes; check for "access" field if present  
    String access = "public";  

    // Build signature  
    String signature = returnType + " " + name + "(" + String.join(", ", params) + ")";  

    return new MethodSymbol(
        classFqn + "#" + name,
        name,
        signature,
        returnType,
        params,
        lineStart,
        lineEnd,
        access
    );
  }

  private FieldSymbol extractField(JsonNode node) {
    String name = text(node, "name");
    if (name == null || name.isEmpty()) return null;

    String typeStr = qualType(node.get("type"));
    if (typeStr == null) typeStr = "?";

    int line = node.path("loc").path("line").asInt(0);

    return new FieldSymbol(name, typeStr, line, line, "public");
  }

  // ─── Helpers ─────────────────────────────────────────────────────────────────

  /** Find an existing synthetic class in the list or create a new one. */  
  private static ClassSymbol findOrCreate(List<ClassSymbol> out, String fqn, String name, String filePath) {  
    for (ClassSymbol cs : out) {  
      if (cs.fqn.equals(fqn)) return cs;  
    }  
    ClassSymbol cs = new ClassSymbol(fqn, name, SymbolKind.CLASS, filePath);  
    out.add(cs);  
    return cs;  
  }  

  /** Extract the "qualType" string from a clang type node. */  
  private static String qualType(JsonNode typeNode) {  
    if (typeNode == null) return null;  
    // Newer clang: typeNode has "qualType" directly  
    JsonNode qt = typeNode.get("qualType");  
    if (qt != null && qt.isTextual()) return qt.asText();  
    // Fallback: try "type" sub-field (some versions nest it)  
    JsonNode inner = typeNode.get("type");  
    if (inner != null) {
      JsonNode innerQt = inner.get("qualType");
      if (innerQt != null && innerQt.isTextual()) return innerQt.asText();
    }
    return null;
  }

  private static String text(JsonNode node, String field) {
    if (node == null) return null;
    JsonNode child = node.get(field);
    return child != null && child.isTextual() ? child.asText() : null;
  }
}
