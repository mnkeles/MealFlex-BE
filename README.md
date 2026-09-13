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

## Staging dağıtımı ve smoke testi

Tek Linux VPS için Caddy otomatik HTTPS, PostgreSQL, backend ve frontend Compose
paketi `deploy/` altındadır. Kurulum, yedekleme, geri yükleme, izleme ve yayın kabul
adımları için `deploy/README.md` dosyasını izleyin.

Yayın sonrası yalnızca `GET` istekleri yapan tekrar kullanılabilir smoke testi:

    ./scripts/Invoke-ApiSmokeTest.ps1 -BaseUrl https://staging.example.com/api -SwaggerExpectedStatus 404 -ApiDocsExpectedStatus 404

Betik health durumunu, Swagger arayüzünü, OpenAPI dokümanını ve korumalı bir
endpointin anonim isteği reddettiğini doğrular. Yetkili bir salt-okunur çağrıyı
da doğrulamak için token'ı komut geçmişine yazmadan ortam değişkeniyle iletin:

    ./scripts/Invoke-ApiSmokeTest.ps1 -BaseUrl https://staging.example.com/api -AccessToken $env:MEALFLEX_SMOKE_TOKEN -SwaggerExpectedStatus 404 -ApiDocsExpectedStatus 404

Staging ortamı HTTPS kullanmıyorsa `-AllowInsecureHttp` yalnızca onaylı iç ağ
ortamları için açıkça verilmelidir. Betik istek/yanıt gövdelerini ve token'ı
çıktıya yazmaz. Canlı profilde Swagger kapalıysa beklenen yanıtı açıkça verin:

    ./scripts/Invoke-ApiSmokeTest.ps1 -BaseUrl https://api.example.com/api -SwaggerExpectedStatus 404 -ApiDocsExpectedStatus 404

## Production ortam ön kontrolü

Sunucuda veya secret store enjeksiyonundan sonra, sırları yazdırmadan zorunlu
ayarları denetlemek için önce `.env.production.example` şablonunu doldurun ve
çalışma ortamına aktarın. Ardından çalıştırın:

    ./scripts/Test-ProductionEnvironment.ps1

Betik PostgreSQL bağlantı türünü, HTTPS originlerini, minimum secret uzunluğunu,
iyzico anahtar/callback ayarlarını ve PostGIS moduyla Flyway konumunun uyumunu
denetler. Yerel veya staging provasında MOCK adaptera açıkça izin vermek için:

    ./scripts/Test-ProductionEnvironment.ps1 -AllowMockPayment

Canlı ödeme için `PAYMENT_PROVIDER=IYZICO` kullanılır. İlk haftalık ödeme iyzico'nun
barındırdığı Checkout Form üzerinde alınır; kart numarası ve CVV MealFlex'e gelmez.
Sonraki değişken tutarlı haftalık tahsilatlar iyzico'nun döndürdüğü kart tokenlarıyla
yapılır. Kayıtlı kart ekleme/yönetme iyzico Card Management Page'e yönlenir; bunun
için ayrı `IYZICO_CARD_MANAGEMENT_CALLBACK_URL` tanımlanır. Sandbox kabul adımları
`../docs/IYZICO_CANLIYA_ALMA.md` dosyasındadır.

Teslimat saati ayarları V42 Flyway geçişiyle `store_delivery_slots` tablosunda
tutulur. Satıcı GET/PUT `/api/v1/seller/stores/{storeId}/delivery-slots`
üzerinden kendi mağazasının 15 dakikalık saatlerini yönetir. Müşteri
`GET /api/v1/stores/{storeId}/delivery-times?startDate=YYYY-MM-DD&endDate=YYYY-MM-DD`
ile tüm hizmet günlerine uygun ortak saatleri alır; abonelik oluştururken
aynı uygunluk backend tarafından yeniden doğrulanır. Bu ayar mevcut
teslimat kayıtlarının saatlerini geriye dönük değiştirmez.

Frontend ayrı MealFlex-FE reposunda tutulur. İki uygulama HTTP API sözleşmesi
üzerinden haberleşir.

## PostGIS mesafe motoru

Varsayılan `LOCATION_DISTANCE_ENGINE=HAVERSINE` mevcut Java mesafe hesabını
korur. PostGIS kurulu staging/üretim ortamında spatial şemayı etkinleştirmek için:

    FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/spatial-migration
    LOCATION_DISTANCE_ENGINE=SHADOW

`SHADOW` modu kullanıcıya Haversine sonucunu verirken PostGIS sonucunu metrik ve
loglarla karşılaştırır. Doğrulama tamamlandıktan sonra
`LOCATION_DISTANCE_ENGINE=POSTGIS` kullanılabilir. `POSTGIS` modu spatial şema
eksikse uygulamayı başlangıçta durdurur. Ayrıntılı dağıtım ve geri dönüş adımları
`../docs/POSTGIS_ENTEGRASYON_PLANI.md` dosyasındadır.

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
