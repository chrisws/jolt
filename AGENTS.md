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

- look in the docs folder for depenency details about jdt
- ask the user to clone more repos if you require further specific details

