-- V11__refresh_region_catalog.sql
--
-- V2 seeded only 2 sigungu per sido (except Jeonbuk, which got 3), so any
-- sido other than Jeonbuk shows an incomplete sigungu list in region
-- exploration. This migration re-applies the canonical catalog through an
-- idempotent upsert (never destructive) and soft-disables any row that is no
-- longer part of the reference set instead of deleting it, so existing
-- region_analyses / farms rows that reference an old sigungu_code stay valid.
--
-- NOTE: this migration only re-affirms the rows already known to this
-- codebase (V2's 20 rows). Populating full nationwide administrative +
-- KMA-grid coverage requires sourcing an authoritative dataset (e.g. 통계청
-- 행정표준코드 + 기상청 격자 변환표); fabricating additional sigungu/kma_nx/ny
-- rows here without that source would risk silently wrong weather lookups,
-- so this migration only fixes the mechanism (upsert + soft-disable) and the
-- known-good rows. Extending real coverage is a follow-up data-sourcing task.

CREATE TEMP TABLE region_catalog_staging (
    sido_code VARCHAR(20) NOT NULL,
    sido_name VARCHAR(100) NOT NULL,
    sigungu_code VARCHAR(20) NOT NULL,
    sigungu_name VARCHAR(100) NOT NULL,
    kma_nx INT,
    kma_ny INT,
    asos_station_id VARCHAR(20)
) ON COMMIT DROP;

INSERT INTO region_catalog_staging (sido_code, sido_name, sigungu_code, sigungu_name, kma_nx, kma_ny, asos_station_id)
VALUES
  ('52', '전북특별자치도', '52180', '고창군', 52, 77, '172'),
  ('52', '전북특별자치도', '52110', '전주시', 63, 89, '146'),
  ('52', '전북특별자치도', '52140', '익산시', 60, 91, '146'),
  ('41', '경기도', '41110', '수원시', 60, 121, '119'),
  ('41', '경기도', '41460', '용인시', 64, 121, '119'),
  ('41', '경기도', '41500', '이천시', 68, 121, '203'),
  ('42', '강원특별자치도', '42150', '강릉시', 92, 131, '105'),
  ('42', '강원특별자치도', '42110', '춘천시', 73, 134, '101'),
  ('43', '충청북도', '43110', '청주시', 69, 107, '131'),
  ('43', '충청북도', '43130', '충주시', 76, 114, '127'),
  ('44', '충청남도', '44200', '논산시', 62, 99, '133'),
  ('44', '충청남도', '44150', '공주시', 63, 102, '133'),
  ('46', '전라남도', '46170', '나주시', 56, 71, '156'),
  ('46', '전라남도', '46810', '해남군', 54, 61, '261'),
  ('47', '경상북도', '47250', '상주시', 81, 107, '137'),
  ('47', '경상북도', '47110', '포항시', 102, 94, '138'),
  ('48', '경상남도', '48120', '창원시', 90, 77, '155'),
  ('48', '경상남도', '48170', '진주시', 86, 75, '192'),
  ('50', '제주특별자치도', '50110', '제주시', 52, 38, '184'),
  ('50', '제주특별자치도', '50130', '서귀포시', 52, 33, '189');

-- Upsert: insert new codes, refresh names/coordinates for existing codes,
-- and re-enable any row that was previously soft-disabled but is back in
-- the reference set.
INSERT INTO regions (sido_code, sido_name, sigungu_code, sigungu_name, kma_nx, kma_ny, asos_station_id, enabled)
SELECT sido_code, sido_name, sigungu_code, sigungu_name, kma_nx, kma_ny, asos_station_id, TRUE
FROM region_catalog_staging
ON CONFLICT (sido_code, sigungu_code) DO UPDATE SET
    sido_name = EXCLUDED.sido_name,
    sigungu_name = EXCLUDED.sigungu_name,
    kma_nx = EXCLUDED.kma_nx,
    kma_ny = EXCLUDED.kma_ny,
    asos_station_id = EXCLUDED.asos_station_id,
    enabled = TRUE,
    updated_at = CURRENT_TIMESTAMP;

-- Soft-disable rows that fell out of the reference set instead of deleting
-- them, so historical region_analyses/farms rows referencing them stay valid.
UPDATE regions r
SET enabled = FALSE, updated_at = CURRENT_TIMESTAMP
WHERE r.enabled = TRUE
  AND NOT EXISTS (
      SELECT 1 FROM region_catalog_staging s
      WHERE s.sido_code = r.sido_code AND s.sigungu_code = r.sigungu_code
  );
