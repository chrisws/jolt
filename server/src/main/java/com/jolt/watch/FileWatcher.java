package com.jolt.watch;

import org.slf4j.Logger;  
import org.slf4j.LoggerFactory;  

import java.io.IOException;  
import java.nio.file.*;  
import java.util.ArrayList;  
import java.util.List;  
import java.util.concurrent.*;  
import java.util.function.Consumer;  

/**  
 * Watches the codebase directory for file changes using {@link WatchService}.  
 * Events are debounced (batched over a configurable window) before being  
 * delivered to registered listeners. Runs on a dedicated daemon thread.  
 */  
public class FileWatcher implements AutoCloseable {  

  private static final Logger log = LoggerFactory.getLogger(FileWatcher.class);  

  private final Path watchRoot;  
  private final long debounceMs;  
  private final List<Consumer<List<FileChangeEvent>>> listeners = new CopyOnWriteArrayList<>();  

  private WatchService watchService;  
  private Thread workerThread;  
  private volatile boolean running = false;  

  public FileWatcher(Path root, long debounceMs) {
    this.watchRoot = root.toAbsolutePath().normalize();
    this.debounceMs = debounceMs;
  }

  /** Register a callback that receives batches of change events. */
  public void addListener(Consumer<List<FileChangeEvent>> listener) {
    listeners.add(listener);
  }

  /** Start watching in a background daemon thread. */
  public synchronized void start() throws IOException {
    if (running) return;

    watchService = FileSystems.getDefault().newWatchService();
    
    // Walk the tree and register all directories (skip .git, target, node_modules)  
    walkAndRegister(watchRoot, watchService);

    running = true;
    workerThread = new Thread(this::watchLoop, "jolt-file-watcher");
    workerThread.setDaemon(true);
    workerThread.start();

    log.info("File watcher started on {} (debounce={}ms)", watchRoot, debounceMs);
  }

  private void walkAndRegister(Path dir, WatchService ws) throws IOException {
    try (var stream = Files.list(dir)) {
      for (Path entry : stream.toList()) {
        if (!Files.isDirectory(entry)) continue;

        String name = entry.getFileName().toString();
        // Skip common non-source directories  
        if (name.equals(".git") || name.equals("target") || name.equals("node_modules") 
            || name.equals(".jolt") || name.equals("build")) continue;  

        entry.register(ws, StandardWatchEventKinds.ENTRY_MODIFY, 
                       StandardWatchEventKinds.ENTRY_CREATE, 
                       StandardWatchEventKinds.ENTRY_DELETE);  
        walkAndRegister(entry, ws); // recurse into subdirs  
      }
    } catch (IOException e) {
      log.debug("Skipping directory {}: {}", dir, e.getMessage());
    }
  }

  @SuppressWarnings("unchecked")  
  private void watchLoop() {    
    while (running) {      
      WatchKey key;      
      try {        
        key = watchService.take(); // blocks until an event is available       
     } catch (InterruptedException e) {         
       Thread.currentThread().interrupt();         
       break;       
     } catch (ClosedWatchServiceException e) {         
       break;       
     }      

      List<FileChangeEvent> batch = new ArrayList<>();  

      for (var event : key.pollEvents()) {
        Path changedRel = (Path) event.context();
        Path changedPath = ((Path) key.watchable()).resolve(changedRel);
        String relativePath;
        try {
          relativePath = watchRoot.relativize(changedPath).toString();
        } catch (IllegalArgumentException e) {
          relativePath = changedPath.toString(); // fallback: cross-filesystem paths can't be relativized
        }

        String type;        
        var kind = event.kind();        
        if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {          
          type = "modify";        
       } else if (kind == StandardWatchEventKinds.ENTRY_CREATE) {          
          type = "create";        
       } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {          
          type = "delete";        
       } else {          
          type = "unknown";        
       }        

        batch.add(new FileChangeEvent(relativePath, type));  

        // If a directory was created, register it so we keep watching  
        if (kind == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(changedPath)) {          
          try { walkAndRegister(changedPath, watchService); }           
          catch (IOException ignored) {}        
       }      
     }      

      key.reset();

      if (!batch.isEmpty()) {
        deliverWithDebounce(batch);
      }
    }
  }

  /** Debounce: accumulate events and deliver after the window elapses with no new arrivals. */  
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {    
    Thread t = new Thread(r, "jolt-watcher-debounce");     
    t.setDaemon(true);     
    return t;  
  });  

  private volatile List<FileChangeEvent> pendingEvents = null;  

  private synchronized void deliverWithDebounce(List<FileChangeEvent> batch) {    
    // Accumulate into pending   
    if (pendingEvents == null) pendingEvents = new ArrayList<>();   
    pendingEvents.addAll(batch);   

    // Schedule delivery after debounce window  
    scheduler.schedule(() -> {     
      List<FileChangeEvent> toDeliver;      
      synchronized (this) {        
        toDeliver = pendingEvents != null ? new ArrayList<>(pendingEvents) : List.of();        
        pendingEvents = null;      
     }      

      if (!toDeliver.isEmpty()) {        
        log.debug("Delivering {} file change event(s)", toDeliver.size());        
        for (var listener : listeners) {          
          try { listener.accept(toDeliver); }           
          catch (Exception e) {             
            log.error("Listener error: {}", e.getMessage());           
       }        
       }      
     }
   }, debounceMs, TimeUnit.MILLISECONDS); 
  }

  @Override  
  public synchronized void close() throws Exception {    
    running = false;    
    if (workerThread != null) workerThread.interrupt();    
    if (watchService != null) watchService.close();    
    scheduler.shutdownNow();    
    log.info("File watcher stopped");  
  }  

}