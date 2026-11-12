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
package com.jolt.model;

public record SearchHit(
    String id,           // unique doc id e.g. "com.foo.Bar#method1" or file path
    SymbolKind kind,     // CLASS, METHOD, FIELD, FILE
    String name,         // display name
    String qualifiedName,// FQN if applicable
    String filePath,     // relative path in repo
    int lineStart,       // starting line (1-based)
    float score          // Lucene relevance score
) {}

