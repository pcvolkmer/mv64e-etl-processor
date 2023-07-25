CREATE TABLE IF NOT EXISTS request
(
    id           serial,
    uuid         varchar(255)                           not null unique,
    patient_id   varchar(255)                           not null,
    pid          varchar(255)                           not null,
    fingerprint  varchar(255)                           not null,
    status       varchar(16)                            not null,
    processed_at timestamp with time zone default now() not null,
    PRIMARY KEY (id)
);