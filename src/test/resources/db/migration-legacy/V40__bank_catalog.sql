CREATE TABLE bank_catalog (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL UNIQUE,
    legal_name VARCHAR(255) NOT NULL,
    bank_type VARCHAR(30) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO bank_catalog (name, legal_name, bank_type) VALUES
    ('Akbank', 'Akbank T.A.Ş.', 'MEVDUAT'),
    ('Albaraka Türk Katılım', 'Albaraka Türk Katılım Bankası A.Ş.', 'KATILIM'),
    ('Alternatif Bank', 'Alternatifbank A.Ş.', 'MEVDUAT'),
    ('Anadolubank', 'Anadolubank A.Ş.', 'MEVDUAT'),
    ('Burgan Bank', 'Burgan Bank A.Ş.', 'MEVDUAT'),
    ('DenizBank', 'Denizbank A.Ş.', 'MEVDUAT'),
    ('Emlak Katılım', 'Türkiye Emlak Katılım Bankası A.Ş.', 'KATILIM'),
    ('Enpara Bank', 'Enpara Bank A.Ş.', 'MEVDUAT'),
    ('Fibabanka', 'Fibabanka A.Ş.', 'MEVDUAT'),
    ('Garanti BBVA', 'Türkiye Garanti Bankası A.Ş.', 'MEVDUAT'),
    ('Halkbank', 'Türkiye Halk Bankası A.Ş.', 'MEVDUAT'),
    ('HSBC', 'HSBC Bank A.Ş.', 'MEVDUAT'),
    ('ING', 'ING Bank A.Ş.', 'MEVDUAT'),
    ('İş Bankası', 'Türkiye İş Bankası A.Ş.', 'MEVDUAT'),
    ('Kuveyt Türk', 'Kuveyt Türk Katılım Bankası A.Ş.', 'KATILIM'),
    ('Odeabank', 'Odea Bank A.Ş.', 'MEVDUAT'),
    ('QNB', 'QNB Bank A.Ş.', 'MEVDUAT'),
    ('Şekerbank', 'Şekerbank T.A.Ş.', 'MEVDUAT'),
    ('TEB', 'Türk Ekonomi Bankası A.Ş.', 'MEVDUAT'),
    ('Türkiye Finans Katılım', 'Türkiye Finans Katılım Bankası A.Ş.', 'KATILIM'),
    ('Vakıf Katılım', 'Vakıf Katılım Bankası A.Ş.', 'KATILIM'),
    ('VakıfBank', 'Türkiye Vakıflar Bankası T.A.O.', 'MEVDUAT'),
    ('Yapı Kredi', 'Yapı ve Kredi Bankası A.Ş.', 'MEVDUAT'),
    ('Ziraat Bankası', 'Türkiye Cumhuriyeti Ziraat Bankası A.Ş.', 'MEVDUAT'),
    ('Ziraat Katılım', 'Ziraat Katılım Bankası A.Ş.', 'KATILIM');
