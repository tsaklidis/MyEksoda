# Changelog

What changed in each release of Eksoda. Earlier releases are described on the
[Releases](https://github.com/tsaklidis/MyEksoda/releases) page.

## 0.4.0 - A new look, budgets month by month, bills and prices

A bigger update. Install it over 0.3.5 as usual: your data stays. **Household sync needs every
phone of the household on 0.4.0**: the database gains new tables and columns, and a phone on
0.3.5 cannot sync with one on 0.4.0 until both are updated.

### New

- **A new look.** Monochrome, light or charcoal dark (the phone's choice, or fixed in Settings →
  Theme), with one blue for comparisons and links and red only for "over budget". The bottom bar
  is Overview · Spending · + · Prices · More.
- **Overview.** Pick any of the last six months: everyday spending against your budget, with
  what is left per day this month, your bills on their own line, and your categories. A month
  that went over its budget is marked in red.
- **One + for everything.** Scan the QR code, add an expense by hand, or paste a receipt link.
- **Add by hand.** The amount, whether it was a product, a service or something else, a few
  words ("Dentist"), an optional category and the day. It counts like any receipt and can be
  edited from its detail.
- **Budget month by month.** One everyday budget for every month, and the current month can be
  different (a trip, a big month). Past months keep the budget they had.
- **Category limits.** Open a category from the Overview to rename it or set its monthly limit:
  three amounts around what it usually costs, or your own.
- **Bills.** Rent, electricity, water, internet, phones: each fixed (the same every month,
  changeable for the current month) or variable (you enter it each month). They are kept apart
  from receipts. Past months are read-only; last month's variable bills stay open until the
  10th, since they often arrive late.
- **Prices.** From your own receipts: where each product you buy costs least and what that would
  save, what each shop is cheapest for, and how prices moved over the last month.
- **Your month in onboarding.** A new page asks what your bills and everyday spending come to,
  all optional.
- **Shop logos** for AB, Lidl, Sklavenitis and My Market.

### Improved

- Receipts and products moved to More, with budget, bills, categories, shops, imports, household
  sync and settings.
- Insights is now called Spending.

## 0.3.5 - Hide the app's content

A small update. Install it over 0.3.4 as usual: your data stays, and household sync keeps
working between 0.3.4 and 0.3.5.

### New

- **Hide app content.** Settings → Preferences has a new "Hide app content" switch. When it is
  on, screenshots and screen recordings of Eksoda come out blank, and its card in the recent
  apps screen is empty, so your receipts and spending do not show when you switch apps or share
  your screen. It is off by default and applies to this phone only: it is not synced to the
  household's other phones and is not part of a backup.

### Improved

- **Labels in two-option toggles are no longer cut.** In Insights → Top products, the Greek
  "Περισσότερα χρήματα" showed as "Περισσότερα χρήμ…" on most phones. A label that does not fit
  now wraps to a second line, with both options keeping the same height.

## 0.3.4 - A way to support Eksoda

A small update. Install it over 0.3.3 as usual: your data stays, and household sync keeps
working between 0.3.3 and 0.3.4.

### New

- **Support Eksoda.** Eksoda is free. If you find it useful, Settings → About now has a
  "Support Eksoda" link that opens `paypal.me/stsaklidis` in your browser, where you can make
  a donation. It is optional, unlocks nothing, and the app sends nothing to PayPal: it only opens
  the link when you tap it.

### Under the hood

- More tests for how receipts are stored and how household sync finds the other phones. Nothing
  changes in how the app works.

## 0.3.3 - Discounts on receipts

A small update: discounted items on receipts from `einvoice.impact.gr` (My Market, for example)
now import with their discount. Install it over 0.3.2 as usual: your data stays, and household
sync keeps working between 0.3.2 and 0.3.3.

### Fixed

- **Discounted items no longer show a unit price warning.** The discount column of these
  receipts ("Έκπτ. 1") is now read, so an item at 30% or 50% off no longer shows "Η τιμή
  μονάδας μιας γραμμής δεν ταιριάζει με το ποσό της". The receipt's total discount is read too. Receipts
  imported before this version keep the warning: delete such a receipt and scan it again.

### Improved

- **A discount that cannot be read is shown as such.** If a receipt prints a discount in a way
  Eksoda does not recognize yet, the receipt still imports, leaves that discount out, and says
  so in its warnings.

## 0.3.2 - One more receipt site, and synced receipts at a glance

A small update: receipts from one more e-receipt site now import, and the lists show which
receipts came from another phone of the household. Install it over 0.3.1 as usual: your data
stays, and household sync keeps working between 0.3.1 and 0.3.2.

### New

- **Receipts on `einvoice.s1ecos.gr`** now import, for example Sephora's. Scan the receipt's
  QR code as usual; the link printed on the receipt's page works too. Scan these receipts
  again: the earlier attempt was refused before anything was saved.

### Improved

- **Receipts from another phone are marked in the lists.** With household sync on, a receipt
  that another phone of the household scanned shows a small ⇄ mark next to the shop's name,
  in Receipts and in Home's recent receipts, so you can tell before opening it. Inside the
  receipt, the note about where it came from stays as it was.

## 0.3.1 - More receipts import

A small update for receipts that some shops' e-receipt pages kept from importing. Install it
over 0.3.0 as usual: your data stays, and household sync keeps working between 0.3.0 and
0.3.1.

### Fixed

- **Impact receipts that said "The receipt page has conflicting information"** now import.
  Some shops put an id of their own in the receipt link instead of the receipt's official one.
  The app now accepts such a receipt when its page names the scanned link as its own
  ([#6](https://github.com/tsaklidis/MyEksoda/issues/6)). Receipts that failed this way are
  tried again automatically after the update, so there is no need to scan them again.
- **Epsilon Digital receipts from smaller shops**, such as fuel stations (links on
  `epsilondigital-3rd.epsilonnet.gr`), are now supported. The shop's name is shown as the
  receipt prints it, which may be the station's label (for example "R203-ΚΟΖΑΝΗ"); you can
  rename the shop in the app. Scan these receipts again: the earlier attempt was refused before
  anything was saved.

### Improved

- **Payments on Impact receipts are checked more carefully.** The amount and the tip must
  match in both parts of the receipt page; a page whose payment details disagree is refused
  instead of being saved with the wrong amount. The receipt shows the payment method as
  printed (for example "EPAY A80").

## 0.3.0

### New: household sync

The phones of a household can now share one set of receipts, directly with each other over the
same Wi-Fi. There is still no server and no account, and nothing goes over the internet.

- **Off by default.** Turn it on in **Settings → Household sync**. Everything about sync is on
  that screen.
- **Pairing with a QR code.** On one phone tap **Add a phone**; on the other tap **Join a
  household** and scan the code. Pair each phone of the household once. The code works like a
  password for the household's data, so the app does not allow screenshots of it.
- **What is shared:** receipts and their lines, products, categories, shops, budgets and the
  household's settings. A receipt imported on two phones appears once, and so does a product
  both phones created. Edits and deletions reach the other phones; when the same thing was
  changed on two phones, the later change wins.
- **When it syncs:** automatically when two phones of the household have Eksoda open on the
  same Wi-Fi, or at once with **Sync now**.
- **History:** every sync is listed with its time, the other phone, how many items were added,
  changed or deleted, and the reason when it failed.
- **Receipt links stay on the phone that scanned them.** A receipt that came from another phone
  says so, and has no "Open original".
- **Leave household sync** at any time: the phone stops syncing and forgets the paired phones.
  Your data stays on the phone.
- **Private by design:** each sync is encrypted with the household's key, only phones on the
  local network are contacted, and phones without the key are refused.

Good to know:

- Both phones need this version. With an older version on one of them, the sync stops and
  asks you to update the app on both.
- Sync needs Wi-Fi (or Ethernet), with Eksoda open on both phones. Guest networks and some
  routers keep devices from seeing each other; the Sync screen then says that no phone was
  found.

### Other changes

- **Settings → Data and privacy** now mentions household sync among the app's connections.
- The database is upgraded automatically on the first start (schema 7). Your data is kept. As
  always, exporting a backup before updating is a good idea.

No new permissions. Joining a household uses the camera, which the receipt scanner already
uses.
