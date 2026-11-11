package com.jolt.parse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads a CMake-generated {@code compile_commands.json} and provides
 * per-file compiler flags for use by {@link CppParser}.
 */
public class CompileDb {

  private static final Logger log = LoggerFactory.getLogger(CompileDb.class);

  /** One entry from compile_commands.json. */
  private record Entry(String directory, String command, String file) {}

  private final List<Entry> entries = new ArrayList<>();
  private final Map<String, Entry> fileIndex = new LinkedHashMap<>(); // relative path → entry

  /**
   * Load the compile database from the given path.
   * Expected JSON: an array of objects with "directory", "command", "file" keys.
   */
  public void load(Path dbPath) {
    entries.clear();
    fileIndex.clear();

    if (!Files.exists(dbPath)) {
      log.warn("compile_commands.json not found at {}", dbPath);
      return;
    }

    try {
      ObjectMapper mapper = new ObjectMapper();
      JsonNode root = mapper.readTree(dbPath.toFile());

      if (!root.isArray()) {
        log.warn("compile_commands.json root is not an array");
        return;
      }

      for (JsonNode node : root) {
        String directory = text(node, "directory");
        String command = text(node, "command");
        String file = text(node, "file");

        if (file == null || file.isEmpty()) continue;

        Entry entry = new Entry(directory, command, file);
        entries.add(entry);

        // Index by absolute path (normalised) for fast lookup
        Path absFile = Path.of(file).toAbsolutePath().normalize();
        fileIndex.put(absFile.toString(), entry);
      }

      log.info("Loaded {} compile database entries from {}", entries.size(), dbPath);

    } catch (IOException e) {
      log.error("Failed to read compile_commands.json: {}", e.getMessage());
    }
  }

  /** Convenience: try common locations for the compile database. */
  public static CompileDb loadFromProject(Path projectRoot) {
    CompileDb db = new CompileDb();
    // CMake convention: build/compile_commands.json or project-root/compile_commands.json
    Path candidate1 = projectRoot.resolve("build").resolve("compile_commands.json");
    Path candidate2 = projectRoot.resolve("compile_commands.json");

    if (Files.exists(candidate1)) {
      db.load(candidate1);
    } else if (Files.exists(candidate2)) {
      db.load(candidate2);
    } else {
      log.warn("No compile_commands.json found in {} or {}", candidate1, candidate2);
    }
    return db;
  }

  /**
   * Return the compiler flags for a given source file.
   * The returned list excludes the compiler binary, -c, -o &lt;arg&gt;, and the source file itself.
   * Returns an empty list if the file is not in the database.
   */
  public List<String> getFlags(String filePath) {
    Path abs = Path.of(filePath).toAbsolutePath().normalize();
    Entry entry = fileIndex.get(abs.toString());

    // Fallback: try matching by filename suffix (handles relative vs absolute mismatches)
    if (entry == null) {
      String fileName = abs.getFileName().toString();
      for (Map.Entry<String, Entry> e : fileIndex.entrySet()) {
        if (e.getKey().endsWith(fileName)) {
          entry = e.getValue();
          break;
        }
      }
    }

    if (entry == null || entry.command() == null) {
      return List.of();
    }

    return parseCommandFlags(entry.command());
  }

  /** All source files tracked by this database (absolute paths). */
  public List<String> getAllFiles() {
    List<String> files = new ArrayList<>(fileIndex.keySet());
    return files;
  }

  /** The working directory for a given file's compilation. */
  public String getDirectory(String filePath) {
    Path abs = Path.of(filePath).toAbsolutePath().normalize();
    Entry entry = fileIndex.get(abs.toString());
    return entry != null ? entry.directory() : "";
  }

  /** Number of entries loaded. */
  public int size() { return entries.size(); }

  /** Whether any entries were loaded. */
  public boolean isEmpty() { return entries.isEmpty(); }

  // ─── internal ────────────────────────────────────────────────────────────────

  /**
   * Tokenise a compiler command and extract only the flags we need for AST extraction.
   * Strips: compiler binary, -c, -o &lt;arg&gt;, the source file, -o output target.
   */
  static List<String> parseCommandFlags(String command) {
    String[] tokens = command.split("\\s+");
    List<String> flags = new ArrayList<>();

    for (int i = 0; i < tokens.length; i++) {
      String tok = tokens[i];

      // Skip the compiler binary (first token)
      if (i == 0) continue;

      // Skip -c (compile only)
      if (tok.equals("-c")) continue;

      // Skip -o and its argument
      if (tok.equals("-o")) { i++; continue; }

      // Skip -MF, -MQ, -MT and their args (dependency file flags)
      if (tok.equals("-MF") || tok.equals("-MQ") || tok.equals("-MT")) { i++; continue; }

      // Skip the source file (ends with .cpp, .cc, .cxx, .c, .h, .hpp)
      if (isSourceFile(tok)) continue;

      flags.add(tok);
    }

    return flags;
  }

  private static boolean isSourceFile(String token) {
    String lower = token.toLowerCase();
    return lower.endsWith(".cpp") || lower.endsWith(".cc") || lower.endsWith(".cxx")
        || lower.endsWith(".c") || lower.endsWith(".h") || lower.endsWith(".hpp")
        || lower.endsWith(".hh");
  }

  private static String text(JsonNode node, String field) {
    JsonNode child = node.get(field);
    return child != null ? child.asText() : null;
  }
}
