package com.settlement.mod

import com.settlement.mod.data.ItemTagProvider
import com.settlement.mod.data.ItemPriceProvider
import com.settlement.mod.data.BlockTagProvider
import com.settlement.mod.data.ProfessionLootTableProvider
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator

object SettlementDataGenerator : DataGeneratorEntrypoint {
    override fun onInitializeDataGenerator(fabricDataGenerator: FabricDataGenerator) {
        val pack = fabricDataGenerator.createPack()

        pack.addProvider(::ProfessionLootTableProvider)
        pack.addProvider(::BlockTagProvider)
        pack.addProvider(::ItemTagProvider)
        pack.addProvider(::ItemPriceProvider)
    }
}
