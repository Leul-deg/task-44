package com.shiftworks.jobops.controller;

import com.shiftworks.jobops.dto.FileAttachmentResponse;
import com.shiftworks.jobops.entity.FileAttachment;
import com.shiftworks.jobops.enums.AttachmentEntityType;
import com.shiftworks.jobops.security.AuthenticatedUser;
import com.shiftworks.jobops.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private static final Map<String, MediaType> MEDIA_TYPES = Map.of(
        "pdf", MediaType.APPLICATION_PDF,
        "jpg", MediaType.IMAGE_JPEG,
        "jpeg", MediaType.IMAGE_JPEG,
        "png", MediaType.IMAGE_PNG
    );

    private final FileService fileService;

    @PostMapping("/upload")
    public FileAttachmentResponse upload(@RequestParam("file") MultipartFile file,
                                         @RequestParam("entityType") AttachmentEntityType entityType,
                                         @RequestParam("entityId") Long entityId,
                                         Authentication authentication) {
        return fileService.upload(file, entityType, entityId, (AuthenticatedUser) authentication.getPrincipal());
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id,
                                           @RequestParam(value = "export", defaultValue = "false") boolean export,
                                           Authentication authentication) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        FileAttachment attachment = fileService.getAttachment(id);
        byte[] content = fileService.download(id, export, user);
        MediaType mediaType = MEDIA_TYPES.getOrDefault(
            attachment.getFileType().toLowerCase(), MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
            .contentType(mediaType)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + attachment.getOriginalFilename() + "\"")
            .body(content);
    }

    @GetMapping("/entity/{entityType}/{entityId}")
    public List<FileAttachmentResponse> listByEntity(@PathVariable AttachmentEntityType entityType,
                                                     @PathVariable Long entityId,
                                                     Authentication authentication) {
        return fileService.listByEntity(entityType, entityId, (AuthenticatedUser) authentication.getPrincipal());
    }

    @GetMapping("/quarantined")
    @PreAuthorize("hasRole('ADMIN')")
    public List<FileAttachmentResponse> listQuarantined() {
        return fileService.listQuarantined();
    }

    @PutMapping("/{id}/release")
    @PreAuthorize("hasRole('ADMIN')")
    public void release(@PathVariable Long id, Authentication authentication) {
        fileService.release(id, (AuthenticatedUser) authentication.getPrincipal());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id, Authentication authentication) {
        fileService.delete(id, (AuthenticatedUser) authentication.getPrincipal());
    }
}
