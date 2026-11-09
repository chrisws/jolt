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

