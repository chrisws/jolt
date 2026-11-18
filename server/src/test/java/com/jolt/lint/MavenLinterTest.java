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

import com.jolt.lint.MavenLinter.OsvClient;
import com.jolt.lint.MavenLinter.OsvVuln;
import com.jolt.model.Problem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MavenLinterTest {

  @TempDir
  Path tempDir;

  private MavenLinter linter;

  @BeforeEach
  void setUp() {
    linter = new MavenLinter(mockOsvClient());
  }

  // ── parsing tests ────────────────────────────────────────────────

  @Test
  void noDependenciesReturnsEmptyList() throws Exception {
    Path pom = tempDir.resolve("pom.xml");
    Files.writeString(pom, """
      <?xml version="1.0"?>
      <project>
        <modelVersion>4.0.0</modelVersion>
        <groupId>com.test</groupId>
        <artifactId>test</artifactId>
        <version>1.0</version>
      </project>
      """);

    List<Problem> problems = linter.lint(pom);

    assertTrue(problems.isEmpty());
  }

  @Test
  void singleDependencyWithCveProducesWarning() throws Exception {
    Path pom = tempDir.resolve("pom.xml");
    Files.writeString(pom, """
      <?xml version="1.0"?>
      <project>
        <modelVersion>4.0.0</modelVersion>
        <groupId>com.test</groupId>
        <artifactId>test</artifactId>
        <version>1.0</version>
        <dependencies>
          <dependency>
            <groupId>org.vulnerable</groupId>
            <artifactId>bad-lib</artifactId>
            <version>1.0.0</version>
          </dependency>
        </dependencies>
      </project>
      """);

    List<Problem> problems = linter.lint(pom);

    assertEquals(1, problems.size());
    Problem p = problems.get(0);
    assertEquals("CVE-2024-TEST", p.ruleId());
    assertEquals("osv", p.source());
    assertEquals(Problem.Severity.WARNING, p.severity());
    assertTrue(p.message().contains("CVE-2024-TEST"));
  }

  @Test
  void propertyVersionIsResolved() throws Exception {
    Path pom = tempDir.resolve("pom.xml");
    Files.writeString(pom, """
      <?xml version="1.0"?>
      <project>
        <modelVersion>4.0.0</modelVersion>
        <groupId>com.test</groupId>
        <artifactId>test</artifactId>
        <version>1.0</version>
        <properties>
          <lib.version>2.5.0</lib.version>
        </properties>
        <dependencies>
          <dependency>
            <groupId>org.vulnerable</groupId>
            <artifactId>bad-lib</artifactId>
            <version>${lib.version}</version>
          </dependency>
        </dependencies>
      </project>
      """);

    // The mock client matches on version "2.5.0" — if property resolution works, we get a hit
    List<Problem> problems = linter.lint(pom);

    assertEquals(1, problems.size());
    assertEquals("CVE-2024-TEST", problems.get(0).ruleId());
  }

  @Test
  void multipleCvesProduceMultipleProblems() throws Exception {
    Path pom = tempDir.resolve("pom.xml");
    Files.writeString(pom, """
      <?xml version="1.0"?>
      <project>
        <modelVersion>4.0.0</modelVersion>
        <groupId>com.test</groupId>
        <artifactId>test</artifactId>
        <version>1.0</version>
        <dependencies>
          <dependency>
            <groupId>org.vulnerable</groupId>
            <artifactId>bad-lib</artifactId>
            <version>1.0.0</version>
          </dependency>
        </dependencies>
      </project>
      """);

    // Use a mock that returns multiple vulns for this dep
    MavenLinter multiLinter = new MavenLinter((g, a, v) -> {
      if (g.equals("org.vulnerable") && a.equals("bad-lib")) {
        return List.of(
          new OsvVuln("CVE-2024-AAA", "First vuln"),
          new OsvVuln("CVE-2024-BBB", "Second vuln")
        );
      }
      return List.of();
    });

    List<Problem> problems = multiLinter.lint(pom);

    assertEquals(2, problems.size());
    assertEquals("CVE-2024-AAA", problems.get(0).ruleId());
    assertEquals("CVE-2024-BBB", problems.get(1).ruleId());
  }

  @Test
  void lineNumberOfDependencyIsCorrect() throws Exception {
    Path pom = tempDir.resolve("pom.xml");
    Files.writeString(pom, """
      <?xml version="1.0"?>
      <project>
        <modelVersion>4.0.0</modelVersion>
        <groupId>com.test</groupId>
        <artifactId>test</artifactId>
        <version>1.0</version>
        <dependencies>
          <dependency>
            <groupId>org.vulnerable</groupId>
            <artifactId>bad-lib</artifactId>
            <version>1.0.0</version>
          </dependency>
        </dependencies>
      </project>
      """);

    List<Problem> problems = linter.lint(pom);

    assertEquals(1, problems.size());
    // The <dependency> tag is on line 8 (1-based)
    assertEquals(8, problems.get(0).line());
  }

  @Test
  void cleanDependencyProducesNoProblems() throws Exception {
    Path pom = tempDir.resolve("pom.xml");
    Files.writeString(pom, """
      <?xml version="1.0"?>
      <project>
        <modelVersion>4.0.0</modelVersion>
        <groupId>com.test</groupId>
        <artifactId>test</artifactId>
        <version>1.0</version>
        <dependencies>
          <dependency>
            <groupId>org.clean</groupId>
            <artifactId>good-lib</artifactId>
            <version>3.0.0</version>
          </dependency>
        </dependencies>
      </project>
      """);

    List<Problem> problems = linter.lint(pom);

    assertTrue(problems.isEmpty());
  }

  @Test
  void unresolvablePropertyKeepsRawValue() throws Exception {
    Path pom = tempDir.resolve("pom.xml");
    Files.writeString(pom, """
      <?xml version="1.0"?>
      <project>
        <modelVersion>4.0.0</modelVersion>
        <groupId>com.test</groupId>
        <artifactId>test</artifactId>
        <version>1.0</version>
        <dependencies>
          <dependency>
            <groupId>org.vulnerable</groupId>
            <artifactId>bad-lib</artifactId>
            <version>${unknown.prop}</version>
          </dependency>
        </dependencies>
      </project>
      """);

    // Should not throw; the unresolved property is passed as-is to OSV (which returns nothing)
    assertDoesNotThrow(() -> linter.lint(pom));
  }

  // ── helpers ──────────────────────────────────────────────────────

  private OsvClient mockOsvClient() {
    return (groupId, artifactId, version) -> {
      if (groupId.equals("org.vulnerable") && artifactId.equals("bad-lib") && version.equals("1.0.0")) {
        return List.of(new OsvVuln("CVE-2024-TEST", "Test vulnerability"));
      }
      if (groupId.equals("org.vulnerable") && artifactId.equals("bad-lib") && version.equals("2.5.0")) {
        return List.of(new OsvVuln("CVE-2024-TEST", "Test vulnerability"));
      }
      return List.of();
    };
  }
}
