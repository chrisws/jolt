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

import com.jolt.model.ClassSymbol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory symbol resolution engine. Complements Lucene (search) by providing
 * fast O(1) lookups for go-to-definition, references, and class hierarchy.
 */
public class SymbolTable {

  private static final Logger log = LoggerFactory.getLogger(SymbolTable.class);

  /** FQN → ClassSymbol */
  private final Map<String, ClassSymbol> classes = new ConcurrentHashMap<>();

  /** FQN → list of (filePath, lineStart) where this symbol is referenced */
  private final Map<String, List<Reference>> references = new ConcurrentHashMap<>();

  public record Reference(String filePath, int line) {}

  /** Register a parsed class and its members. */
  public void addClass(ClassSymbol cs) {
    classes.put(cs.fqn, cs);
    // Index methods too for direct lookup
    for (var entry : cs.methods.entrySet()) {
      String methodFqn = cs.fqn + "#" + entry.getKey();
      // We don't store MethodSymbols in a separate map yet — Phase 2+ will need that
      // For now the ClassSymbol holds them via .methods map
    }
    log.debug("Indexed: {}", cs);
  }

  /** Bulk-add from a batch of parsed files. */
  public void addAll(List<ClassSymbol> symbols) {
    for (ClassSymbol cs : symbols) {
      addClass(cs);
    }
    log.info("Symbol table size: {} classes", classes.size());
  }

  /** Go-to-definition: resolve an FQN to its ClassSymbol. */
  public Optional<ClassSymbol> findDefinition(String fqn) {
    return Optional.ofNullable(classes.get(fqn));
  }

  /** Find all known references to a given FQN. */
  public List<Reference> findReferences(String fqn) {
    return references.getOrDefault(fqn, List.of());
  }

  /** Record a reference occurrence. */
  public void addReference(String referencedFqn, String filePath, int line) {
    references.computeIfAbsent(referencedFqn, k -> new ArrayList<>()).add(new Reference(filePath, line));
  }

  /** Get direct superclass chain (walks up). */
  public List<String> getSuperChain(String fqn) {
    List<String> chain = new ArrayList<>();
    Set<String> visited = new HashSet<>(); // prevent cycles in malformed codebases
    
    String current = fqn;
    while (current != null && !visited.contains(current)) {
      visited.add(current);
      ClassSymbol cs = classes.get(current);
      if (cs == null || cs.superClass == null) break;
      chain.add(cs.superClass);
      current = cs.superClass;
    }
    return chain;
  }

  /** Get all known subtypes of a class (linear scan — fine for Phase 0). 
   * TODO: Phase 2+ → maintain reverse index for O(1) lookup. */
  public List<ClassSymbol> getSubTypes(String superFqn) {
    List<ClassSymbol> subs = new ArrayList<>();
    for (ClassSymbol cs : classes.values()) {
      if (superFqn.equals(cs.superClass)) {
        subs.add(cs);
      } else {
        // Check interfaces too (shallow — one level)
        if (cs.interfaces.contains(superFqn)) {
          subs.add(cs);
        }
      }
    }
    return subs;
  }

  /** Total number of indexed classes. */
  public int size() { return classes.size(); }

  /** All indexed FQNs (for debugging / status endpoint). */  
  public Set<String> allClasses() { return Collections.unmodifiableSet(classes.keySet()); }

  /** All indexed ClassSymbol objects (for aggregation queries). */
  public Collection<ClassSymbol> allClassSymbols() { return Collections.unmodifiableCollection(classes.values()); }

  /** Clear the table (used on reindex). */
  public void clear() {
    classes.clear();
    references.clear();
    log.info("Symbol table cleared");
  }

}

