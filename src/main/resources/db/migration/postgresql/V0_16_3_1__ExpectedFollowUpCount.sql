ALTER TABLE request ADD COLUMN expected_followup_count int DEFAULT 0;
UPDATE request SET expected_followup_count = -1;