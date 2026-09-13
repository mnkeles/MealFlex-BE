# Staging performans kabul testi

`staging-readonly.js` yalnız GET istekleri gönderir; abonelik, ödeme, onay veya
teslimat verisini değiştirmez. Varsayılan yük 10 sanal kullanıcıyla iki dakikadır.
Kabul eşikleri hata oranı `%1` altı, `p95 < 500 ms`, `p99 < 1 sn` ve kontrollerin
`%99` üzeri başarılı olmasıdır.

Testi staging sunucusundan farklı bir makinede çalıştırın. Önce hedefi ortam
değişkenine yazın; token değerlerini komut satırına eklemeyin:

    export BASE_URL=https://staging.example.com
    export CUSTOMER_TOKEN=...
    export SELLER_TOKEN=...
    export STORE_ID=1

Kurulu k6 ile:

    k6 run performance/staging-readonly.js

Docker ile k6 2.1.0 kullanırken repository kökünde:

    docker run --rm -i \
      -e BASE_URL -e CUSTOMER_TOKEN -e SELLER_TOKEN -e STORE_ID \
      -v "$PWD/MealFlex-BE/performance:/scripts:ro" \
      grafana/k6:2.1.0 run /scripts/staging-readonly.js

İlk koşu 10 kullanıcıyla yapılır. Başarılıysa `PUBLIC_VUS`, `CUSTOMER_VUS`,
`SELLER_VUS` ve `DURATION` kontrollü artırılır. Staging veritabanı boyutu üretime
yakın değilse sonuç kapasite kanıtı olarak değil, yalnız regresyon tabanı olarak
kaydedilir. Yazma/ödeme webhook yükleri ayrı ve sıfırlanabilir test verisi ile,
iyzico sandbox kabulü sırasında yapılmalıdır.
