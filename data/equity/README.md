# Equity fundamentals snapshots

## Source

Yahoo Finance, via the `yfinance` package. No API key, no account. Figures
are trailing-twelve-month as reported by the data provider at fetch time,
not a specific fiscal-year filing.

`fundamentals_2026-09-06.csv` is one dated snapshot for the two companies
this platform's sample equity research notes cover, columns:

- `ticker` -- the exchange ticker
- `company_name` -- the provider's short name for the company
- `sector` -- the provider's sector classification
- `fetch_date` -- the date this snapshot was taken (ISO 8601)
- `total_revenue` -- trailing-twelve-month total revenue, USD
- `trailing_eps` -- trailing-twelve-month diluted earnings per share, USD
- `net_margin` -- trailing-twelve-month net profit margin, as a decimal
- `diluted_shares_outstanding` -- diluted shares outstanding
- `trailing_pe` -- trailing price-to-earnings ratio at fetch time
- `current_price` -- share price at fetch time, USD
- `free_cash_flow` -- trailing-twelve-month free cash flow, USD

`comps_2026-09-06.csv` is a small peer set for the comparable-multiples
valuation: for each covered ticker, the trailing P/E of 3 real peer
companies at the same fetch date. Columns: `ticker`, `peer_ticker`,
`peer_trailing_pe`.

## What this is not

This is a point-in-time snapshot of a handful of headline numbers, not a
financial-statement database. There is no historical time series, no segment
breakdown, and no adjustment for one-time items -- `EquityDcfValuation`'s
free-cash-flow growth assumption is the candidate's own stated estimate, not
a figure derived from this data. `docs/spec-equity-research.md` states this
explicitly.

## Regenerating

```
python3 tools/fetch_equity_fundamentals.py
```

The script is documented in `tools/README.md` and is **not** run by CI or by
any Java code: `EquityFundamentals` reads only the committed CSVs, so a clean
clone works fully offline, matching every other data file in this
repository.
