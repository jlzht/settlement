package com.settlement.mod.block.entity

import com.settlement.mod.screen.ContractTableScreenHandler
import net.minecraft.block.BlockState
import net.minecraft.block.entity.BlockEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
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

    override fun getDisplayName(): Text = Text.translatable(getCachedState().getBlock().getTranslationKey())
}
