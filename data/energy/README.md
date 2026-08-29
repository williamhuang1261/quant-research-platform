# data/energy/

`henry_hub_<date>.csv` is a committed snapshot of the U.S. EIA's daily
"Henry Hub Natural Gas Spot Price" series (source key `RNGWHHD`,
dollars per million Btu), fetched by `tools/fetch_energy_prices.py`.

- **Source:** https://www.eia.gov/dnav/ng/hist_xls/RNGWHHDd.xls -- a U.S.
  government work, public domain, no API key and no account required.
- **Real data, not synthetic.** Unlike the rest of this repository's sample
  data (`SYNA`, `SYNB`, `SYNETF`, `SYNOPT`), this is the actual reported
  Henry Hub spot price. Do not replace it with synthetic data without
  updating the dated filename and this README, the same convention
  `data/rates/README.md` follows for the Treasury curve snapshot.
- **What this is not:** a futures or forward curve, a bid/ask quote, or a
  real-time feed. It is one reported daily spot print per trading day, with
  no volume, no counterparty, and no intraday granularity.
- **Refreshing it:**
  ```bash
  python3 tools/fetch_energy_prices.py > data/energy/henry_hub_$(date +%Y-%m-%d).csv
  ```
  Not run by CI or by any Java code -- a clean clone reads only the
  committed CSV, so the build stays fully offline and reproducible.
