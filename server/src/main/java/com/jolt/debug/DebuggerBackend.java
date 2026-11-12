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

import java.util.List;

/**
 * Common operations for a debugger backend (JDB for Java, GDB for C++).
 * Implementations must be thread-safe: commands may arrive from REST handlers
 * while an async reader thread processes events.
 */
public interface DebuggerBackend extends AutoCloseable {

  /** Launch the target process and attach the debugger. */
  void start(String target, List<String> args) throws Exception;

  /** Attach to an already-running process by PID. */
  default void attach(int pid) throws Exception {
    throw new UnsupportedOperationException("Attach not supported by this backend");
  }

  /** Set or toggle a breakpoint at the given file/line. Returns true if set. */
  boolean setBreakpoint(String filePath, int line) throws Exception;

  /** Remove a breakpoint by its backend-specific id. */
  void removeBreakpoint(String backendId) throws Exception;

  /** Step over (next line in same method). */
  void stepOver() throws Exception;

  /** Step into (enter called method). */
  void stepInto() throws Exception;

  /** Step out (finish current method, return to caller). */
  void stepOut() throws Exception;

  /** Resume execution from a paused state. */
  void continueExecution() throws Exception;

  /** Pause / interrupt the running target. */
  void pause() throws Exception;

  /** Get local variables at the given stack frame index (0 = top). */
  List<VariableInfo> getVariables(int frameIndex) throws Exception;

  /** Current status: "running", "paused", "terminated". */
  String status();

  /** Current file path (nullable if unknown). */
  String currentFile();

  /** Current line number (0 if unknown). */
  int currentLine();

  /** Current thread name (nullable). */
  String currentThread();

  /** Register a callback invoked when async events occur. */
  void setEventCallback(java.util.function.Consumer<DebugEvent> callback);

  @Override
  void close();
}