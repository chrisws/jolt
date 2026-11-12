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

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * C++ debugger backend using GDB's Machine Interface (MI) protocol.
 * Spawns {@code gdb --interpreter=mi <binary>} and communicates via stdin/stdout.
 */
public class GdbDebugger implements DebuggerBackend {

  private Process process;         // GDB process
  private Process targetProcess;   // the user's binary
  private BufferedWriter gdbIn;
  private BufferedReader gdbOut;

  private final String gdbPath;
  private final String workingDir;

  private final AtomicReference<String> status = new AtomicReference<>("idle");
  private final AtomicReference<String> currentFile = new AtomicReference<>(null);
  private final AtomicReference<Integer> currentLine = new AtomicReference<>(0);
  private final AtomicReference<String> currentThread = new AtomicReference<>("main");

  private final Map<String, String> breakpoints = new ConcurrentHashMap<>(); // bpId -> gdbNum
  private int bpCounter = 0;

  private volatile Consumer<DebugEvent> eventCallback;
  private Thread readerThread;

  public GdbDebugger(String gdbPath, String workingDir) {
    this.gdbPath = gdbPath != null ? gdbPath : "gdb";
    this.workingDir = workingDir != null ? workingDir : ".";
  }

  // ─── Lifecycle ────────────────────────────────────────────────

  @Override
  public void start(String target, List<String> args) throws Exception {
    // 1. Launch the target process (Jolt owns its lifecycle)
    List<String> targetCmd = new ArrayList<>();
    targetCmd.add(target);
    if (args != null) targetCmd.addAll(args);

    targetProcess = new ProcessBuilder(targetCmd)
        .directory(new java.io.File(workingDir))
        .redirectErrorStream(true)
        .start();

    long pid = targetProcess.pid();

    // Pump target stdout/stderr to debug events
    Thread outputThread = new Thread(() -> pumpTargetOutput(), "gdb-target-output");
    outputThread.setDaemon(true);
    outputThread.start();

    // 2. Launch GDB without a target (we'll attach)
    List<String> gdbCmd = List.of(gdbPath, "--interpreter=mi", "-q");

    process = new ProcessBuilder(gdbCmd)
        .directory(new java.io.File(workingDir))
        .redirectErrorStream(false)
        .start();

    gdbIn = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
    gdbOut = new BufferedReader(new InputStreamReader(process.getInputStream()));

    // 3. Start the MI reader thread
    readerThread = new Thread(this::readLoop, "gdb-reader");
    readerThread.setDaemon(true);
    readerThread.start();

    // 4. Wait for GDB to be ready, then attach to the target PID
    Thread.sleep(300);

    List<String> resp = sendCommand("-exec-attach " + pid);
    boolean attached = false;
    for (String line : resp) {
      if (line.contains("^connected") || line.contains("^done")) {
        attached = true;
        break;
      }
      if (line.contains("(error)")) {
        throw new RuntimeException("GDB failed to attach to PID " + pid + ": " + line);
      }
    }
    if (!attached) {
      throw new RuntimeException("GDB attach to PID " + pid + " did not confirm");
    }

    status.set("running");
  }

  private void pumpTargetOutput() {
    try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(targetProcess.getInputStream()))) {
      String line;
      while ((line = reader.readLine()) != null) {
        emit(DebugEvent.output(sessionId(), line));
      }
    } catch (IOException ignored) {}
  }

  /** Attach to an already-running process by PID (Jolt does not own its lifecycle). */
  @Override
  public void attach(int pid) throws Exception {
    // Launch GDB without a target
    List<String> gdbCmd = List.of(gdbPath, "--interpreter=mi", "-q");

    process = new ProcessBuilder(gdbCmd)
        .directory(new java.io.File(workingDir))
        .redirectErrorStream(false)
        .start();

    gdbIn = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
    gdbOut = new BufferedReader(new InputStreamReader(process.getInputStream()));

    // Start the MI reader thread
    readerThread = new Thread(this::readLoop, "gdb-reader");
    readerThread.setDaemon(true);
    readerThread.start();

    // Wait for GDB to be ready, then attach
    Thread.sleep(300);

    List<String> resp = sendCommand("-exec-attach " + pid);
    boolean attached = false;
    for (String line : resp) {
      if (line.contains("^connected") || line.contains("^done")) {
        attached = true;
        break;
      }
      if (line.contains("(error)")) {
        throw new RuntimeException("GDB failed to attach to PID " + pid + ": " + line);
      }
    }
    if (!attached) {
      throw new RuntimeException("GDB attach to PID " + pid + " did not confirm");
    }

    status.set("running");
  }

  private void readLoop() {
    try {
      String line;
      while ((line = gdbOut.readLine()) != null) {
        handleMiLine(line);
      }
    } catch (IOException e) {
      if (status.get().equals("running")) {
        status.set("terminated");
        emit(DebugEvent.terminated(sessionId(), -1));
      }
    }
  }

  // ─── MI protocol helpers ──────────────────────────────────────

  /** Send an MI command and return the raw response lines. */
  private List<String> sendCommand(String cmd) throws IOException, InterruptedException {
    gdbIn.write(cmd + "\n");
    gdbIn.flush();

    List<String> responses = new ArrayList<>();
    String line;
    while ((line = gdbOut.readLine()) != null) {
      responses.add(line);
      // MI responses end with a "(done)" or "(error)" token on the last line,
      // but async events may interleave. We collect until we see a result.
      if (line.contains("(done)") || line.contains("(error)")) break;
    }
    return responses;
  }

  private void handleMiLine(String line) {
    if (line.startsWith("*stopped")) {
      parseStoppedEvent(line);
    } else if (line.startsWith("*running")) {
      status.set("running");
    } else if (line.startsWith("*exit")) {
      status.set("terminated");
      emit(DebugEvent.terminated(sessionId(), 0));
    } else if (line.startsWith("^")) {
      // Result record — handled in sendCommand, ignore here for async
    } else if (line.startsWith("$")) {
      // Stream output (stdout from inferior)
      String text = line.substring(1).replace("\"", "");
      emit(DebugEvent.output(sessionId(), text));
    }
  }

  private void parseStoppedEvent(String line) {
    // Format: *stopped,reason="breakpoint-hit",bpnum="1",frame={...}
    String reason = extractField(line, "reason");
    String file = extractField(line, "file");
    int ln = parseIntField(line, "line");

    if (file != null) currentFile.set(file);
    if (ln > 0) currentLine.set(ln);

    status.set("paused");

    if ("breakpoint-hit".equals(reason)) {
      emit(DebugEvent.breakpointHit(sessionId(), file, ln, currentThread.get()));
    } else if ("stop".equals(reason) || "breakpoint".equals(reason)) {
      emit(DebugEvent.stepComplete(sessionId(), file, ln, currentThread.get()));
    } else {
      emit(DebugEvent.paused(sessionId()));
    }
  }

  private String extractField(String line, String field) {
    String key = field + "=\"";
    int start = line.indexOf(key);
    if (start < 0) return null;
    start += key.length();
    int end = line.indexOf('"', start);
    return end > start ? line.substring(start, end) : null;
  }

  private int parseIntField(String line, String field) {
    String val = extractField(line, field);
    if (val == null) {
      // Try unquoted: field=42
      String key = field + "=";
      int start = line.indexOf(key);
      if (start < 0) return 0;
      start += key.length();
      int end = start;
      while (end < line.length() && Character.isDigit(line.charAt(end))) end++;
      try { return Integer.parseInt(line.substring(start, end)); } catch (Exception e) { return 0; }
    }
    try { return Integer.parseInt(val); } catch (Exception e) { return 0; }
  }

  // ─── Breakpoints ──────────────────────────────────────────────

  @Override
  public boolean setBreakpoint(String filePath, int line) throws Exception {
    String cmd = String.format("-break-insert %s:%d", filePath, line);
    List<String> resp = sendCommand(cmd);

    for (String r : resp) {
      if (r.startsWith("^connected") || r.startsWith("^done")) {
        String bpNum = extractField(r, "number");
        if (bpNum != null) {
          String bpId = "bp-" + (++bpCounter);
          breakpoints.put(bpId, bpNum);
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public void removeBreakpoint(String backendId) throws Exception {
    String gdbNum = breakpoints.remove(backendId);
    if (gdbNum != null) {
      sendCommand("-break-delete " + gdbNum);
    }
  }

  // ─── Stepping ─────────────────────────────────────────────────

  @Override public void stepOver() throws Exception { sendCommand("-exec-next"); status.set("stepping"); }

  @Override public void stepInto() throws Exception { sendCommand("-exec-step"); status.set("stepping"); }

  @Override public void stepOut() throws Exception { sendCommand("-exec-finish"); status.set("stepping"); }

  // ─── Run control ──────────────────────────────────────────────

  @Override
  public void continueExecution() throws Exception {
    sendCommand("-exec-continue");
    status.set("running");
    emit(DebugEvent.resumed(sessionId()));
  }

  @Override
  public void pause() throws Exception {
    // GDB: interrupt via SIGINT to the process, or use -exec-interrupt (not standard)
    // Most reliable: send Ctrl-C equivalent via the MI protocol
    sendCommand("-exec-interrupt");
    status.set("paused");
    emit(DebugEvent.paused(sessionId()));
  }

  // ─── Variables ────────────────────────────────────────────────

  @Override
  public List<VariableInfo> getVariables(int frameIndex) throws Exception {
    // Select the frame first
    sendCommand("-stack-select " + frameIndex);

    // Get local variables for the selected frame
    List<String> resp = sendCommand("-stack-list-variables");

    List<VariableInfo> vars = new ArrayList<>();
    for (String line : resp) {
      if (line.startsWith("^")) {
        parseVariableList(line, frameIndex, vars);
      }
    }
    return vars;
  }

  private void parseVariableList(String line, int frameIndex, List<VariableInfo> vars) {
    // Format: ^done,variables=[{name="x",type="int",value="42"},...]
    int start = line.indexOf("variables=[");
    if (start < 0) return;
    start += "variables=[".length();

    int end = line.indexOf("]", start);
    if (end < 0) end = line.length();

    String content = line.substring(start, end);

    // Parse individual {name="...",type="...",value="..."} entries
    int pos = 0;
    while (pos < content.length()) {
      int braceStart = content.indexOf('{', pos);
      if (braceStart < 0) break;
      int braceEnd = content.indexOf('}', braceStart);
      if (braceEnd < 0) break;

      String entry = content.substring(braceStart + 1, braceEnd);
      String name = extractField(entry, "name");
      String type = extractField(entry, "type");
      String value = extractField(entry, "value");

      if (name != null) {
        vars.add(new VariableInfo(name, type != null ? type : "?", value != null ? value : "?", frameIndex));
      }

      pos = braceEnd + 1;
    }
  }

  // ─── State queries ────────────────────────────────────────────

  @Override public String status() { return status.get(); }

  @Override public String currentFile() { return currentFile.get(); }

  @Override public int currentLine() { return currentLine.get(); }

  @Override public String currentThread() { return currentThread.get(); }

  @Override public void setEventCallback(Consumer<DebugEvent> callback) { this.eventCallback = callback; }

  // ─── Helpers ──────────────────────────────────────────────────

  private String sessionId() { return "gdb-" + System.identityHashCode(this); }

  private void emit(DebugEvent event) {
    Consumer<DebugEvent> cb = eventCallback;
    if (cb != null) cb.accept(event);
  }

  @Override
  public void close() {
    try {
      if (gdbIn != null) gdbIn.close();
      if (process != null && process.isAlive()) process.destroyForcibly();
      if (targetProcess != null && targetProcess.isAlive()) targetProcess.destroyForcibly();
      if (readerThread != null) readerThread.interrupt();
    } catch (Exception e) { /* best effort */ }
    status.set("terminated");
  }

  /** Expose the GDB process. */
  public Process process() { return process; }

  /** Expose the target process. */
  public Process targetProcess() { return targetProcess; }
}