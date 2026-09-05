package com.mealflex.platform.entity;
import com.mealflex.common.entity.BaseEntity; import jakarta.persistence.*; import lombok.*; import java.time.*;
@Entity @Table(name="automation_tasks") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder public class AutomationTask extends BaseEntity { private String taskType; @Column(columnDefinition="TEXT") private String payload; private String status; private int attempts; private Instant runAfter; private String lastError; }
