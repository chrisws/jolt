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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PrefsStoreTest {

  @TempDir Path tempDir;

  private PrefsStore store;

  @BeforeEach
  void setUp() {
    store = new PrefsStore(tempDir);
  }

  @Test
  void loadReturnsDefaultsWhenNoFile() {
    ObjectNode root = store.load();
    assertNotNull(root);
    assertEquals(1, root.path("version").asInt());
    assertTrue(root.path("bookmarks").isArray());
    assertTrue(root.path("recentFiles").isArray());
    assertTrue(root.path("searchHistory").isArray());
  }

  @Test
  void saveStampsVersion() throws Exception {
    ObjectNode root = store.load();
    root.putArray("bookmarks").addObject().put("path", "a.java").put("line", 1);

    store.save(root);

    // Read back from disk and verify version is present.
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode onDisk = (ObjectNode) mapper.readTree(
        tempDir.resolve(".jolt/prefs.json").toFile());
    assertEquals(1, onDisk.path("version").asInt());
  }

  @Test
  void saveIsAtomic_noPartialFile() throws Exception {
    ObjectNode root = store.load();
    ArrayNode bookmarks = store.getArray(root, "bookmarks");
    for (int i = 0; i < 50; i++) {
      bookmarks.addObject().put("path", "f" + i + ".java").put("line", i);
    }

    store.save(root);

    // File should be valid JSON with all entries.
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode onDisk = (ObjectNode) mapper.readTree(
        tempDir.resolve(".jolt/prefs.json").toFile());
    assertEquals(50, onDisk.path("bookmarks").size());

    // No leftover temp files in the .jolt directory.
    long tmpCount = Files.list(tempDir.resolve(".jolt"))
        .filter(p -> p.getFileName().toString().endsWith(".tmp"))
        .count();
    assertEquals(0, tmpCount, "No temp files should remain after save");
  }

  @Test
  void migrateV0ToV1() throws Exception {
    // Create a v0 prefs file (no version field, missing arrays).
    Path joltDir = tempDir.resolve(".jolt");
    Files.createDirectories(joltDir);
    Path prefsFile = joltDir.resolve("prefs.json");

    ObjectMapper mapper = new ObjectMapper();
    ObjectNode v0 = mapper.createObjectNode();
    v0.putArray("bookmarks").addObject().put("path", "old.java").put("line", 5);
    mapper.writerWithDefaultPrettyPrinter().writeValue(prefsFile.toFile(), v0);

    // Load should migrate to v1.
    ObjectNode root = store.load();
    assertEquals(1, root.path("version").asInt());
    assertTrue(root.path("recentFiles").isArray());
    assertTrue(root.path("searchHistory").isArray());

    // Original data preserved.
    assertEquals(1, root.path("bookmarks").size());
    assertEquals("old.java", root.path("bookmarks").get(0).path("path").asText());
  }

  @Test
  void readOnlyFileSystem_degradedMode() throws Exception {
    // Create the .jolt dir and make it read-only.
    Path joltDir = tempDir.resolve(".jolt");
    Files.createDirectories(joltDir);

    // Remove write permission (works on Unix).
    joltDir.toFile().setWritable(false);

    try {
      PrefsStore roStore = new PrefsStore(tempDir);
      assertFalse(roStore.isWritable(), "Should detect read-only filesystem");

      // Load should still work (returns defaults).
      ObjectNode root = roStore.load();
      assertNotNull(root);

      // Save should be a no-op (no exception, no file created).
      roStore.save(root);

      // prefs.json should not exist since we couldn't write.
      assertFalse(Files.exists(joltDir.resolve("prefs.json")));
    } finally {
      // Restore permissions for cleanup.
      joltDir.toFile().setWritable(true);
    }
  }

  @Test
  void writableFileSystem_normalMode() {
    assertTrue(store.isWritable(), "Temp dir should be writable");

    ObjectNode root = store.load();
    ArrayNode bookmarks = store.getArray(root, "bookmarks");
    bookmarks.addObject().put("path", "test.java").put("line", 10);

    store.save(root);

    // Verify round-trip.
    ObjectNode reloaded = store.load();
    assertEquals(1, reloaded.path("bookmarks").size());
    assertEquals("test.java", reloaded.path("bookmarks").get(0).path("path").asText());
  }

  @Test
  void getArrayCreatesMissingKey() {
    ObjectNode root = store.load();
    root.remove("bookmarks");

    ArrayNode arr = store.getArray(root, "bookmarks");
    assertNotNull(arr);
    assertTrue(arr.isEmpty());
  }

  @Test
  void getArrayReturnsExisting() {
    ObjectNode root = store.load();
    ArrayNode existing = root.withArray("bookmarks");
    existing.addObject().put("path", "x.java").put("line", 1);

    ArrayNode arr = store.getArray(root, "bookmarks");
    assertEquals(1, arr.size());
  }
}