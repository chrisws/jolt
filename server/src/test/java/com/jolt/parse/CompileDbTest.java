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

package com.jolt.parse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CompileDbTest {

  @TempDir
  Path tempDir;

  private Path dbFile;

  @BeforeEach
  void setUp() throws Exception {
    dbFile = tempDir.resolve("compile_commands.json");
    String json = """
        [
          {
            "directory": "/home/user/project/build",
            "command": "g++ -I/home/user/project/include -DDEBUG=1 -std=c++17 -c /home/user/project/src/main.cpp -o main.o",
            "file": "/home/user/project/src/main.cpp"
          },
          {
            "directory": "/home/user/project/build",
            "command": "g++ -I/home/user/project/include -DDEBUG=1 -std=c++17 -c /home/user/project/src/utils.cpp -o utils.o",
            "file": "/home/user/project/src/utils.cpp"
          }
        ]
        """;
    Files.writeString(dbFile, json);
  }

  @Test
  void load_validDb_populatesEntries() {
    CompileDb db = new CompileDb();
    db.load(dbFile);

    assertEquals(2, db.size());
    assertFalse(db.isEmpty());
  }

  @Test
  void getAllFiles_returnsAllSourceFiles() {
    CompileDb db = new CompileDb();
    db.load(dbFile);

    List<String> files = db.getAllFiles();
    assertEquals(2, files.size());

    // Should contain the absolute paths (normalised)
    assertTrue(files.stream().anyMatch(f -> f.endsWith("main.cpp")));
    assertTrue(files.stream().anyMatch(f -> f.endsWith("utils.cpp")));
  }

  @Test
  void getFlags_extractsCompilerFlags() {
    CompileDb db = new CompileDb();
    db.load(dbFile);

    // Use the absolute path that was stored  
    String filePath = db.getAllFiles().stream()  
        .filter(f -> f.endsWith("main.cpp"))  
        .findFirst()  
        .orElseThrow();  

    List<String> flags = db.getFlags(filePath);

    // Should contain include, define, and std flags  
    assertTrue(flags.contains("-I/home/user/project/include"));  
    assertTrue(flags.contains("-DDEBUG=1"));  
    assertTrue(flags.contains("-std=c++17"));  

    // Should NOT contain -c, -o, output file, or source file  
    assertFalse(flags.contains("-c"));  
    assertFalse(flags.contains("-o"));  
    assertFalse(flags.contains("main.o"));  
    assertFalse(flags.stream().anyMatch(f -> f.endsWith(".cpp")));  
  }

  @Test
  void getFlags_unknownFile_returnsEmpty() {
    CompileDb db = new CompileDb();
    db.load(dbFile);

    List<String> flags = db.getFlags("/nonexistent/file.cpp");
    assertTrue(flags.isEmpty());
  }

  @Test
  void load_missingFile_graceful() {
    CompileDb db = new CompileDb();
    db.load(tempDir.resolve("nonexistent.json"));

    assertEquals(0, db.size());
    assertTrue(db.isEmpty());
  }

  @Test
  void parseCommandFlags_stripsCompilerAndOutput() {
    List<String> flags = CompileDb.parseCommandFlags(
        "clang++ -I/opt/include -DFOO -std=c++20 -Wall -c /src/test.cpp -o test.o"
    );

    assertEquals(4, flags.size());
    assertEquals("-I/opt/include", flags.get(0));
    assertEquals("-DFOO", flags.get(1));
    assertEquals("-std=c++20", flags.get(2));
    assertEquals("-Wall", flags.get(3));
  }

  @Test
  void parseCommandFlags_stripsDependencyFlags() {
    List<String> flags = CompileDb.parseCommandFlags(
        "gcc -I. -MF dep.file -MQ target.o -MT target.o -c main.c -o main.o"
    );

    // Should only have -I.  
    assertEquals(1, flags.size());  
    assertEquals("-I.", flags.get(0));  
  }

  @Test
  void loadFromProject_findsInBuildDir() throws Exception {
    // Create build/compile_commands.json  
    Path buildDir = tempDir.resolve("build");  
    Files.createDirectories(buildDir);  
    Files.writeString(buildDir.resolve("compile_commands.json"), "[]");  

    CompileDb db = CompileDb.loadFromProject(tempDir);
    assertNotNull(db);
  }

  @Test
  void loadFromProject_findsInRoot() throws Exception {
    // No build/ dir, but root has compile_commands.json  
    Files.writeString(tempDir.resolve("compile_commands.json"), "[]");  

    CompileDb db = CompileDb.loadFromProject(tempDir);
    assertNotNull(db);
  }
}
