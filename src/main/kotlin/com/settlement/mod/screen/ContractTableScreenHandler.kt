package com.settlement.mod.screen

import com.settlement.mod.Settlement
import net.minecraft.entity.player.PlayerEntity
import com.settlement.mod.screen.ModScreens
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.slot.Slot

class ContractTableScreenHandler(
    syncId: Int,
    playerInventory: PlayerInventory,
) : ScreenHandler(ModScreens.CONTRACT_TABLE_SCREEN_HANDLER, syncId) {
    private val inventory = SimpleInventory(9)

    init {
        checkSize(inventory, 9)
        inventory.onOpen(playerInventory.player)

        for (m in 0 until 3) {
            for (l in 0 until 3) {
                addSlot(Slot(inventory, l + m * 3, 62 + l * 18, 17 + m * 18))
            }
        }

        for (m in 0 until 3) {
            for (l in 0 until 9) {
                addSlot(Slot(playerInventory, l + m * 9 + 9, 8 + l * 18, 84 + m * 18))
            }
        }

        for (m in 0 until 9) {
            addSlot(Slot(playerInventory, m, 8 + m * 18, 142))
        }
    }

    override fun canUse(player: PlayerEntity): Boolean = inventory.canPlayerUse(player)

    override fun quickMove(
        player: PlayerEntity,
        invSlot: Int,
    ): ItemStack {
        var newStack = ItemStack.EMPTY
        val slot = slots[invSlot]

        if (slot.hasStack()) {
            val originalStack = slot.stack
            newStack = originalStack.copy()

            if (invSlot < inventory.size()) {
                if (!insertItem(originalStack, inventory.size(), slots.size, true)) {
                    return ItemStack.EMPTY
                }
            } else if (!insertItem(originalStack, 0, inventory.size(), false)) {
                return ItemStack.EMPTY
            }

            if (originalStack.isEmpty) {
                slot.stack = ItemStack.EMPTY
            } else {
                slot.markDirty()
            }
        }

        return newStack
    }
}
