package com.settlement.mod.screen

import com.settlement.mod.LOGGER
import com.settlement.mod.Settlement
import com.settlement.mod.screen.ModScreens
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.Inventory
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.ScreenHandlerListener
import net.minecraft.screen.slot.Slot
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.world.World

class TradingScreenHandler(
    syncId: Int,
    playerInventory: PlayerInventory,
) : ScreenHandler(ModScreens.TRADING_SCREEN_HANDLER, syncId),
    ScreenHandlerListener {
    private val world: World = playerInventory.player.world
    private val inventory: Inventory = SimpleInventory(9)
    private val player: PlayerEntity = playerInventory.player

    init {
        // Insertable slots (0?5)
        val insertableSlots =
            listOf(
                Triple(0, 16, 34),
                Triple(1, 34, 34),
                Triple(2, 52, 34),
                Triple(3, 16, 52),
                Triple(4, 34, 52),
                Triple(5, 52, 52),
            )
        for ((index, x, y) in insertableSlots) {
            this.addSlot(
                object : Slot(inventory, index, x, y) {
                    override fun canInsert(stack: ItemStack) = true
                },
            )
        }

        // Output-only slots (6?8)
        val outputSlots =
            listOf(
                Triple(6, 107, 43),
                Triple(7, 125, 43),
                Triple(8, 143, 43),
            )
        for ((index, x, y) in outputSlots) {
            this.addSlot(
                object : Slot(inventory, index, x, y) {
                    override fun canInsert(stack: ItemStack) = false

                    override fun onTakeItem(
                        player: PlayerEntity,
                        stack: ItemStack,
                    ) {
                        val takenValue = getShardValue(stack.item)?.times(stack.count) ?: 0
                        if (takenValue > 0) {
                            decrementInputsByShards(inventory, takenValue)
                            clearOutputSlots(inventory)
                            sendContentUpdates()
                        }
                        super.onTakeItem(player, stack)
                    }
                    // override fun canTakeItems(player: PlayerEntity) = false
                },
            )
        }

        for (row in 0 until 3) {
            for (col in 0 until 9) {
                val slotIndex = col + row * 9 + 9
                this.addSlot(Slot(playerInventory, slotIndex, 8 + col * 18, 84 + row * 18))
            }
        }
        for (i in 0 until 9) {
            this.addSlot(Slot(playerInventory, i, 8 + i * 18, 142))
        }
        this.addListener(this)
    }

    override fun canUse(player: PlayerEntity) = true

    private fun clearOutputSlots(inventory: Inventory) {
        for (i in 6..8) {
            inventory.setStack(i, ItemStack.EMPTY)
        }
    }

    fun decrementInputsByShards(
        inventory: Inventory,
        shardsToConsume: Int,
    ) {
        var remaining = shardsToConsume

        // Coleta lista de (slot, item, count, value_per_item)
        val slotsWithValue =
            (0..5)
                .mapNotNull { i ->
                    val stack = inventory.getStack(i)
                    val value = getShardValue(stack.item) ?: return@mapNotNull null
                    Triple(i, stack, value)
                }.sortedByDescending { (_, _, value) -> value } // mais valiosos primeiro

        for ((slot, stack, valuePerItem) in slotsWithValue) {
            val maxCanConsume = stack.count * valuePerItem
            if (maxCanConsume <= 0) continue

            val toRemove = (remaining / valuePerItem).coerceAtMost(stack.count)
            if (toRemove > 0) {
                stack.decrement(toRemove)
                remaining -= toRemove * valuePerItem
            }

            if (remaining <= 0) break
        }
    }

    override fun onClosed(player: PlayerEntity) {
        super.onClosed(player)
        clearOutputSlots(inventory)
        dropInventory(player, inventory)
    }

    override fun onContentChanged(inventory: Inventory) {
        super.onContentChanged(inventory)
        LOGGER.info("GOT HERE!")
    }

    fun calculateTotalShardValue(inventory: Inventory): Int {
        var total = 0
        for (i in 0..5) {
            val stack = inventory.getStack(i)
            val value = ITEM_TO_SHARDS[stack.item] ?: continue
            total += value * stack.count
        }
        return total
    }

    fun getItemsForShardValue(totalShards: Int): List<Pair<Item, Int>> {
        val result = mutableListOf<Pair<Item, Int>>()
        var remaining = totalShards

        val sortedItems = ITEM_TO_SHARDS.entries.sortedByDescending { it.value }

        for ((item, shardValue) in sortedItems) {
            val count = remaining / shardValue
            if (count > 0) {
                result += item to count
                remaining -= shardValue * count
            }
            if (remaining <= 0) break
        }

        return result
    }

    override fun canInsertIntoSlot(
        stack: ItemStack,
        slot: Slot,
    ): Boolean = true

    override fun quickMove(
        player: PlayerEntity,
        slot: Int,
    ): ItemStack {
        val slotObj = slots.getOrNull(slot) ?: return ItemStack.EMPTY
        if (!slotObj.hasStack()) return ItemStack.EMPTY

        val originalStack = slotObj.stack
        val copyStack = originalStack.copy()

        val moved =
            when (slot) {
                2 -> insertItem(originalStack, 3, 39, true)
                0, 1 -> insertItem(originalStack, 3, 39, false)
                in 3 until 30 -> insertItem(originalStack, 30, 39, false)
                in 30 until 39 -> insertItem(originalStack, 3, 30, false)
                else -> false
            }

        if (!moved) return ItemStack.EMPTY
        slotObj.onQuickTransfer(originalStack, copyStack)

        if (originalStack.isEmpty) {
            slotObj.stack = ItemStack.EMPTY
        } else {
            slotObj.markDirty()
        }

        return if (originalStack.count == copyStack.count) ItemStack.EMPTY else copyStack
    }

    override fun onSlotUpdate(
        handler: ScreenHandler,
        slotId: Int,
        stackz: ItemStack,
    ) {
        LOGGER.info("{} - {}", slotId, stackz)
        val inputValue =
            (0..5).sumOf { slot ->
                val stack = inventory.getStack(slot)
                getShardValue(stack.item)?.times(stack.count) ?: 0
            }

        val totalShards = calculateTotalShardValue(inventory)
        if (totalShards <= 0) {
            for (i in 6..8) {
                if (player is ServerPlayerEntity) {
                    player.networkHandler.sendPacket(ScreenHandlerSlotUpdateS2CPacket(this.syncId, this.nextRevision(), i, ItemStack.EMPTY))
                }
            }
        }

        val resultItems = getItemsForShardValue(totalShards)
        LOGGER.info("{}", resultItems)
        resultItems.take(3).forEachIndexed { i, (item, count) ->
            val stack = ItemStack(item, count)
            inventory.setStack(6 + i, stack)
            this.setReceivedStack(6 + i, stack)
            if (player is ServerPlayerEntity) {
                player.networkHandler.sendPacket(ScreenHandlerSlotUpdateS2CPacket(this.syncId, this.nextRevision(), 6 + i, stack))
            }
        }
    }

    override fun onPropertyUpdate(
        handler: ScreenHandler,
        property: Int,
        value: Int,
    ) {
        // opcional: lida com propriedades sincronizadas
    }

    companion object {
        val ITEM_TO_SHARDS: Map<Item, Int> =
            mapOf(
                Items.DIAMOND to 20,
                Items.GOLD_INGOT to 5,
                Items.IRON_INGOT to 2,
                Items.EMERALD to 9,
                Items.COAL to 1,
                Items.NETHERITE_INGOT to 81,
                Items.APPLE to 1,
            )

        val SHARDS_TO_ITEM: Map<Int, List<Item>> =
            ITEM_TO_SHARDS.entries.groupBy({ it.value }, { it.key })

        fun getShardValue(item: Item): Int? = ITEM_TO_SHARDS[item]

        fun getItemsByShardValue(shards: Int): List<Item> = SHARDS_TO_ITEM[shards] ?: emptyList()
    }
}
