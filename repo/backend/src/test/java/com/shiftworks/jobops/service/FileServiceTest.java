package com.shiftworks.jobops.service;

import com.shiftworks.jobops.entity.FileAttachment;
import com.shiftworks.jobops.entity.User;
import com.shiftworks.jobops.enums.AttachmentEntityType;
import com.shiftworks.jobops.enums.FileStatus;
import com.shiftworks.jobops.enums.UserRole;
import com.shiftworks.jobops.exception.BusinessException;
import com.shiftworks.jobops.repository.AppealRepository;
import com.shiftworks.jobops.repository.FileAttachmentRepository;
import com.shiftworks.jobops.repository.TicketRepository;
import com.shiftworks.jobops.repository.UserRepository;
import com.shiftworks.jobops.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @Mock private FileAttachmentRepository fileAttachmentRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private AppealRepository appealRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;
    @InjectMocks private FileService fileService;

    private AuthenticatedUser adminUser() {
        return new AuthenticatedUser(1L, "admin", UserRole.ADMIN);
    }

    private User mockUser() {
        User u = new User();
        u.setId(1L);
        u.setUsername("admin");
        return u;
    }

    @Test
    void validPdfUploadAccepted() throws Exception {
        byte[] pdfContent = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34, 0x20, 0x74, 0x65, 0x73, 0x74};
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", pdfContent);
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser()));
        when(fileAttachmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        fileService.upload(file, AttachmentEntityType.CLAIM, 1L, adminUser());

        ArgumentCaptor<FileAttachment> captor = ArgumentCaptor.forClass(FileAttachment.class);
        verify(fileAttachmentRepository).save(captor.capture());
        assertEquals(FileStatus.ACTIVE, captor.getValue().getStatus());
        verify(auditService).log(eq(1L), eq("FILE_UPLOAD"), eq("FileAttachment"), any(), isNull(), isNotNull());
    }

    @Test
    void oversizedFileRejected() {
        byte[] bigContent = new byte[11 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile("file", "big.pdf", "application/pdf", bigContent);

        BusinessException ex = assertThrows(BusinessException.class,
            () -> fileService.upload(file, AttachmentEntityType.CLAIM, 1L, adminUser()));
        assertTrue(ex.getMessage().contains("10 MB"));
    }

    @Test
    void invalidExtensionRejected() {
        MockMultipartFile file = new MockMultipartFile("file", "virus.exe", "application/octet-stream", new byte[]{1, 2, 3});

        BusinessException ex = assertThrows(BusinessException.class,
            () -> fileService.upload(file, AttachmentEntityType.CLAIM, 1L, adminUser()));
        assertTrue(ex.getMessage().contains("File type not allowed"));
    }

    @Test
    void magicByteMismatchQuarantines() throws Exception {
        byte[] jpegBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0};
        MockMultipartFile file = new MockMultipartFile("file", "fake.pdf", "application/pdf", jpegBytes);
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser()));
        when(fileAttachmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BusinessException ex = assertThrows(BusinessException.class,
            () -> fileService.upload(file, AttachmentEntityType.CLAIM, 1L, adminUser()));
        assertEquals(422, ex.getStatus().value());
    }

    @Test
    void checksumGenerated() throws Exception {
        byte[] pdfContent = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34, 0x20, 0x74, 0x65, 0x73, 0x74};
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", pdfContent);
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser()));
        when(fileAttachmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        fileService.upload(file, AttachmentEntityType.CLAIM, 1L, adminUser());

        ArgumentCaptor<FileAttachment> captor = ArgumentCaptor.forClass(FileAttachment.class);
        verify(fileAttachmentRepository).save(captor.capture());
        assertNotNull(captor.getValue().getChecksum());
        assertFalse(captor.getValue().getChecksum().isEmpty());
    }
}
