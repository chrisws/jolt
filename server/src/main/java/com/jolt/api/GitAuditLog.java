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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/** Appends git write-operation entries to {@code .jolt/git-audit.log}. */
public class GitAuditLog {

  private static final Logger log = LoggerFactory.getLogger(GitAuditLog.class);

  private final Path logFile;

  public GitAuditLog(Path homeDir) {
    Path joltDir = homeDir.resolve(".jolt");
    this.logFile = joltDir.resolve("git-audit.log");

    try {
      Files.createDirectories(joltDir);
    } catch (IOException e) {
      log.warn("Could not create .jolt directory: {}", e.getMessage());
    }
  }

  /** Record a git write operation. */
  public synchronized void log(String action, String detail) {
    String line = Instant.now().toString() + " " + action + (detail != null ? " " + detail : "") + "\n";
    try {
      Files.writeString(logFile, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    } catch (IOException e) {
      log.warn("Failed to write audit log: {}", e.getMessage());
    }
  }
}