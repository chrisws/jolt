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
import org.slf4j.Logger;  
import org.slf4j.LoggerFactory;  

import java.io.IOException;  
import java.nio.file.*;  
import java.time.Instant;  
import java.util.LinkedHashMap;  
import java.util.Map;  

/** Persists user feedback entries as JSON Lines for agentic consumption. */  
public class FeedbackService {  

  private static final Logger log = LoggerFactory.getLogger(FeedbackService.class);  
  private static final ObjectMapper mapper = new ObjectMapper();  

  private final Path feedbackFile; // .jolt/feedback.jsonl  

  public FeedbackService(Path homeDir) {    
    this.feedbackFile = homeDir.resolve(".jolt").resolve("feedback.jsonl");   
  }  

   /** Record a feedback entry and return it with timestamp. */  
  public Map<String, Object> record(String page, String file, String message) throws IOException {    
    if (message == null || message.isBlank()) throw new IllegalArgumentException("Message is required");   

    // Ensure directory exists   
    Files.createDirectories(feedbackFile.getParent());   

    Map<String, Object> entry = new LinkedHashMap<>();   
    entry.put("ts", Instant.now().toString());   
    entry.put("page", page != null ? page : "unknown");   
    if (file != null && !file.isBlank()) entry.put("file", file);   
    entry.put("msg", message);   

    String jsonLine = mapper.writeValueAsString(entry);   
    Files.writeString(feedbackFile, jsonLine + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);   

    log.info("Feedback recorded [{}]: {}", page, message.substring(0, Math.min(message.length(), 80)));    

    return entry;  
  }  

  /** Append a completed feedback entry to the tracking journal (.jolt/completed.jsonl). */
  public Map<String, Object> markCompleted(String page, String file, String message) throws IOException {
    Path completedFile = feedbackFile.getParent().resolve("completed.jsonl");
    Files.createDirectories(completedFile.getParent());

    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("ts", Instant.now().toString());
    entry.put("page", page != null ? page : "unknown");
    if (file != null && !file.isBlank()) entry.put("file", file);
    entry.put("msg", message);
    entry.put("completed", Instant.now().toString());

    String jsonLine = mapper.writeValueAsString(entry);
    Files.writeString(completedFile, jsonLine + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);

    log.info("Completed journaled [{}]: {}", page, message.substring(0, Math.min(message.length(), 80)));

    return entry;
  }

}
