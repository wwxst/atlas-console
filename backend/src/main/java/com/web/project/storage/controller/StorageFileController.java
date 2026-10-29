package com.web.project.storage.controller;

import com.web.project.storage.service.StorageService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class StorageFileController {
    private final StorageService service;

    @GetMapping("/files/{id}")
    public ResponseEntity<?> read(@PathVariable Long id, HttpServletRequest request) {
        var content = service.content(id);
        if (content == null) { return ResponseEntity.notFound().header("X-Content-Type-Options", "nosniff").build(); }
        if (content.redirect() != null) {
            return ResponseEntity.status(302).location(content.redirect()).header("X-Content-Type-Options", "nosniff").build();
        }
        return ResponseEntity.ok().header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(content.file().getContentType()))
                .contentLength(content.file().getSizeBytes()).body("HEAD".equals(request.getMethod()) ? null : content.resource());
    }
}
