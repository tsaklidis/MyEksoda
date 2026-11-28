# Code map: read this instead of the codebase

How the app is built, so a session can find the right files without reading them. Written by
hand; `SYMBOLS.md` beside it is generated (`make codemap`) and lists every declaration with its
line. UI rules are in `docs/design/ui-redesign.md`.

**Lookup order:** this file → `rtk grep -n '<Name>' docs/codemap/SYMBOLS.md` → open only that
line range. Keep this file current when a change adds a module, a pipeline step or a pattern.

## What the app promises

Your receipts never leave the phone: no account, no analytics, no crash reporting.
The only network traffic is HTTPS GETs to the receipt providers (Impact, s1ecos, Entersoft,
AADE, Epsilon Digital, Parochos), and, only when the user turns household sync on, encrypted sessions with
the household's phones on the local network (private addresses, never the internet).
Data is in a SQLCipher database; Android cloud backup is off (`allowBackup=false`,
`dataExtractionRules`); the user's own encrypted export is the only backup.

Not built yet: backup reminder, restore from onboarding, app lock, hide content
(`FLAG_SECURE`), delete all data, light theme, R8 and a baseline profile for the release build
(it is signed and published, but not minified). What is
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
                       categoryNameRes); chain logos (component/MerchantLogos.kt,
                       res/drawable/merchant_logo_*.xml)
core:testing           Test-only (testImplementation of app and the features, never production):
                       MainDispatcherRule, keepCollecting, a Fake<X>Repository per core:data
                       repository (Recorder: calls, failNext/failOperation/failure), staticPage,
                       test<Model> builders
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

Chain logos: `core:engine` `merchants/Brand.kt` (`merchantBrand`) maps a merchant's VAT number
to a chain (never the name alone: the chain names are also surnames). A VAT number two chains
share (METRO ΑΕΒΕ: My Market and Metro Cash & Carry) shows the logo until the household renames
the merchant to something that does not name the chain. core:data reads it from the merchant's
own row on every screen (`repository/Brands.kt`; the receipt queries select the merchant's
country, VAT and rename flag) into `Merchant.brand` / `MerchantSummary.brand`; `MerchantAvatar`
tints the logo with the ink color instead of the initials. Logos are alpha masks (white paths,
dim parts as `fillAlpha`, holes for cutouts) from Wikimedia Commons, My Market's redrawn from a
supplied image; source and license are in each drawable's header. A new chain: a `Brand` entry
and its VAT number (engine), the same-named `MerchantBrand` (model; a test keeps them in step),
a drawable and its arm in `core:ui` `MerchantLogos.kt` (the `when` will not compile without it).

## The import pipeline (the heart of the app)

```
Scan/paste/share/photo (feature:scan; ScanViewModel: the camera skips a link already sent this
 session, a photo or paste always sends it, a link whose submit failed unexpectedly is forgotten
 so any of the three retries it; photos are read through PhotoQrReader, zxing-cpp's QrImageDecoder)
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

Providers: `engine/providers/{impact,s1ecos,entersoft,aadefhm,epsilon,parochos}/` each with
`Urls.kt` (recognize), `Parser.kt` (layouts → `CanonicalReceipt`), `Sanitize.kt`; Impact also
`Labels.kt`, `Views.kt`. Shared: `Base.kt` (`Provider`, `Layout`, `UrlIdentity`,
`maskUrl`), `Registry.kt` (`REGISTRY`), `Html.kt` (`textOf`, Jsoup), `Sanitizing.kt`.
The registry finds a provider by exact host (`Provider.hosts`), then by `Provider.hostPattern`
(only Epsilon has one). One host, one provider: a listed host matching another provider's
pattern fails at startup, and a host two patterns match is refused. A provider with a pattern
must bring its own `fetchDocument` (it allows the link's own host only). A pattern host that
serves no receipts fails at the fetch like any unreadable link; one that does not resolve is
retried like any network failure (DNS cannot tell a host that does not exist from a resolver
outage, so a DNS failure never refuses a link).
Epsilon Digital (any `epsilondigital-<name>.epsilonnet.gr` host, `HOST_PATTERN`; the hosts
known to serve receipts, one merchant's such as `-sklavenitis`, `-abmarket`, `-marketin` or
shared ones such as `-3rd` and `-3rdpartb`, are `HOSTS`) is not
read from its viewer page (behind a Cloudflare Turnstile check) but from the receipt's detailed
myDATA XML (`/filedocument/GetInvoiceDocDetailed/<uuid>`, Jsoup XML parser). The XML has no
time of day (the time is the date only) and often no merchant name (merchant hosts never send
one, shared hosts usually do; Masoutis on `3rdpartb` does not). A name in the XML wins;
otherwise it comes from `ISSUER_NAMES` in `epsilon/Urls.kt`, keyed by issuer VAT and valid on
every Epsilon host. Without either the receipt is still read with an empty legal name, and the
merchant is called by its VAT number until a receipt names it (`merchants/Merchants.kt`
`defaultDisplayName`; an empty name never overwrites a known one). A new Epsilon host needs no
code to be read; add it to `HOSTS` once it is seen serving receipts (the record), and its
issuer's legal name to `ISSUER_NAMES` when the name is verified.
Parochos (`srv.parochos.gr`, `srv2`, `srv3`; pharmacies) runs Epsilon Digital's platform under
its own hosts: the same `/fd/` and `/DocViewer/` links (`epsilon/Urls.kt` `splitLink`, with the
host rule as a parameter) and the same detailed XML, read by Epsilon's layout, sanitizer and
fetch under its own provider code and parser version (`epsilon/Parser.kt` `Site`: code,
version, link rule, issuer names; `parseXmlDocument`; `epsilon/Sanitize.kt` `fetchXml`). Its
viewer is a Blazor app drawn over a websocket, so the XML is the only readable form. Its XML
never names the issuer either: `parochos/Urls.kt` `ISSUER_NAMES` (the name is in the receipt's
UBL, `/filedocument/getfile?fileType=4&documentId=<uuid>`, which is not fetched). A link on an
Epsilon host is Epsilon's and one on a Parochos host is Parochos's: each refuses an XML that
prints the other's link.
Impact: the link's 40-hex id is usually the myDATA UID but not always; when it differs, the
page's tax view must print the link itself. The tax view's VAT category decides a line's rate;
the commercial "Κατηγορία Φ.Π.Α" column, a rate ("24") or the category's name with its
mainland and island rates ("ΦΠΑ Κανονικός (24/17)%"), must allow it (`lineRate`). Unit prices
are with VAT unless more of the receipt's lines fit them before VAT (`inferPriceBasis`; e-shops).
Buyer data is never read: a party table other than the issuer's, the address table nested in it,
and the delivery address (tax header `OtherDeliveryNoteHeaderDeliveryAddress`, commercial
`section-distributiondetails-destination`, `Labels`); the sanitizer replaces the same cells.
s1ecos (`einvoice.s1ecos.gr`) serves Impact's pages under its own host: its own provider code and
parser version, read by Impact's layout (`impact/Parser.kt` `Site`: code, version, printed-link
rule; `parsePage`) and Impact's sanitizer. Its QR link is `/v/<prefix><VAT>-<number>-<40-hex
authentication code>-<GUID>` (`s1ecos/Urls.kt`); the page prints the `/p/` form, which must have
the same issuer and access GUID (`printsLink`). Neither 40-hex id is the myDATA UID.
Entersoft (`e-invoicing.gr`) has two link forms (`entersoft/Urls.kt` `EntersoftLink`): the
current `/edocuments/ViewInvoice/<ofenm>/<uuid>_<token>` (`DocumentLink`) and, printed until
2025, `/edocuments/ViewInvoice?ct=…&id=<40 hex>&s=…&h=<token>` (`LegacyLink`; its id is the
receipt's AuthenticationCode, not the myDATA UID). Either opens a shell page whose frame
(`/api/GetInvoice?…`) must name the same receipt (`framedBy`). The issuer VAT is the nine
digits that start the "Α.Φ.Μ" line; Sklavenitis prints its phone and ΓΕΜΗ after them.
Entersoft does not say whether its unit prices include VAT (LIDL's do, Masoutis' do not). The
parser (`entersoft/Parser.kt` `unitPriceBasis`) takes the issuer's basis when it is known
(`NET_PRICED_ISSUERS`, by VAT number); otherwise the basis more of the receipt's lines fit
(`validation/Validation.kt` `inferPriceBasis`), with VAT on a tie. R5 checks a price before VAT
with the line's VAT rate added. A merchant found printing prices before VAT is added to
`NET_PRICED_ISSUERS`.
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
  `BackupException(BackupError)`). Each has an `*Impl`. `sync/MerchantVatRepair` (every
  start, and after every sync merge in `SyncMerger` and every backup restore, in a transaction
  of its own so a failed repair never undoes them; one query when there is nothing to do)
  moves receipts the first Entersoft parser (`entersoft/1`, before 0.3.10) saved under a Greek
  VAT followed by more digits to the right merchant. Only with that parser's receipts as
  evidence, never from a VAT's shape alone, and never for a merchant with another provider's
  receipts. The rule and its query (`NEEDS_REPAIR_SQL`) are core:engine `sync/VatRepair`; a
  core:data test checks they agree. `VatRepair.patch` gives the corrected rows, each just newer
  than its stored copy, merged in with `SyncMerge` like another phone's copy, so merchants,
  stores, mappings and products join and the household converges; the merchant whose details
  are kept is the one the user renamed, else the one already under the right VAT.
  `products/AutoCategoryBackfill`
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
  history and paired phones; `SyncDiscovery` (behind `PeerDiscovery`) is NSD
  (`_eksoda._tcp`, device id and household tag) and the Wi-Fi address (`localAddress`);
  `SyncRuntime` (started by
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
  ≥ 97% lines and ≥ 87% branches, `core:data` ≥ 95% lines and ≥ 79% branches, each feature's
  ViewModel classes ≥ 94% lines and ≥ 80% branches (`AndroidFeatureConventionPlugin`; composables
  and Hilt code are not counted, so a new ViewModel needs its test). The floors sit just under
  the measured coverage: raise them as tests are added, never lower them to let a change
  through. On pull requests Codecov (`codecov.yml`)
  requires 90% of the changed lines covered and lets each module (flag) drop at most 0.5%
  (status checks need the Codecov GitHub App). Check a file
  with `./gradlew :core:engine:koverXmlReport` (`core/engine/build/reports/kover/report.xml`)
  or `:core:data:koverXmlReportDebug` (`core/data/build/reports/kover/reportDebug.xml`).
  Every ViewModel has a `<Name>ViewModelTest` on `core:testing`: declare `MainDispatcherRule`
  first (it sets Main before the ViewModel properties are built), `keepCollecting(state)` for a
  `WhileSubscribed` state, Turbine for effects, `asSnapshot()` for paged lists. A fake's reads
  fail (`NotFaked`) until the test sets them, and its paged lists are `staticPage` (a bare
  `PagingData.from` never finishes loading, and `asSnapshot` hangs). A fake's writes fail with
  `failNext("op")` (the next call only: fail, then succeed on retry), `failOperation("op", e)`
  or `failure` (all); every write, `importReceipt` too, is in `calls`. To see intermediate
  states, pass `MainDispatcherRule(StandardTestDispatcher())` and step with `runCurrent` /
  `advanceUntilIdle` (`ScanPhotoTest`).
  `core:data` `CriticalJourneysTest` is what the app is for, end to end: every fixture with an
  expected output is imported through the real pipeline and stored as its parser reads it (a
  new provider's sample is checked there by itself), and a receipt shows in the list, Home and
  Insights and leaves them all when deleted.
  `core:data` tests run the real import pipeline on the fixtures with `ImportHarness`
  (in-memory Room seeded like the app, WorkManager test driver, a fetcher serving
  `testdata/fixtures/`); paged lists are read with `asSnapshot()` (paging-testing).
  `FakeAndroidKeyStore` stands in for the Android Keystore (KeyManager, KeystoreSyncDevice);
  `SyncDiscovery` runs on Robolectric's NSD and connectivity shadows; `SyncRuntimeTest`
  syncs two phones over loopback through a fake `PeerDiscovery` (`SyncTestPhone` is the
  shared test phone; its sockets time out, so a regression fails instead of hanging). In `core:engine`,
  `DriftTest` parses every fixture again with each text blanked or garbled and each element
  removed, one at a time: a parser reads the page or throws a `ReceiptException`, never
  anything else.
  The receipt scanner's settings (feature:scan `QrReading`: frame size and zxing-cpp options,
  shared by the camera and photos; `FrameQrReader` gives every third frame the thorough reading)
  are measured by `QrSampleSetTest`, an instrumented test (zxing-cpp is native) on drawn hard
  codes with made-up links: `make qr_samples` on a running emulator or device, not part of
  `make check`. Change the scanner's settings against its report.
