package com.settlement.mod.item

import com.settlement.mod.component.BoundInfo
import com.settlement.mod.LOGGER
import com.settlement.mod.block.EnchantedBellBlock
import com.settlement.mod.component.ModComponentTypes
import com.settlement.mod.screen.Response
import com.settlement.mod.world.SettlementAccessor
import net.minecraft.component.type.TooltipDisplayComponent
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.tooltip.TooltipType
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.Formatting
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.world.RaycastContext
import net.minecraft.world.World
import java.util.function.Consumer

class HandBellItem(
    settings: Settings,
) : Item(settings) {
    override fun use(
        world: World,
        user: PlayerEntity,
        hand: Hand,
    ): ActionResult {
        val itemStack = user.getStackInHand(hand)
        val blockHitResult: BlockHitResult = Item.raycast(world, user, RaycastContext.FluidHandling.ANY)
        if (blockHitResult.getType() == HitResult.Type.MISS) {
            return ActionResult.PASS
        }
        if (blockHitResult.getType() == HitResult.Type.BLOCK) {
            val pos: BlockPos = blockHitResult.getBlockPos()
            user.getItemCooldownManager().set(itemStack, 45)
            if (!world.isClient && !(user.world.getBlockState(pos).getBlock() is EnchantedBellBlock)) {
                itemStack.components.getOrDefault(ModComponentTypes.BOUND_INFO, BoundInfo()).let { component ->
                    if (!component.name.equals("")) {
                        SettlementAccessor.findSettlementById(component.id)?.let { settlement ->
                            settlement.createStructure(pos, user)
                            itemStack.damage(1, user, EquipmentSlot.MAINHAND)
                        } ?: run {
                            // assumes the settlemnt was deleted
                            Response.NO_SETTLEMENT_NEARBY.send(user)
                            return ActionResult.PASS
                        }
                    } else {
                        // not bound not anything
                        Response.NO_SETTLEMENT_NEARBY.send(user)
                        return ActionResult.PASS
                    }
                    return ActionResult.SUCCESS
                }
            }
        }
        return ActionResult.PASS
    }

    fun ringAt(
        player: PlayerEntity,
        stack: ItemStack,
        pos: BlockPos,
    ) {
        SettlementAccessor.findNearestSettlement(player.world, pos)?.let { settlement ->
            settlement.allies[player.uuid]?.let {
                if (it >= 20) {
                    stack.components.getOrDefault(ModComponentTypes.BOUND_INFO, "")?.let { bind ->
                        if (!bind.equals("")) {
                            stack.set(ModComponentTypes.BOUND_INFO, BoundInfo())
                            Response.UNBINDED_SETTLEMENT.send(player, settlement.name)
                        } else {
                            stack.set(ModComponentTypes.BOUND_INFO, BoundInfo(settlement.id, settlement.name))
                            Response.BINDED_TO_SETTLEMENT.send(player, settlement.name)
                        }
                    }
                } else {
                    LOGGER.info("Unable to bind, no reputation")
                }
            }
        }
    }

    override fun appendTooltip(
        stack: ItemStack,
        context: Item.TooltipContext,
        displayComponent: TooltipDisplayComponent,
        textConsumer: Consumer<Text>,
        type: TooltipType,
    ) {
        stack.components.get(ModComponentTypes.BOUND_INFO)?.let { bind ->
            textConsumer.accept(Text.literal("Bound to Settlement: $bind").formatted(Formatting.DARK_GRAY))
        }
    }
}
