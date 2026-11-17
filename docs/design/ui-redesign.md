# UI rules

The UI is built; the code is the spec for values and layouts. This file keeps only the rules a
new screen or change must follow. Section numbers are cited from code comments: keep them.

## 1. Colors

Tokens: `core:designsystem` `theme/Color.kt` (`EksodaColors`, also mapped to
`MaterialTheme.colorScheme`). Never a hex in a screen.

- One dark neutral base and two accents, each with one meaning. No other hue (tints and shades
  of the three are fine). **No green anywhere.**
- **Terracotta** (`spend*`): this period, spending, "more", "pricier", attention items, Scan.
- **Periwinkle** (`compare*`): previous period, selection, "less", "cheaper", unit prices,
  links, active tab.
- Red (`error`) only for errors and destructive actions, never for spending.
- Color is never the only signal: badges carry ▲/▼ and a word; a null change shows "—".
- `outline`/`guide` is for lines only, never text (fails contrast).
- Dynamic color off, dark scheme only (in both system modes).

## 2. Type

- Inter for words; IBM Plex Mono for every number (`MoneyText`, `HeroMoney`, `tabular()`,
  `monoNumbers()`). Plex Mono has no Greek, ▲ or ▼: never set it on words.
- The unit follows the number, smaller, in ink-2 ("342,60 €", "1,29 € / l").
- Labels in sentence case, never capitals. When a label does not fit, wrap or stack; never cut.

## 3. Components

Use what `core:designsystem` has (`SectionCard`, `ChangeBadge`, `HeroAmount`, `StatRow`,
`OverlineText`, `PeriodSelector`, `SegmentedToggle`, navigation, `CategoryTile`); shapes are in
`theme/Shape.kt`. Cards have a line border and no elevation. Categories have no color: their
identity is the name and icon on a neutral tile.

## 4. Charts

From `core:designsystem/chart`, drawn with `Canvas`, `BigDecimal` in, content description
required.

- Colors by rank, not by category (`EksodaColors.rank`); the rest grouped in one neutral slice.
- Current partial period: striped terracotta. Comparison series: dashed periwinkle. Averages:
  dashed `guide` line.
- Month labels use the short name; skip every other one when they don't fit (never one
  letter).
- Animate in once per load (`ChartReveal`) and respect "Remove animations".

## 5. Screens

Bottom bar: Home · Receipts · (Scan) · Insights · Products. Home stays short (one figure and
what needs doing); details belong in Insights, which Home's cards open. Insights cards load and
fail on their own.

## 6. Where the numbers come from

Pace and "vs the same day last month": `AnalyticsDao.dayTotals` → `core:engine`
`analytics/Combine.kt`. Product detail: `feature:products` `detail/PriceInsights.kt` and
`core:engine` `products/History.kt`. Averages cover full months only (the current partial month
is excluded).
