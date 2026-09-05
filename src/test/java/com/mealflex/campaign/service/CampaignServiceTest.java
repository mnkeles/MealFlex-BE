package com.mealflex.campaign.service;

import com.mealflex.campaign.entity.Campaign;
import com.mealflex.campaign.repository.CampaignRedemptionRepository;
import com.mealflex.campaign.repository.CampaignRepository;
import com.mealflex.common.exception.BusinessException;
import com.mealflex.menu.repository.MenuRepository;
import com.mealflex.store.entity.Store;
import com.mealflex.user.entity.User;
import com.mealflex.store.service.SellerStoreAccessService;
import com.mealflex.subscription.repository.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CampaignServiceTest {
 @Mock CampaignRepository campaigns; @Mock CampaignRedemptionRepository redemptions; @Mock MenuRepository menus; @Mock SellerStoreAccessService storeAccess; @Mock SubscriptionRepository subscriptions; @InjectMocks CampaignService service;
 @Test void platformCouponWorksAcrossStoresButSellerCouponCannotLeakToAnotherStore(){
  Campaign platform=Campaign.builder().code("PLATFORM").campaignType("FIXED").discountValue(BigDecimal.TEN).startDate(LocalDate.now().minusDays(1)).endDate(LocalDate.now().plusDays(1)).maxUsesPerCustomer(1).build(); platform.setId(1L);
  when(campaigns.findByCodeIgnoreCaseAndActiveTrueAndDeletedAtIsNull("PLATFORM")).thenReturn(Optional.of(platform));
  assertThat(service.quote(9L,2L,3L,BigDecimal.valueOf(100),"PLATFORM",5,2).discount()).isEqualByComparingTo("10");
  Store other=Store.builder().name("Diğer").build(); other.setId(8L);
  Campaign seller=Campaign.builder().store(other).code("OTHER").campaignType("FIXED").discountValue(BigDecimal.TEN).startDate(LocalDate.now().minusDays(1)).endDate(LocalDate.now().plusDays(1)).maxUsesPerCustomer(1).build(); seller.setId(2L);
  when(campaigns.findByCodeIgnoreCaseAndActiveTrueAndDeletedAtIsNull("OTHER")).thenReturn(Optional.of(seller));
  assertThatThrownBy(()->service.quote(9L,2L,3L,BigDecimal.valueOf(100),"OTHER",5,2)).isInstanceOf(BusinessException.class);
 }

 @Test void couponRejectsExpiredTargetedAndExhaustedUsesWithoutMakingARedemption(){
  Campaign expired=Campaign.builder().code("EXPIRED").campaignType("FIXED").discountValue(BigDecimal.TEN).startDate(LocalDate.now().minusDays(3)).endDate(LocalDate.now().minusDays(1)).maxUsesPerCustomer(1).build(); expired.setId(3L);
  when(campaigns.findByCodeIgnoreCaseAndActiveTrueAndDeletedAtIsNull("EXPIRED")).thenReturn(Optional.of(expired));
  assertThatThrownBy(()->service.quote(9L,2L,3L,BigDecimal.valueOf(100),"EXPIRED",5,2)).isInstanceOf(BusinessException.class).hasMessageContaining("kullanım tarihi");

  User another=User.builder().email("other@example.com").password("x").firstName("Other").lastName("User").build(); another.setId(11L);
  Campaign targeted=Campaign.builder().code("TARGETED").targetCustomer(another).campaignType("FIXED").discountValue(BigDecimal.TEN).startDate(LocalDate.now().minusDays(1)).endDate(LocalDate.now().plusDays(1)).maxUsesPerCustomer(1).build(); targeted.setId(4L);
  when(campaigns.findByCodeIgnoreCaseAndActiveTrueAndDeletedAtIsNull("TARGETED")).thenReturn(Optional.of(targeted));
  assertThatThrownBy(()->service.quote(9L,2L,3L,BigDecimal.valueOf(100),"TARGETED",5,2)).isInstanceOf(BusinessException.class).hasMessageContaining("başka bir müşteriye");

  Campaign exhausted=Campaign.builder().code("ONCE").campaignType("FIXED").discountValue(BigDecimal.TEN).startDate(LocalDate.now().minusDays(1)).endDate(LocalDate.now().plusDays(1)).maxUsesPerCustomer(1).build(); exhausted.setId(5L);
  when(campaigns.findByCodeIgnoreCaseAndActiveTrueAndDeletedAtIsNull("ONCE")).thenReturn(Optional.of(exhausted)); when(redemptions.countByCampaignIdAndCustomerId(5L,9L)).thenReturn(1L);
  assertThatThrownBy(()->service.quote(9L,2L,3L,BigDecimal.valueOf(100),"ONCE",5,2)).isInstanceOf(BusinessException.class).hasMessageContaining("kullanım sınırına");
 }
}
