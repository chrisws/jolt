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

package com.jolt.index;

import com.jolt.model.ClassSymbol;
import com.jolt.nav.CallGraph;
import com.jolt.nav.SymbolTable;
import com.jolt.parse.CompileDb;
import com.jolt.parse.CppParser;
import com.jolt.parse.FileWalker;
import com.jolt.parse.JavaParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Builds the symbol table, call graph, and Lucene index from source files. */
public final class IndexingService {

  private static final Logger log = LoggerFactory.getLogger(IndexingService.class);

  private IndexingService() {}

  public record Result(SymbolTable symbols, LuceneIndexer indexer, CallGraph callGraph) {}

  public static Result build(Path homeDir, Path indexPath) throws Exception {
    SymbolTable symbolTable = new SymbolTable();
    Files.createDirectories(indexPath);
    LuceneIndexer indexer = new LuceneIndexer(indexPath);
    CallGraph callGraph = new CallGraph();

    try {
      indexJava(homeDir, symbolTable, indexer, callGraph);
      indexCpp(homeDir, symbolTable, indexer);

      indexer.commit();
      log.info("Indexing complete: {} classes, {} call edges", symbolTable.size(), callGraph.size());

    } catch (Exception e) {
      indexer.close();
      throw e;
    }

    return new Result(symbolTable, indexer, callGraph);
  }

  private static void indexJava(Path homeDir, SymbolTable table, LuceneIndexer indexer, CallGraph callGraph) throws Exception {
    List<Path> javaFiles = FileWalker.findJavaFiles(homeDir);
    log.info("Found {} Java files under {}", javaFiles.size(), homeDir);

    JavaParser parser = new JavaParser();
    parser.setCallGraph(callGraph);
    List<ClassSymbol> allSymbols = new ArrayList<>();

    for (int i = 0; i < javaFiles.size(); i++) {
      Path file = javaFiles.get(i);
      List<ClassSymbol> symbols = parser.parse(file);
      for (ClassSymbol cs : symbols) {
        allSymbols.add(cs);
        indexer.indexClass(cs);
      }

      String relPath = homeDir.relativize(file).toString();
      try {
        indexer.indexFile(relPath, Files.readString(file));
      } catch (Exception e) {
        log.debug("Skipping raw index for {}: {}", relPath, e.getMessage());
      }

      if ((i + 1) % 100 == 0) {
        log.info("Indexed {}/{} files", i + 1, javaFiles.size());
      }
    }

    table.addAll(allSymbols);
  }

  private static void indexCpp(Path homeDir, SymbolTable table, LuceneIndexer indexer) throws Exception {
    Path cmakeFile = homeDir.resolve("CMakeLists.txt");
    if (!Files.exists(cmakeFile)) return;

    CompileDb compileDb = CompileDb.loadFromProject(homeDir);
    if (compileDb.isEmpty()) {
      log.info("CMake project detected but no compile_commands.json — skipping C++ indexing");
      return;
    }

    List<Path> cppFiles = FileWalker.findCppFiles(homeDir);
    log.info("Found {} C/C++ files under {}", cppFiles.size(), homeDir);

    CppParser cppParser = new CppParser(compileDb);
    List<ClassSymbol> allSymbols = new ArrayList<>();
    int count = 0;

    for (Path file : cppFiles) {
      List<ClassSymbol> symbols = cppParser.parse(file);
      for (ClassSymbol cs : symbols) {
        allSymbols.add(cs);
        indexer.indexClass(cs);
        count++;
      }

      String relPath = homeDir.relativize(file).toString();
      try {
        indexer.indexFile(relPath, Files.readString(file));
      } catch (Exception e) {
        log.debug("Skipping raw index for {}: {}", relPath, e.getMessage());
      }
    }

    table.addAll(allSymbols);
    log.info("Indexed {} C/C++ symbols", count);
  }
}
