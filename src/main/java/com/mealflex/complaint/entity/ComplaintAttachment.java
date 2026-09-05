package com.mealflex.complaint.entity;
import com.mealflex.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="complaint_attachments") @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ComplaintAttachment extends BaseEntity {
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="complaint_id",nullable=false) private Complaint complaint;
 @Column(nullable=false) private String fileName; @Column(nullable=false) private String storageName;
 @Column(nullable=false) private String contentType; @Column(nullable=false) private Long fileSize;
}
