-- TrustLend accounts database (data/accounts.db): who can log in.
-- Each account's vendors, loans and transactions live in its own workspace database.

CREATE TABLE IF NOT EXISTS users (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    username      TEXT    NOT NULL UNIQUE,
    password_hash TEXT    NOT NULL,
    salt          TEXT    NOT NULL,
    full_name     TEXT    NOT NULL,
    role          TEXT    NOT NULL DEFAULT 'OFFICER',
    created_on    TEXT    NOT NULL DEFAULT (date('now'))
);
