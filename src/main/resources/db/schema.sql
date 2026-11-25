-- TrustLend community database (SQLite): one shared database for borrowers, lenders and HQ.
-- Executed statement-by-statement on start-up (CREATE ... IF NOT EXISTS).

CREATE TABLE IF NOT EXISTS users (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    username      TEXT    NOT NULL UNIQUE,
    password_hash TEXT    NOT NULL,
    salt          TEXT    NOT NULL,
    full_name     TEXT    NOT NULL,
    role          TEXT    NOT NULL,                       -- ADMIN, LENDER or BORROWER
    vendor_id     INTEGER REFERENCES vendors(id) ON DELETE SET NULL,   -- borrowers: their profile
    organisation  TEXT,                                   -- lenders: e.g. "Meena Self-Help Group"
    lender_type   TEXT,                                   -- lenders: Moneylender, Self-help group, ...
    created_on    TEXT    NOT NULL DEFAULT (date('now'))
);

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
    note      TEXT,
    source    TEXT    NOT NULL DEFAULT 'RECORDED',   -- RECORDED, UPLOADED, HELD (held for review)
    upload_id INTEGER REFERENCES uploads(id) ON DELETE CASCADE
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
    score_at_issue INTEGER NOT NULL,
    lender_id      INTEGER REFERENCES users(id) ON DELETE SET NULL,   -- NULL = TrustLend community fund
    origin         TEXT    NOT NULL DEFAULT 'PLATFORM'                -- PLATFORM or REPORTED (outside loan)
);

CREATE TABLE IF NOT EXISTS repayments (
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    loan_id   INTEGER NOT NULL REFERENCES loans(id) ON DELETE CASCADE,
    amount    REAL    NOT NULL CHECK (amount > 0),
    due_date  TEXT    NOT NULL,
    paid_date TEXT    NOT NULL,
    on_time   INTEGER NOT NULL,
    status    TEXT    NOT NULL DEFAULT 'CONFIRMED',   -- CONFIRMED, PENDING (awaiting borrower), DISPUTED, REJECTED
    reported_by INTEGER REFERENCES users(id) ON DELETE SET NULL,
    note      TEXT
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

-- sales / UPI statement files uploaded by borrowers, with the automatic quality check
CREATE TABLE IF NOT EXISTS uploads (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    vendor_id   INTEGER NOT NULL REFERENCES vendors(id) ON DELETE CASCADE,
    file_name   TEXT    NOT NULL,
    uploaded_on TEXT    NOT NULL,
    rows        INTEGER NOT NULL,
    sales_days  INTEGER NOT NULL,
    total_sales REAL    NOT NULL,
    quality     INTEGER NOT NULL CHECK (quality BETWEEN 0 AND 100),
    status      TEXT    NOT NULL,                 -- ACCEPTED, REVIEW, REJECTED
    issues      TEXT
);

-- monthly income declared by borrowers (salary slip / business income), verified by a lender or HQ
CREATE TABLE IF NOT EXISTS incomes (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    vendor_id      INTEGER NOT NULL REFERENCES vendors(id) ON DELETE CASCADE,
    monthly_amount REAL    NOT NULL CHECK (monthly_amount > 0),
    kind           TEXT    NOT NULL,
    proof_file     TEXT,
    declared_on    TEXT    NOT NULL,
    status         TEXT    NOT NULL,              -- DECLARED, VERIFIED, REJECTED
    verified_by    INTEGER REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_txn_vendor   ON transactions(vendor_id, txn_date);
CREATE INDEX IF NOT EXISTS idx_loan_vendor  ON loans(vendor_id);
CREATE INDEX IF NOT EXISTS idx_repay_loan   ON repayments(loan_id);
CREATE INDEX IF NOT EXISTS idx_hist_vendor  ON score_history(vendor_id, computed_on);
CREATE INDEX IF NOT EXISTS idx_repay_status ON repayments(status);
CREATE INDEX IF NOT EXISTS idx_loan_lender  ON loans(lender_id);
