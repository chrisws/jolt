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

class LintServiceTest {

  @TempDir
  Path tempDir;

  private LintService service;

  @BeforeEach
  void setUp() {
    service = new LintService(new CompileDb());
  }

  @Test
  void unsupportedExtensionReturnsEmptyList() throws Exception {
    Path file = tempDir.resolve("test.txt");
    Files.writeString(file, "hello world");

    List<Problem> problems = service.lint(file);

    assertTrue(problems.isEmpty());
  }

  @Test
  void javaExtensionDispatchesToJavaLinter() throws Exception {
    Path file = tempDir.resolve("Test.java");
    Files.writeString(file, """
      import java.util.ArrayList;

      public class Test {
        public static void main(String[] args) {}
      }
      """);

    List<Problem> problems = service.lint(file);

    assertNotNull(problems);
    assertTrue(problems.stream().allMatch(p -> "checkstyle".equals(p.source())));
  }

  @Test
  void cppExtensionDispatchesToCppLinter() throws Exception {
    // C++ file — may return empty if clang-tidy is not installed, but should not throw
    Path file = tempDir.resolve("test.cpp");
    Files.writeString(file, "int main() { return 0; }\n");

    assertDoesNotThrow(() -> service.lint(file));
  }

  @Test
  void problemRecordFieldsArePopulated() throws Exception {
    Path file = tempDir.resolve("Bad.java");
    Files.writeString(file, """
      import java.util.ArrayList;

      public class Bad {
        public static void main(String[] args) {}
      }
      """);

    List<Problem> problems = service.lint(file);

    if (!problems.isEmpty()) {
      Problem p = problems.get(0);
      assertTrue(p.line() > 0, "Line should be positive");
      assertNotNull(p.message(), "Message should not be null");
      assertNotNull(p.source(), "Source should not be null");
    }
  }
}