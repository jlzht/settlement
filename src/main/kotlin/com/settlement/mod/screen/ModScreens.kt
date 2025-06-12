package com.settlement.mod.screen

import com.settlement.mod.MODID
import com.settlement.mod.client.gui.screen.TradingScreen
import com.settlement.mod.client.gui.screen.ContractTableScreen
import net.minecraft.client.gui.screen.ingame.HandledScreens
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.resource.featuretoggle.FeatureSet
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.util.Identifier
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.screen.ScreenHandler

object ModScreens {
    private fun <T : ScreenHandler> register(
        id: String,
        factory: (Int, PlayerInventory) -> T,
    ): ScreenHandlerType<T> =
        Registry.register(
            Registries.SCREEN_HANDLER,
            Identifier.of(MODID, id),
            ScreenHandlerType(factory, FeatureSet.empty()),
        )

    val TRADING_SCREEN_HANDLER = register("trading", ::TradingScreenHandler)
    val CONTRACT_TABLE_SCREEN_HANDLER = register("contract_table", ::ContractTableScreenHandler)

    fun initializeClient() {
        HandledScreens.register(TRADING_SCREEN_HANDLER, ::TradingScreen)
        HandledScreens.register(CONTRACT_TABLE_SCREEN_HANDLER, ::ContractTableScreen)
    }
}
