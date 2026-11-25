# Contributing to Eksoda

Thank you for helping. Bug reports, receipt layouts that fail to import, translations, fixes
and features are all welcome. This page explains how to set up the project, what a change
needs, and how a pull request gets through CI and into a release.

By contributing you agree that your contribution is published under the project's
[GNU General Public License v3.0 or later](LICENSE).

## Before you start

- **Small fixes** (a typo, a wrong translation, a crash with an obvious cause): open a pull
  request directly.
- **Anything bigger** (a feature, a new screen, a change to the database or the sync
  protocol): open an issue first and describe what you want to do, so we can agree on the
  approach before you spend time on it.
- **Issues marked [`good first issue`](https://github.com/tsaklidis/MyEksoda/labels/good%20first%20issue)**
  are a good place to begin.

### Never share a receipt link

A receipt's QR link works like a password: whoever has it can open the receipt. **Never paste
a real receipt link in an issue, a pull request, a commit or a log.** If a receipt does not
import, open an issue with the provider's host name (for example `einvoice.s1ecos.gr`), the
shop, and the error the app showed, and say that you can share a sanitized sample page. The
same goes for screenshots: crop out the QR code and any card or loyalty numbers.

### Security issues

Do not open a public issue for a vulnerability. Write to stefanos@tsaklidis.gr instead.

## Setting up

You need:

- **Android Studio** (current stable), which also brings the JDK the build uses (JetBrains
  Runtime 25). Gradle downloads that JDK by itself if it is missing.
- **The Android SDK** with platform 37 (Android Studio's SDK Manager installs it).
- `make` and `git`. Python 3 only for `make codemap`.

```bash
git clone https://github.com/<you>/MyEksoda.git   # your fork
cd MyEksoda
git remote add upstream https://github.com/tsaklidis/MyEksoda.git
make check                                         # everything CI runs; must pass
```

The Makefile assumes Android Studio's JDK on macOS. On Linux or Windows, point `JAVA_HOME` at a
JDK 25 first, for example `JAVA_HOME=/opt/android-studio/jbr make check`. Without `make`
(Windows), run the Gradle tasks of the `check` target in the [Makefile](Makefile) with
`gradlew.bat`.

Useful targets:

```bash
make check     # format check, detekt, Android Lint, unit tests, coverage, no-telemetry, debug build
make test      # unit tests only
make format    # fix ktlint formatting
make build     # debug APK
make install   # install the debug APK on a running emulator or device
make codemap   # regenerate docs/codemap/SYMBOLS.md after adding, moving or renaming declarations
```

All tests run on the JVM (JUnit 5 in `core:engine` and `core:model`, Robolectric in the
Android modules), so no emulator is needed.

### Finding your way around

Start with [`docs/codemap/OVERVIEW.md`](docs/codemap/OVERVIEW.md): the modules, the import
pipeline, the data layer, the UI pattern and the rules. To find a class or function, search
[`docs/codemap/SYMBOLS.md`](docs/codemap/SYMBOLS.md) (every declaration with its file and
line). UI work follows [`docs/design/ui-redesign.md`](docs/design/ui-redesign.md).

## Rules every change follows

These are checked in review, and many by the build itself.

**Privacy**

- **No telemetry.** No analytics, crash reporting, ads, Firebase, ML Kit or Google Play
  services. `make check` fails if one of them reaches the app's classpath.
- **The app talks only to the receipt providers** (and, with household sync on, to the
  household's phones on the local network). No new hosts, no new network libraries; OkHttp is
  used only in `core:engine`.
- **Receipt links are never logged,** shown in notifications, passed in navigation arguments
  or sent anywhere but the provider.
- **Personal data from receipts is never stored:** loyalty codes, card transaction ids,
  terminal ids and signatures are not extracted.

**Code**

- **Behavior lives in `core:engine`**: parsing, validation, classification, matching,
  categories, prices and analytics math, as pure Kotlin with no `android.*` imports.
- **Money, quantities, prices and rates are `BigDecimal`** (created from a `String` or a
  `Long`, compared with `compareTo`), and fixed-point `Long` in the database. Never `Double` or
  `Float`.
- **No `android.util.Log`, `println`, `GlobalScope`, `java.util.Date` or `Calendar`.** Inject
  `Clock` and the dispatchers.
- **Every database schema change needs a migration** that keeps the data, and a case in
  `MigrationTest`. There is no destructive fallback.
- Follow the existing patterns (ViewModel with `StateFlow` state and `onEvent`, `XRoute` and a
  stateless `XScreen`, repositories behind interfaces). OVERVIEW.md describes them.

**Text**

- **Every string is in three files:** `values/` (Greek, the default), `values-el/` (Greek) and
  `values-en/` (English). Lint fails otherwise. The Greek must read naturally to a native
  speaker; if you do not speak Greek, write the English and say so in the pull request.
- **No text in capitals** (no `uppercase()`, no all-caps styles). If a label does not fit,
  change the layout.
- American English in code, comments and docs.

**Tests**

- **Every change ships with tests** for its new paths, warning and error paths included.
- Coverage must stay at `core:engine` ≥ 95% and `core:data` ≥ 92% of lines (`make check`
  verifies it), and on a pull request Codecov wants 90% of the changed lines covered.
- **No real network in tests.** Tests that need it are tagged `live` and do not run in CI.

### A new receipt provider or layout

Providers are in `core/engine/src/main/kotlin/io/eksoda/engine/providers/`, one directory
each with `Urls.kt` (recognizes the link), `Parser.kt` (reads the page) and `Sanitize.kt`
(removes secrets from a sample page). Their sample pages and expected outputs are in
`testdata/fixtures/<provider>/`.

1. Write or extend the provider's sanitizer, and run the real page through it to make the
   fixture. The sanitizer replaces every token, code and identifier (receipt ids,
   authentication codes, loyalty and card numbers, terminal ids, signatures) with a fake of
   the same shape, and fails if an original survives. **Never commit an unsanitized page.**
2. The fixture's link is faked the same way (digits and hex replaced by `0`), never the real
   one.
3. Write the expected output as JSON next to the page.
4. Write or extend the parser and its test, and register a new provider in `Registry.kt`.
5. `DriftTest` parses every fixture again with parts of it blanked or removed: the parser must
   read the page or throw a `ReceiptException`, never anything else.

If this is more than you want to take on, an issue describing the provider is just as
welcome (without the link; see above).

## Making a pull request

1. **Branch from the latest master:**

   ```bash
   git fetch upstream
   git switch --no-track -c fix/short-description upstream/master
   ```

   Use a prefix that says what it is: `feat/`, `fix/`, `docs/`, `chore/`.

2. **Make the change, with its tests.** Keep a pull request to one subject; unrelated
   cleanups go in a pull request of their own.

3. **Run `make check`** and fix what it reports (`make format` fixes the formatting). If you
   added, moved or renamed declarations, run `make codemap` and commit `SYMBOLS.md`. If the
   change alters the architecture, a pipeline step or a pattern, update `OVERVIEW.md` too.

4. **Commit** with a message in the imperative that says what the change does for the app, for
   example `Read Masoutis unit prices before VAT`. Several commits are fine.

5. **Push to your fork and open the pull request** against `master`. Fill in the template:
   what changed, why, and how you tested it. Add screenshots for UI changes. Leave "Allow
   edits by maintainers" on, so the release bump can be added for you if needed.

### The checks a pull request must pass

| Check | What it runs | How to pass it |
|---|---|---|
| **make check** (`ci.yml`) | ktlint, detekt, Android Lint, unit tests, coverage thresholds, no-telemetry, debug build | Run `make check` locally before pushing. When it fails, the job uploads the reports as an artifact named `reports`. |
| **release ready** (`release.yml`) | `make release_check`: the version is newer than master's and the changelog has notes for it | See below. |
| **codecov** | 90% of the changed lines covered, no module dropping more than 0.5% | Test the new code paths, including the error paths. |

### Releases and the version bump

Every merge to master is a release, so a pull request that changes the app carries its own
version bump:

```bash
make bump              # patch, for fixes (0.3.7 -> 0.3.8)
make bump BUMP=minor   # for features (0.3.7 -> 0.4.0)
make release_check     # verifies it, like CI does
```

`make bump` raises the version in `version.properties` and adds a section to
[`CHANGELOG.md`](CHANGELOG.md) with a TODO line. Replace the TODO with release notes written
for the people who use the app (what they will notice, not how the code changed), and commit
both files on your branch.

If you are unsure, you may leave the bump out: the maintainer adds it before merging. If
another pull request is merged first with the same version, bump again from the new master.

A pull request that ships no release (docs, CI or scripts only) gets the **`no release`**
label from the maintainer instead, and needs no bump.

### Review and merge

The maintainer reviews every pull request, may ask for changes, and merges it once all checks
are green and the branch is up to date with master. Publishing the release (the signed APK and
the GitHub release) is done by the maintainer after the merge; contributors never need the
release key.

## Your fork and the name

You are free to build and publish your own app from this code under the GPL, but the name
"Eksoda", the logo and the application id `io.eksoda` are not licensed: a fork needs its own.
See [TRADEMARKS.md](TRADEMARKS.md).
