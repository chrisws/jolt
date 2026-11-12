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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jolt.index.EmbeddingService;
import com.jolt.index.LuceneIndexer;
import com.jolt.model.SearchHit;
import io.javalin.Javalin;
import io.javalin.websocket.WsHandlerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages WebSocket connections and provides broadcast to connected clients.
 * Clients connect to {@code /ws} and receive JSON messages like:
 * <pre>{"type":"file_changed","path":"src/Foo.java","event":"modify"}</pre>
 * <pre>{"type":"search_results","q":"foo","results":[...]}</pre>
 *
 * Clients can send JSON messages:
 * <pre>{"type":"search","q":"foo","mode":"text","limit":50}</pre>
 */
public class WsHub {

  private static final Logger log = LoggerFactory.getLogger(WsHub.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final AtomicInteger connectionCount = new AtomicInteger(0);
  @SuppressWarnings("rawtypes")
  private final List<Object> sessions = new CopyOnWriteArrayList<>();

  private final LuceneIndexer indexer;
  private final EmbeddingService embedder; // nullable — null if model file missing

  public WsHub(LuceneIndexer indexer, EmbeddingService embedder) {
    this.indexer = indexer;
    this.embedder = embedder;
  }

  /** Register the WebSocket endpoint on the Javalin app. */
  public void register(Javalin app) {
    app.unsafe.routes.addWsHandler(WsHandlerType.WEBSOCKET, "/ws", ws -> {

      ws.onConnect(ctx -> {
        connectionCount.incrementAndGet();
        try {
          var sessionObj = findSession(ctx);
          if (sessionObj != null) sessions.add(sessionObj);
        } catch (Exception e) {
          log.debug("Could not extract WS session: {}", e.getMessage());
        }
        log.info("WS client connected (total={})", connectionCount.get());
      });

      ws.onMessage(ctx -> {
        try {
          String msg = extractMessage(ctx);
          if (msg != null && !msg.isBlank()) {
            handleClientMessage(ctx, msg);
          }
        } catch (Exception e) {
          log.error("WS message handling error: {}", e.getMessage(), e);
        }
      });

      ws.onClose(ctx -> {
        connectionCount.decrementAndGet();
        try {
          var sessionObj = findSession(ctx);
          if (sessionObj != null) sessions.remove(sessionObj);
        } catch (Exception ignored) {}
        log.info("WS client disconnected (total={})", connectionCount.get());
      });

      // Note: ws.onError signature varies by Javalin patch version; omitted for compatibility
    });
  }

  // ─── Message handling ──────────────────────────────────────────

  private void handleClientMessage(Object ctx, String msg) throws Exception {
    var node = MAPPER.readTree(msg);
    String type = node.path("type").asText("");

    switch (type) {
      case "search" -> handleSearch(ctx, node);
      default -> log.debug("Unknown WS message type: {}", type);
    }
  }

  private void handleSearch(Object ctx, com.fasterxml.jackson.databind.JsonNode node) throws Exception {
    String q = node.path("q").asText("");
    String mode = node.path("mode").asText("text");
    int limit = node.path("limit").asInt(50);

    if (q.isBlank()) return;

    List<SearchHit> hits;
    if ("semantic".equals(mode) && embedder != null) {
      float[] vec = embedder.embed(q);
      hits = indexer.searchSemantic(vec, limit);
    } else {
      hits = indexer.search(q, null, limit);
    }

    ObjectNode response = MAPPER.createObjectNode();
    response.put("type", "search_results");
    response.put("q", q);
    response.set("results", MAPPER.valueToTree(hits));

    sendToClient(ctx, response.toString());
  }

  // ─── Session / message extraction (reflection-based for Javalin compat) ───

  /** Extract the message string from a WsMessageContext. */
  private String extractMessage(Object ctx) throws Exception {
    // Pattern 1: ctx.message() method
    try { return (String) ctx.getClass().getMethod("message").invoke(ctx); }
    catch (NoSuchMethodException ignored) {}
    // Pattern 2: field access — look for a 'message' field
    for (var f : ctx.getClass().getDeclaredFields()) {
      if ("message".equals(f.getName())) {
        f.setAccessible(true);
        return (String) f.get(ctx);
      }
    }
    return null;
  }

  /** Send a JSON string to a specific client (not broadcast). */
  private void sendToClient(Object ctx, String json) {
    try {
      var sessionObj = findSession(ctx);
      if (sessionObj != null) {
        var m = sessionObj.getClass().getMethod("send", String.class);
        m.invoke(sessionObj, json);
      } else {
        // Fallback: try ctx itself as the session
        var m = ctx.getClass().getMethod("send", String.class);
        m.invoke(ctx, json);
      }
    } catch (Exception e) {
      log.debug("Could not send WS response: {}", e.getMessage());
    }
  }

  /** Try to extract the underlying Jetty session from a context object via common accessor patterns. */
  private Object findSession(Object ctx) throws Exception {
    // Pattern 1: ctx has .getSession() method
    try { return ctx.getClass().getMethod("getSession").invoke(ctx); } catch (NoSuchMethodException ignored) {}
    // Pattern 2: ctx has .session() method (Javalin 5 WsMessageContext)
    try { return ctx.getClass().getMethod("session").invoke(ctx); } catch (NoSuchMethodException ignored) {}
    // Pattern 3: ctx IS the session (has send/isOpen directly)
    if (ctx.getClass().getMethod("isOpen") != null && ctx.getClass().getMethod("send", String.class) != null) {
      return ctx;
    }
    // Pattern 4: field access — look for a 'session' or 'jettySession' field
    for (var f : ctx.getClass().getDeclaredFields()) {
      f.setAccessible(true);
      Object val = f.get(ctx);
      if (val != null && val.getClass().getName().contains("Session")) {
        return val;
      }
    }
    return null;
  }

  // ─── Broadcast ─────────────────────────────────────────────────

  /** Broadcast a JSON string to all connected clients. */
  public void broadcast(String jsonMessage) {
    var stale = new java.util.ArrayList<Object>();
    for (var obj : sessions) {
      try {
        java.lang.reflect.Method m = obj.getClass().getMethod("send", String.class);
        m.invoke(obj, jsonMessage);
      } catch (Exception e) {
        stale.add(obj);
      }
    }
    sessions.removeAll(stale);
  }

  /** Convenience: broadcast a file change event. */
  public void broadcastFileChange(String path, String eventType) {
    String msg = String.format(
      "{\"type\":\"file_changed\",\"path\":\"%s\",\"event\":\"%s\"}",
      escapeJson(path), escapeJson(eventType));
    broadcast(msg);
  }

  /** Broadcast a debug event to all connected clients. */
  public void broadcastDebugEvent(com.jolt.debug.DebugEvent event) {
    ObjectNode node = MAPPER.createObjectNode();
    node.put("type", "debug_event");
    node.put("sessionId", event.sessionId());
    node.put("event", event.type());
    if (event.filePath() != null) node.put("filePath", event.filePath());
    if (event.line() > 0) node.put("line", event.line());
    if (event.threadName() != null) node.put("threadName", event.threadName());
    if (!event.payload().isEmpty()) node.set("payload", MAPPER.valueToTree(event.payload()));

    broadcast(node.toString());
  }

  /** Current number of connected WebSocket clients. */
  public int connectionCount() { return connectionCount.get(); }

  private static String escapeJson(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
