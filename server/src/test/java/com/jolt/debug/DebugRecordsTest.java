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

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class DebugRecordsTest {

  // ─── Breakpoint ───────────────────────────────────────────────

  @Test void breakpointCreateDefaults() {
    var bp = Breakpoint.create("bp-1", "src/Foo.java", 42);
    assertEquals("bp-1", bp.id());
    assertEquals("src/Foo.java", bp.filePath());
    assertEquals(42, bp.line());
    assertTrue(bp.enabled());
    assertEquals(0, bp.hitCount());
  }

  @Test void breakpointToggle() {
    var bp = Breakpoint.create("bp-1", "src/Foo.java", 42);
    var toggled = bp.toggle();
    assertFalse(toggled.enabled());
    assertTrue(bp.enabled()); // original unchanged (immutable)
  }

  @Test void breakpointToggleTwiceRestores() {
    var bp = Breakpoint.create("bp-1", "src/Foo.java", 42);
    var restored = bp.toggle().toggle();
    assertTrue(restored.enabled());
  }

  @Test void breakpointIncrementHit() {
    var bp = Breakpoint.create("bp-1", "src/Foo.java", 42);
    var hit1 = bp.incrementHit();
    var hit2 = hit1.incrementHit();
    assertEquals(1, hit1.hitCount());
    assertEquals(2, hit2.hitCount());
  }

  // ─── DebugEvent ───────────────────────────────────────────────

  @Test void eventBreakpointHit() {
    var evt = DebugEvent.breakpointHit("s1", "src/Foo.java", 10, "main");
    assertEquals("s1", evt.sessionId());
    assertEquals("breakpoint_hit", evt.type());
    assertEquals("src/Foo.java", evt.filePath());
    assertEquals(10, evt.line());
    assertEquals("main", evt.threadName());
    assertTrue(evt.payload().isEmpty());
  }

  @Test void eventStepComplete() {
    var evt = DebugEvent.stepComplete("s1", "src/Bar.java", 55, "thread-2");
    assertEquals("step_complete", evt.type());
    assertEquals(55, evt.line());
  }

  @Test void eventPaused() {
    var evt = DebugEvent.paused("s1");
    assertEquals("paused", evt.type());
    assertNull(evt.filePath());
    assertEquals(0, evt.line());
  }

  @Test void eventResumed() {
    var evt = DebugEvent.resumed("s1");
    assertEquals("resumed", evt.type());
  }

  @Test void eventTerminated() {
    var evt = DebugEvent.terminated("s1", 0);
    assertEquals("terminated", evt.type());
    assertEquals(0, evt.payload().get("exitCode"));
  }

  @Test void eventOutput() {
    var evt = DebugEvent.output("s1", "Hello from target");
    assertEquals("output", evt.type());
    assertEquals("Hello from target", evt.payload().get("text"));
  }

  // ─── VariableInfo ─────────────────────────────────────────────

  @Test void variableInfoFields() {
    var v = new VariableInfo("count", "int", "42", 0);
    assertEquals("count", v.name());
    assertEquals("int", v.type());
    assertEquals("42", v.value());
    assertEquals(0, v.frameIndex());
  }

  @Test void variableInfoDeepFrame() {
    var v = new VariableInfo("this", "com.foo.Bar", "Bar@1a2b3c", 3);
    assertEquals(3, v.frameIndex());
  }

  // ─── DebugSession (with mock backend) ─────────────────────────

  @Test void sessionStateMap() {
    var mock = new MockBackend();
    var session = new DebugSession("test-id", "java", mock);

    var map = session.stateMap();
    assertEquals("test-id", map.get("sessionId"));
    assertEquals("java", map.get("language"));
    assertEquals("idle", map.get("status"));
  }

  @Test void sessionBreakpointsTracking() {
    var mock = new MockBackend();
    var session = new DebugSession("test-id", "cpp", mock);

    assertTrue(session.breakpoints().isEmpty());

    // Simulate adding a breakpoint through the session
    try { session.setBreakpoint("src/main.cpp", 10); } catch (Exception ignored) {}

    // MockBackend.setBreakpoint returns true, so one bp should be tracked
    assertEquals(1, session.breakpoints().size());
    assertEquals("src/main.cpp", session.breakpoints().get(0).filePath());
    assertEquals(10, session.breakpoints().get(0).line());
  }

  @Test void sessionEventCallbackReceivesEvents() {
    var mock = new MockBackend();
    var session = new DebugSession("test-id", "java", mock);

    final List<DebugEvent> received = new java.util.concurrent.CopyOnWriteArrayList<>();
    session.setEventCallback(received::add);

    // Trigger an event through the backend
    mock.triggerEvent(DebugEvent.paused("test-id"));

    assertEquals(1, received.size());
    assertEquals("paused", received.get(0).type());
  }

  @Test void sessionCloseDelegatesToBackend() {
    var mock = new MockBackend();
    var session = new DebugSession("test-id", "java", mock);

    session.close();

    assertTrue(mock.closed);
  }

  // ─── DebugManager ─────────────────────────────────────────────

  @Test void managerActiveSessionsInitiallyEmpty() {
    var mgr = new DebugManager(java.nio.file.Path.of("/tmp"), null);
    assertTrue(mgr.activeSessions().isEmpty());
  }

  @Test void managerGetSessionReturnsNullForUnknown() {
    var mgr = new DebugManager(java.nio.file.Path.of("/tmp"), null);
    assertNull(mgr.getSession("nonexistent"));
  }

  @Test void managerStopSessionNoOpForUnknown() {
    var mgr = new DebugManager(java.nio.file.Path.of("/tmp"), null);
    assertDoesNotThrow(() -> mgr.stopSession("nonexistent"));
  }

  @Test void managerShutdownClearsAll() {
    var mgr = new DebugManager(java.nio.file.Path.of("/tmp"), null);
    mgr.shutdown(); // no sessions, should not throw
    assertTrue(mgr.activeSessions().isEmpty());
  }

  @Test void managerResolvePath() {
    var mgr = new DebugManager(java.nio.file.Path.of("/home/user/project"), null);
    var resolved = mgr.resolvePath("src/main.java");
    assertEquals("/home/user/project/src/main.java", resolved.toString());
  }

  // ─── Mock backend for testing ─────────────────────────────────

  static class MockBackend implements DebuggerBackend {
    boolean closed = false;
    private Consumer<DebugEvent> callback;

    @Override public void start(String target, List<String> args) {}
    @Override public boolean setBreakpoint(String filePath, int line) { return true; }
    @Override public void removeBreakpoint(String backendId) {}
    @Override public void stepOver() {}
    @Override public void stepInto() {}
    @Override public void stepOut() {}
    @Override public void continueExecution() {}
    @Override public void pause() {}
    @Override public List<VariableInfo> getVariables(int frameIndex) { return List.of(); }
    @Override public String status() { return "idle"; }
    @Override public String currentFile() { return null; }
    @Override public int currentLine() { return 0; }
    @Override public String currentThread() { return null; }
    @Override public void setEventCallback(Consumer<DebugEvent> cb) { this.callback = cb; }
    @Override public void close() { this.closed = true; }

    void triggerEvent(DebugEvent event) {
      if (callback != null) callback.accept(event);
    }
  }
}