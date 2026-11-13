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

import java.nio.file.Files;
import java.nio.file.Path;

/** Shared JSON prefs file reader/writer for user preference services. */
public class PrefsStore {

  private static final Logger log = LoggerFactory.getLogger(PrefsStore.class);
  private static final ObjectMapper mapper = new ObjectMapper();

  private final Path prefsFile;

  public PrefsStore(Path homeDir) {
    this.prefsFile = homeDir.toAbsolutePath().resolve(".jolt").resolve("prefs.json");
    try {
      Files.createDirectories(prefsFile.getParent());
    } catch (Exception e) {
      log.warn("Could not create prefs dir: {}", e.getMessage());
    }
  }

  public ObjectNode load() {
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

  public void save(ObjectNode root) {
    try {
      mapper.writerWithDefaultPrettyPrinter().writeValue(prefsFile.toFile(), root);
    } catch (Exception e) {
      log.warn("Failed to save prefs: {}", e.getMessage());
    }
  }

  public ArrayNode getArray(ObjectNode root, String key) {
    JsonNode existing = root.get(key);
    if (existing instanceof ArrayNode arr) return arr;

    ArrayNode arr = mapper.createArrayNode();
    root.set(key, arr);
    return arr;
  }

  public static ObjectMapper mapper() { return mapper; }
}
