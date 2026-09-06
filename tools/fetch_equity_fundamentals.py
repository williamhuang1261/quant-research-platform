#!/usr/bin/env python3
"""Downloads basic real fundamentals for a small, fixed set of tickers and
writes them in the format EquityFundamentals (qrp-equity) expects.

Not run by CI or by any Java code in this repository -- EquityFundamentals
reads only the committed CSVs under data/equity/, so a clean clone stays
fully offline. Run this by hand when the committed snapshot should be
refreshed:

    python3 tools/fetch_equity_fundamentals.py

writes data/equity/fundamentals_<date>.csv and data/equity/comps_<date>.csv.

Source: Yahoo Finance, via the `yfinance` package (`pip install yfinance`).
Trailing-twelve-month figures as reported at fetch time; see
data/equity/README.md for exactly what each column means and does not mean.

The ticker list is intentionally small and fixed (see TICKERS below): this
script exists to produce a hand-checkable snapshot for 2-3 real companies,
not a general market-data pipeline.
"""
import csv
import datetime
import sys

TICKERS = ["AAPL", "MSFT"]
PEER_TICKERS = ["AAPL", "MSFT", "GOOGL", "AMZN"]

FUNDAMENTALS_FIELDS = [
    "ticker",
    "company_name",
    "sector",
    "fetch_date",
    "total_revenue",
    "trailing_eps",
    "net_margin",
    "diluted_shares_outstanding",
    "trailing_pe",
    "current_price",
    "free_cash_flow",
]


def fetch_one(yf, ticker: str, fetch_date: str) -> dict:
    info = yf.Ticker(ticker).info
    required = {
        "shortName": info.get("shortName"),
        "sector": info.get("sector"),
        "totalRevenue": info.get("totalRevenue"),
        "trailingEps": info.get("trailingEps"),
        "profitMargins": info.get("profitMargins"),
        "sharesOutstanding": info.get("sharesOutstanding"),
        "trailingPE": info.get("trailingPE"),
        "currentPrice": info.get("currentPrice"),
        "freeCashflow": info.get("freeCashflow"),
    }
    missing = [k for k, v in required.items() if v is None]
    if missing:
        raise SystemExit(f"{ticker}: missing fields from yfinance: {missing}")
    return {
        "ticker": ticker,
        "company_name": required["shortName"],
        "sector": required["sector"],
        "fetch_date": fetch_date,
        "total_revenue": required["totalRevenue"],
        "trailing_eps": required["trailingEps"],
        "net_margin": required["profitMargins"],
        "diluted_shares_outstanding": required["sharesOutstanding"],
        "trailing_pe": required["trailingPE"],
        "current_price": required["currentPrice"],
        "free_cash_flow": required["freeCashflow"],
    }


def main() -> None:
    try:
        import yfinance as yf
    except ImportError:
        raise SystemExit("needs yfinance: pip install yfinance")

    fetch_date = datetime.date.today().isoformat()

    peer_rows = {t: fetch_one(yf, t, fetch_date) for t in PEER_TICKERS}

    fundamentals_path = f"data/equity/fundamentals_{fetch_date}.csv"
    with open(fundamentals_path, "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=FUNDAMENTALS_FIELDS)
        writer.writeheader()
        for ticker in TICKERS:
            writer.writerow(peer_rows[ticker])
    print(f"wrote {fundamentals_path}", file=sys.stderr)

    comps_path = f"data/equity/comps_{fetch_date}.csv"
    with open(comps_path, "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["ticker", "peer_ticker", "peer_trailing_pe"])
        for ticker in TICKERS:
            for peer in PEER_TICKERS:
                if peer == ticker:
                    continue
                writer.writerow([ticker, peer, peer_rows[peer]["trailing_pe"]])
    print(f"wrote {comps_path}", file=sys.stderr)


if __name__ == "__main__":
    main()
