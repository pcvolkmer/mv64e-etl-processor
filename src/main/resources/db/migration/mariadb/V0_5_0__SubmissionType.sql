ALTER TABLE request ADD COLUMN submission_type varchar(16) not null default 'UNKNOWN';
ALTER TABLE request ADD COLUMN submission_accepted boolean not null default false;