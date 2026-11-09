package com.jolt.model;

/** Represents a parsed field (instance or static). */
public class FieldSymbol {

  public String name;
  public String type;          // e.g. "String", "List<Foo>"



  public String access;        // public/private/protected/package
  public boolean isStatic;
  public boolean isFinal;
  public int lineStart;
  public int lineEnd;
  public java.util.List<String> annotations = new java.util.ArrayList<>();

}





