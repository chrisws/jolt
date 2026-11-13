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

/** Vim-mode keybinding definitions. */
public final class VimKeybindings {

  private VimKeybindings() {}

  public static Map<String, List<Map<String, String>>> bindings() {
    Map<String, List<Map<String, String>>> b = new LinkedHashMap<>();

    b.put("normal_mode", List.of(
        // Navigation
        KeybindingsService.binding("h", "cursor_left"),
        KeybindingsService.binding("j", "cursor_down"),
        KeybindingsService.binding("k", "cursor_up"),
        KeybindingsService.binding("l", "cursor_right"),
        KeybindingsService.binding("w", "forward_word"),
        KeybindingsService.binding("b", "backward_word"),
        KeybindingsService.binding("0", "beginning_of_line"),
        KeybindingsService.binding("$", "end_of_line"),
        KeybindingsService.binding("gg", "top_of_file"),
        KeybindingsService.binding("G", "bottom_of_file"),

        // Search
        KeybindingsService.binding("/", "search_forward"),
        KeybindingsService.binding("?", "search_backward"),
        KeybindingsService.binding("n", "next_match"),
        KeybindingsService.binding("N", "prev_match"),

        // Delete / Change / Yank (clipboard operations)
        KeybindingsService.binding("dw", "delete_word"),
        KeybindingsService.binding("d$", "delete_to_eol"),
        KeybindingsService.binding("d0", "delete_to_bol"),
        KeybindingsService.binding("dd", "delete_line"),
        KeybindingsService.binding("cw", "change_word"),
        KeybindingsService.binding("c$", "change_to_eol"),
        KeybindingsService.binding("cc", "change_line"),
        KeybindingsService.binding("yw", "yank_word"),
        KeybindingsService.binding("y$", "yank_to_eol"),
        KeybindingsService.binding("yy", "yank_line"),
        KeybindingsService.binding("p", "paste_after"),
        KeybindingsService.binding("P", "paste_before"),

        // Jolt-specific (bracket-prefixed to avoid conflicts)
        KeybindingsService.binding("[d]", "go_to_definition"),
        KeybindingsService.binding("[D]", "open_file_diff"),
        KeybindingsService.binding("[r]", "find_references"),
        KeybindingsService.binding("[R]", "show_call_graph"),
        KeybindingsService.binding("[h]", "show_class_hierarchy"),
        KeybindingsService.binding("[H]", "show_type_hierarchy"),
        KeybindingsService.binding("[g g]", "open_git_log"),
        KeybindingsService.binding("[G g]", "open_commit_diff"),
        KeybindingsService.binding("[a a]", "open_agent_changes"),
        KeybindingsService.binding("[A a]", "toggle_agent_filter"),
        KeybindingsService.binding("[t t]", "quick_type_open"),
        KeybindingsService.binding("[T t]", "next_tab"),
        KeybindingsService.binding("[W W]", "close_tab"),
        KeybindingsService.binding("[w w]", "close_all_except_current")
    ));

    b.put("insert_mode", List.of(
        // Standard text input — no special bindings except Esc to exit
        KeybindingsService.binding("Esc", "normal_mode")
    ));

    b.put("visual_mode", List.of(
        KeybindingsService.binding("v", "visual_char"),
        KeybindingsService.binding("V", "visual_line"),
        KeybindingsService.binding("Ctrl+v", "visual_block"),
        KeybindingsService.binding("y", "yank_selection"),
        KeybindingsService.binding("d", "delete_selection"),
        KeybindingsService.binding("Esc", "normal_mode")
    ));

    b.put("command_mode", List.of(
        // Triggered by ':' in normal mode.
        // Parsed as :command on the frontend:
        //   :w       → save (no-op in read-only)
        //   :q       → close tab
        //   :q!      → force close tab
        //   :tabn    → next tab
        //   :tabp    → prev tab
        //   :tabo    → close all other tabs
        // Documented here for reference; frontend handles parsing
    ));

    return b;
  }
}
