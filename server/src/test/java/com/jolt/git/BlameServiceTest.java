package com.jolt.git;

import com.jolt.model.BlameLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Tests for BlameService using a real git repository in a temp directory. */  
class BlameServiceTest {

  @TempDir
  Path tempDir;

  private BlameService blameSvc;

  @BeforeEach
  void setUp() throws Exception {
    blameSvc = new BlameService(tempDir);

    // Initialize a git repo and create a file with two commits
    runGit("init");
    runGit("config", "user.email", "test@example.com");
    runGit("config", "user.name", "Test User");

    // First commit: create file with one line
    Path file = tempDir.resolve("hello.txt");
    Files.writeString(file, "line one\n");
    runGit("add", "hello.txt");
    runGit("commit", "-m", "first commit");

    // Second commit: add a second line
    Files.writeString(file, "line one\nline two\n");
    runGit("add", "hello.txt");
    runGit("commit", "-m", "second commit");
  }

  @Test
  void blameReturnsLinesForFullFile() throws IOException {
    List<BlameLine> lines = blameSvc.blame("hello.txt", 1, Integer.MAX_VALUE);

    assertEquals(2, lines.size());

    // Line 1 should be from the first commit
    BlameLine line1 = lines.get(0);
    assertEquals(1, line1.line());
    assertEquals("Test User", line1.author());
    assertEquals("first commit", line1.summary());
    assertNotNull(line1.hash());
    assertTrue(line1.hash().length() >= 7);

    // Line 2 should be from the second commit
    BlameLine line2 = lines.get(1);
    assertEquals(2, line2.line());
    assertEquals("Test User", line2.author());
    assertEquals("second commit", line2.summary());
  }

  @Test
  void blameRespectsLineRange() throws IOException {
    List<BlameLine> lines = blameSvc.blame("hello.txt", 2, 2);

    assertEquals(1, lines.size());
    assertEquals(2, lines.get(0).line());
    assertEquals("second commit", lines.get(0).summary());
  }

  @Test
  void blameRejectsPathTraversal() {
    assertThrows(IOException.class, () -> blameSvc.blame("../etc/passwd", 1, 10));
  }

  @Test
  void blameThrowsForMissingFile() {
    assertThrows(IOException.class, () -> blameSvc.blame("nonexistent.txt", 1, 10));
  }

  private void runGit(String... args) throws Exception {
    List<String> cmd = new ArrayList<>();
    cmd.add("git");
    cmd.addAll(List.of(args));
    ProcessBuilder pb = new ProcessBuilder(cmd);
    pb.directory(tempDir.toFile());
    pb.redirectErrorStream(true);
    Process p = pb.start();

    // Consume output to prevent blocking
    try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
      while (reader.readLine() != null) { /* drain */ }
    }

    int exit = p.waitFor();
    assertEquals(0, exit, "git " + String.join(" ", args) + " failed");
  }

}

