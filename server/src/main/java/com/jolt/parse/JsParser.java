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

import com.jolt.model.ClassSymbol;
import com.jolt.model.FieldSymbol;
import com.jolt.model.MethodSymbol;
import com.jolt.model.SymbolKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Basic regex-based parser for JavaScript/TypeScript source files.
 * Extracts classes, interfaces, enums, functions, and fields into Jolt's symbol model.
 */
public class JsParser {

  private static final Logger log = LoggerFactory.getLogger(JsParser.class);

  // ─── Patterns ──────────────────────────────────────────────────────────────

  private static final Pattern CLASS_RE = Pattern.compile(
      "^(?:export\\s+)?(?:abstract\\s+)?class\\s+(\\w+)(?:\\s+extends\\s+(\\w+))?(?:\\s+implements\\s+([\\w,\\s]+))?\\s*\\{");

  private static final Pattern INTERFACE_RE = Pattern.compile(
      "^(?:export\\s+)?interface\\s+(\\w+)(?:\\s+extends\\s+([\\w,\\s]+))?\\s*\\{");

  private static final Pattern ENUM_RE = Pattern.compile(
      "^(?:export\\s+)?enum\\s+(\\w+)\\s*\\{");

  private static final Pattern FUNCTION_RE = Pattern.compile(
      "^(?:export\\s+)?(?:async\\s+)?function\\s+(\\w+)\\s*\\(([^)]*)\\)");

  private static final Pattern ARROW_RE = Pattern.compile(
      "^(?:export\\s+)?(?:const|let|var)\\s+(\\w+)\\s*=.*?=>");

  private static final Pattern CLASS_METHOD_RE = Pattern.compile(
      "^(?:async|static|get|set|public|private|protected|readonly|abstract|override)*" +
      "\\s*(?:async)?(?!if\\b|for\\b|while\\b|switch\\b|catch\\b|return\\b)" +
      "([a-zA_$][\\w$]*)\\s*\\(([^)]*)\\)");

  private static final Pattern CLASS_ARROW_PROP_RE = Pattern.compile(
      "^(?:public|private|protected|readonly)*" +
      "\\s*(?:async)?([a-zA_$][\\w$]*)\\s*=.*?=>");

  private static final Pattern CLASS_FIELD_RE = Pattern.compile(
      "^(?:public|private|protected|readonly)*" +
      "\\s*([a-zA_$][\\w$]*)\\s*(?::\\s*[\\w<>,\\[\\]\\s]+)?=?.*;?$");

  private static final Pattern IMPORT_RE = Pattern.compile(
      "^import\\s+.*from\\s+['\"]([^'\"]+)['\"]");

  // ─── Public API ────────────────────────────────────────────────────────────

  /** Parse a JS/TS file and extract all top-level symbols. */
  public List<ClassSymbol> parse(Path filePath) {
    try {
      List<String> lines = java.nio.file.Files.readAllLines(filePath);
      String moduleFqn = toModuleFqn(filePath);
      return parseLines(lines, moduleFqn, filePath.toString());
    } catch (Exception e) {
      log.warn("Failed to parse {}: {}", filePath, e.getMessage());
      return List.of();
    }
  }

  /** Parse raw lines for testing. Package-private. */
  List<ClassSymbol> parseLines(List<String> lines, String moduleFqn, String filePath) {
    List<ClassSymbol> results = new ArrayList<>();
    int depth = 0;

    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i).strip();

      if (line.isEmpty() || line.startsWith("//") || line.startsWith("/*") || line.startsWith("*")) {
        continue;
      }

      // Track brace depth for top-level detection
      depth += countBraces(line);

      if (depth > 1) {
        continue; // inside a nested block, skip
      }

      // Try class/interface/enum at top level (depth == 1 means we just opened the brace)
      ClassSymbol cs = tryMatchClass(line, i + 1, moduleFqn, filePath);
      if (cs != null) {
        results.add(cs);
        parseClassBody(lines, i + 1, cs);
        continue;
      }

      cs = tryMatchInterface(line, i + 1, moduleFqn, filePath);
      if (cs != null) {
        results.add(cs);
        continue;
      }

      cs = tryMatchEnum(line, i + 1, moduleFqn, filePath);
      if (cs != null) {
        results.add(cs);
        parseEnumBody(lines, i + 1, cs);
        continue;
      }

      // Free functions and arrow functions at top level
      MethodSymbol fn = tryMatchFunction(line, i + 1, moduleFqn);
      if (fn != null) {
        addToModule(results, moduleFqn, filePath).methods.put(fn.name(), fn);
        continue;
      }

      fn = tryMatchArrow(line, i + 1, moduleFqn);
      if (fn != null) {
        addToModule(results, moduleFqn, filePath).methods.put(fn.name(), fn);
      }
    }

    log.debug("Extracted {} symbols from {}", results.size(), filePath);
    return results;
  }

  // ─── Matchers ──────────────────────────────────────────────────────────────

  private ClassSymbol tryMatchClass(String line, int lineNo, String moduleFqn, String filePath) {
    Matcher m = CLASS_RE.matcher(line);
    if (!m.find()) return null;

    String name = m.group(1);
    String fqn = moduleFqn + "." + name;
    ClassSymbol cs = new ClassSymbol(fqn, name, SymbolKind.CLASS, filePath);
    cs.lineStart = lineNo;

    if (m.group(2) != null) {
      cs.superClass = m.group(2);
    }
    if (m.group(3) != null) {
      for (String iface : m.group(3).split(",")) {
        String trimmed = iface.strip();
        if (!trimmed.isEmpty()) cs.interfaces.add(trimmed);
      }
    }
    return cs;
  }

  private ClassSymbol tryMatchInterface(String line, int lineNo, String moduleFqn, String filePath) {
    Matcher m = INTERFACE_RE.matcher(line);
    if (!m.find()) return null;

    String name = m.group(1);
    String fqn = moduleFqn + "." + name;
    ClassSymbol cs = new ClassSymbol(fqn, name, SymbolKind.INTERFACE, filePath);
    cs.lineStart = lineNo;

    if (m.group(2) != null) {
      for (String ext : m.group(2).split(",")) {
        String trimmed = ext.strip();
        if (!trimmed.isEmpty()) cs.interfaces.add(trimmed);
      }
    }
    return cs;
  }

  private ClassSymbol tryMatchEnum(String line, int lineNo, String moduleFqn, String filePath) {
    Matcher m = ENUM_RE.matcher(line);
    if (!m.find()) return null;

    String name = m.group(1);
    String fqn = moduleFqn + "." + name;
    ClassSymbol cs = new ClassSymbol(fqn, name, SymbolKind.ENUM, filePath);
    cs.lineStart = lineNo;
    return cs;
  }

  private MethodSymbol tryMatchFunction(String line, int lineNo, String moduleFqn) {
    Matcher m = FUNCTION_RE.matcher(line);
    if (!m.find()) return null;

    String name = m.group(1);
    List<String> params = parseParams(m.group(2));
    String fqn = moduleFqn + "#" + name;

    return new MethodSymbol(fqn, name, name + "(" + String.join(", ", params) + ")", "void", params, lineNo, lineNo, "public");
  }

  private MethodSymbol tryMatchArrow(String line, int lineNo, String moduleFqn) {
    Matcher m = ARROW_RE.matcher(line);
    if (!m.find()) return null;

    String name = m.group(1);
    // Extract params from the arrow expression — look for ( ... ) before =>
    List<String> params = extractArrowParams(line);
    String fqn = moduleFqn + "#" + name;

    return new MethodSymbol(fqn, name, name + "(" + String.join(", ", params) + ")", "void", params, lineNo, lineNo, "public");
  }

  // ─── Class Body Parsing ────────────────────────────────────────────────────

  private void parseClassBody(List<String> lines, int startIdx, ClassSymbol cs) {
    int depth = 1; // we're inside the class brace
    int endLine = startIdx;

    for (int i = startIdx; i < lines.size(); i++) {
      String raw = lines.get(i);
      String line = raw.strip();

      // Check for methods/fields at class-body level BEFORE updating depth,
      // because a method line like `foo() {` increments depth.
      if (depth == 1 && !line.isEmpty() && !line.startsWith("//")) {
        endLine = i + 1;

        MethodSymbol ms = tryMatchClassMethod(line, i + 1, cs);
        if (ms != null) {
          cs.methods.put(ms.name(), ms);
        } else {
          FieldSymbol fs = tryMatchClassField(line, i + 1);
          if (fs != null) {
            cs.fields.put(fs.name(), fs);
          }
        }
      }

      depth += countBraces(line);

      if (depth <= 0) {
        cs.lineEnd = i + 1;
        return;
      }
    }

    cs.lineEnd = endLine;
  }

  private MethodSymbol tryMatchClassMethod(String line, int lineNo, ClassSymbol cs) {
    // Try arrow property first (e.g., `foo = (a, b) => {`)
    Matcher arrowM = CLASS_ARROW_PROP_RE.matcher(line);
    if (arrowM.find()) {
      String name = arrowM.group(1);
      List<String> params = extractArrowParams(line);
      String fqn = cs.fqn + "#" + name;
      return new MethodSymbol(fqn, name, name + "(" + String.join(", ", params) + ")", "void", params, lineNo, lineNo, "public");
    }

    // Try regular method (e.g., `foo(a: string): void {`)
    Matcher m = CLASS_METHOD_RE.matcher(line);
    if (!m.find()) return null;

    String name = m.group(1);
    // Skip keywords that look like methods but aren't
    if (isJsKeyword(name)) return null;

    List<String> params = parseParams(m.group(2));
    String fqn = cs.fqn + "#" + name;

    return new MethodSymbol(fqn, name, name + "(" + String.join(", ", params) + ")", "void", params, lineNo, lineNo, "public");
  }

  private FieldSymbol tryMatchClassField(String line, int lineNo) {
    // Must end with ; or = to distinguish from method signatures
    if (!line.endsWith(";") && !line.contains("=")) return null;

    Matcher m = CLASS_FIELD_RE.matcher(line);
    if (!m.find()) return null;

    String name = m.group(1);
    if (isJsKeyword(name)) return null;

    // Extract type annotation if present (e.g., `name: string`)
    String type = extractTypeAnnotation(line);

    return new FieldSymbol(name, type, lineNo, lineNo, "public");
  }

  private void parseEnumBody(List<String> lines, int startIdx, ClassSymbol cs) {
    int depth = 1;

    for (int i = startIdx; i < lines.size(); i++) {
      String line = lines.get(i).strip();
      depth += countBraces(line);

      if (depth <= 0) {
        cs.lineEnd = i + 1;
        return;
      }

      if (depth == 1 && !line.isEmpty() && !line.startsWith("//")) {
        cs.lineEnd = i + 1;
        // Enum members: `FOO = 1` or just `FOO`
        String memberName = line.split("[=,]")[0].strip();
        if (!memberName.isEmpty() && memberName.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '_')) {
          cs.fields.put(memberName, new FieldSymbol(memberName, "enumerator", i + 1, i + 1, "public"));
        }
      }
    }
  }

  // ─── Helpers ───────────────────────────────────────────────────────────────

  /** Convert a file path to a dot-separated module FQN (e.g., "src/utils/helpers.js" → "src.utils.helpers"). */
  static String toModuleFqn(Path filePath) {
    String name = filePath.getFileName().toString();
    // Strip extension
    int dot = name.lastIndexOf('.');
    if (dot > 0) name = name.substring(0, dot);

    // Build path without the filename, replace path separators with dots
    Path parent = filePath.getParent();
    if (parent == null) return name;

    String dir = parent.toString().replace('/', '.').replace('\\', '.');
    // Remove leading dots from absolute paths
    while (dir.startsWith(".")) {
      dir = dir.substring(1);
    }

    return dir.isEmpty() ? name : dir + "." + name;
  }

  private static int countBraces(String line) {
    int count = 0;
    for (char c : line.toCharArray()) {
      if (c == '{') count++;
      else if (c == '}') count--;
    }
    return count;
  }

  private static List<String> parseParams(String paramStr) {
    if (paramStr == null || paramStr.strip().isEmpty()) return List.of();

    List<String> params = new ArrayList<>();
    for (String p : paramStr.split(",")) {
      String trimmed = p.strip();
      if (trimmed.isEmpty()) continue;

      // Strip default values: `x = 5` → `x`
      int eq = trimmed.indexOf('=');
      if (eq > 0) trimmed = trimmed.substring(0, eq).strip();

      // Strip type annotations: `x: string` → `x`
      int colon = trimmed.indexOf(':');
      if (colon > 0) trimmed = trimmed.substring(0, colon).strip();

      // Strip rest/spread: `...args` → `args`
      if (trimmed.startsWith("...")) trimmed = trimmed.substring(3);

      params.add(trimmed);
    }
    return params;
  }

  private static List<String> extractArrowParams(String line) {
    // Find the last ( ... ) before => 
    int arrowIdx = line.indexOf("=>");
    if (arrowIdx < 0) return List.of();

    String before = line.substring(0, arrowIdx);
    int openParen = before.lastIndexOf('(');
    int closeParen = before.lastIndexOf(')');

    if (openParen < 0 || closeParen < openParen) return List.of();

    String paramStr = before.substring(openParen + 1, closeParen);
    return parseParams(paramStr);
  }

  private static String extractTypeAnnotation(String line) {
    // Look for `: type` pattern before `=` or `;`
    int colonIdx = line.indexOf(':');
    if (colonIdx < 0) return "unknown";

    int endIdx = line.indexOf('=', colonIdx);
    if (endIdx < 0) endIdx = line.indexOf(';', colonIdx);
    if (endIdx < 0) endIdx = line.length();

    String type = line.substring(colonIdx + 1, endIdx).strip();
    return type.isEmpty() ? "unknown" : type;
  }

  private static boolean isJsKeyword(String word) {
    return switch (word) {
      case "if", "for", "while", "switch", "catch", "return", "function", "class",
           "new", "delete", "typeof", "instanceof", "throw", "try", "finally",
           "do", "else", "case", "break", "continue", "const", "let", "var" -> true;
      default -> false;
    };
  }

  /** Find or create the synthetic module container in the results list. */
  private static ClassSymbol addToModule(List<ClassSymbol> results, String moduleFqn, String filePath) {
    for (ClassSymbol cs : results) {
      if (cs.fqn.equals(moduleFqn)) return cs;
    }
    ClassSymbol mod = new ClassSymbol(moduleFqn, "<module>", SymbolKind.FILE, filePath);
    results.add(mod);
    return mod;
  }
}
