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

package com.jolt;

 import com.jolt.api.ApiRoutes;
 import com.jolt.api.WsHub;
 import com.jolt.index.EmbeddingService;
 import com.jolt.index.LuceneIndexer;
 import com.jolt.model.ClassSymbol;
 import com.jolt.nav.CallGraph;
 import com.jolt.nav.SymbolTable;
 import com.jolt.parse.CompileDb;
 import com.jolt.parse.CppParser;
 import com.jolt.parse.FileWalker;
 import com.jolt.parse.JavaParser;
 import com.jolt.watch.FileChangeEvent;
 import com.jolt.watch.FileWatcher;
 import io.javalin.Javalin;
 import io.javalin.http.staticfiles.Location;
 import org.slf4j.Logger;
 import org.slf4j.LoggerFactory;

 import java.nio.file.Files;
 import java.nio.file.Path;
 import java.util.ArrayList;
 import java.util.List;

 /** Performs indexing and starts the web server. */  
 public final class ServerBootstrap {  

   private static final Logger log = LoggerFactory.getLogger(ServerBootstrap.class);  

   public static void start(Path homeDir, int port, String host, Path indexPath) throws Exception {
     homeDir = homeDir.toAbsolutePath().normalize();

     // ─── Indexing phase ────────────────────────────────────── 
     SymbolTable symbolTable = new SymbolTable();

     Files.createDirectories(indexPath); // ensure index dir exists

     LuceneIndexer indexer = new LuceneIndexer(indexPath);

     CallGraph callGraph = new CallGraph();

     try {
       List<Path> javaFiles = FileWalker.findJavaFiles(homeDir);
       log.info("Found {} Java files under {}", javaFiles.size(), homeDir);
       JavaParser parser = new JavaParser();
       parser.setCallGraph(callGraph);

       List<ClassSymbol> allSymbols = new ArrayList<>();

       for (int i = 0; i < javaFiles.size(); i++) {
         Path file = javaFiles.get(i);

         // Parse structured symbols — filePath is set from the Path passed to parse()  
         List<ClassSymbol> symbols = parser.parse(file);
         for (ClassSymbol cs : symbols) {
           allSymbols.add(cs);
           indexer.indexClass(cs);
         }

         // Also index raw text for full-text search fallback  
         String relPath = homeDir.relativize(file).toString();  
         try {
           String content = Files.readString(file);
           indexer.indexFile(relPath, content);
         } catch (Exception e) {
           log.debug("Skipping raw index for {}: {}", relPath, e.getMessage());
         }

         if ((i + 1) % 100 == 0) {
           log.info("Indexed {}/{} files", i + 1, javaFiles.size());
         }
       } 

       // ─── C++ indexing (if compile_commands.json is available) ───
       int cppCount = 0;
       Path cmakeFile = homeDir.resolve("CMakeLists.txt");
       if (Files.exists(cmakeFile)) {
         CompileDb compileDb = CompileDb.loadFromProject(homeDir);
         if (!compileDb.isEmpty()) {
           List<Path> cppFiles = FileWalker.findCppFiles(homeDir);
           log.info("Found {} C/C++ files under {}", cppFiles.size(), homeDir);

           CppParser cppParser = new CppParser(compileDb);

           for (Path file : cppFiles) {
             List<ClassSymbol> symbols = cppParser.parse(file);
             for (ClassSymbol cs : symbols) {
               allSymbols.add(cs);
               indexer.indexClass(cs);
               cppCount++;
             }

             // Also index raw text for full-text search fallback
             String relPath = homeDir.relativize(file).toString();
             try {
               String content = Files.readString(file);
               indexer.indexFile(relPath, content);
             } catch (Exception e) {
               log.debug("Skipping raw index for {}: {}", relPath, e.getMessage());
             }
           }

           log.info("Indexed {} C/C++ symbols", cppCount);
         } else {
           log.info("CMake project detected but no compile_commands.json — skipping C++ indexing");
         }
       }

       symbolTable.addAll(allSymbols);
       indexer.commit();

       log.info("Indexing complete: {} classes, {} call edges", symbolTable.size(), callGraph.size());

     } catch (Exception e) {
       indexer.close(); // clean up on failure before rethrowing  
       throw e; 
     }

     // ─── Server phase ────────────────────────────────────── 
     System.setProperty("javalin.port", String.valueOf(port));  
     if (!"localhost".equals(host)) {
       System.setProperty("javalin.host", host);  
     }  

     Javalin app = Javalin.create(cfg -> cfg.staticFiles.add("/static", Location.CLASSPATH));

     // Load optional embedding model for semantic search (shared by WsHub + ApiRoutes)
     EmbeddingService embedder = null;
     try {
       Path modelPath = homeDir.resolve(".jolt/models/all-MiniLM-L6-v2.onnx");
       if (Files.exists(modelPath)) {
         embedder = new EmbeddingService(modelPath);
         log.info("Semantic search enabled (model: {})", modelPath.getFileName());
       } else {
         log.info("Embedding model not found at {} — semantic search disabled", modelPath);
       }
     } catch (Exception e) {
       log.warn("Failed to load embedding model: {}", e.getMessage());
     }

     // WebSocket hub for live notifications + search
     WsHub wsHub = new WsHub(indexer, embedder);
     wsHub.register(app);

     ApiRoutes routes = new ApiRoutes(symbolTable, indexer, homeDir, callGraph, embedder);
     routes.register(app);

     // File watcher → broadcast changes via WebSocket  
     FileWatcher fileWatcher = new FileWatcher(homeDir, 300L);  
     fileWatcher.addListener((List<FileChangeEvent> events) -> {      
       for (FileChangeEvent evt : events) {        
         wsHub.broadcastFileChange(evt.path(), evt.eventType());      
      }    
    });   
    fileWatcher.start();  

    app.start(port); 
    log.info("Jolt server listening on http://{}:{}/", host, port);

    // Register shutdown hook to close resources cleanly when JVM exits  
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      try {
        app.stop();
        fileWatcher.close();
        indexer.close();
        log.info("Clean shutdown complete");
      } catch (Exception e) {
        log.warn("Error during shutdown: {}", e.getMessage());
      }
    }, "jolt-shutdown"));

    // Keep main thread alive (Javalin 7 start() is non-blocking)
    Thread.currentThread().join();  

  }  

}

