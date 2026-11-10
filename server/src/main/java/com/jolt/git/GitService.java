package com.jolt.git;

import org.eclipse.jgit.api.Git;
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
            "date", ident != null ? ident.getWhen().toString() : "",
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
      detail.put("date", ident != null ? ident.getWhen().toString() : "");
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

  // ─── Private helpers ────────────────────────────────────────────

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

  /** Read a blob's content at a given file path within a commit. */
  private String readBlobAt(Git git, RevCommit commit, String filePath) {
    try {
      Repository repo = git.getRepository();
      // Use git's path resolution: <commit>:<path> resolves to the blob ObjectId
      String ref = commit.getName() + ":" + filePath;
      ObjectId blobId = repo.resolve(ref);
      if (blobId == null) return null;

      var blob = repo.open(blobId);
      return new String(blob.getBytes());

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

}