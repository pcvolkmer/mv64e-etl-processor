ALTER TABLE request ADD COLUMN type varchar(16) not null;
UPDATE request SET type = 'MTB_FILE';