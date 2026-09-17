-- Ankara bölgesel kabul testleri için tekrar çalıştırılabilir hesaplar.
-- Ortak şifre: password
-- Yalnız geliştirme/test veritabanında çalıştırılmalıdır.

BEGIN;

CREATE TEMP TABLE regional_sellers (
    email text, first_name text, last_name text, phone text, district text,
    company_title text, store_name text, tax_number text,
    latitude numeric(10,7), longitude numeric(10,7)
) ON COMMIT DROP;

INSERT INTO regional_sellers VALUES
('seller.yenimahalle1@test.mealflex.local','Ayşe','Demir','5557001001','Yenimahalle','Yenimahalle Lezzet Catering Ltd.','Yenimahalle Lezzet Mutfağı','9100000001',39.9653000,32.7797000),
('seller.yenimahalle2@test.mealflex.local','Mehmet','Arslan','5557001002','Yenimahalle','Başkent Tabldot Ltd.','Başkent Catering','9100000002',39.9716000,32.7551000),
('seller.yenimahalle3@test.mealflex.local','Zeynep','Kaya','5557001003','Yenimahalle','Batıkent Sofrası Ltd.','Batıkent Sofrası','9100000003',39.9688000,32.7247000),
('seller.etimesgut1@test.mealflex.local','Emine','Şahin','5557002001','Etimesgut','Etimesgut Ev Yemekleri Ltd.','Etimesgut Ev Yemekleri','9200000001',39.9477000,32.6698000),
('seller.etimesgut2@test.mealflex.local','Burak','Yıldız','5557002002','Etimesgut','Eryaman Catering Ltd.','Eryaman Catering','9200000002',39.9860000,32.6415000),
('seller.etimesgut3@test.mealflex.local','Selin','Koç','5557002003','Etimesgut','Göksu Toplu Yemek Ltd.','Göksu Toplu Yemek','9200000003',39.9828000,32.6249000),
('seller.kecioren1@test.mealflex.local','Hasan','Aydın','5557003001','Keçiören','Keçiören Sofrası Ltd.','Keçiören Sofrası','9300000001',40.0007000,32.8650000),
('seller.kecioren2@test.mealflex.local','Elif','Çelik','5557003002','Keçiören','Kalaba Catering Ltd.','Kalaba Catering','9300000002',39.9924000,32.8536000),
('seller.kecioren3@test.mealflex.local','Murat','Öztürk','5557003003','Keçiören','Estergon Ev Yemekleri Ltd.','Estergon Ev Yemekleri','9300000003',40.0181000,32.8425000);

CREATE TEMP TABLE regional_customers (
    email text, first_name text, last_name text, phone text, district text,
    company_name text, tax_number text, latitude numeric(10,7), longitude numeric(10,7)
) ON COMMIT DROP;

INSERT INTO regional_customers VALUES
('customer.yenimahalle1@test.mealflex.local','Ali','Aksoy','5557101001','Yenimahalle','Yenimahalle Test Teknoloji A.Ş.','9400000001',39.9662000,32.7810000),
('customer.yenimahalle2@test.mealflex.local','Derya','Güneş','5557101002','Yenimahalle','Batı Ankara Test Ltd.','9400000002',39.9702000,32.7507000),
('customer.yenimahalle3@test.mealflex.local','Can','Eren','5557101003','Yenimahalle','Ostim Test Sanayi Ltd.','9400000003',39.9751000,32.7609000),
('customer.etimesgut1@test.mealflex.local','Ece','Acar','5557102001','Etimesgut','Etimesgut Test Ofisi Ltd.','9500000001',39.9491000,32.6710000),
('customer.etimesgut2@test.mealflex.local','Okan','Polat','5557102002','Etimesgut','Eryaman Test Bilişim A.Ş.','9500000002',39.9834000,32.6451000),
('customer.etimesgut3@test.mealflex.local','Nazlı','Tunç','5557102003','Etimesgut','Göksu Test Hizmet Ltd.','9500000003',39.9806000,32.6293000),
('customer.kecioren1@test.mealflex.local','Berk','Kılıç','5557103001','Keçiören','Keçiören Test Yazılım A.Ş.','9600000001',40.0015000,32.8663000),
('customer.kecioren2@test.mealflex.local','İrem','Tekin','5557103002','Keçiören','Kalaba Test Danışmanlık Ltd.','9600000002',39.9931000,32.8550000),
('customer.kecioren3@test.mealflex.local','Onur','Erdoğan','5557103003','Keçiören','Kuzey Ankara Test Ltd.','9600000003',40.0169000,32.8440000);

-- Spring BCryptPasswordEncoder ile "password" parolasının hash'i.
INSERT INTO users (email,password,first_name,last_name,phone,role,email_verified,email_verified_at,
                   terms_accepted_at,terms_version,privacy_accepted_at,privacy_version,active,created_at,updated_at)
SELECT email,'$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG',first_name,last_name,phone,
       'SELLER',true,now(),now(),'1.0',now(),'1.0',true,now(),now()
FROM regional_sellers ON CONFLICT (email) DO NOTHING;

INSERT INTO users (email,password,first_name,last_name,phone,role,email_verified,email_verified_at,
                   terms_accepted_at,terms_version,privacy_accepted_at,privacy_version,active,created_at,updated_at)
SELECT email,'$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG',first_name,last_name,phone,
       'CUSTOMER',true,now(),now(),'1.0',now(),'1.0',true,now(),now()
FROM regional_customers ON CONFLICT (email) DO NOTHING;

-- Bölgesel senaryolardaki destek taleplerini ve yönetici ekranlarını test etmek için.
INSERT INTO users (email,password,first_name,last_name,phone,role,email_verified,email_verified_at,
                   terms_accepted_at,terms_version,privacy_accepted_at,privacy_version,active,created_at,updated_at)
VALUES ('admin@test.mealflex.local','$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG',
        'Test','Yöneticisi','5557200001','ADMIN',true,now(),now(),'1.0',now(),'1.0',true,now(),now())
ON CONFLICT (email) DO NOTHING;

INSERT INTO seller_profiles (user_id,company_title,tax_number,tax_office,authorized_person,phone,bank_name,iban,created_at,updated_at)
SELECT u.id,s.company_title,s.tax_number,s.district || ' VD',s.first_name || ' ' || s.last_name,s.phone,
       'Türkiye Cumhuriyeti Ziraat Bankası', 'TR' || lpad((700000000000000000000000 + row_number() over (order by s.email))::text,24,'0'), now(),now()
FROM regional_sellers s JOIN users u ON u.email=s.email
WHERE NOT EXISTS (SELECT 1 FROM seller_profiles p WHERE p.user_id=u.id);

INSERT INTO customer_profiles (user_id,company_name,tax_number,tax_office,invoice_address,created_at,updated_at)
SELECT u.id,c.company_name,c.tax_number,c.district || ' VD',c.district || ', Ankara',now(),now()
FROM regional_customers c JOIN users u ON u.email=c.email
WHERE NOT EXISTS (SELECT 1 FROM customer_profiles p WHERE p.user_id=u.id);

INSERT INTO addresses (user_id,title,city,district,neighborhood,street,building_no,floor,apartment_no,
                       full_address,latitude,longitude,default_address,created_at,updated_at)
SELECT u.id,'İş Yeri','Ankara',c.district,'Merkez','Test Caddesi',right(c.phone,2),'1','1',
       'Test Caddesi No: ' || right(c.phone,2) || ', ' || c.district || ', Ankara',c.latitude,c.longitude,true,now(),now()
FROM regional_customers c JOIN users u ON u.email=c.email
WHERE NOT EXISTS (SELECT 1 FROM addresses a WHERE a.user_id=u.id AND a.title='İş Yeri' AND a.deleted_at IS NULL);

INSERT INTO stores (seller_id,name,description,min_person_count,max_person_count,daily_capacity,production_address,
                    status,rating,review_count,temporarily_closed,latitude,longitude,address_title,city,district,
                    neighborhood,street,building_no,change_cutoff_hours,created_at,updated_at)
SELECT p.id,s.store_name,s.district || ' bölgesinde günlük kurumsal yemek ve catering hizmeti.',3,100,250,
       'Test Caddesi, ' || s.district || ', Ankara','ACTIVE',4.5,0,false,s.latitude,s.longitude,
       'Merkez Mutfak','Ankara',s.district,'Merkez','Test Caddesi',right(s.phone,2),24,now(),now()
FROM regional_sellers s JOIN users u ON u.email=s.email JOIN seller_profiles p ON p.user_id=u.id
WHERE NOT EXISTS (SELECT 1 FROM stores st WHERE st.seller_id=p.id AND st.name=s.store_name AND st.deleted_at IS NULL);

-- Kapak ve logo için yerel test varlıkları kullanılır. Satıcının daha önce yüklediği logo korunur.
UPDATE stores st
   SET logo_url = COALESCE(
                      NULLIF(st.logo_url, '/mealflex-social-share.png'),
                      CASE s.store_name
                          WHEN 'Yenimahalle Lezzet Mutfağı' THEN '/images/test-store-logos/yenimahalle-lezzet.svg'
                          WHEN 'Başkent Catering' THEN '/images/test-store-logos/baskent-catering.svg'
                          WHEN 'Batıkent Sofrası' THEN '/images/test-store-logos/batikent-sofrasi.svg'
                      END
                  ),
       cover_image_url = COALESCE(st.cover_image_url, '/images/login-meal-hero.jpg'),
       updated_at = now()
  FROM regional_sellers s
 WHERE st.name = s.store_name
   AND st.deleted_at IS NULL;

INSERT INTO service_areas (store_id,city,district,created_at,updated_at)
SELECT st.id,'Ankara',s.district,now(),now()
FROM regional_sellers s JOIN users u ON u.email=s.email JOIN seller_profiles p ON p.user_id=u.id
JOIN stores st ON st.seller_id=p.id AND st.name=s.store_name
ON CONFLICT (store_id,city,district) DO NOTHING;

INSERT INTO business_hours (store_id,day_of_week,open,open_time,close_time,created_at,updated_at)
SELECT st.id,d.day_name,d.day_name NOT IN ('SATURDAY','SUNDAY'),
       CASE WHEN d.day_name NOT IN ('SATURDAY','SUNDAY') THEN time '08:00' END,
       CASE WHEN d.day_name NOT IN ('SATURDAY','SUNDAY') THEN time '18:00' END,now(),now()
FROM stores st JOIN regional_sellers s ON s.store_name=st.name
CROSS JOIN (VALUES ('MONDAY'),('TUESDAY'),('WEDNESDAY'),('THURSDAY'),('FRIDAY'),('SATURDAY'),('SUNDAY')) d(day_name)
ON CONFLICT (store_id,day_of_week) DO NOTHING;

INSERT INTO store_distance_rules (store_id,distance_km,min_person_count,created_at,updated_at)
SELECT st.id,15,3,now(),now() FROM stores st JOIN regional_sellers s ON s.store_name=st.name
ON CONFLICT (store_id,distance_km) DO NOTHING;

INSERT INTO store_category_labels (store_id,category)
SELECT st.id,'Ev Yemekleri' FROM stores st JOIN regional_sellers s ON s.store_name=st.name
ON CONFLICT (store_id,category) DO NOTHING;

INSERT INTO menus (store_id,name,description,price_per_person,allergen_info,active,price_effective_from,created_at,updated_at)
SELECT st.id,'Haftalık Kurumsal Menü','Bölgesel test senaryoları için aktif öğle yemeği menüsü.',175.00,
       'Gluten ve süt ürünü içerebilir.',true,current_date,now(),now()
FROM stores st JOIN regional_sellers s ON s.store_name=st.name
WHERE NOT EXISTS (SELECT 1 FROM menus m WHERE m.store_id=st.id AND m.name='Haftalık Kurumsal Menü' AND m.deleted_at IS NULL);

-- Catering firması günlük sabit menü sözü vermek yerine genel yemek çeşitlerini belirtir.
INSERT INTO menu_items (menu_id,name,description,sort_order,created_at,updated_at)
SELECT m.id,item.name,item.category,item.sort_order,now(),now()
FROM menus m
JOIN stores st ON st.id=m.store_id
JOIN regional_sellers s ON s.store_name=st.name
CROSS JOIN (VALUES
 ('Mercimek Çorbası','Çorba',1),
 ('Tarhana Çorbası','Çorba',2),
 ('Yayla Çorbası','Çorba',3),
 ('Ezogelin Çorbası','Çorba',4),
 ('Sulu Köfte','Ana yemek',5),
 ('Kuru Fasulye','Ana yemek',6),
 ('Patlıcan Musakka','Ana yemek',7),
 ('Nohut','Ana yemek',8),
 ('Taze Fasulye','Ana yemek',9),
 ('Pirinç Pilavı','Yardımcı yemek',10),
 ('Bulgur Pilavı','Yardımcı yemek',11)
) AS item(name,category,sort_order)
WHERE m.name='Haftalık Kurumsal Menü'
  AND NOT EXISTS (
      SELECT 1 FROM menu_items mi
       WHERE mi.menu_id=m.id AND mi.sort_order=item.sort_order AND mi.deleted_at IS NULL
  );

COMMIT;
