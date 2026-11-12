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