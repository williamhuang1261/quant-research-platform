// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {Script, console2} from "forge-std/Script.sol";
import {MockERC20} from "../src/MockERC20.sol";
import {ConstantProductPool} from "../src/ConstantProductPool.sol";

/// @notice Deploys a fresh pool, seeds it with initial liquidity at a 1:1
///         price, then pushes the price away from 1:1 with one large swap
///         and immediately executes the opposite-direction "rebalancing"
///         trade an arbitrageur would make to pull the pool back toward
///         its externally-fair price -- the same mechanism that keeps a
///         real AMM's price anchored to the wider market. Run against a
///         local Anvil node:
///
///     anvil &
///     forge script script/Rebalance.s.sol --rpc-url http://127.0.0.1:8545 --broadcast
contract RebalanceScript is Script {
    uint256 internal constant INITIAL_LIQUIDITY = 1_000_000 ether;
    uint256 internal constant DISPLACEMENT_TRADE = 50_000 ether;

    function run() external {
        uint256 deployerKey = vm.envOr(
            "PRIVATE_KEY",
            uint256(0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80)
        );
        vm.startBroadcast(deployerKey);

        MockERC20 token0 = new MockERC20("Token Zero", "TK0");
        MockERC20 token1 = new MockERC20("Token One", "TK1");
        ConstantProductPool pool = new ConstantProductPool(token0, token1);

        address deployer = vm.addr(deployerKey);
        token0.mint(deployer, INITIAL_LIQUIDITY + DISPLACEMENT_TRADE);
        token1.mint(deployer, INITIAL_LIQUIDITY);
        token0.approve(address(pool), type(uint256).max);
        token1.approve(address(pool), type(uint256).max);

        pool.addLiquidity(INITIAL_LIQUIDITY, INITIAL_LIQUIDITY);
        console2.log("pool deployed at", address(pool));
        console2.log("spot price (1e18) after seeding:", pool.spotPrice0In1());

        // Displace the price by selling a large amount of token0 into the pool.
        pool.swap(address(token0), DISPLACEMENT_TRADE);
        uint256 displacedPrice = pool.spotPrice0In1();
        console2.log("spot price (1e18) after displacement swap:", displacedPrice);

        // A rebalancing trade in the opposite direction pulls the price back
        // toward (but, because of the fee, never exactly to) the original 1:1 ratio.
        uint256 token1Balance = token1.balanceOf(deployer);
        pool.swap(address(token1), token1Balance / 10);
        console2.log("spot price (1e18) after rebalancing swap:", pool.spotPrice0In1());

        vm.stopBroadcast();
    }
}
