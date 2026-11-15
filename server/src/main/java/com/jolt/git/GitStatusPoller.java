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

package com.jolt.git;

import com.jolt.api.SettingsService;
import com.jolt.api.WsHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

/**
 * Periodically polls git working-tree status and HEAD hash.
 * Broadcasts a {@code git_status_changed} WebSocket message when changes are detected.
 */
public class GitStatusPoller implements AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(GitStatusPoller.class);

  private static final Set<Integer> ALLOWED_INTERVALS = Set.of(3, 5, 10, 30);

  private final GitService gitSvc;
  private final WsHub wsHub;
  private final SettingsService settingsSvc;

  private ScheduledExecutorService scheduler;
  private volatile String prevHeadHash = "";
  private volatile Set<String> prevStatusSet = Set.of();

  public GitStatusPoller(GitService gitSvc, WsHub wsHub, SettingsService settingsSvc) {
    this.gitSvc = gitSvc;
    this.wsHub = wsHub;
    this.settingsSvc = settingsSvc;
  }

  /** Start the polling loop. */
  public void start() {
    int intervalSec = resolveInterval();
    log.info("Git status poller started (interval={}s)", intervalSec);

    scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "jolt-git-poller");
      t.setDaemon(true);
      return t;
    });

    scheduler.scheduleAtFixedRate(this::poll, intervalSec, intervalSec, TimeUnit.SECONDS);
  }

  /** Poll git state and broadcast if changed. */
  void poll() {
    try {
      String headHash = gitSvc.headHash();
      List<Map<String, String>> status = gitSvc.workingTreeStatus(true);

      Set<String> currentSet = status.stream()
          .map(e -> e.get("path") + ":" + e.get("type"))
          .collect(java.util.stream.Collectors.toSet());

      boolean headChanged = !headHash.equals(prevHeadHash);
      boolean statusChanged = !currentSet.equals(prevStatusSet);

      if (headChanged || statusChanged) {
        log.debug("[GIT-POLL] Change detected (head={}, status={})", headChanged, statusChanged);

        String msg = String.format(
            "{\"type\":\"git_status_changed\",\"headHash\":\"%s\",\"changes\":%s}",
            escapeJson(headHash), toJsonArray(status));
        wsHub.broadcast(msg);
      }

      prevHeadHash = headHash;
      prevStatusSet = currentSet;

    } catch (Exception e) {
      log.warn("[GIT-POLL] Error during poll: {}", e.getMessage());
    }
  }

  private int resolveInterval() {
    Object val = settingsSvc.getSetting("git.pollInterval");
    int interval = 5; // default
    if (val instanceof Number n) {
      interval = n.intValue();
    } else if (val instanceof String s) {
      try { interval = Integer.parseInt(s); } catch (NumberFormatException ignored) {}
    }
    return ALLOWED_INTERVALS.contains(interval) ? interval : 5;
  }

  private String toJsonArray(List<Map<String, String>> status) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < status.size(); i++) {
      if (i > 0) sb.append(",");
      Map<String, String> entry = status.get(i);
      sb.append("{\"path\":\"").append(escapeJson(entry.get("path")))
        .append("\",\"type\":\"").append(escapeJson(entry.get("type")))
        .append("\"}");
    }
    sb.append("]");
    return sb.toString();
  }

  private static String escapeJson(String s) {
    if (s == null) return "";
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  @Override
  public void close() {
    if (scheduler != null) {
      scheduler.shutdownNow();
      log.info("Git status poller stopped");
    }
  }
}
