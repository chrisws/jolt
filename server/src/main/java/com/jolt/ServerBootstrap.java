package com.jolt;

import com.jolt.api.ApiRoutes;
import com.jolt.api.WsHub;
import com.jolt.index.LuceneIndexer;
import com.jolt.model.ClassSymbol;
import com.jolt.nav.SymbolTable;
import com.jolt.parse.FileWalker;
import com.jolt.parse.JavaParser;
import com.jolt.watch.FileChangeEvent;
import com.jolt.watch.FileWatcher;
import io.javalin.Javalin;
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

    try {
      List<Path> javaFiles = FileWalker.findJavaFiles(homeDir);
      log.info("Found {} Java files under {}", javaFiles.size(), homeDir);

      JavaParser parser = new JavaParser();
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

      symbolTable.addAll(allSymbols);
      indexer.commit();

      log.info("Indexing complete: {} classes in symbol table", symbolTable.size());

    } catch (Exception e) {
      indexer.close(); // clean up on failure before rethrowing  
      throw e; 
    }

    // ─── Server phase ────────────────────────────────────── 
    // NOTE: host/port binding via Jetty config is a Phase 2+ concern.  
    // For now we use defaults (all interfaces, port 8080) unless overridden by system property.  
    System.setProperty("javalin.port", String.valueOf(port));  
    if (!"localhost".equals(host)) {
      System.setProperty("javalin.host", host);  
    }  

    Javalin app = Javalin.create();  

    // WebSocket hub for live notifications  
    WsHub wsHub = new WsHub();  
    wsHub.register(app);  

    ApiRoutes routes = new ApiRoutes(symbolTable, indexer, homeDir);  
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
    log.info("Jolt server listening on http://{}:{}/api/status", host, port);

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

  }  

}

