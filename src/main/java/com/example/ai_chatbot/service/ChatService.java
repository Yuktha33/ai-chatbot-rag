package com.example.ai_chatbot.service;

import com.example.ai_chatbot.dto.ChatResponse;
import com.example.ai_chatbot.entity.ChatMessage;
import com.example.ai_chatbot.entity.Conversation;
import com.example.ai_chatbot.entity.User;
import com.example.ai_chatbot.repository.ConversationRepository;
import com.example.ai_chatbot.repository.UserRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ChatService {

    private static final int MAX_HISTORY = 20;

    /** While a chat is this short, the end of the user's previous chat is shown to the model too. */
    private static final int SHORT_CHAT = 6;
    private static final int TAIL_MESSAGES = 6;
    private static final int TAIL_CLIP = 300;

    private final ChatClient chatClient;
    private final ChatMessageService chatMessageService;
    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;
    private final UserMemoryService userMemoryService;
    private final RagService ragService;
    private final MemoryExtractionService memoryExtractionService;
    private final ChatRecallService chatRecallService;

    public ChatService(ChatClient.Builder builder,
                       ChatMessageService chatMessageService,
                       ConversationRepository conversationRepository,
                       UserRepository userRepository,
                       UserMemoryService userMemoryService,
                       RagService ragService,
                       MemoryExtractionService memoryExtractionService,
                       ChatRecallService chatRecallService) {
        this.chatClient = builder.build();
        this.chatMessageService = chatMessageService;
        this.conversationRepository = conversationRepository;
        this.userRepository = userRepository;
        this.userMemoryService = userMemoryService;
        this.ragService = ragService;
        this.memoryExtractionService = memoryExtractionService;
        this.chatRecallService = chatRecallService;
    }

    public User user(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
    }

    private Conversation owned(User user, String conversationId, boolean create) {
        var existing = conversationRepository.findByConversationId(conversationId);
        if (existing.isPresent()) {
            if (!existing.get().getUser().getId().equals(user.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your conversation");
            }
            return existing.get();
        }
        if (!create) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found");
        }
        return conversationRepository.save(new Conversation(conversationId, user));
    }

    // ------------------------------------------------------------------ chat
    public ChatResponse chat(String username, String conversationId, String message, boolean useDocuments) {
        User user = user(username);
        owned(user, conversationId, true);

        // 1. RETRIEVE: relevant chunks from this user's documents
        List<Document> chunks = useDocuments ? ragService.search(user, message) : List.of();

        List<ChatMessage> history = chatMessageService.getConversation(conversationId);
        if (history.size() > MAX_HISTORY) {
            history = history.subList(history.size() - MAX_HISTORY, history.size());
        }

        // 2. RECALL: what happened in this user's OTHER chats
        List<String> pastChats = new ArrayList<>();
        if (history.size() < SHORT_CHAT) {
            String tail = previousConversationTail(user, conversationId);
            if (tail != null) {
                pastChats.add(tail);
            }
        }
        for (Document d : chatRecallService.recall(user, conversationId, message)) {
            if (d.getText() != null && !d.getText().isBlank()) {
                pastChats.add(d.getText());
            }
        }

        // 3. AUGMENT: system prompt = instructions + long-term memory + earlier chats + retrieved excerpts
        String system = PromptBuilder.build(userMemoryService.getMemories(user), chunks, pastChats);

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(system)); // Message object: braces in documents are not template syntax
        for (ChatMessage m : history) {
            messages.add("user".equals(m.getRole()) ? new UserMessage(m.getMessage()) : new AssistantMessage(m.getMessage()));
        }
        messages.add(new UserMessage(message));

        // 4. GENERATE
        String response = chatClient.prompt().messages(messages).call().content();

        chatMessageService.saveMessage(conversationId, message, "user");
        chatMessageService.saveMessage(conversationId, response, "assistant");

        // 5. Learn in the background: long-term facts + searchable chat history
        memoryExtractionService.extractAndStore(user, message);
        chatRecallService.remember(user, conversationId, message, response);

        return new ChatResponse(response, toSources(chunks));
    }

    /** The last few messages of the user's most recent OTHER conversation, or null if there is none. */
    private String previousConversationTail(User user, String currentConversationId) {
        for (Conversation c : conversationRepository.findByUserOrderByIdDesc(user)) {
            if (c.getConversationId().equals(currentConversationId)) {
                continue;
            }
            List<ChatMessage> msgs = chatMessageService.getConversation(c.getConversationId());
            if (msgs.isEmpty()) {
                continue;
            }
            List<ChatMessage> tail = msgs.subList(Math.max(0, msgs.size() - TAIL_MESSAGES), msgs.size());
            StringBuilder sb = new StringBuilder("Most recent previous conversation (last messages):\n");
            for (ChatMessage m : tail) {
                sb.append("user".equals(m.getRole()) ? "User: " : "Assistant: ")
                        .append(clip(m.getMessage(), TAIL_CLIP)).append('\n');
            }
            return sb.toString();
        }
        return null;
    }

    private static String clip(String s, int max) {
        String t = s == null ? "" : s.replaceAll("\\s+", " ").trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }

    private static List<ChatResponse.SourceRef> toSources(List<Document> chunks) {
        List<ChatResponse.SourceRef> out = new ArrayList<>();
        int i = 1;
        for (Document d : chunks) {
            String text = d.getText() == null ? "" : d.getText().replaceAll("\\s+", " ").trim();
            String snippet = text.length() > 220 ? text.substring(0, 220) + "…" : text;
            out.add(new ChatResponse.SourceRef(i++, String.valueOf(d.getMetadata().getOrDefault("filename", "document")), snippet));
        }
        return out;
    }

    // --------------------------------------------------------- conversations
    public List<Map<String, String>> listConversations(String username) {
        User user = user(username);
        List<Map<String, String>> result = new ArrayList<>();
        for (Conversation c : conversationRepository.findByUserOrderByIdDesc(user)) {
            List<ChatMessage> msgs = chatMessageService.getConversation(c.getConversationId());
            if (msgs.isEmpty()) {
                continue;
            }
            String first = msgs.get(0).getMessage();
            result.add(Map.of("id", c.getConversationId(), "title", first.length() > 40 ? first.substring(0, 40) + "…" : first));
        }
        return result;
    }

    public List<Map<String, String>> getMessages(String username, String conversationId) {
        owned(user(username), conversationId, false);
        return chatMessageService.getConversation(conversationId).stream()
                .map(m -> Map.of("role", m.getRole(), "message", m.getMessage()))
                .toList();
    }
}
