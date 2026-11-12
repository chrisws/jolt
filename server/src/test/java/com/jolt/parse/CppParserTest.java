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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CppParserTest {

  private CppParser parser;

  @BeforeEach
  void setUp() {
    // CompileDb not needed for parseAst tests — pass a stub  
    CompileDb db = new CompileDb();  
    parser = new CppParser(db);  
  }  

  @Test  
  void parseAst_simpleClass_withMethodsAndFields() {  
    String json = """  
        {  
          "kind": "TranslationUnitDecl",  
          "loc": {"offset": 0, "path": "/src/test.cpp", "line": 1, "column": 1},  
          "inner": [  
            {  
              "kind": "ClassDecl",  
              "name": "Widget",  
              "loc": {"offset": 10, "path": "/src/test.cpp", "line": 3, "column": 8},  
              "inner": [  
                {  
                  "kind": "FieldDecl",  
                  "name": "width",  
                  "type": {"qualType": "int"},  
                  "loc": {"offset": 30, "path": "/src/test.cpp", "line": 4, "column": 10}  
                },  
                {  
                  "kind": "VarDecl",  
                  "name": "_count",  
                  "type": {"qualType": "int"},  
                  "loc": {"offset": 50, "path": "/src/test.cpp", "line": 5, "column": 10}  
                },  
                {  
                  "kind": "CXXMethodDecl",  
                  "name": "resize",  
                  "type": {"qualType": "void"},  
                  "params": [
                    {"name": "w", "type": {"qualType": "int"}},
                    {"name": "h", "type": {"qualType": "int"}}
                  ],
                  "loc": {"offset": 80, "path": "/src/test.cpp", "line": 7, "column": 10}
                },
                {
                  "kind": "CXXMethodDecl",
                  "name": "area",
                  "type": {"qualType": "double"},
                  "params": [],
                  "loc": {"offset": 120, "path": "/src/test.cpp", "line": 12, "column": 10}
                }
              ]
            }
          ]
        }
        """;

    List<ClassSymbol> symbols = parser.parseAst(json, "/src/test.cpp");

    assertEquals(1, symbols.size());

    ClassSymbol cs = symbols.get(0);
    assertEquals("Widget", cs.name);
    assertEquals("Widget", cs.fqn);
    assertEquals(SymbolKind.CLASS, cs.kind);
    assertEquals("/src/test.cpp", cs.filePath);

    // Fields  
    assertTrue(cs.fields.containsKey("_count"));  
    FieldSymbol countField = cs.fields.get("_count");  
    assertEquals("int", countField.type());  

    // Methods  
    assertTrue(cs.methods.containsKey("resize(int,int)"));  
    MethodSymbol resize = cs.methods.get("resize(int,int)");  
    assertEquals("void", resize.returnType());  
    assertEquals(List.of("int", "int"), resize.params());  
    assertEquals("Widget#resize", resize.fqn());  

    assertTrue(cs.methods.containsKey("area()"));  
    MethodSymbol area = cs.methods.get("area()");  
    assertEquals("double", area.returnType());  
  }  

  @Test  
  void parseAst_namespace_withClass() {  
    String json = """  
        {  
          "kind": "TranslationUnitDecl",  
          "inner": [  
            {  
              "kind": "NamespaceDecl",  
              "name": "graphics",  
              "inner": [  
                {  
                  "kind": "NamespaceDecl",  
                  "name": "render",  
                  "inner": [  
                    {
                      "kind": "ClassDecl",
                      "name": "Renderer",
                      "loc": {"line": 5},
                      "inner": [
                        {
                          "kind": "CXXMethodDecl",
                          "name": "draw",
                          "type": {"qualType": "void"},
                          "params": [{"name": "x", "type": {"qualType": "int"}}],
                          "loc": {"line": 6}
                        }
                      ]
                    }
                  ]
                }
              ]
            }
          ]
        }
        """;

    List<ClassSymbol> symbols = parser.parseAst(json, "/src/render.cpp");

    assertEquals(1, symbols.size());

    ClassSymbol cs = symbols.get(0);
    assertEquals("graphics.render.Renderer", cs.fqn);
    assertEquals("Renderer", cs.name);

    MethodSymbol draw = cs.methods.get("draw(int)");
    assertNotNull(draw);
    assertEquals("graphics.render.Renderer#draw", draw.fqn());
  }

  @Test  
  void parseAst_freeFunction_atGlobalScope() {  
    String json = """  
        {  
          "kind": "TranslationUnitDecl",  
          "inner": [  
            {  
              "kind": "FunctionDecl",  
              "name": "computeHash",  
              "type": {"qualType": "uint64_t"},  
              "params": [  
                {"name": "data", "type": {"qualType": "const char*"}},  
                {"name": "len", "type": {"qualType": "size_t"}}  
              ],  
              "loc": {"line": 10}  
            }  
          ]  
        }
        """;

    List<ClassSymbol> symbols = parser.parseAst(json, "/src/hash.cpp");

    // Free function goes into a synthetic "<global>" container
    assertEquals(1, symbols.size());

    ClassSymbol global = symbols.get(0);
    assertEquals("<global>", global.fqn);

    MethodSymbol fn = global.methods.get("computeHash(const char*,size_t)");
    assertNotNull(fn);
    assertEquals("uint64_t", fn.returnType());
    assertEquals(List.of("const char*", "size_t"), fn.params());
  }

  @Test
  void parseAst_enum_withConstants() {
    String json = """
        {
          "kind": "TranslationUnitDecl",
          "inner": [
            {
              "kind": "EnumDecl",
              "name": "Color",
              "loc": {"line": 2},
              "inner": [
                {"kind": "EnumConstantDecl", "name": "RED",   "loc": {"line": 3}},
                {"kind": "EnumConstantDecl", "name": "GREEN", "loc": {"line": 4}},
                {"kind": "EnumConstantDecl", "name": "BLUE",  "loc": {"line": 5}}
              ]
            }
          ]
        }
        """;

    List<ClassSymbol> symbols = parser.parseAst(json, "/src/color.cpp");

    assertEquals(1, symbols.size());

    ClassSymbol cs = symbols.get(0);
    assertEquals("Color", cs.name);
    assertEquals(SymbolKind.ENUM, cs.kind);

    assertEquals(3, cs.fields.size());
    assertTrue(cs.fields.containsKey("RED"));
    assertTrue(cs.fields.containsKey("GREEN"));
    assertTrue(cs.fields.containsKey("BLUE"));

    // Line range should span the constants  
    assertEquals(2, cs.lineStart);  
    assertEquals(5, cs.lineEnd);  
  }  

  @Test  
  void parseAst_structDeclaration() {  
    String json = """  
        {  
          "kind": "TranslationUnitDecl",  
          "inner": [  
            {  
              "kind": "StructDecl",  
              "name": "Point",  
              "loc": {"line": 1},  
              "inner": [  
                {"kind": "VarDecl", "name": "x", "type": {"qualType": "float"}, "loc": {"line": 2}},  
                {"kind": "VarDecl", "name": "y", "type": {"qualType": "float"}, "loc": {"line": 3}}  
              ]  
            }  
          ]  
        }
        """;

    List<ClassSymbol> symbols = parser.parseAst(json, "/src/point.cpp");

    assertEquals(1, symbols.size());
    ClassSymbol cs = symbols.get(0);
    assertEquals("Point", cs.name);
    assertEquals(SymbolKind.CLASS, cs.kind); // struct maps to CLASS
    assertEquals(2, cs.fields.size());
  }

  @Test
  void parseAst_emptyAst_returnsEmpty() {
    String json = "{\"kind\": \"TranslationUnitDecl\", \"inner\": []}";

    List<ClassSymbol> symbols = parser.parseAst(json, "/src/empty.cpp");
    assertTrue(symbols.isEmpty());
  }

  @Test
  void parseAst_malformedJson_returnsEmpty() {
    List<ClassSymbol> symbols = parser.parseAst("not valid json {{{", "/src/bad.cpp");
    assertTrue(symbols.isEmpty());
  }

  @Test
  void parseAst_globalVariable() {
    String json = """
        {
          "kind": "TranslationUnitDecl",
          "inner": [
            {
              "kind": "VarDecl",
              "name": "MAX_RETRIES",
              "type": {"qualType": "int"},
              "loc": {"line": 1}
            }
          ]
        }
        """;

    List<ClassSymbol> symbols = parser.parseAst(json, "/src/config.cpp");

    assertEquals(1, symbols.size());
    ClassSymbol global = symbols.get(0);
    assertEquals("<global>", global.fqn);

    FieldSymbol field = global.fields.get("MAX_RETRIES");
    assertNotNull(field);
    assertEquals("int", field.type());
  }

  @Test  
  void parseAst_classWithSuperclass() {  
    String json = """  
        {  
          "kind": "TranslationUnitDecl",  
          "inner": [  
            {  
              "kind": "ClassDecl",  
              "name": "Derived",  
              "loc": {"line": 5},  
              "bases": [  
                {"type": {"qualType": "Base"}}  
              ],  
              "inner": []  
            }  
          ]  
        }
        """;

    List<ClassSymbol> symbols = parser.parseAst(json, "/src/derived.cpp");

    assertEquals(1, symbols.size());
    ClassSymbol cs = symbols.get(0);
    assertEquals("Base", cs.superClass);
  }

  @Test
  void parseAst_multipleTopLevelTypes() {
    String json = """
        {
          "kind": "TranslationUnitDecl",
          "inner": [
            {"kind": "ClassDecl", "name": "Alpha",  "loc": {"line": 1}, "inner": []},
            {"kind": "ClassDecl", "name": "Beta",   "loc": {"line": 5}, "inner": []},
            {"kind": "EnumDecl",  "name": "Gamma",  "loc": {"line": 9}, 
             "inner": [{"kind":"EnumConstantDecl","name":"A","loc":{"line":10}}]}
          ]
        }
        """;

    List<ClassSymbol> symbols = parser.parseAst(json, "/src/multi.cpp");

    assertEquals(3, symbols.size());
    assertEquals("Alpha", symbols.get(0).name);
    assertEquals("Beta", symbols.get(1).name);
    assertEquals("Gamma", symbols.get(2).name);
    assertEquals(SymbolKind.ENUM, symbols.get(2).kind);
  }
}
