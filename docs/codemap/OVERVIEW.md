# Code map: read this instead of the codebase

A compact map of how the app is built, so a session can find the right files without
reading them. It is written by hand; `SYMBOLS.md` next to it is generated
(`make codemap`) and lists every declaration with its line number.

**Lookup order (cheapest first):**

1. This file: which module and file own the thing.
2. `rtk grep -n '<Name>' docs/codemap/SYMBOLS.md`: the exact file and line.
3. Open only that range: `rtk read <file>` or `sed -n '<from>,<to>p' <file>`.
4. A guide section, never the whole guide: `scripts/guide.sh 7.4` (standalone guide) or
   `scripts/guide.sh 6.4.2 android-app`. `scripts/guide.sh toc` lists the sections.

**The app is standalone and Android-only. This repository is the only source of truth.**

Keep this file current: when a change adds a module, a pipeline step, a pattern or a
milestone result, update the matching section here in the same change, and run
`make codemap`.

## Status (2026-11-16)

| Milestone | State |
|---|---|
| L0 foundation | Done |
| L1 engine core, L2 providers + fetcher | Built, **few tests yet** (owner's choice; the planned Python test vectors are cancelled). `make check` fails at `:core:engine:koverVerify` until tests exist |
| L3 storage + import | Built: schema, SQLCipher, KeyManager, Persister, ImportService/Worker. No tests |
| L4 capture + receipts UI | **Done** (2026-11-15): onboarding, scan, imports list, share dialog, receipts list/filters/detail, swipe actions, remembered filters |
| L5 products + review | Built (2026-11-16), awaiting `make check`: `feature:products` (list, detail, edit, merge, review queue, categories, merchants), `core:data/products/ReviewService`, `imports/ProductMatcher`. **UI redesign applied (2026-11-16, owner decision):** dark three-color theme, Plex Mono numbers, new components and charts, Product detail per `docs/design/ui-redesign.md` §5; not yet compiled |
| L6 analytics + home | In progress (2026-11-16): `AnalyticsDao` + `AnalyticsRepositoryImpl`, `engine/analytics/Combine.kt`, Home and Insights per the mockups, `feature:settings` (language, notifications, catalog links, credits), automatic categories (`engine/products/AutoCategory.kt`: products by name/brand, merchants by activity, eating out at cafés; `AutoCategoryBackfill` v2), tab back history in `MainTabs`, price chart by change of price (`pricePoints`) |
| L7 | Not started |

The authoritative table is the guide's §16 (`scripts/guide.sh 16`).

## Modules and dependency rules

```
app                    Application, MainActivity, AppViewModel, nav graph (ui/EksodaApp.kt,
                       ui/Destinations.kt), Hilt root. Depends on the features it shows.
core:engine   (JVM)    Pure Kotlin rules: parsers, matching, prices, analytics math. NO android.*.
                       Explicit API. Owned here (first ported from the retired server).
core:model    (JVM)    Domain types the UI sees (Receipt, Money, ImportJob, Period, filters…)
core:database          Room + SQLCipher: entities (entity/Entities.kt), DAOs, CategorySeed
core:data              Repositories (interface + Impl), imports/ pipeline, KeyManager,
                       WriteCoordinator, Fixed, DI modules (di/)
core:designsystem      Theme (the redesign's dark scheme + EksodaColors, Inter + IBM Plex Mono,
                       shapes), components (SectionCard, ChangeBadge, HeroAmount, StatRow,
                       OverlineText, PeriodSelector, SegmentedToggle, navigation bar/rail +
                       ScanButton, CategoryTile/Icon, MerchantAvatar, groupedItem, Button),
                       chart/ (MinMaxBar, RangeDotPlot, BarChart, LineChart, DonutChart,
                       ShareBar), text/monoNumbers
core:ui                Shared composables (EmptyState, MoneyText, HeroMoney) and text helpers
                       (formatMoney, formatAmount, currencySymbol, dayLabel, importErrorRes,
                       categoryNameRes)
feature:*              Screens. See only core:data interfaces, core:model, core:ui,
                       core:designsystem (wired by the eksoda.android.feature plugin)
build-logic            Convention plugins: eksoda.android.{application,library,compose,
                       feature,room}, eksoda.jvm.library, eksoda.hilt; Quality.kt (Spotless,
                       detekt, Kover); NoTelemetryCheck.kt
```

Package roots: `io.eksoda` (app), `io.eksoda.engine` (engine), `io.eksoda.core.<module>`,
`io.eksoda.feature.<feature>`. Sources are under `src/main/kotlin/`.
Short paths in this file drop that prefix: `core/data/util/Fixed.kt` is
`core/data/src/main/kotlin/io/eksoda/core/data/util/Fixed.kt`; `SYMBOLS.md` has full paths.

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

Providers: `engine/providers/{impact,entersoft,aadefhm}/` each with `Urls.kt`
(recognize), `Parser.kt` (layouts → `CanonicalReceipt`), `Sanitize.kt`; Impact also
`Labels.kt`, `Views.kt`. Shared: `Base.kt` (`Provider`, `Layout`, `UrlIdentity`,
`maskUrl`), `Registry.kt` (`REGISTRY`), `Html.kt` (`textOf`), `Sanitizing.kt`.
Fetcher: `engine/fetch/SafeFetcher.kt` (the only OkHttp client), `PublicAddress.kt`.

Python-compatible text helpers (kept because stored keys depend on them; reuse them in
parsers, don't reinvent):
`text/PyStrings.kt` (`pySplit`, `pyStrip`, `pyIsDigit`, `pyRegex` = Unicode-aware regex),
`text/PyDecimal.kt` (`pyDivide` = 28 digits HALF_EVEN, `pyStr`), `text/PyTime.kt`
(`pyIsoFormat`, `pyLocalToOffset`), `text/Text.kt` (`normalizeText`, `stripDiacritics`,
`collapseWhitespace`, `fingerprint`), `text/Numbers.kt` (`parseDecimal`, `parseDate`),
`text/SequenceMatcher.kt`.

## Data layer

- **Tables** (`core/database/.../entity/Entities.kt`, `HouseholdEntity.kt`): household,
  category, merchant, store, product, merchant_product_mapping, receipt, receipt_item,
  import_job, tombstone. One DB file = one household (no `household_id`). UUID `TEXT` ids,
  `created_at`/`updated_at` epoch ms. Deletes write a `tombstone` row.
- `EksodaDatabase` version **4**, `exportSchema = true`; schema JSON in
  `core/database/schemas/`. Pre-release: bump the version and let the schema change; no
  migrations until L7 (then `MigrationTestHelper`).
- **Decimals:** columns are fixed-point `Long` with a suffix: `_minor` (2), `_e2` (2),
  `_e4` (4), `_e6` (6). Convert only with `core/data/util/Fixed.kt`
  (`Fixed.toLong(v, Fixed.E4)`, `Fixed.toDecimal(l, Fixed.MINOR)`).
- **Writes** touching receipts/products/mappings: `WriteCoordinator.write { }` (Mutex +
  transaction). Reads: `Flow` from Room; lists use Paging (`PagingSource` in the DAO,
  `Pager` in the repository).
- Dynamic list SQL: `repository/ReceiptQuery.kt` builds `SimpleSQLiteQuery` with bound
  args on top of `ReceiptDao.LIST_SELECT` (the `ReceiptListRow` projection: never
  `source_data` or the URL); `ROW_SELECT` (`receipt.*`) is for one receipt (detail). Search
  is `instr(normalized_col, :key) > 0` on `normalizeText(query)`. Orderings end with the id in
  the same direction, matching the `(issued_at, id)` and `(gross_total_minor, id)` indexes.
- The SQLCipher key is passed in raw-key form (`sqlCipherRawKey`, `db/KeyManager.kt`): no
  PBKDF2 per connection.
- Repositories return `core:model` types, `Result<T>` for suspend calls, `Flow` for reads.
  Mapping entity/row → model is private in the `*Impl` (`toModel()`), using
  `HouseholdZone.current()` for dates.
- DI: `di/AppModule.kt` (Engine, WorkManager, DataStore, `@ApplicationScope`,
  `RepositoryModule` @Binds), `di/DatabaseModule.kt` (DB + one `@Provides` per DAO),
  `di/CoreModule.kt` (`Clock`, `@IoDispatcher`, `@DefaultDispatcher`).
- Repositories: Receipt (+Impl), ReceiptOptions (+Impl), ReceiptFilterStore (+Impl: the
  receipt list's last filters in DataStore, ids only, names re-read from the database),
  Household (+Impl), Product (+Impl), Review (+Impl: queue, link, create, reclassify),
  Catalog (+Impl: categories, merchant rename and default category), Analytics (+Impl:
  summary, by category with buckets, by merchant, top products, monthly trend, Home pace,
  over `AnalyticsDao`; `changes()` re-emits on table changes), Settings (+Impl: preferences
  shown in Settings). Refused catalog writes are `CatalogException`.
  `products/AutoCategoryBackfill` categorizes old products once.

## UI pattern (follow `feature/receipts`, `feature/scan`, `feature/products`, `feature/home`)

- ViewModel: `@HiltViewModel class XViewModel @Inject constructor(repo…)`,
  `val state: StateFlow<XUiState>` (immutable data class or sealed Loading/Loaded/Missing),
  `fun onEvent(event: XEvent)` with a `sealed interface XEvent`, one-shot effects through
  `Channel<XEffect>(BUFFERED)` exposed as `val effects = _effects.receiveAsFlow()`.
  `stateIn(viewModelScope, WhileSubscribed(5_000), initial)`.
- Screen file: `fun XRoute(onBack…, viewModel: XViewModel = hiltViewModel())` collects
  with `collectAsStateWithLifecycle()` and effects in a `LaunchedEffect`; it calls a
  stateless `fun XScreen(state, onEvent, …)`; sub-parts are `private`/`internal`
  composables (big screens split into `XSections.kt`).
- Navigation: `@Serializable` destinations in `app/ui/Destinations.kt` (ids only, **never a
  receipt URL**), wired in `MainNavHost` in `app/ui/EksodaApp.kt`; top-level sections are tabs
  in `app/ui/MainTabs.kt` (`EksodaNavigationBar` under 600 dp, `EksodaNavigationRail` wider,
  Scan as the raised button); add the feature to
  `app/build.gradle.kts` dependencies. Elements that travel between screens use
  `Modifier.sharedBoundsIfAvailable(SharedKeys…)` (`core:ui` `motion/`).
- **Look: `docs/design/ui-redesign.md`** (mockups linked there). Dark base, two accents with one
  meaning each: terracotta = this period, spending, "more", "pricier", attention, Scan;
  periwinkle = previous period, selection, "less", "cheaper", links, active tab. No green; red
  (`colorScheme.error`) only for errors and destructive actions; color never alone (arrows and
  words). Read colors from `MaterialTheme.colorScheme` or `EksodaTheme.colors` (`EksodaColors`:
  spend*/compare* families, panel, inset, tile, line, guide, inks, `rank`), **never a hex in a
  screen**. `outline`/`guide` (#6B737D) is for lines only, never text. Cards are `SectionCard`;
  small labels are `OverlineText` (sentence case, wraps to 2 lines); figures are `StatRow`
  (side by side when they fit, else one per line); charts come from
  `core:designsystem/chart` (BigDecimal in, colors by rank, content description required).
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
  (`core:ui` `AppLanguage`: `LocaleManager` on 13+, `wrap()` in `attachBaseContext` before). Error codes → `importErrorRes(code)`; money → `MoneyText` /
  `formatMoney(money, appLocale())`; category names → `categoryNameRes(slug)`.
- Receipt text (descriptions, merchant names) is shown as printed, never translated.
- Speed and polish helpers in `core:ui`: `dateFormatter(pattern, locale)` (cached; never
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
  `BigDecimal(Double)`). Compare with `compareTo`.
- No `android.util.Log`, `println`, `GlobalScope`, `java.util.Date`/`Calendar` (detekt).
  Inject `Clock` and dispatchers.
- Receipt URLs are secrets: no logs, notifications, nav args, clipboard. Log host +
  `fingerprint(url).take(12)` only.
- OkHttp only in `core:engine` (configuration fails elsewhere). No Firebase/ML Kit/GMS
  (`:app:checkNoTelemetry`).
- `testdata/fixtures/` belongs to this repository: edit fixtures
  with the parser they test; there is nothing to sync. Never commit, push or `git init`.
- Back on a tab other than Home returns to the previous tab (`MainTabs` history); only Back
  on Home leaves the app.
