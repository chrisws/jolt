package com.jolt.model;

public record SearchHit(
    String id,           // unique doc id e.g. "com.foo.Bar#method1" or file path
    SymbolKind kind,     // CLASS, METHOD, FIELD, FILE
    String name,         // display name
    String qualifiedName,// FQN if applicable
    String filePath,     // relative path in repo
    int lineStart,       // starting line (1-based)
    float score          // Lucene relevance score
) {}

