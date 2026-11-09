package com.jolt.watch;

import java.nio.file.Path;

/** Immutable record describing a single filesystem change event. */
public record FileChangeEvent(String path, String eventType) {
  /** @param path relative path from codebase root */
  /** @param eventType one of "modify", "create", "delete" */
}