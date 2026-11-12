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

package com.jolt.debug;

/** A single breakpoint in a debug session. */
public record Breakpoint(
    String id,          // unique id within session (e.g. "bp-1")
    String filePath,    // relative path from project root
    int line,           // 1-based line number
    boolean enabled,    // can be toggled without removing
    int hitCount        // times this breakpoint has been hit
) {

  /** Create a new breakpoint with default values (enabled, zero hits). */
  public static Breakpoint create(String id, String filePath, int line) {
    return new Breakpoint(id, filePath, line, true, 0);
  }

  /** Return a copy with the enabled flag flipped. */
  public Breakpoint toggle() {
    return new Breakpoint(id, filePath, line, !enabled, hitCount);
  }

  /** Return a copy with hitCount incremented. */
  public Breakpoint incrementHit() {
    return new Breakpoint(id, filePath, line, enabled, hitCount + 1);
  }
}