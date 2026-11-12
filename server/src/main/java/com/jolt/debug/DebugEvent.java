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

import java.util.Map;

/** An event emitted during a debug session, broadcast over WebSocket. */
public record DebugEvent(
    String sessionId,       // which session this event belongs to
    String type,            // breakpoint_hit | step_complete | paused | resumed | terminated | output
    String filePath,        // current file (nullable)
    int line,               // current line (0 if unknown)
    String threadName,      // current thread (nullable)
    Map<String, Object> payload  // extra data (e.g. output text, exit code)
) {

  public static DebugEvent breakpointHit(String sid, String file, int line, String thread) {
    return new DebugEvent(sid, "breakpoint_hit", file, line, thread, Map.of());
  }

  public static DebugEvent stepComplete(String sid, String file, int line, String thread) {
    return new DebugEvent(sid, "step_complete", file, line, thread, Map.of());
  }

  public static DebugEvent paused(String sid) {
    return new DebugEvent(sid, "paused", null, 0, null, Map.of());
  }

  public static DebugEvent resumed(String sid) {
    return new DebugEvent(sid, "resumed", null, 0, null, Map.of());
  }

  public static DebugEvent terminated(String sid, int exitCode) {
    return new DebugEvent(sid, "terminated", null, 0, null, Map.of("exitCode", exitCode));
  }

  public static DebugEvent output(String sid, String text) {
    return new DebugEvent(sid, "output", null, 0, null, Map.of("text", text));
  }
}