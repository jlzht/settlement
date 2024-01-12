package com.settlement.mod.screen

import com.settlement.mod.Settlement
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.Inventory
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.network.PacketByteBuf
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.slot.Slot
import net.minecraft.server.network.ServerPlayerEntity

class TradingScreenHandler(
    syncId: Int,
    playerInventory: PlayerInventory,
) : ScreenHandler(Settlement.TRADING_SCREEN_HANDLER, syncId) {
    constructor(syncId: Int, playerInventory: PlayerInventory, buf: PacketByteBuf) : this(syncId, playerInventory) {
        // Additional initialization logic if needed
    }

    var inventory: Inventory = SimpleInventory(3)

    init {
        for (i in 0 until 3) {
            for (j in 0 until 9) {
                addSlot(Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, 84 + i * 18))
            }
        }
        for (i in 0 until 9) {
            addSlot(Slot(playerInventory, i, 8 + i * 18, 142))
        }
    }

    override fun onContentChanged(inventory: Inventory) {
        super.onContentChanged(inventory)
    }

    override fun canUse(player: PlayerEntity): Boolean = true

    override fun canInsertIntoSlot(
        stack: ItemStack,
        slot: Slot,
    ): Boolean = false

    override fun quickMove(
        player: PlayerEntity,
        slot: Int,
    ): ItemStack {
        var itemStack = ItemStack.EMPTY
        val slot2 = slots[slot]
        if (slot2 != null && slot2.hasStack()) {
            val itemStack2 = slot2.stack
            itemStack = itemStack2.copy()
            if (slot == 2) {
                if (!insertItem(itemStack2, 3, 39, true)) {
                    return ItemStack.EMPTY
                }
                slot2.onQuickTransfer(itemStack2, itemStack)
            } else if (slot == 0 || slot == 1) {
                if (!insertItem(itemStack2, 3, 39, false)) return ItemStack.EMPTY
            } else if (slot in 3 until 30) {
                if (!insertItem(itemStack2, 30, 39, false)) return ItemStack.EMPTY
            } else if (slot in 30 until 39) {
                if (!insertItem(itemStack2, 3, 30, false)) return ItemStack.EMPTY
            }
            if (itemStack2.isEmpty) {
                slot2.stack = ItemStack.EMPTY
            } else {
                slot2.markDirty()
            }
            if (itemStack2.count == itemStack.count) return ItemStack.EMPTY
            slot2.onTakeItem(player, itemStack2)
        }
        return itemStack
    }

    override fun onClosed(player: PlayerEntity) {
        super.onClosed(player)
        if (!player.isAlive || player is ServerPlayerEntity && player.isDisconnected) {
            for (i in 0..5) {
                val itemStack = inventory.removeStack(i)
                if (!itemStack.isEmpty) {
                    player.dropItem(itemStack, false)
                }
            }
        } else if (player is ServerPlayerEntity) {
        }
    }
}
