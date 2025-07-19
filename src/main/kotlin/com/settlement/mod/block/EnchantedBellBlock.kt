package com.settlement.mod.block

import com.mojang.serialization.MapCodec
import com.settlement.mod.block.entity.EnchantedBellBlockEntity
import com.settlement.mod.item.HandBellItem
import com.settlement.mod.item.ModItems
import com.settlement.mod.world.SettlementAccessor
import net.minecraft.block.AbstractBlock
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.BlockWithEntity
import net.minecraft.block.ShapeContext
import net.minecraft.block.entity.BlockEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.ActionResult
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes
import net.minecraft.world.BlockView
import net.minecraft.world.World

class EnchantedBellBlock(
    settings: AbstractBlock.Settings,
) : BlockWithEntity(settings) {
    override fun getCodec(): MapCodec<out EnchantedBellBlock> = createCodec(::EnchantedBellBlock)

    override fun onUse(
        state: BlockState,
        world: World,
        pos: BlockPos,
        player: PlayerEntity,
        hit: BlockHitResult,
    ): ActionResult {
        if (!world.isClient && player is ServerPlayerEntity) {
            val itemStack = player.getMainHandStack()
            if (itemStack.isOf(ModItems.HAND_BELL)) {
                SettlementAccessor.findNearestSettlementRef(world, pos)?.let { ref ->
                    if (ref.pos.equals(hit.getBlockPos())) {
                        (itemStack.getItem() as HandBellItem).ringAt(player, itemStack, ref.pos)
                    }
                }
            }
            // state.createScreenHandlerFactory(world, pos)?.let { screen ->
            //    player.openHandledScreen(screen)
            // }
        }
        return ActionResult.SUCCESS
    }

    override fun createBlockEntity(
        pos: BlockPos,
        state: BlockState,
    ): BlockEntity? = EnchantedBellBlockEntity(pos, state)

    override fun getCollisionShape(
        state: BlockState,
        world: BlockView,
        pos: BlockPos,
        context: ShapeContext,
    ): VoxelShape = Block.createCubeShape(7.0)

    override fun getOutlineShape(
        state: BlockState,
        world: BlockView,
        pos: BlockPos,
        context: ShapeContext,
    ): VoxelShape =
        Block.createCubeShape(7.0)
}
