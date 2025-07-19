package com.settlement.mod.entity.mob

import com.google.common.collect.ImmutableList
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.inventory.Inventories
import net.minecraft.inventory.Inventory
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtList
import net.minecraft.util.collection.DefaultedList

// TODO: revisit markDirty method
class VillagerInventory(
    private val entity: AbstractVillagerEntity,
) : Inventory {
    private val held = DefaultedList.ofSize(2, ItemStack.EMPTY)
    private val main = DefaultedList.ofSize(9, ItemStack.EMPTY)
    private val armor = DefaultedList.ofSize(4, ItemStack.EMPTY)
    private val merged: List<DefaultedList<ItemStack>> = ImmutableList.of(this.held, this.main, this.armor)

    fun getArmor(slot: Int): ItemStack = armor.get(slot)

    fun setArmor(
        id: Int,
        itemStack: ItemStack,
    ): ItemStack = this.armor.set(id, itemStack)

    fun getHeld(id: Int): ItemStack = held[id]

    fun setHeld(
        id: Int,
        itemStack: ItemStack,
    ): ItemStack = this.held.set(id, itemStack)

    // TODO: make function that returns inventory with decayed values
    fun getItems(): List<ItemStack> = merged.flatMap { it }

    fun canInsert(stack: ItemStack): Boolean {
        for (item in main) {
            if (!item.isEmpty && (!ItemStack.areItemsAndComponentsEqual(item, stack) || item.count >= item.maxCount)) continue
            return true
        }
        return false
    }

    fun takeItem(predicate: (Item) -> Boolean): ItemStack {
        for (i in 0 until main.size) {
            val stack = this.getStack(i)
            if (predicate(stack.item)) {
                return this.removeStack(i)
            }
        }
        return ItemStack.EMPTY
    }

    fun takeItem(
        predicate: (ItemStack) -> Boolean,
        slot: Int,
    ): ItemStack {
        val stack = this.getStack(slot)
        if (predicate(stack)) {
            return this.removeStack(slot)
        }
        return ItemStack.EMPTY
    }

    fun findItem(predicate: (ItemStack) -> Boolean): Int {
        for (i in 0 until main.size) {
            val stack = this.getStack(i)
            if (predicate(stack)) {
                return i
            }
        }
        return -1
    }

    fun findItem(itemStack: ItemStack): Int {
        for (i in 0 until main.size) {
            val stack = this.getStack(i)
            if (ItemStack.areItemsAndComponentsEqual(itemStack, stack)) {
                return i
            }
        }
        return -1
    }

    override fun getStack(slot: Int): ItemStack {
        if (slot < 0 || slot >= this.main.size) {
            return ItemStack.EMPTY
        }
        return this.main.get(slot)
    }

    override fun setStack(
        slot: Int,
        stack: ItemStack,
    ) {
        this.main.set(slot, stack)
        if (!stack.isEmpty() && stack.getCount() > this.getMaxCountPerStack()) {
            stack.setCount(this.getMaxCountPerStack())
        }
        this.markDirty()
    }

    fun addStack(stack: ItemStack): ItemStack {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY
        }
        val itemStack = stack.copy()
        this.addToExistingSlot(main, itemStack)
        if (itemStack.isEmpty()) {
            return ItemStack.EMPTY
        }
        this.addToNewSlot(main, itemStack)
        if (itemStack.isEmpty()) {
            return ItemStack.EMPTY
        }
        this.markDirty()
        return itemStack
    }

    override fun removeStack(
        slot: Int,
        amount: Int,
    ): ItemStack {
        val itemStack = Inventories.splitStack(this.main, slot, amount)
        if (!itemStack.isEmpty()) {
            this.markDirty()
        }
        return itemStack
    }

    override fun removeStack(id: Int): ItemStack = this.main.set(id, ItemStack.EMPTY)

    override fun size(): Int = this.main.size + this.armor.size + this.held.size

    override fun isEmpty(): Boolean {
        for (field in merged) {
            for (item in field) {
                if (item.isEmpty()) continue
                return false
            }
        }
        return true
    }

    override fun canPlayerUse(player: PlayerEntity): Boolean = false

    override fun clear() {
        for (list in merged) {
            list.clear()
        }
    }

    override fun markDirty() {}

    private fun transfer(
        target: ItemStack,
        source: ItemStack,
    ) {
        val i = Math.min(this.getMaxCountPerStack(), target.getMaxCount())
        val j = Math.min(source.getCount(), i - target.getCount())
        if (j > 0) {
            target.increment(j)
            source.decrement(j)
            this.markDirty()
        }
    }

    private fun addToNewSlot(
        field: DefaultedList<ItemStack>,
        stack: ItemStack,
    ) {
        for (i in 0 until field.size) {
            val itemStack = this.getStack(i)
            if (!itemStack.isEmpty) continue
            this.setStack(i, stack.copyAndEmpty())
        }
    }

    private fun addToExistingSlot(
        field: DefaultedList<ItemStack>,
        stack: ItemStack,
    ) {
        for (k in 0 until field.size) {
            val itemStack = this.getStack(k)
            if (!ItemStack.areItemsAndComponentsEqual(itemStack, stack)) continue
            this.transfer(stack, itemStack)
            if (!stack.isEmpty) continue
            return
        }
    }

    fun writeNbt(): NbtCompound {
        val root = NbtCompound()
        val itemsList = NbtList()

        fun addToList(
            list: DefaultedList<ItemStack>,
            offset: Int,
        ) {
            for (i in list.indices) {
                val stack = list[i]
                if (stack.isEmpty) continue
                val tag = NbtCompound()
                tag.putByte("Slot", (i + offset).toByte())
                itemsList.add(stack.toNbt(entity.registryManager, tag))
            }
        }

        addToList(main, 0)
        addToList(armor, 100)
        addToList(held, 150)

        root.put("Items", itemsList)
        return root
    }

    fun readNbt(nbt: NbtCompound) {
        clear()
        nbt.getList("Items").ifPresent { list ->
            for (i in 0 until list.size) {
                list.getCompound(i).ifPresent { tag ->
                    val slot = tag.getByte("Slot", 0).toInt() and 0xFF
                    val stack = ItemStack.fromNbt(entity.registryManager, tag).orElse(ItemStack.EMPTY)
                    when {
                        slot < main.size -> main[slot] = stack
                        slot in 100 until 100 + armor.size -> armor[slot - 100] = stack
                        slot in 150 until 150 + held.size -> held[slot - 150] = stack
                    }
                }
            }
        }
    }
}
