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

import com.jolt.model.BlameLine;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.blame.BlameResult;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Provides git blame information using the JGit blame API. */  
public class BlameService {

  private static final Logger log = LoggerFactory.getLogger(BlameService.class);
  private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

  private final Path homeDir;

  public BlameService(Path homeDir) {
    this.homeDir = homeDir.toAbsolutePath().normalize();
  }

  /**
   * Returns blame information for a file, limited to the given line range.
   *
   * @param relPath    file path relative to the repository root (forward slashes)
   * @param startLine  1-based start line (inclusive)
   * @param endLine    1-based end line (inclusive); use Integer.MAX_VALUE for "to end"
   * @return list of BlameLine records, one per line in range
   * @throws IOException if the file or repository cannot be accessed
   */
  public List<BlameLine> blame(String relPath, int startLine, int endLine) throws IOException {
    Path filePath = homeDir.resolve(relPath).normalize();

    if (!filePath.startsWith(homeDir)) {
      throw new IOException("Access denied: path escapes repository root");
    }

    String gitRelPath = relPath.replace('\\', '/');

    try (Git git = Git.open(homeDir.toFile())) {
      BlameResult result = git.blame().setFilePath(gitRelPath).call();
      if (result == null) {
        throw new IOException("Blame returned null for: " + gitRelPath);
      }

      try {
        int totalLines = result.getResultContents().size();
        int actualEnd = Math.min(endLine, totalLines);

        // computeRange uses 0-based indices: [start, end)
        result.computeRange(startLine - 1, actualEnd);

        List<BlameLine> lines = new ArrayList<>();
        for (int i = startLine - 1; i < actualEnd; i++) {
          RevCommit commit = result.getSourceCommit(i);
          PersonIdent author = result.getSourceAuthor(i);

          if (commit == null || author == null) {
            // Uncommitted or unknown — use placeholder
            lines.add(new BlameLine(i + 1, "unknown", "", "", ""));
            continue;
          }

          String shortHash = commit.getName().substring(0, Math.min(7, commit.getName().length()));
          String authorName = author.getName();
          String date = ISO_FMT.format(Instant.ofEpochSecond(author.getWhen().getTime() / 1000L).atZone(ZoneOffset.UTC));
          String summary = commit.getShortMessage();

          lines.add(new BlameLine(i + 1, authorName, date, shortHash, summary));
        }

        return lines;

      } finally {
        result.discardResultContents();
      }

    } catch (IOException e) {
      throw e;
    } catch (Exception e) {
      log.warn("Blame failed for {}: {}", relPath, e.getMessage());
      throw new IOException("Blame failed: " + e.getMessage(), e);
    }
  }

}

