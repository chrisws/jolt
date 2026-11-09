package com.jolt.model;

public record FieldSymbol(
    String name,           // simple name "field1"
    String type,           // declared type "String"
    int lineStart,
    int lineEnd,
    String access          // public/private/protected/package
) {}

