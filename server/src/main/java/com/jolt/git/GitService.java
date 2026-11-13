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
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.*;

/**
 * Full JGit service layer: log, diff, branches, commit detail.
 * Complements BlameService (which handles per-line blame).
 */
public class GitService {

  private static final Logger log = LoggerFactory.getLogger(GitService.class);

  /** The well-known empty tree object in git. */
  private static final String EMPTY_TREE = "4b825dc642cb6eb9a060e54bf8d69288fbee4904";

  private final Path homeDir;

  public GitService(Path homeDir) {
    this.homeDir = homeDir.toAbsolutePath().normalize();
  }

  /** Get commit log, optionally filtered by author name or message prefix. */
  public List<Map<String, Object>> log(int count, String authorFilter, String messagePrefix) {
    try (Git git = Git.open(homeDir.toFile())) {
      Repository repo = git.getRepository();

      List<RevCommit> allCommits = new ArrayList<>();
      try (RevWalk revWalk = new RevWalk(repo)) {
        revWalk.setRevFilter(RevFilter.ALL);
        RevCommit head = revWalk.parseCommit(repo.resolve("HEAD"));
        revWalk.markStart(head);
        for (RevCommit c : revWalk) {
          allCommits.add(c);
        }
      }

      // Apply filters manually (JGit LogCommand lacks regex author / message prefix)
      List<RevCommit> filtered = allCommits.stream()
          .filter(c -> {
            if (authorFilter != null && !authorFilter.isBlank()) {
              PersonIdent ident = c.getAuthorIdent();
              if (ident == null || !ident.getName().contains(authorFilter)) return false;
            }
            if (messagePrefix != null && !messagePrefix.isBlank()) {
              if (!c.getFullMessage().trim().startsWith(messagePrefix)) return false;
            }
            return true;
          })
          .limit(count)
          .toList();

      List<Map<String, Object>> result = new ArrayList<>();
      for (RevCommit c : filtered) {
        PersonIdent ident = c.getAuthorIdent();
        result.add(Map.of(
            "hash", c.getName(),
            "abbrevHash", c.getName().substring(0, Math.min(8, c.getName().length())),
            "author", ident != null ? ident.getName() : "unknown",
            "email", ident != null ? ident.getEmailAddress() : "",
            "date", ident != null ? ident.getWhen().toInstant().toString() : "",
            "message", c.getFullMessage().trim()
        ));
      }
      return result;

    } catch (Exception e) {
      log.error("Git log failed: {}", e.getMessage());
      return List.of();
    }
  }

  /** Get diff between two commits (or a commit and its parent). */
  public List<Map<String, Object>> diff(String fromHash, String toHash) {
    try (Git git = Git.open(homeDir.toFile())) {
      Repository repo = git.getRepository();

      RevCommit from;
      RevCommit to;

      try (RevWalk revWalk = new RevWalk(repo)) {
        if (fromHash == null || fromHash.isBlank()) {
          to = revWalk.parseCommit(repo.resolve(toHash));
          from = to.getParentCount() > 0 ? revWalk.parseCommit(to.getParents()[0]) : null;
        } else {
          from = revWalk.parseCommit(repo.resolve(fromHash));
          to = revWalk.parseCommit(repo.resolve(toHash));
        }
      }

      if (from == null) {
        return diffAgainstEmpty(git, to);
      }

      DiffFormatter formatter = new DiffFormatter(new ByteArrayOutputStream());
      formatter.setRepository(repo);

      List<DiffEntry> diffs = formatter.scan(from.getTree(), to.getTree());

      List<Map<String, Object>> result = new ArrayList<>();
      for (DiffEntry de : diffs) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("status", de.getChangeType().name());
        entry.put("oldPath", de.getOldPath());
        entry.put("newPath", de.getNewPath());
        entry.put("linesAdded", 0);
        entry.put("linesRemoved", 0);
        result.add(entry);
      }

      return result;

    } catch (Exception e) {
      log.error("Git diff failed: {}", e.getMessage());
      return List.of();
    }
  }

  /** Get file-level diff content (unified diff format) for a specific file. */
  public String fileDiff(String fromHash, String toHash, String filePath) {
    try (Git git = Git.open(homeDir.toFile())) {
      Repository repo = git.getRepository();

      RevCommit from;
      RevCommit to;

      try (RevWalk revWalk = new RevWalk(repo)) {
        if (fromHash == null || fromHash.isBlank()) {
          to = revWalk.parseCommit(repo.resolve(toHash));
          from = to.getParentCount() > 0 ? revWalk.parseCommit(to.getParents()[0]) : null;
        } else {
          from = revWalk.parseCommit(repo.resolve(fromHash));
          to = revWalk.parseCommit(repo.resolve(toHash));
        }
      }

      if (from == null) return "";

      String oldContent = readBlobAt(git, from, filePath);
      String newContent = readBlobAt(git, to, filePath);

      if (oldContent == null && newContent == null) return "";

      return simpleUnifiedDiff(oldContent, newContent, filePath);

    } catch (Exception e) {
      log.error("File diff failed: {}", e.getMessage());
      return "";
    }
  }

  /** List all branches. */
  public List<Map<String, Object>> branches() {
    try (Git git = Git.open(homeDir.toFile())) {
      Repository repo = git.getRepository();
      var branches = git.branchList().call();

      List<Map<String, Object>> result = new ArrayList<>();
      for (var ref : branches) {
        result.add(Map.of(
            "name", ref.getName().replace("refs/heads/", ""),
            "isCurrent", ref.getName().equals(repo.getFullBranch())
        ));
      }
      return result;

    } catch (Exception e) {
      log.error("Git branch list failed: {}", e.getMessage());
      return List.of();
    }
  }

  /** Get current branch name. */
  public String currentBranch() {
    try (Git git = Git.open(homeDir.toFile())) {
      return git.getRepository().getBranch();
    } catch (Exception e) {
      return "unknown";
    }
  }

  /** Checkout a branch by name. Returns the new current branch name, or throws. */
  public String checkoutBranch(String name) throws Exception {
    try (Git git = Git.open(homeDir.toFile())) {
      git.checkout().setName(name).call();
      return git.getRepository().getBranch();
    }
  }

  /** Get commit detail (message, author, files changed). */
  public Map<String, Object> commitDetail(String hash) {
    try (Git git = Git.open(homeDir.toFile())) {
      Repository repo = git.getRepository();

      RevCommit commit;
      try (RevWalk revWalk = new RevWalk(repo)) {
        commit = revWalk.parseCommit(repo.resolve(hash));
      }

      PersonIdent ident = commit.getAuthorIdent();

      Map<String, Object> detail = new LinkedHashMap<>();
      detail.put("hash", commit.getName());
      detail.put("author", ident != null ? ident.getName() : "unknown");
      detail.put("email", ident != null ? ident.getEmailAddress() : "");
      detail.put("date", ident != null ? ident.getWhen().toInstant().toString() : "");
      detail.put("message", commit.getFullMessage().trim());

      List<String> filesChanged = new ArrayList<>();
      if (commit.getParentCount() > 0) {
        RevCommit parent;
        try (RevWalk revWalk = new RevWalk(repo)) {
          parent = revWalk.parseCommit(commit.getParents()[0]);
        }

        DiffFormatter formatter = new DiffFormatter(new ByteArrayOutputStream());
        formatter.setRepository(repo);

        List<DiffEntry> diffs = formatter.scan(parent.getTree(), commit.getTree());
        for (DiffEntry de : diffs) {
          filesChanged.add(de.getNewPath());
        }
      } else {
        // Initial commit — diff against empty tree to get all files
        filesChanged = listAllFilesInCommit(repo, commit);
      }

      detail.put("filesChanged", filesChanged);
      return detail;

    } catch (Exception e) {
      log.error("Commit detail failed for {}: {}", hash, e.getMessage());
      return Map.of("error", e.getMessage());
    }
  }

  /** Check if the home directory is a git repository. */
  public boolean isGitRepo() {
    try (Git git = Git.open(homeDir.toFile())) {
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  /** Project-level statistics: total commits, contributors, dates, top committers. */
  public Map<String, Object> stats() {
    try (Git git = Git.open(homeDir.toFile())) {
      Repository repo = git.getRepository();

      List<RevCommit> allCommits = new ArrayList<>();
      try (RevWalk revWalk = new RevWalk(repo)) {
        revWalk.setRevFilter(RevFilter.ALL);
        RevCommit head = revWalk.parseCommit(repo.resolve("HEAD"));
        revWalk.markStart(head);
        for (RevCommit c : revWalk) { allCommits.add(c); }
      }

      if (allCommits.isEmpty()) { return Map.of("totalCommits", 0, "contributors", List.of()); }

      // First and last commit dates
      RevCommit first = allCommits.get(allCommits.size() - 1); // oldest (walk is newest-first)
      RevCommit last = allCommits.get(0); // newest

      // Count commits per author (name+email as key)
      Map<String, Integer> authorCounts = new LinkedHashMap<>();
      Map<String, String> authorEmails = new LinkedHashMap<>();
      for (RevCommit c : allCommits) {
        PersonIdent ident = c.getAuthorIdent();
        if (ident == null) continue;
        String key = ident.getName() + " <" + ident.getEmailAddress() + ">";
        authorCounts.merge(key, 1, Integer::sum);
        authorEmails.putIfAbsent(key, ident.getEmailAddress());
      }

      // Top committers sorted by count desc
      List<Map<String, Object>> topCommitters = authorCounts.entrySet().stream()
          .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
          .limit(10)
          .map(e -> {
            String name = e.getKey().split(" <")[0];
            return Map.<String, Object>of(
                "name", name,
                "email", authorEmails.getOrDefault(e.getKey(), ""),
                "commits", e.getValue()
            );
          })
          .toList();

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("totalCommits", allCommits.size());
      result.put("totalContributors", authorCounts.size());
      result.put("firstCommitDate", first.getAuthorIdent() != null ? first.getAuthorIdent().getWhen().toInstant().toString() : "");
      result.put("lastCommitDate", last.getAuthorIdent() != null ? last.getAuthorIdent().getWhen().toInstant().toString() : "");
      result.put("currentBranch", repo.getBranch());
      result.put("topCommitters", topCommitters);

      // Streaks and busiest period (for Stage 6 flair)
      Map<String, Integer> dayCounts = new LinkedHashMap<>();
      for (RevCommit c : allCommits) {
        PersonIdent ident = c.getAuthorIdent();
        if (ident == null) continue;
        String dayKey = ident.getWhen().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString();
        dayCounts.merge(dayKey, 1, Integer::sum);
      }

      int longestStreak = 0, currentStreak = 0;
      String busiestDay = ""; int busiestCount = 0;
      java.time.LocalDate today = java.time.LocalDate.now();

      // Compute longest streak by sorting dates
      List<java.time.LocalDate> sortedDays = dayCounts.keySet().stream()
          .map(java.time.LocalDate::parse).sorted().toList();

      int streak = 0;
      for (int i = 0; i < sortedDays.size(); i++) {
        if (i > 0 && sortedDays.get(i).minusDays(1).equals(sortedDays.get(i - 1))) { streak++; }
        else { streak = 1; }
        if (streak > longestStreak) longestStreak = streak;
      }

      // Current streak: count back from today
      java.time.LocalDate d = today;
      while (dayCounts.containsKey(d.toString())) { currentStreak++; d = d.minusDays(1); }

      // Busiest day
      for (var entry : dayCounts.entrySet()) {
        if (entry.getValue() > busiestCount) { busiestCount = entry.getValue(); busiestDay = entry.getKey(); }
      }

      result.put("longestStreak", longestStreak);
      result.put("currentStreak", currentStreak);
      result.put("busiestDay", busiestDay);
      result.put("busiestDayCount", busiestCount);

      return result;

    } catch (Exception e) {
      log.error("Git stats failed: {}", e.getMessage());
      return Map.of("error", e.getMessage());
    }
  }

  /** Paginated commit list with optional filters. */
  public Map<String, Object> commitsPaginated(String branch, String author, String from, String to, int page, int size) {
    try (Git git = Git.open(homeDir.toFile())) {
      Repository repo = git.getRepository();

      List<RevCommit> allCommits = new ArrayList<>();

      // If a specific branch is requested, walk from that ref; otherwise HEAD
      String refName = (branch != null && !branch.isBlank()) ? branch : "HEAD";
      try (RevWalk revWalk = new RevWalk(repo)) {
        revWalk.setRevFilter(RevFilter.ALL);
        RevCommit head = revWalk.parseCommit(repo.resolve(refName));
        revWalk.markStart(head);
        for (RevCommit c : revWalk) { allCommits.add(c); }
      }

      // Apply filters (final for lambda capture)
      final java.time.Instant fromInstant = parseInstantSafe(from);
      final java.time.Instant toInstant = parseInstantSafe(to);

      List<RevCommit> filtered = allCommits.stream()
          .filter(c -> {
            PersonIdent ident = c.getAuthorIdent();
            if (ident == null) return false;

            if (author != null && !author.isBlank() && !ident.getName().contains(author)) return false;

            java.time.Instant commitTime = ident.getWhen().toInstant();
            if (fromInstant != null && commitTime.isBefore(fromInstant)) return false;
            if (toInstant != null && commitTime.isAfter(toInstant)) return false;

            return true;
          })
          .toList();

      int total = filtered.size();
      int startIdx = Math.min(page * size, total);
      int endIdx = Math.min(startIdx + size, total);

      List<Map<String, Object>> pageItems = new ArrayList<>();
      for (int i = startIdx; i < endIdx; i++) {
        RevCommit c = filtered.get(i);
        PersonIdent ident = c.getAuthorIdent();

        // Count files changed for this commit
        int filesChanged = 0;
        if (c.getParentCount() > 0) {
          try (RevWalk rw2 = new RevWalk(repo)) {
            RevCommit parent = rw2.parseCommit(c.getParents()[0]);
            DiffFormatter fmt = new DiffFormatter(new ByteArrayOutputStream());
            fmt.setRepository(repo);
            filesChanged = fmt.scan(parent.getTree(), c.getTree()).size();
          } catch (Exception ignored) {}
        } else {
          try { filesChanged = listAllFilesInCommit(repo, c).size(); } catch (Exception ignored) {}
        }

        pageItems.add(Map.of(
            "hash", c.getName(),
            "abbrevHash", c.getName().substring(0, Math.min(8, c.getName().length())),
            "author", ident != null ? ident.getName() : "unknown",
            "email", ident != null ? ident.getEmailAddress() : "",
            "date", ident != null ? ident.getWhen().toInstant().toString() : "",
            "message", c.getFullMessage().trim().split("\n")[0], // first line only for list view
            "filesChanged", filesChanged
        ));
      }

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("commits", pageItems);
      result.put("total", total);
      result.put("page", page);
      result.put("size", size);
      result.put("totalPages", (int) Math.ceil((double) total / size));

      return result;

    } catch (Exception e) {
      log.error("Paginated commits failed: {}", e.getMessage());
      return Map.of("error", e.getMessage(), "commits", List.of());
    }
  }

  /** Yearly activity data for heatmap: 365/366 cells with commit counts. */
  public List<Map<String, Object>> activity(int year) {
    try (Git git = Git.open(homeDir.toFile())) {
      Repository repo = git.getRepository();

      List<RevCommit> allCommits = new ArrayList<>();
      try (RevWalk revWalk = new RevWalk(repo)) {
        revWalk.setRevFilter(RevFilter.ALL);
        RevCommit head = revWalk.parseCommit(repo.resolve("HEAD"));
        revWalk.markStart(head);
        for (RevCommit c : revWalk) { allCommits.add(c); }
      }

      // Count commits per day in the given year
      Map<String, Integer> dayCounts = new LinkedHashMap<>();
      for (RevCommit c : allCommits) {
        PersonIdent ident = c.getAuthorIdent();
        if (ident == null) continue;
        java.time.LocalDate date = ident.getWhen().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        if (date.getYear() == year) {
          dayCounts.merge(date.toString(), 1, Integer::sum);
        }
      }

      // Build 365/366 cells starting from Jan 1
      boolean leapYear = java.time.Year.isLeap(year);
      int daysInYear = leapYear ? 366 : 365;

      List<Map<String, Object>> cells = new ArrayList<>();
      java.time.LocalDate start = java.time.LocalDate.of(year, 1, 1);
      for (int i = 0; i < daysInYear; i++) {
        java.time.LocalDate d = start.plusDays(i);
        int count = dayCounts.getOrDefault(d.toString(), 0);
        cells.add(Map.of("date", d.toString(), "count", count));
      }

      return cells;

    } catch (Exception e) {
      log.error("Activity data failed: {}", e.getMessage());
      return List.of();
    }
  }

  /** Enhanced commit detail with per-file add/remove line counts. */
  public Map<String, Object> commitDetailEnhanced(String hash) {
    Map<String, Object> base = commitDetail(hash);
    if (base.containsKey("error")) return base;

    try (Git git = Git.open(homeDir.toFile())) {
      Repository repo = git.getRepository();

      RevCommit commit;
      try (RevWalk revWalk = new RevWalk(repo)) {
        commit = revWalk.parseCommit(repo.resolve(hash));
      }

      List<Map<String, Object>> fileStats = new ArrayList<>();

      if (commit.getParentCount() > 0) {
        RevCommit parent;
        try (RevWalk rw2 = new RevWalk(repo)) { parent = rw2.parseCommit(commit.getParents()[0]); }

        DiffFormatter formatter = new DiffFormatter(new ByteArrayOutputStream());
        formatter.setRepository(repo);

        List<DiffEntry> diffs = formatter.scan(parent.getTree(), commit.getTree());
        for (DiffEntry de : diffs) {
          int added = 0, removed = 0;

          // Count lines in the diff for this file
          String diffContent = fileDiff(parent.getName(), commit.getName(), de.getNewPath());
          for (String line : diffContent.split("\n")) {
            if (line.startsWith("+") && !line.startsWith("+++")) added++;
            else if (line.startsWith("-") && !line.startsWith("---")) removed++;
          }

          fileStats.add(Map.of(
              "path", de.getNewPath(),
              "status", de.getChangeType().name(),
              "linesAdded", added,
              "linesRemoved", removed,
              "isBinary", isBinaryFile(de.getNewPath())
          ));
        }
      } else {
        // Initial commit — all files are additions
        List<String> files = listAllFilesInCommit(repo, commit);
        for (String path : files) {
          int lines = 0;
          String content = readBlobAt(git, commit, path);
          if (content != null) lines = content.split("\n", -1).length;

          fileStats.add(Map.of(
              "path", path, "status", "A", "linesAdded", lines, "linesRemoved", 0,
              "isBinary", isBinaryFile(path)
          ));
        }
      }

      base.put("fileStats", fileStats);

      // Parent hashes for navigation
      List<String> parents = new ArrayList<>();
      for (ObjectId p : commit.getParents()) { parents.add(p.getName()); }
      base.put("parents", parents);

      return base;

    } catch (Exception e) {
      log.error("Enhanced commit detail failed: {}", e.getMessage());
      return base;
    }
  }

  /** Raw file content at a specific commit. Returns null if not found. */
  public String rawFileAtCommit(String hash, String path) {
    try (Git git = Git.open(homeDir.toFile())) {
      Repository repo = git.getRepository();

      RevCommit commit;
      try (RevWalk revWalk = new RevWalk(repo)) { commit = revWalk.parseCommit(repo.resolve(hash)); }

      return readBlobAt(git, commit, path);

    } catch (Exception e) {
      log.error("Raw file at commit failed: {}", e.getMessage());
      return null;
    }
  }

  /** Generate a .patch file for a commit. */
  public String generatePatch(String hash) {
    try (Git git = Git.open(homeDir.toFile())) {
      Repository repo = git.getRepository();

      RevCommit commit;
      try (RevWalk revWalk = new RevWalk(repo)) { commit = revWalk.parseCommit(repo.resolve(hash)); }

      ByteArrayOutputStream out = new ByteArrayOutputStream();

      if (commit.getParentCount() > 0) {
        RevCommit parent;
        try (RevWalk rw2 = new RevWalk(repo)) { parent = rw2.parseCommit(commit.getParents()[0]); }

        DiffFormatter formatter = new DiffFormatter(out);
        formatter.setRepository(repo);
        formatter.format(parent, commit);
      } else {
        // Initial commit: list all files with their content
        DiffFormatter formatter = new DiffFormatter(out);
        formatter.setRepository(repo);

        org.eclipse.jgit.treewalk.TreeWalk tw = new org.eclipse.jgit.treewalk.TreeWalk(repo);
        tw.addTree(commit.getTree());

        // Just list files for initial commit patch
        StringBuilder sb = new StringBuilder();
        sb.append("From ").append(commit.getName()).append(" Mon Sep 17 00:00:00 2001\n");
        sb.append("From: ").append(commit.getAuthorIdent().getName()).append("\n");
        sb.append("Date: ").append(commit.getAuthorIdent().getWhen()).append("\n");
        sb.append("Subject: [PATCH] ").append(commit.getShortMessage()).append("\n\n");

        while (tw.next()) {
          String path = tw.getPathString();
          String content = readBlobAt(git, commit, path);
          if (content != null) {
            sb.append("--- /dev/null\n+++ b/").append(path).append("\n");
            sb.append("@@ -0,0 +1,").append(content.split("\n", -1).length).append(" @@\n");
            for (String line : content.split("\n", -1)) { sb.append("+").append(line).append("\n"); }
          }
        }
        out.write(sb.toString().getBytes());
      }

      return out.toString();

    } catch (Exception e) {
      log.error("Patch generation failed: {}", e.getMessage());
      return "";
    }
  }

  // ─── Private helpers ────────────────────────────────────────────

  /** Check if a file path looks like a binary file based on extension. */
  private boolean isBinaryFile(String path) {
    String lower = path.toLowerCase();
    return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
           lower.endsWith(".gif") || lower.endsWith(".bmp") || lower.endsWith(".ico") ||
           lower.endsWith(".svg") || lower.endsWith(".woff") || lower.endsWith(".woff2") ||
           lower.endsWith(".ttf") || lower.endsWith(".eot") || lower.endsWith(".class") ||
           lower.endsWith(".jar") || lower.endsWith(".zip") || lower.endsWith(".gz");
  }

  private List<Map<String, Object>> diffAgainstEmpty(Git git, RevCommit commit) throws Exception {
    Repository repo = git.getRepository();
    List<String> files = listAllFilesInCommit(repo, commit);

    List<Map<String, Object>> result = new ArrayList<>();
    for (String path : files) {
      result.add(Map.of(
          "status", "A",
          "oldPath", "/dev/null",
          "newPath", path
      ));
    }
    return result;
  }

  /** List all file paths in a commit by walking its tree. */
  private List<String> listAllFilesInCommit(Repository repo, RevCommit commit) throws Exception {
    var walk = new org.eclipse.jgit.treewalk.TreeWalk(repo);
    walk.addTree(commit.getTree());

    List<String> files = new ArrayList<>();
    while (walk.next()) {
      files.add(walk.getPathString());
    }
    return files;
  }

  private java.time.Instant parseInstantSafe(String s) {
    if (s == null || s.isBlank()) return null;
    try { return java.time.Instant.parse(s); }
    catch (Exception e) { return null; }
  }

  /** Read a blob's content at a given file path within a commit. */
  private String readBlobAt(Git git, RevCommit commit, String filePath) {
    try {
      Repository repo = git.getRepository();
      // Use git's path resolution: <commit>:<path> resolves to the blob ObjectId
      String ref = commit.getName() + ":" + filePath;
      ObjectId blobId = repo.resolve(ref);
      if (blobId == null) return null;

      // Shell out to git cat-file for reliable blob reading across JGit versions
      ProcessBuilder pb = new ProcessBuilder("git", "cat-file", "blob", blobId.name());
      pb.directory(homeDir.toFile());
      pb.redirectErrorStream(true);
      Process process = pb.start();
      String content = new String(process.getInputStream().readAllBytes());
      process.waitFor();
      return content;

    } catch (Exception e) {
      return null;
    }
  }

  /** Simple line-by-line unified diff (no external diff library needed). */
  private String simpleUnifiedDiff(String oldContent, String newContent, String path) {
    List<String> oldLines = oldContent != null ? Arrays.asList(oldContent.split("\n", -1)) : List.of();
    List<String> newLines = newContent != null ? Arrays.asList(newContent.split("\n", -1)) : List.of();

    StringBuilder sb = new StringBuilder();
    sb.append("--- a/").append(path).append("\n");
    sb.append("+++ b/").append(path).append("\n");

    int i = 0, j = 0;
    while (i < oldLines.size() || j < newLines.size()) {
      if (i < oldLines.size() && j < newLines.size() && oldLines.get(i).equals(newLines.get(j))) {
        sb.append(" ").append(oldLines.get(i)).append("\n");
        i++; j++;
      } else if (j < newLines.size() && (i >= oldLines.size() || !oldLines.get(i).equals(newLines.get(j)))) {
        boolean foundOld = false;
        for (int k = j; k < Math.min(j + 5, newLines.size()); k++) {
          if (i < oldLines.size() && oldLines.get(i).equals(newLines.get(k))) { foundOld = true; break; }
        }
        if (!foundOld && i < oldLines.size()) {
          sb.append("-").append(oldLines.get(i)).append("\n");
          i++;
        } else {
          sb.append("+").append(newLines.get(j)).append("\n");
          j++;
        }
      } else {
        sb.append("-").append(oldLines.get(i)).append("\n");
        i++;
      }
    }

    return sb.toString();
  }

  /** Returns structured status: list of {path, type} where type is modified|deleted|untracked. */
  public List<Map<String, String>> workingTreeStatus(boolean includeUntracked) {
    List<Map<String, String>> result = new ArrayList<>();
    try (Git git = Git.open(homeDir.toFile())) {
      Status status = git.status().call();

      for (String path : status.getUncommittedChanges()) {
        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("path", path);
        boolean deleted = !homeDir.resolve(path).toFile().exists();
        entry.put("type", deleted ? "deleted" : "modified");
        result.add(entry);
      }

      if (includeUntracked) {
        for (String path : status.getUntracked()) {
          Map<String, String> entry = new LinkedHashMap<>();
          entry.put("path", path);
          entry.put("type", "untracked");
          result.add(entry);
        }
      }
    } catch (Exception e) {
      log.warn("Git status failed: {}", e.getMessage());
    }
    return result;
  }

  /** Stage a file (add to index). Works for both new and modified files. */
  public boolean stageFile(String path) {
    try (Git git = Git.open(homeDir.toFile())) {
      git.add().addFilepattern(path).call();
      return true;
    } catch (Exception e) {
      log.error("Failed to stage file {}: {}", path, e.getMessage());
      return false;
    }
  }

  /** Unified diff between HEAD and the working tree for a specific file. */
  public String workingTreeDiff(String path) {
    try (Git git = Git.open(homeDir.toFile())) {
      Status status = git.status().call();

      String oldContent = null;
      String newContent = null;

      if (status.getUntracked().contains(path)) {
        newContent = readFileSafe(path);
      } else if (status.getUncommittedChanges().contains(path)) {
        boolean deleted = !homeDir.resolve(path).toFile().exists();
        oldContent = readHeadContent(git, path);
        if (!deleted) {
          newContent = readFileSafe(path);
        }
      } else {
        return "";
      }

      return buildUnifiedDiff(path, oldContent, newContent);

    } catch (Exception e) {
      log.error("Working tree diff failed for {}: {}", path, e.getMessage());
      return "";
    }
  }

  private String readFileSafe(String path) {
    try {
      return java.nio.file.Files.readString(homeDir.resolve(path));
    } catch (Exception e) {
      log.warn("Cannot read file {}: {}", path, e.getMessage());
      return "";
    }
  }

  private String readHeadContent(Git git, String path) {
    try {
      var repo = git.getRepository();
      var id = repo.resolve("HEAD:" + path);
      if (id == null) return "";
      var is = repo.open(id).openStream();
      return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    } catch (Exception e) {
      log.debug("No HEAD content for {}: {}", path, e.getMessage());
      return "";
    }
  }

  private String buildUnifiedDiff(String path, String oldContent, String newContent) {
    StringBuilder sb = new StringBuilder();
    sb.append("--- a/").append(path).append("\n");
    sb.append("+++ b/").append(path).append("\n");

    List<String> oldLines = oldContent != null ? List.of(oldContent.split("\n", -1)) : List.of();
    List<String> newLines = newContent != null ? List.of(newContent.split("\n", -1)) : List.of();

    // Find common prefix
    int prefix = 0;
    while (prefix < oldLines.size() && prefix < newLines.size()
        && oldLines.get(prefix).equals(newLines.get(prefix))) {
      prefix++;
    }

    // Find common suffix
    int suffixOld = oldLines.size();
    int suffixNew = newLines.size();
    while (suffixOld > prefix && suffixNew > prefix
        && oldLines.get(suffixOld - 1).equals(newLines.get(suffixNew - 1))) {
      suffixOld--;
      suffixNew--;
    }

    int oldStart = prefix;
    int oldEnd = suffixOld;
    int newStart = prefix;
    int newEnd = suffixNew;

    // Hunk header
    int oldCount = oldEnd - oldStart;
    int newCount = newEnd - newStart;
    int oldLineNo = oldStart + 1;
    int newLineNo = newStart + 1;
    sb.append("@@ -").append(oldLineNo).append(",").append(oldCount)
      .append(" +").append(newLineNo).append(",").append(newCount).append(" @@\n");

    // Context before (up to 3 lines)
    int ctxStart = Math.max(0, oldStart - 3);
    for (int i = ctxStart; i < oldStart; i++) {
      sb.append(" ").append(oldLines.get(i)).append("\n");
    }

    // Deletions
    for (int i = oldStart; i < oldEnd; i++) {
      sb.append("-").append(oldLines.get(i)).append("\n");
    }

    // Additions
    for (int i = newStart; i < newEnd; i++) {
      sb.append("+").append(newLines.get(i)).append("\n");
    }

    // Context after (up to 3 lines)
    int ctxEnd = Math.min(oldLines.size(), oldEnd + 3);
    for (int i = oldEnd; i < ctxEnd; i++) {
      sb.append(" ").append(oldLines.get(i)).append("\n");
    }

    return sb.toString();
  }

}
