CREATE TABLE IF NOT EXISTS token
(
    id                  serial,
    name                varchar(255)                           not null,
    username            varchar(255)                           not null unique,
    password            varchar(255)                           not null,
    created_at          timestamp with time zone default now() not null,
    PRIMARY KEY (id)
);