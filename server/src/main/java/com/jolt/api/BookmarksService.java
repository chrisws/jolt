package com.jolt.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Manages user bookmarks, recent files, and search history in a local JSON file. */
public class BookmarksService {

  private static final Logger log = LoggerFactory.getLogger(BookmarksService.class);
  private static final ObjectMapper mapper = new ObjectMapper();

  private final Path prefsFile;

  public BookmarksService(Path homeDir) {
    this.prefsFile = homeDir.toAbsolutePath().resolve(".jolt").resolve("prefs.json");
    // Ensure directory exists
    try {
      Files.createDirectories(prefsFile.getParent());
    } catch (Exception e) {
      log.warn("Could not create prefs dir: {}", e.getMessage());
    }
  }

  /** Add a bookmark. */
  public Map<String, Object> addBookmark(String path, int line, String label) {
    var root = loadPrefs();

    ArrayNode bookmarks = getArray(root, "bookmarks");
    ObjectNode bm = mapper.createObjectNode();
    bm.put("path", path);
    bm.put("line", line);
    bm.put("label", label != null ? label : "");
    bm.put("createdAt", Instant.now().toString());

    bookmarks.add(bm);
    savePrefs(root);

    return Map.of(
        "path", path, "line", line, "label", label != null ? label : "",
        "createdAt", Instant.now().toString()
    );
  }

  /** Remove a bookmark by path+line. */
  public boolean removeBookmark(String path, int line) {
    var root = loadPrefs();

    ArrayNode bookmarks = getArray(root, "bookmarks");
    Iterator<JsonNode> it = bookmarks.iterator();
    while (it.hasNext()) {
      JsonNode bm = it.next();
      if (bm.path("path").asText().equals(path) && bm.path("line").asInt() == line) {
        it.remove();
        savePrefs(root);
        return true;
      }
    }
    return false;
  }

  /** Get all bookmarks. */
  public List<Map<String, Object>> getBookmarks() {
    var root = loadPrefs();

    ArrayNode bookmarks = getArray(root, "bookmarks");
    List<Map<String, Object>> result = new ArrayList<>();

    for (JsonNode bm : bookmarks) {
      result.add(Map.of(
          "path", bm.path("path").asText(),
          "line", bm.path("line").asInt(),
          "label", bm.path("label").asText(),
          "createdAt", bm.path("createdAt").asText()
      ));
    }

    return result;
  }

  /** Record a recently opened file (maintains a list of last 20). */
  public void recordRecentFile(String path) {
    var root = loadPrefs();

    ArrayNode recent = getArray(root, "recentFiles");

    // Remove if already present (to move to front)
    Iterator<JsonNode> it = recent.iterator();
    while (it.hasNext()) {
      if (it.next().path("path").asText().equals(path)) {
        it.remove();
      }
    }

    ObjectNode entry = mapper.createObjectNode();
    entry.put("path", path);
    entry.put("openedAt", Instant.now().toString());

    // Prepend by rebuilding the array with entry first
    ArrayNode newRecent = mapper.createArrayNode();
    newRecent.add(entry);
    for (JsonNode node : recent) { newRecent.add(node); }
    root.set("recentFiles", newRecent);

    // Cap at 20
    while (newRecent.size() > 20) {
      newRecent.remove(newRecent.size() - 1);
    }

    savePrefs(root);
  }

  /** Get recent files. */
  public List<Map<String, Object>> getRecentFiles() {
    var root = loadPrefs();

    ArrayNode recent = getArray(root, "recentFiles");
    List<Map<String, Object>> result = new ArrayList<>();

    for (JsonNode entry : recent) {
      result.add(Map.of(
          "path", entry.path("path").asText(),
          "openedAt", entry.path("openedAt").asText()
      ));
    }

    return result;
  }

  /** Record a search query in history (last 50). */
  public void recordSearch(String query) {
    var root = loadPrefs();

    ArrayNode history = getArray(root, "searchHistory");

    // Remove duplicate if present
    Iterator<JsonNode> it = history.iterator();
    while (it.hasNext()) {
      if (it.next().path("query").asText().equals(query)) {
        it.remove();
      }
    }

    ObjectNode entry = mapper.createObjectNode();
    entry.put("query", query);
    entry.put("searchedAt", Instant.now().toString());

    // Prepend by rebuilding the array with entry first
    ArrayNode newHistory = mapper.createArrayNode();
    newHistory.add(entry);
    for (JsonNode node : history) { newHistory.add(node); }
    root.set("searchHistory", newHistory);

    // Cap at 50
    while (newHistory.size() > 50) {
      newHistory.remove(newHistory.size() - 1);
    }

    savePrefs(root);
  }

  /** Get search history. */
  public List<Map<String, Object>> getSearchHistory() {
    var root = loadPrefs();

    ArrayNode history = getArray(root, "searchHistory");
    List<Map<String, Object>> result = new ArrayList<>();

    for (JsonNode entry : history) {
      result.add(Map.of(
          "query", entry.path("query").asText(),
          "searchedAt", entry.path("searchedAt").asText()
      ));
    }

    return result;
  }

  // ─── Private helpers ────────────────────────────────────────────

  private ObjectNode loadPrefs() {
    try {
      if (Files.exists(prefsFile)) {
        return (ObjectNode) mapper.readTree(prefsFile.toFile());
      }
    } catch (Exception e) {
      log.warn("Failed to load prefs: {}", e.getMessage());
    }

    ObjectNode root = mapper.createObjectNode();
    root.putArray("bookmarks");
    root.putArray("recentFiles");
    root.putArray("searchHistory");
    return root;
  }

  private void savePrefs(ObjectNode root) {
    try {
      mapper.writerWithDefaultPrettyPrinter().writeValue(prefsFile.toFile(), root);
    } catch (Exception e) {
      log.warn("Failed to save prefs: {}", e.getMessage());
    }
  }

  private ArrayNode getArray(ObjectNode root, String key) {
    JsonNode existing = root.get(key);
    if (existing instanceof ArrayNode arr) return arr;

    ArrayNode arr = mapper.createArrayNode();
    root.set(key, arr);
    return arr;
  }

}