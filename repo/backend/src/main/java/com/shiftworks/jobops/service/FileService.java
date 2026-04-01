package com.shiftworks.jobops.service;

import com.shiftworks.jobops.dto.FileAttachmentResponse;
import com.shiftworks.jobops.entity.Appeal;
import com.shiftworks.jobops.entity.Claim;
import com.shiftworks.jobops.entity.FileAttachment;
import com.shiftworks.jobops.entity.Ticket;
import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.enums.AttachmentEntityType;
import com.shiftworks.jobops.enums.FileStatus;
import com.shiftworks.jobops.enums.UserRole;
import com.shiftworks.jobops.exception.BusinessException;
import com.shiftworks.jobops.repository.AppealRepository;
import com.shiftworks.jobops.repository.ClaimRepository;
import com.shiftworks.jobops.repository.FileAttachmentRepository;
import com.shiftworks.jobops.repository.TicketRepository;
import com.shiftworks.jobops.repository.UserRepository;
import com.shiftworks.jobops.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "jpg", "jpeg", "png");

    private static final byte[] MAGIC_PDF = {0x25, 0x50, 0x44, 0x46};
    private static final byte[] MAGIC_JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] MAGIC_PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private final FileAttachmentRepository fileAttachmentRepository;
    private final FileStorageService fileStorageService;
    private final ClaimRepository claimRepository;
    private final AppealRepository appealRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    @Transactional
    public FileAttachmentResponse upload(MultipartFile file, AttachmentEntityType entityType,
                                         Long entityId, AuthenticatedUser authUser) {
        verifyEntityAccess(entityType, entityId, authUser);

        String originalFilename = file.getOriginalFilename();
        String extension = extractExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "File type not allowed");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "File exceeds 10 MB limit");
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read uploaded file", e);
        }

        boolean magicMatch = verifyMagicBytes(content, extension);
        String checksum = computeSha256(content);
        FileStatus status = magicMatch ? FileStatus.ACTIVE : FileStatus.QUARANTINED;

        String storedFilename = UUID.randomUUID() + "." + extension;
        String storedPath = entityType.name() + "/" + entityId + "/" + storedFilename;
        fileStorageService.save(storedPath, content);

        User uploader = userRepository.findById(authUser.id())
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "user: Not found"));

        FileAttachment attachment = new FileAttachment();
        attachment.setEntityType(entityType);
        attachment.setEntityId(entityId);
        attachment.setOriginalFilename(originalFilename);
        attachment.setStoredPath(storedPath);
        attachment.setFileType(extension);
        attachment.setFileSize(file.getSize());
        attachment.setChecksum(checksum);
        attachment.setStatus(status);
        attachment.setUploadedBy(uploader);
        attachment.setCreatedAt(Instant.now());
        fileAttachmentRepository.save(attachment);

        auditService.log(authUser.id(), "FILE_UPLOAD", "FileAttachment", attachment.getId(), null, attachment);

        if (status == FileStatus.QUARANTINED) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,
                "File failed validation and has been quarantined for admin review");
        }

        return toResponse(attachment);
    }

    public byte[] download(Long id, boolean export, AuthenticatedUser authUser) {
        FileAttachment attachment = fileAttachmentRepository.findById(id)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "file: Not found"));

        if (attachment.getStatus() == FileStatus.QUARANTINED && authUser.role() != UserRole.ADMIN) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "file: Quarantined files are admin-only");
        }

        boolean isUploader = attachment.getUploadedBy().getId().equals(authUser.id());
        boolean isEntityOwner = isEntityOwner(attachment.getEntityType(), attachment.getEntityId(), authUser);
        boolean isAdmin = authUser.role() == UserRole.ADMIN;
        if (!isUploader && !isEntityOwner && !isAdmin) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "file: Access denied");
        }

        byte[] content = fileStorageService.read(attachment.getStoredPath());

        if (export && "pdf".equalsIgnoreCase(attachment.getFileType())) {
            content = applyWatermark(content, authUser.username());
        }

        return content;
    }

    public FileAttachment getAttachment(Long id) {
        return fileAttachmentRepository.findById(id)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "file: Not found"));
    }

    public List<FileAttachmentResponse> listByEntity(AttachmentEntityType entityType, Long entityId,
                                                     AuthenticatedUser authUser) {
        verifyEntityAccess(entityType, entityId, authUser);
        return fileAttachmentRepository.findByEntityTypeAndEntityId(entityType, entityId)
            .stream()
            .filter(f -> f.getStatus() == FileStatus.ACTIVE || authUser.role() == UserRole.ADMIN)
            .map(this::toResponse)
            .toList();
    }

    public List<FileAttachmentResponse> listQuarantined() {
        return fileAttachmentRepository.findByStatus(FileStatus.QUARANTINED)
            .stream().map(this::toResponse).toList();
    }

    @Transactional
    public void release(Long id, AuthenticatedUser authUser) {
        FileAttachment attachment = fileAttachmentRepository.findById(id)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "file: Not found"));
        if (attachment.getStatus() != FileStatus.QUARANTINED) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "file: Not quarantined");
        }
        attachment.setStatus(FileStatus.ACTIVE);
        fileAttachmentRepository.save(attachment);
        auditService.log(authUser.id(), "FILE_RELEASE", "FileAttachment", id, null, attachment);
    }

    @Transactional
    public void delete(Long id, AuthenticatedUser authUser) {
        FileAttachment attachment = fileAttachmentRepository.findById(id)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "file: Not found"));
        fileStorageService.delete(attachment.getStoredPath());
        fileAttachmentRepository.delete(attachment);
        auditService.log(authUser.id(), "FILE_DELETE", "FileAttachment", id, attachment, null);
    }

    private void verifyEntityAccess(AttachmentEntityType entityType, Long entityId, AuthenticatedUser authUser) {
        if (authUser.role() == UserRole.ADMIN) return;
        boolean hasAccess = isEntityOwner(entityType, entityId, authUser);
        if (!hasAccess) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "file: Access denied to entity");
        }
    }

    private boolean isEntityOwner(AttachmentEntityType entityType, Long entityId, AuthenticatedUser authUser) {
        if (authUser.role() == UserRole.ADMIN) return true;
        return switch (entityType) {
            case CLAIM -> claimRepository.findById(entityId)
                .map(c -> c.getClaimant().getId().equals(authUser.id()))
                .orElse(false);
            case APPEAL -> appealRepository.findById(entityId)
                .map(a -> a.getEmployer().getId().equals(authUser.id()))
                .orElse(false);
            case TICKET -> ticketRepository.findById(entityId)
                .map(t -> t.getReporter().getId().equals(authUser.id()))
                .orElse(false);
        };
    }

    private boolean verifyMagicBytes(byte[] content, String extension) {
        if (content.length < 8) return false;
        byte[] magic;
        String ext = extension.toLowerCase();
        switch (ext) {
            case "pdf" -> magic = MAGIC_PDF;
            case "jpg", "jpeg" -> magic = MAGIC_JPEG;
            case "png" -> magic = MAGIC_PNG;
            default -> {
                return false;
            }
        }
        return Arrays.equals(content, 0, magic.length, magic, 0, magic.length);
    }

    private String computeSha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1);
    }

    private byte[] applyWatermark(byte[] pdfBytes, String username) {
        String watermarkText = String.format("CONFIDENTIAL — %s — %s",
            username,
            LocalDateTime.now(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        );
        try (PDDocument doc = Loader.loadPDF(pdfBytes); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            for (PDPage page : doc.getPages()) {
                try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
                    gs.setNonStrokingAlphaConstant(0.3f);
                    cs.setGraphicsStateParameters(gs);
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 40);
                    cs.setNonStrokingColor(0.7f, 0.7f, 0.7f);
                    float pw = page.getMediaBox().getWidth();
                    float ph = page.getMediaBox().getHeight();
                    cs.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(45), pw / 4, ph / 4));
                    cs.showText(watermarkText);
                    cs.endText();
                }
            }
            doc.save(bos);
            return bos.toByteArray();
        } catch (IOException ex) {
            log.warn("Failed to apply watermark", ex);
            return pdfBytes;
        }
    }

    private FileAttachmentResponse toResponse(FileAttachment f) {
        return new FileAttachmentResponse(
            f.getId(),
            f.getEntityType().name(),
            f.getEntityId(),
            f.getOriginalFilename(),
            f.getFileType(),
            f.getFileSize(),
            f.getChecksum(),
            f.getStatus().name(),
            f.getUploadedBy().getUsername(),
            f.getCreatedAt()
        );
    }
}
