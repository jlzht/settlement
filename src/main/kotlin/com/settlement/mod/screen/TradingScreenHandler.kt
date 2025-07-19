package com.settlement.mod.screen
import com.settlement.mod.item.ModItems
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.Inventory
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.ScreenHandlerListener
import net.minecraft.screen.slot.Slot

// use this in SimpleFishingBobber
interface ConditionalCloseHandler {
    var isClosed: Boolean

    fun shouldClose(): Boolean

    fun setCloseCondition(condition: () -> Boolean)
}

class TradingScreenHandler(
    syncId: Int,
    playerInventory: PlayerInventory,
) : ScreenHandler(ModScreens.TRADING_SCREEN_HANDLER, syncId),
    ScreenHandlerListener,
    ConditionalCloseHandler {
    private val inventory: Inventory = SimpleInventory(INPUT_SLOT_COUNT + OUTPUT_SLOT_COUNT)
    private val world = playerInventory.player.world

    private var closeCondition: () -> Boolean = { false }

    override var isClosed: Boolean = false

    override fun shouldClose(): Boolean = closeCondition()

    override fun setCloseCondition(condition: () -> Boolean) {
        this.closeCondition = condition
    }

    init {
        checkSize(inventory, INPUT_SLOT_COUNT + OUTPUT_SLOT_COUNT)

        addInputSlots()
        addOutputSlots()
        addPlayerInventorySlots(playerInventory)
        this.addListener(this) // revisit this
    }

    private fun addInputSlots() {
        val slots =
            listOf(
                16 to 34,
                34 to 34,
                52 to 34,
                16 to 52,
                34 to 52,
                52 to 52,
            )
        slots.forEachIndexed { index, pos -> this.addSlot(Slot(inventory, index, pos.first, pos.second)) }
    }

    private fun addOutputSlots() {
        val slots =
            listOf(
                107 to 43,
                125 to 43,
                143 to 43,
            )
        slots.forEachIndexed { index, pos ->
            val slotIndex = index + INPUT_SLOT_COUNT
            this.addSlot(OutputSlot(inventory, slotIndex, pos.first, pos.second))
        }
    }

    private fun addPlayerInventorySlots(playerInventory: PlayerInventory) {
        for (row in 0 until 3) {
            for (col in 0 until 9) {
                this.addSlot(Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18))
            }
        }
        for (i in 0 until 9) {
            this.addSlot(Slot(playerInventory, i, 8 + i * 18, 142))
        }
    }

    override fun onContentChanged(inventory: Inventory) {
        super.onContentChanged(inventory)
        // if (world.isClient) return
    }

    override fun onSlotUpdate(
        handler: ScreenHandler,
        slotId: Int,
        stack: ItemStack,
    ) {
        if (!world.isClient) {
            if (slotId < 9) {
                updateTradeOutput()
            }
        }
    }

    private fun updateTradeOutput() {
        val inputStacks = (0 until INPUT_SLOT_COUNT).map { inventory.getStack(it) }
        this.sendContentUpdates()
    }

    fun onTrade(takenStack: ItemStack) {
        if (world.isClient) return
    }

    override fun quickMove(
        player: PlayerEntity,
        slotIndex: Int,
    ): ItemStack {
        val slot = slots.getOrNull(slotIndex)
        if (slot == null || !slot.hasStack()) {
            return ItemStack.EMPTY
        }

        val originalStack = slot.stack
        val newStack = originalStack.copy()

        val moved = insertItem(originalStack, PLAYER_INVENTORY_START_INDEX, PLAYER_INVENTORY_END_INDEX, true)

        if (!moved) return ItemStack.EMPTY

        if (slot is OutputSlot) {
            slot.onTakeItem(player, newStack)
        }
        if (originalStack.isEmpty) {
            slot.stack = ItemStack.EMPTY
        } else {
            slot.markDirty()
        }
        if (!world.isClient) {
            onContentChanged(this.inventory)
        }

        return newStack
    }

    override fun canUse(player: PlayerEntity): Boolean = closeCondition()

    override fun onClosed(player: PlayerEntity) {
        isClosed = true
        super.onClosed(player)
    }

    private inner class OutputSlot(
        inventory: Inventory,
        index: Int,
        x: Int,
        y: Int,
    ) : Slot(inventory, index, x, y) {
        override fun canInsert(stack: ItemStack): Boolean = false

        override fun canTakeItems(playerEntity: PlayerEntity): Boolean = hasStack()

        override fun onTakeItem(
            player: PlayerEntity,
            stack: ItemStack,
        ) {
            onTrade(stack)
            super.onTakeItem(player, stack)
        }
    }

    override fun onPropertyUpdate(
        handler: ScreenHandler,
        property: Int,
        value: Int,
    ) {
    }

    companion object {
        const val INPUT_SLOT_COUNT = 6
        const val OUTPUT_SLOT_COUNT = 3
        private const val PLAYER_INVENTORY_START_INDEX = INPUT_SLOT_COUNT + OUTPUT_SLOT_COUNT
        private const val PLAYER_INVENTORY_END_INDEX = PLAYER_INVENTORY_START_INDEX + 36
    }
}
