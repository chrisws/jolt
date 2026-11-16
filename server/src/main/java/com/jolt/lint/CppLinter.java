package com.jolt.lint;

import com.jolt.model.Problem;
import com.jolt.parse.CompileDb;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lints C/C++ files by shelling out to {@code clang-tidy}.
 * Expects clang-tidy to be on the system PATH.
 */
public class CppLinter {

  private static final Logger log = LoggerFactory.getLogger(CppLinter.class);
  private static final int TIMEOUT_SECONDS = 30;

  // Matches: file:line:col: warning|error|note: message [rule-name]
  private static final Pattern CLANG_TIDY_LINE = Pattern.compile(
    "^.+?(?::\\d+)?:(\\d+):(\\d+):\\s+(warning|error|note):\\s+(.+?)(?:\\s+\\[([\\w:-]+)\\])?$"
  );

  private final CompileDb compileDb;

  public CppLinter(CompileDb compileDb) {
    this.compileDb = compileDb;
  }

  /** Run clang-tidy on the given file and return all findings. */
  public List<Problem> lint(Path filePath) {
    List<Problem> problems = new ArrayList<>();

    List<String> cmd = buildCommand(filePath);
    try {
      ProcessBuilder pb = new ProcessBuilder(cmd);
      pb.redirectErrorStream(true);
      Process proc = pb.start();

      try (BufferedReader reader = new BufferedReader(
          new InputStreamReader(proc.getInputStream()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          Matcher m = CLANG_TIDY_LINE.matcher(line);
          if (m.matches()) {
            int lineNum = Integer.parseInt(m.group(1));
            int col = Integer.parseInt(m.group(2));
            String level = m.group(3);
            String message = m.group(4);
            String rule = m.group(5) != null ? m.group(5) : "clang-tidy";

            Problem.Severity sev = switch (level) {
              case "error" -> Problem.Severity.ERROR;
              case "warning" -> Problem.Severity.WARNING;
              default -> Problem.Severity.INFO;
            };

            problems.add(Problem.of(lineNum, col, sev, message, rule, "clang-tidy"));
          }
        }
      }

      proc.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);

    } catch (IOException e) {
      log.debug("clang-tidy not available or IO error: {}", e.getMessage());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.debug("clang-tidy timed out for {}", filePath);
    }

    return problems;
  }

  private List<String> buildCommand(Path filePath) {
    List<String> flags = compileDb.getFlags(filePath.toString());

    List<String> cmd = new ArrayList<>();
    cmd.add("clang-tidy");
    cmd.add("-p");
    cmd.add(findCompileCommandsDir(filePath));

    // Pass extra flags from compile database if available
    for (String flag : flags) {
      cmd.add(flag);
    }

    cmd.add(filePath.toString());
    return cmd;
  }

  /** Find the directory containing compile_commands.json for the -p flag. */
  private String findCompileCommandsDir(Path filePath) {
    Path dir = filePath.getParent();
    while (dir != null) {
      if (dir.resolve("compile_commands.json").toFile().exists()) {
        return dir.toString();
      }
      // Also check build/ subdirectory (common CMake layout)
      if (dir.resolve("build/compile_commands.json").toFile().exists()) {
        return dir.resolve("build").toString();
      }
      dir = dir.getParent();
    }
    // Fallback: current directory (clang-tidy will use defaults)
    return ".";
  }
}
