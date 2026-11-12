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

import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class KeybindingsServiceTest {

  private final KeybindingsService service = new KeybindingsService();

  @Test
  void testDefaultModeHasContextsAndFlat() {
    Map<String, Object> result = service.getKeybindings("default");
    assertNotNull(result.get("contexts"));
    assertNotNull(result.get("flat"));

    @SuppressWarnings("unchecked")
    Map<String, List<Map<String, String>>> contexts =
        (Map<String, List<Map<String, String>>>) result.get("contexts");

    assertTrue(contexts.containsKey("global"));
    assertTrue(contexts.containsKey("editor"));
  }

  @Test
  void testEmacsModeHasExpectedContexts() {
    Map<String, Object> result = service.getKeybindings("emacs");

    @SuppressWarnings("unchecked")
    Map<String, List<Map<String, String>>> contexts =
        (Map<String, List<Map<String, String>>>) result.get("contexts");

    assertTrue(contexts.containsKey("global"));
    assertTrue(contexts.containsKey("editor"));
    assertTrue(contexts.containsKey("search_panel"));
  }

  @Test
  void testVimModeHasExpectedContexts() {
    Map<String, Object> result = service.getKeybindings("vim");

    @SuppressWarnings("unchecked")
    Map<String, List<Map<String, String>>> contexts =
        (Map<String, List<Map<String, String>>>) result.get("contexts");

    assertTrue(contexts.containsKey("normal_mode"));
    assertTrue(contexts.containsKey("insert_mode"));
    assertTrue(contexts.containsKey("visual_mode"));
    assertTrue(contexts.containsKey("command_mode"));
  }

  @Test
  void testUnknownModeReturnsError() {
    Map<String, Object> result = service.getKeybindings("sublime");
    assertNotNull(result.get("error"), "Unknown mode should return an error field");

    @SuppressWarnings("unchecked")
    List<String> available = (List<String>) result.get("available");
    assertEquals(List.of("default", "emacs", "vim"), available);
  }

  @Test
  void testNullModeDefaultsToDefault() {
    Map<String, Object> result = service.getKeybindings(null);
    assertNotNull(result.get("contexts"), "null mode should fall back to default bindings");
  }

  @Test
  void testBlankModeDefaultsToDefault() {
    Map<String, Object> result = service.getKeybindings("   ");
    assertNotNull(result.get("contexts"), "blank mode should fall back to default bindings");
  }

  @Test
  void testAvailableModes() {
    List<String> modes = service.availableModes();
    assertEquals(3, modes.size());
    assertTrue(modes.contains("default"));
    assertTrue(modes.contains("emacs"));
    assertTrue(modes.contains("vim"));
  }

  @Test
  void testFlatMapConsistency() {
    for (String mode : List.of("default", "emacs", "vim")) {
      Map<String, Object> result = service.getKeybindings(mode);

      @SuppressWarnings("unchecked")
      Map<String, List<Map<String, String>>> contexts =
          (Map<String, List<Map<String, String>>>) result.get("contexts");

      @SuppressWarnings("unchecked")
      Map<String, String> flat = (Map<String, String>) result.get("flat");

      // Every binding in contexts should appear in the flat map
      for (var ctxEntry : contexts.entrySet()) {
        for (Map<String, String> b : ctxEntry.getValue()) {
          String keys = b.get("keys");
          String action = b.get("action");
          assertEquals(action, flat.get(keys),
              "Flat map should contain " + keys + " -> " + action + " in mode " + mode);
        }
      }
    }
  }

  @Test
  void testVimNormalModeSpotChecks() {
    Map<String, Object> result = service.getKeybindings("vim");

    @SuppressWarnings("unchecked")
    Map<String, String> flat = (Map<String, String>) result.get("flat");

    // Navigation keys
    assertEquals("cursor_left", flat.get("h"));
    assertEquals("cursor_down", flat.get("j"));
    assertEquals("cursor_up", flat.get("k"));
    assertEquals("cursor_right", flat.get("l"));

    // Jolt-specific bracket bindings
    assertEquals("go_to_definition", flat.get("[d]"));
    assertEquals("open_git_log", flat.get("[g g]"));

    // Delete operations
    assertEquals("delete_line", flat.get("dd"));
  }

  @Test
  void testEmacsEditorBindings() {
    Map<String, Object> result = service.getKeybindings("emacs");

    @SuppressWarnings("unchecked")
    Map<String, String> flat = (Map<String, String>) result.get("flat");

    assertEquals("go_to_definition", flat.get("M-."));
    assertEquals("forward_word", flat.get("M-f"));
    assertEquals("backward_word", flat.get("M-b"));
    assertEquals("beginning_of_line", flat.get("C-a"));
  }

  @Test
  void testDefaultGlobalBindings() {
    Map<String, Object> result = service.getKeybindings("default");

    @SuppressWarnings("unchecked")
    Map<String, String> flat = (Map<String, String>) result.get("flat");

    assertEquals("focus_search", flat.get("Ctrl+Shift+F"));
    assertEquals("quick_file_open", flat.get("Ctrl+P"));
    assertEquals("open_git_log", flat.get("Ctrl+Shift+G"));
  }

  @Test
  void testModeCaseInsensitive() {
    Map<String, Object> upper = service.getKeybindings("VIM");
    Map<String, Object> lower = service.getKeybindings("vim");

    assertNotNull(upper.get("contexts"), "Uppercase mode should resolve to valid bindings");
    assertNotNull(lower.get("contexts"));
  }

}
