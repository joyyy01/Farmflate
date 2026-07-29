-- Auto-generated cases are useful for repeatable regression checks but are not
-- human-reviewed ground truth. Preserve that distinction in the database.
ALTER TABLE rag.eval_case
    ADD COLUMN IF NOT EXISTS evaluation_origin varchar(32) NOT NULL DEFAULT 'LEGACY'
    CHECK (evaluation_origin IN ('LEGACY', 'AUTO_GENERATED', 'HUMAN_REVIEWED'));

CREATE INDEX rag_eval_case_dataset_origin_idx
    ON rag.eval_case (dataset_key, dataset_version, evaluation_origin, status);
