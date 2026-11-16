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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JavaLinterTest {

  @TempDir
  Path tempDir;

  private JavaLinter linter;

  @BeforeEach
  void setUp() {
    linter = new JavaLinter();
  }

  @Test
  void lintReturnsListForValidJavaFile() throws Exception {
    Path file = tempDir.resolve("Clean.java");
    Files.writeString(file, """
      public class Clean {
        public static void main(String[] args) {
          System.out.println("hello");
        }
      }
      """);

    List<Problem> problems = linter.lint(file);

    assertNotNull(problems);
  }

  @Test
  void lintDoesNotThrowOnFileWithIssues() throws Exception {
    Path file = tempDir.resolve("Unused.java");
    Files.writeString(file, """
      import java.util.ArrayList;

      public class Unused {
        public static void main(String[] args) {}
      }
      """);

    assertDoesNotThrow(() -> linter.lint(file));
  }

  @Test
  void lintHandlesLongLines() throws Exception {
    String longLine = "int " + "x".repeat(130) + ";";
    Path file = tempDir.resolve("Long.java");
    Files.writeString(file, """
      public class Long {
        public static void main(String[] args) { %s }
      }
      """.formatted(longLine));

    assertDoesNotThrow(() -> linter.lint(file));
  }

  @Test
  void lintHandlesEmptyFile() throws Exception {
    Path file = tempDir.resolve("Empty.java");
    Files.writeString(file, "");

    List<Problem> problems = linter.lint(file);

    assertNotNull(problems);
  }

  @Test
  void returnedProblemsHaveValidFields() throws Exception {
    Path file = tempDir.resolve("Multi.java");
    Files.writeString(file, """
      import java.util.ArrayList;
      import java.util.HashMap;

      public class Multi {
        public static void main(String[] args) {}
      }
      """);

    List<Problem> problems = linter.lint(file);

    for (Problem p : problems) {
      assertTrue(p.line() > 0, "Line should be positive");
      assertNotNull(p.message());
      assertEquals("checkstyle", p.source());
    }
  }

  @Test
  void lintHandlesMalformedJava() throws Exception {
    Path file = tempDir.resolve("Bad.java");
    Files.writeString(file, "this is not valid java {{{");

    assertDoesNotThrow(() -> linter.lint(file));
  }
}