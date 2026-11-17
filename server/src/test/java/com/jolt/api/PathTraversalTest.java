/*
 * This file is part of Jolt
 *
 * Copyright (C) 2026 Chris Warren-Smith. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the copy at
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

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies that path resolution blocks traversal attempts. */
class PathTraversalTest {

  @TempDir
  Path homeDir;

  private Path resolve(String userPath) {
    return homeDir.resolve(userPath).normalize();
  }

  private boolean isAllowed(String userPath) {
    return resolve(userPath).startsWith(homeDir);
  }

  @Test
  void normalPathsAreAllowed() {
    assertTrue(isAllowed("src/main/java/Foo.java"));
    assertTrue(isAllowed("README.md"));
    assertTrue(isAllowed(""));
    assertTrue(isAllowed("."));
  }

  @Test
  void singleDotDotIsBlocked() {
    assertFalse(isAllowed("../etc/passwd"));
    assertFalse(isAllowed(".."));
  }

  @Test
  void multipleDotDotIsBlocked() {
    assertFalse(isAllowed("../../etc/passwd"));
    assertFalse(isAllowed("../../../etc/shadow"));
    assertFalse(isAllowed("src/../../.."));
  }

  @Test
  void embeddedDotDotIsBlocked() {
    assertFalse(isAllowed("src/../../../etc/passwd"));
    assertFalse(isAllowed("a/b/../../../../etc/passwd"));
  }

  @Test
  void dotDotThatStaysInsideIsAllowed() {
    // src/../src/Main.java normalizes to src/Main.java — still inside
    assertTrue(isAllowed("src/../src/Main.java"));
    // a/b/../c normalizes to a/c — still inside
    assertTrue(isAllowed("a/b/../c"));
  }

  @Test
  void absolutePathIsBlocked() {
    // resolve() with absolute path returns the absolute path (ignores homeDir)
    assertFalse(resolve("/etc/passwd").startsWith(homeDir));
  }

  @Test
  void urlEncodedDotDotIsBlocked() {
    // After URL decoding, %2F becomes /, so the server sees ../..
    // This test simulates the decoded form
    assertFalse(isAllowed("..%2F..%2Fetc%2Fpasswd".replace("%2F", "/")));
  }

  @Test
  void nullAndBlankHandledByCaller() {
    // These are rejected before resolve() is called (400 in routes)
    // Just verify resolve doesn't throw for edge cases
    assertDoesNotThrow(() -> resolve(""));
    assertDoesNotThrow(() -> resolve("."));
  }

  @Test
  void modelPathTraversalIsBlocked() {
    Path joltBase = homeDir.resolve(".jolt").normalize();
    String malicious = "../../etc/passwd";
    Path result = joltBase.resolve(malicious).normalize();
    assertFalse(result.startsWith(joltBase));
  }

  @Test
  void modelPathNormalIsAllowed() {
    Path joltBase = homeDir.resolve(".jolt").normalize();
    String normal = "all-MiniLM-L6-v2.onnx";
    Path result = joltBase.resolve(normal).normalize();
    assertTrue(result.startsWith(joltBase));
  }
}
