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