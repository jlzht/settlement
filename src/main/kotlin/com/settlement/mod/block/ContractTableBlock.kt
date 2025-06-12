package com.settlement.mod.block

import com.mojang.serialization.MapCodec
import com.settlement.mod.block.entity.ContractTableBlockEntity
import com.settlement.mod.screen.ContractTableScreenHandler
import net.minecraft.block.BlockState
import net.minecraft.block.BlockWithEntity
import net.minecraft.block.entity.BlockEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.screen.NamedScreenHandlerFactory
import net.minecraft.screen.SimpleNamedScreenHandlerFactory
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

class ContractTableBlock(
    settings: Settings,
) : BlockWithEntity(settings) {
    override fun getCodec(): MapCodec<out ContractTableBlock> = createCodec(::ContractTableBlock)

    override fun onUse(
        state: BlockState,
        world: World,
        pos: BlockPos,
        player: PlayerEntity,
        hit: BlockHitResult,
    ): ActionResult {
        if (!world.isClient && player is ServerPlayerEntity) {
            state.createScreenHandlerFactory(world, pos)?.let { screen ->
                player.openHandledScreen(screen)
            }
        }
        return ActionResult.SUCCESS
    }

    override fun createScreenHandlerFactory(
        state: BlockState,
        world: World,
        pos: BlockPos,
    ): NamedScreenHandlerFactory? {
        val blockEntity = world.getBlockEntity(pos)
        return if (blockEntity is ContractTableBlockEntity) {
            SimpleNamedScreenHandlerFactory(
                { syncId, playerInventory, _ ->
                    ContractTableScreenHandler(syncId, playerInventory)
                },
                Text.translatable("container.contract_table"),
            )
        } else {
            null
        }
    }

    override fun createBlockEntity(
        pos: BlockPos,
        state: BlockState,
    ): BlockEntity? = ContractTableBlockEntity(pos, state)
}
