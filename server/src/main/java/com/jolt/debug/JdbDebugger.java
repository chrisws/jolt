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
 * Java debugger backend that drives JDB (the JDK's built-in debugger) as a subprocess.
 * Launches the target JVM with a JDWP agent, then connects JDB via socket.
 * Commands are sent via stdin; responses are parsed from stdout.
 */
public class JdbDebugger implements DebuggerBackend {

  private Process targetProcess;   // the JVM running the user's code
  private Process jdbProcess;      // the JDB debugger process

  private BufferedWriter jdbIn;
  private BufferedReader jdbOut;

  private final int port;
  private final String workingDir;
  private final String jdbPath;

  private final AtomicReference<String> status = new AtomicReference<>("idle");
  private final AtomicReference<String> currentFile = new AtomicReference<>(null);
  private final AtomicReference<Integer> currentLine = new AtomicReference<>(0);
  private final AtomicReference<String> currentThread = new AtomicReference<>("main");

  private final Map<String, String> breakpoints = new ConcurrentHashMap<>(); // bpId -> jdbId
  private int bpCounter = 0;

  private volatile Consumer<DebugEvent> eventCallback;
  private Thread readerThread;

  public JdbDebugger(int port, String workingDir, String jdbPath) {
    this.port = port;
    this.workingDir = workingDir;
    this.jdbPath = jdbPath != null ? jdbPath : "jdb";
  }

  // ─── Lifecycle ────────────────────────────────────────────────

  @Override
  public void start(String target, List<String> args) throws Exception {
    // Launch target JVM with JDWP agent (suspend on start)
    String agentArg = String.format(
        "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=%d", port);

    List<String> targetCmd = new ArrayList<>();
    targetCmd.add("java");
    targetCmd.add(agentArg);
    targetCmd.addAll(args);
    targetCmd.add(target);

    targetProcess = new ProcessBuilder(targetCmd)
        .directory(new File(workingDir))
        .redirectErrorStream(true)
        .start();

    // Pump target stdout to events
    Thread outputThread = new Thread(() -> pumpTargetOutput(), "jdb-target-output");
    outputThread.setDaemon(true);
    outputThread.start();

    // Connect JDB to the target via socket
    String connectStr = "socket=localhost:" + port;
    jdbProcess = new ProcessBuilder(jdbPath, "-connect", connectStr)
        .directory(new File(workingDir))
        .redirectErrorStream(false)
        .start();

    jdbIn = new BufferedWriter(new OutputStreamWriter(jdbProcess.getOutputStream()));
    jdbOut = new BufferedReader(new InputStreamReader(jdbProcess.getInputStream()));

    // Start reader thread for async JDB output
    readerThread = new Thread(this::readLoop, "jdb-reader");
    readerThread.setDaemon(true);
    readerThread.start();

    // Give JDB a moment to connect and show the initial prompt
    Thread.sleep(500);

    status.set("paused"); // suspended on start
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

  private void readLoop() {
    try {
      String line;
      while ((line = jdbOut.readLine()) != null) {
        handleJdbLine(line);
      }
    } catch (IOException e) {
      if ("running".equals(status.get())) {
        status.set("terminated");
        emit(DebugEvent.terminated(sessionId(), -1));
      }
    }
  }

  private void handleJdbLine(String line) {
    // JDB prints "Breakpoint hit: ..." or "Step completed: ..." etc.
    if (line.contains("Breakpoint hit")) {
      parsePosition(line);
      status.set("paused");
      emit(DebugEvent.breakpointHit(sessionId(), currentFile.get(), currentLine.get(), currentThread.get()));
    } else if (line.contains("Step completed") || line.contains("Stepping resumed")) {
      parsePosition(line);
      status.set("paused");
      emit(DebugEvent.stepComplete(sessionId(), currentFile.get(), currentLine.get(), currentThread.get()));
    } else if (line.contains("VM terminated") || line.contains("Disconnected from the target VM")) {
      status.set("terminated");
      emit(DebugEvent.terminated(sessionId(), 0));
    } else if (line.startsWith("main:")) {
      // Thread info line — extract thread name
      String thread = line.substring(6).trim();
      currentThread.set(thread);
    }
  }

  private void parsePosition(String line) {
    // JDB format: "at com.foo.Bar.method(File.java:42)"
    int atIdx = line.indexOf("at ");
    if (atIdx < 0) return;

    String rest = line.substring(atIdx + 3);

    // Extract file:line from parentheses
    int parenStart = rest.lastIndexOf('(');
    int parenEnd = rest.lastIndexOf(')');
    if (parenStart >= 0 && parenEnd > parenStart) {
      String fileLine = rest.substring(parenStart + 1, parenEnd);
      int colon = fileLine.lastIndexOf(':');
      if (colon > 0) {
        currentFile.set(fileLine.substring(0, colon));
        try { currentLine.set(Integer.parseInt(fileLine.substring(colon + 1))); } catch (Exception ignored) {}
      }
    }

    // Extract class.method for thread context
    if (parenStart > 0) {
      String classMethod = rest.substring(0, parenStart).trim();
      // e.g. "com.foo.Bar.method" — store as thread context if useful
    }
  }

  // ─── Command helper ───────────────────────────────────────────

  /** Send a JDB command and return the response lines. */
  private List<String> sendCommand(String cmd) throws IOException, InterruptedException {
    jdbIn.write(cmd + "\n");
    jdbIn.flush();

    // Read response until we see the JDB prompt ("> " or thread prefix)
    List<String> responses = new ArrayList<>();
    String line;
    int timeout = 50; // max lines to collect
    while (timeout-- > 0 && (line = jdbOut.readLine()) != null) {
      responses.add(line);
      if (line.startsWith("> ") || line.matches("^\\w+:.*>$")) break;
    }
    return responses;
  }

  // ─── Breakpoints ──────────────────────────────────────────────

  @Override
  public boolean setBreakpoint(String filePath, int line) throws Exception {
    // JDB: stop at <class>:<line> — we need the class name, not file path.
    // For now, use the file path directly; JDB can resolve it if source is available.
    String cmd = "stop at " + filePath + ":" + line;
    List<String> resp = sendCommand(cmd);

    for (String r : resp) {
      if (r.contains("Breakpoint set") || r.contains("added")) {
        String bpId = "bp-" + (++bpCounter);
        breakpoints.put(bpId, cmd);
        return true;
      }
      if (r.contains("Error") || r.contains("not found")) {
        return false;
      }
    }
    return false;
  }

  @Override
  public void removeBreakpoint(String backendId) throws Exception {
    String cmd = breakpoints.remove(backendId);
    if (cmd != null) {
      // Extract the location from the original "stop at X" command
      String location = cmd.replace("stop at ", "").trim();
      sendCommand("clear " + location);
    }
  }

  // ─── Stepping ─────────────────────────────────────────────────

  @Override public void stepOver() throws Exception { sendCommand("step"); status.set("stepping"); }

  @Override public void stepInto() throws Exception { sendCommand("stepi"); status.set("stepping"); }

  @Override public void stepOut() throws Exception { sendCommand("cont"); status.set("stepping"); }

  // ─── Run control ──────────────────────────────────────────────

  @Override
  public void continueExecution() throws Exception {
    sendCommand("cont");
    status.set("running");
    emit(DebugEvent.resumed(sessionId()));
  }

  @Override
  public void pause() throws Exception {
    // JDB doesn't have a direct "pause" — we can use "suspend" 
    sendCommand("suspend all");
    status.set("paused");
    emit(DebugEvent.paused(sessionId()));
  }

  // ─── Variables ────────────────────────────────────────────────

  @Override
  public List<VariableInfo> getVariables(int frameIndex) throws Exception {
    // JDB: "locals" shows local variables for the current frame
    // For deeper frames, use "locals <frameNumber>" (1-based in JDB)
    String cmd = frameIndex == 0 ? "locals" : "locals " + (frameIndex + 1);
    List<String> resp = sendCommand(cmd);

    List<VariableInfo> vars = new ArrayList<>();
    for (String line : resp) {
      parseLocalVar(line, frameIndex, vars);
    }
    return vars;
  }

  private void parseLocalVar(String line, int frameIndex, List<VariableInfo> vars) {
    // JDB format: "  int count = 42" or "  String name = \"hello\""
    String trimmed = line.trim();
    if (trimmed.isEmpty() || trimmed.startsWith(">") || trimmed.startsWith("main:")) return;

    // Try to parse "type name = value"
    int eqIdx = trimmed.indexOf(" = ");
    if (eqIdx < 0) return;

    String leftPart = trimmed.substring(0, eqIdx).trim();
    String value = trimmed.substring(eqIdx + 3).trim();

    // leftPart is "type name" — split into type and name
    int lastSpace = leftPart.lastIndexOf(' ');
    if (lastSpace <= 0) return;

    String type = leftPart.substring(0, lastSpace).trim();
    String name = leftPart.substring(lastSpace + 1).trim();

    vars.add(new VariableInfo(name, type, value, frameIndex));
  }

  // ─── State queries ────────────────────────────────────────────

  @Override public String status() { return status.get(); }

  @Override public String currentFile() { return currentFile.get(); }

  @Override public int currentLine() { return currentLine.get(); }

  @Override public String currentThread() { return currentThread.get(); }

  @Override public void setEventCallback(Consumer<DebugEvent> callback) { this.eventCallback = callback; }

  // ─── Helpers ──────────────────────────────────────────────────

  private String sessionId() { return "jdb-" + port; }

  private void emit(DebugEvent event) {
    Consumer<DebugEvent> cb = eventCallback;
    if (cb != null) cb.accept(event);
  }

  @Override
  public void close() {
    try {
      if (jdbIn != null) jdbIn.close();
      if (jdbProcess != null && jdbProcess.isAlive()) jdbProcess.destroyForcibly();
      if (targetProcess != null && targetProcess.isAlive()) targetProcess.destroyForcibly();
      if (readerThread != null) readerThread.interrupt();
    } catch (Exception e) { /* best effort */ }
    status.set("terminated");
  }

  /** Expose the target process. */
  public Process targetProcess() { return targetProcess; }

  /** Expose the JDB process. */
  public Process jdbProcess() { return jdbProcess; }

  /** Expose the port. */
  public int port() { return port; }
}