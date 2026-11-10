-- TrustLend workspace schema (SQLite). Every account has its own workspace database
-- (data/workspaces/user-<id>.db) built from this script; logins live in accounts.sql.
-- Statements are separated by semicolons; every statement is idempotent.

CREATE TABLE IF NOT EXISTS vendors (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    name              TEXT    NOT NULL,
    phone             TEXT    NOT NULL UNIQUE,
    category          TEXT    NOT NULL,
    market_area       TEXT    NOT NULL,
    city              TEXT    NOT NULL,
    years_in_business INTEGER NOT NULL DEFAULT 0,
    join_date         TEXT    NOT NULL,
    active            INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS transactions (
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    vendor_id INTEGER NOT NULL REFERENCES vendors(id) ON DELETE CASCADE,
    amount    REAL    NOT NULL CHECK (amount > 0),
    type      TEXT    NOT NULL,
    txn_date  TEXT    NOT NULL,
    note      TEXT
);

CREATE TABLE IF NOT EXISTS loans (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    vendor_id      INTEGER NOT NULL REFERENCES vendors(id) ON DELETE CASCADE,
    principal      REAL    NOT NULL CHECK (principal > 0),
    interest_rate  REAL    NOT NULL,
    tenure_weeks   INTEGER NOT NULL,
    issue_date     TEXT    NOT NULL,
    due_date       TEXT    NOT NULL,
    status         TEXT    NOT NULL,
    score_at_issue INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS repayments (
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    loan_id   INTEGER NOT NULL REFERENCES loans(id) ON DELETE CASCADE,
    amount    REAL    NOT NULL CHECK (amount > 0),
    due_date  TEXT    NOT NULL,
    paid_date TEXT    NOT NULL,
    on_time   INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS vouches (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    voucher_id   INTEGER NOT NULL REFERENCES vendors(id) ON DELETE CASCADE,
    vouchee_id   INTEGER NOT NULL REFERENCES vendors(id) ON DELETE CASCADE,
    strength     INTEGER NOT NULL CHECK (strength BETWEEN 1 AND 5),
    relationship TEXT    NOT NULL,
    created_date TEXT    NOT NULL,
    UNIQUE (voucher_id, vouchee_id),
    CHECK (voucher_id <> vouchee_id)
);

CREATE TABLE IF NOT EXISTS score_history (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    vendor_id   INTEGER NOT NULL REFERENCES vendors(id) ON DELETE CASCADE,
    score       INTEGER NOT NULL,
    consistency REAL    NOT NULL,
    repayment   REAL    NOT NULL,
    tenure      REAL    NOT NULL,
    network     REAL    NOT NULL,
    computed_on TEXT    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_txn_vendor   ON transactions(vendor_id, txn_date);
CREATE INDEX IF NOT EXISTS idx_loan_vendor  ON loans(vendor_id);
CREATE INDEX IF NOT EXISTS idx_repay_loan   ON repayments(loan_id);
CREATE INDEX IF NOT EXISTS idx_hist_vendor  ON score_history(vendor_id, computed_on);
