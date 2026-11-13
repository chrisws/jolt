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
import com.jolt.index.LuceneIndexer;  
import com.jolt.model.ClassSymbol;  
import com.jolt.nav.SymbolTable;  
import com.jolt.parse.FileWalker;  
import com.jolt.parse.JavaParser;  
import io.javalin.Javalin;  
import picocli.CommandLine;  
import picocli.CommandLine.Option;  
import org.slf4j.Logger;  
import org.slf4j.LoggerFactory;  

import java.nio.charset.StandardCharsets;  
import java.nio.file.*;  
import java.util.List;  
import java.util.concurrent.Callable;  

@CommandLine.Command(name = "jolt", description = "Server-side Java code explorer")  
public class JoltApp implements Callable<Integer> {  

  private static final Logger log = LoggerFactory.getLogger(JoltApp.class);  

  @Option(names = "--home", description = "Codebase root (default: CWD)") Path homeDir = Paths.get(".");  
  @Option(names = "--port", description = "Listen port (default: 9000)") int port = 9000;  
  @Option(names = "--host", description = "Bind address (default: localhost)") String host = "localhost";  
  @Option(names = "--index", description = "Lucene index dir") Path indexPath;  
  @Option(names = {"--log-level"}, description = "Log level") String logLevel = "INFO";  
  @Option(names = "--reindex", description = "Force re-index") boolean reindex;
  @Option(names = "--readonly", description = "Read-only mode: no auth, no git write operations") boolean readonly;  

  public static void main(String[] args) { System.exit(new CommandLine(new JoltApp()).execute(args)); }

  @Override
  public Integer call() throws Exception {
    // Apply log level if specified
    applyLogLevel(logLevel);

    // Determine index directory — default to .jolt/index under homeDir
    Path idxPath = (indexPath != null) ? indexPath : homeDir.toAbsolutePath().resolve(".jolt").resolve("index");

    log.info("Jolt starting | home={} | port={} | host={} | index={}", homeDir, port, host, idxPath);

    ServerBootstrap.start(homeDir, port, host, idxPath, readonly);

    return 0;
  }

  private static void applyLogLevel(String level) {
    // Logback is on the classpath; use its programmatic API for simplicity in Phase 0.
    ch.qos.logback.classic.Logger rootLogger = 
        (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    
    switch (level.toUpperCase()) {
      case "TRACE" -> rootLogger.setLevel(ch.qos.logback.classic.Level.TRACE);
      case "DEBUG" -> rootLogger.setLevel(ch.qos.logback.classic.Level.DEBUG);
      case "INFO" -> rootLogger.setLevel(ch.qos.logback.classic.Level.INFO);
      case "WARN" -> rootLogger.setLevel(ch.qos.logback.classic.Level.WARN);
      case "ERROR" -> rootLogger.setLevel(ch.qos.logback.classic.Level.ERROR);
      default -> {
        log.warn("Unknown log level '{}', defaulting to INFO", level);
        rootLogger.setLevel(ch.qos.logback.classic.Level.INFO);
      }
    }
  }

}

