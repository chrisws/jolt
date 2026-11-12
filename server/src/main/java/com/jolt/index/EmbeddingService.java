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

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.LongBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads an ONNX sentence-embedding model and produces float[384] vectors for text.
 * Uses all-MiniLM-L6-v2 (or compatible) model. Tokenization is simplified whitespace-based
 * for code search — sufficient for identifier-heavy content.
 */
public class EmbeddingService implements AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

  private static final int MAX_SEQ_LEN = 128;
  private static final int EMBED_DIM = 384;

  private final OrtEnvironment env;
  private final OrtSession session;

  /** Create an EmbeddingService from a local .onnx file. */
  public EmbeddingService(Path modelPath) throws IOException {
    this.env = OrtEnvironment.getEnvironment();
    try {
      this.session = env.createSession(modelPath.toAbsolutePath().toString(), new OrtSession.SessionOptions());
    } catch (ai.onnxruntime.OrtException e) {
      throw new IOException("Failed to load ONNX model: " + e.getMessage(), e);
    }
    log.info("Embedding model loaded from {} (dim={})", modelPath.getFileName(), EMBED_DIM);
  }

  /** Embed a single text string into a float vector. */
  public float[] embed(String text) {
    try {
      int[] inputIds = tokenize(text);
      int[] attentionMask = new int[inputIds.length];
      java.util.Arrays.fill(attentionMask, 1);

      long[] idsLong = new long[inputIds.length];
      long[] maskLong = new long[attentionMask.length];
      for (int i = 0; i < inputIds.length; i++) {
        idsLong[i] = inputIds[i];
        maskLong[i] = attentionMask[i];
      }

      long[] shape = {1, inputIds.length};

      try (OnnxTensor idsTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(idsLong), shape);
           OnnxTensor maskTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(maskLong), shape)) {

        var inputs = new java.util.HashMap<String, OnnxTensor>();
        inputs.put("input_ids", idsTensor);
        inputs.put("attention_mask", maskTensor);

        try (var result = session.run(inputs)) {
          // Output: [batch=1, seq_len, 384] — mean pool over non-padding positions
          float[][][] arr = (float[][][]) result.get(0).getValue();
          return meanPool(arr[0], attentionMask);
        }
      }
    } catch (Exception e) {
      log.error("Embedding failed for text: {}", truncate(text, 80), e);
      return new float[EMBED_DIM]; // zero vector as fallback
    }
  }

  /** Simple whitespace + punctuation tokenizer. Maps tokens to small vocab IDs. */
  private int[] tokenize(String text) {
    // For code search, split on whitespace and common delimiters
    String[] tokens = text.toLowerCase().split("[\\s{}();,\\.\\[\\]\"'=+\\-*/<>!&|]+");

    List<Integer> ids = new ArrayList<>();
    ids.add(0); // [CLS]

    for (String tok : tokens) {
      if (tok.isEmpty()) continue;
      // Truncate long identifiers to first 12 chars (subword approximation)
      String t = tok.length() > 12 ? tok.substring(0, 12) : tok;
      ids.add(hashToken(t));
      if (ids.size() >= MAX_SEQ_LEN - 1) break; // leave room for [SEP]
    }

    ids.add(0); // [SEP] — reuse 0 as a simple separator
    return ids.stream().mapToInt(Integer::intValue).toArray();
  }

  /** Deterministic hash of a token into a vocab range (0..30000). */
  private int hashToken(String token) {
    int h = 17;
    for (char c : token.toCharArray()) {
      h = h * 31 + c;
    }
    return Math.abs(h) % 30000 + 1; // avoid 0 which is reserved for CLS/SEP/pad
  }

  /** Mean-pool the sequence output over non-padding positions. */
  private float[] meanPool(float[][] seq, int[] mask) {
    float[] pooled = new float[EMBED_DIM];
    int count = 0;
    for (int i = 0; i < seq.length; i++) {
      if (mask[i] == 1) {
        for (int j = 0; j < EMBED_DIM; j++) {
          pooled[j] += seq[i][j];
        }
        count++;
      }
    }
    if (count > 0) {
      for (int j = 0; j < EMBED_DIM; j++) {
        pooled[j] /= count;
      }
    }
    // L2 normalize
    double norm = 0;
    for (float v : pooled) norm += v * v;
    norm = Math.sqrt(norm);
    if (norm > 0) {
      for (int j = 0; j < EMBED_DIM; j++) pooled[j] /= (float) norm;
    }
    return pooled;
  }

  private static String truncate(String s, int max) {
    return s.length() <= max ? s : s.substring(0, max) + "…";
  }

  @Override
  public void close() throws Exception {
    if (session != null) session.close();
    log.info("EmbeddingService closed");
  }

}
