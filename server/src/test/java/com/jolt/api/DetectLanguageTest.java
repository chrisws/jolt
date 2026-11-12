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

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DetectLanguageTest {

  @Test
  void javaFiles() {
    assertEquals("java", ApiRoutes.detectLanguage("src/main/java/Foo.java"));
  }

  @Test
  void cppFiles() {
    assertEquals("cpp", ApiRoutes.detectLanguage("src/main.cpp"));
    assertEquals("cpp", ApiRoutes.detectLanguage("lib/foo.cc"));
    assertEquals("cpp", ApiRoutes.detectLanguage("lib/foo.cxx"));
    assertEquals("cpp", ApiRoutes.detectLanguage("include/foo.hpp"));
    assertEquals("cpp", ApiRoutes.detectLanguage("include/foo.hxx"));
    assertEquals("cpp", ApiRoutes.detectLanguage("include/foo.h"));
  }

  @Test
  void cFiles() {
    assertEquals("c", ApiRoutes.detectLanguage("src/main.c"));
  }

  @Test
  void pythonFiles() {
    assertEquals("python", ApiRoutes.detectLanguage("scripts/run.py"));
  }

  @Test
  void javascriptFiles() {
    assertEquals("javascript", ApiRoutes.detectLanguage("app.js"));
    assertEquals("javascript", ApiRoutes.detectLanguage("app.mjs"));
  }

  @Test
  void typescriptFiles() {
    assertEquals("typescript", ApiRoutes.detectLanguage("app.ts"));
    assertEquals("typescript", ApiRoutes.detectLanguage("app.tsx"));
  }

  @Test
  void htmlFiles() {
    assertEquals("html", ApiRoutes.detectLanguage("index.html"));
    assertEquals("html", ApiRoutes.detectLanguage("page.htm"));
  }

  @Test
  void cssFiles() {
    assertEquals("css", ApiRoutes.detectLanguage("styles.css"));
  }

  @Test
  void jsonFiles() {
    assertEquals("json", ApiRoutes.detectLanguage("config.json"));
  }

  @Test
  void tomlFiles() {
    assertEquals("toml", ApiRoutes.detectLanguage("Cargo.toml"));
  }

  @Test
  void dockerfile() {
    assertEquals("dockerfile", ApiRoutes.detectLanguage("Dockerfile"));
    assertEquals("dockerfile", ApiRoutes.detectLanguage("docker/Dockerfile.dev"));
  }

  @Test
  void existingLanguagesStillWork() {
    assertEquals("xml", ApiRoutes.detectLanguage("pom.xml"));
    assertEquals("jsp", ApiRoutes.detectLanguage("view.jsp"));
    assertEquals("sql", ApiRoutes.detectLanguage("schema.sql"));
    assertEquals("yaml", ApiRoutes.detectLanguage("config.yml"));
    assertEquals("yaml", ApiRoutes.detectLanguage("config.yaml"));
    assertEquals("properties", ApiRoutes.detectLanguage("app.properties"));
    assertEquals("shell", ApiRoutes.detectLanguage("build.sh"));
    assertEquals("shell", ApiRoutes.detectLanguage("run.bash"));
    assertEquals("kotlin", ApiRoutes.detectLanguage("Main.kt"));
    assertEquals("kotlin", ApiRoutes.detectLanguage("build.kts"));
    assertEquals("groovy", ApiRoutes.detectLanguage("Build.groovy"));
    assertEquals("groovy", ApiRoutes.detectLanguage("test.gvy"));
    assertEquals("markdown", ApiRoutes.detectLanguage("README.md"));
  }

  @Test
  void unknownExtensionReturnsEmpty() {
    assertEquals("", ApiRoutes.detectLanguage("file.xyz"));
    assertEquals("", ApiRoutes.detectLanguage("noextension"));
  }
}
