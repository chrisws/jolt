package com.jolt.lint;

import com.jolt.model.Problem;
import com.puppycrawl.tools.checkstyle.Checker;
import com.puppycrawl.tools.checkstyle.DefaultConfiguration;
import com.puppycrawl.tools.checkstyle.api.AuditEvent;
import com.puppycrawl.tools.checkstyle.api.AuditListener;
import com.puppycrawl.tools.checkstyle.api.CheckstyleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Lints Java files using Checkstyle with a sensible default ruleset. */
public class JavaLinter {

  private static final Logger log = LoggerFactory.getLogger(JavaLinter.class);

  /** Run Checkstyle on the given file and return all findings. */
  public List<Problem> lint(Path filePath) {
    List<Problem> problems = new ArrayList<>();
    try {
      Checker checker = createChecker(problems);
      try {
        checker.process(List.of(filePath.toFile()));
      } finally {
        checker.destroy();
      }
    } catch (CheckstyleException e) {
      log.debug("Checkstyle error on {}: {}", filePath, e.getMessage());
    }
    return problems;
  }

  private Checker createChecker(List<Problem> sink) throws CheckstyleException {
    DefaultConfiguration treeWalkCfg = new DefaultConfiguration("TreeWalker");

    treeWalkCfg.addChild(new DefaultConfiguration("UnusedImports"));

    treeWalkCfg.addChild(new DefaultConfiguration("RedundantImport"));

    treeWalkCfg.addChild(new DefaultConfiguration("MissingOverride"));

    treeWalkCfg.addChild(new DefaultConfiguration("EmptyBlock"));

    DefaultConfiguration lineLen = new DefaultConfiguration("LineLength");
    lineLen.addProperty("max", "120");

    DefaultConfiguration rootCfg = new DefaultConfiguration("Checker");
    rootCfg.addProperty("charset", "UTF-8");
    rootCfg.addChild(treeWalkCfg);
    rootCfg.addChild(lineLen);

    Checker checker = new Checker();
    checker.setModuleClassLoader(Checker.class.getClassLoader());
    checker.configure(rootCfg);

    checker.addListener(new AuditListener() {
      @Override public void auditStarted(AuditEvent event) {}
      @Override public void auditFinished(AuditEvent event) {}
      @Override public void fileStarted(AuditEvent event) {}
      @Override public void fileFinished(AuditEvent event) {}

      @Override public void addError(AuditEvent event) {
        sink.add(Problem.of(
          event.getLine(), event.getColumn(), Problem.Severity.WARNING,
          event.getMessage(), "checkstyle", "checkstyle"
        ));
      }

      @Override public void addException(AuditEvent event, Throwable t) {
        log.debug("Checkstyle exception: {}", t.getMessage());
      }
    });

    return checker;
  }
}