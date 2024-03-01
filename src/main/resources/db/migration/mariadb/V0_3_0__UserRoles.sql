CREATE TABLE IF NOT EXISTS user_role
(
    id         int auto_increment primary key,
    username   varchar(255)                     not null unique,
    role       varchar(255)                     not null,
    created_at datetime default utc_timestamp() not null
);