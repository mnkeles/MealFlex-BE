package com.mealflex.campaign.repository;
import com.mealflex.campaign.entity.Campaign; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface CampaignRepository extends JpaRepository<Campaign,Long>{ Optional<Campaign> findByCodeIgnoreCaseAndActiveTrueAndDeletedAtIsNull(String code); List<Campaign> findByStoreIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long storeId); }
