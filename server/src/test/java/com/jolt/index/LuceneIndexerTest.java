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

package com.jolt.index;

import com.jolt.model.*;  
import org.junit.jupiter.api.*;  
import org.junit.jupiter.api.io.TempDir;  

import java.nio.file.Path;  
import java.util.List;  

import static org.junit.jupiter.api.Assertions.*;  

class LuceneIndexerTest {  

  @TempDir Path tempDir;  
  private LuceneIndexer indexer;  

  @BeforeEach  
  void setUp() throws Exception {    
    indexer = new LuceneIndexer(tempDir);  
  }  

  @AfterEach  
  void tearDown() throws Exception {    
    indexer.close();  
  }  

  @Test  
  void indexAndSearchClassByFqn() throws Exception {    
    var cs = new ClassSymbol("com.example.OrderService", "OrderService", SymbolKind.CLASS, "src/OrderService.java");
    cs.lineStart = 10;   

    indexer.indexClass(cs);   
    indexer.commit();   

    List<SearchHit> hits = indexer.search("OrderService", null, 10);   
    assertFalse(hits.isEmpty());     
    assertTrue(hits.stream().anyMatch(h -> h.qualifiedName().equals("com.example.OrderService")));  
  }  

  @Test  
  void searchFiltersByKind() throws Exception {    
    var cls = new ClassSymbol("com.test.MyClass", "MyClass", SymbolKind.CLASS, "src/MyClass.java");
    cls.lineStart = 5;

    // Add a method to the class so it gets indexed too
    cls.methods.put("doWork", new MethodSymbol(
        "com.test.MyClass#doWork", "doWork", "public void doWork()",
        "void", List.of(), 8, 10, "public"));

    indexer.indexClass(cls);   
    indexer.commit();   

    // Search for only METHOD kind  
    List<SearchHit> methodsOnly = indexer.search("doWork", SymbolKind.METHOD, 10);   
    assertTrue(methodsOnly.stream().allMatch(h -> h.kind() == SymbolKind.METHOD));   

    // Search for only CLASS kind  
   List<SearchHit> classesOnly = indexer.search("MyClass", SymbolKind.CLASS, 10);    
   assertTrue(classesOnly.stream().allMatch(h -> h.kind() == SymbolKind.CLASS));  
  }  

  @Test  
  void indexFileAndFullTextSearch() throws Exception {    
    String content = "package com.example;\n\n/** A service for handling orders. */\npublic class OrderHandler {\n}";   
    indexer.indexFile("src/OrderHandler.java", content);   
    indexer.commit();   

    List<SearchHit> hits = indexer.search("handling orders", null, 10);   
    assertFalse(hits.isEmpty());     
    assertEquals(SymbolKind.FILE, hits.get(0).kind());  
  }  

  @Test  
  void upsertReplacesExistingDocument() throws Exception {    
    var cs1 = new ClassSymbol("com.dup.Dup", "Dup", SymbolKind.CLASS, "src/Dup.java");
    cs1.lineStart = 1;
    indexer.indexClass(cs1);

    // Re-index same FQN with different line
   var cs2 = new ClassSymbol("com.dup.Dup", "Dup", SymbolKind.CLASS, "src/Dup.java");
   cs2.lineStart = 99;   
   indexer.indexClass(cs2);   
   indexer.commit();   

   List<SearchHit> hits = indexer.search("Dup", SymbolKind.CLASS, 10);    
   long count = hits.stream().filter(h -> h.qualifiedName().equals("com.dup.Dup")).count();    
   assertEquals(1, count, "Upsert should replace, not duplicate");  
  }  

}