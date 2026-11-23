# UI rules

The UI is built; the code is the spec for values and layouts. This file keeps only the rules a
new screen or change must follow. Section numbers are cited from code comments: keep them.

## 1. Colors

Tokens: `core:designsystem` `theme/Color.kt` (`EksodaColors`, also mapped to
`MaterialTheme.colorScheme`). Never a hex in a screen.

- Monochrome, in the spirit of Revolut: a light gray page with white cards, or a charcoal one
  (not black) with lighter cards. Ink for text, figures, buttons and the Add button. One accent,
  blue, and red for one meaning. No other hue (tints and shades are fine). **No green
  anywhere.**
- **Ink** (`spend*`): this period, spending, "more", "pricier", attention items.
- **Blue** (`compare*`, `primary`): previous period, selection, "less", "cheaper", unit prices,
  links, progress toward a budget or a limit, switches.
- **Red** (`over*`, `error`): over a budget or a limit (the bar, the figure, the month's
  underline), and errors or destructive actions. Never for spending as such.
- No colored boxes around controls: month tabs, chips and segmented choices get their contrast
  from text weight, an underline or an outline, not from white or tinted fills.
- Shop logos are the one exception to the palette: they keep their own colors, in a circle.
- Color is never the only signal: badges carry ▲/▼ and a word; a null change shows "—"; over
  says "over" in words.
- `outline`/`guide` is for lines only, never text (fails contrast).
- Dynamic color off. Light and dark, the phone's choice by default (Settings → Theme).

## 2. Type

- Inter for words and numbers. Numbers use tabular digits (`MoneyText`, `HeroMoney`,
  `tabular()`), so amounts line up and don't shift. Chart axis labels stay in IBM Plex Mono
  (`ChartLabelStyle`, `monoNumbers()`), which has no Greek, ▲ or ▼: never set it on words.
- The unit follows the number, smaller, in ink-2 ("342,60 €", "1,29 € / l").
- Labels in sentence case, never capitals. When a label does not fit, wrap or stack; never cut.

## 3. Components

Use what `core:designsystem` has (`SectionCard`, `ChangeBadge`, `HeroAmount`, `StatRow`,
`OverlineText`, `PeriodSelector`, `SegmentedToggle`, `SwitchRow`, navigation, `CategoryTile`);
shapes are in
`theme/Shape.kt`. Cards have a line border and no elevation. Categories have no color: their
identity is the name and icon on a neutral tile.

## 4. Charts

From `core:designsystem/chart`, drawn with `Canvas`, `BigDecimal` in, content description
required.

- Colors by rank, not by category (`EksodaColors.rank`); the rest grouped in one neutral slice.
- Current partial period: striped ink. Comparison series: dashed blue. Averages:
  dashed `guide` line.
- Month labels use the short name; skip every other one when they don't fit (never one
  letter).
- Animate in once per load (`ChartReveal`) and respect "Remove animations".

## 5. Screens

Bottom bar, docked, with nothing drawn under it: Overview · Spending · (+) · Prices · More. The
round ink "+" opens a sheet with the three ways to add (scan the QR code, add by hand, paste a
link). Overview stays short: the month's everyday spending against its budget, the bills, and the
categories, each of which opens its own screen (rename, monthly limit). Details belong in
Spending, whose cards load and fail on their own. Receipts, products, budget, bills, categories,
shops and settings are in More.

Bills (rent, power, water…) are never mixed with receipts: they have their own screen and line.
Past months are read-only everywhere (budget, bills); only the current month can be changed,
except the previous month's variable bills until the 10th.

## 6. Where the numbers come from

Pace and "vs the same day last month": `AnalyticsDao.dayTotals` → `core:engine`
`analytics/Combine.kt`. Product detail: `feature:products` `detail/PriceInsights.kt` and
`core:engine` `products/History.kt`. Averages cover full months only (the current partial month
is excluded).
