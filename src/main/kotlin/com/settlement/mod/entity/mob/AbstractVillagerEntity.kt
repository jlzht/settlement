package com.settlement.mod.entity.mob

import com.settlement.mod.LOGGER
import com.settlement.mod.action.Action
import com.settlement.mod.entity.ai.goal.ActGoal
import com.settlement.mod.entity.ai.goal.ReactGoal
import com.settlement.mod.entity.ai.pathing.VillagerNavigation
import com.settlement.mod.item.ItemPredicate
import com.settlement.mod.profession.Profession
import com.settlement.mod.profession.ProfessionType
import com.settlement.mod.screen.TradingScreenHandler
import com.settlement.mod.world.SettlementAccessor
import net.minecraft.entity.EntityData
import net.minecraft.entity.EntityDimensions
import net.minecraft.entity.EntityPose
import net.minecraft.entity.EntityType
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.ItemEntity
import net.minecraft.entity.SpawnReason
import net.minecraft.entity.ai.pathing.EntityNavigation
import net.minecraft.entity.ai.pathing.MobNavigation
import net.minecraft.entity.ai.pathing.Path
import net.minecraft.entity.attribute.DefaultAttributeContainer
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.data.DataTracker
import net.minecraft.entity.data.TrackedData
import net.minecraft.entity.data.TrackedDataHandlerRegistry
import net.minecraft.entity.mob.MobEntity
import net.minecraft.entity.mob.PathAwareEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.Inventory
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.item.consume.UseAction
import net.minecraft.nbt.NbtCompound
import net.minecraft.registry.tag.FluidTags
import net.minecraft.screen.NamedScreenHandlerFactory
import net.minecraft.screen.ScreenHandler
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.LocalDifficulty
import net.minecraft.world.ServerWorldAccess
import net.minecraft.world.World
import java.util.Optional
import java.util.Random

// will be responsible for fuzzy states
class JitterPulse(
    val baseHigh: Int,
    val baseLow: Int,
    val jitterHigh: Int = 1,
    val jitterLow: Int = 2,
    val random: Random = Random(),
) {
    private var remaining = baseHigh + random.nextInt(-jitterHigh, jitterHigh + 1)
    private var state = true

    fun next(): Boolean {
        val result = state
        remaining--

        if (remaining <= 0) {
            state = !state
            remaining =
                if (state) {
                    baseHigh + random.nextInt(-jitterHigh, jitterHigh + 1)
                } else {
                    baseLow + random.nextInt(-jitterLow, jitterLow + 1)
                }
        }

        return result
    }
}

// TODO:
// - add bit masking for reaching O(1) errand lookup, instead of set looking O(n)
// - improve data tracking state for animation handling
// - make villagers unleashable
class AbstractVillagerEntity(
    entityType: EntityType<out AbstractVillagerEntity>,
    world: World?,
) : PathAwareEntity(entityType, world),
    NamedScreenHandlerFactory {
    val inventory: VillagerInventory = VillagerInventory(this)
    lateinit var profession: Profession
    var errandManager = ErrandManager()
    var path: Path? = null

    val pulse = JitterPulse(40, 20)

    // -------
    val item = mutableMapOf<Action.Type, Int>()
    var tickingValue: Int = -1
    var pendingValue: Double = -1.0

    init {
        this.getNavigation().setCanSwim(true)
        (this.getNavigation() as MobNavigation).setCanPathThroughDoors(true)
    }

    override fun initDataTracker(builder: DataTracker.Builder) {
        super.initDataTracker(builder)
        builder.add(SITTING_POSITION, Optional.empty())
        builder.add(STATE, 0)
    }

    override fun initialize(
        world: ServerWorldAccess,
        difficulty: LocalDifficulty,
        spawnReason: SpawnReason,
        entityData: EntityData?,
    ): EntityData? {
        SettlementAccessor.setProfession(this)

        return super.initialize(world, difficulty, spawnReason, entityData)
    }

    override fun initGoals() {
        goalSelector.add(0, ReactGoal(this))
        goalSelector.add(0, ActGoal(this))
    }

    override fun createNavigation(world: World): EntityNavigation = VillagerNavigation(this, world)

    fun pushErrand(
        cid: Action.Type,
        pos: BlockPos? = null,
    ): Boolean {
        val action = Action.get(cid)
        val priority = action.scan(this, pos)
        if (priority < 0) return false
        errandManager.pushErrand(action.type, cid, pos, priority, action.isUnique)
        return true
    }

    private var working: Boolean = false

    fun isWorking(): Boolean = this.working

    fun setWorking(working: Boolean) {
        this.working = working
    }

    // this is used for combat profession for distinguish neutral mobs from hostiles
    private var fighting: Boolean = false

    fun isFighting(): Boolean = fighting

    fun setFighting(fighting: Boolean) {
        this.fighting = fighting
    }

    private var picking = false

    fun setPicking(picking: Boolean) {
        this.picking = picking
    }

    fun isPicking(): Boolean = this.picking

    fun canSleep(): Boolean = this.world.getTimeOfDay() % 24000.0f / 1000 > 12

    fun getSittingPosition(): Optional<BlockPos> = this.dataTracker.get(SITTING_POSITION)

    fun setSittingPosition(pos: BlockPos) {
        this.dataTracker.set(SITTING_POSITION, Optional.of(pos))
    }

    fun clearSittingPosition() {
        this.dataTracker.set(SITTING_POSITION, Optional.empty())
    }

    fun isSitting() = this.getSittingPosition().isPresent()

    fun getUp() {
        this.getSittingPosition().filter { p -> this.getWorld().isChunkLoaded(p) }.ifPresent { pos ->
            val v = Vec3d(pos.getX().toDouble(), pos.getY() + 0.3, pos.getZ().toDouble())
            this.setPosition(v.x, v.y, v.z)
        }
        val vec3d = this.getPos()
        this.setPose(EntityPose.STANDING)
        this.setPosition(vec3d.x, vec3d.y, vec3d.z)
        this.clearSittingPosition()
    }

    fun sit(pos: BlockPos?) {
        this.setPose(EntityPose.SITTING)
        this.setVelocity(Vec3d.ZERO)
        pos?.let { p ->
            val target = p.toCenterPos()
            this.setSittingPosition(p)
            this.setPosition(target.getX(), target.getY() - 0.4, target.getZ())
        } ?: run {
            val target = blockPos.toCenterPos()
            this.setSittingPosition(blockPos)
            this.setPosition(target.getX(), target.getY() - 0.4, target.getZ())
        }
    }

    fun isEating(): Boolean {
        val stack = this.getActiveItem()
        return stack.getUseAction() == UseAction.EAT
    }

    // TODO: make animation trigger only if fully submerged
    override fun updateSwimming() {
        if (!this.getWorld().isClient) {
            val height = this.getSwimHeight()
            if (this.isTouchingWater() && this.getFluidHeight(FluidTags.WATER) > height) {
                this.setSwimming(true)
            } else {
                this.setSwimming(false)
            }
        }
    }
    // triggers swim animation
    // override fun isInSwimmingPose(): Boolean = this.isSwimming()

    override fun getSwimHeight(): Double = 0.4

    private fun getArmorItem(id: Int): ItemStack = this.inventory.getArmorBySlot(id)

    private fun getHandItem(id: Int): ItemStack = this.inventory.getHeldItems()[id]

    private fun setArmorItem(
        id: Int,
        stack: ItemStack,
    ): ItemStack = this.inventory.setArmorField(id, stack)

    private fun setHandItem(
        id: Int,
        stack: ItemStack,
    ): ItemStack = this.inventory.setHeldField(id, stack)

    override fun getEquippedStack(slot: EquipmentSlot): ItemStack =
        when (slot.type) {
            EquipmentSlot.Type.HAND -> this.getHandItem(slot.entitySlotId)
            EquipmentSlot.Type.HUMANOID_ARMOR -> this.getArmorItem(slot.entitySlotId)
            else -> ItemStack.EMPTY
        }

    override fun equipStack(
        slot: EquipmentSlot,
        stack: ItemStack,
    ) {
        when (slot.type) {
            EquipmentSlot.Type.HAND -> {
                this.onEquipStack(slot, this.setHandItem(slot.entitySlotId, stack), stack)
            }
            EquipmentSlot.Type.HUMANOID_ARMOR -> {
                this.onEquipStack(slot, this.setArmorItem(slot.entitySlotId, stack), stack)
            }
            else -> ItemStack.EMPTY
        }
    }

    private fun isProfessionInitialized() = ::profession.isInitialized

    fun setProfession(type: ProfessionType) {
        this.profession = Profession.get(type)
    }

    override fun createMenu(
        syncId: Int,
        playerInventory: PlayerInventory,
        player: PlayerEntity,
    ): ScreenHandler? = TradingScreenHandler(syncId, playerInventory)

    override fun interactMob(
        player: PlayerEntity,
        hand: Hand,
    ): ActionResult {
        if (!this.world.isClient) {
            val stack: ItemStack = player.getStackInHand(hand)
            if (!stack.isOf(Items.VILLAGER_SPAWN_EGG) && isAlive && !this.isSleeping()) {
                if (this.profession.CLICK_HANDLER(this, player)) {
                    return ActionResult.SUCCESS
                } else {
                    return ActionResult.FAIL
                }
            }
        }
        return super.interactMob(player, hand)
    }

    override fun tick() {
        super.tick()
    }

    override fun tickMovement() {
        this.tickHandSwing()
        super.tickMovement()
    }

    override fun canImmediatelyDespawn(distanceSquared: Double): Boolean = false

    override fun remove(reason: RemovalReason) {
        super.remove(reason)
    }

    override fun damage(
        world: ServerWorld,
        source: DamageSource,
        amount: Float,
    ): Boolean {
        val bl = super.damage(world, source, amount)
        if (bl) {
            if (!this.getWorld().isClient && this.isSitting()) {
                this.getUp()
            }
        }
        return bl
    }

    override fun damageArmor(
        damageSource: DamageSource,
        amount: Float,
    ) {
        val slots = arrayOf(EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD)
        if (amount <= 0.0f) return
        val damage: Int = if ((amount / 4.0f) < 1.0f) 1 else (amount / 4.0f).toInt()
        slots.forEachIndexed { i, slot ->
            val stack = this.getArmorItem(i)
            if (stack.takesDamageFrom(damageSource)) {
                stack.damage(
                    damage,
                    this,
                    slot,
                )
            }
        }
    }

    override fun onDeath(damageSource: DamageSource) {
        LOGGER.info("Villager {} died, message: {}", this as Any, damageSource.getDeathMessage(this).string)
        if (!this.getWorld().isClient) {
            SettlementAccessor.leaveSettlement(this)
        }
        super.onDeath(damageSource)
    }

    override fun canPickUpLoot(): Boolean = picking

    override fun canGather(
        world: ServerWorld,
        stack: ItemStack,
    ): Boolean = this.profession.desiredItems.any { p -> p(stack.item) } && this.inventory.canInsert(stack)

    // TODO: rework armor wearing logic
    override fun loot(
        world: ServerWorld,
        entity: ItemEntity,
    ) {
        val stack = entity.stack
        if (!this.world.isClient && this.canGather(this.world as ServerWorld, stack) && this.inventory.canInsert(stack)) {
            val originalCount = stack.count
            val remainingStack = this.inventory.addStack(stack)
            this.sendPickup(entity, originalCount - remainingStack.count)
            if (remainingStack.isEmpty) {
                entity.discard()
            } else {
                stack.setCount(remainingStack.getCount())
            }

            val i = this.inventory.findItem(stack)
            if (i != -1) {
                ItemPredicate.getActionFromItem(stack.item)?.let {
                    this.item[it] = i
                }
            }

            if (ItemPredicate.ARMOR(stack.item)) {
                val equipmentSlot = this.getPreferredEquipmentSlot(stack)
                val oldStack = this.getEquippedStack(equipmentSlot)
                if (prefersNewEquipment(stack, oldStack, equipmentSlot)) {
                    if (!stack.isEmpty) {
                        this.equipStack(equipmentSlot, stack)
                        // quick fix for armor duplication
                        val index = inventory.findItem(stack)
                        if (index != -1) inventory.setStack(index, ItemStack.EMPTY)
                    }
                } else {
                    this.tryInsert(stack)
                }
            }
        }
    }

    fun shouldStoreItems(): Boolean {
        val desiredItemCounts = mutableMapOf<((Item) -> Boolean), Int>()

        profession.desiredItems.forEach { predicate ->
            desiredItemCounts[predicate] = 0
        }

        var totalExcessItems = 0

        for (i in 0 until inventory.size()) {
            val stack = inventory.getStack(i)
            if (stack.isEmpty) continue

            LOGGER.debug("  Slot {}: {} (Count: {})", i, stack.item.name.string, stack.count)

            var matchedDesiredPredicate = false
            for (desiredPredicate in profession.desiredItems) {
                if (desiredPredicate.invoke(stack.item)) {
                    val currentCount = desiredItemCounts.getOrDefault(desiredPredicate, 0)
                    desiredItemCounts[desiredPredicate] = currentCount + stack.count
                    LOGGER.debug(
                        "    -> Matched desired item predicate. Accumulated count for this type: {}",
                        desiredItemCounts[desiredPredicate],
                    )
                    matchedDesiredPredicate = true
                    break
                }
            }

            if (!matchedDesiredPredicate) {
                totalExcessItems += stack.count
                LOGGER.debug("    -> Not a desired item. Adding {} to total excess. Current excess: {}", stack.count, totalExcessItems)
            }
        }
        LOGGER.debug("--- End Inventory Scan ---")

        var allDesiredTypesFoundAtLeastOne = true
        LOGGER.debug("--- Desired Item Fulfillment Check for ${this.name.string} ---")
        for ((predicate, count) in desiredItemCounts) {
            // Note: Logging a lambda (the predicate itself) might not give a readable string.
            // You might need a way to map predicates to a more descriptive name if you log them frequently.
            // For now, we'll just log the count.
            LOGGER.debug("  Desired Predicate Check: Count = {}", count)

            if (count == 0) {
                allDesiredTypesFoundAtLeastOne = false
                LOGGER.debug("  -> MISSING desired item type. Should NOT store yet (condition 1 failed).")
                break
            } else if (count > 1) {
                totalExcessItems += (count - 1)
                LOGGER.debug("  -> Found {} excess of a desired type. Total excess updated to: {}", count - 1, totalExcessItems)
            }
        }
        LOGGER.debug("--- End Desired Item Fulfillment Check ---")

        LOGGER.debug("Final Check for ${this.name.string}:")
        LOGGER.debug("  All desired types found at least one: {}", allDesiredTypesFoundAtLeastOne)
        LOGGER.debug("  Total excess items detected: {}", totalExcessItems)

        val shouldStore = allDesiredTypesFoundAtLeastOne && totalExcessItems > 0
        LOGGER.info("Villager ${this.name.string} should store items: {}", shouldStore)

        return shouldStore
    }

    fun getExcessItems(): List<ItemStack> {
        val excessItems = mutableListOf<ItemStack>()
        val fulfilledDesiredTypes = mutableSetOf<((Item) -> Boolean)>()

        for (i in 0 until inventory.size()) {
            val stackInSlot = inventory.getStack(i)
            if (stackInSlot.isEmpty) continue

            var isDesiredType = false
            for (desiredPredicate in profession.desiredItems) {
                if (desiredPredicate.invoke(stackInSlot.item)) {
                    isDesiredType = true

                    if (!fulfilledDesiredTypes.add(desiredPredicate)) {
                        excessItems.add(inventory.removeStack(i))
                    }
                    break
                }
            }

            if (!isDesiredType) {
                excessItems.add(inventory.removeStack(i))
            }
        }
        return excessItems
    }

    fun insertStackIntoInventory(
        targetInventory: Inventory,
        stack: ItemStack,
    ): ItemStack {
        var remainingStack = stack.copy() // Work with a copy to not modify the original input stack directly immediately

        // First pass: try to stack with existing items
        for (i in 0 until targetInventory.size()) {
            val existingStack = targetInventory.getStack(i)
            if (existingStack.item == remainingStack.item &&
                existingStack.isStackable &&
                existingStack.count < existingStack.maxCount &&
                ItemStack.areEqual(existingStack, remainingStack)
            ) {
                val transferAmount = minOf(remainingStack.count, existingStack.maxCount - existingStack.count)
                existingStack.increment(transferAmount) // Add to existing stack
                remainingStack.decrement(transferAmount) // Reduce remaining
                if (remainingStack.isEmpty) return ItemStack.EMPTY // All inserted
            }
        }

        // Second pass: fill empty slots
        for (i in 0 until targetInventory.size()) {
            if (targetInventory.getStack(i).isEmpty) {
                val transferAmount = minOf(remainingStack.count, remainingStack.maxCount) // Max one full stack per slot
                val stackToPlace = remainingStack.split(transferAmount) // Create a new stack for the slot
                targetInventory.setStack(i, stackToPlace)
                if (remainingStack.isEmpty) return ItemStack.EMPTY // All inserted
            }
        }

        return remainingStack
    }

    private fun tryInsert(stack: ItemStack) {
        if (this.inventory.canInsert(stack)) {
            this.inventory.addStack(stack)
        } else if (!this.world.isClient) {
            this.dropStack(this.world as ServerWorld, stack)
        }
    }

    fun tryEquip(
        predicate: (Item) -> Boolean,
        slot: EquipmentSlot,
        index: Int,
    ): Boolean {
        if (index == -1) return false

        val equipped =
            when (slot) {
                EquipmentSlot.MAINHAND -> getStackInHand(Hand.MAIN_HAND)
                EquipmentSlot.OFFHAND -> getStackInHand(Hand.OFF_HAND)
                else -> return false
            }

        val equippedItem = equipped.item
        if (!equipped.isEmpty && predicate(equippedItem)) {
            item[ ItemPredicate.getActionFromItem(equippedItem) ?: return true ] = -2
            return true
        }

        if (index == -3) {
            val foundIndex = inventory.findItem(predicate)
            if (foundIndex == -1) {
                item[ ItemPredicate.predicateToActionMap[predicate] ?: return false ] = -1
                return false
            }
            return tryEquip(predicate, slot, foundIndex)
        }

        if (index >= 0) {
            val new = inventory.takeItem(predicate, index)
            if (new.isEmpty) return false

            val old = equipped.copy()
            ItemPredicate.getActionFromItem(old.item)?.let { item[it] = index }
            ItemPredicate.getActionFromItem(new.item)?.let { item[it] = -2 }

            inventory.setStack(index, old)
            equipStack(slot, new)
            return true
        }

        return false
    }

    // TODO: destroy a faction of items in entity inventory
    override fun dropInventory(world: ServerWorld) {
        this.inventory.getItems().forEach { item ->
            this.dropStack(world, item)
        }
        this.inventory.clear()
    }

    override fun getBaseDimensions(pose: EntityPose): EntityDimensions =
        when (pose) {
            EntityPose.SLEEPING -> SLEEPING_DIMENSIONS
            EntityPose.SITTING -> SITTING_DIMENSIONS
            else -> STANDING_DIMENSIONS
        }

    fun getState(): AbstractVillagerEntity.Companion.State = AbstractVillagerEntity.Companion.State.values()[this.dataTracker.get(STATE)]

    fun setState(state: AbstractVillagerEntity.Companion.State) {
        this.dataTracker.set(STATE, state.ordinal)
    }

    override fun readCustomDataFromNbt(nbt: NbtCompound) {
        super.readCustomDataFromNbt(nbt)

        if (nbt.contains("SittingX") &&
            nbt.contains("SittingY") &&
            nbt.contains("SittingZ")
        ) {
            val pos = BlockPos(nbt.getInt("SittingX").get(), nbt.getInt("SittingY").get(), nbt.getInt("SittingZ").get())
            this.sit(pos)
        }

        if (nbt.contains(INVENTORY_KEY)) {
            val z = nbt.getList(INVENTORY_KEY)
            if (z.isPresent) {
                this.inventory.readNbt(nbt.getList(INVENTORY_KEY).get())
            }
        }
        // make it immutable
        if (nbt.contains(ERRAND_MANAGER)) {
            val manager = nbt.get(ERRAND_MANAGER, ErrandManager.CODEC)
            if (manager.isPresent) {
                errandManager = manager.get()
            }
        }

        if (!isProfessionInitialized()) {
            if (nbt.getInt(VILLAGER_PROFESSION).isPresent) {
                this.setProfession(ProfessionType.values()[nbt.getInt(VILLAGER_PROFESSION).get()])
            } else {
                this.setProfession(ProfessionType.values()[0])
            }
        }
    }

    override fun writeCustomDataToNbt(nbt: NbtCompound) {
        super.writeCustomDataToNbt(nbt)
        this.getSittingPosition().ifPresent { pos ->
            nbt.putInt("SittingX", pos.getX())
            nbt.putInt("SittingY", pos.getY())
            nbt.putInt("SittingZ", pos.getZ())
        }

        nbt.put(INVENTORY_KEY, inventory.writeNbt())
        nbt.put(ERRAND_MANAGER, ErrandManager.CODEC, this.errandManager)
        nbt.putInt(VILLAGER_PROFESSION, profession.type.ordinal)
    }

    fun getDebugData(): List<String>? =
        this.errandManager.getDebugData() + if (isProfessionInitialized()) this.profession.type.name else "NONE"

    companion object {
        const val INVENTORY_KEY = "Inventory"
        const val VILLAGER_PROFESSION = "VillagerProfession"
        const val ERRAND_MANAGER = "ErrandManager"

        enum class State {
            NONE,
            DISAGREE,
            AGREE,
            TALK,
            OFFER,
            SWEAT,
            GREET,
        }

        val STATE: TrackedData<Int> =
            DataTracker.registerData(
                AbstractVillagerEntity::class.java,
                TrackedDataHandlerRegistry.INTEGER,
            )

        val SITTING_POSITION: TrackedData<Optional<BlockPos>> =
            DataTracker.registerData(
                AbstractVillagerEntity::class.java,
                TrackedDataHandlerRegistry.OPTIONAL_BLOCK_POS,
            )

        val SITTING_DIMENSIONS: EntityDimensions = EntityDimensions.fixed(0.6f, 1.35f)
        val STANDING_DIMENSIONS: EntityDimensions = EntityDimensions.fixed(0.6f, 1.95f)

        fun createAttributes(): DefaultAttributeContainer.Builder =
            MobEntity
                .createMobAttributes()
                .add(EntityAttributes.MAX_HEALTH, 20.0)
                .add(EntityAttributes.ATTACK_DAMAGE, 0.5)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.25)
                .add(EntityAttributes.FOLLOW_RANGE, 32.0)
    }
}
