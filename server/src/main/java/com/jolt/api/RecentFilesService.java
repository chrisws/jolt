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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

/** Tracks recently opened files (last 20) in the local prefs JSON file. */
public class RecentFilesService {

  private static final Logger log = LoggerFactory.getLogger(RecentFilesService.class);

  private final PrefsStore store;

  public RecentFilesService(PrefsStore store) {
    this.store = store;
  }

  /** Record a recently opened file (maintains a list of last 20). */
  public void recordRecentFile(String path) {
    var root = store.load();

    ArrayNode recent = store.getArray(root, "recentFiles");

    // Remove if already present (to move to front)
    Iterator<JsonNode> it = recent.iterator();
    while (it.hasNext()) {
      if (it.next().path("path").asText().equals(path)) {
        it.remove();
      }
    }

    ObjectNode entry = PrefsStore.mapper().createObjectNode();
    entry.put("path", path);
    entry.put("openedAt", Instant.now().toString());

    // Prepend by rebuilding the array with entry first
    ArrayNode newRecent = PrefsStore.mapper().createArrayNode();
    newRecent.add(entry);
    for (JsonNode node : recent) { newRecent.add(node); }
    root.set("recentFiles", newRecent);

    // Cap at 20
    while (newRecent.size() > 20) {
      newRecent.remove(newRecent.size() - 1);
    }

    store.save(root);
    log.debug("[RECENT] Recorded: {}", path);
  }

  /** Get recent files. */
  public List<Map<String, Object>> getRecentFiles() {
    var root = store.load();

    ArrayNode recent = store.getArray(root, "recentFiles");
    List<Map<String, Object>> result = new ArrayList<>();

    for (JsonNode entry : recent) {
      result.add(Map.of(
          "path", entry.path("path").asText(),
          "openedAt", entry.path("openedAt").asText()
      ));
    }

    return result;
  }
}
