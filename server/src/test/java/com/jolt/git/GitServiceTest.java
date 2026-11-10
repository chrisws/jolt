package com.jolt.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GitServiceTest {

  @TempDir
  Path tempDir;

  private GitService service;
  private Git git;

  @BeforeEach
  void setUp() throws Exception {
    git = Git.init().setDirectory(tempDir.toFile()).call();
    service = new GitService(tempDir);

    // Create initial commit with a file
    Path file1 = tempDir.resolve("hello.txt");
    Files.writeString(file1, "line one\nline two\n");
    git.add().addFilepattern(".").call();
    git.commit().setMessage("initial commit").setAuthor("Alice", "alice@test.com").call();

    // Second commit modifying the file and adding a new one
    Files.writeString(file1, "line one\nline two modified\nline three\n");
    Path file2 = tempDir.resolve("world.txt");
    Files.writeString(file2, "world content\n");
    git.add().addFilepattern(".").call();
    git.commit().setMessage("second commit").setAuthor("Bob", "bob@test.com").call();

    // Save main branch name before switching
    String mainBranch = git.getRepository().getBranch();

    // Third commit on a new branch
    git.branchCreate().setName("feature").call();
    git.checkout().setName("feature").call();
    Path file3 = tempDir.resolve("feature.txt");
    Files.writeString(file3, "feature stuff\n");
    git.add().addFilepattern(".").call();
    git.commit().setMessage("feature work").setAuthor("Alice", "alice@test.com").call();

    // Switch back to master/main
    git.checkout().setName(mainBranch).call();
  }

  private String getMainBranchName() throws Exception {
    return git.getRepository().getBranch();
  }

  private RevCommit findCommitByMessage(String messagePart) throws Exception {
    try (RevWalk revWalk = new RevWalk(git.getRepository())) {
      revWalk.setRevFilter(RevFilter.ALL);
      RevCommit head = revWalk.parseCommit(git.getRepository().resolve("HEAD"));
      revWalk.markStart(head);
      for (RevCommit c : revWalk) {
        if (c.getFullMessage().contains(messagePart)) return c;
      }
      return null;
    }
  }

  @Test
  void isGitRepo_returnsTrue() {
    assertTrue(service.isGitRepo());
  }

  @Test
  void isGitRepo_returnsFalseForNonRepo() throws IOException {
    Path nonRepo = tempDir.resolve("subdir");
    Files.createDirectory(nonRepo);
    GitService nonRepoService = new GitService(nonRepo);
    assertFalse(nonRepoService.isGitRepo());
  }

  @Test
  void log_returnsAllCommits() {
    List<Map<String, Object>> commits = service.log(10, null, null);
    assertEquals(2, commits.size());

    // Most recent first (on main branch: initial + second)
    assertEquals("second commit", commits.get(0).get("message"));
    assertEquals("Bob", commits.get(0).get("author"));
  }

  @Test
  void log_respectsCountLimit() {
    List<Map<String, Object>> commits = service.log(1, null, null);
    assertEquals(1, commits.size());
  }

  @Test
  void log_filtersByAuthor() {
    List<Map<String, Object>> commits = service.log(10, "Alice", null);
    // On main branch: only "initial commit" is by Alice
    assertEquals(1, commits.size());
    assertEquals("Alice", commits.get(0).get("author"));
  }

  @Test
  void log_filtersByMessagePrefix() {
    List<Map<String, Object>> commits = service.log(10, null, "second");
    assertEquals(1, commits.size());
    assertEquals("second commit", commits.get(0).get("message"));
  }

  @Test
  void log_returnsEmptyForNoMatch() {
    List<Map<String, Object>> commits = service.log(10, "NonExistent", null);
    assertTrue(commits.isEmpty());
  }

  @Test
  void diff_betweenTwoCommits() throws Exception {
    RevCommit first = findCommitByMessage("initial commit");
    RevCommit second = findCommitByMessage("second commit");

    assertNotNull(first);
    assertNotNull(second);

    List<Map<String, Object>> diffs = service.diff(first.getName(), second.getName());
    assertFalse(diffs.isEmpty());

    // Should show hello.txt modified and world.txt added
    boolean hasModified = diffs.stream().anyMatch(d -> "hello.txt".equals(d.get("newPath")));
    boolean hasAdded = diffs.stream().anyMatch(d -> "world.txt".equals(d.get("newPath")));
    assertTrue(hasModified);
    assertTrue(hasAdded);
  }

  @Test
  void diff_singleCommit_diffsAgainstParent() throws Exception {
    RevCommit second = findCommitByMessage("second commit");
    assertNotNull(second);

    // Pass null for from → diff against parent
    List<Map<String, Object>> diffs = service.diff(null, second.getName());
    assertFalse(diffs.isEmpty());
  }

  @Test
  void fileDiff_returnsUnifiedDiff() throws Exception {
    RevCommit first = findCommitByMessage("initial commit");
    RevCommit second = findCommitByMessage("second commit");

    String diff = service.fileDiff(first.getName(), second.getName(), "hello.txt");
    assertNotNull(diff);
    assertTrue(diff.contains("--- a/hello.txt"));
    assertTrue(diff.contains("+++ b/hello.txt"));
    assertTrue(diff.contains("-line two"));
    assertTrue(diff.contains("+line two modified"));
  }

  @Test
  void fileDiff_newFile_showsAllAdded() throws Exception {
    RevCommit first = findCommitByMessage("initial commit");
    RevCommit second = findCommitByMessage("second commit");

    String diff = service.fileDiff(first.getName(), second.getName(), "world.txt");
    assertNotNull(diff);
    assertTrue(diff.contains("+world content"));
  }

  @Test
  void branches_includesCurrentAndFeature() {
    List<Map<String, Object>> branches = service.branches();
    assertFalse(branches.isEmpty());

    boolean hasFeature = branches.stream().anyMatch(b -> "feature".equals(b.get("name")));
    assertTrue(hasFeature);

    // Current branch should be marked
    boolean hasCurrent = branches.stream().anyMatch(b -> Boolean.TRUE.equals(b.get("isCurrent")));
    assertTrue(hasCurrent);
  }

  @Test
  void currentBranch_returnsNonEmpty() {
    String branch = service.currentBranch();
    assertNotNull(branch);
    assertFalse(branch.isBlank());
  }

  @Test
  void commitDetail_returnsFullInfo() throws Exception {
    RevCommit second = findCommitByMessage("second commit");

    Map<String, Object> detail = service.commitDetail(second.getName());
    assertEquals(second.getName(), detail.get("hash"));
    assertEquals("Bob", detail.get("author"));
    assertEquals("bob@test.com", detail.get("email"));
    assertEquals("second commit", detail.get("message"));

    @SuppressWarnings("unchecked")
    List<String> files = (List<String>) detail.get("filesChanged");
    assertNotNull(files);
    assertTrue(files.contains("hello.txt"));
    assertTrue(files.contains("world.txt"));
  }

  @Test
  void commitDetail_initialCommit_listsAllFiles() throws Exception {
    RevCommit first = findCommitByMessage("initial commit");

    Map<String, Object> detail = service.commitDetail(first.getName());
    @SuppressWarnings("unchecked")
    List<String> files = (List<String>) detail.get("filesChanged");
    assertNotNull(files);
    assertTrue(files.contains("hello.txt"));
  }

  @Test
  void log_onNonRepo_returnsEmpty() throws IOException {
    Path nonRepo = tempDir.resolve("empty");
    Files.createDirectory(nonRepo);
    GitService svc = new GitService(nonRepo);
    assertTrue(svc.log(5, null, null).isEmpty());
  }

  @Test
  void diff_onNonRepo_returnsEmpty() throws IOException {
    Path nonRepo = tempDir.resolve("empty2");
    Files.createDirectory(nonRepo);
    GitService svc = new GitService(nonRepo);
    assertTrue(svc.diff("abc", "def").isEmpty());
  }

}
