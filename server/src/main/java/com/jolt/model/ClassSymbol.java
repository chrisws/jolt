package com.jolt.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Represents a parsed Java class, interface, or enum. */
public class ClassSymbol {

  public String fqn;              // fully qualified name e.g. "com.foo.Bar"
  public String simpleName;       // e.g. "Bar"
  public String filePath;         // relative path in repo
  public int lineStart;
  public int lineEnd;
  public String superClass;       // FQN of parent class (nullable)
  public List<String> interfaces = new ArrayList<>();
  public Map<String, MethodSymbol> methods = new HashMap<>();
  public Map<String, FieldSymbol> fields = new HashMap<>();
  public List<String> annotations = new ArrayList<>();

  @Override
  public String toString() { return "Class[" + fqn + "]"; }

}
