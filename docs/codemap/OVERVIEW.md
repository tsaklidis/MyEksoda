# Code map: read this instead of the codebase

How the app is built, so a session can find the right files without reading them. Written by
hand; `SYMBOLS.md` beside it is generated (`make codemap`) and lists every declaration with its
line. UI rules are in `docs/design/ui-redesign.md`.

**Lookup order:** this file → `rtk grep -n '<Name>' docs/codemap/SYMBOLS.md` → open only that
line range. Keep this file current when a change adds a module, a pipeline step or a pattern.

## What the app promises

Your receipts never leave the phone: no account, no analytics, no crash reporting.
The only network traffic is HTTPS GETs to the receipt providers (Impact, s1ecos, Entersoft,
AADE, Epsilon Digital), and, only when the user turns household sync on, encrypted sessions with
the household's phones on the local network (private addresses, never the internet).
Data is in a SQLCipher database; Android cloud backup is off (`allowBackup=false`,
`dataExtractionRules`); the user's own encrypted export is the only backup.

Not built yet: backup reminder, restore from onboarding, app lock, hide content
(`FLAG_SECURE`), delete all data, light theme, release build (R8, baseline profile). What is
left of household sync is under "Not built yet" in `docs/design/household-sync.md`.

## Modules and dependency rules

```
app                    Application, MainActivity, AppViewModel, nav graph (ui/EksodaApp.kt,
                       ui/Destinations.kt), Hilt root. Depends on the features it shows.
core:engine   (JVM)    Pure Kotlin rules: parsers, fetcher, matching, prices, analytics math.
                       NO android.*. Explicit API.
core:model    (JVM)    Domain types the UI sees (Receipt, Money, ImportJob, Period, filters…)
core:database          Room + SQLCipher: entities (entity/Entities.kt), DAOs, CategorySeed
core:data              Repositories (interface + Impl), imports/ pipeline, backup/, KeyManager,
                       WriteCoordinator, Fixed, DI modules (di/)
core:designsystem      Theme (dark scheme + EksodaColors, Inter + IBM Plex Mono, shapes),
                       components (SectionCard, ChangeBadge, HeroAmount, StatRow, OverlineText,
                       PeriodSelector, SegmentedToggle, navigation bar/rail + ScanButton,
                       CategoryTile/Icon, MerchantAvatar, groupedItem, Button), chart/
                       (MinMaxBar, RangeDotPlot, BarChart, LineChart, DonutChart, ShareBar),
                       text/monoNumbers
core:ui                Shared composables (EmptyState, MoneyText, HeroMoney) and text helpers
                       (formatMoney, formatAmount, currencySymbol, dayLabel, importErrorRes,
                       categoryNameRes)
feature:*              Screens. See only core:data interfaces, core:model, core:ui,
                       core:designsystem (wired by the eksoda.android.feature plugin).
                       feature:household: the Sync screen (`sync/`, opened from Settings).
build-logic            Convention plugins: eksoda.android.{application,library,compose,
                       feature,room}, eksoda.jvm.library, eksoda.hilt; Quality.kt (Spotless,
                       detekt, Kover); NoTelemetryCheck.kt
```

Package roots: `io.eksoda` (app), `io.eksoda.engine` (engine), `io.eksoda.core.<module>`,
`io.eksoda.feature.<feature>`. Sources are under `src/main/kotlin/`. Short paths here drop that
prefix: `core/data/util/Fixed.kt` is `core/data/src/main/kotlin/io/eksoda/core/data/util/Fixed.kt`.
SDK: `minSdk` 26, `targetSdk` 36, `compileSdk` 37.

## The import pipeline (the heart of the app)

```
Scan/paste/share/photo (feature:scan)
 → ReceiptRepository.importReceipt(url)               core:data/repository/ReceiptRepositoryImpl
 → ImportService.submit(rawUrl)                       core:data/imports/ImportService
     engine.resolve(url)  (Rejected(code) on InvalidUrl/Unsupported*: nothing stored)
     upsert import_job by (provider_code, url_identity) under WriteCoordinator
     → ImportSubmission.Queued / AlreadyImported / AlreadyPending / Rejected
     ImportScheduler.enqueue(jobId, userInitiated)  unique work "import-<id>", KEEP, network
     CONNECTED; user-started runs are expedited (foreground notification below Android 12)
 → ImportWorker (HiltWorker)                          core:data/imports/ImportWorker
     claim → engine.read(link) [fetch → parse → validate] → Persister.persist + complete
     failure → recordFailure: retry schedule [60s,5m,30m,2h,6h,24h] ±10% as NEW delayed
     work (never Result.retry()), or fail
 → Persister.persist (one transaction)                core:data/imports/Persister
     resolveMerchant → resolveStore → findExisting (duplicate) → receipt row →
     items (classify, match via mappings + code-reuse guard, createProductAndMapping with
     package suggestion, normalized prices)
 → ImportNotifier (opt-in, only when app not visible, never the URL)
ImportStartup (app start): after-update retry of format_changed/inconsistent/internal
failures once per versionCode; requeue pending jobs.
```

Engine entry point: `core/engine/.../engine/Engine.kt`: `resolve(rawUrl): ResolvedLink`,
`suspend read(link): ParsedReceipt(receipt: CanonicalReceipt, issues)`. Errors are the
sealed `ReceiptException` (`ReceiptException.kt`) with `code`, retry policy and
`publicMessage` (never shown; UI maps `code` with `importErrorRes`).

Providers: `engine/providers/{impact,s1ecos,entersoft,aadefhm,epsilon}/` each with `Urls.kt`
(recognize), `Parser.kt` (layouts → `CanonicalReceipt`), `Sanitize.kt`; Impact also
`Labels.kt`, `Views.kt`. Shared: `Base.kt` (`Provider`, `Layout`, `UrlIdentity`,
`maskUrl`), `Registry.kt` (`REGISTRY`), `Html.kt` (`textOf`, Jsoup), `Sanitizing.kt`.
Epsilon Digital (a host per big merchant, `epsilondigital-<merchant>.epsilonnet.gr`, and the
shared `epsilondigital-3rd` host for smaller ones) is not read from its viewer page (behind a
Cloudflare Turnstile check) but from the receipt's detailed myDATA XML
(`/filedocument/GetInvoiceDocDetailed/<uuid>`, Jsoup XML parser). The XML has no time of day
(the time is the date only) and, on merchant hosts, no merchant name: names come from
`TENANTS` in `epsilon/Urls.kt` (a new merchant host is one entry there). Shared hosts
(`SHARED_HOSTS`) carry the issuer name in the XML.
Impact: the link's 40-hex id is usually the myDATA UID but not always; when it differs, the
page's tax view must print the link itself.
s1ecos (`einvoice.s1ecos.gr`) serves Impact's pages under its own host: its own provider code and
parser version, read by Impact's layout (`impact/Parser.kt` `Site`: code, version, printed-link
rule; `parsePage`) and Impact's sanitizer. Its QR link is `/v/<prefix><VAT>-<number>-<40-hex
authentication code>-<GUID>` (`s1ecos/Urls.kt`); the page prints the `/p/` form, which must have
the same issuer and access GUID (`printsLink`). Neither 40-hex id is the myDATA UID.
Fixtures: `testdata/fixtures/<provider>/` (sample page + expected JSON), edited with the parser.
`EpsilonTest` compares its fixture with the golden JSON and, on a difference, writes the
actual output to `core/engine/build/golden/` for review.

Fetcher: `engine/fetch/SafeFetcher.kt`, the only OkHttp client in the app: https/443 to
allowlisted hosts, a `Dns` that rejects non-public addresses (`PublicAddress.kt`), no
redirects/cookies/cache/proxy, timeouts 5/15/30 s, 2 MiB after decompression, `text/html`
only (a provider may ask for other types: `XML_TYPES`), one request per host at least 1 s
apart.

Text helpers (stored keys depend on their exact behavior; reuse them, don't reinvent):
`text/PyStrings.kt` (`pySplit`, `pyStrip`, `pyIsDigit`, `pyRegex` = Unicode-aware regex),
`text/PyDecimal.kt` (`pyDivide` = 28 digits HALF_EVEN, `pyStr`), `text/PyTime.kt`
(`pyIsoFormat`, `pyLocalToOffset`), `text/Text.kt` (`normalizeText`, `stripDiacritics`,
`collapseWhitespace`, `fingerprint`), `text/Numbers.kt` (`parseDecimal`, `parseDate`),
`text/SequenceMatcher.kt`.

## Data layer

- **Tables** (`core/database/.../entity/Entities.kt`, `HouseholdEntity.kt`,
  `BudgetEntity.kt`): household (with `annual_income_minor`), category (19 system ones
  seeded), budget (id `total` or the category's id), merchant, store, product,
  merchant_product_mapping, receipt, receipt_item, import_job, tombstone. One DB file = one household (no
  `household_id`). UUID `TEXT` ids, `created_at`/`updated_at` epoch ms UTC, plus
  `issued_local_date` (`YYYY-MM-DD` in the household zone). Deletes write a `tombstone` row
  (a receipt's with its `natural_key`, `SyncSchema.naturalKey`; merged products' with
  `replaced_by`, the product kept). `receipt.source_url` is null on a receipt that came from
  another phone by sync.
  Local to the phone (`SyncEntities.kt`): sync_peer (paired phones) and sync_event (the sync
  history); never backed up and never synced.
- `EksodaDatabase` version **7**, `exportSchema = true`; schema JSON in
  `core/database/schemas/`. Version 4 shipped in 0.1.1, so **every schema change needs a
  migration** that keeps the data: an `AutoMigration` when Room can derive it (4 → 5 and
  5 → 6 and 6 → 7 are), else a manual `Migration`, plus a case in `core/database` `MigrationTest`
  (`MigrationTestHelper` on Robolectric, schemas as test assets). There is no destructive
  fallback: a missing migration fails to open. A new table also goes in `BackupArchive.TABLES`
  (household data) or `BackupArchive.LOCAL_TABLES` (about this phone: not exported, left as it
  is by a restore).
- **Decimals:** columns are fixed-point `Long` with a suffix: `_minor` (2), `_e2` (2),
  `_e4` (4), `_e6` (6). Convert only with `core/data/util/Fixed.kt`
  (`Fixed.toLong(v, Fixed.E4)`, `Fixed.toDecimal(l, Fixed.MINOR)`).
- **Long lists** (receipts, products, review, imports) page with `listPagingConfig`
  (`repository/ListPaging.kt`): no placeholders, pages loaded one page ahead. Filters go in
  the query, not in Kotlin over a loaded list. The product list has one query per ordering
  (`ProductDao.pagingByName` walks the `normalized_name` index; never a `CASE` in ORDER BY).
- **Counts and pickers** get their own light query (`ImportJobDao.observePendingCount`,
  `CatalogRepository.getCategoryList`, `ReceiptOptionsRepository`), never a full row list
  mapped and thrown away. One-shot reads are `suspend` queries (`HouseholdDao.get`), never
  `observe().first()`. A ViewModel that derives two flows from one cold repository flow
  shares it (`shareIn`) instead of collecting it twice.
- **Writes** touching receipts/products/mappings: `WriteCoordinator.write { }` (Mutex +
  transaction). Reads: `Flow` from Room; lists use Paging (`PagingSource` in the DAO,
  `Pager` in the repository).
- Dynamic list SQL: `repository/ReceiptQuery.kt` builds `SimpleSQLiteQuery` with bound
  args on top of `ReceiptDao.LIST_SELECT` (the `ReceiptListRow` projection: never
  `source_data` or the URL, only `from_other_phone`, whether it is null: synced receipts carry
  `core:ui` `FromOtherPhoneMark` in the list and on Home); `ROW_SELECT` (`receipt.*`) is for one receipt (detail). Search
  is `instr(normalized_col, :key) > 0` on `normalizeText(query)`. Orderings end with the id in
  the same direction, matching the `(issued_at, id)` and `(gross_total_minor, id)` indexes.
- Database key (`db/KeyManager.kt`, the only code touching keys): 32 random bytes, wrapped by an
  AES-GCM Android Keystore key in `noBackupFilesDir`, passed in raw-key form
  (`sqlCipherRawKey`, no PBKDF2 per connection). If unwrapping fails, never create a new empty
  database over the old file.
- Repositories return `core:model` types, `Result<T>` for suspend calls, `Flow` for reads.
  Mapping entity/row → model is private in the `*Impl` (`toModel()`), using
  `HouseholdZone.current()` for dates.
- DI: `di/AppModule.kt` (Engine, WorkManager, DataStore, `@ApplicationScope`,
  `RepositoryModule` @Binds; `di/SyncModule.kt` for sync), `di/DatabaseModule.kt` (DB + one
  `@Provides` per DAO),
  `di/CoreModule.kt` (`Clock`, `@IoDispatcher`, `@DefaultDispatcher`). Parsing and analytics
  on Default, database and network on IO.
- Repositories: Receipt, ReceiptOptions, ReceiptFilterStore (the receipt list's last filters
  in DataStore, ids only, names re-read from the database), Household, Product, Review (queue,
  link, create, reclassify), Catalog (categories, merchant rename and default category;
  refusals are `CatalogException`), Analytics (summary, by category with buckets, by merchant,
  top products, monthly trend, Home pace, over `AnalyticsDao`; `changes()` re-emits on table
  changes), Settings (preferences shown in Settings), Backup (export, open, restore; errors are
  `BackupException(BackupError)`). Each has an `*Impl`. `products/AutoCategoryBackfill`
  categorizes old products and merchants once (versioned in preferences). Goals (budgets and
  the e-payment target: `GoalsRepository`; the rules are core:engine `analytics/Budgets.kt`
  and `analytics/EPayments.kt`; the annual income is set through `HouseholdRepository`). The
  e-payment total counts `payment_method` card/bank receipts whole and `mixed` ones by their
  card and bank payments, read from `source_data` by core:engine `electronicPartOf` (credit
  notes subtract; unreadable data counts 0). Home and Insights pass the month's category
  spending they already loaded to `getMonthBudgets` instead of querying it twice.
  Sync (`SyncRepository`: the on/off flag in DataStore, off by default, turning it on makes
  the household key; paired phones; the paged history; the last successful sync for the
  Settings row; `pairingCode`, `join`, `leave`).
- **Household sync merge** (docs/design/household-sync.md): core:engine `sync/` is pure:
  `SyncSchema.TABLES` (per synced table: unique keys, foreign keys with their `ON DELETE`,
  local columns such as `receipt.source_url`; parents first, the same order as
  `BackupArchive.TABLES`, a test checks), `Snapshot` / `SnapshotCodec` (gzip JSON lines,
  values as stored), `SyncMerge.merge(local, remote)` (same result whichever phone merges;
  copies of one thing under two ids become the smaller id; later `updated_at` wins;
  tombstones by id or natural key). core:data `sync/SnapshotStore` reads the snapshot and
  applies a `MergeResult` (removals children first, then only changed rows parents first,
  then import jobs repointed); `SyncMerger` runs both under `WriteCoordinator`. A schema
  change to a synced table may need its `TableSpec` updated.
- **Household sync pairing:** the household key (`HouseholdKey`, 32 bytes) is held by
  core:data `sync/SyncDevice` (`KeystoreSyncDevice`: wrapped by its own Keystore alias in
  `noBackupFilesDir`, next to the device id; tests use a fake). The QR text is core:engine
  `PairingCode`. feature:household draws it with zxing core (`HouseholdCodeDialog`, a
  secure window) and reads it with its own zxing-cpp scanner (`JoinScanner`). The code holds
  the key: never in saved state, a destination, a log or `toString()`.
- **Household sync connection:** core:engine `SyncSession` is the protocol (hellos sealed
  with the household key, then a per-session key; the client sends its snapshot, the server
  merges and answers with its own) over `Frames`; `LocalNetwork.isLocal` is the address rule.
  core:data `sync/SyncRunner` runs one session at a time over a TCP socket and writes the
  history and paired phones; `SyncDiscovery` is NSD (`_eksoda._tcp`, device id and household
  tag) and the Wi-Fi address (`localAddress`); `SyncRuntime` (started by
  `EksodaApplication`) listens on the Wi-Fi address and discovers only while the app is in
  the foreground, sync is on and a key is held (`SyncDevice.keyVersion`: a new key restarts
  it), and syncs automatically or on "Sync now". A sync already running answers `busy`;
  nothing a session throws escapes (`internal`); history entries are always finished. The
  repository reaches it through `SyncControl`.
- **Backup** (`core:data/backup/`): `BackupFile.kt` writes `EKSODABK` | header length (uint32
  BE) | header JSON (format, app version, created at, PBKDF2-HMAC-SHA256 600k iterations +
  16-byte salt, 12-byte nonce) | AES-256-GCM ciphertext; the header is the GCM additional data
  and the passphrase is NFC-normalized, never stored. `BackupArchive.kt`: a ZIP of
  `manifest.json` (schema, counts, household, receipt date range) + `<table>.ndjson`, rows by
  column name (older files restore), decimals as strings without the scale suffix
  (`"gross_total": "32.10"`). `TABLES` is parents first and a test checks it lists every table.
  Restore replaces everything in one `WriteCoordinator` transaction (deferred foreign keys,
  then `foreign_key_check`), then requeues pending import jobs. Export and restore run in
  `@ApplicationScope`, not WorkManager: worker input is stored unencrypted, and the passphrase
  must never reach disk. Passphrase fields use `remember`, not `rememberSaveable`. Files go
  through the Storage Access Framework (`CreateDocument` / `OpenDocument`).

## UI pattern (follow `feature/receipts`, `feature/scan`, `feature/products`, `feature/home`)

- ViewModel: `@HiltViewModel class XViewModel @Inject constructor(repo…)`,
  `val state: StateFlow<XUiState>` (immutable data class or sealed Loading/Loaded/Missing),
  `fun onEvent(event: XEvent)` with a `sealed interface XEvent`, one-shot effects through
  `Channel<XEffect>(BUFFERED)` exposed as `val effects = _effects.receiveAsFlow()`.
  `stateIn(viewModelScope, WhileSubscribed(5_000), initial)`.
- Screen file: `fun XRoute(onBack…, viewModel: XViewModel = hiltViewModel())` collects
  with `collectAsStateWithLifecycle()` and effects in a `LaunchedEffect`; it calls a
  stateless `fun XScreen(state, onEvent, …)`; sub-parts are `private`/`internal`
  composables (big screens split into `XSections.kt`). Components have a `@Preview`.
- Navigation: `@Serializable` destinations in `app/ui/Destinations.kt` (ids only, **never a
  receipt URL**), wired in `MainNavHost` in `app/ui/EksodaApp.kt`; top-level sections are tabs
  in `app/ui/MainTabs.kt` (`EksodaNavigationBar` under 600 dp, `EksodaNavigationRail` wider,
  Scan as the raised button); add the feature to `app/build.gradle.kts` dependencies. Elements
  that travel between screens use `Modifier.sharedBoundsIfAvailable(SharedKeys…)` (`core:ui`
  `motion/`). Back on a tab other than Home returns to the previous tab; only Back on Home
  leaves the app.
- Insights cards fold to their title (`FoldableCard` in feature:insights, `SectionCard`'s
  `expanded`/`onToggleExpanded`): a chevron, and a tap on the title; the folded set is kept in
  `SettingsRepository.foldedInsightsCards`. The summary card at the top does not fold.
- A category row in Insights opens `CategoryDetailDestination(categoryId, from, to)`
  (feature:insights `category/`): the category over that period by product and by shop, from
  `AnalyticsRepository.getCategoryDetail`. Its total equals the row's: product lines plus what
  the receipts of merchants whose default it is hold beyond their lines (`AnalyticsDao`
  `categoryProducts`, `categoryLinesByMerchant`, `categoryRemaindersByMerchant`; core:engine
  `categoryShopTotals`). The "Uncategorized" row opens it with a null `categoryId`: product
  lines with no product category at shops with no default (SQL `IS :categoryId`), to sort.
- **Look: `docs/design/ui-redesign.md`** (color meanings, type, charts). Colors from
  `MaterialTheme.colorScheme` or `EksodaTheme.colors`, never a hex in a screen. Cards are
  `SectionCard`, small labels `OverlineText`, figures `StatRow`.
- **Compose stability:** `config/compose/stability.conf` (applied by `eksoda.android.compose`)
  marks every `core:model` type stable, so list rows skip redrawing when their data is equal.
  Keep `core:model` immutable (no `var`, no mutable collections). List rows are plain
  `Row`s, not `ListItem` (see `ReceiptRow`).
- **Periods:** `core:model` `PeriodPreset` (Today, This week from Monday, This month, Last month,
  3 months, This year) serves Insights (`soFar(today)`) and the receipt filter (`range(today)`,
  the whole calendar period). Custom ranges use `core:ui` `DateRangeDialog`.
- Screens without a `Scaffold` (Home, Insights) set `LocalContentColor` to `colors.ink`
  themselves, or plain `Text` draws black.
- Strings: per module, every string in `values/`, `values-el/` and `values-en/` (lint fails
  otherwise). Plurals for counts. The app's language is chosen in Settings
  (`core:ui` `AppLanguage`: `LocaleManager` on 13+, `wrap()` in `attachBaseContext` before).
  Error codes → `importErrorRes(code)`; money → `MoneyText` / `formatMoney(money, appLocale())`;
  category names → `categoryNameRes(slug)`.
- Receipt text (descriptions, merchant names) is shown as printed, never translated.
- Helpers in `core:ui`: `dateFormatter(pattern, locale)` (cached; never
  `DateTimeFormatter.ofPattern` in a composable), `dayTimeLabel(instant)` ("Today, 14:20"),
  `searchKey(text)` for accent-free matching
  of in-memory lists, `DelayedProgressIndicator` (no spinner flash on fast loads), and
  `EmptyState` with a one-line description, and `AmountDialog` for typing money
  (`parseMoneyInput` reads "18.000" as 18000 in either convention); `IncomeDialog` (on top of
  it) is the one annual-income dialog, shared by Insights and Settings. Text fields keep their text
  in the screen
  (`rememberSaveable`) and send changes to the ViewModel; never feed a `TextField` from a
  Flow. Lazy rows use `Modifier.animateItem()`.
- The splash screen stays up while `AppState` is `LOADING` (`MainActivity`).

## Hard rules that are easy to break (beyond the ground rules)

- `BigDecimal` from `String`/`Long` only (detekt enforces; only `core:designsystem/chart`
  converts to Float, for drawing). Compare with `compareTo`.
- No `android.util.Log`, `println`, `GlobalScope`, `java.util.Date`/`Calendar` (detekt).
  Inject `Clock` and dispatchers.
- Receipt URLs are stored only in `receipt.source_url` and `import_job.submitted_url`; never
  in nav args or the clipboard. Log host + `fingerprint(url).take(12)` only.
- OkHttp only in `core:engine` (configuration fails elsewhere). No Firebase/ML Kit/GMS
  (`:app:checkNoTelemetry`).
- Tests: JUnit 5 + AssertK in `core:engine` (fixtures in `testdata/fixtures/`); JUnit 4 +
  Robolectric in Android modules (in-memory Room). Every change ships with tests for its new
  paths, warning and error paths included. Coverage (Kover, in `make check`): `core:engine`
  ≥ 90% lines, `core:data` ≥ 60% (target 80%); on pull requests Codecov (`codecov.yml`)
  requires 90% of the changed lines covered and lets the total drop at most 0.5%. Check a file
  with `./gradlew :core:engine:koverXmlReport` (`core/engine/build/reports/kover/report.xml`)
  or `:core:data:koverXmlReportDebug` (`core/data/build/reports/kover/reportDebug.xml`).
  `core:data` tests run the real import pipeline on the fixtures with `ImportHarness`
  (in-memory Room seeded like the app, WorkManager test driver, a fetcher serving
  `testdata/fixtures/`).
