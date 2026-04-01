package com.shiftworks.jobops.repository;

import com.shiftworks.jobops.entity.FileAttachment;
import com.shiftworks.jobops.enums.AttachmentEntityType;
import com.shiftworks.jobops.enums.FileStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FileAttachmentRepository extends JpaRepository<FileAttachment, Long> {

    List<FileAttachment> findByEntityTypeAndEntityId(AttachmentEntityType entityType, Long entityId);

    List<FileAttachment> findByStatus(FileStatus status);
}
