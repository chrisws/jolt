package com.jolt.parse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Lightweight JSP parser. Extracts scriptlets, EL expressions, tag library refs,
 * and directive attributes from .jsp files. Not a full JSP engine — just enough
 * for navigation and cross-referencing.
 */
public class JspParser {

  private static final Logger log = LoggerFactory.getLogger(JspParser.class);

  public record JspElement(
      String type,        // "scriptlet", "expression", "directive", "taglib", "tag"
      String content,     // the extracted text
      int line,           // line number in the file
      String name         // for directives: directive name; for tags: tag name; for taglib: URI
  ) {}

  public record JspParseResult(
      Path filePath,
      List<JspElement> elements,
      List<String> includedFiles,   // from <%@ include file="..." %> or <jsp:include>
      List<String> tagLibUris       // from <%@ taglib uri="..." %>
  ) {}

  /** Parse a .jsp file and extract navigable elements. */
  public JspParseResult parse(Path filePath) {
    try {
      String content = Files.readString(filePath, StandardCharsets.UTF_8);
      return doParse(content, filePath);
    } catch (IOException e) {
      log.warn("Failed to read JSP {}: {}", filePath, e.getMessage());
      return new JspParseResult(filePath, List.of(), List.of(), List.of());
    } catch (Exception e) {
      log.debug("JSP parse error in {}: {}", filePath, e.getMessage());
      return new JspParseResult(filePath, List.of(), List.of(), List.of());
    }
  }

  private JspParseResult doParse(String content, Path filePath) {
    List<JspElement> elements = new ArrayList<>();
    List<String> includedFiles = new ArrayList<>();
    List<String> tagLibUris = new ArrayList<>();

    String[] lines = content.split("\n", -1);

    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      int lineNum = i + 1;

      // Directives: <%@ ... %>
      int dirStart = line.indexOf("<%@");
      if (dirStart >= 0) {
        int dirEnd = line.indexOf("%>", dirStart);
        if (dirEnd > dirStart) {
          String directive = line.substring(dirStart + 3, dirEnd).trim();
          handleDirective(directive, lineNum, elements, includedFiles, tagLibUris);
        }
        continue; // skip rest of line after directive (simplification)
      }

      // Scriptlets: <%= ... %> (expression) or <%% ... %> (scriptlet) — actually <% ... %> for scriptlet, <%= ... %> for expression
      // Expression: <%= expr %>
      int exprStart = line.indexOf("<%=");
      if (exprStart >= 0) {
        int exprEnd = line.indexOf("%>", exprStart);
        if (exprEnd > exprStart) {
          String expr = line.substring(exprStart + 3, exprEnd).trim();
          elements.add(new JspElement("expression", expr, lineNum, ""));
        }
        continue;
      }

      // Scriptlet: <% ... %> (but not <%= which we handled above)
      int scriptStart = line.indexOf("<%");
      if (scriptStart >= 0 && !line.substring(scriptStart, scriptStart + 3).equals("<%=")) {
        int scriptEnd = line.indexOf("%>", scriptStart);
        if (scriptEnd > scriptStart) {
          String scriptlet = line.substring(scriptStart + 2, scriptEnd).trim();
          if (!scriptlet.isEmpty() && !scriptlet.startsWith("@")) { // not a directive
            elements.add(new JspElement("scriptlet", scriptlet, lineNum, ""));
          }
        }
        continue;
      }

      // JSP tags: <jsp:include page="..."/>, <jsp:useBean .../> etc.
      int tagStart = line.indexOf("<jsp:");
      if (tagStart >= 0) {
        int tagEnd = line.indexOf(">", tagStart);
        if (tagEnd > tagStart) {
          String tag = line.substring(tagStart + 5, tagEnd).trim();
          String tagName = tag.split("[\\s/>]")[0];

          elements.add(new JspElement("tag", tagName, lineNum, tagName));

          // Extract page attribute from jsp:include / jsp:forward
          if (tagName.equals("include") || tagName.equals("forward")) {
            String pageAttr = extractAttr(tag, "page");
            if (pageAttr != null) includedFiles.add(pageAttr);
          }

          // Extract class attribute from jsp:useBean
          if (tagName.equals("useBean")) {
            String classAttr = extractAttr(tag, "class");
            if (classAttr != null) {
              elements.add(new JspElement("bean-class", classAttr, lineNum, classAttr));
            }
          }
        }
      }

      // Custom tags: <prefix:tagName ...> — detect by pattern <word:word>
      // Simple heuristic: look for <[a-z]+:[a-z]+ patterns that aren't HTML
      var matcher = java.util.regex.Pattern.compile("<([a-z][a-z0-9]*):([a-z][a-z0-9_]*)").matcher(line);
      while (matcher.find()) {
        String prefix = matcher.group(1);
        String tagName = matcher.group(2);
        // Skip common HTML namespaces (html:, xml:) and jsp: (handled above)
        if (!prefix.equals("html") && !prefix.equals("xml") && !prefix.equals("jsp")) {
          elements.add(new JspElement("tag", prefix + ":" + tagName, lineNum, tagName));
        }
      }
    }

    return new JspParseResult(filePath, elements, includedFiles, tagLibUris);
  }

  private void handleDirective(String directive, int lineNum, List<JspElement> elements,
                               List<String> includedFiles, List<String> tagLibUris) {
    // Format: "directiveName attr1=\"val1\" attr2=\"val2\""
    String dirName = directive.split("\\s+")[0];

    elements.add(new JspElement("directive", dirName, lineNum, dirName));

    switch (dirName) {
      case "include" -> {
        String file = extractAttr(directive, "file");
        if (file != null) includedFiles.add(file);
      }
      case "taglib" -> {
        String uri = extractAttr(directive, "uri");
        if (uri != null) tagLibUris.add(uri);
        String prefix = extractAttr(directive, "prefix");
        if (prefix != null) {
          elements.add(new JspElement("taglib-prefix", prefix, lineNum, prefix));
        }
      }
      case "page" -> {
        // Extract import attributes for class references
        var importMatcher = java.util.regex.Pattern.compile("import\\s*=\\s*\"([^\"]+)\"").matcher(directive);
        while (importMatcher.find()) {
          elements.add(new JspElement("import", importMatcher.group(1), lineNum, importMatcher.group(1)));
        }
      }
      default -> { /* unknown directive — just record it */ }
    }
  }

  /** Extract a quoted attribute value from a tag/directive string. */
  private static String extractAttr(String tagStr, String attrName) {
    var pattern = java.util.regex.Pattern.compile(attrName + "\\s*=\\s*\"([^\"]*)\"");
    var matcher = pattern.matcher(tagStr);
    if (matcher.find()) return matcher.group(1);

    // Try single quotes
    var pattern2 = java.util.regex.Pattern.compile(attrName + "\\s*=\\s*'([^']*)'");
    var matcher2 = pattern2.matcher(tagStr);
    if (matcher2.find()) return matcher2.group(1);

    return null;
  }

}
