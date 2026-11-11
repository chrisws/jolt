# Jolt

[![Jolt](.hali/badge.svg)](.hali/badge.svg) [![Tests](.hali/test_badge.svg)](.hali/test_badge.svg)

A server-side Java code explorer with IntelliJ-like navigation, accessible from a browser. Built for large legacy codebases (100K–500K+ files), it provides fast search, go-to-definition, find-references, class hierarchy, call graphs, git blame/log/diff review for agent-made changes — all shareable via URL.

**80% of the navigation value at 20% of the complexity.** Not a full IDE — a fast, shareable, server-hosted code browser.

---

## Features

- **Full-text & symbol search** across hundreds of thousands of source files (Lucene 9.x)
- **Semantic search** via ONNX embeddings (all-MiniLM-L6-v2) — optional, model file required
- **Go-to-definition**, **find references**, **class hierarchy**, **call graph** (depth-capped traversal + who-calls-me)
- **Browser-based UI** with plain ES6 + highlight.js — no local install, no npm
- **Multi-tab editor** with live file-change indicators (WebSocket push)
- **File tree browser** with directory navigation
- **Breadcrumbs** for package/class context
- **Quick Open** (Ctrl+P) — fuzzy file/symbol jump dialog with keyboard navigation
- **Shareable links**: open any file at a specific line/symbol via URL
- **Git integration**: commit log with author/prefix filtering, multi-mode diffs (unified, side-by-side, inline, word-level), branch listing, per-line blame gutter
- **JSP parsing**: element extraction, include resolution, taglib URIs
- **Spring XML parsing**: bean definitions, component-scan paths, imports
- **J2EE wiring**: web.xml servlet/filter mapping, persistence.xml entities, EJB/DI annotation scanning, DI wiring graph (nodes + edges)
- **Live file watching**: UI updates automatically when files change on disk (WatchService + debounce)
- **Syntax highlighting** for Java, XML, JSP, SQL, YAML, properties, shell, Kotlin, Groovy, Markdown and more
- **Bookmarks**: save/restore file+line positions with labels; recent-files tracking; search history
- **Dashboard**: at-a-glance stats (classes indexed, methods count)
- **User settings**: theme and preferences persisted to `.jolt/settings.json`
- **Keybinding modes**: configurable keybinding sets served via API
- **Feedback button**: in-app feedback logging to `.jolt/feedback.jsonl`

### Non-goals (v1)

No code editing/refactoring, compilation/build integration, debugging, or attempt to match IntelliJ's full feature set.

---

## Technology Stack

| Layer | Choice | Version |
|---|---|---|
| Language | Java (LTS) | 21 |
| Parser (Java) | Eclipse JDT Core | 3.38.0 |
| Parser (XML/JSP) | Jackson XML / custom tokenizer | 2.17.0 |
| Index & Search | Apache Lucene + in-memory symbol table | 9.10.0 |
| Semantic Search | ONNX Runtime (all-MiniLM-L6-v2) | 1.18.0 |
| Web Server | Javalin (embedded Jetty) — REST + WebSocket | 5.6.3 |
| CLI Parsing | Picocli | 4.7.5 |
| Frontend | Plain ES6 + highlight.js (vendored) | hljs 11.x |
| Git | JGit (Eclipse) | 6.8.0 |
| Logging | Logback Classic | 1.5.3 |
| Build (server) | Maven → single fat JAR (`maven-shade-plugin`) | shade 3.5.1, surefire 3.2.5 |
| Testing | JUnit Jupiter (Jupiter) + Surefire plugin for server; manual browser testing for UI

---

## Quick Start

```bash
# Minimal: index current directory, serve on localhost:9000
java -jar jolt.jar --home /path/to/codebase

# Custom port and host
java -jar jolt.jar --home ~/code/app --port 8080 --host 0.0.0.0

# Force re-index with verbose logging
java -jar jolt.jar --home ~/code/app --reindex --log-level DEBUG

# Custom index location
java -jar jolt.jar --home ~/code/app --index /tmp/jolt-index
```

### CLI Options

```
--home=<path>        Codebase root (required). Default: CWD
--port=<n>           Listen port. Default: 9000
--host=<addr>        Bind address. Default: localhost
--index=<path>       Lucene index dir. Default: <home>/.jolt/index
--log-level=<level>  TRACE|DEBUG|INFO|WARN|ERROR. Default: INFO
--reindex            Force full re-index on start
--version, --help    Self-explanatory
```

No config files. No `application.properties`. Everything via CLI flags with sensible defaults.

---

## Building the JAR

### Prerequisites

- **JDK 21** (LTS) — `java -version` must report 21+
- **Maven 3.8+** — `mvn -version`

### Step 1: Build the server JAR

```bash
cd server
mvn clean package -DskipTests
```

This produces a single fat JAR at:

```
server/target/jolt-server-0.1.0-SNAPSHOT.jar
```

The `maven-shade-plugin` bundles all dependencies (JDT, Lucene, Javalin, JGit, ONNX Runtime, etc.) into one self-contained executable JAR. You can rename it to `jolt.jar` for convenience.

### Step 2: Run

```bash
java -jar server/target/jolt-server-0.1.0-SNAPSHOT.jar --home /path/to/codebase
```

### Optional: Semantic Search Model

Semantic search is **disabled by default**. To enable it, place the ONNX model file at:

```
<home>/.jolt/models/all-MiniLM-L6-v2.onnx
```

The server logs `Semantic search enabled` on startup if the model is found, or `semantic search disabled` if not. The `/api/search/semantic` endpoint returns HTTP 503 with a hint when the model is missing.

---

## Testing

### Unit Tests (Server)

All server-side components have JUnit 5 test suites:

| Package | Test Classes | What's Covered |
|---|---|---|
| `nav/` | `SymbolTableTest`, `CallGraphTest` | Symbol lookup, hierarchy resolution, reference tracking, call graph traversal + caller lookup |
| `index/` | `LuceneIndexerTest`, `EmbeddingServiceTest` | Lucene indexing, text/symbol search, semantic vector search |
| `parse/` | `JavaParserServiceTest`, `SpringXmlParserTest`, `JspParserTest` | JDT AST parsing, Spring bean extraction, JSP element/include/taglib parsing |
| `git/` | `GitServiceTest`, `BlameServiceTest` | Commit log filtering, diff generation, branch listing, per-line blame |
| `api/` | `SettingsServiceTest`, `KeybindingsServiceTest`, `FeedbackServiceTest`, `BookmarksServiceTest` | Settings persistence, keybinding modes, feedback logging, bookmark CRUD + recent files + search history |
| `watch/` | `FileWatcherTest` | File change detection, debounce behaviour |

Run all tests:

```bash
cd server
mvn test
```

Run a single test class:

```bash
mvn test -Dtest=SymbolTableTest
mvn test -Dtest=CallGraphTest
mvn test -Dtest=LuceneIndexerTest
```

### Manual / Integration Testing

1. **Start the server** against a real codebase:
   ```bash
   java -jar jolt-server-0.1.0-SNAPSHOT.jar --home /path/to/some-java-project --port 9000
   ```

2. **Open the browser** at `http://localhost:9000` — the ES6 UI loads from the embedded static assets.

3. **Exercise the key flows:**
   - **Search**: type in the search bar → results appear with file/line/symbol info. Try both text and symbol-mode search.
   - **Go-to-definition**: click a symbol in search results → editor opens at the definition with members listed.
   - **Class hierarchy**: open a class → check the hierarchy panel for super-classes, interfaces, sub-types.
   - **Call graph**: select a method → call graph panel shows outgoing edges (depth-capped) and incoming callers.
   - **File tree**: navigate directories via the file tree panel; click files to open them.
   - **Git log / diff**: open the git log panel → filter by author or message prefix → click a commit → view file list diff → click a file for side-by-side diff.
   - **Blame gutter**: open a file → blame gutter shows per-line author/commit on the left margin.
   - **Live watching**: modify a file on disk (e.g., `echo "// test" >> somefile.java`) → the editor tab should show a change indicator within ~300 ms (debounce window).
   - **Bookmarks**: bookmark a file+line position → verify it appears in the bookmarks list; delete it.
   - **Dashboard**: check the dashboard stats match the indexed class/method counts.
   - **Settings / themes**: switch themes via settings → verify persistence across page reloads.
   - **Quick Open**: press Ctrl+P (or configured shortcut) → type a filename or symbol → jump to it.
   - **Shareable Links**: open a file → click the 🔗 Link button in the breadcrumb bar → verify the URL contains `?file=...&line=...` → paste that URL into a new tab → the same file/line should open automatically.
   - **Inline Diff**: open a git commit diff → click the "Inline" toggle → verify single-column view with paired old/new lines and word-level `<ins>` highlights on changed tokens.

4. **API smoke test** (no browser needed):
   ```bash
   # Status
   curl http://localhost:9000/api/status

   # Search
   curl "http://localhost:9000/api/search?q=Service&limit=10"

   # Go-to-definition
   curl "http://localhost:9000/api/definition?symbol=com.example.MyService"

   # Class hierarchy
   curl "http://localhost:9000/api/hierarchy?class=com.example.MyService"

   # Find references
   curl "http://localhost:9000/api/references?symbol=com.example.MyService"

   # Call graph
   curl "http://localhost:9000/api/call-graph?method=com.example.MyService#doWork&depth=3"

   # File content (first 50 lines)
   curl "http://localhost:9000/api/file?path=src/main/java/com/example/MyService.java&start=1&end=50"

   # File tree
   curl "http://localhost:9000/api/file/tree?path=src/main/java"

   # Git commits (first page, 10 per page)
   curl "http://localhost:9000/api/git/commits?page=0&size=10"

   # Git branches
   curl "http://localhost:9000/api/git/branches"

   # Blame (lines 1–20)
   curl "http://localhost:9000/api/blame?path=src/main/java/com/example/MyService.java&start=1&end=20"

   # Spring beans (if applicable)
   curl "http://localhost:9000/api/spring/beans?path=src/main/resources/applicationContext.xml"

   # J2EE wiring graph
   curl "http://localhost:9000/api/j2ee/wiring-graph"

   # Bookmarks
   curl http://localhost:9000/api/bookmarks
   curl -X POST http://localhost:9000/api/bookmarks \
     -H 'Content-Type: application/json' \
     -d '{"path":"src/Foo.java","line":42,"label":"important"}'

   # Settings
   curl http://localhost:9000/api/settings
   curl -X PUT http://localhost:9000/api/settings \
     -H 'Content-Type: application/json' \
     -d '{"theme":"light"}'

   # Keybindings
   curl "http://localhost:9000/api/keybindings?mode=default"

   # Dashboard
   curl http://localhost:9000/api/dashboard
   ```

5. **WebSocket test** (live file changes):
   ```bash
   # Using wscat or a browser DevTools console:
   # Connect to ws://localhost:9000/ws
   # Modify a file on disk → expect a JSON message:
   # {"type":"file_changed","path":"src/Foo.java","event":"modify"}
   ```

---

## How It Works

Jolt maintains a **dual index**:

- **Lucene** — full-text and structured symbol search ("find it") + optional semantic vector search via ONNX embeddings
- **In-memory symbol table** — go-to-definition, references, hierarchy resolution, call graph ("resolve it")

Files that fail to parse are gracefully indexed as raw text only; the ingestion pipeline never crashes. File changes are detected via `WatchService`, debounced (300 ms), and broadcast to connected WebSocket clients. The server is stateless per session — multiple users can share one instance.

### Indexing Pipeline

1. `FileWalker` recursively discovers all `.java` files under `--home`
2. `JavaParser` (JDT AST) extracts `ClassSymbol`, `MethodSymbol`, `FieldSymbol` per file; populates the `CallGraph` with caller→callee edges
3. Each class is indexed into Lucene (structured fields: FQN, name, kind, file path, line) and raw file text is indexed for full-text fallback
4. All symbols are loaded into the in-memory `SymbolTable` for O(1) lookups
5. The Lucene index is committed to disk at `<home>/.jolt/index/`

### Shareable Links

Every file view generates a canonical URL with file, line, and optional symbol:

```
http://explorer.internal/?file=src/com/foo/Bar.java&line=42&symbol=method1
```

These links serve two purposes:

- **In Jolt**: opening the URL loads the file at the specified line/symbol directly in the browser viewer.
- **External editor handoff**: the URL encodes enough context (`file`, `line`, `symbol`) for editor integrations, IDE plugins, or CLI tools to open the exact location in a local editor (e.g. `code --goto file:line`). Paste in Slack, include in commit messages, or use for code review context.

### Git Review Workflow

Agents identify commits via author name and message prefix (`[agent:name]`). The explorer provides:

- Per-agent change feed (filterable by author, message prefix)
- Multi-mode diff viewer (unified, side-by-side, inline, word-level highlighting)
- Commit history with author filtering
- Per-line blame gutter in the editor
- Branch listing and current-branch indicator

---

## API Reference

All endpoints are under `/api/`. WebSocket endpoint at `/ws`.

### Status & Dashboard

| Method | Path | Params | Description |
|---|---|---|---|
| GET | `/api/status` | — | Server status + indexed class count |
| GET | `/api/dashboard` | — | Dashboard stats: classes, methods, total files |

### Search

| Method | Path | Params | Description |
|---|---|---|---|
| GET | `/api/search` | `q`, `kind` (optional), `limit` (default 50) | Lucene full-text + symbol search. `kind`: CLASS, METHOD, FIELD, etc. |
| GET | `/api/search/semantic` | `q`, `limit` (default 20) | ONNX semantic vector search. Returns 503 if model not loaded. |

### File & Navigation

| Method | Path | Params | Description |
|---|---|---|---|
| GET | `/api/file` | `path`, `start` (optional), `end` (optional) | File content with line range + detected language. Path-traversal protected. |
| GET
| GET | `/api/definition` | `symbol` | Go-to-definition: returns FQN, kind, file path, line, super-class, and all members (methods + fields) |
| GET | `/api/hierarchy` | `class` | Class hierarchy: super-class chain, interfaces, sub-types |
| GET | `/api/file/tree` | `path` (optional) | Directory listing (files + subdirs, sorted dirs-first). Path-traversal protected. |
| GET | `/api/references` | `symbol` | Find all references to a symbol across the codebase |

### Call Graph

| Method | Path | Params | Description |
|---|---|---|---|
| GET | `/api/call-graph` | `method`, `depth` (default 3, max 10) | Outgoing call edges from a method, traversed to given depth. Returns caller→callee edges with receiver and line. |
| GET | `/api/call-graph/callers` | `method` | Who-calls-me: all methods that call the given method. Accepts `com.Foo#bar` or just `bar`. |

### JSP & Spring

| Method | Path | Params | Description |
|---|---|---|---|
| GET | `/api/jsp/parse` | `path` | Parse a JSP file: elements (scriptlet, expression, directive), included files, taglib URIs |
| GET | `/api/spring/beans` | `path` | Parse a Spring XML config: bean definitions (id, class, scope, depends-on), component-scan paths, imports |

### Git

| Method | Path | Params | Description |
|---|---|---|---|
| GET | `/api/git/stats` | — | Repository stats: total commits, contributors, top committers, current branch, streaks, busiest day |
| GET | `/api/git/commits` | `page` (default 0), `size` (default 25), `branch`, `author`, `from`, `to` (all optional) | Paginated commit list with optional branch/author/date-range filtering |
| GET | `/api/git/activity` | `year` (optional, default current) | Activity heatmap data: per-day commit counts for the given year |
| GET | `/api/git/diff` | `from` (optional), `to` (required) | File-level diff between two commits (or commit vs parent). Returns list of changed files with status. |
| GET | `/api/git/file-diff` | `from` (optional), `to`, `path` (required) | Unified diff content for a single file between two commits |
| GET | `/api/git/branches` | — | List all local branches with current-branch indicator |
| GET | `/api/git/commit/enhanced` | `hash` (required) | Full commit detail: message, author, date, parents, per-file stats (added/removed lines, binary flag) |
| GET | `/api/git/commit/raw` | `hash`, `path` (both required) | Raw file content at a specific commit (supports text and binary) |
| GET | `/api/git/commit/patch` | `hash` (required) | Downloadable `.patch` file for a commit |
| GET | `/api/blame` | `path`, `start` (default 1), `end` (default EOF) | Per-line git blame: author, commit hash, date for each line in range. Path-traversal protected. |

### J2EE / Enterprise

| Method | Path | Params | Description |
|---|---|---|---|
| GET | `/api/j2ee/webxml` | — | Parse `web.xml`: servlets, filters, listeners, servlet-mappings, filter-mappings, session config, welcome files |
| GET
| GET | `/api/j2ee/persistence` | — | Parse `persistence.xml`: persistence units, entities (class, table), properties |
| GET | `/api/j2ee/annotations` | — | Scan all Java files for EJB (`@Stateless`, `@Stateful`, `@Singleton`) and DI (`@Component`, `@Service`, `@Repository`, `@Controller`) annotations |
| GET | `/api/j2ee/wiring-graph` | — | Build DI wiring graph: nodes (annotated classes) + edges (dependency relationships). Returns node count, edge count, and edge list. |

### Bookmarks, Settings & Feedback

| Method | Path | Params | Description |
|---|---|---|---|
| GET | `/api/bookmarks` | — | List all saved bookmarks (path, line, label) |
| POST | `/api/bookmarks` | body: `{path, line, label}` | Add a bookmark. Returns 201 with the created entry. |
| DELETE | `/api/bookmarks` | `path`, `line` (optional) | Remove a bookmark by path+line. Returns 404 if not found. |
| GET | `/api/recent` | — | Recently opened files (from `.jolt/recent.json`) |
| GET
| GET | `/api/search/history` | — | Recent search queries (from `.jolt/search-history.json`) |
| POST | `/api/feedback` | body: `{page, file (optional), message}` | Log user feedback to `.jolt/feedback.jsonl`. Returns 201. |
| GET | `/api/settings` | — | Current user settings (theme, etc.) from `.jolt/settings.json` |
| PUT | `/api/settings` | body: `{theme, ...}` | Update user settings. Returns the updated settings object. |
| GET | `/api/keybindings` | `mode` (default `default`) | Keybinding definitions for a given mode. Returns bindings + list of available modes. Returns 400 for unknown mode. |

### WebSocket

| Endpoint | Description |
|---|---|
| `ws://host:port/ws` | Live notifications + real-time search. Server pushes `{"type":"file_changed","path":"...","event":"modify\|create\|delete"}` when files change on disk. Clients can send `{"type":"search","q":"...","mode":"text\|semantic","limit":50}` for real-time search results pushed back as `{"type":"search_results",...}`. Connection count tracked server-side. |

---

## Repository Structure

```
jolt/
├── server/
│   ├── src/main/java/com/jolt/
│   │   ├── JoltApp.java              # Picocli CLI entry point
│   │   ├── ServerBootstrap.java      # Indexing + server startup orchestration
│   │   ├── api/
│   │   │   ├── ApiRoutes.java        # All REST endpoints (~30 routes)
│   │   │   ├── WsHub.java            # WebSocket hub: live file changes + search
│   │   │   ├── SettingsService.java  # User settings persistence (.jolt/settings.json)
│   │   │   ├── KeybindingsService.java # Keybinding mode definitions
│   │
│   │   │   ├── FeedbackService.java  # Feedback logging (.jolt/feedback.jsonl)
│   │   │   ├── BookmarksService.java # Bookmarks, recent files, search history
│   │   │   └── DashboardService.java # Dashboard stats aggregation
│   │   ├── index/
│   │   │   ├── LuceneIndexer.java    # Lucene index: text + symbol + semantic search
│   │   │   └── EmbeddingService.java # ONNX runtime: all-MiniLM-L6-v2 embeddings
│   │   ├── nav/
│   │   │   ├── SymbolTable.java      # In-memory symbol table: classes, methods, fields, refs
│   │   │   └── CallGraph.java        # Call graph: edges, traversal, caller lookup
│   │   ├── parse/
│   │   │   ├── FileWalker.java       # Recursive .java file discovery
│
│   │   │   ├── JavaParser.java       # JDT AST parser (classes, methods, fields, calls)
│   │   │   ├── JavaParserService.java# Parser service wrapper
│   │   │   ├── JspParser.java        # JSP element/include/taglib extraction
│   │   │   ├── SpringXmlParser.java  # Spring XML bean/config parsing
│   │   │   ├── J2eeService.java      # web.xml, persistence.xml, annotations, wiring graph
│   │   │   └── ParseUtils.java       # Shared parsing utilities
│   │   ├── git/
│   │   │   ├── GitService.java       # JGit: log, diff, branches, commit detail
│   │   │   └── BlameService.java     # JGit: per-line blame
│
│   │   ├── model/
│   │   │   ├── ClassSymbol.java      # Class/Interface/Enum record: FQN, kind, members, hierarchy
│   │   │   ├── MethodSymbol.java     # Method record: name, signature, line range
│   │   │   ├── FieldSymbol.java      # Field record: name, type, line
│   │   │   ├── SymbolKind.java       # Enum: CLASS, INTERFACE, ENUM, METHOD, FIELD, etc.
│   │   │   ├── SearchHit.java        # Search result: file path, line, symbol, score
│   │   │   ├── CallEdge.java         # Call graph edge: caller FQN, callee name/receiver, line
│   │   │   └── BlameLine.java        # Blame entry: line number, author, commit hash, date
│   │   └── watch/
│   │       ├── FileWatcher.java      # WatchService-based file change detection + debounce
│   │       └── FileChangeEvent.java  # Event record: path + event type (modify/create/delete)
│   ├── src/main/resources/static/    # Frontend: plain ES6 + vendored libs
│   │   ├── index.html                # Single-page app shell
│   │   ├── app.js                    # All app logic (ES6)
│   │   ├── style.css                 # Stylesheet
│   │   └── hljs/                     # Vendored highlight.js
│   │       ├── highlight.min.js
│   │       └── theme-github-dark.css
│   ├── src/test/java/com/jolt/       # JUnit 5 test suites (14 test classes)
│   └── pom.xml                       # Maven config: shade plugin → fat JAR




│|tool_result
HALI_TOOL_RESULT: TOOL_RESULT: OK: appended to /home/chrisws/src/jolt/README.md





├── docs/                             # Dependency docs (JDT, JGit)
├── plans/java-code-explorer.md       # Full technical plan (original design doc)
└── AGENTS.md                         # Project conventions for AI assistants
```

---

## Performance Notes

- Indexing 500K files: ~5–15 min on modern hardware (sequential parse + index)
- Warm start (subsequent runs with existing Lucene index): <10 seconds
- Search latency: <50 ms (Lucene), <1 ms (symbol table), ~100 ms (semantic/ONNX)
- Memory budget for large codebases: ~8 GB heap recommended (`-Xmx8g`)
- Concurrent users: 10–20 on a single node (read-only API)
- File watcher debounce: 300 ms — rapid successive edits are coalesced into one notification

---

## Data Files Created at Runtime

Jolt creates a `.jolt/` directory under `--home` at startup:

```
<home>/.jolt/
├── index/              # Lucene index segments (auto-managed)
├── models/             # Optional: ONNX embedding model files
├── settings.json       # User settings (theme, etc.) — created on first PUT /api/settings
├── feedback.jsonl      # Feedback log — appended on POST /api/feedback
├── recent.json         # Recently opened files — updated by frontend
└── search-history.json # Recent search queries — updated by frontend
```

These files are safe to delete; Jolt will recreate them as needed. The Lucene index can be forced to rebuild with `--reindex`.

---

## Status

> **Phase 1–4 implemented.** Core navigation (search, go-to-definition, references, hierarchy, call graph), JSP/Spring/J2EE parsing, git integration (log, diff, blame, branches), live file watching, bookmarks, settings, dashboard, Quick Open, shareable links, and the full ES6 frontend are in place. 14 JUnit test classes cover the server-side components. Semantic search is available but opt-in (requires model file). Multi-project support is not yet implemented.