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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages session authentication for the Jolt server.
 * <p>
 * On startup a one-time bootstrap token is generated. The user exchanges it
 * (via {@code POST /api/auth/login}) for a long-lived session cookie.
 * All subsequent requests must carry a valid session cookie.
 */
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);
  private static final SecureRandom RNG = new SecureRandom();

  public static final String COOKIE_NAME = "jolt_session";

  private String bootstrapToken;       // one-time token printed at startup
  private final Set<String> activeSessions = ConcurrentHashMap.newKeySet();

  /** Generate the one-time bootstrap token. Call once at startup. */
  public String generateToken() {
    byte[] bytes = new byte[16];
    RNG.nextBytes(bytes);
    bootstrapToken = toHex(bytes);
    log.info("Auth: bootstrap token generated");
    return bootstrapToken;
  }

  /**
   * Exchange the one-time bootstrap token for a new session value.
   * The bootstrap token is invalidated after a successful exchange.
   *
   * @return a new session cookie value, or {@code null} if the token is invalid/already used
   */
  public synchronized String exchangeToken(String token) {
    if (token == null || !token.equals(bootstrapToken)) {
      return null;
    }
    // Invalidate the bootstrap token (single-use)
    bootstrapToken = null;

    byte[] sessionBytes = new byte[16];
    RNG.nextBytes(sessionBytes);
    String sessionValue = toHex(sessionBytes);
    activeSessions.add(sessionValue);
    log.info("Auth: new session issued");
    return sessionValue;
  }

  /** Check whether a cookie value corresponds to an active session. */
  public boolean isValidSession(String cookieValue) {
    return cookieValue != null && activeSessions.contains(cookieValue);
  }

  /** Revoke all sessions (e.g., on shutdown). */
  public void clearAll() {
    activeSessions.clear();
  }

  private static String toHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }
}
