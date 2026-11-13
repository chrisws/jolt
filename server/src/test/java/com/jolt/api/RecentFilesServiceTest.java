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

class RecentFilesServiceTest {

  @TempDir
  Path tempDir;

  private RecentFilesService service;

  @BeforeEach
  void setUp() {
    var store = new PrefsStore(tempDir);
    service = new RecentFilesService(store);
  }

  @Test
  void recordRecentFile_appearsInList() {
    service.recordRecentFile("src/Foo.java");

    List<Map<String, Object>> recent = service.getRecentFiles();
    assertEquals(1, recent.size());
    assertEquals("src/Foo.java", recent.get(0).get("path"));
  }

  @Test
  void recordRecentFile_movesToFrontOnRepeat() {
    service.recordRecentFile("a.java");
    service.recordRecentFile("b.java");
    service.recordRecentFile("a.java"); // re-open a

    List<Map<String, Object>> recent = service.getRecentFiles();
    assertEquals(2, recent.size()); // no duplicate
    assertEquals("a.java", recent.get(0).get("path")); // moved to front
    assertEquals("b.java", recent.get(1).get("path"));
  }

  @Test
  void recordRecentFile_capsAt20() {
    for (int i = 0; i < 25; i++) {
      service.recordRecentFile("file" + i + ".java");
    }

    List<Map<String, Object>> recent = service.getRecentFiles();
    assertEquals(20, recent.size());

    // Most recent should be file24 (last added)
    assertEquals("file24.java", recent.get(0).get("path"));
  }
}
