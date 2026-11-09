package com.jolt.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Represents a parsed Java class, interface, or enum. */
public class ClassSymbol {

    private String fqn;              // fully qualified name e.g. "com.foo.Bar"
    private String simpleName;       // e.g. "Bar"
    private String filePath;         // relative path in repo
    private int lineStart;
    private int lineEnd;
    private String superClass;       // FQN of parent class (nullable)
    private List<String> interfaces = new ArrayList<>();
    private Map<String, MethodSymbol> methods = new HashMap<>();
    private Map<String, FieldSymbol> fields = new HashMap<>();
    private List<String> annotations = new ArrayList<>();

    public ClassSymbol() {}



    public String getFqn() { return fqn; }
    public void setFqn(String v) { this.fqn = v; }
    public String getSimpleName() { return simpleName; }



    public void setSimpleName(String v) { this.simpleName = v; }
    public String getFilePath() { return filePath; }



    public void setFilePath(String v) { this.filePath = v; }
    public int getLineStart() { return lineStart; }



    public void setLineStart(int v) { this.lineStart = v; }
    public int getLineEnd() { return lineEnd; }



    public void setSuperClass(String v) { this.superClass = v; }



    public List<String> getInterfaces() { return interfaces; }



    public Map<String, MethodSymbol> getMethods() { return methods; }



    public Map<String, FieldSymbol> getFields() { return fields; }



    @Override
    public String toString() { return "Class[" + fqn + "]"; }
}


