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

/** Represents a single method invocation edge in the call graph. */
public record CallEdge(
    String callerFqn,      // "com.foo.Bar#doWork"
    String calleeName,     // "process" (resolved name if possible)
    String calleeReceiver, // receiver type name if detectable, else ""
    int line               // line number of the invocation
) {}
