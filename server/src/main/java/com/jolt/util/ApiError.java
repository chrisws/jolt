package com.jolt.util;

import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/** Uniform API error response helper. All errors use format: {"error","code","details"}. */
public final class ApiError {

  private static final Logger log = LoggerFactory.getLogger(ApiError.class);

  private ApiError() {}

  /** Send a structured error response. */
  public static void send(Context ctx, int status, String code, String message) {
    send(ctx, status, code, message, null);
  }

  /** Send a structured error response with optional details. */
  public static void send(Context ctx, int status, String code, String message, String details) {
    if (status >= 500) {
      log.error("API [{}]: {}", code, message);
    } else {
      log.warn("API [{}]: {}", code, message);
    }
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("error", message);
    body.put("code", code);
    if (details != null && !details.isEmpty()) {
      body.put("details", details);
    }
    ctx.status(status).json(body);
  }

  /** 500 Internal Server Error from an exception. */
  public static void internal(Context ctx, Exception e) {
    send(ctx, 500, "INTERNAL", "Internal server error", e.getMessage());
  }

  /** 404 Not Found. */
  public static void notFound(Context ctx, String what) {
    send(ctx, 404, "NOT_FOUND", what + " not found");
  }

  /** 400 Bad Request. */
  public static void badRequest(Context ctx, String message) {
    send(ctx, 400, "BAD_REQUEST", message);
  }

  /** 503 Service Unavailable. */
  public static void unavailable(Context ctx, String what) {
    send(ctx, 503, "UNAVAILABLE", what + " not available");
  }

  /** 413 Payload Too Large. */
  public static void tooLarge(Context ctx, String what) {
    send(ctx, 413, "TOO_LARGE", what + " exceeds size limit");
  }
}