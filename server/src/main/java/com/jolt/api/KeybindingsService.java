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

import java.util.*;

/**
 * Provides keybinding definitions for each supported input mode.
 * The frontend fetches these via {@code GET /api/keybindings?mode=X}
 * and dynamically rebinds keyboard handlers.
 */
public class KeybindingsService {

  /** Get all keybindings for a given mode. Returns empty map for unknown modes. */
  public Map<String, Object> getKeybindings(String mode) {
    if (mode == null || mode.isBlank()) {
      mode = "default";
    }

    return switch (mode.toLowerCase()) {
      case "default" -> wrapBindings(DefaultKeybindings.bindings());
      case "emacs" -> wrapBindings(EmacsKeybindings.bindings());
      case "vim" -> wrapBindings(VimKeybindings.bindings());
      default -> Map.of("error", "Unknown mode: " + mode,
          "available", List.of("default", "emacs", "vim"));
    };
  }

  /** List available keybinding modes. */
  public List<String> availableModes() {
    return List.of("default", "emacs", "vim");
  }

  // ─── Shared helpers (used by binding data classes) ──────────────

  static Map<String, String> binding(String keys, String action) {
    return Map.of("keys", keys, "action", action);
  }

  static Map<String, Object> wrapBindings(
      Map<String, List<Map<String, String>>> bindings) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("contexts", bindings);

    // Build a flat key→action map for quick lookup (last context wins)
    Map<String, String> flat = new LinkedHashMap<>();
    for (var entry : bindings.entrySet()) {
      for (Map<String, String> b : entry.getValue()) {
        flat.put(b.get("keys"), b.get("action"));
      }
    }
    result.put("flat", flat);

    return result;
  }
}
