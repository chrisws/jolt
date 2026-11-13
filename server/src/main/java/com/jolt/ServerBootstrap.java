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
import com.jolt.api.AuthConfig;
import com.jolt.api.AuthService;
import com.jolt.api.SettingsService;
import com.jolt.api.WsHub;
import com.jolt.debug.DebugManager;
import com.jolt.index.EmbeddingService;
import com.jolt.index.IndexingService;
import com.jolt.index.LuceneIndexer;
import com.jolt.nav.CallGraph;
import com.jolt.nav.SymbolTable;
import com.jolt.watch.FileChangeEvent;
import com.jolt.watch.FileWatcher;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Composition root: builds indexes, wires services, and starts the web server. */
public final class ServerBootstrap {

  private static final Logger log = LoggerFactory.getLogger(ServerBootstrap.class);

  private ServerBootstrap() {}

  public static void start(Path homeDir, int port, String host, Path indexPath, boolean readonly) throws Exception {
    homeDir = homeDir.toAbsolutePath().normalize();

    // ─── Indexing ────────────────────────────────────────────
    IndexingService.Result idx = IndexingService.build(homeDir, indexPath);
    SymbolTable symbolTable = idx.symbols();
    LuceneIndexer indexer = idx.indexer();
    CallGraph callGraph = idx.callGraph();

    // ─── Server ──────────────────────────────────────────────
    System.setProperty("javalin.port", String.valueOf(port));
    if (!"localhost".equals(host)) {
      System.setProperty("javalin.host", host);
    }

    Javalin app = Javalin.create(cfg -> cfg.staticFiles.add("/static", Location.CLASSPATH));

    // ─── Auth ────────────────────────────────────────────────
    AuthService authService = AuthConfig.register(app, readonly, host, port);

    // ─── Embedding model (optional) ──────────────────────────
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

    // ─── WebSocket + API routes ──────────────────────────────
    WsHub wsHub = new WsHub(indexer, embedder);
    wsHub.register(app);

    ApiRoutes routes = new ApiRoutes(symbolTable, indexer, homeDir, callGraph, embedder, readonly);

    SettingsService settingsService = new SettingsService(homeDir);
    DebugManager debugMgr = new DebugManager(homeDir, settingsService);
    routes.setDebugManager(debugMgr);
    routes.register(app);

    // ─── File watcher ────────────────────────────────────────
    FileWatcher fileWatcher = new FileWatcher(homeDir, 300L);
    fileWatcher.addListener((List<FileChangeEvent> events) -> {
      for (FileChangeEvent evt : events) {
        wsHub.broadcastFileChange(evt.path(), evt.eventType());
      }
    });
    fileWatcher.start();

    // ─── Start ───────────────────────────────────────────────
    app.start(port);
    log.info("Jolt server listening on http://{}:{}/", host, port);

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      try {
        app.stop();
        fileWatcher.close();
        debugMgr.shutdown();
        indexer.close();
        if (authService != null) authService.clearAll();
        log.info("Clean shutdown complete");
      } catch (Exception e) {
        log.warn("Error during shutdown: {}", e.getMessage());
      }
    }, "jolt-shutdown"));

    Thread.currentThread().join();
  }
}
