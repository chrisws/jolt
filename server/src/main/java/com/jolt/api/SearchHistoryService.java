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

/** Tracks search query history (last 50) in the local prefs JSON file. */
public class SearchHistoryService {

  private static final Logger log = LoggerFactory.getLogger(SearchHistoryService.class);

  private final PrefsStore store;

  public SearchHistoryService(PrefsStore store) {
    this.store = store;
  }

  /** Record a search query in history (last 50). */
  public void recordSearch(String query) {
    var root = store.load();

    ArrayNode history = store.getArray(root, "searchHistory");

    // Remove duplicate if present
    Iterator<JsonNode> it = history.iterator();
    while (it.hasNext()) {
      if (it.next().path("query").asText().equals(query)) {
        it.remove();
      }
    }

    ObjectNode entry = PrefsStore.mapper().createObjectNode();
    entry.put("query", query);
    entry.put("searchedAt", Instant.now().toString());

    // Prepend by rebuilding the array with entry first
    ArrayNode newHistory = PrefsStore.mapper().createArrayNode();
    newHistory.add(entry);
    for (JsonNode node : history) { newHistory.add(node); }
    root.set("searchHistory", newHistory);

    // Cap at 50
    while (newHistory.size() > 50) {
      newHistory.remove(newHistory.size() - 1);
    }

    store.save(root);
  }

  /** Get search history. */
  public List<Map<String, Object>> getSearchHistory() {
    var root = store.load();

    ArrayNode history = store.getArray(root, "searchHistory");
    List<Map<String, Object>> result = new ArrayList<>();

    for (JsonNode entry : history) {
      result.add(Map.of(
          "query", entry.path("query").asText(),
          "searchedAt", entry.path("searchedAt").asText()
      ));
    }

    return result;
  }
}
