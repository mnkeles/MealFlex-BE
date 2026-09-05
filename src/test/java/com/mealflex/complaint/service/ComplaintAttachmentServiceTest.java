package com.mealflex.complaint.service;

import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.complaint.entity.Complaint;
import com.mealflex.complaint.entity.ComplaintAttachment;
import com.mealflex.complaint.repository.ComplaintAttachmentRepository;
import com.mealflex.complaint.repository.ComplaintRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplaintAttachmentServiceTest {
    @Mock ComplaintRepository complaints;
    @Mock ComplaintAttachmentRepository attachments;
    @InjectMocks ComplaintAttachmentService service;

    @Test
    void adminCannotLoadAnAttachmentThroughAnotherComplaintsUrl() {
        Complaint ownComplaint = Complaint.builder().build(); ownComplaint.setId(12L);
        Complaint otherComplaint = Complaint.builder().build(); otherComplaint.setId(13L);
        ComplaintAttachment attachment = ComplaintAttachment.builder().complaint(otherComplaint).storageName("secret.pdf").fileName("secret.pdf").contentType("application/pdf").fileSize(1L).build(); attachment.setId(99L);
        when(attachments.findById(99L)).thenReturn(Optional.of(attachment));

        assertThrows(ResourceNotFoundException.class, () -> service.loadForAdmin(12L, 99L));
    }
}
