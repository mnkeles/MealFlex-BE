package com.mealflex.campaign.repository;

import com.mealflex.campaign.entity.CampaignRedemption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CampaignRedemptionRepository extends JpaRepository<CampaignRedemption,Long> {
    long countByCampaignIdAndCustomerId(Long campaignId,Long customerId);

    @Query("SELECT r.customer.id, COUNT(r.id) FROM CampaignRedemption r GROUP BY r.customer.id HAVING COUNT(r.id) >= 5")
    List<Object[]> findCustomersWithExcessiveUsage();
}
