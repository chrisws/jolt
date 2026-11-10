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

## Depenency documents

- look in the docs folder for depenency details about jdt, jgit
- ask the user to clone more repos if you require further specific details


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
│   └── pom.xml                # maven-shade-plugin → target/jolt.jar
├── web/                       # Plain ES6 frontend with vendored JS libs (embedded in JAR)
├── tests/                     # Server and web test suites
├── docs/                      # Architecture, API reference, deployment guide
└── plans/java-code-explorer.md  # Full technical plan
```

