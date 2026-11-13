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

/** Default (IntelliJ-style) keybinding definitions. */
public final class DefaultKeybindings {

  private DefaultKeybindings() {}

  public static Map<String, List<Map<String, String>>> bindings() {
    Map<String, List<Map<String, String>>> b = new LinkedHashMap<>();

    b.put("global", List.of(
        KeybindingsService.binding("Ctrl+Shift+F", "focus_search"),
        KeybindingsService.binding("Ctrl+P", "quick_file_open"),
        KeybindingsService.binding("Ctrl+E", "open_recent"),
        KeybindingsService.binding("Ctrl+T", "quick_type_open"),
        KeybindingsService.binding("Ctrl+Shift+G", "open_git_log"),
        KeybindingsService.binding("Ctrl+Shift+D", "open_commit_diff"),
        KeybindingsService.binding("Ctrl+Shift+A", "open_agent_changes"),
        KeybindingsService.binding("Ctrl+Tab", "next_tab"),
        KeybindingsService.binding("ArrowRight", "next_tab"),
        KeybindingsService.binding("ArrowLeft", "prev_tab"),
        KeybindingsService.binding("Ctrl+W", "close_tab"),
        KeybindingsService.binding("Esc", "close_panel"),

        // J2EE project-wide parsers
        KeybindingsService.binding("Ctrl+Shift+B", "j2ee_webxml"),
        KeybindingsService.binding("Ctrl+Alt+E", "j2ee_persistence"),
        KeybindingsService.binding("Ctrl+Alt+N", "j2ee_annotations"),
        KeybindingsService.binding("Ctrl+Alt+W", "j2ee_wiring_graph"),

        // Navigation (IntelliJ-style)
        KeybindingsService.binding("Ctrl+F12", "show_structure"),
        KeybindingsService.binding("Ctrl+Alt+B", "go_to_implementation"),
        KeybindingsService.binding("Ctrl+U", "super_method"),
        KeybindingsService.binding("Ctrl+D", "toggle_bookmark")
    ));

    b.put("editor", List.of(
        KeybindingsService.binding("Ctrl+Click", "go_to_definition"),
        KeybindingsService.binding("Alt+F7", "find_references"),
        KeybindingsService.binding("Ctrl+H", "show_hierarchy"),
        KeybindingsService.binding("Ctrl+G", "go_to_line"),
        KeybindingsService.binding("Ctrl+Shift+C", "show_call_graph"),

        // Navigation history
        KeybindingsService.binding("Ctrl+Alt+Left", "nav_back"),
        KeybindingsService.binding("Ctrl+Alt+Right", "nav_forward"),

        // File-specific parsers (use active tab)
        KeybindingsService.binding("Ctrl+Shift+J", "parse_jsp"),
        KeybindingsService.binding("Ctrl+Shift+S", "parse_spring")
    ));

    return b;
  }
}
