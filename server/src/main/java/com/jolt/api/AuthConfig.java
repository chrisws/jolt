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

import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/** Wires up authentication: before-filter, CSRF guard, and login endpoint. */
public final class AuthConfig {

  private static final Logger log = LoggerFactory.getLogger(AuthConfig.class);

  private AuthConfig() {}

  /**
   * Generate a bootstrap token, print the banner, and register the auth filter + login route.
   *
   * @return the {@link AuthService} instance (for shutdown cleanup), or {@code null} in read-only mode
   */
  public static AuthService register(Javalin app, boolean readonly, String host, int port) {
    if (readonly) {
      log.info("Running in read-only mode — no auth, no git write operations");
      return null;
    }

    AuthService authService = new AuthService();
    String bootstrapToken = authService.generateToken();

    System.out.println();
    System.out.println("  ┌────────────────────────────────────────────────────────────────────────┐");
    System.out.println("  │  Jolt server ready                                                     │");
    System.out.println("  │  Open: http://" + host + ":" + port + "/?token=" + bootstrapToken);
    System.out.println("  └────────────────────────────────────────────────────────────────────────┘");
    System.out.println();

    final AuthService authRef = authService;

    // ── Before-filter: auth + CSRF ──
    app.unsafe.routes.before(ctx -> {
      String path = ctx.path();

      // Public paths: static files, root, login page, login endpoint
      if (path.startsWith("/static/") || path.equals("/") || path.equals("/login.html")
          || path.equals("/api/auth/login")) {
        return;
      }

      // Auth check for /api/* and /ws
      if (path.startsWith("/api/") || path.startsWith("/ws")) {
        var cookie = ctx.cookie(AuthService.COOKIE_NAME);
        if (cookie == null || !authRef.isValidSession(cookie)) {
          log.info("[AUTH] 401 for {} — cookie={}", path, cookie);
          if (path.startsWith("/api/")) {
            ctx.status(401).json(Map.of("error", "unauthorized"));
          } else {
            ctx.status(401);
          }
          ctx.result("");
          return;
        }

        // CSRF check on state-changing methods
        var method = ctx.method();
        if (method == io.javalin.http.HandlerType.POST || method == io.javalin.http.HandlerType.PUT
            || method == io.javalin.http.HandlerType.DELETE || method == io.javalin.http.HandlerType.PATCH) {
          if (!"true".equals(ctx.header("X-Jolt-Request"))) {
            ctx.status(403).json(Map.of("error", "csrf rejected"));
            ctx.result("");
            return;
          }
        }
      }
    });

    // ── Login endpoint ──
    app.unsafe.routes.post("/api/auth/login", ctx -> {
      String loginToken = ctx.queryParam("token");
      log.info("[AUTH] Login attempt — queryParam token={}", loginToken);

      if (loginToken == null || loginToken.isBlank()) {
        try {
          var body = ctx.bodyAsClass(Map.class);
          log.info("[AUTH] Body parsed: {}", body);
          if (body != null) loginToken = (String) body.get("token");
        } catch (Exception e) {
          log.warn("[AUTH] Failed to parse body: {}", e.getMessage());
        }
      }
      log.info("[AUTH] Final token value={}", loginToken);

      String sessionValue = authRef.exchangeToken(loginToken);
      log.info("[AUTH] exchangeToken result={}", sessionValue != null ? "OK" : "null (rejected)");

      if (sessionValue == null) {
        ctx.status(401).json(Map.of("error", "invalid token"));
        return;
      }

      ctx.header("Set-Cookie", AuthService.COOKIE_NAME + "=" + sessionValue + "; Path=/; HttpOnly; SameSite=Strict");
      ctx.json(Map.of("ok", true));
    });

    return authService;
  }
}
