# UI design: stats-first, three colors

The look of every screen. Home, Insights and Product detail follow the mockups; the other
screens use the same tokens.

- **Mockups (source of truth for look and layout):**
  (private design file)
  (artboards: Home, Insights, Product detail, Chart & stat kit). The figures in
  the mockups are sample data.
- **Palette source:** the owner's site https://logs.tsaklidis.gr/ (its CSS
  variables `--bg`, `--panel`, `--line`, `--ink*`, `--temp`, `--prs`).

## 1. Colors: three, each with one meaning

Owner rules: use colors similar to logs.tsaklidis.gr, avoid green, three colors
at most. The three are one neutral base and two accents. Tints and shades of the
same three are allowed; no other hue.

| Role | Token | Value | Meaning |
|---|---|---|---|
| Base | `background` | `#0B0D11` | App background |
| Base | `surfaceContainer` (panel) | `#101319` | Cards |
| Base | `surfaceContainerHigh` (inset) | `#14181F` | Callouts inside cards |
| Base | `outlineVariant` (line) | `#1F242D` | Card borders, dividers, gridlines, bar tracks |
| Base | chart neutral | `#2A303A` | Inactive bars |
| Base | `outline` | `#6B737D` | Average lines, grouped donut slices. **Lines only**: fails 4.5:1 as small text |
| Base | `onSurface` (ink) | `#E8EAED` | Primary text |
| Base | `onSurfaceVariant` (ink-2) | `#A2AAB4` | Secondary text, labels, units |
| Terracotta | `primary` / spend | `#E2795A` | **This period, spending, "spent more", "pricier", attention items, the Scan button** |
| Terracotta | spend text | `#EE9A7F` | Terracotta text on dark tints |
| Terracotta | light / deep | `#F2B8A3` / `#A24B30` | Extra chart slices only |
| Terracotta | tint + border | `#22150F` / `#4A2A20` | "More" badge background |
| Periwinkle | `secondary` / compare | `#8F8FD0` | **Previous period, selection, "spent less", "cheaper", unit prices, links, active tab** |
| Periwinkle | compare text | `#A9A9E0` | Periwinkle text on dark tints |
| Periwinkle | deep | `#5F5FA8` | Extra chart slices, range bars |
| Periwinkle | tint + border | `#15172A` / `#2E3256` | "Less" badge background |
| Periwinkle | nav indicator | `#1C1E33` | Selected tab pill |

Rules:

- Color is never the only signal: badges carry ▲/▼ and a word ("8% less",
  "4% more"); a null change shows "—" and words ("First month with receipts").
- Red is never used for spending. `error` stays Material's for errors and
  destructive actions only.
- **No green anywhere**, including category colors (see §4).
- **Dynamic color is off.** The palette is the brand; wallpaper colors would
  break the meanings.
- **Dark only.** No light theme is designed yet; the dark scheme is used in both
  system modes.

## 2. Type

- **Inter** (already bundled, `core/designsystem/res/font/inter_variable.ttf`)
  for all words.
- **IBM Plex Mono** for every number (amounts, counts, percents, dates in
  charts, axis labels), bundled like Inter (OFL in `assets/licenses/`; no
  downloadable fonts, they need Play services). Weights 400, 500. Plex Mono has no Greek glyphs:
  use it for digits, separators, €, %, ▲▼ and short Latin units only; Greek text
  stays in Inter.
- Scale: hero amount 42–46 sp / 500, letter-spacing −1.5; screen title 26 sp /
  600; section title 15 sp / 600; body 14–15 sp; labels 12 sp medium in
  ink-2, **in sentence case** (the mockups show labels in capitals; text is
  never put in capitals). When a label does
  not fit, the layout changes (it wraps or the figures stack); it is never cut
  or capitalized. The currency or unit follows the number, smaller, in ink-2
  ("342,60 €", "1,29 € / l").

## 3. Components (`core:designsystem`)

Shapes: cards 16 dp radius with a 1 dp `#1F242D` border and no elevation; chips
and buttons 10 dp; badges 6 dp; bars 3 dp top corners.

- `ChangeBadge(percent, direction)`: less → periwinkle text on the periwinkle
  tint, ▼; more → terracotta text on the terracotta tint, ▲; null → neutral "—".
- `HeroAmount`: mono number + smaller unit; terracotta for spending, periwinkle
  for a unit price.
- `StatRow`: 2–3 stats split by 1 dp vertical dividers, a label above a mono
  value, when every text fits its column on one line; otherwise one stat per
  line (label and supporting text on the left, value on the right).
- `MinMaxBar`: a 2 dp track with a periwinkle → terracotta gradient, an ink dot
  at the average, "Min 1,15" and "Max 1,45" at its ends and "avg 1,27 · 14
  buys" on its own line below (after the station page).
- `RangeDotPlot` (price comparison): one row per merchant on a shared axis,
  bar = min–max range, dot = average; the cheapest row in periwinkle, others
  neutral.
- `PeriodSelector`: chips; the selected one is ink background with dark text
  (neutral, not an accent).
- `SegmentedToggle`: dark well with a `#1F242D` selected segment.
- Bottom bar: `#101319` with a top line; the selected tab has a `#1C1E33` pill
  and a periwinkle icon; Scan is a raised terracotta 54 dp button with a dark
  icon.
- Category icons on lists: neutral tiles (`#171B22`, line border, ink-2 icon).
  Category identity is the name and icon, not a color.

## 4. Charts

Drawn with Compose `Canvas` in `core:designsystem/chart`; the mockup shapes are
the target.

- Gridlines `#1F242D`; axis labels mono 10–11 sp ink-2. Month labels use the
  short name ("Νοε", "Nov"); when 12 do not fit, every other month is labeled
  (never one-letter capitals).
- **Bars:** past periods `#2A303A`; the selected one periwinkle; the current
  partial period striped terracotta (`#E2795A`/`#9A4E36`); empty months keep a
  slot; average as a dashed `#6B737D` line. Tapping a bar selects it and shows a
  callout: month, amount, "▲ n% above / ▼ n% below average" (the current month:
  "so far · d of N days", no comparison).
- **Lines:** the current series solid (2–2.2 dp) with a vertical fill fading
  28% → 0; the comparison series dashed periwinkle. Lowest point: periwinkle
  ring; highest: terracotta ring, each with its value.
- **Donut** (by category): stroke 20 dp, small gaps. Colors go **by rank, not
  by category**: 1 `#E2795A`, 2 `#8F8FD0`, 3 `#F2B8A3`, 4 `#5F5FA8`,
  5 `#A24B30`; the remaining categories grouped in one `#6B737D` slice
  ("Smaller, grouped in the chart" in the list); non-category buckets
  (Uncategorized, Unallocated, discounts, fees, other) `#363C46`. The ranked
  list under the chart names every row with its swatch.
- **Horizontal bars** (by shop, top categories, top products): 4–6 dp, track
  `#1F242D`; spend amounts terracotta, counts periwinkle.
- Charts animate in once per load and respect "Remove animations".

## 5. Screens

Bottom bar order: Home · Receipts · (Scan) · Insights · Products.

**Home** is short on purpose (2026-11-17): one figure and what needs doing. The
details live in Insights, which the cards open. Top to bottom:

1. Greeting label + month title; household menu button.
2. Hero card: "Spent in November so far", hero amount (terracotta), change
   badge **against the same day of last month** ("8% less than by 15 Oct
   (371,20 €)"); tap → Insights.
3. Attention list: "n items to match" (terracotta), "n receipts importing…"
   (periwinkle); each hidden when 0.
4. "Where it went": top 3 categories, name and amount → Insights.
5. Recent receipts (3) → detail; "See all".

**Insights**: period chips (Today · This week · This month · Last month ·
3 months · Year · Custom range, a date range picker; the presets are
`core:model` `PeriodPreset`, shared with the receipt filter, weeks start on
Monday); summary (hero, change vs the previous equal period, Receipts · Per
day · Average · Refunds, the plain-words note); for "This month" a "Day by
day" **pace chart**: running total this month (terracotta, up to today) over
last month's running total (dashed periwinkle, whole month, its total in the
legend); By category
(donut + full ranked list, "Assign categories" on Uncategorized); By shop (top
5 bars with share %, "Show all n"); Monthly trend (12 tappable bars + callout);
Top products (Most spent / Most bought toggle, top 5 with bars). Cards load
and fail on their own.

**Product detail**: category · brand label, name, package chip; price
card (last paid in periwinkle with where and when, change vs the previous
purchase as a badge, `MinMaxBar`, Lowest · Average · Highest with month and
merchant); price-per-unit line (each change of price, oldest to newest, close
prices merged above 24 points, average dashed, lowest and highest marked, first,
middle and last dates below); "Where it's cheapest" (a sentence: "Lidl is 0,13 €/l cheaper than AB
Vasilopoulos on average", then `RangeDotPlot`); purchases list.

## 6. Where the numbers come from

- Insights' pace chart and Home's "vs the same day last month": `AnalyticsDao.dayTotals`,
  cumulated by `runningTotals` / `totalBySameDay` (`core:engine`
  `analytics/Combine.kt`). Insights compares with the period of equal length
  just before.
- Product detail: `feature:products` `detail/PriceInsights.kt` (min/max with
  month and merchant, `pricePoints`) and `core:engine` `products/History.kt`
  (per-merchant ranges).
- Averages (Insights' trend) cover full months with receipts;
  the current partial month is excluded.
