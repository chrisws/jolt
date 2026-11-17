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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Shared JSON prefs file reader/writer for user preference services. */
public class PrefsStore {

  private static final Logger log = LoggerFactory.getLogger(PrefsStore.class);
  private static final ObjectMapper mapper = new ObjectMapper();
  private static final int SCHEMA_VERSION = 1;

  private final Path prefsFile;
  private final boolean writable;

  public PrefsStore(Path homeDir) {
    this.prefsFile = homeDir.toAbsolutePath().resolve(".jolt").resolve("prefs.json");

    boolean canWrite = true;
    try {
      Files.createDirectories(prefsFile.getParent());
      // Probe writability by creating and deleting a temp file in the same dir.
      Path probe = Files.createTempFile(prefsFile.getParent(), ".jolt-probe", ".tmp");
      Files.delete(probe);
    } catch (AccessDeniedException e) {
      log.error("Read-only filesystem detected at {}: {}", prefsFile.getParent(), e.getMessage());
      canWrite = false;
    } catch (Exception e) {
      log.warn("Could not create prefs dir: {}", e.getMessage());
      canWrite = false;
    }
    this.writable = canWrite;
  }

  /** Returns true if the prefs file can be written to. */
  public boolean isWritable() { return writable; }

  public ObjectNode load() {
    try {
      if (Files.exists(prefsFile)) {
        ObjectNode root = (ObjectNode) mapper.readTree(prefsFile.toFile());
        migrate(root);
        return root;
      }
    } catch (Exception e) {
      log.warn("Failed to load prefs: {}", e.getMessage());
    }

    return defaultRoot();
  }

  /** Atomically persists the prefs object to disk. */
  public void save(ObjectNode root) {
    if (!writable) {
      log.error("Skipping prefs save — filesystem is read-only");
      return;
    }

    root.put("version", SCHEMA_VERSION);

    Path tmp = null;
    try {
      tmp = Files.createTempFile(prefsFile.getParent(), ".jolt-prefs", ".tmp");
      mapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), root);
      Files.move(tmp, prefsFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (AccessDeniedException e) {
      log.error("Read-only filesystem — cannot save prefs: {}", e.getMessage());
    } catch (IOException e) {
      log.warn("Failed to save prefs: {}", e.getMessage());
    } catch (Exception e) {
      log.warn("Unexpected error saving prefs: {}", e.getMessage());
    } finally {
      if (tmp != null) {
        try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
      }
    }
  }

  public ArrayNode getArray(ObjectNode root, String key) {
    JsonNode existing = root.get(key);
    if (existing instanceof ArrayNode arr) return arr;

    ArrayNode arr = mapper.createArrayNode();
    root.set(key, arr);
    return arr;
  }

  /** Returns a fresh default prefs object with schema version stamped. */
  private ObjectNode defaultRoot() {
    ObjectNode root = mapper.createObjectNode();
    root.put("version", SCHEMA_VERSION);
    root.putArray("bookmarks");
    root.putArray("recentFiles");
    root.putArray("searchHistory");
    return root;
  }

  /** Apply schema migrations for older prefs files. */
  private void migrate(ObjectNode root) {
    int version = root.path("version").asInt(0);
    if (version < SCHEMA_VERSION) {
      // v0 → v1: stamp version, ensure required arrays exist.
      root.put("version", SCHEMA_VERSION);
      if (!root.has("bookmarks")) { root.putArray("bookmarks"); }
      if (!root.has("recentFiles")) { root.putArray("recentFiles"); }
      if (!root.has("searchHistory")) { root.putArray("searchHistory"); }
      log.info("Migrated prefs from v{} to v{}", version, SCHEMA_VERSION);
    }
  }

  public static ObjectMapper mapper() { return mapper; }
}