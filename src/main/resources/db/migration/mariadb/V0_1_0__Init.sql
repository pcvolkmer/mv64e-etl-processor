CREATE TABLE IF NOT EXISTS request
(
    id           int auto_increment primary key,
    uuid         varchar(255)                     not null,
    patient_id   varchar(255)                     not null,
    fingerprint  varchar(255)                     not null,
    status       varchar(16)                      not null,
    processed_at datetime default utc_timestamp() not null
);