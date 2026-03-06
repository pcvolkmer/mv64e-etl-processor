ALTER TABLE request ADD COLUMN tan varchar(64) not null default '';
UPDATE request SET tan = '';
