package com.settlement.mod.entity.mob

import com.google.common.collect.ImmutableList
import com.settlement.mod.LOGGER
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.inventory.Inventories
import net.minecraft.inventory.Inventory
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtList
import net.minecraft.registry.tag.TagKey
import net.minecraft.util.collection.DefaultedList

// TODO:
// - run predicates in added item not entire inventory
// - revisit markDirty method
class VillagerInventory(
    private val entity: AbstractVillagerEntity,
) : Inventory {
    private val held = DefaultedList.ofSize(2, ItemStack.EMPTY)
    private val main = DefaultedList.ofSize(9, ItemStack.EMPTY)
    private val armor = DefaultedList.ofSize(4, ItemStack.EMPTY)
    private val merged: List<DefaultedList<ItemStack>> = ImmutableList.of(this.held, this.main, this.armor)

    fun setArmorField(
        id: Int,
        itemStack: ItemStack,
    ): ItemStack = this.armor.set(id, itemStack)

    fun setHeldField(
        id: Int,
        itemStack: ItemStack,
    ): ItemStack = this.held.set(id, itemStack)

    fun getHeldItems(): DefaultedList<ItemStack> = held

    fun getStoredItems(): DefaultedList<ItemStack> = main

    fun getArmorItems(): DefaultedList<ItemStack> = this.armor

    fun getArmorBySlot(slot: Int): ItemStack = this.armor.get(slot)

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
        predicate: (Item) -> Boolean,
        slot: Int,
    ): ItemStack {
        val stack = this.getStack(slot)
        if (predicate(stack.item)) {
            return this.removeStack(slot)
        }
        return ItemStack.EMPTY
    }

    fun hasItem(predicate: (Item) -> Boolean): Boolean = findItem(predicate) != -1

    fun isHolding(predicate: (Item) -> Boolean) = held.any { stack -> predicate(stack.item) }

    fun findItem(predicate: (Item) -> Boolean): Int {
        for (i in 0 until main.size) {
            val stack = this.getStack(i)
            if (predicate(stack.item)) {
                return i
            }
        }
        return -1
    }

    fun findItem(itemStack: ItemStack): Int {
        for (i in 0 until main.size) {
            val stack = this.getStack(i)
            if (itemStack.item == stack.item) {
                return i
            }
        }
        return -1
    }

    fun contains(stack: ItemStack): Boolean {
        for (field in merged) {
            for (item in field) {
                if (item.isEmpty() || !ItemStack.areItemsAndComponentsEqual(item, stack)) continue
                return true
            }
        }
        return false
    }

    fun contains(tag: TagKey<Item>): Boolean {
        for (field in merged) {
            for (item in field) {
                if (item.isEmpty() || !item.isIn(tag)) continue
                return true
            }
        }
        return false
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

    override fun getStack(slot: Int): ItemStack {
        if (slot < 0 || slot >= this.main.size) {
            return ItemStack.EMPTY
        }
        return this.main.get(slot)
    }

    override fun canPlayerUse(player: PlayerEntity): Boolean = false

    override fun clear() {
        for (list in merged) {
            list.clear()
        }
    }

    override fun markDirty() {}

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

    fun writeNbt(): NbtList {
        val nbtList: NbtList = NbtList()
        for (i in this.main.indices) {
            if (this.main[i].isEmpty) continue
            val nbtCompound = NbtCompound()
            nbtCompound.putByte("Slot", i.toByte())
            nbtList.add(this.main.get(i).toNbt(entity.getRegistryManager(), nbtCompound))
        }

        for (i in this.armor.indices) {
            if (this.armor[i].isEmpty) continue
            val nbtCompound = NbtCompound()
            nbtCompound.putByte("Slot", (i + 100).toByte())
            nbtList.add(this.armor.get(i).toNbt(entity.getRegistryManager(), nbtCompound))
        }

        for (i in this.held.indices) {
            if (this.held[i].isEmpty) continue
            val nbtCompound = NbtCompound()
            nbtCompound.putByte("Slot", (i + 150).toByte())
            nbtList.add(this.held.get(i).toNbt(entity.getRegistryManager(), nbtCompound))
        }
        return nbtList
    }

    fun readNbt(nbtList: NbtList) {
        this.clear()
        for (i in 0 until nbtList.size) {
            val nbtCompound = nbtList.getCompoundOrEmpty(i)
            val j = nbtCompound.getByte("Slot", 0).toInt() and 0xFF
            val itemStack = ItemStack.fromNbt(entity.getRegistryManager(), nbtCompound).orElse(ItemStack.EMPTY)
            when {
                j >= 0 && j < this.main.size -> {
                    this.main[j] = itemStack
                }
                j >= 100 && j < this.armor.size + 100 -> {
                    this.armor[j - 100] = itemStack
                }
                j >= 150 && j < this.held.size + 150 -> {
                    this.held[j - 150] = itemStack
                }
            }
        }
    }
}
