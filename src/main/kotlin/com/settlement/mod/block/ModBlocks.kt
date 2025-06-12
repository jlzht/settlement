package com.settlement.mod.block

import com.settlement.mod.MODID
import net.minecraft.block.AbstractBlock
import net.minecraft.block.Block
import net.minecraft.item.BlockItem
import net.minecraft.item.Item
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.sound.BlockSoundGroup
import net.minecraft.util.Identifier

object ModBlocks {
    @JvmField
    val ENCHANTED_BELL =
        register(
            "charged_bell",
            { settings -> EnchantedBellBlock(settings) },
            AbstractBlock.Settings
                .create()
                .strength(-1.0f, 3600000.0f)
                .dropsNothing()
                .sounds(BlockSoundGroup.LODESTONE),
            false,
        )

    val CONTRACT_TABLE =
        register(
            "contract_table",
            { settings -> ContractTableBlock(settings) },
            AbstractBlock.Settings.create().sounds(BlockSoundGroup.WOOD),
            true,
        )

    fun initialize() {
        ENCHANTED_BELL
        CONTRACT_TABLE
    }

    private fun register(
        name: String,
        blockFactory: (AbstractBlock.Settings) -> Block,
        settings: AbstractBlock.Settings,
        shouldRegisterItem: Boolean,
    ): Block {
        val blockKey: RegistryKey<Block> = keyOfBlock(name)
        val block: Block = blockFactory(settings.registryKey(blockKey))

        if (shouldRegisterItem) {
            val itemKey: RegistryKey<Item> = keyOfItem(name)
            val blockItem = BlockItem(block, Item.Settings().registryKey(itemKey))
            Registry.register(Registries.ITEM, itemKey, blockItem)
        }

        return Registry.register(Registries.BLOCK, blockKey, block)
    }

    private fun keyOfBlock(name: String): RegistryKey<Block> = RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(MODID, name))

    private fun keyOfItem(name: String): RegistryKey<Item> = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MODID, name))
}
