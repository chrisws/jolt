# Jolt — Technical Plan

> **Status:** Planning / Discussion
> **Not to be implemented in this sandbox.**
> **Target:** Web-based code navigation & review tool for a 20+ year J2EE codebase.
> **CLI:** `jolt` · **URL:** `https://jolt.internal`

---

## 1. Overview

**Jolt** is a server-side Java code explorer that provides IntelliJ-like navigation
over a large legacy J2EE codebase, accessible from a browser. The goal is
**80% of the navigation value at 20% of the complexity** — not a full IDE, but a
fast, shareable, server-hosted code browser with built-in git review for
agent-made changes.

### 1.1 Goals
- Fast full-text and symbol search across 100K–500K+ source files
- Go-to-definition, find-references, class hierarchy, call graph
- J2EE awareness: Spring XML, EJB annotations, JSP, `web.xml`, `persistence.xml`
- Browser-based UI with Monaco Editor, no local install required
- Shareable via URL (team can open the same codebase in a browser)
- **Git integration**: review agent-made changes via diff viewer, commit history,
  branch comparison. Approve/revert agent commits before they land on main.
- **Multi-tab editor**: open multiple files simultaneously, switch with Ctrl+Tab.
- **Live file watching**: UI updates automatically when a file changes on disk
  (agent edit, git checkout, manual save).
- **Shareable file links**: "Copy Link" generates a URL that opens a specific
  file at a specific line, e.g. `?file=src/com/foo/Bar.java&line=42&symbol=method1`.

### 1.2 Non-Goals (v1)
- Code editing / refactoring
- Compilation / build integration
- Debugging
- Matching IntelliJ's full feature set

---

## 2. Technology Stack

| Layer | Choice | Rationale |
|---|---|---|
| **Language** | Java 21 (LTS) | Native ecosystem for parsing Java, Lucene, web frameworks |
| **Parser (Java)** | Eclipse JDT Core | Most mature Java AST; handles Java 5–21; used by Eclipse/IntelliJ internally |
| **Parser (XML)** | Jackson XML / DOM | `web.xml`, `ejb-jar.xml`, Spring XML, `persistence.xml` |
| **Parser (JSP)** | Custom lightweight tokenizer | Extract scriptlets, EL expressions, tag library refs; treat rest as text |
| **Index / Search** | Apache Lucene 9.x | Full-text search, structured field queries, typo tolerance |
| **Symbol Table** | In-memory HashMap / Trie | Fast go-to-def, find-refs, hierarchy resolution (complements Lucene) |
| **Web Server** | Javalin 4.x (embedded Jetty) | Lightweight REST + WebSocket. Single-JAR friendly. No Spring context overhead. |
| **WebSocket** | Javalin built-in WS | Live search-as-you-type, file open notifications |
| **CLI Parsing** | Picocli | Command-line options only. No properties files. |
| **Frontend** | Monaco Editor + React | Monaco = VS Code editor; React for UI shell |
| **Frontend Styling** | Tailwind CSS | Fast layout, dark/light themes |
| **Database (optional)** | H2 / SQLite | Persist index metadata, user bookmarks, recent files |
| **Git** | JGit (Eclipse) | Native Java git: diff, log, branch, commit inspection |
| **Build** | Maven + `maven-shade-plugin` | Single fat JAR. No external deps at runtime. |
| **Container** | Docker (optional) | `docker run -v /code:/code jolt/jolt --home /code`. Also runs bare: `java -jar jolt.jar --home /code` |

---

## 3. Command-Line Interface

### 3.1 Philosophy

One JAR. One command. No config files. No `application.properties`.
All configuration via CLI flags. Sensible defaults mean the shortest
invocation is just:

```bash
java -jar jolt.jar --home /path/to/codebase
```

### 3.2 CLI Options (Picocli)

```
Usage: jolt.jar [--home=<path>] [--port=<n>] [--host=<addr>]
                [--branch=<name>] [--theme=<name>] [--index=<path>]
                [--log-level=<level>] [--watch] [--no-watch]
                [--reindex] [--version] [--help]

Options:
  --home=<path>        Codebase root directory (REQUIRED)
                       Default: current working directory
  --port=<n>           Listen port. Default: 9000
  --host=<addr>        Bind address. Default: localhost
  --branch=<name>      Git branch to check out on start. Default: current HEAD
  --theme=<name>       Default UI theme: dark|light|high-contrast. Default: dark
  --index=<path>       Lucene index directory. Default: <home>/.jolt/index
  --log-level=<level>  TRACE|DEBUG|INFO|WARN|ERROR. Default: INFO
  --watch              Watch for file changes (default: true)
  --no-watch           Disable file watching
  --reindex            Force full re-index on start (ignore existing index)
  --version            Print version and exit
  --help               Show help and exit
```

### 3.3 Examples

```bash
# Minimal: index CWD, serve on localhost:9000
java -jar jolt.jar --home ~/projects/legacy-j2ee

# Custom port, specific branch
java -jar jolt.jar --home ~/code/app --port 8080 --branch feature/payment

# Force re-index, verbose logging
java -jar jolt.jar --home ~/code/app --reindex --log-level DEBUG

# Docker
docker run -p 9000:9000 -v ~/code/app:/code jolt/jolt --home /code

# As a service (systemd)
# /etc/systemd/system/jolt.service
#   ExecStart=/usr/bin/java -jar /opt/jolt/jolt.jar --home /var/code/app --host 0.0.0.0
```

### 3.4 The `.jolt/` Directory

Jolt creates and manages a `.jolt/` folder inside `--home`. This is
Jolt's private space — the user never touches it, and it should be
added to `.gitignore`.

```
<home>/.jolt/
├── index/              # Lucene index segments + write-ahead log
│   ├── segments_1
│   ├── _0.cfe
│   ├── _0.cfs
│   └── write.lock
├── symbols/            # Serialised symbol table (fast restart)
│   └── symbols.bin
├── logs/
│   └── jolt.log        # Rolling log (10 MB max, 5 files)
├── themes/             # User-imported custom themes (Phase 5)
│   └── my-theme.json
├── prefs.json          # User preferences (last theme, open tabs, bookmarks)
└── version             # Jolt version string (for upgrade migration)
```

**Lifecycle:**
- **First run**: creates `.jolt/`, builds index from scratch (~5–15 min).
- **Subsequent runs**: loads `symbols.bin` + Lucene index from disk.
  Warm start in <10 seconds.
- **`--reindex`**: deletes `.jolt/index/` + `.jolt/symbols/`, rebuilds.
- **Version upgrade**: checks `.jolt/version`. If Jolt version changed,
  invalidates index (schema may have changed), rebuilds.
- **`.gitignore`**: on first run, Jolt appends `.jolt/` to the project's
  `.gitignore` if it exists and doesn't already contain it.

### 3.5 Single JAR Build

```xml
<!-- maven-shade-plugin in pom.xml -->
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-shade-plugin</artifactId>
  <version>3.5.1</version>
  <executions>
    <execution>
      <phase>package</phase>
      <goals><goal>shade</goal></goals>
      <configuration>
        <transformers>
          <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
            <mainClass>com.jolt.JoltApp</mainClass>
          </transformer>
          <transformer implementation="org.apache.maven.plugins.shade.resource.ServicesResourceTransformer"/>
        </transformers>
        <filters>
          <filter>
            <artifact>*:*</artifact>
            <excludes>
              <exclude>META-INF/*.SF</exclude>
              <exclude>META-INF/*.DSA</exclude>
              <exclude>META-INF/*.RSA</exclude>
            </excludes>
          </filter>
        </filters>
      </configuration>
    </execution>
  </executions>
</plugin>
```

Output: `target/jolt.jar` (~80–120 MB with all deps shaded in).
Frontend (React/Vite) is built to `server/src/main/resources/static/`
and embedded in the JAR. No separate web server needed.

---

## 4. Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Browser (React + Monaco)                     │
│  ┌───────────┐  ┌──────────────┐  ┌────────────┐  ┌────────────┐  │
│  │  Editor   │  │  Search Panel│  │ Hierarchy  │  │  Call Graph│  │
│  │  (Monaco) │  │  (Lucene)    │  │  Tree      │  │  (SVG/D3)  │  │
│  └─────┬─────┘  └──────┬───────┘  └─────┬──────┘  └─────┬──────┘  │
│        │               │               │               │         │
│  ──────┴───────────────┴───────────────┴───────────────┴────────  │
│                    REST + WebSocket API                             │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
┌──────────────────────────────┴──────────────────────────────────────┐
│                     Jolt Server (Javalin)                            │
│                                                                     │
│  ┌───────────────────────────────────────────────────────────────┐ │
│  │                    API Layer (REST + WS)                       │ │
│  │  /search  /definition  /references  /hierarchy  /file  /graph │ │
│  └──────────────────────────┬────────────────────────────────────┘ │
│                             │                                      │
│  ┌──────────────────────────┴────────────────────────────────────┐ │
│  │                  Navigation Engine                             │ │
│  │  ┌─────────────┐  ┌──────────────┐  ┌──────────────────────┐ │ │
│  │  │ Symbol Table│  │  Hierarchy   │  │  J2EE Wiring Resolver │ │ │
│  │  │ (in-mem)    │  │  Resolver    │  │  (Spring/EJB/JSP)    │ │ │
│  │  └─────────────┘  └──────────────┘  └──────────────────────┘ │ │
│  └──────────────────────────┬────────────────────────────────────┘ │
│                             │                                      │
│  ┌──────────────────────────┴────────────────────────────────────┐ │
│  │                  Index Layer (Lucene)                          │ │
│  │  Full-text index + structured symbol index                    │ │
│  └──────────────────────────┬────────────────────────────────────┘ │
│                             │                                      │
│  ┌──────────────────────────┴────────────────────────────────────┐ │
│  │                  Ingestion Pipeline                            │ │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────────────┐   │ │
│  │  │ JDT      │ │ Jackson  │ │ JSP      │ │ Build/Config   │   │ │
│  │  │ Parser   │ │ XML      │ │ Tokenizer│ │ Parser         │   │ │
│  │  └──────────┘ └──────────┘ └──────────┘ └────────────────┘   │ │
│  └───────────────────────────────────────────────────────────────┘ │
│                                                                     │
│  ┌───────────────────────────────────────────────────────────────┐ │
│  │              Git Service (JGit)                                │ │
│  │  diff, log, branch, commit inspection, agent-change tracking  │ │
│  └───────────────────────────────────────────────────────────────┘ │
│                                                                     │
│  ┌───────────────────────────────────────────────────────────────┐ │
│  │              File System (volume-mounted codebase)             │ │
│  └───────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
```

### 4.1 Key Design Decisions

- **Dual index**: Lucene for search (full-text + structured queries), in-memory
  symbol table for navigation (go-to-def, refs, hierarchy). Lucene is the
  "find it" engine; the symbol table is the "resolve it" engine.
- **Graceful degradation**: Files that fail to parse are indexed as raw text
  only. The UI shows a warning badge. Never crash the ingestion pipeline.
- **Incremental indexing**: Watch file changes (WatchService), re-index only
  changed files. Full re-index on demand.
- **Stateless API**: The server holds the index in memory. No per-session
  state. Multiple users can share the same server instance.

---

## 5. Data Model

### 5.1 Lucene Index Schema

Each source file produces **multiple Lucene documents**:

```
Document types:
  CLASS    → one per class/interface/enum
  METHOD   → one per method
  FIELD    → one per field
  FILE     → one per file (raw text, for full-text search)
  ANNOTATION → one per annotation usage
```

**Fields (common):**
| Field | Type | Description |
|---|---|---|
| `id` | STRING (unique) | `com.foo.Bar#method1` |
| `kind` | STRING | `CLASS`, `METHOD`, `FIELD`, `FILE` |
| `name` | STRING | Simple name (`Bar`, `method1`) |
| `qualifiedName` | STRING | FQN (`com.foo.Bar`) |
| `signature` | STRING | `public void method1(String, int)` |
| `package` | STRING | `com.foo` |
| `filePath` | STRING | Relative path in repo |
| `lineStart` | INT | Starting line |
| `lineEnd` | INT | Ending line |
| `access` | STRING | `public`, `private`, `protected`, `package` |
| `modifiers` | STRING[] | `abstract`, `static`, `final`, etc. |
| `annotations` | STRING[] | `@Service`, `@Autowired`, etc. |
| `superClass` | STRING | FQN of parent (for CLASS) |
| `implements` | STRING[] | FQNs of interfaces (for CLASS) |
| `params` | STRING[] | Parameter types (for METHOD) |
| `returnType` | STRING | Return type (for METHOD) |
| `body` | TEXT | Full source text (for FILE docs) |
| `tokens` | TEXT | Word-tokenised text (for full-text search) |

### 5.2 In-Memory Symbol Table

```java
// Simplified structure
Map<String, ClassSymbol> classes;        // FQN → ClassSymbol
Map<String, List<RefSymbol>> references; // FQN → all references
Map<String, List<String>> callGraph;     // method FQN → callee FQNs

class ClassSymbol {
    String fqn;
    String filePath;
    int lineStart, lineEnd;
    String superClass;
    List<String> interfaces;
    Map<String, MethodSymbol> methods;   // name → MethodSymbol
    Map<String, FieldSymbol> fields;     // name → FieldSymbol
    List<AnnotationSymbol> annotations;
}

class MethodSymbol {
    String fqn;          // com.foo.Bar#method1
    String signature;
    String returnType;
    List<String> params;
    int lineStart, lineEnd;
    List<String> callees; // resolved method FQNs called inside
}
```

---

## 6. API Design

### 6.1 REST Endpoints

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/search?q=&kind=&package=` | Full-text + symbol search (Lucene) |
| `GET` | `/api/definition?symbol=&file=&line=` | Go to definition |
| `GET` | `/api/references?symbol=` | Find all references |
| `GET` | `/api/hierarchy?class=` | Class hierarchy (super + sub) |
| `GET` | `/api/file?path=&start=&end=` | Fetch file content (line range) |
| `GET` | `/api/file/tree?path=` | Directory listing |
| `GET` | `/api/callgraph?method=&depth=` | Call graph (bounded depth) |
| `GET` | `/api/annotations?name=` | Find all usages of an annotation |
| `GET` | `/api/j2ee/beans?context=` | Spring/EJB bean definitions |
| `GET` | `/api/status` | Index status, file count, last update |
| `POST` | `/api/reindex` | Trigger full re-index |
| `GET` | `/api/git/log?branch=&limit=&author=` | Commit history (filterable by author/agent) |
| `GET` | `/api/git/diff?commit=&file=` | Unified diff for a commit (optional: single file) |
| `GET` | `/api/git/diff/compare?a=&b=` | Diff between two refs (branch, tag, commit) |
| `GET` | `/api/git/branches` | List branches with latest commit |
| `GET` | `/api/git/agent-changes?agent=&since=` | All commits by a specific agent since a timestamp |
| `GET` | `/api/git/commit?sha=` | Full commit detail: message, author, files changed, stats |
| `POST` | `/api/git/revert?sha=` | Revert a specific commit (creates a new revert commit) |
| `POST` | `/api/git/cherry-pick?sha=&branch=` | Cherry-pick a commit onto a branch |
| `WS` | `/ws` | WebSocket for live search, notifications, git events |

### 6.2 Response Format (JSON)

```json
// GET /api/definition?symbol=Bar&file=src/com/foo/Baz.java&line=42
{
  "symbol": "com.foo.Bar",
  "kind": "CLASS",
  "filePath": "src/com/foo/Bar.java",
  "lineStart": 15,
  "lineEnd": 230,
  "signature": "public class Bar extends AbstractBar implements Serializable",
  "members": [
    { "name": "method1", "kind": "METHOD", "line": 42, "signature": "public void method1(String s)" },
    { "name": "field1",  "kind": "FIELD",  "line": 20, "signature": "private String field1" }
  ]
}
```

---

## 7. Ingestion Pipeline

### 7.1 File Discovery
- Walk the mounted volume, filter by extension:
  `.java`, `.xml`, `.jsp`, `.properties`, `.yml`, `.yaml`, `.sql`, `.dtd`
- Skip: `node_modules`, `.git`, `target`, `build`, `dist`, binary files
- Configurable include/exclude patterns (YAML config file)

### 7.2 Parsing Strategy

| File Type | Parser | Output |
|---|---|---|
| `.java` | Eclipse JDT | AST → ClassSymbol, MethodSymbol, FieldSymbol |
| `.xml` (Spring, EJB, web.xml) | Jackson XML | Bean definitions, wiring refs |
| `.jsp` | Custom tokenizer | Scriptlet Java code → JDT; EL → ref extraction; tags → lib refs |
| `.properties` / `.yml` | Key-value parser | Property names (for search) |
| `.sql` | Text only | Full-text index |
| **Unparseable** | Raw text | FILE doc only, flagged `parseError: true` |

### 7.3 J2EE Wiring Resolution
This is the hardest parsing sub-problem:

- **Spring XML**: Parse `<bean id="foo" class="com.foo.Foo"/>` → link bean ID to class
- **Spring Annotations**: `@Component`, `@Service`, `@Autowired` → DI graph
- **EJB**: `@Stateless`, `@EJB` → EJB references
- **JSP**: `<c:out value="${bean.prop}"/>` → resolve `bean` to Spring bean → class
- **`web.xml`**: Servlet/filter/listener mappings → class refs
- **`persistence.xml`**: Entity classes → JPA mappings

The output is a **wiring graph** that augments the symbol table with
"this field is injected with that class" edges.

### 7.4 Incremental Updates
- `java.nio.file.WatchService` on the codebase root
- Debounce: batch changes over 500ms
- Re-parse changed files, update Lucene index + symbol table
- WebSocket broadcast: `file_changed`, `index_updated`

---

## 8. Git Integration (Agent Change Review)

### 8.1 Purpose

Enable a human reviewer to inspect, understand, and approve/revert changes made
by an AI agent before they are merged. The explorer becomes a **review surface**,
not just a navigation tool.

### 8.2 JGit Service Layer

A `GitService` class wraps JGit operations:

```java
class GitService {
    // Read operations
    List<CommitInfo> log(String branch, int limit, String authorFilter);
    String diff(String commitSha, String filePath);       // unified diff
    String diffCompare(String refA, String refB);         // two-ref diff
    List<BranchInfo> branches();
    CommitDetail commit(String sha);                       // full detail
    List<CommitInfo> agentChanges(String agentId, Instant since);

    // Write operations (gated by permission)
    String revert(String sha);                             // creates revert commit
    String cherryPick(String sha, String targetBranch);
}
```

### 8.3 Agent Change Tracking

Agents identify their commits via a **convention**:

- Commit author: `agent-name <agent@local>` (e.g. `hali <hali@local>`)
- Commit message prefix: `[agent:hali]` or a structured trailer:
  ```
  [agent:hali]
  Task: fix null pointer in OrderService
  Files: src/com/foo/OrderService.java, src/com/foo/OrderTest.java
  Risk: low
  ```

The `/api/git/agent-changes?agent=hali&since=...` endpoint filters by
author name and/or message prefix. This gives a **per-agent change feed**.

### 8.4 Review Workflow

```
Agent makes changes → commits to a feature branch
        │
        ▼
Reviewer opens explorer → "Agent Changes" panel
        │
        ▼
Sees list of agent commits (filterable by agent, date, risk)
        │
        ▼
Clicks a commit → sees:
  • Commit message + metadata
  • Files changed (with +/- line counts)
  • Side-by-side diff (Monaco diff editor)
  • For each changed symbol: "Go to definition" still works
    (can inspect the surrounding code in context)
        │
        ▼
Reviewer decides:
  ✓ Approve → merge branch (or just close the review)
  ✗ Revert → POST /api/git/revert?sha=... (creates revert commit)
  ~ Partial → cherry-pick specific files/commits
```

### 8.5 Diff Viewer (Monaco)

Monaco has a built-in **diff editor** (`monaco.editor.createDiffEditor`):

- Side-by-side: original (left) vs changed (right)
- Inline: unified diff with +/- highlighting
- Syntax-aware: Java, XML, JSP highlighting in both panes
- Click a changed line → "Go to definition" works on the *new* version
- File list sidebar: all changed files, click to switch diff
- Summary bar: `+42 −17 across 5 files`

### 8.6 Incremental Index on Git Events

When the reviewer checks out a different branch or the agent pushes new
commits:

1. JGit detects HEAD change (or WebSocket notification)
2. Ingestion pipeline re-indexes changed files only
3. Symbol table updates
4. WebSocket broadcast: `index_updated`
5. Browser refreshes breadcrumbs, search results

This means **navigation always reflects the currently-checked-out state**.

### 8.7 Security / Permissions

- **Read operations** (log, diff, branches): open to all users
- **Write operations** (revert, cherry-pick): require explicit permission
  - Config: `git.write.enabled=true` + optional API key or Spring Security role
  - Default: **write disabled**. Reviewer uses local git to merge; the
    explorer is read-only for git by default.
- **Revert safety**: `POST /api/git/revert` creates a *new* commit, never
  rewrites history. Original commit remains in the log.

---

## 9. Frontend (React + Monaco)

### 9.1 Layout

```
┌────────────────────────────────────────────────────────────────┐
│  Toolbar: [Search bar] [Breadcrumbs] [File tree toggle] [⚙]   │
├────────────┬───────────────────────────────────────────────────┤
│            │                                                   │
│  File      │                                                   │
│  Tree      │              Monaco Editor                        │
│  (left)    │              (centre)                             │
│            │                                                   │
│            │                                                   │
├────────────┴───────────────────────────────────────────────────┤
│  Bottom Panel: [Search Results] [References] [Hierarchy] [ⓘ]  │
└────────────────────────────────────────────────────────────────┘
```

#### Key UI Components

| Component | Description |
|---|---|
| **Search Bar** | Top bar, Ctrl+Shift+F. Debounced input → `/api/search`. Results in bottom panel. |
| **File Tree** | Left sidebar. Lazy-loaded via `/api/file/tree`. Click → open in Monaco. |
| **Monaco Editor** | Centre. Syntax highlighting (Java, XML, JSP). Ctrl+Click → go-to-def. Minimap. |
| **Breadcrumbs** | Above editor. `com > foo > Bar > method1`. Clickable. |
| **References Panel** | Bottom tab. List of all refs with file:line. Click → jump. |
| **Hierarchy Tree** | Bottom tab or right panel. Super/sub classes. Expandable. |
| **Call Graph** | Bottom tab. D3.js force-directed graph. Bounded depth (default 3). |
| **Git Log Panel** | Left sidebar tab or bottom panel. Commit history with author filter.
  Agent commits highlighted. Click → open diff. |
| **Diff Viewer** | Centre (replaces editor). Monaco `createDiffEditor`. Side-by-side or
  inline. File list sidebar. `+N −M` summary. |
| **Agent Changes Feed** | Bottom tab. Filterable list: agent name, date, risk level,
  files touched. "Review" button → opens diff. |
| **Branch Selector** | Toolbar dropdown. Current branch + list. Switch → triggers
  re-index. |
| **Tab Bar** | Above editor. Open files as tabs. Middle-click or × to close.
  Ctrl+Tab to cycle. Dirty indicator if file changed on disk. Max ~20 tabs. |
| **Change Indicator** | Small dot/badge on tab + file tree. Pulses when a file
  changes on disk. Click tab → editor reloads to latest content. |
| **Copy Link Button** | Toolbar + right-click in editor. Generates a canonical
  URL for the current file/line/symbol. Copies to clipboard. |
| **Status Bar** | Bottom. `Ln 42, Col 15 | UTF-8 | Java | branch: main | Index: 234,567 files | Last update: 2m ago` |

#### Keyboard Shortcuts

| Key | Action |
|---|---|
| `Ctrl+Shift+F` | Focus search bar |
| `Ctrl+Click` | Go to definition |
| `Alt+F7` | Find references |
| `Ctrl+H` | Show hierarchy |
| `Ctrl+G` | Show call graph |
| `Ctrl+P` | Quick file open (fuzzy) |
| `Ctrl+T` | Quick type/class open |
| `Ctrl+Shift+G` | Open Git Log panel |
| `Ctrl+Shift+D` | Open diff for selected commit |
| `Ctrl+Shift+A` | Open Agent Changes feed |
| `Ctrl+Tab` | Cycle open tabs |
| `Ctrl+W` | Close current tab |
| `Esc` | Close panel / cancel |

### 9.2 Multi-Tab Editor

- **Tab bar** sits above the Monaco editor. Each tab shows:
  - File name (basename) + a coloured dot per file type (Java=blue, XML=orange, JSP=green)
  - × button to close. Middle-click also closes.
  - **Change badge**: if the file changed on disk since it was opened, the tab
    shows a small pulsing dot. Clicking the tab reloads the file content.
- **Tab limit**: soft cap at 20 tabs. Oldest inactive tab is evicted first.
- **State preservation**: each tab remembers scroll position, cursor line, and
  any expanded/collapsed regions. Switching tabs is instant (no re-fetch).
- **Keyboard**: `Ctrl+Tab` cycles forward, `Ctrl+Shift+Tab` cycles backward,
  `Ctrl+W` closes current tab, `Ctrl+Shift+T` reopens last closed tab.
- **Implementation**: React state manages a `Tab[]` array. Each tab holds
  `{ filePath, line, symbol, content, isDirty, lastModified }`. Monaco
  editor instances are pooled (max 3 active DOM nodes; others are
  serialised to state and restored on activation).

### 9.3 Live File Change Listener

**Server side:**
- `java.nio.file.WatchService` watches the codebase root (already in the
  ingestion pipeline for re-indexing).
- On `ENTRY_MODIFY`, `ENTRY_CREATE`, `ENTRY_DELETE` events:
  1. Debounce: batch events over 300ms.
  2. Re-parse + re-index changed files (existing pipeline).
  3. Broadcast WebSocket message:
     ```json
     {
       "type": "file_changed",
       "path": "src/com/foo/Bar.java",
       "event": "modify",
       "timestamp": "2025-07-11T14:32:07Z"
     }
     ```
  4. If the change is git-related (branch switch, new commit), also broadcast:
     ```json
     { "type": "git_changed", "branch": "feature/xyz", "commit": "abc123" }
     ```

**Client side:**
- WebSocket listener receives `file_changed` events.
- For each open tab whose `filePath` matches:
  - Set `isDirty = true` on the tab.
  - Show pulsing dot on the tab + file tree entry.
  - **Do NOT auto-reload** (user might be reading). Instead, show a small
    "File updated — click to reload" tooltip on the tab.
- User clicks the tab → fetch latest content via `/api/file?path=...` →
  update Monaco model → clear `isDirty`.
- For `git_changed` events: update branch selector, trigger re-index
  notification in status bar (`Re-indexing... 34/234567`).

**Edge cases:**
- File deleted on disk: tab shows "File no longer exists" overlay. × to close.
- File renamed: tab shows "File moved to `new/path`" with a "Follow" button.
- Rapid changes (agent writing multiple files): debounce prevents tab
  flicker. Badge shows "3 changes" if multiple events batched.

### 9.4 Syntax Highlighting & Colour Themes

#### Language Coverage

Monaco ships with built-in language support. We register additional
languages for J2EE-specific file types:

| File Extension | Monaco Language | Source |
|---|---|---|
| `.java` | `java` | Built-in |
| `.xml` | `xml` | Built-in |
| `.jsp` | `jsp` | **Custom** (extend `html` + Java scriptlet tokens) |
| `.properties` | `ini` | Built-in (rename) |
| `.yml` / `.yaml` | `yaml` | Built-in |
| `.sql` | `sql` | Built-in |
| `.dtd` | `xml` | Built-in |
| `.html` / `.htm` | `html` | Built-in |
| `.css` | `css` | Built-in |
| `.js` / `.ts` | `javascript` / `typescript` | Built-in |
| `.sh` / `.bash` | `shell` | Built-in |
| `.gradle` / `.groovy` | `groovy` | Built-in |
| `.kt` | `kotlin` | Built-in |
| `.md` | `markdown` | Built-in |
| `.txt` / unknown | `plaintext` | Fallback |

**Custom JSP language** (`web/src/languages/jsp.ts`):
- Extends Monaco's `html` language definition.
- Adds token rules for:
  - `<% ... %>` scriptlet blocks → Java tokenizer
  - `<%= ... %>` expression blocks → Java tokenizer
  - `${ ... }` EL expressions → highlight as `variable`
  - `<c:tag>`, `<fmt:tag>`, `<spring:tag>` → highlight as `keyword`
  - JSP directives (`<%@ page ... %>`) → highlight as `annotation`
- Monarch tokenizer: ~150 rules. One-time cost, then fast.

#### Syntax Highlighting Features

- **Token-level highlighting**: keywords, types, strings, comments,
  annotations, numbers, operators — all colour-coded per theme.
- **Bracket matching**: Java `{}`, `()`, `[]` + XML tags + JSP scriptlets.
- **Comment toggling**: `Ctrl+/` for line comments, `Ctrl+Shift+A` for
  block comments (Java). `<!-- -->` for XML/JSP.
- **Folding**: Java methods/classes, XML elements, JSP scriptlet blocks.
  `Ctrl+Shift+[` / `Ctrl+Shift+]` to fold/unfold.
- **Word highlighting**: selecting a symbol highlights all occurrences
  in the current file (Monaco built-in).
- **Indentation guides**: subtle vertical lines showing block structure.
- **Minimap**: right edge of editor. Shows code structure at a glance.
  Toggleable.

#### Colour Themes

Three built-in themes + user-customisable:

| Theme | Background | Target |
|---|---|---|
| **Dark** (default) | `#1e1e1e` | Low-light offices, night shifts |
| **Light** | `#ffffff` | Bright rooms, printing |
| **High Contrast** | `#000000` | Accessibility, visual impairment |

**Theme structure** (`web/src/themes/`):
```
themes/
├── dark.ts          # Default dark theme
├── light.ts         # Light theme
├── high-contrast.ts # High contrast theme
├── base.ts          # Shared token colour mappings
└── index.ts         # Theme registry + switcher
```

Each theme file defines a Monaco `editor.IStandaloneThemeData`:
```typescript
// dark.ts (excerpt)
export const darkTheme: editor.IStandaloneThemeData = {
  base: 'vs-dark',
  inherit: true,
  rules: [
    { token: 'keyword',          foreground: 'C586C0' },
    { token: 'type',             foreground: '4EC9B0' },
    { token: 'string',           foreground: 'CE9178' },
    { token: 'comment',          foreground: '6A9955' },
    { token: 'annotation',       foreground: 'DCA641' },
    { token: 'number',           foreground: 'B5CEA8' },
    { token: 'variable',         foreground: '9CDCFE' },
    { token: 'el-expression',    foreground: 'D4A7FF' },  // JSP EL
    { token: 'jsp-scriptlet',    foreground: '569CD6' },  // JSP <% %>
    { token: 'xml-tag',          foreground: '569CD6' },
    { token: 'xml-attr',         foreground: '9CDCFE' },
  ],
  colors: {
    'editor.background':             '#1e1e1e',
    'editor.foreground':             '#d4d4d4',
    'editor.lineHighlightBackground':'#2a2a2a',
    'editorCursor.foreground':       '#aeafad',
    'editorIndentGuide.background1': '#404040',
    'editorIndentGuide.activeBackground1': '#707070',
    'minimap.background':            '#1e1e1e',
    'tab.activeBackground':          '#2a2a2a',
    'tab.inactiveBackground':        '#252526',
    'tab.activeBorder':              '#007acc',
    'statusBar.background':          '#007acc',
    'statusBar.foreground':          '#ffffff',
  }
};
```

**Theme switching:**
- Toolbar button (🎨 icon) → dropdown: Dark / Light / High Contrast
- `Ctrl+Shift+P` → "Toggle Theme"
- Persisted in `localStorage` + sent as `?theme=dark` URL param
  (so a shared link can specify the theme)
- Switch is instant: `monaco.editor.setTheme(themeName)` — no page reload.

**Custom themes (Phase 5):**
- Users can export/import theme JSON files.
- Admin can deploy a team-wide default theme via `application.yml`:
  ```yaml
  jolt:
    theme:
      default: dark
      allowed: [dark, light, high-contrast]
  ```

#### Diff Viewer Highlighting

The Monaco diff editor inherits the active theme. Additional diff-specific
colours:
```typescript
colors: {
  'diffEditor.insertedTextBackground':    '#264f2840',  // green tint
  'diffEditor.removedTextBackground':     '#6b202040',  // red tint
  'diffEditor.insertedLineBackground':    '#264f2820',  // subtle green
  'diffEditor.removedLineBackground':     '#6b202020',  // subtle red
}
```
These are appended to the active theme at runtime, so diff colours
adapt to dark/light automatically.

### 9.5 Copy Link (Shareable File URLs)

**URL format:**
```
https://explorer.internal/?file=src/com/foo/Bar.java&line=42&symbol=method1&branch=main
```

| Param | Required | Description |
|---|---|---|
| `file` | yes | Relative file path |
| `line` | no | Line number to scroll to |
| `symbol` | no | Symbol name to highlight (e.g. `method1`) |
| `branch` | no | Git branch (default: current) |
| `diff` | no | If set to a commit SHA, opens diff view for this file |

**Behaviour:**
- **Copy Link button** in toolbar + right-click context menu in editor.
- Generates the URL from the current tab's state (file, cursor line,
  selected symbol) + current branch.
- Copies to clipboard. Shows a toast: `Link copied: https://...`.
- **Opening a link**: on page load, the app parses query params:
  1. If `branch` is set, check out that branch (server-side).
  2. Fetch the file, open in a new tab.
  3. Scroll to `line`, highlight `symbol` if provided.
  4. If `diff` is set, open the diff viewer for that file at that commit.
- **Use cases**:
  - Paste a link in Slack/Teams: "Look at line 42 of Bar.java"
  - Agent includes links in its commit message: "Changed `Bar.java` —
    see `?file=...&line=42`"
  - Reviewer shares a diff link: `?file=...&diff=abc123`
- **Security**: links are read-only. No auth token in the URL (use session
  cookie or header auth if needed).

---

## 10. Performance Considerations

| Concern | Strategy |
|---|---|
| **Indexing 500K files** | Parallel parsing (ForkJoinPool). ~5–15 min on modern hardware. Show progress. |
| **Memory** | Symbol table for 500K files ≈ 2–4 GB RAM. Lucene index ≈ 1–2 GB on disk. Plan for 8 GB heap. |
| **Search latency** | Lucene queries: <50ms. Symbol table lookups: <1ms. API response: <100ms. |
| **Find references (large)** | Can return 10K+ refs. Paginate (50 per page). Stream via WebSocket for very large results. |
| **Browser memory** | Monaco virtualises lines. Don't load >5000-line files fully. Stream via `/api/file?start=&end=`. |
| **Concurrent users** | Read-only API. No locking. 10–20 concurrent users fine on a single node. |

---

## 11. Phased Delivery

### Phase 0 — Spike (2 weeks)
- [ ] Stand up Spring Boot + Monaco in a Docker container
- [ ] Parse a small Java project with JDT, dump AST to JSON
- [ ] Index into Lucene, query from browser
- **Exit criteria:** Can search a 100-file Java project from a browser.

### Phase 1 — MVP (6–8 weeks)
- [ ] Full ingestion pipeline (Java + XML)
- [ ] Lucene index with structured schema
- [ ] In-memory symbol table
- [ ] REST API: search, go-to-definition, file viewer
- [ ] React + Monaco UI: file tree, editor, search bar, breadcrumbs
- [ ] **Syntax highlighting**: Java, XML, SQL, YAML, properties, shell,
  Groovy, Kotlin, Markdown. Bracket matching, folding, minimap.
- [ ] **Colour themes**: Dark (default), Light, High Contrast. Toolbar
  switcher. `?theme=` URL param. Persisted in localStorage.
- [ ] **Multi-tab editor** (tab bar, Ctrl+Tab, close/reopen, state preservation)
- [ ] **Live file change listener** (WatchService → WebSocket → tab badge)
- [ ] **Copy Link** (canonical URL with file/line/symbol/branch params)
- [ ] Docker deployment with volume mount
- [ ] Basic dark/light theme
- **Exit criteria:** Can open a 10K-file Java project, search, click through to
  definitions, open multiple files in tabs, see live update badges, and share
  a file link that opens at the right line.

### Phase 2 — Navigation (4–6 weeks)
- [ ] Find references (Java)
- [ ] Class hierarchy (extends/implements)
- [ ] Call graph (depth-limited, D3.js)
- [ ] JSP parsing (scriptlets + EL)
- [ ] **Custom JSP Monaco language** (Monarch tokenizer for scriptlets,
  EL expressions, JSP tags, directives)
- [ ] Spring XML bean resolution
- [ ] WebSocket live search
- **Exit criteria:** Can navigate a 50K-file J2EE project with DI-aware references.

### Phase 3 — Git Review (4–6 weeks)
- [ ] JGit service layer: log, diff, branches, commit detail
- [ ] Agent change tracking (author filter, message prefix parsing)
- [ ] REST API: `/api/git/*` endpoints
- [ ] Monaco diff editor integration (side-by-side + inline)
- [ ] Git Log panel (left sidebar) with agent highlighting
- [ ] Agent Changes feed (bottom tab)
- [ ] Branch selector in toolbar
- [ ] Incremental re-index on branch switch / new commits
- [ ] Revert + cherry-pick (write operations, permission-gated)
- [ ] WebSocket: `git_commit`, `branch_changed` events
- **Exit criteria:** Can review an agent's branch, read diffs with syntax
  highlighting, navigate into changed code, and revert a bad commit.

### Phase 4 — J2EE Depth (4–6 weeks)
- [ ] EJB annotation resolution
- [ ] `web.xml` servlet/filter mapping
- [ ] `persistence.xml` entity mapping
- [ ] JSP tag library resolution
- [ ] Annotation-based search (`@Autowired`, `@Service`, etc.)
- [ ] Wiring graph view (DI dependencies)
- **Exit criteria:** Can trace a request from JSP → Servlet → EJB → Entity.

### Phase 5 — Polish & Scale (4–8 weeks)
- [ ] Incremental indexing (WatchService)
- [ ] Performance tuning (caching, pagination, lazy loading)
- [ ] User bookmarks, recent files, search history
- [ ] Multi-project support (switch between codebases)
- [ ] **Custom themes**: user export/import, admin default via config
- [ ] Configurable include/exclude patterns
- [ ] CI/CD: Docker image, health checks, index warm-up
- [ ] Documentation + onboarding guide
- **Exit criteria:** Production-ready for a team of 5–20 developers.

### Total estimated timeline: **~8–12 months** (solo, experienced)

---

## 12. Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Legacy Java syntax breaks JDT parser | High | Medium | Graceful degradation: index as raw text, flag `parseError`. Custom pre-processor for known edge cases. |
| J2EE wiring is too complex for v1 | Medium | High | Defer to Phase 3. Ship Java-only navigation first. |
| Memory pressure at 500K+ files | Medium | Medium | Profile early (Phase 1). Consider off-heap symbol table or partitioned index. |
| Scope creep toward "full IDE" | High | High | Strict non-goals list. Phase gates. Say no to editing/refactoring. |
| Monaco performance with large files | Low | Medium | Virtualise. Stream file content. Cap editor at 10K lines. |
| Team adoption (why not just use IntelliJ?) | Medium | High | Position as **complementary**: lightweight, shareable, server-side, no install. Good for CI dashboards, code review, onboarding. |

---

## 13. Open Questions

1. **Auth / multi-tenancy?** Single team on a LAN, or multi-project with auth?
   → Suggest: start single-tenant, add Spring Security later if needed.
2. **Read-only or allow annotations/notes?**
   → Suggest: read-only for v1. Notes/bookmarks in Phase 4.
3. **Codebase access: git checkout or live mount?**
   → Suggest: git checkout to a volume. The explorer reads from the working tree.
   Agent commits land on feature branches; reviewer switches branches in the UI.
4. **Agent commit conventions?**
   → Need a standard: author name (`agent-name <agent@local>`), message prefix
   (`[agent:name]`), and optional structured trailers (task, files, risk).
   → Should this be enforced by the agent framework or just a convention?
5. **Multiple Java versions in one codebase?**
   → JDT handles this, but confirm the oldest version in the codebase.
6. **Non-Java sources?** (COBOL mainframes, PL/SQL, old C++)
   → Out of scope for v1. Index as text only if present.
7. **Write operations: how permissive?**
   → Default: read-only git. Revert/cherry-pick behind a permission flag.
   → Should the explorer ever push to a remote? Suggest: no. Local git only.

---

## 14. Repository Structure (proposed)

```
jolt/
├── docker/
│   └── Dockerfile            # Optional: docker run -v /code:/code jolt/jolt
├── server/
│   ├── src/main/java/
│   │   ├── com/jolt/
│   │   │   ├── JoltApp.java  # Main entry point. Picocli CLI. Starts Javalin.
│   │   │   ├── api/          # REST routes, WebSocket handlers
│   │   │   ├── index/        # Lucene index management
│   │   │   ├── parse/        # JDT, XML, JSP parsers
│   │   │   ├── nav/          # Symbol table, hierarchy, call graph
│   │   │   ├── j2ee/         # Spring, EJB, JSP wiring
│   │   │   ├── git/          # JGit service, agent tracking, diff gen
│   │   │   ├── model/        # ClassSymbol, MethodSymbol, CommitInfo, etc.
│   │   │   └── watch/        # File watcher, .jolt/ directory management
│   │   └── resources/
│   │       └── static/       # Built frontend (React/Vite output embedded)
│   └── pom.xml               # maven-shade-plugin → target/jolt.jar
├── web/
│   ├── src/
│   │   ├── components/
│   │   │   ├── Editor.tsx
│   │   │   ├── FileTree.tsx
│   │   │   ├── SearchBar.tsx
│   │   │   ├── Breadcrumbs.tsx
│   │   │   ├── ReferencesPanel.tsx
│   │   │   ├── HierarchyTree.tsx
│   │   │   ├── CallGraph.tsx
│   │   │   ├── GitLog.tsx
│   │   │   ├── DiffViewer.tsx
│   │   │   ├── AgentChanges.tsx
│   │   │   ├── BranchSelector.tsx
│   │   │   ├── TabBar.tsx
│   │   │   ├── ChangeIndicator.tsx
│   │   │   ├── CopyLinkButton.tsx
│   │   │   └── StatusBar.tsx
│   │   ├── hooks/
│   │   ├── api/              # Fetch/WebSocket clients
│   │   ├── languages/
│   │   │   └── jsp.ts        # Custom JSP Monarch tokenizer
│   │   ├── themes/
│   │   │   ├── base.ts       # Shared token colour mappings
│   │   │   ├── dark.ts       # Default dark theme
│   │   │   ├── light.ts      # Light theme
│   │   │   ├── high-contrast.ts
│   │   │   └── index.ts      # Theme registry + switcher
│   │   ├── App.tsx
│   │   └── main.tsx
│   ├── package.json
│   └── vite.config.ts
├── tests/
│   ├── server/
│   └── web/
├── docs/
│   ├── architecture.md
│   ├── api.md
│   └── deployment.md
└── README.md
```

---

*Jolt. Plan authored by Hali. For discussion purposes only — not to be implemented in this sandbox.*