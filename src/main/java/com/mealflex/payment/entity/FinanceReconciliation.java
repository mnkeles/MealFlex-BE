package com.mealflex.payment.entity;

import com.mealflex.common.entity.BaseEntity;
import com.mealflex.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity @Table(name = "finance_reconciliations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FinanceReconciliation extends BaseEntity {
    @Column(name="reconciliation_date", nullable=false, unique=true) private LocalDate reconciliationDate;
    @Column(name="provider_collected_amount", precision=12, scale=2) private BigDecimal providerCollectedAmount;
    @Column(name="ledger_collected_amount", nullable=false, precision=12, scale=2) private BigDecimal ledgerCollectedAmount;
    @Column(name="paid_payout_amount", nullable=false, precision=12, scale=2) private BigDecimal paidPayoutAmount;
    @Column(name="discrepancy_amount", precision=12, scale=2) private BigDecimal discrepancyAmount;
    @Column(nullable=false, length=30) private String status;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="assigned_admin_id") private User assignedAdmin;
    @Column(name="resolution_note", length=1000) private String resolutionNote;
    private Instant resolvedAt;
}
