package com.mealflex.campaign.repository;
import com.mealflex.campaign.entity.CampaignRedemption; import org.springframework.data.jpa.repository.JpaRepository;
public interface CampaignRedemptionRepository extends JpaRepository<CampaignRedemption,Long>{ long countByCampaignIdAndCustomerId(Long campaignId,Long customerId); }
