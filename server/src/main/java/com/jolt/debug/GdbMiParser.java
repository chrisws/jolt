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

package com.jolt.debug;

import java.util.*;

/** Static utilities for parsing GDB MI protocol response lines. */
public final class GdbMiParser {

  private GdbMiParser() {}

  /** Extract a quoted field value: {@code field="value"} → {@code value}. */
  public static String extractField(String line, String field) {
    String key = field + "=\"";
    int start = line.indexOf(key);
    if (start < 0) return null;
    start += key.length();
    int end = line.indexOf('"', start);
    return end > start ? line.substring(start, end) : null;
  }

  /** Extract an integer field (quoted or bare). */
  public static int parseIntField(String line, String field) {
    String val = extractField(line, field);
    if (val == null) {
      String key = field + "=";
      int start = line.indexOf(key);
      if (start < 0) return 0;
      start += key.length();
      int end = start;
      while (end < line.length() && Character.isDigit(line.charAt(end))) end++;
      try { return Integer.parseInt(line.substring(start, end)); }
      catch (Exception e) { return 0; }
    }
    try { return Integer.parseInt(val); } catch (Exception e) { return 0; }
  }

  /** Parsed fields from a {@code *stopped} event. */
  public record StoppedInfo(String reason, String file, int line) {}

  /** Parse a {@code *stopped,...} MI event line into its key fields. */
  public static StoppedInfo parseStoppedEvent(String line) {
    String reason = extractField(line, "reason");
    String file = extractField(line, "file");
    int ln = parseIntField(line, "line");
    return new StoppedInfo(reason, file, ln);
  }

  /** Parse a {@code ^done,variables=[...]} response into VariableInfo list. */
  public static List<VariableInfo> parseVariableList(String line, int frameIndex) {
    List<VariableInfo> vars = new ArrayList<>();

    int start = line.indexOf("variables=[");
    if (start < 0) return vars;
    start += "variables=[".length();

    int end = line.indexOf("]", start);
    if (end < 0) end = line.length();

    String content = line.substring(start, end);

    int pos = 0;
    while (pos < content.length()) {
      int braceStart = content.indexOf('{', pos);
      if (braceStart < 0) break;
      int braceEnd = content.indexOf('}', braceStart);
      if (braceEnd < 0) break;

      String entry = content.substring(braceStart + 1, braceEnd);
      String name = extractField(entry, "name");
      String type = extractField(entry, "type");
      String value = extractField(entry, "value");

      if (name != null) {
        vars.add(new VariableInfo(name, type != null ? type : "?",
            value != null ? value : "?", frameIndex));
      }

      pos = braceEnd + 1;
    }

    return vars;
  }
}
