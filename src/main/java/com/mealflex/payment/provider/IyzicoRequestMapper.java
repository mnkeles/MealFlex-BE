package com.mealflex.payment.provider;

import com.iyzipay.model.*;
import com.iyzipay.request.CreateCheckoutFormInitializeRequest;
import com.iyzipay.request.CreatePaymentRequest;
import com.mealflex.address.entity.Address;
import com.mealflex.common.exception.BusinessException;
import com.mealflex.common.exception.ResourceNotFoundException;
import com.mealflex.customer.entity.CustomerProfile;
import com.mealflex.customer.repository.CustomerProfileRepository;
import com.mealflex.subscription.entity.Subscription;
import com.mealflex.subscription.repository.SubscriptionRepository;
import com.mealflex.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@RequiredArgsConstructor
public class IyzicoRequestMapper {
    private static final DateTimeFormatter IYZICO_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CustomerProfileRepository customerProfileRepository;
    private final SubscriptionRepository subscriptionRepository;

    @Value("${app.payment.iyzico.callback-url:}")
    private String callbackUrl;

    public CreateCheckoutFormInitializeRequest checkout(Subscription subscription, BigDecimal amount,
                                                         String currency, String conversationId,
                                                         String clientIp, String existingCustomerToken) {
        requireCallbackUrl();
        CreateCheckoutFormInitializeRequest request = new CreateCheckoutFormInitializeRequest();
        common(request, subscription, amount, currency, conversationId, clientIp);
        request.setCallbackUrl(callbackUrl);
        request.setForceThreeDS(1);
        request.setSubscriptionPaymentEnabled(true);
        request.setPaymentWithNewCardEnabled(true);
        if (existingCustomerToken != null && !existingCustomerToken.isBlank()) {
            request.setCardUserKey(existingCustomerToken);
        }
        return request;
    }

    public CreatePaymentRequest savedCard(PaymentProvider.ChargeCommand command) {
        if (command.subscriptionId() == null) {
            throw new BusinessException("PAYMENT_CONTEXT_REQUIRED", "iyzico tahsilatı için abonelik bilgisi zorunludur.");
        }
        if (blank(command.cardToken()) || blank(command.customerToken())) {
            throw new BusinessException("PAYMENT_METHOD_INVALID", "iyzico kart tokenı eksik veya geçersiz.");
        }
        Subscription subscription = subscriptionRepository.findById(command.subscriptionId())
                .orElseThrow(() -> new ResourceNotFoundException("Abonelik", command.subscriptionId()));
        CreatePaymentRequest request = new CreatePaymentRequest();
        common(request, subscription, command.amount(), command.currency(), command.idempotencyKey(), command.buyerIp());
        PaymentCard card = new PaymentCard();
        card.setCardToken(command.cardToken());
        card.setCardUserKey(command.customerToken());
        card.setRegisterCard(0);
        request.setPaymentCard(card);
        request.setInstallment(1);
        return request;
    }

    private void common(CreateCheckoutFormInitializeRequest request, Subscription subscription, BigDecimal amount,
                        String currency, String conversationId, String clientIp) {
        request.setLocale(Locale.TR.getValue());
        request.setConversationId(conversationId);
        request.setPrice(amount);
        request.setPaidPrice(amount);
        request.setCurrency(currency);
        request.setBasketId(basketId(subscription));
        request.setPaymentGroup(PaymentGroup.SUBSCRIPTION.name());
        request.setBuyer(buyer(subscription, clientIp));
        request.setShippingAddress(address(subscription));
        request.setBillingAddress(address(subscription));
        request.setBasketItems(List.of(basketItem(subscription, amount)));
    }

    private void common(CreatePaymentRequest request, Subscription subscription, BigDecimal amount,
                        String currency, String conversationId, String clientIp) {
        request.setLocale(Locale.TR.getValue());
        request.setConversationId(conversationId);
        request.setPrice(amount);
        request.setPaidPrice(amount);
        request.setCurrency(currency);
        request.setPaymentChannel(PaymentChannel.WEB.name());
        request.setBasketId(basketId(subscription));
        request.setPaymentGroup(PaymentGroup.SUBSCRIPTION.name());
        request.setBuyer(buyer(subscription, clientIp));
        request.setShippingAddress(address(subscription));
        request.setBillingAddress(address(subscription));
        request.setBasketItems(List.of(basketItem(subscription, amount)));
    }

    private Buyer buyer(Subscription subscription, String clientIp) {
        User user = subscription.getCustomer();
        CustomerProfile profile = customerProfileRepository.findByUserId(user.getId()).orElse(null);
        String identity = profile == null ? "" : digits(profile.getTaxNumber());
        if (identity.length() != 10 && identity.length() != 11) {
            throw new BusinessException("IYZICO_IDENTITY_REQUIRED",
                    "Ödeme için profilinizde 10 haneli vergi numarası veya 11 haneli T.C. kimlik numarası bulunmalıdır.");
        }
        String phone = normalizePhone(user.getPhone());
        if (phone.isBlank()) {
            throw new BusinessException("IYZICO_PHONE_REQUIRED", "Ödeme için profilinizde geçerli bir telefon numarası bulunmalıdır.");
        }
        Buyer buyer = new Buyer();
        buyer.setId("customer-" + user.getId());
        buyer.setName(user.getFirstName());
        buyer.setSurname(user.getLastName());
        buyer.setIdentityNumber(identity);
        buyer.setEmail(user.getEmail());
        buyer.setGsmNumber(phone);
        if (user.getCreatedAt() != null) {
            buyer.setRegistrationDate(IYZICO_DATE.format(user.getCreatedAt().atZone(ZoneOffset.UTC)));
        }
        buyer.setRegistrationAddress(addressText(subscription.getAddress()));
        buyer.setCity(subscription.getAddress().getCity());
        buyer.setCountry("Türkiye");
        buyer.setIp(blank(clientIp) ? "127.0.0.1" : clientIp);
        return buyer;
    }

    private com.iyzipay.model.Address address(Subscription subscription) {
        Address source = subscription.getAddress();
        com.iyzipay.model.Address target = new com.iyzipay.model.Address();
        target.setContactName(subscription.getCustomer().getFirstName() + " " + subscription.getCustomer().getLastName());
        target.setAddress(addressText(source));
        target.setCity(source.getCity());
        target.setCountry("Türkiye");
        return target;
    }

    private BasketItem basketItem(Subscription subscription, BigDecimal amount) {
        BasketItem item = new BasketItem();
        item.setId("subscription-" + subscription.getId());
        item.setName(subscription.getStore().getName() + " - " + subscription.getMenu().getName());
        item.setCategory1("Kurumsal yemek aboneliği");
        item.setItemType(BasketItemType.PHYSICAL.name());
        item.setPrice(amount);
        return item;
    }

    public String basketId(Subscription subscription) {
        return "subscription-" + subscription.getId();
    }

    private String addressText(Address address) {
        if (!blank(address.getFullAddress())) return address.getFullAddress().trim();
        return java.util.stream.Stream.of(address.getStreet(), address.getBuildingNo(), address.getDistrict(), address.getCity())
                .filter(value -> !blank(value)).collect(java.util.stream.Collectors.joining(", "));
    }

    private String normalizePhone(String value) {
        String digits = digits(value);
        if (digits.length() == 10 && digits.startsWith("5")) return "+90" + digits;
        if (digits.length() == 11 && digits.startsWith("05")) return "+9" + digits;
        if (digits.length() == 12 && digits.startsWith("90")) return "+" + digits;
        return "";
    }

    private void requireCallbackUrl() {
        if (blank(callbackUrl)) throw new BusinessException("IYZICO_CALLBACK_REQUIRED", "iyzico callback adresi yapılandırılmamış.");
    }

    private static String digits(String value) { return value == null ? "" : value.replaceAll("\\D", ""); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
