package com.example.ai_chatbot.controller;

import com.example.ai_chatbot.dto.DocumentInfo;
import com.example.ai_chatbot.repository.UserRepository;
import com.example.ai_chatbot.service.RagService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Knowledge base management: upload, list and delete the documents the chatbot can search. */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final RagService ragService;
    private final UserRepository userRepository;

    public DocumentController(RagService ragService, UserRepository userRepository) {
        this.ragService = ragService;
        this.userRepository = userRepository;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentInfo upload(@RequestParam("file") MultipartFile file, Authentication auth) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty");
        }
        return DocumentInfo.from(ragService.ingest(user(auth), file));
    }

    @GetMapping
    public List<DocumentInfo> list(Authentication auth) {
        return ragService.list(user(auth)).stream().map(DocumentInfo::from).toList();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication auth) {
        ragService.delete(user(auth), id);
    }

    private com.example.ai_chatbot.entity.User user(Authentication auth) {
        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
    }
}
