ALTER TABLE request ADD COLUMN updated_at timestamp with time zone;
ALTER TABLE request ADD COLUMN updated_by varchar(255);