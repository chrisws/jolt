package com.jolt.index;

import com.jolt.model.*;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Manages the Lucene index for full-text and structured symbol search. */
public class LuceneIndexer implements AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(LuceneIndexer.class);

  private final Directory directory;
  private final IndexWriter writer;
  private volatile SearcherManager searcherMgr;

  public LuceneIndexer(Path indexPath) throws IOException {
    this.directory = FSDirectory.open(indexPath);
    
    IndexWriterConfig config = new IndexWriterConfig(ANALYZER); // use standard analyzer below
    
    // We'll set up a proper analyzer in the constructor — using StandardAnalyzer from lucene-analysis-common
    // For Phase 0, we can get away with the default (KeywordAnalyzer won't work for text search)
    // Let's use a simple approach: store raw + analyzed fields separately
    
    this.writer = new IndexWriter(directory, config);
    this.searcherMgr = new SearcherManager(writer, null); // null → use writer's reader
    
    log.info("Lucene indexer opened at {}", indexPath.toAbsolutePath());
  }

  /** Index a single file as a FILE document (raw text for full-text search). */
  public void indexFile(String filePath, String content) throws IOException {
    Document doc = new Document();
    doc.add(new StoredField("id", filePath));
    doc.add(new StoredField("kind", SymbolKind.FILE.name()));
    doc.add(new StoredField("name", fileName(filePath)));
    doc.add(new StoredField("filePath", filePath));
    doc.add(new TextField("body", content, Field.Store.YES));       // analyzed for search
    doc.add(new StringField("tokens", content.toLowerCase(), Field.Store.NO)); // keyword tokens
    
    writer.updateDocument(new Term("filePath", filePath), doc); // upsert by path
  }

  /** Index a class symbol as a structured CLASS document. */  
  public void indexClass(ClassSymbol cs) throws IOException {  
    Document doc = new Document();  
    String id = cs.fqn;  

    doc.add(new StringField("id", id, Field.Store.YES));              // indexed keyword + stored → upsert & exact match  
    doc.add(new StringField("kind", cs.kind.name(), Field.Store.YES)); // indexed keyword + stored → TermQuery filter  
    doc.add(new TextField("name", cs.name, Field.Store.YES));          // analyzed + stored → text search  
    doc.add(new TextField("qualifiedName", cs.fqn, Field.Store.YES));   // analyzed + stored → FQN search  
    doc.add(new StringField("filePath", cs.filePath, Field.Store.YES)); // indexed keyword + stored  
    doc.add(new IntPoint("lineStart", cs.lineStart));                  // numeric range queries  
    doc.add(new StoredField("_lineStartStr", String.valueOf(cs.lineStart))); // display only  

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

