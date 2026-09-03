#!/usr/bin/env python3
"""Deploys the onchain/ constant-product AMM pool to a local Anvil fork,
drives a fixed, seeded sequence of simulated trades through web3.py, and
writes the resulting swap/reserve history as a plain CSV.

Not run by CI or by any Java code in this repository -- same "run once,
commit the frozen snapshot" convention fetch_ust_curve.py and
fetch_energy_prices.py already establish, so a clean clone of qrp-onchain's
tests stays fully offline. Run this by hand when the committed snapshot
should be refreshed:

    python3 tools/amm_sim.py > data/onchain/amm_swaps_$(date +%Y-%m-%d).csv

Needs `web3` (`pip install web3`) and the Foundry toolkit's `forge`/`anvil`
binaries on PATH (https://getfoundry.sh) -- unlike fetch_energy_prices.py's
xlrd dependency, there is no way around this one: an on-chain simulation
needs an actual EVM to execute against, not just a data parser.

This script starts and stops its own local Anvil node (chain id 31337); it
never touches a public testnet or mainnet, and no real capital is at risk
anywhere in this simulation.
"""
import csv
import json
import os
import random
import subprocess
import sys
import time
import warnings

from web3 import Web3

# process_receipt() below decodes every log in a swap's receipt, including
# the two MockERC20 Transfer events the swap also emits; web3 warns on each
# one it can't match against the Swap ABI before correctly discarding it.
# Expected noise, not a real problem -- silenced so it doesn't bury the CSV
# output when this script's stdout is redirected to a file.
warnings.filterwarnings("ignore", message="The log with transaction hash")

ANVIL_PORT = 8560
ANVIL_RPC_URL = f"http://127.0.0.1:{ANVIL_PORT}"
ONCHAIN_DIR = os.path.join(os.path.dirname(__file__), "..", "onchain")
OUT_DIR = os.path.join(ONCHAIN_DIR, "out")

# Anvil's well-known, publicly documented default dev account #0. This key
# is never used anywhere except this local, throwaway Anvil node.
DEPLOYER_KEY = "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80"

INITIAL_LIQUIDITY = 1_000_000 * 10**18
TRADE_COUNT = 40
MIN_TRADE = 500 * 10**18
MAX_TRADE = 20_000 * 10**18
RANDOM_SEED = 20260903  # fixed so the committed CSV is reproducible byte-for-byte


def load_artifact(contract_file: str, contract_name: str) -> dict:
    path = os.path.join(OUT_DIR, contract_file, f"{contract_name}.json")
    with open(path) as f:
        artifact = json.load(f)
    return {"abi": artifact["abi"], "bytecode": artifact["bytecode"]["object"]}


def start_anvil() -> subprocess.Popen:
    proc = subprocess.Popen(
        ["anvil", "--port", str(ANVIL_PORT), "--silent"],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    w3 = Web3(Web3.HTTPProvider(ANVIL_RPC_URL))
    for _ in range(50):
        if w3.is_connected():
            return proc
        time.sleep(0.1)
    proc.terminate()
    raise RuntimeError("anvil did not become reachable within 5 seconds")


def deploy(w3: Web3, deployer: str, artifact: dict, *constructor_args):
    contract = w3.eth.contract(abi=artifact["abi"], bytecode=artifact["bytecode"])
    tx_hash = contract.constructor(*constructor_args).transact({"from": deployer})
    receipt = w3.eth.wait_for_transaction_receipt(tx_hash)
    return w3.eth.contract(address=receipt.contractAddress, abi=artifact["abi"])


def main() -> None:
    if not os.path.isdir(OUT_DIR):
        sys.exit(
            "onchain/out/ not found -- run `forge build` in onchain/ first "
            "so this script has compiled ABIs/bytecode to deploy from."
        )

    anvil_proc = start_anvil()
    try:
        w3 = Web3(Web3.HTTPProvider(ANVIL_RPC_URL))
        deployer = w3.eth.account.from_key(DEPLOYER_KEY).address
        w3.eth.default_account = deployer

        token_artifact = load_artifact("MockERC20.sol", "MockERC20")
        pool_artifact = load_artifact("ConstantProductPool.sol", "ConstantProductPool")

        token0 = deploy(w3, deployer, token_artifact, "Token Zero", "TK0")
        token1 = deploy(w3, deployer, token_artifact, "Token One", "TK1")
        pool = deploy(w3, deployer, pool_artifact, token0.address, token1.address)

        def send(fn) -> None:
            tx_hash = fn.transact({"from": deployer})
            w3.eth.wait_for_transaction_receipt(tx_hash)

        send(token0.functions.mint(deployer, INITIAL_LIQUIDITY * 10))
        send(token1.functions.mint(deployer, INITIAL_LIQUIDITY * 10))
        send(token0.functions.approve(pool.address, 2**256 - 1))
        send(token1.functions.approve(pool.address, 2**256 - 1))
        send(pool.functions.addLiquidity(INITIAL_LIQUIDITY, INITIAL_LIQUIDITY))

        rng = random.Random(RANDOM_SEED)
        rows = []
        for _ in range(TRADE_COUNT):
            zero_for_one = rng.random() < 0.5
            token_in = token0 if zero_for_one else token1
            amount_in = rng.randint(MIN_TRADE, MAX_TRADE)

            reserve0_before = pool.functions.reserve0().call()
            reserve1_before = pool.functions.reserve1().call()

            tx_hash = pool.functions.swap(token_in.address, amount_in).transact({"from": deployer})
            receipt = w3.eth.wait_for_transaction_receipt(tx_hash)
            swap_event = pool.events.Swap().process_receipt(receipt)[0]["args"]

            reserve0_after = pool.functions.reserve0().call()
            reserve1_after = pool.functions.reserve1().call()
            realized_price_1e18 = (swap_event["amountOut"] * 10**18) // amount_in

            rows.append(
                {
                    "block_number": receipt.blockNumber,
                    "token_in": "token0" if zero_for_one else "token1",
                    "amount_in": amount_in,
                    "amount_out": swap_event["amountOut"],
                    "reserve0_before": reserve0_before,
                    "reserve1_before": reserve1_before,
                    "reserve0_after": reserve0_after,
                    "reserve1_after": reserve1_after,
                    "realized_price_1e18": realized_price_1e18,
                }
            )

        writer = csv.DictWriter(sys.stdout, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)
    finally:
        anvil_proc.terminate()
        anvil_proc.wait(timeout=5)


if __name__ == "__main__":
    main()
