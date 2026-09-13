package com.mealflex.payment.provider;

import com.iyzipay.Options;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;

@Configuration
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "IYZICO")
public class IyzicoConfiguration {
    @Bean
    Options iyzicoOptions(
            @Value("${app.payment.iyzico.api-key:}") String apiKey,
            @Value("${app.payment.iyzico.secret-key:}") String secretKey,
            @Value("${app.payment.iyzico.base-url:}") String baseUrl,
            @Value("${app.payment.iyzico.callback-url:}") String callbackUrl,
            @Value("${app.payment.iyzico.card-management-callback-url:}") String cardManagementCallbackUrl) {
        if (apiKey.isBlank() || secretKey.isBlank() || baseUrl.isBlank()) {
            throw new IllegalStateException("IYZICO seçildiğinde API anahtarı, secret ve base URL zorunludur.");
        }
        URI endpoint;
        try {
            endpoint = URI.create(baseUrl);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("IYZICO base URL geçersizdir.");
        }
        String host = endpoint.getHost() == null ? "" : endpoint.getHost().toLowerCase();
        if (!"https".equalsIgnoreCase(endpoint.getScheme())
                || !(host.equals("api.iyzipay.com") || host.equals("sandbox-api.iyzipay.com"))) {
            throw new IllegalStateException("IYZICO base URL yalnız resmi HTTPS iyzipay adresi olabilir.");
        }
        requireExternalHttpsCallback("IYZICO ödeme callback", callbackUrl);
        requireExternalHttpsCallback("IYZICO kart yönetimi callback", cardManagementCallbackUrl);
        Options options = new Options();
        options.setApiKey(apiKey);
        options.setSecretKey(secretKey);
        options.setBaseUrl(baseUrl);
        return options;
    }

    private static void requireExternalHttpsCallback(String name, String value) {
        URI callback;
        try {
            callback = URI.create(value);
        } catch (RuntimeException exception) {
            throw new IllegalStateException(name + " adresi geçersizdir.");
        }
        String host = callback.getHost() == null ? "" : callback.getHost().toLowerCase();
        if (!"https".equalsIgnoreCase(callback.getScheme()) || host.isBlank()
                || host.equals("localhost") || host.equals("127.0.0.1") || host.equals("::1")) {
            throw new IllegalStateException(name + " internetten erişilebilir bir HTTPS adresi olmalıdır.");
        }
    }
}
