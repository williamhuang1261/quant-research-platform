package io.github.williamhuang1261.qrp.onchain;

import java.math.BigInteger;

/**
 * One row of {@code data/onchain/amm_swaps_<date>.csv}: a single swap
 * executed against {@code onchain/src/ConstantProductPool.sol} on a local
 * Anvil fork, captured by {@code tools/amm_sim.py}.
 *
 * <p>Every amount is a raw 18-decimal on-chain integer ({@link BigInteger}),
 * not a {@code long} or {@code double} — a real pool's reserves and trade
 * sizes routinely exceed {@link Long#MAX_VALUE} (roughly 9.2e18) once scaled
 * by 1e18, and silently truncating them would corrupt every downstream sum.
 *
 * @param blockNumber      the Anvil block the swap was mined in
 * @param tokenInIsToken0  {@code true} if the trader sold token0 for token1
 * @param amountIn         raw amount of the input token, 18-decimal scaled
 * @param amountOut        raw amount of the output token, 18-decimal scaled
 * @param reserve0Before   pool's token0 reserve immediately before the swap
 * @param reserve1Before   pool's token1 reserve immediately before the swap
 * @param reserve0After    pool's token0 reserve immediately after the swap
 * @param reserve1After    pool's token1 reserve immediately after the swap
 */
public record AmmSwapRow(
        long blockNumber,
        boolean tokenInIsToken0,
        BigInteger amountIn,
        BigInteger amountOut,
        BigInteger reserve0Before,
        BigInteger reserve1Before,
        BigInteger reserve0After,
        BigInteger reserve1After) {
}
