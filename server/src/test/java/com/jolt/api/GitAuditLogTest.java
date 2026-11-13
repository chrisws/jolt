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

package com.jolt.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GitAuditLogTest {

  @TempDir
  Path tempDir;

  private GitAuditLog auditLog;

  @BeforeEach
  void setUp() {
    auditLog = new GitAuditLog(tempDir);
  }

  @Test
  void constructor_createsJoltDirectory() {
    Path joltDir = tempDir.resolve(".jolt");
    assertTrue(Files.isDirectory(joltDir));
  }

  @Test
  void log_appendsEntryToFile() throws IOException {
    auditLog.log("COMMIT", "abc123");

    Path logFile = tempDir.resolve(".jolt/git-audit.log");
    assertTrue(Files.exists(logFile));

    String content = Files.readString(logFile);
    assertTrue(content.contains("COMMIT"));
    assertTrue(content.contains("abc123"));
  }

  @Test
  void log_multipleEntriesAreAppended() throws IOException {
    auditLog.log("COMMIT", "abc123");
    auditLog.log("PUSH", "origin main");

    Path logFile = tempDir.resolve(".jolt/git-audit.log");
    String content = Files.readString(logFile);

    // Both entries should be present, on separate lines
    assertTrue(content.contains("COMMIT abc123"));
    assertTrue(content.contains("PUSH origin main"));

    // Should have exactly 2 lines (each entry is one line)
    long lineCount = content.lines().count();
    assertEquals(2, lineCount);
  }

  @Test
  void log_entryContainsTimestamp() throws IOException {
    auditLog.log("PULL", "origin main");

    Path logFile = tempDir.resolve(".jolt/git-audit.log");
    String content = Files.readString(logFile);

    // Each line should start with an ISO-8601 timestamp (e.g. 2026-11-13T...)
    String line = content.lines().findFirst().orElse("");
    assertTrue(line.matches("\\d{4}-\\d{2}-\\d{2}T.* PULL origin main"));
  }

  @Test
  void log_nullDetail_omitsDetail() throws IOException {
    auditLog.log("COMMIT", null);

    Path logFile = tempDir.resolve(".jolt/git-audit.log");
    String content = Files.readString(logFile);

    // Should contain the action but no trailing detail text
    assertTrue(content.contains("COMMIT"));
    // Line should end with "COMMIT\n" (no extra detail after it)
    String trimmed = content.trim();
    assertTrue(trimmed.endsWith("COMMIT"));
  }

}