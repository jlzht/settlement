package com.settlement.mod.item

import com.settlement.mod.screen.Response
import com.settlement.mod.world.SettlementManager
import net.minecraft.block.BellBlock
import net.minecraft.client.item.TooltipContext
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.text.Text
import net.minecraft.util.Hand
import net.minecraft.util.TypedActionResult
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.world.RaycastContext
import net.minecraft.world.World

class HandBellItem(
    settings: Settings,
) : Item(settings) {
    override fun use(
        world: World,
        user: PlayerEntity,
        hand: Hand,
    ): TypedActionResult<ItemStack> {
        val itemStack = user.getStackInHand(hand)
        val blockHitResult: BlockHitResult = Item.raycast(world, user, RaycastContext.FluidHandling.NONE)
        if (blockHitResult.getType() == HitResult.Type.MISS) {
            return TypedActionResult.pass(itemStack)
        }
        if (blockHitResult.getType() == HitResult.Type.BLOCK) {
            val pos: BlockPos = blockHitResult.getBlockPos()
            user.getItemCooldownManager().set(this, 45)
            if (!world.isClient && !(user.world.getBlockState(pos) is BellBlock)) {
                val bind = itemStack.orCreateNbt.getString("Bind")
                val manager = SettlementManager.getInstance()
                val settlements = manager.getSettlements()
                if (!bind.equals("")) {
                    settlements.firstOrNull { it.name.equals(bind) }?.let { settlement ->
                        settlement.createStructure(pos, user)
                        itemStack.damage(1, user) { p -> p.sendToolBreakStatus(hand) }
                    } ?: run {
                        Response.NO_SETTLEMENT_NEARBY.send(user)
                    }
                } else {
                    Response.NO_SETTLEMENT_NEARBY.send(user)
                }

                return TypedActionResult.success(itemStack, world.isClient)
            }
            return TypedActionResult.fail(itemStack)
        }
        return TypedActionResult.pass(itemStack)
    }

    override fun appendTooltip(
        stack: ItemStack,
        world: World?,
        tooltip: MutableList<Text>,
        context: TooltipContext,
    ) {
        val bind = stack.nbt?.getString("Bind") ?: ""
        if (!bind.equals("")) {
            tooltip.add(Text.literal("Bound to Settlement: $bind"))
        }
    }
}
