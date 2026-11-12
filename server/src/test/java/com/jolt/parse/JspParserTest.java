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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JspParserTest {

  @TempDir
  Path tempDir;

  private JspParser parser;

  @BeforeEach
  void setUp() {
    parser = new JspParser();
  }

  private Path writeJsp(String name, String content) throws IOException {
    Path p = tempDir.resolve(name);
    Files.writeString(p, content);
    return p;
  }

  @Test
  void parse_emptyFile_returnsEmptyResult() throws IOException {
    Path p = writeJsp("empty.jsp", "");
    var result = parser.parse(p);
    assertEquals(p, result.filePath());
    assertTrue(result.elements().isEmpty());
    assertTrue(result.includedFiles().isEmpty());
    assertTrue(result.tagLibUris().isEmpty());
  }

  @Test
  void parse_scriptlet_detected() throws IOException {
    Path p = writeJsp("scriptlet.jsp", """
        <html>
        <% int x = 5; %>
        </html>""");

    var result = parser.parse(p);
    List<JspParser.JspElement> scriptlets = result.elements().stream()
        .filter(e -> e.type().equals("scriptlet"))
        .toList();

    assertEquals(1, scriptlets.size());
    assertEquals("int x = 5;", scriptlets.get(0).content());
    assertEquals(2, scriptlets.get(0).line());
  }

  @Test
  void parse_expression_detected() throws IOException {
    Path p = writeJsp("expr.jsp", """
        <p><%= request.getParameter("name") %></p>""");

    var result = parser.parse(p);
    List<JspParser.JspElement> exprs = result.elements().stream()
        .filter(e -> e.type().equals("expression"))
        .toList();

    assertEquals(1, exprs.size());
    assertTrue(exprs.get(0).content().contains("request.getParameter"));
  }

  @Test
  void parse_includeDirective_extractsFile() throws IOException {
    Path p = writeJsp("include.jsp", """
        <%@ include file="header.jsp" %>
        <body>content</body>""");

    var result = parser.parse(p);
    assertEquals(List.of("header.jsp"), result.includedFiles());

    List<JspParser.JspElement> directives = result.elements().stream()
        .filter(e -> e.type().equals("directive"))
        .toList();
    assertEquals(1, directives.size());
    assertEquals("include", directives.get(0).name());
  }

  @Test
  void parse_taglibDirective_extractsUri() throws IOException {
    Path p = writeJsp("taglib.jsp", """
        <%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
        <c:if test="true">yes</c:if>""");

    var result = parser.parse(p);
    assertEquals(List.of("http://java.sun.com/jsp/jstl/core"), result.tagLibUris());

    // Should also detect the c:if custom tag
    List<JspParser.JspElement> tags = result.elements().stream()
        .filter(e -> e.type().equals("tag"))
        .toList();
    assertTrue(tags.stream().anyMatch(t -> t.name().equals("if")));
  }

  @Test
  void parse_pageDirective_extractsImports() throws IOException {
    Path p = writeJsp("imports.jsp", """
        <%@ page import="java.util.List, java.util.Map" %>
        <body></body>""");

    var result = parser.parse(p);
    List<JspParser.JspElement> imports = result.elements().stream()
        .filter(e -> e.type().equals("import"))
        .toList();

    assertEquals(1, imports.size());
    assertTrue(imports.get(0).content().contains("java.util.List"));
  }

  @Test
  void parse_jspIncludeTag_extractsPage() throws IOException {
    Path p = writeJsp("jspinclude.jsp", """
        <jsp:include page="footer.jsp" />""");

    var result = parser.parse(p);
    assertEquals(List.of("footer.jsp"), result.includedFiles());

    List<JspParser.JspElement> tags = result.elements().stream()
        .filter(e -> e.type().equals("tag"))
        .toList();
    assertTrue(tags.stream().anyMatch(t -> t.name().equals("include")));
  }

  @Test
  void parse_useBean_extractsClass() throws IOException {
    Path p = writeJsp("usebean.jsp", """
        <jsp:useBean id="user" class="com.example.User" scope="session" />""");

    var result = parser.parse(p);
    List<JspParser.JspElement> beans = result.elements().stream()
        .filter(e -> e.type().equals("bean-class"))
        .toList();

    assertEquals(1, beans.size());
    assertEquals("com.example.User", beans.get(0).content());
  }

  @Test
  void parse_customTagDetected() throws IOException {
    Path p = writeJsp("customtag.jsp", """
        <myapp:button label="Click" action="submit" />""");

    var result = parser.parse(p);
    List<JspParser.JspElement> tags = result.elements().stream()
        .filter(e -> e.type().equals("tag"))
        .toList();

    assertTrue(tags.stream().anyMatch(t -> t.name().equals("button")));
  }

  @Test
  void parse_multipleElements_lineNumbersCorrect() throws IOException {
    Path p = writeJsp("multi.jsp", """
        <%@ page import="java.io.File" %>
        <% int i = 0; %>
        <%= i %>""");

    var result = parser.parse(p);

    List<JspParser.JspElement> directives = result.elements().stream()
        .filter(e -> e.type().equals("directive")).toList();
    assertEquals(1, directives.size());
    assertEquals(1, directives.get(0).line());

    List<JspParser.JspElement> scriptlets = result.elements().stream()
        .filter(e -> e.type().equals("scriptlet")).toList();
    assertEquals(1, scriptlets.size());
    assertEquals(2, scriptlets.get(0).line());

    List<JspParser.JspElement> exprs = result.elements().stream()
        .filter(e -> e.type().equals("expression")).toList();
    assertEquals(1, exprs.size());
    assertEquals(3, exprs.get(0).line());
  }

  @Test
  void parse_nonExistentFile_returnsEmpty() {
    Path p = tempDir.resolve("does-not-exist.jsp");
    var result = parser.parse(p);
    assertTrue(result.elements().isEmpty());
  }

  @Test
  void parse_htmlTagsNotDetectedAsCustomTags() throws IOException {
    Path p = writeJsp("html.jsp", """
        <html>
        <body>
        <div class="box">hello</div>
        </body>
        </html>""");

    var result = parser.parse(p);
    // No custom tags should be detected (html:, xml: are excluded)
    List<JspParser.JspElement> tags = result.elements().stream()
        .filter(e -> e.type().equals("tag"))
        .toList();
    assertTrue(tags.isEmpty());
  }

}
