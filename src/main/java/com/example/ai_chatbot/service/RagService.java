package com.example.ai_chatbot.service;

import com.example.ai_chatbot.entity.KnowledgeDocument;
import com.example.ai_chatbot.entity.User;
import com.example.ai_chatbot.repository.KnowledgeDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.*;

/**
 * The RAG pipeline.
 * Ingest:   file -> Tika text extraction -> token-based chunking -> embeddings -> pgvector
 * Retrieve: question -> embedding -> cosine similarity search, ALWAYS filtered to the current user's chunks
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);
    private static final Set<String> ALLOWED = Set.of("pdf", "txt", "md", "docx", "html", "csv");

    private final VectorStore vectorStore;
    private final KnowledgeDocumentRepository documents;
    private final TokenTextSplitter splitter;
    private final int topK;
    private final double threshold;
    private final int maxDocs;

    public RagService(VectorStore vectorStore,
                      KnowledgeDocumentRepository documents,
                      @Value("${app.rag.top-k:4}") int topK,
                      @Value("${app.rag.similarity-threshold:0.45}") double threshold,
                      @Value("${app.rag.chunk-size:500}") int chunkSize,
                      @Value("${app.rag.max-documents-per-user:20}") int maxDocs) {

        this.vectorStore = vectorStore;
        this.documents = documents;
        this.topK = topK;
        this.threshold = threshold;
        this.maxDocs = maxDocs;

        this.splitter = TokenTextSplitter.builder()
                .withChunkSize(chunkSize)
                .withMinChunkSizeChars(350)
                .withMinChunkLengthToEmbed(5)
                .withMaxNumChunks(10_000)
                .withKeepSeparator(true)
                .withPunctuationMarks(List.of('.', '!', '?', ',', ';', ':', '\n'))
                .build();
    }
    // ------------------------------------------------------------------ ingest
    public KnowledgeDocument ingest(User user, MultipartFile file) {
        String filename = cleanName(file.getOriginalFilename());
        String ext = filename.contains(".") ? filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        if (!ALLOWED.contains(ext)) {
            throw bad("Unsupported file type. Allowed: " + String.join(", ", new TreeSet<>(ALLOWED)));
        }
        if (documents.countByUser(user) >= maxDocs) {
            throw bad("Document limit reached (" + maxDocs + "). Delete one first.");
        }

        List<Document> pages;
        try {
            pages = new TikaDocumentReader(new ByteArrayResource(file.getBytes())).get();
        } catch (IOException | RuntimeException e) {
            throw bad("Could not read that file.");
        }

        String docKey = UUID.randomUUID().toString();
        List<Document> tagged = new ArrayList<>();
        for (Document page : pages) {
            String text = page.getText();
            if (text == null || text.isBlank()) {
                continue;
            }
            Map<String, Object> meta = new HashMap<>();
            meta.put("userId", String.valueOf(user.getId())); // isolation key
            meta.put("docId", docKey);
            meta.put("filename", filename);
            tagged.add(new Document(text, meta));
        }
        if (tagged.isEmpty()) {
            throw bad("No readable text found (scanned PDFs need OCR first).");
        }

        List<Document> chunks = splitter.apply(tagged);
        if (chunks.isEmpty()) {
            throw bad("Document is too short to index.");
        }

        vectorStore.add(chunks); // calls the embedding model for each chunk
        try {
            return documents.save(new KnowledgeDocument(docKey, filename, chunks.size(), user));
        } catch (RuntimeException e) {
            vectorStore.delete(new FilterExpressionBuilder().eq("docId", docKey).build()); // don't orphan vectors
            throw e;
        }
    }

    // ---------------------------------------------------------------- retrieve
    public List<Document> search(User user, String query) {
        if (documents.countByUser(user) == 0) {
            return List.of();
        }
        try {
            List<Document> hits = vectorStore.similaritySearch(SearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .similarityThreshold(threshold)
                    .filterExpression(new FilterExpressionBuilder().eq("userId", String.valueOf(user.getId())).build())
                    .build());
            return hits == null ? List.of() : hits;
        } catch (RuntimeException e) {
            log.warn("Retrieval failed, answering without documents: {}", e.getMessage());
            return List.of();
        }
    }

    // ------------------------------------------------------------ list/delete
    public List<KnowledgeDocument> list(User user) {
        return documents.findByUserOrderByIdDesc(user);
    }

    public void delete(User user, Long id) {
        KnowledgeDocument doc = documents.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        vectorStore.delete(b.and(b.eq("userId", String.valueOf(user.getId())), b.eq("docId", doc.getDocKey())).build());
        documents.delete(doc);
    }

    // ----------------------------------------------------------------- helpers
    private static String cleanName(String name) {
        if (name == null || name.isBlank()) {
            return "document.txt";
        }
        String base = name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "");
        return base.length() > 120 ? base.substring(base.length() - 120) : base;
    }

    private static ResponseStatusException bad(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }
}
