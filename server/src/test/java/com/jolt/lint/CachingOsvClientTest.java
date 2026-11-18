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

package com.jolt.lint;

import com.jolt.lint.MavenLinter.CachingOsvClient;
import com.jolt.lint.MavenLinter.OsvClient;
import com.jolt.lint.MavenLinter.OsvVuln;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CachingOsvClientTest {

  @Test
  void secondCallReturnsCachedResult() {
    AtomicInteger callCount = new AtomicInteger(0);
    OsvClient delegate = (g, a, v) -> {
      callCount.incrementAndGet();
      return List.of(new OsvVuln("CVE-1", "First"));
    };

    CachingOsvClient client = new CachingOsvClient(delegate, Duration.ofHours(1));

    List<OsvVuln> first = client.query("g", "a", "1.0");
    List<OsvVuln> second = client.query("g", "a", "1.0");

    assertEquals(1, callCount.get());
    assertEquals(first, second);
  }

  @Test
  void differentParamsTriggerNewQuery() {
    AtomicInteger callCount = new AtomicInteger(0);
    OsvClient delegate = (g, a, v) -> {
      callCount.incrementAndGet();
      return List.of(new OsvVuln("CVE-" + v, "Vuln for " + v));
    };

    CachingOsvClient client = new CachingOsvClient(delegate, Duration.ofHours(1));

    client.query("g", "a", "1.0");
    client.query("g", "a", "2.0");
    client.query("g2", "a", "1.0");

    assertEquals(3, callCount.get());
  }

  @Test
  void expiredCacheTriggersRequery() throws InterruptedException {
    AtomicInteger callCount = new AtomicInteger(0);
    OsvClient delegate = (g, a, v) -> {
      callCount.incrementAndGet();
      return List.of(new OsvVuln("CVE-1", "First"));
    };

    // Use a very short TTL so we can test expiry quickly
    CachingOsvClient client = new CachingOsvClient(delegate, Duration.ofMillis(50));

    client.query("g", "a", "1.0");
    assertEquals(1, callCount.get());

    // Wait for expiry
    Thread.sleep(100);

    client.query("g", "a", "1.0");
    assertEquals(2, callCount.get());
  }

  @Test
  void emptyResultIsAlsoCached() {
    AtomicInteger callCount = new AtomicInteger(0);
    OsvClient delegate = (g, a, v) -> {
      callCount.incrementAndGet();
      return List.of();
    };

    CachingOsvClient client = new CachingOsvClient(delegate, Duration.ofHours(1));

    List<OsvVuln> first = client.query("g", "a", "1.0");
    List<OsvVuln> second = client.query("g", "a", "1.0");

    assertTrue(first.isEmpty());
    assertTrue(second.isEmpty());
    assertEquals(1, callCount.get());
  }

  @Test
  void cacheReturnsSameListInstance() {
    OsvClient delegate = (g, a, v) -> List.of(new OsvVuln("CVE-1", "First"));

    CachingOsvClient client = new CachingOsvClient(delegate, Duration.ofHours(1));

    List<OsvVuln> first = client.query("g", "a", "1.0");
    List<OsvVuln> second = client.query("g", "a", "1.0");

    assertSame(first, second);
  }
}