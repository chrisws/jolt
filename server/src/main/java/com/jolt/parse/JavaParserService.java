package com.jolt.parse;

import com.jolt.model.ClassSymbol;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Service facade over {@link JavaParser}. Provides a higher-level API
 * for indexing pipelines that operate on individual files.
 */
public class JavaParserService {

  private final Path filePath;
  private final String source;
  private final JavaParser parser = new JavaParser();

  public JavaParserService(Path path) throws IOException {
    this.filePath = path.toAbsolutePath();
    this.source = Files.readString(this.filePath);
  }

  /** Parse this file and return all top-level type symbols found. */
  public List<ClassSymbol> parseFile() {
    return parser.parse(filePath);
  }

  /** Convenience: return only the first top-level type, or null if none. */
  public ClassSymbol parseFirstType() {
    List<ClassSymbol> types = parseFile();
    return types.isEmpty() ? null : types.get(0);
  }

}

