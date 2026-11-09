package com.jolt.watch;

import org.junit.jupiter.api.*;  
import org.junit.jupiter.api.io.TempDir;  

import java.nio.file.*;  
import java.util.List;  
import java.util.concurrent.*;  

import static org.junit.jupiter.api.Assertions.*;  

class FileWatcherTest {  

  @TempDir Path tempDir;  

  @Test  
  void detectsFileModification() throws Exception {    
    // Create a subdirectory and a file inside it   
    Path subdir = tempDir.resolve("src");     
    Files.createDirectories(subdir);     
    Path targetFile = subdir.resolve("Hello.java");     
    Files.writeString(targetFile, "public class Hello {}");   

    List<List<FileChangeEvent>> received = new CopyOnWriteArrayList<>();    
    var latch = new CountDownLatch(1);    

   FileWatcher watcher = new FileWatcher(tempDir, 100L); // short debounce for test speed   
   watcher.addListener(events -> {       
     received.add(events);       
     latch.countDown();     
   });   

   try {     
     watcher.start();     

     // Give the watcher a moment to register directories     
     Thread.sleep(200);      

     // Modify the file — this should trigger ENTRY_MODIFY     
     Thread.sleep(50); // ensure OS has flushed the initial state     
     Files.writeString(targetFile, "public class Hello { int x; }");     

     assertTrue(latch.await(5, TimeUnit.SECONDS), "Should receive change event within timeout");      
     assertFalse(received.isEmpty());      
     assertTrue(received.get(0).stream().anyMatch(e -> e.path().contains("Hello.java")));   
   } finally {     
     watcher.close();   
   }  
  }  

  @Test  
  void detectsFileCreation() throws Exception {    
    Path subdir = tempDir.resolve("newdir");     
    Files.createDirectories(subdir);   

    List<List<FileChangeEvent>> received = new CopyOnWriteArrayList<>();    
    var latch = new CountDownLatch(1);    

    FileWatcher watcher = new FileWatcher(tempDir, 100L);   
    watcher.addListener(events -> {       
      received.add(events);       
      latch.countDown();     
   });   

   try {     
     watcher.start();     
     Thread.sleep(200);      

     // Create a new file in an already-watched directory     
     Files.writeString(subdir.resolve("New.java"), "class New {}");     

     assertTrue(latch.await(5, TimeUnit.SECONDS));      
     assertTrue(received.stream().flatMap(List::stream)         
         .anyMatch(e -> e.eventType().equals("create") && e.path().contains("New.java")));   
   } finally {     
     watcher.close();   
   }  
  }  

  @Test  
  void closeStopsWatching() throws Exception {    
    FileWatcher watcher = new FileWatcher(tempDir, 50L);   
    watcher.start();   
    watcher.close();   

    // Should not throw on double-close or after stop   
    assertDoesNotThrow(() -> watcher.close());  
  }  

}