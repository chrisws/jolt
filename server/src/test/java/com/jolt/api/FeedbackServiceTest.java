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

import org.junit.jupiter.api.*;  
import org.junit.jupiter.api.io.TempDir;  

import java.nio.file.*;  
import java.util.List;  
import java.util.Map;  

import static org.junit.jupiter.api.Assertions.*;  

class FeedbackServiceTest {  

  @TempDir Path tempDir;  
  private FeedbackService service;  

  @BeforeEach  
  void setUp() { service = new FeedbackService(tempDir); }  

  @Test  
  void recordWritesJsonLine() throws Exception {    
    Map<String, Object> entry = service.record("editor", "src/Foo.java", "Add go-to-definition");   

    // Verify file exists with one line   
    Path feedbackFile = tempDir.resolve(".jolt").resolve("feedback.jsonl");     
    assertTrue(Files.exists(feedbackFile));     

    List<String> lines = Files.readAllLines(feedbackFile);     
    assertEquals(1, lines.size());      

    // Verify JSON structure contains expected fields   
    String json = lines.get(0);      
    assertTrue(json.contains("\"ts\""));      
    assertTrue(json.contains("\"page\":\"editor\""));      
    assertTrue(json.contains("\"file\":\"src/Foo.java\""));      
    assertTrue(json.contains("\"msg\":\"Add go-to-definition\""));  
  }  

  @Test  
  void multipleRecordsAppendToFile() throws Exception {    
    service.record("search", null, "Search is slow");   
    service.record("editor", "src/Bar.java", "Need refactoring hint");   

   Path feedbackFile = tempDir.resolve(".jolt").resolve("feedback.jsonl");    
   List<String> lines = Files.readAllLines(feedbackFile);    
   assertEquals(2, lines.size()); 
  }  

  @Test  
  void recordWithoutFileFieldOmitsKey() throws Exception {    
   Map<String, Object> entry = service.record("dashboard", null, "Dashboard needs dark mode toggle");    

   Path feedbackFile = tempDir.resolve(".jolt").resolve("feedback.jsonl");    
   String line = Files.readString(feedbackFile).trim();    
   assertFalse(line.contains("\"file\""), "Should not include file key when null");  
  }  

  @Test  
  void blankMessageThrowsException() {    
    assertThrows(IllegalArgumentException.class, () -> service.record("editor", null, ""));     
    assertThrows(IllegalArgumentException.class, () -> service.record("editor", null, "   "));  
  }  

}