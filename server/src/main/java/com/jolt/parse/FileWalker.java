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

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

public final class FileWalker {
  private static final List<String> SKIP = List.of("node_modules", ".git", "target", "build", "dist");

  public static List<Path> findJavaFiles(Path root) throws IOException {
    return walk(root, p -> p.toString().endsWith(".java"));
  }

  public static List<Path> findNonJavaSourceFiles(Path root) throws IOException {
    var exts = List.of(".xml", ".jsp", ".properties", ".yml", ".yaml", ".sql");
    return walk(root, p -> { for (var x : exts) if (p.toString().endsWith(x)) return true; return false; });
  }

  /** Find C/C++ source and header files. */
  public static List<Path> findCppFiles(Path root) throws IOException {
    var exts = List.of(".cpp", ".cc", ".cxx", ".c", ".h", ".hpp", ".hh");
    return walk(root, p -> { for (var x : exts) if (p.toString().endsWith(x)) return true; return false; });
  }

  private static List<Path> walk(Path r, java.util.function.Predicate<Path> f) throws IOException {
    var o = new ArrayList<Path>();
    Files.walkFileTree(r, new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes a) {
        String n = d.getFileName() != null ? d.getFileName().toString() : "";
        if (SKIP.contains(n) || n.startsWith(".jolt")) {
          return FileVisitResult.SKIP_SUBTREE;
        }
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFile(Path p, BasicFileAttributes a) {
        if (f.test(p)) { o.add(p); }
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFileFailed(Path file, IOException exc) {
        // Skip unreadable files gracefully
        return FileVisitResult.CONTINUE;
      }
    });
    return o;
  }

}
