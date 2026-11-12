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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SettingsServiceTest {

  @TempDir Path tmpDir;
  private SettingsService svc;

  @BeforeEach void setUp() { svc = new SettingsService(tmpDir); }

  @Test void defaultSettingsIncludeNewKeys() {
    Map<String, Object> s = svc.getSettings();
    assertEquals(2, s.get("search.fuzzy.maxEdits"));
    assertEquals(10, s.get("search.suggest.limit"));
    assertEquals(1000, s.get("git.diff.maxLines"));
    assertEquals("unified", s.get("git.diff.defaultView"));
  }

  @Test void updateNewKeysPersists() {
    Map<String, Object> updated = svc.updateSettings(Map.of(
        "search.fuzzy.maxEdits", 3,
        "search.suggest.limit", 20,
        "git.diff.maxLines", 500,
        "git.diff.defaultView", "split"
    ));

    assertEquals(3, updated.get("search.fuzzy.maxEdits"));
    assertEquals(20, updated.get("search.suggest.limit"));
    assertEquals(500, updated.get("git.diff.maxLines"));
    assertEquals("split", updated.get("git.diff.defaultView"));

    // Verify persistence by creating a new service instance
    SettingsService svc2 = new SettingsService(tmpDir);
    Map<String, Object> reloaded = svc2.getSettings();
    assertEquals(3, reloaded.get("search.fuzzy.maxEdits"));
    assertEquals(20, reloaded.get("search.suggest.limit"));
    assertEquals(500, reloaded.get("git.diff.maxLines"));
    assertEquals("split", reloaded.get("git.diff.defaultView"));
  }

  @Test void unknownKeyIsRejected() {
    Map<String, Object> updated = svc.updateSettings(Map.of("hacked.key", "evil"));
    assertFalse(updated.containsKey("hacked.key"));
  }

  @Test void getSettingReturnsNullForMissing() {
    assertNull(svc.getSetting("nonexistent.key"));
  }

  @Test void getSettingReturnsNewKeyDefault() {
    assertEquals(2, svc.getSetting("search.fuzzy.maxEdits"));
  }

}