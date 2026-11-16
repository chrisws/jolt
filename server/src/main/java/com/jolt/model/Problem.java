package com.jolt.model;

/** A single lint/analysis finding for a source file. */
public record Problem(
  int line,
  int column,
  int endLine,
  int endColumn,
  Severity severity,
  String message,
  String ruleId,
  String source
) {
  public enum Severity { ERROR, WARNING, INFO }

  /** Factory for problems where end position is unknown (same as start). */
  public static Problem of(int line, int col, Severity sev, String msg, String ruleId, String source) {
    return new Problem(line, col, line, col, sev, msg, ruleId, source);
  }
}
