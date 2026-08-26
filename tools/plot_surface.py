#!/usr/bin/env python3
"""Renders the volatility surface exported by `qrp options --export`.

Reads a CSV of (expiry, years, strike, implied_vol) rows -- the format
`SurfaceGridExporter` writes -- and draws three views: the smile at each
expiry, the at-the-money term structure, and the full surface in 3D.

Usage:
    mvn -q -pl qrp-app exec:java -Dexec.mainClass=io.github.williamhuang1261.qrp.app.QrpCli \\
        -Dexec.args="options --export /tmp/surface_grid.csv"
    python3 tools/plot_surface.py /tmp/surface_grid.csv docs/vol-surface.png

Not run by CI or by any Java code: this is an offline reporting tool over a
CSV the Java side already produced, the same relationship
`fetch_ust_curve.py` has to `data/rates/`.
"""
import csv
import sys
from collections import defaultdict

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
from mpl_toolkits.mplot3d import Axes3D  # noqa: F401  (registers the 3D projection)


def read_grid(csv_path):
    by_expiry = defaultdict(list)
    with open(csv_path, newline="", encoding="utf-8") as handle:
        for row in csv.DictReader(handle):
            by_expiry[row["expiry"]].append(
                (float(row["years"]), float(row["strike"]), float(row["implied_vol"]))
            )
    for rows in by_expiry.values():
        rows.sort(key=lambda r: r[1])  # by strike
    return by_expiry


def plot(csv_path, output_path):
    by_expiry = read_grid(csv_path)
    if not by_expiry:
        raise SystemExit(f"no rows in {csv_path}")

    expiries = sorted(by_expiry, key=lambda e: by_expiry[e][0][0])
    fig = plt.figure(figsize=(13, 4.2))

    # --- smile per expiry ---
    ax_smile = fig.add_subplot(1, 3, 1)
    for expiry in expiries:
        rows = by_expiry[expiry]
        strikes = [r[1] for r in rows]
        vols = [r[2] * 100.0 for r in rows]
        ax_smile.plot(strikes, vols, label=expiry)
    ax_smile.set_xlabel("strike")
    ax_smile.set_ylabel("implied vol (%)")
    ax_smile.set_title("smile per expiry")
    ax_smile.legend(fontsize=7)

    # --- ATM term structure: the vol nearest the median strike per expiry ---
    ax_term = fig.add_subplot(1, 3, 2)
    term_years, term_vols = [], []
    for expiry in expiries:
        rows = by_expiry[expiry]
        median_strike = rows[len(rows) // 2][1]
        closest = min(rows, key=lambda r: abs(r[1] - median_strike))
        term_years.append(closest[0])
        term_vols.append(closest[2] * 100.0)
    ax_term.plot(term_years, term_vols, marker="o")
    ax_term.set_xlabel("years to expiry")
    ax_term.set_ylabel("~at-the-money implied vol (%)")
    ax_term.set_title("term structure")

    # --- full surface ---
    ax_surface = fig.add_subplot(1, 3, 3, projection="3d")
    all_years = np.array([r[0] for rows in by_expiry.values() for r in rows])
    all_strikes = np.array([r[1] for rows in by_expiry.values() for r in rows])
    all_vols = np.array([r[2] * 100.0 for rows in by_expiry.values() for r in rows])
    ax_surface.plot_trisurf(all_strikes, all_years, all_vols, cmap="viridis", linewidth=0.1)
    ax_surface.set_xlabel("strike")
    ax_surface.set_ylabel("years")
    ax_surface.set_zlabel("implied vol (%)")
    ax_surface.set_title("surface")

    fig.suptitle("SYNOPT volatility surface (synthetic, generated for testing -- see docs/spec-options.md)")
    fig.tight_layout(rect=(0, 0, 1, 0.95))
    fig.savefig(output_path, dpi=140)
    print(f"wrote {output_path}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit(f"usage: {sys.argv[0]} <grid.csv> <output.png>")
    plot(sys.argv[1], sys.argv[2])
