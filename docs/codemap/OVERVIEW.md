# Code map: read this instead of the codebase

How the app is built, so a session can find the right files without reading them. Written by
hand; `SYMBOLS.md` beside it is generated (`make codemap`) and lists every declaration with its
line. The look is in `docs/design/ui-redesign.md`. Nothing else is needed.

**Lookup order:** this file → `rtk grep -n '<Name>' docs/codemap/SYMBOLS.md` → open only that
line range. Keep this file current when a change adds a module, a pipeline step or a pattern.

## What the app promises

Your receipts never leave the phone: no server, no account, no analytics, no crash reporting.
The only network traffic is HTTPS GETs to the receipt providers (Impact, Entersoft, AADE,
Epsilon Digital).
Data is in a SQLCipher database; Android cloud backup is off (`allowBackup=false`,
`dataExtractionRules`); the user's own encrypted export is the only backup.

## Status (2026-11-17)

Built: scan/paste/share/photo import with retries, receipts (list, filters, detail, edits),
products (list, detail with price history, edit, merge, review queue), categories, shops,
automatic categories, Home (short: total, attention, top categories, recent), Insights (presets
from Today to Year plus a custom range, pace chart for this month), Settings, encrypted backup
and restore.
Not built: backup reminder, restore from onboarding, app lock (`BiometricPrompt`), hide content
(`FLAG_SECURE`), delete all data, light theme, release build (R8, baseline profile).
Tests are few outside the engine rules (validation, automatic categories), price points and
backup.

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
                       feature:household is empty.
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

Providers: `engine/providers/{impact,entersoft,aadefhm,epsilon}/` each with `Urls.kt`
(recognize), `Parser.kt` (layouts → `CanonicalReceipt`), `Sanitize.kt`; Impact also
`Labels.kt`, `Views.kt`. Shared: `Base.kt` (`Provider`, `Layout`, `UrlIdentity`,
`maskUrl`), `Registry.kt` (`REGISTRY`), `Html.kt` (`textOf`, Jsoup), `Sanitizing.kt`.
Epsilon Digital (one host per merchant, `epsilondigital-<merchant>.epsilonnet.gr`) is not
read from its viewer page (behind a Cloudflare Turnstile check) but from the receipt's detailed
myDATA XML (`/filedocument/GetInvoiceDocDetailed/<uuid>`, Jsoup XML parser). The XML has no
merchant name or time of day: names come from `TENANTS` in `epsilon/Urls.kt` (a new merchant
host is one entry there), the time is the date only.
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

- **Tables** (`core/database/.../entity/Entities.kt`, `HouseholdEntity.kt`): household,
  category (19 system ones seeded), merchant, store, product, merchant_product_mapping,
  receipt, receipt_item, import_job, tombstone. One DB file = one household (no
  `household_id`). UUID `TEXT` ids, `created_at`/`updated_at` epoch ms UTC, plus
  `issued_local_date` (`YYYY-MM-DD` in the household zone). Deletes write a `tombstone` row.
- `EksodaDatabase` version **4**, `exportSchema = true`; schema JSON in
  `core/database/schemas/`. Before the first release: bump the version and let the schema
  change; no migrations yet (then `MigrationTestHelper`). A new table also goes in
  `BackupArchive.TABLES`.
- **Decimals:** columns are fixed-point `Long` with a suffix: `_minor` (2), `_e2` (2),
  `_e4` (4), `_e6` (6). Convert only with `core/data/util/Fixed.kt`
  (`Fixed.toLong(v, Fixed.E4)`, `Fixed.toDecimal(l, Fixed.MINOR)`).
- **Long lists** (receipts, products) page with `listPagingConfig` (`repository/ListPaging.kt`):
  no placeholders, pages loaded one page ahead. The product list has one query per ordering
  (`ProductDao.pagingByName` walks the `normalized_name` index; never a `CASE` in ORDER BY).
- **Writes** touching receipts/products/mappings: `WriteCoordinator.write { }` (Mutex +
  transaction). Reads: `Flow` from Room; lists use Paging (`PagingSource` in the DAO,
  `Pager` in the repository).
- Dynamic list SQL: `repository/ReceiptQuery.kt` builds `SimpleSQLiteQuery` with bound
  args on top of `ReceiptDao.LIST_SELECT` (the `ReceiptListRow` projection: never
  `source_data` or the URL); `ROW_SELECT` (`receipt.*`) is for one receipt (detail). Search
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
  `RepositoryModule` @Binds), `di/DatabaseModule.kt` (DB + one `@Provides` per DAO),
  `di/CoreModule.kt` (`Clock`, `@IoDispatcher`, `@DefaultDispatcher`). Parsing and analytics
  on Default, database and network on IO.
- Repositories: Receipt, ReceiptOptions, ReceiptFilterStore (the receipt list's last filters
  in DataStore, ids only, names re-read from the database), Household, Product, Review (queue,
  link, create, reclassify), Catalog (categories, merchant rename and default category;
  refusals are `CatalogException`), Analytics (summary, by category with buckets, by merchant,
  top products, monthly trend, Home pace, over `AnalyticsDao`; `changes()` re-emits on table
  changes), Settings (preferences shown in Settings), Backup (export, open, restore; errors are
  `BackupException(BackupError)`). Each has an `*Impl`. `products/AutoCategoryBackfill`
  categorizes old products and merchants once (versioned in preferences).
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
- **Look: `docs/design/ui-redesign.md`.** Dark base, two accents with one meaning each:
  terracotta = this period, spending, "more", "pricier", attention, Scan; periwinkle =
  previous period, selection, "less", "cheaper", links, active tab. No green; red
  (`colorScheme.error`) only for errors and destructive actions; color never alone (arrows and
  words). Read colors from `MaterialTheme.colorScheme` or `EksodaTheme.colors` (`EksodaColors`:
  spend*/compare* families, panel, inset, tile, line, guide, inks, `rank`), **never a hex in a
  screen**. `outline`/`guide` (#6B737D) is for lines only, never text. Cards are `SectionCard`;
  small labels are `OverlineText` (sentence case, wraps to 2 lines); figures are `StatRow`
  (side by side when they fit, else one per line); charts come from
  `core:designsystem/chart` (BigDecimal in, colors by rank, content description required).
- **Compose stability:** `compose-stability.conf` (root, applied by `eksoda.android.compose`)
  marks every `core:model` type stable, so list rows skip redrawing when their data is equal.
  Keep `core:model` immutable (no `var`, no mutable collections). List rows are plain
  `Row`s, not `ListItem` (see `ReceiptRow`).
- **Periods:** `core:model` `PeriodPreset` (Today, This week from Monday, This month, Last month,
  3 months, This year) serves Insights (`soFar(today)`) and the receipt filter (`range(today)`,
  the whole calendar period). Custom ranges use `core:ui` `DateRangeDialog`.
- **Never put text in capitals** (no `uppercase()` on UI text, no caps styles). When text does
  not fit, rearrange: wrap, stack (as `StatRow` does), or use a shorter word.
- Screens without a `Scaffold` (Home, Insights) set `LocalContentColor` to `colors.ink`
  themselves, or plain `Text` draws black.
- **Numbers in IBM Plex Mono:** amounts use `MoneyText`/`HeroMoney`, other figures
  `style.tabular()`, mixed sentences `monoNumbers(text)`. Plex Mono has no Greek, ▲ or ▼: never
  set it on words.
- Strings: per module `values/strings.xml` (Greek, the default), `values-el/` (Greek) and
  `values-en/` (English); every string in all three (lint fails otherwise). Plurals for
  counts. The Greek must read naturally. The app's language is chosen in Settings
  (`core:ui` `AppLanguage`: `LocaleManager` on 13+, `wrap()` in `attachBaseContext` before).
  Error codes → `importErrorRes(code)`; money → `MoneyText` / `formatMoney(money, appLocale())`;
  category names → `categoryNameRes(slug)`.
- Receipt text (descriptions, merchant names) is shown as printed, never translated.
- Helpers in `core:ui`: `dateFormatter(pattern, locale)` (cached; never
  `DateTimeFormatter.ofPattern` in a composable), `searchKey(text)` for accent-free matching
  of in-memory lists, `DelayedProgressIndicator` (no spinner flash on fast loads), and
  `EmptyState` with a one-line description. Text fields keep their text in the screen
  (`rememberSaveable`) and send changes to the ViewModel; never feed a `TextField` from a
  Flow. Lazy rows use `Modifier.animateItem()`.
- Categories have no colors: `CategoryTile`/`CategoryIcon` are neutral (icon + name). Dynamic
  color is off and only the dark scheme exists (`theme/Theme.kt`). The splash screen stays up
  while `AppState` is `LOADING` (`MainActivity`).

## Hard rules that are easy to break

- `BigDecimal` from `String`/`Long` only; no `Double`/`Float` (detekt bans `toDouble`,
  `BigDecimal(Double)`; only `core:designsystem/chart` converts to Float, for drawing).
  Compare with `compareTo`.
- No `android.util.Log`, `println`, `GlobalScope`, `java.util.Date`/`Calendar` (detekt).
  Inject `Clock` and dispatchers.
- Receipt URLs are secrets: stored only in `receipt.source_url` and
  `import_job.submitted_url`; no logs, notifications, nav args, clipboard. Log host +
  `fingerprint(url).take(12)` only.
- OkHttp only in `core:engine` (configuration fails elsewhere). No Firebase/ML Kit/GMS
  (`:app:checkNoTelemetry`).
- Tests: JUnit 5 + AssertK in `core:engine` (fixtures in `testdata/fixtures/`); JUnit 4 +
  Robolectric in Android modules (in-memory Room). No live network except tests tagged `live`.
