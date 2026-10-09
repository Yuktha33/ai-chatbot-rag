# AI Chatbot with RAG

Spring Boot 4 + Spring AI 2 chatbot that answers questions about **your own documents** (RAG), remembers facts
about you and recalls earlier conversations across chats (long-term memory), and runs fully locally on Ollama.

## Architecture

```
Browser (HTML/JS) ──JWT──► Spring Boot
                            ├─ AuthController        register / login
                            ├─ DocumentController    upload · list · delete documents
                            ├─ MemoryController      list · delete remembered facts
                            └─ ChatController ─► ChatService
                                                   1. RagService.search   question → embedding → pgvector top-k (per user)
                                                   2. PromptBuilder       instructions + memories + numbered excerpts
                                                   3. Ollama chat model   generates the answer (cites [1], [2])
                                                   4. ChatRecallService     finds relevant exchanges from your OTHER chats
                                                   5. MemoryExtractionService (async) stores new facts about you
                                                   6. ChatRecallService.remember (async) indexes this exchange for later
PostgreSQL + pgvector: users, conversations, messages, memories, document metadata, vector_store (chunks + embeddings)
```

**Ingestion:** file → Apache Tika text extraction → `TokenTextSplitter` chunks → Ollama embeddings (`nomic-embed-text`)
→ pgvector. Every chunk carries `userId`, `docId`, `filename` metadata.

**Isolation:** every similarity search and delete is filtered by `userId`, so users can never retrieve each other's documents.

## Memory across chats

A new chat starts with an empty message history, so three things carry knowledge over:

1. **Facts** - after each message the model extracts facts you stated about yourself (name, skills, projects,
   goals...) into `user_memory`. They are listed in the "What I remember" panel and always added to the system prompt.
2. **Previous chat tail** - while a chat is short, the last messages of your most recent other chat are shown to the
   model, so "what did we talk about last time?" works.
3. **Semantic recall** - every exchange is embedded in pgvector (metadata `chatUserId`, never `userId`, so it can't be
   mistaken for a document). New messages retrieve the most similar exchanges from your other chats.

## Run

Prerequisites: Java 21, PostgreSQL 16 with the **pgvector** extension (the compose file below provides one), [Ollama](https://ollama.com).

```bash
cp local.properties.example local.properties   # set spring.datasource.password and jwt.secret (openssl rand -base64 48)

# Postgres + pgvector: either your own install (database `ai_chatbot`, `CREATE EXTENSION vector;`) or
docker compose up -d            # (needs DB_PASSWORD in .env; see .env.example)
ollama serve &                  # models are pulled automatically on first start, or:
ollama pull llama3.2 && ollama pull nomic-embed-text

./mvnw spring-boot:run          # then open http://localhost:8080
```

Register, upload a document in the **Knowledge base** panel, then ask about it. Answers show a **Sources** list;
untick **Docs** to chat without retrieval.

## API

| Method | Path | Notes |
|---|---|---|
| POST | `/api/auth/register`, `/api/auth/login` | login returns the JWT |
| POST | `/api/chat` | `{conversationId, message, useDocuments}` → `{response, sources[]}` |
| GET | `/api/chat/conversations`, `/api/chat/{id}/messages` | history |
| POST | `/api/documents` | multipart field `file` (pdf, docx, txt, md, html, csv; ≤ 10 MB) |
| GET / DELETE | `/api/documents`, `/api/documents/{id}` | deleting also removes the vectors |
| GET / DELETE | `/api/memory`, `/api/memory/{id}` | what the bot remembers |

## Configuration (`application.properties`)

| Property | Default | Meaning |
|---|---|---|
| `app.rag.top-k` | 4 | chunks retrieved per question |
| `app.rag.similarity-threshold` | 0.45 | minimum cosine similarity; raise if answers cite irrelevant text |
| `app.rag.chunk-size` | 500 | tokens per chunk |
| `spring.ai.vectorstore.pgvector.dimensions` | 768 | **must equal the embedding model's size** (mxbai-embed-large = 1024). Changing model = drop the `vector_store` table and re-upload |
| `app.memory.enabled` | true | background memory extraction |
| `app.recall.enabled` | true | index and retrieve exchanges from your other chats |
| `app.recall.top-k` / `similarity-threshold` | 3 / 0.5 | how many past exchanges, how similar they must be |
| `spring.ai.ollama.chat.options.num-ctx` | 8192 | context window; lower it if RAM is tight |

Secrets (`spring.datasource.password`, `jwt.secret`) live in `local.properties`, which is gitignored.

## Known limitations

- Scanned/image-only PDFs have no text layer and are rejected (needs OCR).
- Retrieval uses the current message only, so a vague follow-up ("and the second one?") may retrieve poorly.
- Sources are shown live but not stored with past messages.
- Memory extraction quality depends on the small local model; review the "What I remember" panel. A bigger model
  (`CHAT_MODEL=llama3.1:8b`) extracts noticeably better. Raw model output is logged at DEBUG level.
- Chats you had BEFORE this version are not recalled semantically (only new exchanges are indexed), but the
  previous-chat tail still works for them.
- The memory/recall changes were reviewed and their pure-Java parts tested, but the whole project was not
  compiled against Spring AI 2.x where they were written. If an import or method name differs, send the error.

## Tests

`./mvnw test` runs `PromptBuilderTest` and `MemoryFactParserTest` (no database or Ollama needed).
