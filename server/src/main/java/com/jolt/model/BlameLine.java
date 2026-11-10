package com.jolt.model;

/** A single line of git blame output for a file range. */  
public record BlameLine(  
    int line,        // 1-based line number in the file  
    String author,   // commit author name  
    String date,     // ISO-8601 timestamp of the commit  
    String hash,     // short (7-char) commit hash  
    String summary   // first line of commit message  
) {}

