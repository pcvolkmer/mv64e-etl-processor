ALTER TABLE request ADD COLUMN followup_count int DEFAULT 0;
UPDATE request SET followup_count = -1;