# Project Jolt

## Dual objectives

- Building project Jolt
- Testing Hali

## Build System
- Maven project, pom.xml in server/pom.xml
- Use TOOL:BUILD for all build operations
- Do NOT patch pom.xml directly

## Hali tool gaps
- if you notice a gap in the hali tool set, please report this to the user

## JDK version

- JDK21
- use all JDK21 features such a record

## Code style

- 2 spaces, no tabs

example layout:

```
if (foo) {
  // something
} else {
  // something else
}

public String getFqn() { 
  return fqn; 
}

```

## Dependency references

- Reference repos (javalin, eclipse.jdt.ls, jgit) live in `~/github` — an allowed read-only folder
- Use them to look up API signatures, usage patterns, and examples when writing code against those libraries
- If you need a library not yet cloned there, ask the user to add it to `~/github`


## Repository Structure

```
jolt/
├── server/
│   ├── src/main/java/com/jolt/
│   │   ├── JoltApp.java       # Entry point, Picocli CLI, starts Javalin
│   │   ├── api/               # REST routes, WebSocket handlers
│   │   ├── index/             # Lucene index management
│   │   ├── parse/             # JDT, XML, JSP parsers
│   │   ├── nav/               # Symbol table, hierarchy, call graph
│   │   ├── j2ee/              # Spring/EJB/JSP wiring resolution
│   │   ├── git/               # JGit service, agent tracking, diffs
│   │   ├── model/             # Domain objects (ClassSymbol, MethodSymbol, etc.)
│   │   └── watch/             # File watcher, .jolt/ directory management
│   │   └── watch/             # File watcher, .jolt/ directory management
│   ├── src/test               # Server test suites
│   └── pom.xml                # maven-shade-plugin → target/jolt.jar
└── plans/java-code-explorer.md  # Full technical plan
```

