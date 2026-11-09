package com.jolt.model;

import java.util.List;

public record MethodSymbol(
    String fqn,            // e.g. "com.foo.Bar#method1"
    String name,           // simple name "method1"
    String signature,      // "public void method1(String s)"
    String returnType,     // "void"
    List<String> params,   // ["String", "int"]
    int lineStart,
    int lineEnd,
    String access          // public/private/protected/package
) {}

