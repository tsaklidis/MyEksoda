# Eksoda for Android

Eksoda is a standalone, privacy-first Android household expense app built around Greek e-receipts. Simply scan the QR code on a receipt, and Eksoda downloads the official e-receipt data directly from the issuing company. It tracks your spending, itemized product costs over time, and price comparisons across merchants.

## 🔒 Privacy First

- **Your receipts never leave your phone.** There is no backend server and no user account.
- **Direct-only network access:** The app connects exclusively to registered receipt provider hosts via HTTPS (system CAs only).
- **Encrypted storage:** All data is stored locally in an encrypted Room database (SQLCipher) secured by an Android Keystore wrapping key.
- **Zero telemetry:** No Firebase, no analytics, no crash reporting SDKs, no tracking.
- **Passphrase-protected backups:** Local backup files are encrypted using AES-256-GCM with PBKDF2-HMAC-SHA256 key derivation.

---

## 📱 Features

- **QR Code Scanning:** Instant receipt import by scanning the receipt's QR code using high-performance zxing-cpp decoding.
- **Smart Parsing & Classification:** Automatic line classification, normalization, package size detection, and category matching.
- **Spending Insights:** Period-based analytics (weekly, monthly, custom ranges) with interactive charts.
- **Product & Price History:** Track price evolution and find the cheapest merchants for any product.
- **Household Management:** Organize expenses by household members.

---

## 🛠️ Technology Stack

- **Language:** Kotlin 2.4.21
- **UI:** Jetpack Compose & Material 3
- **Architecture:** Clean Architecture (`core:engine`, `core:database`, `core:data`, `core:model`, `core:ui`, feature modules)
- **Database:** Room with SQLCipher encryption
- **Dependency Injection:** Hilt
- **Background Processing:** WorkManager (for robust import scheduling & retries)
- **Networking:** OkHttp (`SafeFetcher` with host allowlist and public DNS)
- **Scanning:** zxing-cpp

---

## 📱 Compatible Android Versions

- **Minimum SDK:** API 26 (Android 8.0 Oreo)
- **Target SDK:** API 36 / Compile SDK 37

---

## 📸 Screenshots

```
+-------------------+  +-------------------+  +-------------------+  +-------------------+
|    Home Screen    |  |  Receipt Scanner  |  | Insights & Charts |  |  Product Details  |
|                   |  |                   |  |                   |  |                   |
|  [Summary Card]   |  |  [Camera View]    |  |  [Spending Bar]   |  |  [Price History]  |
|  [Recent Receipts]|  |  [QR Target Box]  |  |  [Category Pie]   |  |  [Cheapest Store] |
+-------------------+  +-------------------+  +-------------------+  +-------------------+
```

*(Screenshots can be placed in `docs/screenshots/` and linked here when publishing binary releases or store listings.)*

---

## 🏗️ Building & Testing

The project uses Gradle with convention plugins and a Makefile for quality checks:

```bash
make check           # Runs lint, ktlint, typed detekt, tests, Kover coverage, and no-telemetry checks
make test            # Runs unit tests
make build           # Builds debug APK
make codemap         # Regenerates documentation symbols map
```

## Documentation

| Document | Description |
|---|---|
| [`docs/codemap/OVERVIEW.md`](docs/codemap/OVERVIEW.md) | Architecture, modules, import pipeline, data layer, backup, and rules |
| [`docs/design/ui-redesign.md`](docs/design/ui-redesign.md) | UI design tokens, colors, typography, and components |

## License

MIT, see [LICENSE](LICENSE).
