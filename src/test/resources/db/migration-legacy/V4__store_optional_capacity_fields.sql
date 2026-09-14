-- =============================================
-- V4: min_person_count ve daily_capacity opsiyonel yap
-- =============================================

ALTER TABLE stores ALTER COLUMN min_person_count DROP NOT NULL;
ALTER TABLE stores ALTER COLUMN daily_capacity DROP NOT NULL;
