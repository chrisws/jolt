package com.jolt.lint;

import com.jolt.model.Problem;
import com.jolt.parse.CompileDb;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

/** Dispatches lint requests to the appropriate language-specific linter. */
public class LintService {

  private static final Logger log = LoggerFactory.getLogger(LintService.class);

  private final JavaLinter javaLinter = new JavaLinter();
  private final CppLinter cppLinter;

  public LintService(CompileDb compileDb) {
    this.cppLinter = new CppLinter(compileDb);
  }

  /** Lint a file and return all findings. Returns empty list for unsupported languages. */
  public List<Problem> lint(Path filePath) {
    String name = filePath.getFileName().toString().toLowerCase();

    List<Problem> problems;
    if (name.endsWith(".java")) {
      problems = javaLinter.lint(filePath);
    } else if (name.endsWith(".cpp") || name.endsWith(".cc")
        || name.endsWith(".cxx") || name.endsWith(".c")
        || name.endsWith(".h") || name.endsWith(".hpp") || name.endsWith(".hxx")) {
      problems = cppLinter.lint(filePath);
    } else {
      log.debug("No linter for file type: {}", name);
      return List.of();
    }

    log.debug("Lint {} → {} problem(s)", filePath, problems.size());
    return problems;
  }
}
