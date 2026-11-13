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

import com.jolt.model.*;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.NIOFSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Manages the Lucene index for full-text and structured symbol search. */
public class LuceneIndexer implements AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(LuceneIndexer.class);

  private final Directory directory;
  private final IndexWriter writer;
  private volatile SearcherManager searcherMgr;

  public LuceneIndexer(Path indexPath) throws IOException {
    this.directory = NIOFSDirectory.open(indexPath);

    IndexWriterConfig config = new IndexWriterConfig(ANALYZER);

    this.writer = new IndexWriter(directory, config);
    this.searcherMgr = new SearcherManager(writer, null);

    log.info("Lucene indexer opened at {}", indexPath.toAbsolutePath());
  }

  /** Index a single file as a FILE document (raw text for full-text search). */
  public void indexFile(String filePath, String content) throws IOException {
    Document doc = new Document();
    doc.add(new StoredField("id", filePath));
    doc.add(new StoredField("kind", SymbolKind.FILE.name()));
    doc.add(new StoredField("name", fileName(filePath)));
    doc.add(new StringField("nameKw", fileName(filePath).toLowerCase(), Field.Store.NO));
    doc.add(new StoredField("filePath", filePath));
    doc.add(new TextField("body", content, Field.Store.YES));
    doc.add(new StringField("tokens", content.toLowerCase(), Field.Store.NO));

    writer.updateDocument(new Term("filePath", filePath), doc); // upsert by path
  }

  /** Index a class symbol as a structured CLASS document. */
  public void indexClass(ClassSymbol cs) throws IOException {
    Document doc = new Document();
    String id = cs.fqn;

    doc.add(new StringField("id", id, Field.Store.YES));
    doc.add(new StringField("kind", cs.kind.name(), Field.Store.YES));
    doc.add(new TextField("name", cs.name, Field.Store.YES));
    doc.add(new TextField("qualifiedName", cs.fqn, Field.Store.YES));
    doc.add(new StringField("filePath", cs.filePath, Field.Store.YES));
    doc.add(new IntPoint("lineStart", cs.lineStart));
    doc.add(new StoredField("_lineStartStr", String.valueOf(cs.lineStart)));

    if (cs.superClass != null) {
      doc.add(new StoredField("superClass", cs.superClass));
    }

    // Body field for full-text search — combine name and FQN so QueryParser can find it
    doc.add(new TextField("body", cs.fqn + " " + cs.name, Field.Store.NO));

    writer.updateDocument(new Term("id", id), doc);

    // Also index each method as its own document
    for (var entry : cs.methods.entrySet()) {
      MethodSymbol ms = entry.getValue();
      indexMethod(ms, cs.filePath);
    }

    // And fields
    for (var entry : cs.fields.entrySet()) {
      FieldSymbol fs = entry.getValue();
      indexField(fs, cs.fqn, cs.filePath);
    }
  }

  private void indexMethod(MethodSymbol ms, String filePath) throws IOException {
    Document doc = new Document();

    doc.add(new StringField("id", ms.fqn(), Field.Store.YES));
    doc.add(new StringField("kind", SymbolKind.METHOD.name(), Field.Store.YES));
    doc.add(new TextField("name", ms.name(), Field.Store.YES));
    doc.add(new TextField("qualifiedName", ms.fqn(), Field.Store.YES));
    doc.add(new TextField("signature", ms.signature(), Field.Store.YES));
    doc.add(new StringField("filePath", filePath, Field.Store.YES));
    doc.add(new IntPoint("lineStart", ms.lineStart()));

    // Body for full-text search
    StringBuilder body = new StringBuilder(ms.fqn()).append(' ').append(ms.name());
    if (ms.signature() != null) body.append(' ').append(ms.signature());
    doc.add(new TextField("body", body.toString(), Field.Store.NO));

    writer.updateDocument(new Term("id", ms.fqn()), doc);
  }

  private void indexField(FieldSymbol fs, String classFqn, String filePath) throws IOException {
    Document doc = new Document();

    String id = classFqn + "#" + fs.name() + "~field";
    doc.add(new StringField("id", id, Field.Store.YES));
    doc.add(new StringField("kind", SymbolKind.FIELD.name(), Field.Store.YES));
    doc.add(new TextField("name", fs.name(), Field.Store.YES));
    doc.add(new TextField("qualifiedName", classFqn, Field.Store.YES));
    doc.add(new TextField("type", fs.type(), Field.Store.YES));
    doc.add(new StringField("filePath", filePath, Field.Store.YES));

    // Body for full-text search
    StringBuilder body = new StringBuilder(classFqn).append(' ').append(fs.name());
    if (fs.type() != null) body.append(' ').append(fs.type());
    doc.add(new TextField("body", body.toString(), Field.Store.NO));

    writer.updateDocument(new Term("id", id), doc);
  }

  /** Search across all indexed documents. */
  public List<SearchHit> search(String queryText, SymbolKind kindFilter, int maxResults) throws Exception {
    return search(queryText, kindFilter, maxResults, 0); // no fuzzy by default
  }

  /** Search with optional fuzzy fallback. If {@code maxEdits > 0}, supplements sparse results with FuzzyQuery. */
  public List<SearchHit> search(String queryText, SymbolKind kindFilter, int maxResults, int maxEdits) throws Exception {
    return search(queryText, kindFilter, maxResults, maxEdits, false, false);
  }

  /** Search with case-sensitive and whole-word post-filters. */
  public List<SearchHit> search(String queryText, SymbolKind kindFilter, int maxResults, int maxEdits,
                                boolean caseSensitive, boolean wholeWord) throws Exception {
    QueryParser parser = new QueryParser("body", ANALYZER); // search in body field primarily

    BooleanQuery.Builder builder = new BooleanQuery.Builder();

    // Text match on name or body
    if (queryText != null && !queryText.isBlank()) {
      var textQuery = parser.parse(queryText);
      builder.add(textQuery, BooleanClause.Occur.SHOULD);

      // Also try matching against the "name" field for symbol searches
      QueryParser nameParser = new QueryParser("name", ANALYZER);
      var nameQuery = nameParser.parse(QueryParser.escape(queryText));
      builder.add(nameQuery, BooleanClause.Occur.SHOULD);
    } else {
      // No query → return everything (match_all)
      builder.add(new MatchAllDocsQuery(), BooleanClause.Occur.MUST);
    }

    // Kind filter
    if (kindFilter != null) {
      builder.add(new TermQuery(new Term("kind", kindFilter.name())), BooleanClause.Occur.FILTER);
    }

    searcherMgr.maybeRefresh(); // ensure latest commits are visible
    IndexSearcher searcher = searcherMgr.acquire();
    try {
      TopDocs topDocs = searcher.search(builder.build(), maxResults);

      List<SearchHit> hits = new ArrayList<>();
      for (var scoreDoc : topDocs.scoreDocs) {
        Document doc = searcher.storedFields().document(scoreDoc.doc);

        SymbolKind kind;
        try { kind = SymbolKind.valueOf(doc.get("kind")); }
        catch (Exception e) { kind = SymbolKind.FILE; }

        int lineStart = 0;
        String lsStr = doc.get("_lineStartStr");
        if (lsStr != null) { try { lineStart = Integer.parseInt(lsStr); } catch (NumberFormatException ignored) {} }

        hits.add(new SearchHit(
            doc.get("id") != null ? doc.get("id") : "",
            kind,
            doc.get("name") != null ? doc.get("name") : "",
            doc.get("qualifiedName") != null ? doc.get("qualifiedName") : "",
            doc.get("filePath") != null ? doc.get("filePath") : "",
            lineStart,
            scoreDoc.score
        ));
      }

      // Fuzzy fallback: if results are sparse and maxEdits > 0, supplement with FuzzyQuery
      if (maxEdits > 0 && queryText != null && !queryText.isBlank() && hits.size() < maxResults && queryText.length() >= 3) {
        Query fuzzyQ = new FuzzyQuery(new Term("name", queryText.toLowerCase()), maxEdits);

        // Apply kind filter to fuzzy query too
        Query finalFuzzyQ;
        if (kindFilter != null) {
          BooleanQuery.Builder fb = new BooleanQuery.Builder();
          fb.add(fuzzyQ, BooleanClause.Occur.MUST);
          fb.add(new TermQuery(new Term("kind", kindFilter.name())), BooleanClause.Occur.FILTER);
          finalFuzzyQ = fb.build();
        } else {
          finalFuzzyQ = fuzzyQ;
        }

        TopDocs fuzzyDocs = searcher.search(finalFuzzyQ, maxResults - hits.size());

        // Avoid duplicates by id
        Set<String> seenIds = new java.util.HashSet<>();
        for (SearchHit h : hits) seenIds.add(h.id());

        for (var scoreDoc : fuzzyDocs.scoreDocs) {
          Document doc = searcher.storedFields().document(scoreDoc.doc);
          String id = doc.get("id") != null ? doc.get("id") : "";
          if (seenIds.contains(id)) continue;

          SymbolKind kind;
          try { kind = SymbolKind.valueOf(doc.get("kind")); } catch (Exception e) { kind = SymbolKind.FILE; }

          int lineStart = 0;
          String lsStr = doc.get("_lineStartStr");
          if (lsStr != null) { try { lineStart = Integer.parseInt(lsStr); } catch (NumberFormatException ignored) {} }

          hits.add(new SearchHit(
              id, kind,
              doc.get("name") != null ? doc.get("name") : "",
              doc.get("qualifiedName") != null ? doc.get("qualifiedName") : "",
              doc.get("filePath") != null ? doc.get("filePath") : "",
              lineStart,
              scoreDoc.score * 0.8f // slight penalty for fuzzy matches vs exact
          ));

          if (hits.size() >= maxResults) break;
        }
      }

      // Post-filters: case-sensitive and whole-word matching on name/qualifiedName
      if ((caseSensitive || wholeWord) && queryText != null && !queryText.isBlank()) {
        List<SearchHit> filtered = new ArrayList<>();
        for (SearchHit h : hits) {
          String name = h.name();
          String qn = h.qualifiedName();

          // Case-sensitive: exact case match in name or qualifiedName
          if (caseSensitive) {
            if (!name.contains(queryText) && !qn.contains(queryText)) continue;
          }

          // Whole-word: query must appear as a standalone word (bounded by non-alphanumeric)
          if (wholeWord) {
            if (!containsWholeWord(name, queryText) && !containsWholeWord(qn, queryText)) continue;
          }

          filtered.add(h);
        }
        hits = filtered;
      }

      return hits;
    } finally {
      searcherMgr.release(searcher);
    }
  }

  /** Check if {@code text} contains {@code word} as a whole word (bounded by non-alphanumeric chars). */
  private static boolean containsWholeWord(String text, String word) {
    if (text == null || word == null || word.isEmpty()) return false;
    int idx = 0;
    while ((idx = text.indexOf(word, idx)) != -1) {
      int end = idx + word.length();
      boolean leftOk = (idx == 0) || !Character.isLetterOrDigit(text.charAt(idx - 1));
      boolean rightOk = (end == text.length()) || !Character.isLetterOrDigit(text.charAt(end));
      if (leftOk && rightOk) return true;
      idx++;
    }
    return false;
  }

  /** Semantic search using brute-force cosine similarity over stored embeddings. */
  public List<SearchHit> searchSemantic(float[] queryVector, int k) throws Exception {
    searcherMgr.maybeRefresh();

    try (DirectoryReader reader = DirectoryReader.open(directory)) {
      int numDocs = reader.maxDoc();

      // Collect (docId, cosineScore) pairs for docs that have an embedding field
      List<float[]> scored = new ArrayList<>(); // [docId, score]

      for (int i = 0; i < numDocs; i++) {
        Document doc;
        try {
          doc = reader.storedFields().document(i);
        } catch (Exception e) {
          continue; // deleted doc — skip
        }

        String embStr = doc.get("embedding");
        if (embStr == null || embStr.isBlank()) continue;

        float[] docVec = parseEmbedding(embStr);
        double cosSim = cosineSimilarity(queryVector, docVec);

        scored.add(new float[]{i, (float) cosSim});
      }

      // Sort by score descending, take top-k
      scored.sort((a, b) -> Float.compare(b[1], a[1]));

      List<SearchHit> hits = new ArrayList<>();
      for (int i = 0; i < Math.min(k, scored.size()); i++) {
        int docId = (int) scored.get(i)[0];
        float score = scored.get(i)[1];
        Document doc = reader.storedFields().document(docId);

        SymbolKind kind;
        try { kind = SymbolKind.valueOf(doc.get("kind")); }
        catch (Exception e) { kind = SymbolKind.FILE; }

        int lineStart = 0;
        String lsStr = doc.get("_lineStartStr");
        if (lsStr != null) { try { lineStart = Integer.parseInt(lsStr); } catch (NumberFormatException ignored) {} }

        hits.add(new SearchHit(
            doc.get("id") != null ? doc.get("id") : "",
            kind,
            doc.get("name") != null ? doc.get("name") : "",
            doc.get("qualifiedName") != null ? doc.get("qualifiedName") : "",
            doc.get("filePath") != null ? doc.get("filePath") : "",
            lineStart,
            score
        ));
      }
      return hits;
    }
  }

  /** Store a float vector as a comma-separated string field on the document. */
  public static void addEmbeddingField(Document doc, float[] vector) {
    StringBuilder sb = new StringBuilder(vector.length * 8);
    for (int i = 0; i < vector.length; i++) {
      if (i > 0) sb.append(',');
      sb.append(vector[i]);
    }
    doc.add(new StoredField("embedding", sb.toString()));
  }

  private static float[] parseEmbedding(String s) {
    String[] parts = s.split(",");
    float[] vec = new float[parts.length];
    for (int i = 0; i < parts.length; i++) {
      vec[i] = Float.parseFloat(parts[i]);
    }
    return vec;
  }

  private static double cosineSimilarity(float[] a, float[] b) {
    if (a.length != b.length) return 0.0;
    double dot = 0, normA = 0, normB = 0;
    for (int i = 0; i < a.length; i++) {
      dot += a[i] * b[i];
      normA += a[i] * a[i];
      normB += b[i] * b[i];
    }
    double denom = Math.sqrt(normA) * Math.sqrt(normB);
    return denom == 0 ? 0.0 : dot / denom;
  }

  /**
   * Predictive suggestions: prefix match on the "name" field, falling back to
   * fuzzy match when prefix yields fewer than {@code limit} results.
   * Returns up to {@code limit} SearchHit objects sorted by score.
   */
  public List<SearchHit> suggest(String query, int limit) throws Exception {
    if (query == null || query.isBlank()) return List.of();

    searcherMgr.maybeRefresh();
    IndexSearcher searcher = searcherMgr.acquire();
    try {
      // Phase 1: PrefixQuery on "name" + "nameKw" fields (fast, exact prefix)
      String qLower = query.toLowerCase();
      Query prefixQ = new BooleanQuery.Builder()
          .add(new PrefixQuery(new Term("name", qLower)), BooleanClause.Occur.SHOULD)
          .add(new PrefixQuery(new Term("nameKw", qLower)), BooleanClause.Occur.SHOULD)
          .build();

      TopDocs topDocs = searcher.search(prefixQ, limit);

      List<SearchHit> hits = new ArrayList<>();
      for (var scoreDoc : topDocs.scoreDocs) {
        Document doc = searcher.storedFields().document(scoreDoc.doc);
        SymbolKind kind;
        try { kind = SymbolKind.valueOf(doc.get("kind")); } catch (Exception e) { kind = SymbolKind.FILE; }

        int lineStart = 0;
        String lsStr = doc.get("_lineStartStr");
        if (lsStr != null) { try { lineStart = Integer.parseInt(lsStr); } catch (NumberFormatException ignored) {} }

        hits.add(new SearchHit(
            doc.get("id") != null ? doc.get("id") : "",
            kind,
            doc.get("name") != null ? doc.get("name") : "",
            doc.get("qualifiedName") != null ? doc.get("qualifiedName") : "",
            doc.get("filePath") != null ? doc.get("filePath") : "",
            lineStart,
            scoreDoc.score
        ));
      }

      // Phase 2: If prefix gave fewer results than limit, supplement with FuzzyQuery
      if (hits.size() < limit && query.length() >= 3) {
        int maxEdits = query.length() <= 4 ? 1 : 2; // tighter for short queries
        Query fuzzyQ = new BooleanQuery.Builder()
            .add(new FuzzyQuery(new Term("name", qLower), maxEdits), BooleanClause.Occur.SHOULD)
            .add(new FuzzyQuery(new Term("nameKw", qLower), maxEdits), BooleanClause.Occur.SHOULD)
            .build();

        TopDocs fuzzyDocs = searcher.search(fuzzyQ, limit - hits.size());

        // Avoid duplicates by id
        Set<String> seenIds = new java.util.HashSet<>();
        for (SearchHit h : hits) seenIds.add(h.id());

        for (var scoreDoc : fuzzyDocs.scoreDocs) {
          Document doc = searcher.storedFields().document(scoreDoc.doc);
          String id = doc.get("id") != null ? doc.get("id") : "";
          if (seenIds.contains(id)) continue;

          SymbolKind kind;
          try { kind = SymbolKind.valueOf(doc.get("kind")); } catch (Exception e) { kind = SymbolKind.FILE; }

          int lineStart = 0;
          String lsStr = doc.get("_lineStartStr");
          if (lsStr != null) { try { lineStart = Integer.parseInt(lsStr); } catch (NumberFormatException ignored) {} }

          hits.add(new SearchHit(
              id, kind,
              doc.get("name") != null ? doc.get("name") : "",
              doc.get("qualifiedName") != null ? doc.get("qualifiedName") : "",
              doc.get("filePath") != null ? doc.get("filePath") : "",
              lineStart,
              scoreDoc.score * 0.8f // slight penalty for fuzzy matches vs prefix
          ));

          if (hits.size() >= limit) break;
        }
      }

      return hits;
    } finally {
      searcherMgr.release(searcher);
    }
  }

  /** Flush pending writes to disk. */
  public void commit() throws IOException { writer.commit(); log.debug("Lucene index committed"); }

  @Override
  public void close() throws Exception {
    if (searcherMgr != null) searcherMgr.close();
    writer.close();
    directory.close();
    log.info("Lucene indexer closed");
  }

  private static org.apache.lucene.analysis.Analyzer ANALYZER = new org.apache.lucene.analysis.standard.StandardAnalyzer();

  private static String fileName(String path) {
    int idx = path.lastIndexOf('/');
    return idx >= 0 ? path.substring(idx + 1) : path;
  }

}
