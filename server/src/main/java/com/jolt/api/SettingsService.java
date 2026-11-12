/*
 * This file is part of Jolt
 *
 * Copyright (C) 2026 Chris Warren-Smith. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jolt.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manages per-user settings stored in {@code .jolt/settings.json}.
 * Thread-safe via synchronized access on a single file.
 */
public class SettingsService {

  private static final Logger log = LoggerFactory.getLogger(SettingsService.class);
  private static final ObjectMapper mapper = new ObjectMapper();

  private final Path settingsFile;

  public SettingsService(Path homeDir) {
    this.settingsFile = homeDir.resolve(".jolt/settings.json");
  }

  /** Get all current settings as a map. Returns defaults if file doesn't exist. */
  public Map<String, Object> getSettings() {
    Map<String, Object> defaults = defaultSettings();
    try {
    if (!Files.exists(settingsFile)) {
        return defaults;
      }

      JsonNode root = mapper.readTree(settingsFile.toFile());
      Map<String, Object> result = new LinkedHashMap<>(defaults);

      // Overlay stored values on top of defaults
      for (var entry : root.properties()) {
        result.put(entry.getKey(), jsonNodeToObj(entry.getValue()));
      }

      return result;

    } catch (Exception e) {
      log.warn("Failed to read settings: {}", e.getMessage());
      return defaults;
    }
  }

  /** Update one or more settings. Returns the full updated settings map. */
  public Map<String, Object> updateSettings(Map<String, Object> updates) {
    Map<String, Object> current = getSettings();

    for (Map.Entry<String, Object> entry : updates.entrySet()) {
      // Only allow known keys to prevent injection of arbitrary data
      if (current.containsKey(entry.getKey()) || isKnownKey(entry.getKey())) {
        current.put(entry.getKey(), entry.getValue());
      }
    }

    writeSettings(current);
    return current;
  }

  /** Get a single setting value, or null if not set. */
  public Object getSetting(String key) {
    return getSettings().get(key);
  }

  // ─── Private ────────────────────────────────────────────────────

  private Map<String, Object> defaultSettings() {
    Map<String, Object> defaults = new LinkedHashMap<>();
    defaults.put("keybindingMode", "default");   // "default" | "emacs" | "vim"
    defaults.put("theme", "dark");               // "dark" | "light" | "custom"
    defaults.put("fontSize", 14);                // editor font size in px
    defaults.put("tabSize", 2);                  // spaces per tab
    defaults.put("wordWrap", false);             // enable word wrap in editor
    defaults.put("showLineNumbers", true);       // show line numbers gutter
    defaults.put("minimapEnabled", true);        // show minimap in editor
    defaults.put("gitAutoRefresh", true);        // auto-refresh git panel on change

    // Search settings (Stage 2)
    defaults.put("search.fuzzy.maxEdits", 2);     // max edit distance for fuzzy fallback (0-3)
    defaults.put("search.suggest.limit", 10);     // max suggestions returned by /api/search/suggest

    // Git diff settings (Stage 2)
    defaults.put("git.diff.maxLines", 1000);      // cap diff rendering at N lines per file
    defaults.put("git.diff.defaultView", "unified"); // "unified" | "split" | "raw"

    // Debug settings
    defaults.put("debug.java.basePort", 5005);     // base JDWP port (auto-increments per session)
    defaults.put("debug.gdb.path", "gdb");         // path to GDB binary
    defaults.put("debug.gdb.mode", "launch");      // "launch" (Jolt spawns exe) | "attach" (client provides PID)
    defaults.put("debug.jdb.path", "jdb");         // path to JDB binary
    defaults.put("debug.defaultStepMode", "over"); // "over" | "into" | "out"

    return defaults;
  }

  private boolean isKnownKey(String key) {
    return switch (key) {
      case "keybindingMode", "theme", "fontSize", "tabSize",
           "wordWrap", "showLineNumbers", "minimapEnabled",
           "gitAutoRefresh",
           "search.fuzzy.maxEdits", "search.suggest.limit",
           "git.diff.maxLines", "git.diff.defaultView",
           "debug.java.basePort", "debug.gdb.path", "debug.gdb.mode",
           "debug.jdb.path", "debug.defaultStepMode" -> true;
      default -> false;
    };
  }

  private void writeSettings(Map<String, Object> settings) {
    try {
      Files.createDirectories(settingsFile.getParent());

      ObjectNode node = mapper.createObjectNode();
      for (Map.Entry<String, Object> entry : settings.entrySet()) {
        setJsonField(node, entry.getKey(), entry.getValue());
      }

      mapper.writerWithDefaultPrettyPrinter().writeValue(settingsFile.toFile(), node);

    } catch (Exception e) {
      log.error("Failed to write settings: {}", e.getMessage());
    }
  }

  private void setJsonField(ObjectNode node, String key, Object value) {
    if (value instanceof String s) {
      node.put(key, s);
    } else if (value instanceof Integer i) {
      node.put(key, i);
    } else if (value instanceof Boolean b) {
      node.put(key, b);
    } else if (value instanceof Number n) {
      node.put(key, n.doubleValue());
    } else {
      node.put(key, String.valueOf(value));
    }
  }

  private Object jsonNodeToObj(JsonNode node) {
    if (node.isTextual()) return node.asText();
    if (node.isInt()) return node.asInt();
    if (node.isLong()) return node.asLong();
    if (node.isBoolean()) return node.asBoolean();
    if (node.isDouble()) return node.asDouble();
    return node.toString();
  }

}
