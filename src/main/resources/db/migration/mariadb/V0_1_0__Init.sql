CREATE TABLE IF NOT EXISTS request
(
    id                  int auto_increment primary key,
    uuid                varchar(255)                         not null unique,
    patient_id          varchar(255)                         not null,
    pid                 varchar(255)                         not null,
    fingerprint         varchar(255)                         not null,
    status              varchar(16)                          not null,
    processed_at        datetime     default utc_timestamp() not null,
    description         varchar(255) default '',
    data_quality_report mediumtext   default ''
);