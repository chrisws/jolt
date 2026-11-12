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

package com.jolt.nav;

import com.jolt.model.CallEdge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CallGraphTest {

  private CallGraph graph;

  @BeforeEach
  void setUp() {
    graph = new CallGraph();
  }

  @Test
  void emptyGraphHasNoEdges() {
    assertEquals(0, graph.size());
    assertTrue(graph.allCallers().isEmpty());
  }

  @Test
  void addEdgeIncrementsSize() {
    graph.addEdge(new CallEdge("com.foo.Bar#doWork", "process", "com.foo.Baz", 42));
    assertEquals(1, graph.size());
  }

  @Test
  void getCalleesReturnsEdgesForCaller() {
    graph.addEdge(new CallEdge("com.foo.Bar#doWork", "process", "com.foo.Baz", 10));
    graph.addEdge(new CallEdge("com.foo.Bar#doWork", "validate", "", 12));
    graph.addEdge(new CallEdge("com.foo.Bar#other", "process", "com.foo.Baz", 20));

    List<CallEdge> callees = graph.getCallees("com.foo.Bar#doWork");
    assertEquals(2, callees.size());
    assertEquals("process", callees.get(0).calleeName());
    assertEquals("validate", callees.get(1).calleeName());
  }

  @Test
  void getCalleesReturnsEmptyForUnknownCaller() {
    assertTrue(graph.getCallees("nonexistent").isEmpty());
  }

  @Test
  void getCallersReturnsCallerFqns() {
    graph.addEdge(new CallEdge("com.foo.Bar#doWork", "process", "com.foo.Baz", 10));
    graph.addEdge(new CallEdge("com.foo.Qux#run", "process", "com.foo.Baz", 30));

    Set<String> callers = graph.getCallers("process");
    assertEquals(2, callers.size());
    assertTrue(callers.contains("com.foo.Bar#doWork"));
    assertTrue(callers.contains("com.foo.Qux#run"));
  }

  @Test
  void getCallersReturnsEmptyForUnknownMethod() {
    assertTrue(graph.getCallers("nonexistent").isEmpty());
  }

  @Test
  void traverseFollowsDepthLimitedBfs() {
    // A → B → C → D (chain of 3)
    graph.addEdge(new CallEdge("A#start", "mid", "B", 1));
    graph.addEdge(new CallEdge("B#mid", "end", "C", 2));
    graph.addEdge(new CallEdge("C#end", "finish", "D", 3));

    // Depth 1: only A→B edge
    List<CallEdge> depth1 = graph.traverse("A#start", 1);
    assertEquals(1, depth1.size());
    assertEquals("mid", depth1.get(0).calleeName());

    // Depth 2: A→B and B→C edges
    List<CallEdge> depth2 = graph.traverse("A#start", 2);
    assertEquals(2, depth2.size());

    // Depth 3: all three edges
    List<CallEdge> depth3 = graph.traverse("A#start", 3);
    assertEquals(3, depth3.size());
  }

  @Test
  void traverseReturnsEmptyForUnknownStart() {
    assertTrue(graph.traverse("nonexistent#method", 5).isEmpty());
  }

  @Test
  void allCallersReturnsCallerFqns() {
    graph.addEdge(new CallEdge("com.foo.Bar#doWork", "process", "", 10));
    graph.addEdge(new CallEdge("com.foo.Qux#run", "validate", "", 20));

    Set<String> callers = graph.allCallers();
    assertEquals(2, callers.size());
    assertTrue(callers.contains("com.foo.Bar#doWork"));
    assertTrue(callers.contains("com.foo.Qux#run"));
  }

  @Test
  void clearRemovesAllEdges() {
    graph.addEdge(new CallEdge("A#start", "mid", "B", 1));
    graph.addEdge(new CallEdge("B#mid", "end", "C", 2));
    assertEquals(2, graph.size());

    graph.clear();

    assertEquals(0, graph.size());
    assertTrue(graph.allCallers().isEmpty());
    assertTrue(graph.getCallers("mid").isEmpty());
  }

  @Test
  void multipleEdgesBetweenSamePairAreTracked() {
    graph.addEdge(new CallEdge("A#start", "process", "B", 10));
    graph.addEdge(new CallEdge("A#start", "process", "B", 25));

    assertEquals(2, graph.size());
    assertEquals(2, graph.getCallees("A#start").size());
    // incoming tracks unique callers only
    assertEquals(1, graph.getCallers("process").size());
  }

}
