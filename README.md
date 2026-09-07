# MealFlex Backend

MealFlex B2B yemek aboneliği platformunun Spring Boot API uygulaması.

## Gereksinimler

- Java 21
- Maven 3.9+
- PostgreSQL 16

## Yerel çalıştırma

İlk çalıştırmada `.env.example` dosyasını `.env` adıyla kopyalayın ve yerel
PostgreSQL parolanızı yazın. `.env` Git'e eklenmez. Ardından aşağıdaki komutu
çalıştırın:

    mvn spring-boot:run -Dspring-boot.run.profiles=dev

Alternatif olarak `DB_URL`, `DB_USERNAME` ve `DB_PASSWORD` değerleri ortam
değişkeni olarak da verilebilir. Ortam değişkenleri `.env` içindeki değerlerin
üzerine yazar.

API http://localhost:9090/api, Swagger arayüzü
http://localhost:9090/api/swagger-ui.html adresinde çalışır.

## Kalite kontrolleri

    mvn verify

Test paketi ve JaCoCo kalite kapısı çalışır; gerçek ödeme/SMS sağlayıcısına
istek gönderildiği veya üretim veritabanının doğrulandığı anlamına gelmez.

Teslimat saati ayarları V42 Flyway geçişiyle `store_delivery_slots` tablosunda
tutulur. Satıcı GET/PUT `/api/v1/seller/stores/{storeId}/delivery-slots`
üzerinden kendi mağazasının 15 dakikalık saatlerini yönetir. Müşteri
`GET /api/v1/stores/{storeId}/delivery-times?startDate=YYYY-MM-DD&endDate=YYYY-MM-DD`
ile tüm hizmet günlerine uygun ortak saatleri alır; abonelik oluştururken
aynı uygunluk backend tarafından yeniden doğrulanır. Bu ayar mevcut
teslimat kayıtlarının saatlerini geriye dönük değiştirmez.

Frontend ayrı MealFlex-FE reposunda tutulur. İki uygulama HTTP API sözleşmesi
üzerinden haberleşir.

## Finans regresyonları ve PostgreSQL

PowerShell ile `./scripts/Run-PostgresTests.ps1` komutu, yerel PostgreSQL'de
ayrı `mealflex_qa_<tarih_saat>` veritabanı oluşturur. Bağlantı parolası çıktı
olarak yazılmaz; mevcut uygulama veritabanı değiştirilmez. QA veritabanı
inceleme için korunur. PostgreSQL 15/psql yolu bulunamazsa PATH'teki psql kullanılır.
Test gerçek PostgreSQL/Flyway/JPA kullanır; ödeme sağlayıcısı mock'tur ve gerçek
para çekmez. Normal `mvn verify` koşusunda bu opt-in test atlanır.

V43, yeni tahsilatların teslimat bazındaki dağılımını `payment_allocations`
tablosunda tutar. Tahsilat öncesi kişi azaltımı ilgili haftadan düşülür;
tahsilat sonrası azaltım öğün bakiyesine döner. Kısmi iadeler hakedişi azaltır.

Önemli: V43 öncesi ödeme kayıtları otomatik olarak tahmin edilip dağıtılmaz.
`scripts/legacy-finance-report.sql` uzlaştırılması gereken kayıtları salt okunur
raporlar. Bu kayıtlar için otomatik azaltım/iade güvenli şekilde engellenir ve
hakediş üretilmez. Canlıya geçişte orijinal tahsilat/iade dökümleriyle doğrulanmış
eşleme tamamlanmalıdır; mevcut ödeme verilerini silmek çözüm değildir.
