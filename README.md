# TrustLend — Micro-Lending Trust Score for Informal Vendors

TrustLend is a JavaFX desktop application that gives street vendors and small shopkeepers a
**credit score without a bank credit history**. Instead of bank records it looks at:

1. **Transaction consistency** – how regular their weekly sales are
2. **Repayment history** – how they repaid earlier micro-loans
3. **Business tenure** – years trading and time on the platform
4. **Community vouches** – who vouches for them, modelled as a **weighted, directed trust graph**

Trust *flows* through the graph (personalised PageRank / EigenTrust): a vouch from a highly
trusted vendor raises your score more than a vouch from someone unknown, and vouching for a
person who later defaults lowers **your** credibility as a voucher.

Nobody has to type in a vendor's data by hand. **Borrowers, lenders and TrustLend HQ each log in with their own
role**, and the data arrives the way it does in real life:

* a **lender reports** "Ramesh repaid ₹15,000 on time" → Ramesh gets a notification and **confirms** (or disputes) it;
* a **borrower uploads** a UPI / PhonePe statement (CSV) → it is checked for signs of forgery and turned into sales history;
* a borrower **declares a salary or business income** with a proof photo → a lender or HQ verifies it;
* a borrower can do all of this from a **phone browser** by scanning a QR code on the laptop.

Every score has an **evidence panel** that says exactly where each number came from.

Built with **Core Java 17+, JavaFX 21, JDBC + SQLite, Maven, java.util.logging, `com.sun.net.httpserver` and JUnit 5**.

---

## 1. Running the project

### Requirements
* **JDK 17 or newer** (JDK 21 recommended) — `java -version`
* **Maven 3.8+** (IntelliJ and Eclipse ship with Maven built in)
* Internet on the first build (Maven downloads JavaFX, SQLite JDBC and JUnit)

No database server is needed — SQLite is an embedded database. Everyone shares one community database,
`data/community.db`. It is created with the demo community and demo accounts the first time the app starts.

### Command line
```bash
mvn clean javafx:run          # build and launch the app
mvn test                      # run the 46 unit/integration tests
mvn clean package             # builds target/trustlend-1.0.0-all.jar
java -jar target/trustlend-1.0.0-all.jar
```

### IntelliJ IDEA
1. *File → Open…* and select the `TrustLend` folder (the one containing `pom.xml`). Trust the project.
2. Wait for Maven to import (bottom-right progress bar).
3. Open the **Maven** tool window → *Plugins → javafx → javafx:run* (double-click).
   Or run `com.trustlend.Launcher` directly.

### Eclipse
1. *File → Import → Maven → Existing Maven Projects* → select the folder.
2. Right-click the project → *Run As → Maven build…* → Goals: `clean javafx:run`.

### VS Code
1. Install the *Extension Pack for Java*. Open the folder.
2. In the *Maven* side panel: `trustlend → Plugins → javafx → run`.

### Roles and demo accounts
The login screen first asks **who you are**: *Borrower*, *Lender* or *TrustLend HQ*. Each role has its own
sign-in form, its own demo-account buttons (click one to log in instantly) and — for borrowers and lenders —
**Create an account**.

| Role | Sees | Can do |
|---|---|---|
| **Borrower** (vendor) | *My TrustLend* (own score, factors, tips, evidence, what-if), Confirmations, Upload proof, My loans, Trust Network | Confirm / dispute repayments lenders reported, upload UPI statements, declare income, open the phone page |
| **Lender** (SHG, wholesaler, MFI, moneylender) | Lender desk, Find a borrower, My loans, Trust Network | Look up a borrower by name, phone or TrustLend ID, lend, report repayments, report a past loan, verify income |
| **TrustLend HQ** (admin) | Dashboard + graphs, all borrowers, Trust Network, all loans, transactions, **Verification**, Members, Reports, Settings | Resolve disputes, approve or reject suspicious uploads, verify income, reset the demo community |

| Username | Password | Who |
|---|---|---|
| `admin` | `admin123` | TrustLend HQ — oversees everything |
| `meena_shg` | `demo123` | Jagriti Mahila Self-Help Group (lender) |
| `sharma_wholesale` | `demo123` | Sharma Wholesale Traders (lender) |
| `udaan_mfi` | `demo123` | Udaan Microfinance (lender) |
| `javed` | `demo123` | Javed Ansari — street food, patchy record (best for showing a score go up) |
| `ramesh` | `demo123` | Ramesh Kumar — fruit seller, has a repayment waiting to be confirmed |
| `geeta` | `demo123` | Geeta Rani — clean uploaded statement, declared income |
| `kamla` | `demo123` | Kamla Bai — has a disputed repayment |
| `sunita`, `irfan` | `demo123` | Sunita (upload held for review), Irfan (verified salary) |

New accounts join the same community, so a new borrower immediately appears in the lenders' search and on the
Trust Network. *Settings → Community database → Reset to demo community* (HQ only) puts everything back.

### How the data gets in (no data-entry clerk)

| Source | Who enters it | Counts as |
|---|---|---|
| Repayment reported by a lender | lender | **pending** (half weight) until the borrower confirms → **confirmed** (full weight). **Disputed** = 0 and HQ decides |
| Past loan from outside TrustLend | lender (*Report a past loan*) | the same confirm / dispute flow |
| UPI / sales statement CSV | borrower (desktop or phone) | quality checks 0–100: ≥ 60 accepted, 35–59 **held for HQ review**, < 35 rejected |
| Income declaration + proof photo | borrower | *declared* +0.05 tenure, *verified* by a lender or HQ +0.15 |

Statement checks: too few payments, missing / duplicate transaction IDs, future dates, unreadable rows,
too many round amounts, the same amount repeated, single days far above normal, very short periods, and
days that were already uploaded. A lender's **credibility** is `(confirmed + 1) / (confirmed + disputed + 2)`,
so a lender who keeps reporting things borrowers dispute loses weight.

### Phone page
Log in as a borrower → **Open on a phone**. A QR code appears; scan it with a phone on the **same Wi-Fi**.
The phone shows the score, pending confirmations (Yes, I paid / This is wrong) and a statement upload with
*Try a sample* / *Try a fake one* buttons. Changes appear on the laptop instantly. On Windows, allow Java
through the firewall when asked (private networks). If the phone can't connect, the same page works on the
laptop's own browser at `http://localhost:8765`.

### Demo script for the professor (about 10 minutes)

**Before the demo:** maximise the window; for a projector switch on **presentation mode** (bottom of the sidebar).

| # | Log in as | Show | Say |
|---|---|---|---|
| 1 | (any) | Sidebar → **Story mode: Meet Sunil** | "50 million street vendors have no credit history. Watch one chai seller go from *declined* to a fair loan." |
| 2 | `admin` | **Dashboard** graphs, **Trust Network** | "HQ oversees the whole community: 36 vendors, three lenders." |
| 3 | `sharma_wholesale` | Lender desk → *Find a borrower* → Ramesh → report a repayment | "The lender logs that he was paid. No clerk." |
| 4 | `ramesh` (or the phone page) | **Confirmations** → *Yes, I paid* | "The borrower confirms, and only then does it count fully. The score goes up." |
| 5 | `javed` | **Upload proof** → save the sample → upload it | Quality 100/100, Javed's score jumps (≈ 580 → 630). Then upload the *suspicious* sample: held for review. |
| 6 | `javed` | **Open on a phone**, scan the QR | Same thing from a phone — this is how a chaiwala would actually use it. |
| 7 | `admin` | **Verification** | Dispute (Kamla), held upload (Sunita), declared income (Geeta): approve / reject. |
| 8 | any vendor profile | **Evidence panel**, **What if…** sliders, **New loan** decision card, **Trust certificate** | "Every number is explainable." |
| 9 | `admin` | Loans → **Mark defaulted** → ripple effect on the network | "Vouching has consequences." |
| 10 | — | Code + `mvn test` | Syllabus table below. |

Keyboard in story mode: → / Space next, ← back, Esc exit.

---

## 2. Screens

| Screen | What you can do |
|---|---|
| **Login** | Salted-hash password check, inline validation, Enter-to-submit |
| **Dashboard** | KPI tiles, score-distribution bar chart, risk-band pie chart, top-5 vendors, factor weights, latest activity, *Recalculate scores* (runs on a background thread) |
| **Vendors** | Live search + band/business filters (`FilteredList` → `SortedList` → `TableView`), add / edit / activate / delete, context menu, double-click to open |
| **Vendor profile** | Animated score gauge, factor bars, plain-language **“Why this score”** reasons, loan offer, score-history line chart, weekly-sales bar chart, vouches given/received, loans, transactions |
| **Trust Network** | Interactive force-directed graph: node size/colour = score/band, edge thickness = vouch weight, arrows show direction. Click to highlight in/out vouches, drag nodes, scroll to zoom, drag to pan, filter by market, **find the strongest trust path** between two vendors |
| **Loans** | Status filter chips, repayment progress bars, record repayment, mark defaulted, repayment history |
| **Transactions** | Ledger with vendor/type/date-range filters and running sales total |
| **Reports & Data** | CSV export (vendors, transactions), formatted text portfolio report, CSV import with per-line error report, serialized backup & restore, raw DB copy, log viewer |
| **Settings** | Sliders for factor weights, damping, voucher penalty, base interest and min loan score — saved to `data/config.properties` |

Every data-entry dialog validates input **before closing**: the offending field turns red and the
error appears inline (custom `ValidationException` carries the field name).

### New in version 5 — automated data entry
* **Three roles** with one shared community DB: borrower, lender, TrustLend HQ; sign-up for borrowers and lenders.
* **Lender reports → borrower confirms**: weighted repayments (pending ½, confirmed 1, disputed 0), lender credibility, HQ dispute resolution.
* **Statement upload** with fraud checks and a quality rating (`io/StatementImporter`); suspicious files are held for HQ.
* **Income declarations** with proof files, verified by a lender or HQ.
* **Evidence panel** on every score; **TrustLend ID** (`TL-1001`) for looking people up.
* **Phone page** served by the app itself (`web/PhoneServer`) with a QR code drawn by our own encoder (`util/QrCode`).

### Earlier versions
* Story mode "Meet Sunil", loan decision card, what-if simulator, trust certificate, default ripple, dark and
  presentation modes, splash screen, live trust-flow particles, market heat map.

### Vouch “what-if” simulator
In *Add vouch* press **Preview impact** to see how the vouchee’s score would change
(e.g. `Asha Pillai: 537 → 619 (+82)`) without saving anything.

---

## 3. How the score is calculated

```
score = 300 + 600 × ( 0.30·C + 0.35·R + 0.10·T + 0.25·N )        → 300 … 900
```

| Factor | Formula (all values 0…1) |
|---|---|
| **C – consistency** | last 12 weeks: 25% weeks-active + 25% days-with-sales + 40% stability `1/(1+CV)` of weekly sales + 10% savings deposits. Vendors observed for fewer weeks are shrunk towards 0.4 (less evidence). |
| **R – repayment** | Bayesian average `(onTime + 0.5·late + 1)/(payments + 2)` + 0.05 per repaid loan (max 0.10) − 0.15 per overdue loan − 0.35 per default. No history ⇒ neutral 0.5. |
| **T – tenure** | `0.6·min(1, years/10) + 0.4·min(1, months on platform/24)` |
| **N – network** | see below |

**Network trust (the graph part)**

1. *Local trust* of each vendor = weighted mean of C, R and T.
2. Each vouch u→v becomes an edge with weight `strength/5 × credibility(u)`, where
   `credibility(u) = max(0.4, 1 − 0.15 × number of defaulters u vouched for)`.
3. Personalised PageRank with the local trust as prior and damping `d = 0.70`:
   `t(v) = (1−d)·p(v) + d·[ Σ_u t(u)·w(u,v)/W(u) + dangling·p(v) ]` — iterated until the L1 change < 10⁻⁶.
4. `N(v)` = trust that **flows into** v from others, divided by the 90th percentile and capped at 1.

**Rules on top:** a defaulted loan caps the score at 549; vendors below 450 cannot vouch;
max 8 vouches per vendor; no self-vouching.

| Band | Score | Max loan | Typical rate |
|---|---|---|---|
| Excellent | 750+ | ₹50,000 | 12–16% |
| Good | 650–749 | ₹25,000 | 16–18% |
| Fair | 550–649 | ₹12,000 | 18–20% |
| Poor | 450–549 | ₹5,000 | 20–22% |
| High Risk | < 450 | — | — |

Interest = `base (18%) + (650 − score) × 0.02`, clamped to 12–30% p.a.

---

## 4. Architecture

```
 JavaFX UI  (ui, ui.view, ui.dialog, ui.component, styles/app.css)
     │   event handlers call services; long work runs in javafx.concurrent.Task
     ▼
 Service layer  (service)       business rules, validation, transactions
     │                 └──── TrustScoreEngine (engine) ── TrustGraph<N> (graph)
     ▼
 DAO interfaces (dao)  ◄── JDBC implementations (dao.jdbc, AbstractJdbcDao template)
     │
     ▼
 DatabaseManager (db) ── JDBC ── SQLite: data/community.db (shared by all roles)

 PhoneServer (web) ── com.sun.net.httpserver ── phone browser (JSON API + one HTML page)
```

```
src/main/java/com/trustlend
├── TrustLendApp.java          JavaFX Application (init/start/stop)
├── Launcher.java              plain main() for the fat jar
├── config/                    AppConfig (properties), LoggingSetup
├── model/                     Person (abstract) ← Vendor, Loan, Repayment, Vouch, Transaction,
│                              enums (RiskBand, LoanStatus, VendorCategory), ScoreBreakdown (record)
├── exception/                 TrustLendException hierarchy
├── db/                        DatabaseManager (connection + transactions), SqlWork
├── dao/  dao/jdbc/            GenericDao<T>, VendorDao …  /  AbstractJdbcDao<T>, JdbcVendorDao …
├── graph/                     TrustGraph<N>: propagation, BFS, Dijkstra
├── engine/  engine/factor/    TrustScoreEngine, ScoringFactor strategy + 3 factors
├── service/                   Vendor/Loan/Vouch/Transaction/Score/Auth/Analytics services,
│                              CommunityService (reports, confirmations, uploads, income, HQ queue, evidence),
│                              DemoDataSeeder, DemoAccounts, AppContext (composition root)
├── io/                        CsvExporter<T>, CsvTransactionImporter, StatementImporter, CsvUtil, BackupService, ReportWriter
├── util/                      Validators, Formats, PasswordHasher, QrCode
├── web/                       PhoneServer, PhonePage
└── ui/                        MainWindow, LoginView, Ui helpers, views, dialogs, components
src/main/resources
├── config.properties  logging.properties  db/schema.sql  styles/app.css dark.css presentation.css
src/test/java                 46 JUnit 5 tests (graph, engine, DAO+SQLite, utilities, file I/O, PNG, QR, statement checks,
                              community flows, story/what-if/loan decisions)
```

### Database schema
One database: `users` (role, linked vendor, organisation), `vendors`, `transactions` (source: recorded / uploaded / held),
`loans` (lender, origin), `repayments` (status, reported by), `uploads`, `incomes`, `vouches`, `score_history` —
with foreign keys (`ON DELETE CASCADE`), `CHECK` constraints (positive amounts, strength 1–5,
no self-vouch), `UNIQUE` constraints (phone, voucher+vouchee) and indexes. See `src/main/resources/db/schema.sql`.

---

## 5. Syllabus coverage

### Unit 1 – Java foundations
| Topic | Where |
|---|---|
| Project structure, packages | `com.trustlend.{model, dao, service, ui, …}` |
| Classes & objects, encapsulation | all `model` classes (private fields + accessors) |
| **Abstract classes** | `Person`, `AbstractJdbcDao<T>`, `AbstractFactor`, `BaseView`, `FormDialog<R>` |
| **Interfaces** (incl. default methods) | `Identifiable` (default `isPersisted()`), `GenericDao`, `ScoringFactor`, `SqlWork` |
| Inheritance & polymorphism | `Vendor extends Person`; every view overrides `refresh()`; factors are used through `ScoringFactor` |
| Access modifiers | `private` fields, `protected` constructors/hooks, package-private `escape()` used by tests |
| **static vs instance** | `Vendor.instancesCreated` counter (shown in Settings), static utility classes `Validators`, `Formats`, static factory `RiskBand.fromScore()` |
| Singleton | `AppConfig` (holder idiom) |
| Coding conventions | standard naming, one public class per file, Javadoc on public types |
| Maven | `pom.xml` – dependencies, javafx-maven-plugin, shade plugin (fat jar), surefire (tests) |

### Unit 2 – Exceptions, collections, generics, utilities
| Topic | Where |
|---|---|
| **Custom exception hierarchy** | `TrustLendException` ← `DataAccessException`, `ValidationException`, `EntityNotFoundException`, `InvalidVouchException`, `LoanEligibilityException` |
| Exception chaining, try-with-resources, multi-catch, suppressed | `DatabaseManager.transaction()`, all DAOs, `BackupService.readSnapshot()` |
| **ArrayList / LinkedList / HashSet / HashMap** | everywhere; `LinkedList` as BFS queue and path builder in `TrustGraph`; `HashSet` for visited nodes |
| **Iterator** | `TrustGraph.removeNode()` removes while iterating |
| **Comparator** | `VendorService.BY_SCORE_DESC` (`comparingInt().reversed().thenComparing()`), PriorityQueue in Dijkstra |
| **Generics** | `TrustGraph<N>`, `GenericDao<T extends Identifiable>`, `CsvExporter<T>`, `FormDialog<R>`, `SqlWork<T>`, generic method `<T> T inTransaction(...)` |
| EnumMap, TreeSet, LinkedHashMap | `AnalyticsService`, `VendorDialog`, `SettingsView` |
| **Objects, Optional, Math, LocalDate** | `Objects.requireNonNull`, `Optional` return types in DAOs, `Math.log/hypot/sqrt` in the engine and graph layout, `LocalDate/Period/ChronoUnit` throughout |
| Records, switch expressions, pattern matching | `ScoreBreakdown`, `FactorResult`, `Offer`; `switch ->`; `instanceof Vendor v` |
| Debugging | `java.util.logging` at FINE level + log viewer screen |

### Unit 3 – Files, serialization, logging, configuration
| Topic | Where |
|---|---|
| **Byte streams** | `BackupService.copyFile()` – `FileInputStream`/`FileOutputStream` with an 8 KB buffer |
| **Character streams** | `CsvExporter` (`BufferedWriter`), `CsvTransactionImporter` (`BufferedReader`), `ReportWriter` (`PrintWriter`), `DatabaseManager.readSqlScript()` |
| **Serialization / deserialization** | `BackupSnapshot implements Serializable` written with `ObjectOutputStream`, restored with `ObjectInputStream`; `transient` fields in `Vendor`/`Loan`; `serialVersionUID` |
| **Logging** | `logging.properties` → console + rotating `FileHandler` (`data/logs/trustlend-0.log`) |
| **Properties files** | `config.properties` defaults + user overrides in `data/config.properties`, saved by the Settings screen |

### Unit 4 – JDBC
| Topic | Where |
|---|---|
| JDBC architecture / `DriverManager` | `DatabaseManager.connect()` |
| **INSERT / SELECT / UPDATE / DELETE** | `AbstractJdbcDao` + every `Jdbc*Dao` |
| **PreparedStatement, ResultSet** | all queries (parameter binding prevents SQL injection) |
| Generated keys, batch updates | `RETURN_GENERATED_KEYS`; `addBatch()/executeBatch()` in `insertBatch`, score history and restore |
| **Transaction management** | `DatabaseManager.inTransaction()` / `transaction()` – commit / rollback; used for loan issue (loan + disbursal), repayment (payment + status), restore, seeding |
| **DAO pattern** | interfaces in `dao`, JDBC implementations in `dao.jdbc`, services depend only on interfaces |
| Aggregates & joins | `GROUP BY` totals, `JOIN loans` for a vendor's repayments |

### Unit 5 – JavaFX
| Topic | Where |
|---|---|
| **Application lifecycle** `init() / start() / stop()` | `TrustLendApp` – DB opened in `init`, UI in `start`, connection closed in `stop` |
| **Stage, Scene, scene graph** | `TrustLendApp.setRoot()`; `NetworkView` builds its own graph of `Circle`, `Line`, `Polygon`, `Text` nodes |
| **Layouts** – HBox, VBox, GridPane, BorderPane, StackPane | `BaseView` (BorderPane), `MainWindow` (StackPane overlay), `FormDialog` (GridPane), all screens (HBox/VBox) |
| **Controls** – Label, TextField, PasswordField, Button, TextArea, CheckBox, ComboBox, DatePicker, Spinner, Slider, ToggleButton, ProgressBar, TabPane | dialogs, Settings, Loans, Data screens |
| **ListView / TableView** | Vendors, Loans, Transactions tables with custom `TableCell`s; vouch `ListView` with custom `ListCell` |
| **Events & listeners** | `setOnAction`, mouse click/drag/scroll handlers in the graph, property listeners driving live filters, event **filter** on dialog OK buttons |
| **Validation & feedback** | inline errors + red fields, `Alert` confirm/info/error dialogs, animated toast notifications, busy overlay |
| Charts | `BarChart`, `PieChart`, `LineChart` |
| CSS styling | `styles/app.css` (looked-up colours, pseudo-classes `:hover :selected :focused`) |
| Animation & concurrency | `Timeline` (gauge), `FadeTransition`/`TranslateTransition` (toasts, page change), `javafx.concurrent.Task` for background recalculation |

### Beyond the syllabus
* Graph algorithms: personalised PageRank, BFS, Dijkstra, Fruchterman–Reingold force-directed layout
* Explainable scoring (reasons list) and a what-if simulator
* Strategy, Template Method, Singleton, DAO and Composition-Root patterns
* Salted, iterated SHA-256 password hashing with constant-time comparison
* `TextFormatter` input masks, `FilteredList`/`SortedList` live filtering, bindings
* JUnit 5 test suite incl. DAO integration tests on in-memory SQLite
* Runnable fat jar via the Maven Shade plugin
* **Networking**: an embedded HTTP server (`com.sun.net.httpserver`) with a small JSON API, token sessions and
  `Platform.runLater` to update the desktop UI from server threads
* **QR codes from scratch**: Reed–Solomon error correction over GF(256), masking and format bits (`util/QrCode`)
* Fraud heuristics on uploaded statements (median, duplicates, round-number ratio)
* Role-based access control across the UI and the phone API

---

## 6. Sample data files
* `samples/upi-statement-good.csv` — 12 weeks of normal UPI payments (uploads as quality 100, accepted).
* `samples/upi-statement-suspicious.csv` — 4 weeks of round, repeated amounts (held for HQ review).
  The *Upload proof* screen can also save fresh copies of both.
* `samples/transactions-import-sample.csv` — the HQ CSV import format
  (use a phone number that exists in your database — copy one from the Borrowers screen).

## 7. Troubleshooting
* **“JavaFX runtime components are missing”** – run through Maven (`mvn javafx:run`) or the fat jar / `Launcher` class, not `TrustLendApp` directly from a plain classpath.
* **Port/lock errors on the DB** – close other running copies of the app; SQLite allows one writer.
* **Reset everything** – HQ → *Settings → Reset to demo community*, or close the app and delete the `data/` folder.
* **Phone can't open the page** – both devices on the same Wi-Fi (not a guest network or mobile hotspot isolation),
  allow Java in Windows Firewall for private networks, or try `http://localhost:8765` on the laptop.
* **Old accounts are gone** – version 5 uses a new shared database (`data/community.db`); use the demo accounts or sign up again.
