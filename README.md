 # Jolt

[![Jolt](.hali/badge.svg)](.hali/badge.svg)

A server-side Java code explorer with IntelliJ-like navigation, accessible from a browser. Built for large legacy codebases (100K–500K+ files), it provides fast search, go-to-definition, find-references, class hierarchy, call graphs, and built-in git review for agent-made changes — all shareable via URL.

**80% of the navigation value at 20% of the complexity.** Not a full IDE — a fast, shareable, server-hosted code browser.

---

## Features

- **Full-text & symbol search** across hundreds of thousands of source files (Lucene)
- **Go-to-definition**, **find references**, **class hierarchy**, **call graph**
- **Browser-based UI** with Monaco Editor — no local install required
- **Multi-tab editor** with live file-change indicators
- **Shareable links**: open any file at a specific line/symbol via URL
- **Git integration**: review agent-made changes with side-by-side diffs, commit history, branch comparison; approve or revert before merging
- **Live file watching**: UI updates automatically when files change on disk
- **Syntax highlighting** for Java, XML, JSP, SQL, YAML, properties, shell, Groovy, Kotlin, Markdown and more
- **Three colour themes**: Dark (default), Light, High Contrast

### Non-goals (v1)

No code editing/refactoring, compilation/build integration, debugging, or attempt to match IntelliJ's full feature set.

---

## Technology Stack

| Layer | Choice |
|---|---|
| Language | Java 21 (LTS) |
| Parser (Java) | Eclipse JDT Core |
| Parser (XML/JSP) | Jackson XML / custom tokenizer |
| Index & Search | Apache Lucene 9.x + in-memory symbol table |
| Web Server | Javalin 4.x (embedded Jetty) — REST + WebSocket |
| CLI Parsing | Picocli |
| Frontend | React + Monaco Editor + Tailwind CSS |
| Git | JGit (Eclipse) |
| Build | Maven → single fat JAR (`maven-shade-plugin`) |
| Container (optional) | Docker — also runs bare: `java -jar jolt.jar` |

---

## Quick Start

```bash
# Minimal: index current directory, serve on localhost:9000
java -jar jolt.jar --home /path/to/codebase

# Custom port and branch
java -jar jolt.jar --home ~/code/app --port 8080 --branch feature/payment

# Force re-index with verbose logging
java -jar jolt.jar --home ~/code/app --reindex --log-level DEBUG

# Docker
docker run -p 9000:9000 -v ~/code/app:/code jolt/jolt --home /code
```

### CLI Options

```
--home=<path>        Codebase root (required). Default: CWD
--port=<n>           Listen port. Default: 9000
--host=<addr>        Bind address. Default: localhost
--branch=<name>      Git branch to check out. Default: HEAD
--theme=<name>       dark | light | high-contrast. Default: dark
--index=<path>       Lucene index dir. Default: <home>/.jolt/index
--log-level=<level>  TRACE|DEBUG|INFO|WARN|ERROR. Default: INFO
--watch / --no-watch Enable/disable file watching (default: watch)
--reindex            Force full re-index on start
--version, --help    Self-explanatory
```

No config files. No `application.properties`. Everything via CLI flags with sensible defaults.

---

## How It Works

Jolt maintains a **dual index**:

- **Lucene** — full-text and structured symbol search ("find it")
- **In-memory symbol table** — go-to-definition, references, hierarchy resolution ("resolve it")

Files that fail to parse are gracefully indexed as raw text only; the ingestion pipeline never crashes. File changes are detected via `WatchService`, debounced, and incrementally re-indexed. The server is stateless per session — multiple users can share one instance.

### Shareable Links

Every file view generates a canonical URL:

```
https://explorer.internal/?file=src/com/foo/Bar.java&line=42&symbol=method1&branch=main
```

Paste in Slack, include in commit messages, or use for code review context.

### Git Review Workflow

Agents identify commits via author name (`agent-name <agent@local>`) and message prefix (`[agent:name]`). The explorer provides:

- Per-agent change feed (filterable by agent, date, risk)
- Side-by-side diff viewer (Monaco diff editor)
- Commit history with author filtering
- Revert / cherry-pick (permission-gated, read-only by default)

---

## Repository Structure

```
jolt/
├── docker/Dockerfile
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
├── web/                       # React + Vite frontend (embedded in JAR)
├── tests/                     # Server and web test suites
├── docs/                      # Architecture, API reference, deployment guide
└── plans/java-code-explorer.md  # Full technical plan
```

---

## Delivery Phases

| Phase | Scope | Est. Duration |
|---|---|---|
| **0 — Spike** | Stand up server + Monaco; parse & search a small project from browser | 2 weeks |
| **1 — MVP** | Ingestion pipeline, Lucene index, symbol table, REST API, React+Monaco UI, multi-tab editor, live file watching, shareable links, themes | 6–8 weeks |
| **2 — Navigation** | Find references, class hierarchy, call graph, JSP parsing, Spring bean resolution | 4–6 weeks |
| **3 — Git Review** | JGit service, agent change tracking, diff viewer, branch selector, revert/cherry-pick | 4–6 weeks |
| **4 — Java Depth** | EJB/web.xml/persistence.xml mapping, annotation-based search, DI wiring graph | 4–6 weeks |
| **5 — Polish & Scale** | Incremental indexing tuning, bookmarks, multi-project support, custom themes, CI/CD hardening | 4–8 weeks |

Total: ~8–12 months (solo).

---

## Performance Notes

- Indexing 500K files: ~5–15 min on modern hardware (parallel `ForkJoinPool`)
- Warm start (subsequent runs): <10 seconds
- Search latency: <50 ms (Lucene), <1 ms (symbol table)
- Memory budget for large codebases: ~8 GB heap recommended
- Concurrent users: 10–20 on a single node (read-only API)

---

## Status

> **Planning / Discussion.** Not yet implemented in this sandbox. See [`plans/java-code-explorer.md`](plans/java-code-explorer.md) for the full technical plan including data model, API design, ingestion pipeline details, risk register, and open questions.