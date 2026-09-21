ALTER TABLE request ADD COLUMN destination varchar(16) DEFAULT 'UNKNOWN';
UPDATE request SET destination = 'UNKNOWN';
