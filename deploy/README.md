# Tek sunuculu staging dağıtımı

Bu paket gerçek ödeme almayan, PostGIS kullanmayan ilk staging provası içindir.
Backend `prod` profiliyle; ödeme `MOCK`, mesafe hesabı `HAVERSINE` olarak çalışır.
Caddy, geçerli alan adı için TLS sertifikasını otomatik alır ve yeniler. PostgreSQL,
backend ve frontend doğrudan internete açılmaz.

## Ön koşullar

- Docker Engine ve Docker Compose v2
- En az 2 GB kullanılabilir bellek
- Sunucuya yönlendirilmiş bir alan adı (`A` ve varsa `AAAA` kaydı)
- Güvenlik duvarında dışarıya açık TCP `80` ve `443`; HTTP/3 için isteğe bağlı UDP `443`
- Sertifika alınabilmesi için sunucudan internete çıkış

## Kurulum

1. `deploy/.env.staging.example` dosyasını `deploy/.env.staging` olarak kopyalayın.
2. `APP_VERSION`, `PUBLIC_HOST`, `PUBLIC_BASE_URL`, `TLS_EMAIL` ve rastgele sırları gerçek staging
   değerleriyle değiştirin. `PUBLIC_HOST` içine `https://` yazmayın. `.env` ayrıştırma
   sorunlarından kaçınmak için sırları 32 baytlık hex değer olarak üretin ve her alan
   için komutu ayrı çalıştırın:

       openssl rand -hex 32

   Dosyayı yalnız sahibi okuyabilsin:

       chmod 600 .env.staging
3. Secret değerlerini ekrana yazdırmadan yapılandırmayı doğrulayın:

       bash validate-staging-env.sh .env.staging

   Windows üzerinde aynı kontrol:

       pwsh -File Test-StagingConfiguration.ps1 -Path .env.staging

4. Sunucuda backend ve frontend repolarını aşağıdaki kardeş dizinler halinde tutun:

       /opt/mealflex/MealFlex-BE
       /opt/mealflex/MealFlex-FE

   `/opt/mealflex` ve altındaki dosyaların sahibi `mealflex` kullanıcısı olmalıdır.

5. `MealFlex-BE/deploy` klasöründe ilk sürümü yayınlayın. Sürüm adı aynı imajların
   tekrar bulunabilmesi için benzersiz olmalıdır:

       sh deploy-release.sh .env.staging 0.1.0-staging

6. Durumu kontrol edin:

       docker compose --env-file .env.staging -f docker-compose.staging.yml ps
       curl https://staging.example.com/api/actuator/health

   İlk sertifikanın durumunu görmek için:

       docker compose --env-file .env.staging -f docker-compose.staging.yml logs edge

7. Migration durumunu kontrol edin:

       docker compose --env-file .env.staging -f docker-compose.staging.yml exec postgres \
         psql -U mealflex_app -d mealflex_staging -c "select version, description, success from flyway_schema_history order by installed_rank desc limit 10;"

8. Yayın sonrası otomatik kabul paketi dağıtım betiği tarafından zaten çalıştırılır;
   gerektiğinde ayrıca tekrarlayın:

       bash verify-deployment.sh .env.staging

   Bu kontrol dört konteynerin health durumunu, HTTP→HTTPS yönlendirmesini, geçerli
   TLS bağlantısını, readiness'i, HSTS'yi, kapalı Swagger'ı, anonim erişim sınırını ve
   başarısız Flyway kaydı bulunmadığını denetler.

## Güncelleme

Yeni kodu doğrulanmış Git tag/commit üzerinden iki repoya aldıktan sonra:

    sh deploy-release.sh .env.staging 0.1.1-staging

Betik mevcut ortam varsa önce yedek alır; iki imajı sürümlü oluşturur, servisleri
kaldırır, yayın kabulünü çalıştırır ve yalnız başarılı sürümü `.env.staging` içinde
kalıcılaştırır. Backend veya frontend çalışma ağacında commit edilmemiş dosya varsa
hangi kodun yayınlandığı belirsizleşmesin diye işlem reddedilir.
Her yayın etiketi benzersiz olmalıdır; mevcut `APP_VERSION` tekrar kullanılamaz.
Kabul başarısız olursa önceki backend/frontend imajları mevcutsa betik uygulama
sürümünü otomatik geri alır ve geri alınan sürümü de doğrular. Flyway şeması otomatik
geri alınmaz; otomatik dönüş de doğrulanamazsa işlem kritik hata ile sonlanır.

## Uygulama sürümünü geri alma

Hedef imajların daha önce bu sunucuda üretilmiş olması gerekir:

    sh rollback-staging.sh .env.staging 0.1.0-staging ROLLBACK_STAGING

Bu işlem önce güvenlik yedeği alır ve yalnız backend/frontend imajlarını değiştirir.
Flyway migration'ları geri alınmaz. Eski uygulama yeni şemayla uyumlu değilse imaj
geri alma kullanılmamalı; ileri düzeltme sürümü hazırlanmalıdır.

## Yedekleme

Veritabanı ile `/app/uploads` aynı geri yükleme noktasında birlikte alınır:

    bash backup.sh .env.staging ./backups

Komut tutarlı bir nokta elde etmek için uygulama trafiğini kısa süre durdurur; hata
olsa bile servisleri yeniden kaldırır. Bir PostgreSQL custom dump, yükleme arşivi ve
ikisini doğrulayan SHA-256 manifesti üretir. Bu üç dosyayı aynı ad kümesiyle,
şifrelenmiş biçimde ve sunucu dışında saklayın. Sunucu üzerindeki `deploy/backups`
geçici kopyadır ve Git'e girmez.

## Geri yükleme

Bu işlem mevcut staging veritabanı ve yüklemelerini değiştirir. Komut önce
`backups/pre-restore` içine ek bir güvenlik yedeği alır, uygulama trafiğini durdurur,
manifest bütünlüğünü ve PostgreSQL custom dump yapısını doğrular; ancak bundan sonra
güvenlik yedeğini alıp mevcut veriyi değiştirir ve işlem sonunda servisleri yeniden
başlatır:

    bash restore.sh .env.staging \
      backups/mealflex-staging-20260912T120000Z.database.dump \
      backups/mealflex-staging-20260912T120000Z.uploads.tar.gz \
      backups/mealflex-staging-20260912T120000Z.sha256 \
      RESTORE_STAGING

Geri yükleme sonrası health, Flyway geçmişi, oturum açma, dosya görüntüleme ve temel
abonelik akışlarını doğrulayın. Üretim yedeğini ilk kez doğrudan üretime yüklemeyin;
önce izole bir ortamda tatbikat yapın.

## Periyodik iş ve izleme

`backup.sh` her gün sunucunun görev zamanlayıcısıyla çalıştırılmalıdır. Ardından
`check-staging.sh`, konteyner health durumlarını, HTTPS readiness'i, sertifikanın en az
14 gün geçerli olduğunu, disk eşiğini ve son yedeğin yaş/bütünlüğünü kontrol eder:

    bash check-staging.sh .env.staging ./backups

Başarılı çalışmada `0`, operasyon gerektiren durumda `2` döner. Cron/systemd timer veya
izleme aracınız sıfır dışı çıkışta sorumlu kişiye alarm göndermelidir. Komutların aynı
anda çalışmasını önlemek için görev zamanlayıcısında kilit (`flock`) kullanın. Örnek
başlangıç sırası: her gece yedek, beş dakika sonra kontrol. Yedeklerin sunucu dışına
aktarımı ve 14 günlük silme politikası ayrıca depolama sağlayıcısında tanımlanmalıdır.

Hazır systemd birimleri `deploy/systemd` altındadır ve `/opt/mealflex` yerleşimini
kullanır. Host üzerinde `mealflex` kullanıcısını yalnız gerekli Docker yetkisiyle
oluşturduktan sonra birimleri `/etc/systemd/system` dizinine kopyalayın, ardından:

    sudo systemctl daemon-reload
    sudo systemctl enable --now mealflex-backup.timer mealflex-health.timer
    systemctl list-timers 'mealflex-*'

İlk kurulumda servisleri elle çalıştırıp sonucu kontrol edin:

    sudo systemctl start mealflex-backup.service
    sudo systemctl start mealflex-health.service
    journalctl -u mealflex-backup.service -u mealflex-health.service --since today

Başarısız systemd işleri `mealflex-alert@.service` birimini tetikler. Alarm endpointi
hazır olduğunda yalnız root tarafından okunabilen `/etc/mealflex/ops-alert.env`
dosyasına aşağıdaki değer yazılır:

    OPS_ALERT_WEBHOOK_URL=https://alerts.example.com/hooks/mealflex

Endpoint `source`, `severity`, `service`, `occurredAt` ve `message` alanlarını içeren
JSON POST kabul etmelidir. Webhook tanımlı değilse hata journal'da kalır; başarılı
alarm teslimi varsayılmaz.

## Durdurma

    docker compose --env-file .env.staging -f docker-compose.staging.yml down

`down -v` kullanmayın; `-v` PostgreSQL ve yüklenen dosya volume'larını siler.
İlk staging doğrulamasında ayrıntılı kontrol listesi için
`../../docs/URETIM_YAYIN_KONTROL_LISTESI.md` dosyasını kullanın.
