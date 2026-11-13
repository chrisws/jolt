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
import java.util.Set;

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

  // ─── stats() tests ───────────────────────────────────────────────

  @Test
  void stats_returnsCorrectTotals() {
    Map<String, Object> s = service.stats();
    assertEquals(2, s.get("totalCommits"));
    assertEquals(2, s.get("totalContributors"));
  }

  @Test
  void stats_hasAllRequiredKeys() {
    Map<String, Object> s = service.stats();
    assertNotNull(s.get("totalCommits"));
    assertNotNull(s.get("totalContributors"));
    assertNotNull(s.get("firstCommitDate"));
    assertNotNull(s.get("lastCommitDate"));
    assertNotNull(s.get("currentBranch"));
    assertNotNull(s.get("topCommitters"));
    assertNotNull(s.get("longestStreak"));
    assertNotNull(s.get("currentStreak"));
    assertNotNull(s.get("busiestDay"));
    assertNotNull(s.get("busiestDayCount"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void stats_topCommitters_containsBothAuthors() {
    Map<String, Object> s = service.stats();
    List<Map<String, Object>> top = (List<Map<String, Object>>) s.get("topCommitters");
    assertNotNull(top);
    assertEquals(2, top.size());

    Set<String> names = top.stream().map(m -> (String) m.get("name")).collect(java.util.stream.Collectors.toSet());
    assertTrue(names.contains("Alice"));
    assertTrue(names.contains("Bob"));

    // Each has exactly 1 commit on main
    for (Map<String, Object> c : top) {
      assertEquals(1, c.get("commits"));
    }
  }

  @Test
  void stats_busiestDay_hasAtLeastOneCommit() {
    Map<String, Object> s = service.stats();
    int busiestCount = (int) s.get("busiestDayCount");
    assertTrue(busiestCount >= 1);

    String busiestDay = (String) s.get("busiestDay");
    assertFalse(busiestDay.isBlank());
  }

  @Test
  void stats_onNonRepo_returnsError() throws IOException {
    Path nonRepo = tempDir.resolve("no-repo-stats");
    Files.createDirectory(nonRepo);
    GitService svc = new GitService(nonRepo);
    Map<String, Object> s = svc.stats();
    assertTrue(s.containsKey("error"));
  }

  // ─── commitsPaginated() tests ────────────────────────────────────

  @Test
  @SuppressWarnings("unchecked")
  void commitsPaginated_returnsAllOnFirstPage() {
    Map<String, Object> result = service.commitsPaginated(null, null, null, null, 0, 10);
    List<Map<String, Object>> commits = (List<Map<String, Object>>) result.get("commits");
    assertEquals(2, commits.size());
    assertEquals(2, result.get("total"));
    assertEquals(1, result.get("totalPages"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void commitsPaginated_respectsPageSize() {
    Map<String, Object> result = service.commitsPaginated(null, null, null, null, 0, 1);
    List<Map<String, Object>> commits = (List<Map<String, Object>>) result.get("commits");
    assertEquals(1, commits.size());
    assertEquals(2, result.get("total"));
    assertEquals(2, result.get("totalPages"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void commitsPaginated_secondPageReturnsOlderCommit() {
    Map<String, Object> page0 = service.commitsPaginated(null, null, null, null, 0, 1);
    Map<String, Object> page1 = service.commitsPaginated(null, null, null, null, 1, 1);

    List<Map<String, Object>> c0 = (List<Map<String, Object>>) page0.get("commits");
    List<Map<String, Object>> c1 = (List<Map<String, Object>>) page1.get("commits");

    // Page 0 = newest ("second commit"), Page 1 = oldest ("initial commit")
    assertEquals("second commit", c0.get(0).get("message"));
    assertEquals("initial commit", c1.get(0).get("message"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void commitsPaginated_filtersByAuthor() {
    Map<String, Object> result = service.commitsPaginated(null, "Alice", null, null, 0, 10);
    List<Map<String, Object>> commits = (List<Map<String, Object>>) result.get("commits");
    assertEquals(1, commits.size());
    assertEquals("Alice", commits.get(0).get("author"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void commitsPaginated_commitHasRequiredFields() {
    Map<String, Object> result = service.commitsPaginated(null, null, null, null, 0, 10);
    List<Map<String, Object>> commits = (List<Map<String, Object>>) result.get("commits");
    assertFalse(commits.isEmpty());

    Map<String, Object> c = commits.get(0);
    assertNotNull(c.get("hash"));
    assertNotNull(c.get("abbrevHash"));
    assertNotNull(c.get("author"));
    assertNotNull(c.get("email"));
    assertNotNull(c.get("date"));
    assertNotNull(c.get("message"));
    assertNotNull(c.get("filesChanged"));

    // abbrevHash should be 8 chars
    assertEquals(8, ((String) c.get("abbrevHash")).length());
  }

  @Test
  @SuppressWarnings("unchecked")
  void commitsPaginated_filesChangedCounted() {
    Map<String, Object> result = service.commitsPaginated(null, null, null, null, 0, 10);
    List<Map<String, Object>> commits = (List<Map<String, Object>>) result.get("commits");

    // "second commit" changed hello.txt + added world.txt = 2 files
    Map<String, Object> second = commits.stream()
        .filter(c -> "second commit".equals(c.get("message"))).findFirst().orElse(null);
    assertNotNull(second);
    assertEquals(2, second.get("filesChanged"));

    // "initial commit" added hello.txt = 1 file
    Map<String, Object> first = commits.stream()
        .filter(c -> "initial commit".equals(c.get("message"))).findFirst().orElse(null);
    assertNotNull(first);
    assertEquals(1, first.get("filesChanged"));
  }

  @Test
  void commitsPaginated_onNonRepo_returnsError() throws IOException {
    Path nonRepo = tempDir.resolve("no-repo-pag");
    Files.createDirectory(nonRepo);
    GitService svc = new GitService(nonRepo);
    Map<String, Object> result = svc.commitsPaginated(null, null, null, null, 0, 10);
    assertTrue(result.containsKey("error"));
  }

  // ─── activity() tests ────────────────────────────────────────────

  @Test
  void activity_returns365CellsForNonLeapYear() {
    List<Map<String, Object>> cells = service.activity(2025);
    assertEquals(365, cells.size());
  }

  @Test
  void activity_returns366CellsForLeapYear() {
    List<Map<String, Object>> cells = service.activity(2024);
    assertEquals(366, cells.size());
  }

  @Test
  void activity_countsCommitsOnToday() {
    int currentYear = java.time.LocalDate.now().getYear();
    List<Map<String, Object>> cells = service.activity(currentYear);

    String todayStr = java.time.LocalDate.now().toString();
    Map<String, Object> todayCell = cells.stream()
        .filter(c -> todayStr.equals(c.get("date"))).findFirst().orElse(null);

    assertNotNull(todayCell);
    // Both commits in setUp were made today
    assertEquals(2, todayCell.get("count"));
  }

  @Test
  void activity_mostDaysHaveZeroCount() {
    int currentYear = java.time.LocalDate.now().getYear();
    List<Map<String, Object>> cells = service.activity(currentYear);

    long zeroDays = cells.stream().filter(c -> Integer.valueOf(0).equals(c.get("count"))).count();
    // Only today has commits, so most days should be zero
    assertTrue(zeroDays > 300);
  }

  @Test
  void activity_datesAreSequential() {
    List<Map<String, Object>> cells = service.activity(2025);
    assertEquals("2025-01-01", cells.get(0).get("date"));
    assertEquals("2025-12-31", cells.get(cells.size() - 1).get("date"));
  }

  @Test
  void activity_onNonRepo_returnsEmpty() throws IOException {
    Path nonRepo = tempDir.resolve("no-repo-act");
    Files.createDirectory(nonRepo);
    GitService svc = new GitService(nonRepo);
    assertTrue(svc.activity(2025).isEmpty());
  }

  // ─── Write-method tests: commit, push, pull, remotes, stage, unstage ───


  @Test
  void stageAll_returnsTrue() throws Exception {
    // Close setUp git to release the repo lock (write ops need exclusive access)
    git.close();

    // stageAll on a valid repo should succeed (no changes is fine)
    boolean result = service.stageAll();
    assertTrue(result);
  }

  @Test
  void unstageFile_returnsTrue() throws Exception {
    // Modify an existing tracked file and stage it
    Path file = tempDir.resolve("world.txt");
    Files.writeString(file, "modified for unstage test\n");

    // Stage it via JGit, then close to release the lock
    git.add().addFilepattern("world.txt").call();
    git.close();

    // Unstage via service — should return true (git reset HEAD -- <path>)
    boolean result = service.unstageFile("world.txt");
    assertTrue(result);

    // Verify the file is now shown as modified (unstaged) in status
    try (Git g = Git.open(tempDir.toFile())) {
      var status = g.status().call();
      assertFalse(status.getUncommittedChanges().isEmpty());
    }
  }

  @Test
  void push_returnsFalseForNoRemote() throws Exception {
    // No remote is configured in the setUp repo — push should fail gracefully
    git.close();
    boolean result = service.push("origin", "main");
    assertFalse(result);
  }

  @Test
  void pull_returnsFalseForNoRemote() throws Exception {
    // No remote is configured in the setUp repo — pull should fail gracefully
    git.close();
    boolean result = service.pull("origin", "main");
    assertFalse(result);
  }

  @Test
  void commit_returnsNonNullHash() throws Exception {
    // Modify an existing tracked file so there's something to commit
    Path file = tempDir.resolve("hello.txt");
    Files.writeString(file, "modified for commit test\n");

    // Close setUp git to release the repo lock, then stage via service
    git.close();

    // Stage via service
    assertTrue(service.stageAll());

    // Commit via service — should return a non-null hash
    String hash = service.commit("test commit message", "Test User", "test@test.com");
    assertNotNull(hash);
    assertFalse(hash.isBlank());

    // Verify the commit exists in the log
    List<Map<String, Object>> commits = service.log(1, null, null);
    assertEquals("test commit message", commits.get(0).get("message"));
  }

  @Test
  void remotes_returnsEmptyForFreshRepo() throws Exception {
    // The repo created in setUp has no remotes configured
    git.close();
    List<String> remotes = service.remotes();
    assertNotNull(remotes);
    assertTrue(remotes.isEmpty());
  }

}
