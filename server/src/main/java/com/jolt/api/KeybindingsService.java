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
      case "default" -> defaultBindings();
      case "emacs" -> emacsBindings();
      case "vim" -> vimBindings();
      default -> Map.of("error", "Unknown mode: " + mode, "available", List.of("default", "emacs", "vim"));
    };
  }

  /** List available keybinding modes. */
  public List<String> availableModes() {
    return List.of("default", "emacs", "vim");
  }

  // ─── Default (IntelliJ-style) ───────────────────────────────────

  private Map<String, Object> defaultBindings() {
    Map<String, List<Map<String, String>>> bindings = new LinkedHashMap<>();

    bindings.put("global", List.of(
        binding("Ctrl+Shift+F", "focus_search"),
        binding("Ctrl+P", "quick_file_open"),
        binding("Ctrl+T", "quick_type_open"),
        binding("Ctrl+Shift+G", "open_git_log"),
        binding("Ctrl+Shift+D", "open_commit_diff"),
        binding("Ctrl+Shift+A", "open_agent_changes"),
        binding("Ctrl+Tab", "next_tab"),
        binding("Ctrl+W", "close_tab"),
        binding("Esc", "close_panel")
    ));

    bindings.put("editor", List.of(
        binding("Ctrl+Click", "go_to_definition"),
        binding("Alt+F7", "find_references"),
        binding("Ctrl+H", "show_hierarchy"),
        binding("Ctrl+G", "show_call_graph")
    ));

    return wrapBindings(bindings);
  }

  // ─── Emacs mode ─────────────────────────────────────────────────

  private Map<String, Object> emacsBindings() {
    Map<String, List<Map<String, String>>> bindings = new LinkedHashMap<>();

    bindings.put("global", List.of(
        binding("C-x C-f", "quick_file_open"),
        binding("C-s", "focus_search_forward"),
        binding("C-r", "focus_search_backward"),
        binding("C-Tab", "next_tab"),
        binding("S-Tab", "prev_tab"),
        binding("Esc Esc", "close_panel")
    ));

    bindings.put("editor", List.of(
        binding("M-.", "go_to_definition"),
        binding("M-f", "forward_word"),
        binding("M-b", "backward_word"),
        binding("C-f", "forward_char"),
        binding("C-b", "backward_char"),
        binding("C-a", "beginning_of_line"),
        binding("C-e", "end_of_line"),
        binding("C-d", "delete_char_forward"),
        binding("C-h", "delete_char_backward"),
        binding("C-k", "kill_to_end_of_line"),
        binding("C-y", "yank"),
        binding("M-}", "next_bracket_match"),
        binding("M-{", "prev_bracket_match")
    ));

    bindings.put("search_panel", List.of(
        binding("C-n", "next_result"),
        binding("C-p", "prev_result")
    ));

    return wrapBindings(bindings);
  }

  // ─── Vim mode ───────────────────────────────────────────────────

  private Map<String, Object> vimBindings() {
    Map<String, List<Map<String, String>>> bindings = new LinkedHashMap<>();

    bindings.put("normal_mode", List.of(
        // Navigation
        binding("h", "cursor_left"),
        binding("j", "cursor_down"),
        binding("k", "cursor_up"),
        binding("l", "cursor_right"),
        binding("w", "forward_word"),
        binding("b", "backward_word"),
        binding("0", "beginning_of_line"),
        binding("$", "end_of_line"),
        binding("gg", "top_of_file"),
        binding("G", "bottom_of_file"),

        // Search
        binding("/", "search_forward"),
        binding("?", "search_backward"),
        binding("n", "next_match"),
        binding("N", "prev_match"),

        // Delete / Change / Yank (clipboard operations)
        binding("dw", "delete_word"),
        binding("d$", "delete_to_eol"),
        binding("d0", "delete_to_bol"),
        binding("dd", "delete_line"),
        binding("cw", "change_word"),
        binding("c$", "change_to_eol"),
        binding("cc", "change_line"),
        binding("yw", "yank_word"),
        binding("y$", "yank_to_eol"),
        binding("yy", "yank_line"),
        binding("p", "paste_after"),
        binding("P", "paste_before"),

        // Jolt-specific (bracket-prefixed to avoid conflicts)
        binding("[d]", "go_to_definition"),
        binding("[D]", "open_file_diff"),
        binding("[r]", "find_references"),
        binding("[R]", "show_call_graph"),
        binding("[h]", "show_class_hierarchy"),
        binding("[H]", "show_type_hierarchy"),
        binding("[g g]", "open_git_log"),
        binding("[G g]", "open_commit_diff"),
        binding("[a a]", "open_agent_changes"),
        binding("[A a]", "toggle_agent_filter"),
        binding("[t t]", "quick_type_open"),
        binding("[T t]", "next_tab"),
        binding("[W W]", "close_tab"),
        binding("[w w]", "close_all_except_current")
    ));

    bindings.put("insert_mode", List.of(
        // Standard text input — no special bindings except Esc to exit
        binding("Esc", "normal_mode")
    ));

    bindings.put("visual_mode", List.of(
        binding("v", "visual_char"),
        binding("V", "visual_line"),
        binding("Ctrl+v", "visual_block"),
        binding("y", "yank_selection"),
        binding("d", "delete_selection"),
        binding("Esc", "normal_mode")
    ));

    bindings.put("command_mode", List.of(
        // Triggered by ':' in normal mode
        // Parsed as :command on the frontend:
        //   :w       → save (no-op in read-only)
        //   :q       → close tab
        //   :q!      → force close tab
        //   :tabn    → next tab
        //   :tabp    → prev tab
        //   :tabo    → close all other tabs
        // Documented here for reference; frontend handles parsing
    ));

    return wrapBindings(bindings);
  }

  // ─── Helpers ────────────────────────────────────────────────────

  private Map<String, String> binding(String keys, String action) {
    return Map.of("keys", keys, "action", action);
  }

  private Map<String, Object> wrapBindings(Map<String, List<Map<String, String>>> bindings) {
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