CREATE TABLE IF NOT EXISTS user_role
(
    id         serial,
    username   varchar(255)                           not null unique,
    role       varchar(255)                           not null,
    created_at timestamp with time zone default now() not null,
    PRIMARY KEY (id)
);