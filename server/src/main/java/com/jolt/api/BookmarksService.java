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

/** Manages user bookmarks in the local prefs JSON file. */
public class BookmarksService {

  private static final Logger log = LoggerFactory.getLogger(BookmarksService.class);

  private final PrefsStore store;

  public BookmarksService(PrefsStore store) {
    this.store = store;
  }

  /** Add a bookmark. */
  public Map<String, Object> addBookmark(String path, int line, String label) {
    var root = store.load();

    ArrayNode bookmarks = store.getArray(root, "bookmarks");
    ObjectNode bm = PrefsStore.mapper().createObjectNode();
    bm.put("path", path);
    bm.put("line", line);
    bm.put("label", label != null ? label : "");
    bm.put("createdAt", Instant.now().toString());

    bookmarks.add(bm);
    store.save(root);

    log.info("[BOOKMARKS] Added: {}:{}", path, line);

    return Map.of(
        "path", path, "line", line, "label", label != null ? label : "",
        "createdAt", Instant.now().toString()
    );
  }

  /** Remove a bookmark by path+line. */
  public boolean removeBookmark(String path, int line) {
    var root = store.load();

    ArrayNode bookmarks = store.getArray(root, "bookmarks");
    Iterator<JsonNode> it = bookmarks.iterator();
    while (it.hasNext()) {
      JsonNode bm = it.next();
      if (bm.path("path").asText().equals(path) && bm.path("line").asInt() == line) {
        it.remove();
        store.save(root);
        log.info("[BOOKMARKS] Removed: {}:{}", path, line);
        return true;
      }
    }

    log.warn("[BOOKMARKS] Remove failed - not found: {}:{}", path, line);
    return false;
  }

  /** Get all bookmarks. */
  public List<Map<String, Object>> getBookmarks() {
    var root = store.load();

    ArrayNode bookmarks = store.getArray(root, "bookmarks");
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
}
