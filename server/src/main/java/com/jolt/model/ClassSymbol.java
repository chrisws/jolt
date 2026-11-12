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
package com.jolt.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ClassSymbol {
  public final String fqn;            // "com.foo.Bar"
  public final String name;           // "Bar"
  public final SymbolKind kind;       // CLASS, INTERFACE, ENUM, RECORD
  public final String filePath;       // relative path in repo
  public int lineStart;
  public int lineEnd;
  public String superClass;          // FQN of parent or null
  public List<String> interfaces = new ArrayList<>();
  public Map<String, MethodSymbol> methods = new LinkedHashMap<>();   // name → symbol (overloads keyed by name+params later)
  public Map<String, FieldSymbol> fields = new LinkedHashMap<>();     // name → symbol

  public ClassSymbol(String fqn, String name, SymbolKind kind, String filePath) {
    this.fqn = fqn;
    this.name = name;
    this.kind = kind;
    this.filePath = filePath;
  }

  @Override
  public String toString() { return fqn + " [" + kind + "]"; }
}

