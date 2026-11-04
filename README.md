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

Built with **Core Java 17+, JavaFX 21, JDBC + SQLite, Maven, java.util.logging and JUnit 5**.

---

## 1. Running the project

### Requirements
* **JDK 17 or newer** (JDK 21 recommended) — `java -version`
* **Maven 3.8+** (IntelliJ and Eclipse ship with Maven built in)
* Internet on the first build (Maven downloads JavaFX, SQLite JDBC and JUnit)

No database server is needed — SQLite is an embedded database stored in `data/trustlend.db`.

### Command line
```bash
mvn clean javafx:run          # build and launch the app
mvn test                      # run the 25 unit/integration tests
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

### Login
| Username | Password  |
|----------|-----------|
| `admin`  | `admin123`|

On first start the app creates the database and seeds a **realistic demo community of 36 vendors**
across five Delhi markets (≈4,000 transactions, 50 loans, 50+ vouches, 12 weeks of score history).
Delete the `data/` folder to start over. Set `db.seed.demo=false` in `data/config.properties`
to start with an empty database.

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
 DatabaseManager (db) ── JDBC ── SQLite file data/trustlend.db
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
│                              DemoDataSeeder, AppContext (composition root)
├── io/                        CsvExporter<T>, CsvTransactionImporter, BackupService, ReportWriter
├── util/                      Validators, Formats, PasswordHasher
└── ui/                        MainWindow, LoginView, Ui helpers, views, dialogs, components
src/main/resources
├── config.properties  logging.properties  db/schema.sql  styles/app.css
src/test/java                 25 JUnit 5 tests (graph, engine, DAO+SQLite, utilities, file I/O)
```

### Database schema
`users`, `vendors`, `transactions`, `loans`, `repayments`, `vouches`, `score_history` —
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

---

## 6. Sample data files
`samples/transactions-import-sample.csv` shows the CSV import format
(use a phone number that exists in your database — copy one from the Vendors screen).

## 7. Troubleshooting
* **“JavaFX runtime components are missing”** – run through Maven (`mvn javafx:run`) or the fat jar / `Launcher` class, not `TrustLendApp` directly from a plain classpath.
* **Port/lock errors on the DB** – close other running copies of the app; SQLite allows one writer.
* **Reset everything** – close the app and delete the `data/` folder.
