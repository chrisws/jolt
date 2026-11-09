package com.jolt.api;

import io.javalin.Javalin;  
import org.slf4j.Logger;  
import org.slf4j.LoggerFactory;  

import java.util.List;  
import java.util.concurrent.CopyOnWriteArrayList;  
import java.util.concurrent.atomic.AtomicInteger;  

/**  
 * Manages WebSocket connections and provides broadcast to connected clients.  
 * Clients connect to `/ws` and receive JSON messages like:  
 * <pre>{"type":"file_changed","path":"src/Foo.java","event":"modify"}</pre>  
 */  
public class WsHub {  

  private static final Logger log = LoggerFactory.getLogger(WsHub.class);  

  private final AtomicInteger connectionCount = new AtomicInteger(0);  
  // Store raw session objects for broadcasting (type varies by Javalin version)  
  @SuppressWarnings("rawtypes")  
  private final List<Object> sessions = new CopyOnWriteArrayList<>();  

  /** Register the WebSocket endpoint on the Javalin app. */  
  public void register(Javalin app) {    
    app.ws("/ws", ws -> {      
      ws.onConnect(ctx -> {        
        connectionCount.incrementAndGet();        
        // ctx is a WsConnectContext — in Javalin 5 it wraps/exposes the underlying session.       
        // We store it and use reflection for send/isOpen since the exact type varies.   
       try {          
          var sessionObj = findSession(ctx);          
          if (sessionObj != null) sessions.add(sessionObj);         
       } catch (Exception e) {         
         log.debug("Could not extract WS session: {}", e.getMessage());       
     }        
        log.info("WS client connected (total={})", connectionCount.get());      
     });      

      ws.onClose(ctx -> {        
        connectionCount.decrementAndGet();        
        try {          
          var sessionObj = findSession(ctx);          
          if (sessionObj != null) sessions.remove(sessionObj);         
       } catch (Exception ignored) {}        
        log.info("WS client disconnected (total={})", connectionCount.get());      
     });      

      // Note: ws.onError signature varies by Javalin patch version; omitted for compatibility  
   }); 
  }

  /** Try to extract the underlying Jetty session from a context object via common accessor patterns. */  
  private Object findSession(Object ctx) throws Exception {    
    // Pattern 1: ctx has .getSession() method   
    try { return ctx.getClass().getMethod("getSession").invoke(ctx); } catch (NoSuchMethodException ignored) {}    
    // Pattern 2: ctx IS the session (has send/isOpen directly)   
    if (ctx.getClass().getMethod("isOpen") != null && ctx.getClass().getMethod("send", String.class) != null) {     
      return ctx;   
   }    
    // Pattern 3: field access — look for a 'session' or 'jettySession' field   
    for (var f : ctx.getClass().getDeclaredFields()) {     
      f.setAccessible(true);       
      Object val = f.get(ctx);       
      if (val != null && val.getClass().getName().contains("Session")) {         
        return val;       
     }   
   }    
    return null;  
  }  

  /** Broadcast a JSON string to all connected clients. */  
  public void broadcast(String jsonMessage) {    
    var stale = new java.util.ArrayList<Object>();      
    for (var obj : sessions) {     
      try {       
        java.lang.reflect.Method m = obj.getClass().getMethod("send", String.class);       
        m.invoke(obj, jsonMessage);     
     } catch (Exception e) {       
        stale.add(obj);     
     }   
   }    
    sessions.removeAll(stale);  
  }  

  /** Convenience: broadcast a file change event. */  
  public void broadcastFileChange(String path, String eventType) {    
    String msg = String.format(      
      "{\"type\":\"file_changed\",\"path\":\"%s\",\"event\":\"%s\"}",      
      escapeJson(path), escapeJson(eventType));    
    broadcast(msg);  
  }  

  /** Current number of connected WebSocket clients. */  
  public int connectionCount() { return connectionCount.get(); }

  private static String escapeJson(String s) {    
    return s.replace("\\", "\\\\").replace("\"", "\\\"");  
  }  

}