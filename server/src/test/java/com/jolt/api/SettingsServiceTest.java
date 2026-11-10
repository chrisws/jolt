package com.jolt.api;

 import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SettingsServiceTest {

  @TempDir
  Path tempDir;

  private SettingsService service;

  @BeforeEach
  void setUp() {
    service = new SettingsService(tempDir);
  }

  @Test
  void testDefaultSettings() {
    Map<String, Object> settings = service.getSettings();
    assertEquals("default", settings.get("keybindingMode"));
    assertEquals("dark", settings.get("theme"));
    assertEquals(14, settings.get("fontSize"));
    assertEquals(2, settings.get("tabSize"));
    assertEquals(false, settings.get("wordWrap"));
    assertEquals(true, settings.get("showLineNumbers"));
    assertEquals(true, settings.get("minimapEnabled"));
    assertEquals(true, settings.get("gitAutoRefresh"));
  }

  @Test
  void testUpdateSingleSetting() {
    Map<String, Object> result = service.updateSettings(Map.of("theme", "light"));
    assertEquals("light", result.get("theme"));

    // Verify persistence by reading back from the same instance
    Map<String, Object> reloaded = service.getSettings();
    assertEquals("light", reloaded.get("theme"));
  }

  @Test
  void testUpdateMultipleSettings() {
    Map<String, Object> updates = new LinkedHashMap<>();
    updates.put("fontSize", 18);
    updates.put("tabSize", 4);
    updates.put("wordWrap", true);

    Map<String, Object> result = service.updateSettings(updates);
    assertEquals(18, result.get("fontSize"));
    assertEquals(4, result.get("tabSize"));
    assertEquals(true, result.get("wordWrap"));

    // Untouched defaults remain intact
    assertEquals("default", result.get("keybindingMode"));
  }

  @Test
  void testUnknownKeyRejected() {
    Map<String, Object> result = service.updateSettings(Map.of("evilKey", "hacked"));
    assertFalse(result.containsKey("evilKey"), "Unknown keys must not be persisted");
  }

  @Test
  void testGetSingleSetting() {
    service.updateSettings(Map.of("fontSize", 20));
    assertEquals(20, service.getSetting("fontSize"));
  }

  @Test
  void testGetSettingReturnsDefaultWhenNoFile() {
    // No file exists yet — should return the default value
    assertEquals(14, service.getSetting("fontSize"));
  }

  @Test
  void testPersistenceRoundTrip() {
    service.updateSettings(Map.of("theme", "custom", "tabSize", 8));

    // Create a NEW instance pointing to the same directory — simulates app restart
    SettingsService fresh = new SettingsService(tempDir);
    Map<String, Object> settings = fresh.getSettings();

    assertEquals("custom", settings.get("theme"));
    assertEquals(8, settings.get("tabSize"));

    // Defaults for untouched keys are still present
    assertEquals("default", settings.get("keybindingMode"));
  }

  @Test
  void testAllKnownKeysAccepted() {
    Map<String, Object> updates = new LinkedHashMap<>();
    updates.put("keybindingMode", "vim");
    updates.put("theme", "light");
    updates.put("fontSize", 16);
    updates.put("tabSize", 4);
    updates.put("wordWrap", true);
    updates.put("showLineNumbers", false);
    updates.put("minimapEnabled", false);
    updates.put("gitAutoRefresh", false);

    Map<String, Object> result = service.updateSettings(updates);

    for (var entry : updates.entrySet()) {
      assertEquals(entry.getValue(), result.get(entry.getKey()),
          "Key " + entry.getKey() + " should be accepted");
    }
  }

}
