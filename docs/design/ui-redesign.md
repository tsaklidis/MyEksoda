# UI redesign: stats-first, three colors (accepted 2026-11-16)

The owner accepted this design on 2026-11-16. It replaces the visual design of
`android-app.md` §7.1–7.4 (teal theme; green = less, amber = more) for the
Home (L6), Insights (L6) and Product detail (L5) screens, and for the shared
theme. Screens already built in L4 move to the new tokens; their layout stays.

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
  break the meanings. (Today `EksodaTheme(dynamicColor = true)`.)
- **Dark first.** Only the dark theme is designed. Until a light theme is
  designed, the app uses the dark scheme in both system modes (open question 1).

## 2. Type

- **Inter** (already bundled, `core/designsystem/res/font/inter_variable.ttf`)
  for all words.
- **IBM Plex Mono** for every number (amounts, counts, percents, dates in
  charts, axis labels). Bundle it like Inter (OFL, `assets/licenses/`, no
  downloadable fonts, §8.1). Weights 400, 500. Plex Mono has no Greek glyphs:
  use it for digits, separators, €, %, ▲▼ and short Latin units only; Greek text
  stays in Inter.
- Scale: hero amount 42–46 sp / 500, letter-spacing −1.5; screen title 26 sp /
  600; section title 15 sp / 600; body 14–15 sp; labels 10–11 sp uppercase with
  +1.2 tracking in ink-2. The currency or unit follows the number, smaller, in
  ink-2 ("342,60 €", "1,29 € / l").

## 3. Components (`core:designsystem`)

Shapes: cards 16 dp radius with a 1 dp `#1F242D` border and no elevation; chips
and buttons 10 dp; badges 6 dp; bars 3 dp top corners.

- `ChangeBadge(percent, direction)`: less → periwinkle text on the periwinkle
  tint, ▼; more → terracotta text on the terracotta tint, ▲; null → neutral "—".
- `HeroAmount`: mono number + smaller unit; terracotta for spending, periwinkle
  for a unit price.
- `StatRow`: 2–3 stats split by 1 dp vertical dividers, uppercase label above a
  mono value. Replaces filled `StatTile` boxes.
- `MinMaxBar`: a 2 dp track with a periwinkle → terracotta gradient, an ink dot
  at the average, "MIN 1,15 · avg 1,27 · MAX 1,45" below (after the station
  page).
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

Vico (`android-app.md` §3) or Compose `Canvas`, whichever reproduces these
rules; the mockup shapes are the target.

- Gridlines `#1F242D`; axis labels mono 10–11 sp ink-2.
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
  (uncategorized and the others in §6.5) `#363C46`. The ranked list under the
  chart names every row with its swatch. This replaces the per-slug colors of
  §7.3 for charts; `CategoryStyle` keeps only the icon.
- **Horizontal bars** (by shop, top categories, top products): 4–6 dp, track
  `#1F242D`; spend amounts terracotta, counts periwinkle.
- Charts animate in once per load and respect "Remove animations".

## 5. Screens

Bottom bar order: Home · Receipts · (Scan) · Insights · Products.

**Home** (L6), top to bottom:

1. Greeting label + month title; household menu button.
2. Hero card: "Spent in November so far", hero amount (terracotta), change
   badge **against the same day of last month** ("8% less than by 15 Oct
   (371,20 €)"), then a **pace chart**: running total this month (terracotta,
   up to today) over last month's running total (dashed periwinkle, whole
   month, its total in the legend). Below: Receipts · Avg receipt · Per day.
3. Attention list: "n items to match" (terracotta), "n receipts importing…"
   (periwinkle); each hidden when 0.
4. "Last 6 months" bars (avg per month), last month periwinkle, this month
   striped; tap → Insights.
5. "Where it went": top 3 categories with share bars → Receipts filtered.
6. Recent receipts (3) → detail; "See all".

**Insights** (L6): period chips; summary (hero, change vs the previous equal
period, Receipts · Average · Refunds, the plain-words note); By category
(donut + full ranked list, "Assign categories" on Uncategorized); By shop (top
5 bars with share %, "Show all n"); Monthly trend (12 tappable bars + callout);
Top products (Most spent / Most bought toggle, top 5 with bars). Cards load
and fail on their own (§6.5).

**Product detail** (L5): category · brand label, name, package chip; price
card (last paid in periwinkle with where and when, change vs the previous
purchase as a badge, `MinMaxBar`, Lowest · Average · Highest with month and
merchant); price-per-unit line (12 months, average dashed, lowest and highest
marked); "Where it's cheapest" (a sentence: "Lidl is 0,13 €/l cheaper than AB
Vasilopoulos on average", then `RangeDotPlot`); purchases list.

## 6. Data the design needs that the plan doesn't have yet

- **Home pace chart and "vs the same day last month"** need running daily
  totals for this month and last month: `SUM(signed total) GROUP BY
  issued_local_date` for two months with the §6.6 inclusion rules, cumulated in
  `core:engine`. Not in the Python analytics, so it is an Android-only
  addition: keep it a pure function with unit tests. `analytics/summary`'s
  full-month comparison stays for Insights.
- **Product detail:** min and max with their month and merchant (from the
  PRICE_HISTORY rows), and each merchant's min–max range for `RangeDotPlot`
  (the Python `compare` gives the average and rank only).
- **Insights trend average:** the average of full months only (the current
  partial month is excluded).

## 7. Open questions for the owner

1. Light theme: design one later, or ship dark only for v1?
2. Category colors elsewhere (receipt lines, filter chips) are neutral now.
   Is that fine for the receipts screens built in L4?
