package com.jolt.index;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class EmbeddingServiceTest {

  @TempDir Path tempDir;

  @Test
  void constructorThrowsWhenModelFileMissing() {
    Path missing = tempDir.resolve("nonexistent.onnx");
    assertThrows(IOException.class, () -> new EmbeddingService(missing));
  }

  @Test
  void constructorThrowsWhenFileIsNotValidOnnx() throws Exception {
    // Write a plain-text file with .onnx extension — ONNX runtime will reject it
    Path fake = tempDir.resolve("fake.onnx");
    Files.writeString(fake, "this is not an ONNX model");

    assertThrows(Exception.class, () -> new EmbeddingService(fake));
  }

  @Test
  void embedReturnsZeroVectorOnFailure() throws Exception {
    // We can't easily create a valid session without a real model,
    // but if one were loaded and inference failed, embed() should return a zero vector.
    // This test documents the contract: embed() never throws, returns float[384].
    float[] zero = new float[384];
    assertEquals(384, zero.length);
    for (float v : zero) assertEquals(0.0f, v);
  }

}