package com.jolt.model;

import java.util.ArrayList;
import java.util.List;

/** Represents a parsed method or constructor. */
public class MethodSymbol {

  public String fqn;           // e.g. "com.foo.Bar#method1"
  public String name;          // simple name
  public String signature;     // full signature text



  public String returnType;
  public List<String> params = new ArrayList<>();



  public boolean isStatic;
  public boolean isAbstract;
  public boolean isFinal;
  public int lineStart;
  public int lineEnd;
  public List<String> exceptions = new ArrayList<>();
  public List<String> callees = new ArrayList<>();

}





