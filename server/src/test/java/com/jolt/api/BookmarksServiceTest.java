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

class BookmarksServiceTest {

  @TempDir
  Path tempDir;

  private BookmarksService service;

  @BeforeEach
  void setUp() {
    var store = new PrefsStore(tempDir);
    service = new BookmarksService(store);
  }

  @Test
  void addBookmark_returnsDetails() {
    Map<String, Object> result = service.addBookmark("src/Main.java", 42, "main entry");

    assertEquals("src/Main.java", result.get("path"));
    assertEquals(42, result.get("line"));
    assertEquals("main entry", result.get("label"));
    assertNotNull(result.get("createdAt"));
  }

  @Test
  void addBookmark_nullLabel_becomesEmpty() {
    Map<String, Object> result = service.addBookmark("src/App.java", 1, null);
    assertEquals("", result.get("label"));
  }

  @Test
  void getBookmarks_emptyInitially() {
    assertTrue(service.getBookmarks().isEmpty());
  }

  @Test
  void getBookmarks_afterAdd() {
    service.addBookmark("a.java", 10, "first");
    service.addBookmark("b.java", 20, "second");

    List<Map<String, Object>> bookmarks = service.getBookmarks();
    assertEquals(2, bookmarks.size());

    assertEquals("a.java", bookmarks.get(0).get("path"));
    assertEquals(10, bookmarks.get(0).get("line"));

    assertEquals("b.java", bookmarks.get(1).get("path"));
    assertEquals(20, bookmarks.get(1).get("line"));
  }

  @Test
  void removeBookmark_existing_returnsTrue() {
    service.addBookmark("x.java", 5, "test");
    assertTrue(service.removeBookmark("x.java", 5));
    assertTrue(service.getBookmarks().isEmpty());
  }

  @Test
  void removeBookmark_nonExistent_returnsFalse() {
    assertFalse(service.removeBookmark("nope.java", 99));
  }

  @Test
  void removeBookmark_onlyRemovesMatching() {
    service.addBookmark("a.java", 1, "one");
    service.addBookmark("a.java", 2, "two");

    assertTrue(service.removeBookmark("a.java", 1));

    List<Map<String, Object>> remaining = service.getBookmarks();
    assertEquals(1, remaining.size());
    assertEquals(2, remaining.get(0).get("line"));
  }
}
