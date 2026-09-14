package com.mealflex.subscription.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mealflex.address.entity.Address;
import com.mealflex.menu.entity.Menu;
import com.mealflex.payment.entity.PaymentMethod;
import com.mealflex.payment.entity.Payment;
import com.mealflex.payment.service.PaymentService;
import com.mealflex.security.UserPrincipal;
import com.mealflex.seller.entity.SellerProfile;
import com.mealflex.store.entity.BusinessHour;
import com.mealflex.store.entity.ServiceArea;
import com.mealflex.store.entity.Store;
import com.mealflex.store.entity.StoreDeliverySlot;
import com.mealflex.store.entity.StoreDistanceRule;
import com.mealflex.store.entity.StoreStatus;
import com.mealflex.subscription.entity.Subscription;
import com.mealflex.subscription.service.SubscriptionDatePolicy;
import com.mealflex.user.entity.Role;
import com.mealflex.user.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Browser route stubs are deliberately not used here. The request traverses the real
 * security filter chain, controllers, services, Flyway schema and PostgreSQL. The
 * configured MOCK payment provider remains intentional until sandbox credentials exist.
 */
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.task.scheduling.enabled=false",
        "logging.level.org.hibernate.SQL=WARN"
})
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfEnvironmentVariable(named = "MEALFLEX_QA_DB_URL",
        matches = "jdbc:postgresql://localhost:5432/mealflex_qa_[0-9_]+")
class PostgresMarketplaceApiTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("MEALFLEX_QA_DB_URL"));
        properties.add("spring.datasource.username", () -> System.getenv("MEALFLEX_QA_DB_USER"));
        properties.add("spring.datasource.password", () -> System.getenv("MEALFLEX_QA_DB_PASSWORD"));
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired EntityManager entityManager;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired PaymentService paymentService;

    @Test
    void customerRequestToSellerDeliveryAndPayoutUsesRealApiAndDatabase() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        Scenario scenario = tx.execute(status -> createScenario());
        UserPrincipal customer = tx.execute(status -> principal(scenario.customerId()));
        UserPrincipal seller = tx.execute(status -> principal(scenario.sellerId()));

        String createBody = objectMapper.createObjectNode()
                .put("storeId", scenario.storeId())
                .put("menuId", scenario.menuId())
                .put("addressId", scenario.addressId())
                .put("paymentMethodId", scenario.paymentMethodId())
                .put("commercialTermsAccepted", true)
                .put("personCount", 5)
                .put("deliveryTime", "12:00")
                .put("startDate", scenario.startDate().toString())
                .put("endDate", scenario.startDate().plusDays(4).toString())
                .toString();

        String created = mockMvc.perform(post("/v1/subscriptions")
                        .with(as(customer))
                        .header("Idempotency-Key", "qa-api-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.storeName").value("QA API Mutfağı"))
                .andReturn().getResponse().getContentAsString();
        long subscriptionId = objectMapper.readTree(created).path("id").asLong();

        mockMvc.perform(post("/v1/seller/subscriptions/{id}/approve", subscriptionId)
                        .with(as(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        String detail = mockMvc.perform(get("/v1/subscriptions/{id}", subscriptionId)
                        .with(as(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveries.length()").value(5))
                .andReturn().getResponse().getContentAsString();
        JsonNode first = objectMapper.readTree(detail).path("deliveries").get(0);
        long deliveryId = first.path("id").asLong();
        String deliveryCode = first.path("deliveryCode").asText();
        java.util.List<Long> remainingDeliveryIds = new java.util.ArrayList<>();
        objectMapper.readTree(detail).path("deliveries").forEach(delivery -> {
            if (delivery.path("id").asLong() != deliveryId) {
                remainingDeliveryIds.add(delivery.path("id").asLong());
            }
        });
        assertThat(deliveryCode).matches("\\d{4}");

        tx.executeWithoutResult(status -> {
            Subscription subscription = entityManager.find(Subscription.class, subscriptionId);
            paymentService.chargeForCalendarWeek(subscription,
                    scenario.startDate().with(DayOfWeek.MONDAY));
        });
        for (Long skippedDeliveryId : remainingDeliveryIds) {
            mockMvc.perform(post("/v1/subscriptions/{id}/deliveries/{deliveryId}/skip",
                            subscriptionId, skippedDeliveryId)
                            .with(as(customer))
                            .param("reason", "QA uçtan uca akış testi"))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/v1/seller/stores/{storeId}/deliveries/{id}/in-transit", scenario.storeId(), deliveryId)
                        .with(as(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"));
        mockMvc.perform(post("/v1/seller/stores/{storeId}/deliveries/{id}/deliver", scenario.storeId(), deliveryId)
                        .with(as(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode().put("deliveryCode", deliveryCode).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.proofPhotoUrl").doesNotExist());

        tx.executeWithoutResult(status -> {
            Payment payment = entityManager.createQuery(
                    "select p from Payment p where p.subscription.id=:id", Payment.class)
                    .setParameter("id", subscriptionId).getSingleResult();
            assertThat(payment.getStatus()).isEqualTo(com.mealflex.payment.entity.PaymentStatus.PARTIALLY_REFUNDED);
            assertThat(payment.getGrossAmount()).isEqualByComparingTo("625.00");
            assertThat(payment.getRefundedAmount()).isEqualByComparingTo("500.00");
            assertThat(entityManager.createQuery(
                    "select count(i) from SellerPayoutItem i where i.payment.subscription.id=:id", Long.class)
                    .setParameter("id", subscriptionId).getSingleResult()).isEqualTo(1L);
        });
    }

    private Scenario createScenario() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User customer = User.builder().email("qa-customer-" + suffix + "@mealflex.test")
                .password("qa-only").firstName("QA").lastName("Customer").role(Role.CUSTOMER)
                .emailVerified(true).active(true).build();
        User seller = User.builder().email("qa-seller-" + suffix + "@mealflex.test")
                .password("qa-only").firstName("QA").lastName("Seller").role(Role.SELLER)
                .emailVerified(true).active(true).build();
        entityManager.persist(customer);
        entityManager.persist(seller);
        SellerProfile sellerProfile = SellerProfile.builder().user(seller).companyTitle("QA Catering Ltd.")
                .taxNumber("1234567890").taxOffice("QA VD").authorizedPerson("QA Seller")
                .phone("5550000000").build();
        entityManager.persist(sellerProfile);
        Store store = Store.builder().seller(sellerProfile).name("QA API Mutfağı")
                .description("Gerçek API akış testi mağazası").minPersonCount(5).maxPersonCount(50)
                .dailyCapacity(100).latitude(new BigDecimal("39.9334000"))
                .longitude(new BigDecimal("32.8597000")).status(StoreStatus.ACTIVE).build();
        entityManager.persist(store);

        String district = "QA-" + UUID.randomUUID().toString().substring(0, 8);
        entityManager.persist(ServiceArea.builder().store(store).city("Ankara").district(district).build());
        entityManager.persist(StoreDistanceRule.builder().store(store).distanceKm(15).minPersonCount(5).build());
        entityManager.persist(StoreDeliverySlot.builder().store(store).deliveryTime(LocalTime.NOON).build());
        for (DayOfWeek day : DayOfWeek.values()) {
            entityManager.persist(BusinessHour.builder().store(store).dayOfWeek(day).open(true)
                    .openTime(LocalTime.of(8, 0)).closeTime(LocalTime.of(20, 0)).build());
        }
        Menu menu = Menu.builder().store(store).name("QA API Menü")
                .description("Gerçek API akış testi")
                .pricePerPerson(new BigDecimal("25.00"))
                .priceEffectiveFrom(SubscriptionDatePolicy.today()).active(true).build();
        entityManager.persist(menu);
        Address address = Address.builder().user(customer).title("QA API")
                .city("Ankara").district(district).fullAddress("QA API adresi")
                .latitude(store.getLatitude()).longitude(store.getLongitude()).defaultAddress(false).build();
        entityManager.persist(address);
        PaymentMethod method = PaymentMethod.builder().customer(customer).provider("MOCK")
                .providerToken("qa-api-token").cardHolderName("QA Customer").brand("QA")
                .lastFour("4242").expiryMonth(12).expiryYear(2030).active(true).build();
        entityManager.persist(method);
        entityManager.flush();

        LocalDate start = SubscriptionDatePolicy.today().plusDays(2);
        return new Scenario(customer.getId(), seller.getId(), store.getId(), menu.getId(), address.getId(),
                method.getId(), start);
    }

    private UserPrincipal principal(Long userId) {
        return UserPrincipal.from(entityManager.find(User.class, userId));
    }

    private static RequestPostProcessor as(UserPrincipal principal) {
        return authentication(new UsernamePasswordAuthenticationToken(
                principal, principal.getPassword(), principal.getAuthorities()));
    }

    private record Scenario(Long customerId, Long sellerId, Long storeId, Long menuId,
                            Long addressId, Long paymentMethodId, LocalDate startDate) { }
}
