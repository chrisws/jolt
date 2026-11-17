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
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JsParserTest {

  private final JsParser parser = new JsParser();

  // ─── Class Parsing ─────────────────────────────────────────────────────────

  @Test
  void parseLines_simpleClass_withMethodsAndFields() {
    List<String> lines = List.of(
        "class Widget {",                          // 1
        "  width = 10;",                            // 2
        "  height: number = 20;",                   // 3
        "  resize(w: number, h: number) {",         // 4
        "    this.width = w;",                      // 5
        "    this.height = h;",                     // 6
        "  }",                                      // 7
        "  area() {",                               // 8
        "    return this.width * this.height;",     // 9
        "  }"                                       // 10
    );

    List<ClassSymbol> result = parser.parseLines(lines, "src.widget", "src/widget.js");

    assertEquals(1, result.size());
    ClassSymbol cs = result.get(0);

    assertEquals("src.widget.Widget", cs.fqn);
    assertEquals("Widget", cs.name);
    assertEquals(SymbolKind.CLASS, cs.kind);
    assertEquals("src/widget.js", cs.filePath);

    // Fields
    assertTrue(cs.fields.containsKey("width"));
    assertTrue(cs.fields.containsKey("height"));

    // Methods
    assertTrue(cs.methods.containsKey("resize"));
    assertTrue(cs.methods.containsKey("area"));

    MethodSymbol resize = cs.methods.get("resize");
    assertEquals(List.of("w", "h"), resize.params());

    MethodSymbol area = cs.methods.get("area");
    assertEquals(List.of(), area.params());
  }

  @Test
  void parseLines_classWithExtendsAndImplements() {
    List<String> lines = List.of(
        "class Animal extends Base implements Serializable, Cloneable {",  // 1
        "  name: string;",                                                  // 2
        "  speak() {",                                                      // 3
        "    return 'hello';",                                              // 4
        "  }"                                                               // 5
    );

    List<ClassSymbol> result = parser.parseLines(lines, "src.animal", "src/animal.ts");

    assertEquals(1, result.size());
    ClassSymbol cs = result.get(0);

    assertEquals("Base", cs.superClass);
    assertTrue(cs.interfaces.contains("Serializable"));
    assertTrue(cs.interfaces.contains("Cloneable"));
  }

  @Test
  void parseLines_classWithArrowFunctionProperty() {
    List<String> lines = List.of(
        "class Handler {",                    // 1
        "  onClick = (e: Event) => {",        // 2
        "    console.log(e);",                // 3
        "  };",                               // 4
        "}"                                   // 5
    );

    List<ClassSymbol> result = parser.parseLines(lines, "src.handler", "src/handler.js");

    assertEquals(1, result.size());
    ClassSymbol cs = result.get(0);

    assertTrue(cs.methods.containsKey("onClick"));
    MethodSymbol onClick = cs.methods.get("onClick");
    assertEquals(List.of("e"), onClick.params());
  }

  @Test
  void parseLines_classWithAsyncMethod() {
    List<String> lines = List.of(
        "class Service {",              // 1
        "  async fetchData(url: string) {", // 2
        "    return fetch(url);",       // 3
        "  }"                           // 4
    );

    List<ClassSymbol> result = parser.parseLines(lines, "src.service", "src/service.ts");

    assertEquals(1, result.size());
    ClassSymbol cs = result.get(0);

    assertTrue(cs.methods.containsKey("fetchData"));
    assertEquals(List.of("url"), cs.methods.get("fetchData").params());
  }

  // ─── Free Functions ────────────────────────────────────────────────────────

  @Test
  void parseLines_freeFunctionDeclaration() {
    List<String> lines = List.of(
        "function greet(name: string, punct: string) {",  // 1
        "  return name + punct;",                         // 2
        "}"                                               // 3
    );

    List<ClassSymbol> result = parser.parseLines(lines, "src.utils", "src/utils.js");

    assertEquals(1, result.size());
    ClassSymbol mod = result.get(0);

    assertEquals("src.utils", mod.fqn);
    assertEquals(SymbolKind.FILE, mod.kind);

    assertTrue(mod.methods.containsKey("greet"));
    MethodSymbol greet = mod.methods.get("greet");
    assertEquals(List.of("name", "punct"), greet.params());
  }

  @Test
  void parseLines_asyncFunctionDeclaration() {
    List<String> lines = List.of(
        "async function load(url) {",   // 1
        "  return fetch(url);",         // 2
        "}"                             // 3
    );

    List<ClassSymbol> result = parser.parseLines(lines, "src.api", "src/api.js");

    assertEquals(1, result.size());
    assertTrue(result.get(0).methods.containsKey("load"));
  }

  @Test
  void parseLines_arrowFunctionAssignment() {
    List<String> lines = List.of(
        "const add = (a: number, b: number) => {",  // 1
        "  return a + b;",                          // 2
        "};"                                        // 3
    );

    List<ClassSymbol> result = parser.parseLines(lines, "src.math", "src/math.ts");

    assertEquals(1, result.size());
    assertTrue(result.get(0).methods.containsKey("add"));

    MethodSymbol add = result.get(0).methods.get("add");
    assertEquals(List.of("a", "b"), add.params());
  }

  @Test
  void parseLines_exportedFunction() {
    List<String> lines = List.of(
        "export function main() {",   // 1
        "  console.log('hi');",       // 2
        "}"                           // 3
    );

    List<ClassSymbol> result = parser.parseLines(lines, "src.main", "src/main.js");

    assertEquals(1, result.size());
    assertTrue(result.get(0).methods.containsKey("main"));
  }

  @Test
  void parseLines_multipleFreeFunctions_shareModuleContainer() {
    List<String> lines = List.of(
        "function foo() {",     // 1
        "}",                    // 2
        "function bar(x) {",   // 3
        "}",                    // 4
        "const baz = (y) => {}" // 5
    );

    List<ClassSymbol> result = parser.parseLines(lines, "src.multi", "src/multi.js");

    assertEquals(1, result.size());
    ClassSymbol mod = result.get(0);

    assertTrue(mod.methods.containsKey("foo"));
    assertTrue(mod.methods.containsKey("bar"));
    assertTrue(mod.methods.containsKey("baz"));
  }

  // ─── TypeScript Specific ───────────────────────────────────────────────────

  @Test
  void parseLines_typescriptInterface() {
    List<String> lines = List.of(
        "interface User {",              // 1
        "  id: number;",                 // 2
        "  name: string;",               // 3
        "}"                              // 4
    );

    List<ClassSymbol> result = parser.parseLines(lines, "src.types", "src/types.ts");

    assertEquals(1, result.size());
    ClassSymbol cs = result.get(0);

    assertEquals("src.types.User", cs.fqn);
    assertEquals(SymbolKind.INTERFACE, cs.kind);
  }

  @Test
  void parseLines_typescriptEnum() {
    List<String> lines = List.of(
        "enum Direction {",   // 1
        "  UP = 1,",          // 2
        "  DOWN = -1,",       // 3
        "  LEFT = 0",         // 4
        "}"                   // 5
    );

    List<ClassSymbol> result = parser.parseLines(lines, "src.dir", "src/dir.ts");

    assertEquals(1, result.size());
    ClassSymbol cs = result.get(0);

    assertEquals("src.dir.Direction", cs.fqn);
    assertEquals(SymbolKind.ENUM, cs.kind);

    assertTrue(cs.fields.containsKey("UP"));
    assertTrue(cs.fields.containsKey("DOWN"));
    assertTrue(cs.fields.containsKey("LEFT"));
  }

  @Test
  void parseLines_exportedClass() {
    List<String> lines = List.of(
        "export class Foo {",   // 1
        "  bar() {}",           // 2
        "}"                     // 3
    );

    List<ClassSymbol> result = parser.parseLines(lines, "src.foo", "src/foo.js");

    assertEquals(1, result.size());
    assertEquals("src.foo.Foo", result.get(0).fqn);
    assertTrue(result.get(0).methods.containsKey("bar"));
  }

  // ─── Edge Cases ────────────────────────────────────────────────────────────

  @Test
  void parseLines_emptyFile() {
    List<ClassSymbol> result = parser.parseLines(List.of(), "src.empty", "src/empty.js");
    assertTrue(result.isEmpty());
  }

  @Test
  void parseLines_onlyComments() {
    List<String> lines = List.of(
        "// just a comment",
        "/* block comment */",
        ""
    );

    List<ClassSymbol> result = parser.parseLines(lines, "src.comments", "src/comments.js");
    assertTrue(result.isEmpty());
  }

  @Test
  void parseLines_importStatementsIgnored() {
    List<String> lines = List.of(
        "import { useState } from 'react';",   // 1
        "import React from 'react';",          // 2
        "",                                     // 3
        "function App() {",                     // 4
        "  return null;",                       // 5
        "}"                                     // 6
    );

    List<ClassSymbol> result = parser.parseLines(lines, "src.app", "src/app.jsx");

    assertEquals(1, result.size());
    assertTrue(result.get(0).methods.containsKey("App"));
  }

  @Test
  void parseLines_classWithStaticMethod() {
    List<String> lines = List.of(
        "class Utils {",                    // 1
        "  static create() {",              // 2
        "    return new Utils();",          // 3
        "  }"                               // 4
    );

    List<ClassSymbol> result = parser.parseLines(lines, "src.utils2", "src/utils2.js");

    assertEquals(1, result.size());
    assertTrue(result.get(0).methods.containsKey("create"));
  }

  @Test
  void parseLines_functionWithDefaultParams() {
    List<String> lines = List.of(
        "function greet(name = 'world', punct = '!') {",  // 1
        "  return name + punct;",                         // 2
        "}"                                               // 3
    );

    List<ClassSymbol> result = parser.parseLines(lines, "src.greet", "src/greet.js");

    assertEquals(1, result.size());
    MethodSymbol greet = result.get(0).methods.get("greet");
    assertNotNull(greet);

    // Default values should be stripped, leaving just param names
    assertEquals(List.of("name", "punct"), greet.params());
  }

  @Test
  void parseLines_functionWithRestParams() {
    List<String> lines = List.of(
        "function sum(...args) {",   // 1
        "  return args.reduce((a, b) => a + b, 0);", // 2
        "}"                          // 3
    );

    List<ClassSymbol> result = parser.parseLines(lines, "src.sum", "src/sum.js");

    assertEquals(1, result.size());
    MethodSymbol sum = result.get(0).methods.get("sum");
    assertNotNull(sum);

    assertEquals(List.of("args"), sum.params());
  }

  // ─── Module FQN ────────────────────────────────────────────────────────────

  @Test
  void toModuleFqn_simpleFilename() {
    var path = java.nio.file.Path.of("src/utils/helpers.js");
    assertEquals("src.utils.helpers", JsParser.toModuleFqn(path));
  }

  @Test
  void toModuleFqn_typescriptExtension() {
    var path = java.nio.file.Path.of("src/components/Button.tsx");
    assertEquals("src.components.Button", JsParser.toModuleFqn(path));
  }

  @Test
  void toModuleFqn_rootLevelFile() {
    var path = java.nio.file.Path.of("index.js");
    assertEquals("index", JsParser.toModuleFqn(path));
  }

  @Test
  void toModuleFqn_deepNesting() {
    var path = java.nio.file.Path.of("a/b/c/d/e.js");
    assertEquals("a.b.c.d.e", JsParser.toModuleFqn(path));
  }
}
