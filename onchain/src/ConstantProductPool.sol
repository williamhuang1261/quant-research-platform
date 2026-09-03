// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {MockERC20} from "./MockERC20.sol";

/// @notice A minimal two-asset constant-product AMM (Uniswap V2 style):
///         reserves move so that reserve0 * reserve1 stays constant (up to
///         fee accrual) after every swap. Internal LP-share accounting only,
///         no external token standard beyond the vendored MockERC20 pair.
contract ConstantProductPool {
    uint256 private constant FEE_BPS = 30; // 0.30%, matching Uniswap V2's convention
    uint256 private constant BPS_DENOMINATOR = 10_000;
    uint256 private constant MINIMUM_LIQUIDITY = 1_000;

    MockERC20 public immutable token0;
    MockERC20 public immutable token1;

    uint256 public reserve0;
    uint256 public reserve1;
    uint256 public totalLpShares;
    mapping(address => uint256) public lpShareOf;

    event LiquidityAdded(address indexed provider, uint256 amount0, uint256 amount1, uint256 lpShares);
    event LiquidityRemoved(address indexed provider, uint256 amount0, uint256 amount1, uint256 lpShares);
    event Swap(address indexed trader, address tokenIn, uint256 amountIn, uint256 amountOut);

    constructor(MockERC20 _token0, MockERC20 _token1) {
        require(address(_token0) != address(_token1), "ConstantProductPool: identical tokens");
        token0 = _token0;
        token1 = _token1;
    }

    /// @notice Deposits both assets in the pool's current ratio (or any
    ///         ratio on first deposit) and mints LP shares proportional to
    ///         the deposit's contribution to the pool.
    function addLiquidity(uint256 amount0, uint256 amount1) external returns (uint256 lpShares) {
        require(amount0 > 0 && amount1 > 0, "ConstantProductPool: zero amount");

        token0.transferFrom(msg.sender, address(this), amount0);
        token1.transferFrom(msg.sender, address(this), amount1);

        if (totalLpShares == 0) {
            uint256 initialShares = _sqrt(amount0 * amount1);
            require(initialShares > MINIMUM_LIQUIDITY, "ConstantProductPool: insufficient initial liquidity");
            lpShares = initialShares - MINIMUM_LIQUIDITY;
            // MINIMUM_LIQUIDITY is counted in totalLpShares but never assigned to any
            // address (locked forever), same convention Uniswap V2 uses to deter a
            // zero-supply pool while keeping every future pro-rata calculation correct.
            totalLpShares = initialShares;
        } else {
            uint256 shareFrom0 = (amount0 * totalLpShares) / reserve0;
            uint256 shareFrom1 = (amount1 * totalLpShares) / reserve1;
            lpShares = shareFrom0 < shareFrom1 ? shareFrom0 : shareFrom1;
            totalLpShares += lpShares;
        }
        require(lpShares > 0, "ConstantProductPool: insufficient liquidity minted");

        reserve0 += amount0;
        reserve1 += amount1;
        lpShareOf[msg.sender] += lpShares;

        emit LiquidityAdded(msg.sender, amount0, amount1, lpShares);
    }

    /// @notice Burns LP shares and returns each asset in proportion to the
    ///         pool's current reserves.
    function removeLiquidity(uint256 lpShares) external returns (uint256 amount0, uint256 amount1) {
        require(lpShares > 0 && lpShares <= lpShareOf[msg.sender], "ConstantProductPool: invalid lpShares");

        amount0 = (lpShares * reserve0) / totalLpShares;
        amount1 = (lpShares * reserve1) / totalLpShares;
        require(amount0 > 0 && amount1 > 0, "ConstantProductPool: zero withdrawal");

        lpShareOf[msg.sender] -= lpShares;
        totalLpShares -= lpShares;
        reserve0 -= amount0;
        reserve1 -= amount1;

        token0.transfer(msg.sender, amount0);
        token1.transfer(msg.sender, amount1);

        emit LiquidityRemoved(msg.sender, amount0, amount1, lpShares);
    }

    /// @notice Swaps `amountIn` of `tokenIn` for the other asset, charging a
    ///         30bps fee and enforcing the constant-product invariant on the
    ///         post-fee input.
    function swap(address tokenIn, uint256 amountIn) external returns (uint256 amountOut) {
        require(amountIn > 0, "ConstantProductPool: zero amount");
        require(tokenIn == address(token0) || tokenIn == address(token1), "ConstantProductPool: unknown token");

        bool zeroForOne = tokenIn == address(token0);
        (MockERC20 tokenInErc, MockERC20 tokenOutErc, uint256 reserveIn, uint256 reserveOut) = zeroForOne
            ? (token0, token1, reserve0, reserve1)
            : (token1, token0, reserve1, reserve0);

        tokenInErc.transferFrom(msg.sender, address(this), amountIn);

        uint256 amountInAfterFee = amountIn * (BPS_DENOMINATOR - FEE_BPS);
        // x*y=k solved for amountOut using the post-fee input:
        // amountOut = reserveOut * amountInAfterFee / (reserveIn * BPS_DENOMINATOR + amountInAfterFee)
        amountOut = (reserveOut * amountInAfterFee) / (reserveIn * BPS_DENOMINATOR + amountInAfterFee);
        require(amountOut > 0, "ConstantProductPool: insufficient output");

        if (zeroForOne) {
            reserve0 += amountIn;
            reserve1 -= amountOut;
        } else {
            reserve1 += amountIn;
            reserve0 -= amountOut;
        }

        tokenOutErc.transfer(msg.sender, amountOut);

        emit Swap(msg.sender, tokenIn, amountIn, amountOut);
    }

    /// @notice The pool's current spot price of token0 in terms of token1,
    ///         scaled by 1e18, before any fee or slippage is applied.
    function spotPrice0In1() external view returns (uint256) {
        require(reserve0 > 0, "ConstantProductPool: no liquidity");
        return (reserve1 * 1e18) / reserve0;
    }

    function _sqrt(uint256 x) private pure returns (uint256 y) {
        if (x == 0) return 0;
        uint256 z = (x + 1) / 2;
        y = x;
        while (z < y) {
            y = z;
            z = (x / z + z) / 2;
        }
    }
}
