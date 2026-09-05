# MealFlex Backend

MealFlex B2B yemek aboneliği platformunun Spring Boot API uygulaması.

## Gereksinimler

- Java 21
- Maven 3.9+
- PostgreSQL 16

## Yerel çalıştırma

Veritabanı bağlantı bilgilerini DB_URL, DB_USERNAME ve DB_PASSWORD ortam
değişkenleriyle tanımlayın. Ardından aşağıdaki komutu çalıştırın:

    mvn spring-boot:run -Dspring-boot.run.profiles=dev

API http://localhost:9090/api, Swagger arayüzü
http://localhost:9090/api/swagger-ui.html adresinde çalışır.

## Kalite kontrolleri

    mvn verify

Frontend ayrı MealFlex-FE reposunda tutulur. İki uygulama HTTP API sözleşmesi
üzerinden haberleşir.
