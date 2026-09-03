// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {Test} from "forge-std/Test.sol";
import {MockERC20} from "../src/MockERC20.sol";
import {ConstantProductPool} from "../src/ConstantProductPool.sol";

contract ConstantProductPoolTest is Test {
    MockERC20 internal token0;
    MockERC20 internal token1;
    ConstantProductPool internal pool;

    address internal lp = address(0xA11CE);
    address internal trader = address(0xB0B);

    function setUp() public {
        token0 = new MockERC20("Token Zero", "TK0");
        token1 = new MockERC20("Token One", "TK1");
        pool = new ConstantProductPool(token0, token1);

        token0.mint(lp, 1_000_000 ether);
        token1.mint(lp, 1_000_000 ether);
        token0.mint(trader, 1_000_000 ether);
        token1.mint(trader, 1_000_000 ether);

        vm.startPrank(lp);
        token0.approve(address(pool), type(uint256).max);
        token1.approve(address(pool), type(uint256).max);
        vm.stopPrank();

        vm.startPrank(trader);
        token0.approve(address(pool), type(uint256).max);
        token1.approve(address(pool), type(uint256).max);
        vm.stopPrank();
    }

    function test_AddLiquidity_FirstDepositMintsSharesMinusMinimumLiquidity() public {
        vm.prank(lp);
        uint256 shares = pool.addLiquidity(10_000 ether, 10_000 ether);

        // sqrt(10_000e18 * 10_000e18) = 10_000e18; the caller is minted that
        // minus the 1_000 wei MINIMUM_LIQUIDITY, which stays locked forever
        // but is still counted in totalLpShares (Uniswap V2's convention).
        assertEq(shares, 10_000 ether - 1_000);
        assertEq(pool.reserve0(), 10_000 ether);
        assertEq(pool.reserve1(), 10_000 ether);
        assertEq(pool.totalLpShares(), 10_000 ether);
        assertEq(pool.lpShareOf(lp), 10_000 ether - 1_000);
    }

    function test_AddLiquidity_SubsequentDepositMintsProportionalShares() public {
        vm.prank(lp);
        pool.addLiquidity(10_000 ether, 10_000 ether);

        address lp2 = address(0xCAFE);
        token0.mint(lp2, 1_000 ether);
        token1.mint(lp2, 1_000 ether);
        vm.startPrank(lp2);
        token0.approve(address(pool), type(uint256).max);
        token1.approve(address(pool), type(uint256).max);
        // Depositing 10% of the existing reserves should mint 10% of totalLpShares.
        uint256 shares = pool.addLiquidity(1_000 ether, 1_000 ether);
        vm.stopPrank();

        assertEq(shares, pool.totalLpShares() / 11); // 1_000 / (10_000 + 1_000) == 1/11
    }

    function test_RemoveLiquidity_ReturnsProportionalReserves() public {
        vm.startPrank(lp);
        uint256 shares = pool.addLiquidity(10_000 ether, 10_000 ether);
        uint256 balBefore0 = token0.balanceOf(lp);
        uint256 balBefore1 = token1.balanceOf(lp);

        (uint256 amount0, uint256 amount1) = pool.removeLiquidity(shares);
        vm.stopPrank();

        // lp only holds (totalLpShares - MINIMUM_LIQUIDITY) shares, so redeeming
        // all of them returns proportionally less than the full reserves; the
        // locked 1_000 wei of shares' worth of reserves stays in the pool forever.
        assertEq(amount0, 10_000 ether - 1_000);
        assertEq(amount1, 10_000 ether - 1_000);
        assertEq(token0.balanceOf(lp), balBefore0 + amount0);
        assertEq(token1.balanceOf(lp), balBefore1 + amount1);
    }

    function test_Swap_ChargesFeeAndRespectsConstantProduct() public {
        vm.prank(lp);
        pool.addLiquidity(100_000 ether, 100_000 ether);

        uint256 reserve0Before = pool.reserve0();
        uint256 reserve1Before = pool.reserve1();

        vm.prank(trader);
        uint256 amountOut = pool.swap(address(token0), 1_000 ether);

        // Hand-computed expected output for a 1_000e18 input at 30bps fee against 100_000e18/100_000e18 reserves:
        // amountInAfterFee = 1_000e18 * 9_970 = 9.97e21
        // amountOut = 100_000e18 * 9.97e21 / (100_000e18 * 10_000 + 9.97e21)
        uint256 amountInAfterFee = 1_000 ether * 9_970;
        uint256 expected = (100_000 ether * amountInAfterFee) / (100_000 ether * 10_000 + amountInAfterFee);
        assertEq(amountOut, expected);

        assertEq(pool.reserve0(), reserve0Before + 1_000 ether);
        assertEq(pool.reserve1(), reserve1Before - amountOut);

        // Post-fee constant product must not have decreased (fee accrues to LPs, growing k over time).
        assertGe(pool.reserve0() * pool.reserve1(), reserve0Before * reserve1Before);
    }

    function test_Swap_RevertsOnUnknownToken() public {
        vm.prank(lp);
        pool.addLiquidity(10_000 ether, 10_000 ether);

        MockERC20 rogue = new MockERC20("Rogue", "RGE");
        rogue.mint(trader, 100 ether);
        vm.startPrank(trader);
        rogue.approve(address(pool), type(uint256).max);
        vm.expectRevert("ConstantProductPool: unknown token");
        pool.swap(address(rogue), 100 ether);
        vm.stopPrank();
    }

    /// @notice The textbook AMM slippage property: as a trade grows relative
    ///         to the pool's reserves, its realized execution price should
    ///         move further from the pre-trade spot price (worse fill),
    ///         never better. This is the price-impact invariant a market
    ///         maker pricing against this pool would rely on.
    function testFuzz_PriceImpactGrowsWithTradeSize(uint256 smallAmount, uint256 largeAmountMultiplier) public {
        smallAmount = bound(smallAmount, 1 ether, 1_000 ether);
        largeAmountMultiplier = bound(largeAmountMultiplier, 2, 50);
        uint256 largeAmount = smallAmount * largeAmountMultiplier;

        vm.prank(lp);
        pool.addLiquidity(1_000_000 ether, 1_000_000 ether);
        uint256 spotPriceBefore = pool.spotPrice0In1();

        // Snapshot state so both trades execute against the same starting reserves.
        uint256 snapshot = vm.snapshotState();

        vm.prank(trader);
        uint256 smallOut = pool.swap(address(token0), smallAmount);
        uint256 smallExecPrice1e18 = (smallOut * 1e18) / smallAmount;

        vm.revertToState(snapshot);

        vm.prank(trader);
        uint256 largeOut = pool.swap(address(token0), largeAmount);
        uint256 largeExecPrice1e18 = (largeOut * 1e18) / largeAmount;

        // Both trades fill worse than the pre-trade spot price (token0 is being sold into the pool).
        assertLt(smallExecPrice1e18, spotPriceBefore);
        assertLt(largeExecPrice1e18, spotPriceBefore);
        // The larger trade's realized price is further from spot: strictly worse execution.
        assertLt(largeExecPrice1e18, smallExecPrice1e18);
    }
}
