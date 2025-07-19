package com.settlement.mod.block.entity

import com.settlement.mod.screen.ContractTableScreenHandler
import com.settlement.mod.world.Settlement
import com.settlement.mod.world.SettlementManager
import net.minecraft.block.BlockState
import net.minecraft.block.entity.BlockEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.nbt.NbtCompound
import net.minecraft.registry.RegistryWrapper
import net.minecraft.screen.NamedScreenHandlerFactory
import net.minecraft.screen.ScreenHandler
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos

class EnchantedBellBlockEntity(
    pos: BlockPos,
    state: BlockState,
) : BlockEntity(ModBlockEntities.ENCHANTED_BELL_BLOCK_ENTITY, pos, state),
    NamedScreenHandlerFactory {
    override fun createMenu(
        syncId: Int,
        playerInventory: PlayerInventory,
        player: PlayerEntity,
    ): ScreenHandler = ContractTableScreenHandler(syncId, playerInventory)

    lateinit var settlement: Settlement
        private set

    override fun readNbt(
        nbt: NbtCompound,
        registries: RegistryWrapper.WrapperLookup,
    ) {
        super.readNbt(nbt, registries)

        // only try to decode if the tag is present
        if (nbt.contains("Settlement")) {
            settlement =
                nbt
                    .get("Settlement", Settlement.CODEC)
                    .orElseThrow {
                        IllegalStateException("EnchantedBell loaded with bad Settlement data")
                    }

            SettlementManager.loadSettlement(settlement)
        }
    }

    override fun writeNbt(
        nbt: NbtCompound,
        registries: RegistryWrapper.WrapperLookup,
    ) {
        super.writeNbt(nbt, registries)
        if (::settlement.isInitialized) {
            nbt.put("Settlement", Settlement.CODEC, settlement)
        }
        SettlementManager.unloadSettlement(settlement.id)
    }

    fun bindSettlement(sett: Settlement) {
        settlement = sett
        markDirty()
    }

    override fun getDisplayName(): Text = Text.translatable(getCachedState().getBlock().getTranslationKey())
}
