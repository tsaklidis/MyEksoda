# Changelog

What changed in each release of Eksoda. Earlier releases are described on the
[Releases](https://github.com/tsaklidis/MyEksoda/releases) page.

## 0.3.10 - Older LIDL receipts and Sklavenitis from e-invoicing.gr

A small update for receipts from Entersoft (`e-invoicing.gr`). Install it over 0.3.9 as usual:
your data stays, and household sync keeps working between 0.3.9 and 0.3.10.

### Fixed

- **Older e-invoicing.gr receipts.** Links printed until 2025
  (`e-invoicing.gr/edocuments/ViewInvoice?ct=…&id=…`), such as LIDL's, were refused as an
  unsupported link. They are now read. Scan receipts that were refused before again: nothing
  was saved for them.
- **Sklavenitis receipts from e-invoicing.gr under their own shop.** The VAT number was read
  together with the phone and ΓΕΜΗ numbers printed next to it, so these receipts went to a
  new shop named by a long number instead of Sklavenitis. New receipts get the right VAT
  number, and the ones already saved move to the right shop by themselves when the app
  starts, keeping your names and categories. On household phones that have not updated yet,
  the empty extra shop goes once they update.

## 0.3.9 - AB Vasilopoulos and Market In receipts

A small update that reads receipts from more supermarkets. Install it over 0.3.8 as usual: your
data stays, and household sync keeps working between 0.3.8 and 0.3.9.

### New

- **AB Vasilopoulos and Market In receipts.** Their e-receipts come from Epsilon Digital, on
  hosts of their own (`epsilondigital-abmarket.epsilonnet.gr`,
  `epsilondigital-marketin.epsilonnet.gr`), and were refused as an unsupported provider. They
  are now read like Sklavenitis' and Masoutis' receipts. AB Vasilopoulos is named from its
  first receipt; a Market In shop shows its VAT number until you rename it. Scan receipts that
  were refused before again: nothing was saved for them.
- **New Epsilon Digital shops work without an update.** Any
  `epsilondigital-<name>.epsilonnet.gr` link is now read, and the receipt is fetched only from
  that link's own site. If the site can't be reached, the import is tried again later like
  any other receipt, and the imports list shows what went wrong.

## 0.3.8 - Clearer product prices

A small update to a product's price page. Install it over 0.3.7 as usual: your data stays, and
household sync keeps working between 0.3.7 and 0.3.8.

### Fixed

- **The same price is the same price.** A product bought twice at 9,95 €/kg could show a
  rising chart, "2 διαφορετικές τιμές" and "▲ 0,00 € ακριβότερο από την προηγούμενη αγορά",
  because prices per kilo differed after the second decimal. Prices are now compared in
  cents, so the chart is flat and the page counts one price.
- **No more "0,00 €" changes.** The badge under the price appears only when the price went
  up or down since the last purchase, and "Πού είναι φθηνότερο" no longer says a shop is
  0,00 € cheaper.
- **The cheapest shop is the cheapest.** Two shops that both show the same average are now
  ranked by which is really cheaper, not by their names.
- **The same cents everywhere.** A price is rounded the same way on the receipt and on the
  product page.

### Improved

- **A tidier price chart.** A change of a cent is a gentle slope instead of a line from the
  bottom to the top, the lowest price's label no longer covers the values on the left, and
  the legend moves to a new line instead of breaking "Υψηλότερη" in two. When every purchase
  had the same price, the chart's legend no longer lists the lowest and highest.

## 0.3.7 - Warnings where the items are

A small update: a receipt's warnings now point at the items they are about. Install it over
0.3.6 as usual: your data stays, and household sync keeps working between 0.3.6 and 0.3.7.

### Improved

- **Items with a warning are marked in the list.** An item whose amounts don't agree shows a
  small "Διαφορά ποσού" tag in its row; tap it to read what doesn't add up. The card at the
  top of the receipt now says how many items are marked, and "Εμφάνιση" takes you to the first
  one, instead of listing the items' names there.
- **A calmer sign in the receipt list.** A receipt with warnings shows a small ⓘ next to the
  shop's name instead of the "⚠ Προσοχή" tag.

## 0.3.6 - Clearer receipt warnings

A small update: fewer false warnings on receipts, and the ones that remain say what they mean.
Install it over 0.3.5 as usual: your data stays, and household sync keeps working between
0.3.5 and 0.3.6.

### Fixed

- **Masoutis receipts from `e-invoicing.gr` no longer warn on every item.** These receipts
  print the unit price before VAT (3,90 for a yogurt that costs 4,40 with 13% VAT), and
  Eksoda took it for the price with VAT, so every item showed "Η τιμή μονάδας μιας γραμμής
  δεν ταιριάζει με το ποσό της". Receipts imported before this version keep the warning:
  delete such a receipt and scan it again.

### Improved

- **Receipt warnings are easier to read.** When a receipt's amounts don't agree with each
  other, a calm card explains it and says the amounts are shown exactly as the shop printed
  them. Warnings of the same kind are shown once, naming the items they concern, instead of
  one "Γραμμή 1, Γραμμή 2…" line each, and every message says what doesn't add up.
- **Warnings that deserve a closer look stand out.** A QR code whose data or amount differs
  from what AADE holds, a receipt without a MARK, a receipt not in euros or of an unknown
  type each get their own message in a highlighted card.

## 0.3.5 - Masoutis receipts and supermarket logos

A small update: Masoutis receipts now import, and the big supermarket chains show their logo.
Install it over 0.3.4 as usual: your data stays, and household sync keeps working between
0.3.4 and 0.3.5.

### New

- **Receipts on `epsilondigital-3rdpartb.epsilonnet.gr`** now import, for example Masoutis'.
  Scan the receipt's QR code as usual. Scan these receipts again: the earlier attempt was
  refused before anything was saved.
- **Supermarket logos.** AB Vasilopoulos, Sklavenitis, Masoutis, Lidl and My Market show
  their logo instead of their initials in Home, Receipts and the shop list, drawn in the app's
  own colors. Other shops keep their initials. My Market's receipts are issued by METRO ΑΕΒΕ,
  which also runs Metro Cash & Carry under the same tax number: if that shop is Metro Cash &
  Carry for you, rename it (for example "Metro Cash & Carry") and it shows its initials again.

### Fixed

- **Epsilon Digital receipts without the shop's name** now import instead of being refused.
  Until a receipt names the shop, it is shown by its tax number (ΑΦΜ); you can rename it.
- **Shops named Ταξιάρχης** (or ταξίδια, travel agencies) are no longer taken for taxis: a
  new shop such as "Φούρνος Ταξιάρχης" gets the bakery category. Shops already in the app keep
  the category they have; change it on the shop's page if needed.
- **Initials are shown as the shop's name is written**, never turned into capitals.

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
