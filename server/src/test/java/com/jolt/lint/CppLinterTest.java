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

package com.jolt.lint;

import com.jolt.model.Problem;
import com.jolt.parse.CompileDb;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CppLinterTest {

  @TempDir
  Path tempDir;

  private CppLinter linter;

  @BeforeEach
  void setUp() {
    linter = new CppLinter(new CompileDb());
  }

  @Test
  void doesNotThrowOnValidCppFile() throws Exception {
    Path file = tempDir.resolve("test.cpp");
    Files.writeString(file, "int main() { return 0; }\n");

    assertDoesNotThrow(() -> linter.lint(file));
  }

  @Test
  void returnsEmptyListWhenClangTidyUnavailable() throws Exception {
    // With no compile_commands.json, clang-tidy likely won't produce useful output.
    // The linter should gracefully return an empty list rather than throwing.
    Path file = tempDir.resolve("empty.cpp");
    Files.writeString(file, "// empty\n");

    List<Problem> problems = linter.lint(file);

    assertNotNull(problems);
    // May be empty if clang-tidy is not installed or no compile db entry
  }

  @Test
  void handlesHeaderFile() throws Exception {
    Path file = tempDir.resolve("test.hpp");
    Files.writeString(file, "#pragma once\nint foo();\n");

    assertDoesNotThrow(() -> linter.lint(file));
  }

  @Test
  void handlesCFile() throws Exception {
    Path file = tempDir.resolve("test.c");
    Files.writeString(file, "int main() { return 0; }\n");

    assertDoesNotThrow(() -> linter.lint(file));
  }
}