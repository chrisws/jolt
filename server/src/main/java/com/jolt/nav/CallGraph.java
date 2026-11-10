package com.jolt.nav;

import com.jolt.model.CallEdge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory call graph. Stores method invocation edges extracted during parsing.
 * Supports depth-limited traversal for the API endpoint.
 */
public class CallGraph {

  private static final Logger log = LoggerFactory.getLogger(CallGraph.class);

  /** callerFqn → list of outgoing call edges */
  private final Map<String, List<CallEdge>> outgoing = new ConcurrentHashMap<>();

  /** calleeName → set of caller FQNs (reverse index for "who calls me") */
  private final Map<String, Set<String>> incoming = new ConcurrentHashMap<>();

  /** Record a method invocation. */
  public void addEdge(CallEdge edge) {
    outgoing.computeIfAbsent(edge.callerFqn(), k -> new ArrayList<>()).add(edge);
    incoming.computeIfAbsent(edge.calleeName(), k -> new HashSet<>()).add(edge.callerFqn());
  }

  /** Get all direct callees of a method. */
  public List<CallEdge> getCallees(String callerFqn) {
    return outgoing.getOrDefault(callerFqn, List.of());
  }

  /** Get all direct callers of a method (by name). */
  public Set<String> getCallers(String methodName) {
    return incoming.getOrDefault(methodName, Set.of());
  }

  /**
   * Depth-limited BFS from a starting method. Returns a flat list of edges
   * representing the reachable call subgraph.
   */
  public List<CallEdge> traverse(String startFqn, int maxDepth) {
    List<CallEdge> result = new ArrayList<>();
    Set<String> visited = new HashSet<>();
    visited.add(startFqn);

    Queue<String> queue = new ArrayDeque<>();
    Map<String, Integer> depthMap = new HashMap<>();
    queue.add(startFqn);
    depthMap.put(startFqn, 0);

    while (!queue.isEmpty()) {
      String current = queue.poll();
      int currentDepth = depthMap.getOrDefault(current, 0);

      if (currentDepth >= maxDepth) continue;

      for (CallEdge edge : getCallees(current)) {
        result.add(edge);

        // Try to resolve the callee to a known FQN for further traversal
        String resolvedCallee = resolveCallee(edge);
        if (resolvedCallee != null && !visited.contains(resolvedCallee)) {
          visited.add(resolvedCallee);
          queue.add(resolvedCallee);
          depthMap.put(resolvedCallee, currentDepth + 1);
        }
      }
    }

    return result;
  }

  /** Try to resolve a call edge's callee to a known method FQN. */
  private String resolveCallee(CallEdge edge) {
    // If we have a receiver type, try "receiver#method" pattern
    if (edge.calleeReceiver() != null && !edge.calleeReceiver().isEmpty()) {
      // The receiver might be a simple name — try to match against known classes
      // This is a best-effort resolution without full type inference
      return edge.calleeReceiver() + "#" + edge.calleeName();
    }
    // Without receiver, we can't resolve — return null to stop traversal
    return null;
  }

  /** Total number of edges in the graph. */
  public int size() { return outgoing.values().stream().mapToInt(List::size).sum(); }

  /** All known caller FQNs. */
  public Set<String> allCallers() { return Collections.unmodifiableSet(outgoing.keySet()); }

  /** Clear the graph (used on reindex). */
  public void clear() {
    outgoing.clear();
    incoming.clear();
    log.info("Call graph cleared");
  }

}
