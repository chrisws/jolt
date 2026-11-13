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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SearchHistoryServiceTest {

  @TempDir
  Path tempDir;

  private SearchHistoryService service;

  @BeforeEach
  void setUp() {
    var store = new PrefsStore(tempDir);
    service = new SearchHistoryService(store);
  }

  @Test
  void recordSearch_appearsInHistory() {
    service.recordSearch("find all classes");

    List<Map<String, Object>> history = service.getSearchHistory();
    assertEquals(1, history.size());
    assertEquals("find all classes", history.get(0).get("query"));
  }

  @Test
  void recordSearch_deduplicates() {
    service.recordSearch("query one");
    service.recordSearch("query two");
    service.recordSearch("query one"); // repeat

    List<Map<String, Object>> history = service.getSearchHistory();
    assertEquals(2, history.size());
    assertEquals("query one", history.get(0).get("query")); // moved to front
  }

  @Test
  void recordSearch_capsAt50() {
    for (int i = 0; i < 55; i++) {
      service.recordSearch("search" + i);
    }

    List<Map<String, Object>> history = service.getSearchHistory();
    assertEquals(50, history.size());

    // Most recent should be search54
    assertEquals("search54", history.get(0).get("query"));
  }

  @Test
  void getSearchHistory_emptyInitially() {
    assertTrue(service.getSearchHistory().isEmpty());
  }
}
