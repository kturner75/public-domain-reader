package org.example.reader.service;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class SearchService {

    private final Directory index;
    private final StandardAnalyzer analyzer;

    public SearchService() {
        this.index = new ByteBuffersDirectory();
        this.analyzer = new StandardAnalyzer();
    }

    public void indexBook(String bookId, String title, String author) throws IOException {
        try (IndexWriter writer = new IndexWriter(index, new IndexWriterConfig(analyzer))) {
            Document doc = new Document();
            doc.add(new StringField("type", "book", Field.Store.YES));
            doc.add(new StringField("bookId", bookId, Field.Store.YES));
            doc.add(new TextField("title", title, Field.Store.YES));
            doc.add(new TextField("author", author, Field.Store.YES));
            doc.add(new TextField("content", title + " " + author, Field.Store.NO));
            writer.addDocument(doc);
        }
    }

    public void indexParagraph(String bookId, String chapterId, int paragraphIndex, String content) throws IOException {
        try (IndexWriter writer = new IndexWriter(index, new IndexWriterConfig(analyzer))) {
            Document doc = new Document();
            doc.add(new StringField("type", "paragraph", Field.Store.YES));
            doc.add(new StringField("bookId", bookId, Field.Store.YES));
            doc.add(new StringField("chapterId", chapterId, Field.Store.YES));
            doc.add(new StoredField("paragraphIndex", paragraphIndex));
            doc.add(new TextField("content", content, Field.Store.YES));
            writer.addDocument(doc);
        }
    }

    public List<SearchResult> search(String queryStr, int maxResults) throws IOException, ParseException {
        return search(queryStr, null, maxResults);
    }

    public List<SearchResult> search(String queryStr, String bookId, int maxResults) throws IOException, ParseException {
        List<SearchResult> results = new ArrayList<>();

        try (DirectoryReader reader = DirectoryReader.open(index)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            QueryParser parser = new QueryParser("content", analyzer);
            Query contentQuery = parser.parse(queryStr);

            Query finalQuery;
            if (bookId != null && !bookId.isBlank()) {
                // Filter by bookId and only return paragraph results (not book metadata)
                BooleanQuery.Builder builder = new BooleanQuery.Builder();
                builder.add(contentQuery, BooleanClause.Occur.MUST);
                builder.add(new TermQuery(new Term("bookId", bookId)), BooleanClause.Occur.MUST);
                builder.add(new TermQuery(new Term("type", "paragraph")), BooleanClause.Occur.MUST);
                finalQuery = builder.build();
            } else {
                finalQuery = contentQuery;
            }

            ScoreDoc[] hits = searcher.search(finalQuery, maxResults).scoreDocs;

            for (ScoreDoc hit : hits) {
                Document doc = searcher.storedFields().document(hit.doc);
                String type = doc.get("type");
                Integer paragraphIndex = null;
                String snippet = null;

                if ("paragraph".equals(type)) {
                    paragraphIndex = doc.getField("paragraphIndex").numericValue().intValue();
                    String content = doc.get("content");
                    snippet = content.length() > 100 ? content.substring(0, 100) + "..." : content;
                }

                results.add(new SearchResult(
                    type,
                    doc.get("bookId"),
                    doc.get("chapterId"),
                    paragraphIndex,
                    doc.get("title"),
                    snippet,
                    hit.score
                ));
            }
        }

        return results;
    }

    public void deleteByBookId(String bookId) throws IOException {
        try (IndexWriter writer = new IndexWriter(index, new IndexWriterConfig(analyzer))) {
            writer.deleteDocuments(new Term("bookId", bookId));
        }
    }

    public record SearchResult(
        String type,
        String bookId,
        String chapterId,
        Integer paragraphIndex,
        String title,
        String snippet,
        float score
    ) {}
}
