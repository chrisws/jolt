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

/** Emacs-mode keybinding definitions. */
public final class EmacsKeybindings {

  private EmacsKeybindings() {}

  public static Map<String, List<Map<String, String>>> bindings() {
    Map<String, List<Map<String, String>>> b = new LinkedHashMap<>();

    b.put("global", List.of(
        KeybindingsService.binding("C-x C-f", "quick_file_open"),
        KeybindingsService.binding("C-s", "focus_search_forward"),
        KeybindingsService.binding("C-r", "focus_search_backward"),
        KeybindingsService.binding("C-Tab", "next_tab"),
        KeybindingsService.binding("S-Tab", "prev_tab"),
        KeybindingsService.binding("Esc Esc", "close_panel")
    ));

    b.put("editor", List.of(
        KeybindingsService.binding("M-.", "go_to_definition"),
        KeybindingsService.binding("M-f", "forward_word"),
        KeybindingsService.binding("M-b", "backward_word"),
        KeybindingsService.binding("C-f", "forward_char"),
        KeybindingsService.binding("C-b", "backward_char"),
        KeybindingsService.binding("C-a", "beginning_of_line"),
        KeybindingsService.binding("C-e", "end_of_line"),
        KeybindingsService.binding("C-d", "delete_char_forward"),
        KeybindingsService.binding("C-h", "delete_char_backward"),
        KeybindingsService.binding("C-k", "kill_to_end_of_line"),
        KeybindingsService.binding("C-y", "yank"),
        KeybindingsService.binding("M-}", "next_bracket_match"),
        KeybindingsService.binding("M-{", "prev_bracket_match")
    ));

    b.put("search_panel", List.of(
        KeybindingsService.binding("C-n", "next_result"),
        KeybindingsService.binding("C-p", "prev_result")
    ));

    return b;
  }
}
