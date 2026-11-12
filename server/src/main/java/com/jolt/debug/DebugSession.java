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

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/** Wraps a {@link DebuggerBackend} with session-level state. */
public class DebugSession {

  private final String id;
  private final String language;   // "java" or "cpp"
  private final DebuggerBackend backend;
  private final List<Breakpoint> breakpoints = new CopyOnWriteArrayList<>();

  public DebugSession(String id, String language, DebuggerBackend backend) {
    this.id = id;
    this.language = language;
    this.backend = backend;

    // Wire backend events to broadcast through the session's callback
    this.backend.setEventCallback(event -> {
      // Increment hit count on breakpoint hits
      if ("breakpoint_hit".equals(event.type())) {
        for (int i = 0; i < breakpoints.size(); i++) {
          Breakpoint bp = breakpoints.get(i);
          if (bp.line() == event.line() && bp.filePath().equals(event.filePath())) {
            breakpoints.set(i, bp.incrementHit());
            break;
          }
        }
      }
      eventCallback.accept(event);
    });
  }

  private volatile java.util.function.Consumer<DebugEvent> eventCallback = e -> {};

  /** Set the callback that receives all debug events (typically a WS broadcaster). */
  public void setEventCallback(java.util.function.Consumer<DebugEvent> cb) {
    this.eventCallback = cb;
    // Re-wire the backend so it goes through our callback chain
    this.backend.setEventCallback(event -> {
      if ("breakpoint_hit".equals(event.type())) {
        for (int i = 0; i < breakpoints.size(); i++) {
          Breakpoint bp = breakpoints.get(i);
          if (bp.line() == event.line() && bp.filePath().equals(event.filePath())) {
            breakpoints.set(i, bp.incrementHit());
            break;
          }
        }
      }
      cb.accept(event);
    });
  }

  // ─── Delegation to backend ────────────────────────────────────

  public void start(String target, List<String> args) throws Exception { backend.start(target, args); }

  public boolean setBreakpoint(String filePath, int line) throws Exception {
    boolean ok = backend.setBreakpoint(filePath, line);
    if (ok) {
      String bpId = "bp-" + (breakpoints.size() + 1);
      breakpoints.add(Breakpoint.create(bpId, filePath, line));
    }
    return ok;
  }

  public void removeBreakpoint(String bpId) throws Exception { backend.removeBreakpoint(bpId); }

  public void stepOver() throws Exception { backend.stepOver(); }

  public void stepInto() throws Exception { backend.stepInto(); }

  public void stepOut() throws Exception { backend.stepOut(); }

  public void continueExecution() throws Exception { backend.continueExecution(); }

  public void pause() throws Exception { backend.pause(); }

  public List<VariableInfo> getVariables(int frameIndex) throws Exception { return backend.getVariables(frameIndex); }

  // ─── State ────────────────────────────────────────────────────

  public String id() { return id; }
  public String language() { return language; }
  public String status() { return backend.status(); }
  public String currentFile() { return backend.currentFile(); }
  public int currentLine() { return backend.currentLine(); }
  public String currentThread() { return backend.currentThread(); }
  public List<Breakpoint> breakpoints() { return List.copyOf(breakpoints); }

  /** Build a JSON-serialisable state map for the REST API. */
  public Map<String, Object> stateMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("sessionId", id);
    map.put("language", language);
    map.put("status", status());
    map.put("currentFile", currentFile());
    map.put("currentLine", currentLine());
    map.put("currentThread", currentThread());
    map.put("breakpoints", breakpoints());
    return map;
  }

  public void close() { backend.close(); }
}