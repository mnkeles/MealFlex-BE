package com.mealflex.payment.service;

import com.mealflex.address.entity.Address;
import com.mealflex.delivery.entity.SubscriptionDelivery;
import com.mealflex.menu.entity.Menu;
import com.mealflex.notification.service.NotificationEventService;
import com.mealflex.payment.entity.*;
import com.mealflex.payment.provider.PaymentProvider;
import com.mealflex.platform.service.PlatformSettingService;
import com.mealflex.seller.entity.SellerProfile;
import com.mealflex.store.entity.Store;
import com.mealflex.store.entity.StoreStatus;
import com.mealflex.store.service.SellerStoreAccessService;
import com.mealflex.subscription.entity.*;
import com.mealflex.user.entity.Role;
import com.mealflex.user.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql=false, properties={"spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false", "logging.level.org.hibernate.SQL=WARN"})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Import({PaymentService.class, MealBalanceService.class, SellerPayoutService.class,
        ProviderOperationService.class, PayoutRefundAdjustmentService.class, PlatformSettingService.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
@EnabledIfEnvironmentVariable(named="MEALFLEX_QA_DB_URL", matches="jdbc:postgresql://localhost:5432/mealflex_qa_[0-9_]+")
class PostgresFinanceTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("MEALFLEX_QA_DB_URL"));
        properties.add("spring.datasource.username", () -> System.getenv("MEALFLEX_QA_DB_USER"));
        properties.add("spring.datasource.password", () -> System.getenv("MEALFLEX_QA_DB_PASSWORD"));
    }
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactions;
    @Autowired PaymentService payments;
    @Autowired SellerPayoutService payouts;
    @MockBean PaymentProvider provider;
    @MockBean SellerStoreAccessService storeAccess;
    @MockBean NotificationEventService notifications;

    @Autowired javax.sql.DataSource dataSource;

    @Test void concurrentWebhookInsertIsAtomic() throws Exception {
        String eventId = "qa-event-" + java.util.UUID.randomUUID();
        TransactionTemplate tx = new TransactionTemplate(transactions);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try {
            Callable<Boolean> insert = () -> {
                ready.countDown(); start.await(10, TimeUnit.SECONDS);
                return tx.execute(status -> payments.acceptVerifiedWebhook("MOCK", eventId, "payment.succeeded", "{}"));
            };
            Future<Boolean> first = workers.submit(insert), second = workers.submit(insert);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            assertThat(java.util.Set.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally { workers.shutdownNow(); }
        tx.executeWithoutResult(status -> assertThat(em.createQuery(
                "select count(e) from PaymentWebhookEvent e where e.providerEventId=:event", Long.class)
                .setParameter("event", eventId).getSingleResult()).isEqualTo(1L));
    }

    @Test void upgradeInvalidatesAutomaticMatchesButPreservesReviewedRecords() {
        String schema = "qa_upgrade_" + java.util.UUID.randomUUID().toString().replace("-", "");
        org.flywaydb.core.Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration-legacy").target("43").load().migrate();
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO " + schema + ".finance_reconciliations "
                + "(reconciliation_date, provider_collected_amount, ledger_collected_amount, discrepancy_amount, status) "
                + "VALUES ('2026-09-01',100,100,0,'MATCHED')");
        jdbc.update("INSERT INTO " + schema + ".finance_reconciliations "
                + "(reconciliation_date, provider_collected_amount, ledger_collected_amount, discrepancy_amount, status, resolution_note, resolved_at) "
                + "VALUES ('2026-09-02',90,100,-10,'RESOLVED','Reviewed evidence','2026-09-03T12:00:00Z')");

        var upgrade = org.flywaydb.core.Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration-legacy").load();
        assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(22);
        var automatic = jdbc.queryForMap("SELECT * FROM " + schema + ".finance_reconciliations WHERE reconciliation_date='2026-09-01'");
        assertThat(automatic.get("status")).isEqualTo("PROVIDER_UNAVAILABLE");
        assertThat(automatic.get("provider_collected_amount")).isNull();
        assertThat(automatic.get("discrepancy_amount")).isNull();
        assertThat((BigDecimal) automatic.get("ledger_collected_amount")).isEqualByComparingTo("100");
        var reviewed = jdbc.queryForMap("SELECT * FROM " + schema + ".finance_reconciliations WHERE reconciliation_date='2026-09-02'");
        assertThat(reviewed.get("status")).isEqualTo("RESOLVED");
        assertThat(reviewed.get("resolution_note")).isEqualTo("Reviewed evidence");
        assertThat((BigDecimal) reviewed.get("provider_collected_amount")).isEqualByComparingTo("90");
        assertThat(reviewed.get("resolved_at")).isNotNull();
        assertThat(upgrade.migrate().migrationsExecuted).isZero();
    }

    @Test void concurrentWeeklyChargeCreatesOnePaymentAndFiveAttributions() throws Exception {
        when(provider.name()).thenReturn("MOCK");
        when(provider.charge(anyString(), any(), anyString(), anyString()))
                .thenReturn(new PaymentProvider.ChargeResult(true,"qa-payment","qa-request","00",null));
        TransactionTemplate tx = new TransactionTemplate(transactions);
        LocalDate monday = LocalDate.of(2026, 9, 7);
        Long id = tx.execute(status -> {
            String suffix = java.util.UUID.randomUUID().toString().substring(0, 8);
            User customer = User.builder().email("qa-finance-customer-" + suffix + "@mealflex.test")
                    .password("qa-only").firstName("QA").lastName("Customer").role(Role.CUSTOMER)
                    .emailVerified(true).active(true).build();
            User seller = User.builder().email("qa-finance-seller-" + suffix + "@mealflex.test")
                    .password("qa-only").firstName("QA").lastName("Seller").role(Role.SELLER)
                    .emailVerified(true).active(true).build();
            em.persist(customer);
            em.persist(seller);
            SellerProfile sellerProfile = SellerProfile.builder().user(seller).companyTitle("QA Finance Ltd.")
                    .taxNumber("1234567890").taxOffice("QA VD").authorizedPerson("QA Seller")
                    .phone("5550000000").build();
            em.persist(sellerProfile);
            Store store = Store.builder().seller(sellerProfile).name("QA Finance Kitchen " + suffix)
                    .minPersonCount(5).maxPersonCount(50).dailyCapacity(100)
                    .latitude(new BigDecimal("39.95")).longitude(new BigDecimal("32.80"))
                    .status(StoreStatus.ACTIVE).build();
            em.persist(store);
            Address address = Address.builder().user(customer).title("QA").city("Ankara").district("Yenimahalle")
                    .latitude(new BigDecimal("39.95")).longitude(new BigDecimal("32.80")).build(); em.persist(address);
            Menu menu = Menu.builder().store(store).name("QA menu").pricePerPerson(BigDecimal.TEN).build(); em.persist(menu);
            PaymentMethod method = PaymentMethod.builder().customer(customer).provider("MOCK").providerToken("qa-only")
                    .brand("QA").lastFour("0000").expiryMonth(12).expiryYear(2030).build(); em.persist(method);
            Subscription s = Subscription.builder().customer(customer).store(store).menu(menu).address(address).paymentMethod(method)
                    .personCount(10).pricePerPerson(BigDecimal.TEN).serviceDayCount(5).totalAmount(new BigDecimal("500.00"))
                    .startDate(monday).endDate(monday.plusDays(4)).deliveryTime(LocalTime.NOON).status(SubscriptionStatus.ACTIVE).build(); em.persist(s);
            for(int i=0;i<5;i++) em.persist(SubscriptionDelivery.builder().subscription(s).menu(menu).address(address)
                    .deliveryDate(monday.plusDays(i)).deliveryTime(LocalTime.NOON).personCount(10).build());
            return s.getId();
        });
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try {
            Callable<Long> charge = () -> {
                ready.countDown(); start.await(10,TimeUnit.SECONDS);
                return tx.execute(status -> payments.chargeForCalendarWeek(em.find(Subscription.class,id),monday).getId());
            };
            Future<Long> first=workers.submit(charge), second=workers.submit(charge);
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue(); start.countDown();
            assertThat(first.get(40,TimeUnit.SECONDS)).isEqualTo(second.get(40,TimeUnit.SECONDS));
        } finally { workers.shutdownNow(); }
        verify(provider,times(1)).charge(anyString(),eq(new BigDecimal("500.00")),eq("TRY"),anyString());
        tx.executeWithoutResult(status -> {
            assertThat(em.createQuery("select count(a) from PaymentAllocation a",Long.class).getSingleResult()).isEqualTo(5L);
            assertThat(em.createQuery("select sum(a.amount) from PaymentAllocation a",BigDecimal.class).getSingleResult()).isEqualByComparingTo("500.00");
        });
        when(provider.refund(anyString(),any(),anyString(),anyString()))
                .thenReturn(new PaymentProvider.RefundResult(true,"qa-refund","00",null));
        Long firstDeliveryId = tx.execute(status -> em.createQuery(
                "select d.id from SubscriptionDelivery d where d.subscription.id=:id order by d.deliveryDate", Long.class)
                .setParameter("id", id).setMaxResults(1).getSingleResult());
        ExecutorService refundWorkers = Executors.newFixedThreadPool(2);
        CountDownLatch refundReady = new CountDownLatch(2), refundStart = new CountDownLatch(1);
        try {
            Callable<BigDecimal> reduce = () -> {
                refundReady.countDown(); refundStart.await(10, TimeUnit.SECONDS);
                return tx.execute(status -> payments.creditPaidReduction(em.find(Subscription.class, id), firstDeliveryId,
                        new BigDecimal("20.00"), 1L, "qa-reduction"));
            };
            Future<BigDecimal> first = refundWorkers.submit(reduce), second = refundWorkers.submit(reduce);
            assertThat(refundReady.await(10, TimeUnit.SECONDS)).isTrue(); refundStart.countDown();
            assertThat(first.get(40, TimeUnit.SECONDS)).isEqualByComparingTo("20.00");
            assertThat(second.get(40, TimeUnit.SECONDS)).isEqualByComparingTo("20.00");
        } finally { refundWorkers.shutdownNow(); }
        tx.executeWithoutResult(status -> assertThat(em.createQuery(
                "select count(t) from MealBalanceTransaction t where t.referenceKey like 'refund-balance-qa-reduction-%'", Long.class)
                .getSingleResult()).isEqualTo(1L));
        tx.executeWithoutResult(status -> {
            Subscription s=em.find(Subscription.class,id);
            var days=em.createQuery("select d from SubscriptionDelivery d where d.subscription.id=:id order by d.deliveryDate",SubscriptionDelivery.class)
                    .setParameter("id",id).getResultList();
            payments.refundForDeliveryChange(s,days.get(4).getId(),new BigDecimal("100.00"),s.getCustomer().getId(),"QA skip");
            days.get(4).setStatus(com.mealflex.delivery.entity.DeliveryStatus.SKIPPED);
            for (int i=0;i<4;i++) { days.get(i).setStatus(com.mealflex.delivery.entity.DeliveryStatus.DELIVERED); days.get(i).setDeliveredAt(Instant.now()); }
        });
        ExecutorService payoutWorkers=Executors.newFixedThreadPool(2);
        try {
            Runnable schedule=() -> tx.executeWithoutResult(status -> {
                var d=em.createQuery("select d from SubscriptionDelivery d where d.subscription.id=:id order by d.deliveryDate",SubscriptionDelivery.class)
                        .setParameter("id",id).setMaxResults(1).getSingleResult();
                payouts.scheduleAfterFinalWeeklyDelivery(d);
            });
            Future<?> one=payoutWorkers.submit(schedule),two=payoutWorkers.submit(schedule);
            one.get(40,TimeUnit.SECONDS); two.get(40,TimeUnit.SECONDS);
        } finally { payoutWorkers.shutdownNow(); }
        tx.executeWithoutResult(status -> {
            assertThat(em.createQuery("select count(i) from SellerPayoutItem i",Long.class).getSingleResult()).isEqualTo(1L);
            assertThat(em.createQuery("select sum(i.netAmount) from SellerPayoutItem i",BigDecimal.class).getSingleResult()).isEqualByComparingTo("334.40");
            assertThat(em.createQuery("select sum(a.availableAmount) from MealBalanceAccount a",BigDecimal.class).getSingleResult()).isEqualByComparingTo("20.00");
        });
    }
}
