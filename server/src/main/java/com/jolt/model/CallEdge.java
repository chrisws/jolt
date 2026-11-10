package com.jolt.model;

/** Represents a single method invocation edge in the call graph. */
public record CallEdge(
    String callerFqn,      // "com.foo.Bar#doWork"
    String calleeName,     // "process" (resolved name if possible)
    String calleeReceiver, // receiver type name if detectable, else ""
    int line               // line number of the invocation
) {}
