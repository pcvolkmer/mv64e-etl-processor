CREATE TABLE IF NOT EXISTS token
(
    id                  int auto_increment primary key,
    name                varchar(255)                         not null,
    username            varchar(255)                         not null unique,
    password            varchar(255)                         not null,
    created_at          datetime     default utc_timestamp() not null
);