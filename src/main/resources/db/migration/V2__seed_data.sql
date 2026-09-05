-- =============================================
-- MealFlex Seed Data
-- =============================================

-- Tüm kullanıcıların şifresi: test12345
-- Hash, Spring BCryptPasswordEncoder ile üretilmiştir.
-- DB sıfırlandığında bu hash otomatik uygulanır.

INSERT INTO users (email, password, first_name, last_name, phone, role, email_verified, active)
VALUES
  ('customer@mealflex.com', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'Ahmet', 'Yılmaz', '5551112233', 'CUSTOMER', true, true),
  ('seller@mealflex.com',   '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'Fatma', 'Kaya',   '5552223344', 'SELLER',   true, true),
  ('admin@mealflex.com',    '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'Admin', 'User',   '5553334455', 'ADMIN',    true, true);

-- Customer profile
INSERT INTO customer_profiles (user_id, company_name, tax_number, tax_office, invoice_address)
SELECT id, 'Ahmet Yılmaz A.Ş.', '1234567890', 'Kadıköy VD', 'Kadıköy, İstanbul'
FROM users WHERE email = 'customer@mealflex.com';

-- Seller profile
INSERT INTO seller_profiles (user_id, company_title, tax_number, tax_office, authorized_person, phone, iban)
SELECT id, 'Fatma Catering Ltd.', '9876543210', 'Beşiktaş VD', 'Fatma Kaya', '5552223344', 'TR330006100519786457841326'
FROM users WHERE email = 'seller@mealflex.com';

-- Store for seller
INSERT INTO stores (seller_id, name, description, min_person_count, max_person_count, daily_capacity, status, rating, review_count)
SELECT sp.id, 'Fatma''nın Mutfağı', 'Ev yapımı yemekler, günlük taze hazırlanan menüler.', 5, 50, 100, 'ACTIVE', 4.5, 0
FROM seller_profiles sp
JOIN users u ON sp.user_id = u.id
WHERE u.email = 'seller@mealflex.com';
