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

import com.jolt.api.SettingsService;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Registry and factory for debug sessions. Thread-safe.
 */
public class DebugManager {

  private final Map<String, DebugSession> sessions = new ConcurrentHashMap<>();
  private final Path homeDir;
  private final SettingsService settings;
  private final AtomicInteger portCounter;

  public DebugManager(Path homeDir, SettingsService settings) {
    this.homeDir = homeDir.toAbsolutePath().normalize();
    this.settings = settings;

    int basePort = 5005; // default fallback
    if (settings != null) {
      Object val = settings.getSetting("debug.java.basePort");
      if (val instanceof Number n) basePort = n.intValue();
    }
    this.portCounter = new AtomicInteger(basePort);
  }

  /** Create a new debug session and start it (launch mode). */
  public DebugSession startSession(String language, String target, List<String> args) throws Exception {
    return startSession(language, target, args, -1);
  }

  /** Create a new debug session. If pid > 0, attach to that process; otherwise launch target. */
  public DebugSession startSession(String language, String target, List<String> args, int pid) throws Exception {
    String id = UUID.randomUUID().toString();

    DebuggerBackend backend;
    if ("java".equalsIgnoreCase(language)) {
      int port = portCounter.getAndIncrement();
      String jdbPath = resolveSetting("debug.jdb.path", "jdb");
      backend = new JdbDebugger(port, homeDir.toString(), jdbPath);
    } else if ("cpp".equalsIgnoreCase(language) || "c".equalsIgnoreCase(language)) {
      String gdbPath = resolveSetting("debug.gdb.path", "gdb");
      backend = new GdbDebugger(gdbPath, homeDir.toString());
    } else {
      throw new IllegalArgumentException("Unsupported language: " + language);
    }

    DebugSession session = new DebugSession(id, language.toLowerCase(), backend);
    sessions.put(id, session);

    if (pid > 0) {
      backend.attach(pid);
    } else {
      session.start(target, args);
    }

    return session;
  }

  /** Look up a session by id. Returns null if not found. */
  public DebugSession getSession(String id) {
    return sessions.get(id);
  }

  /** Remove and close a session. */
  public void stopSession(String id) {
    DebugSession session = sessions.remove(id);
    if (session != null) session.close();
  }

  /** List all active session ids. */
  public Set<String> activeSessions() {
    return Set.copyOf(sessions.keySet());
  }

  /** Close all sessions (called on server shutdown). */
  public void shutdown() {
    for (var entry : sessions.entrySet()) {
      entry.getValue().close();
    }
    sessions.clear();
  }

  // ─── Helpers ──────────────────────────────────────────────────

  /** Read a string setting from SettingsService, falling back to a default. */
  private String resolveSetting(String key, String fallback) {
    if (settings == null) return fallback;
    Object val = settings.getSetting(key);
    return val instanceof String s ? s : fallback;
  }

  /** Resolve a relative file path against the project root. */
  public Path resolvePath(String relativePath) {
    return homeDir.resolve(relativePath).normalize();
  }

  /** The project root directory. */
  public Path homeDir() { return homeDir; }
}