package com.settlement.mod.entity.mob

import com.settlement.mod.LOGGER
import com.settlement.mod.SettlementConfig
import com.settlement.mod.action.Action
import com.settlement.mod.item.ItemPredicate
import com.settlement.mod.screen.ConditionalCloseHandler
import com.settlement.mod.world.SettlementAccessor
import net.minecraft.entity.EntityData
import net.minecraft.entity.EntityDimensions
import net.minecraft.entity.EntityPose
import net.minecraft.entity.EntityType
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.ItemEntity
import net.minecraft.entity.SpawnReason
import net.minecraft.entity.ai.pathing.MobNavigation
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
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtOps
import net.minecraft.registry.tag.FluidTags
import net.minecraft.screen.NamedScreenHandlerFactory
import net.minecraft.screen.ScreenHandler
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.world.LocalDifficulty
import net.minecraft.world.ServerWorldAccess
import net.minecraft.world.World
import java.util.Optional

// TODO:
// - make villagers unleashable
// - create own pathfinder
class AbstractVillagerEntity(
    entityType: EntityType<out AbstractVillagerEntity>,
    world: World,
) : PathAwareEntity(entityType, world),
    NamedScreenHandlerFactory {
    val inventory: VillagerInventory = VillagerInventory(this)
    val controller: EntityController = EntityController(this)

    init {
        this.getNavigation().setCanSwim(true)
        (this.getNavigation() as MobNavigation).setCanPathThroughDoors(true)
    }

    override fun initDataTracker(builder: DataTracker.Builder) {
        super.initDataTracker(builder)
        builder.add(ERRAND_POSITION, Optional.empty())
        builder.add(STATE, 0)
        builder.add(SKIN, 0)
    }
    // TODO: randomize controller stats
    override fun initialize(
        world: ServerWorldAccess,
        difficulty: LocalDifficulty,
        spawnReason: SpawnReason,
        entityData: EntityData?,
    ): EntityData? {
        if (SettlementConfig.skinCount > 0) {
            val skin = (1..SettlementConfig.skinCount).random()
            this.setSkin(skin)
        }

        SettlementAccessor.setProfession(controller)

        return super.initialize(world, difficulty, spawnReason, entityData)
    }

    override fun createMenu(
        syncId: Int,
        playerInventory: PlayerInventory,
        player: PlayerEntity,
    ): ScreenHandler? {
        val factory = controller.profession.getScreenFactory() ?: return null

        return factory(syncId, playerInventory).also { screen ->
            if (screen is ConditionalCloseHandler) {
                screen.setCloseCondition { controller.target !is PlayerEntity }
                controller.callback = { screen.isClosed }
            }
        }
    }

    override fun interactMob(
        player: PlayerEntity,
        hand: Hand,
    ): ActionResult {
        val stack: ItemStack = player.getStackInHand(hand)
        if (!isAlive || controller.containsErrand(Action.Type.SLEEP) || stack.isOf(Items.VILLAGER_SPAWN_EGG)) {
            return super.interactMob(player, hand)
        } else if (!this.world.isClient && !controller.hasErrands(ErrandType.FRAY)) {
            if (player.isSneaking && !controller.containsErrand(Action.Type.FOLLOW)) {
                if (!controller.hasErrands(ErrandType.WORK) && stack.isEmpty) {
                    controller.target = player
                    controller.pushErrand(Action.Type.FOLLOW)
                } else {
                    if (!controller.containsErrand(Action.Type.ANALYZE) && this.canAcceptStack(stack)) {
                        controller.target = player
                        controller.stack = stack.copy()
                        player.setStackInHand(hand, ItemStack.EMPTY)
                        controller.pushErrand(Action.Type.ANALYZE)
                    } else {
                        if (!controller.containsErrand(Action.Type.INTERACT)) {
                            controller.target = player
                            controller.expectedState = State.DISAGREE
                            controller.pushErrand(Action.Type.INTERACT)
                        }
                    }
                }
                return ActionResult.SUCCESS
            } else if (!controller.containsErrand(Action.Type.LOCK) && controller.profession.getScreenFactory() != null) {
                controller.target = (player)
                controller.pushErrand(Action.Type.LOCK)
                return ActionResult.SUCCESS
            } else {
                if (!controller.containsErrand(Action.Type.INTERACT)) {
                    controller.target = player
                    controller.expectedState = State.DISAGREE
                    controller.pushErrand(Action.Type.INTERACT)
                }
                return ActionResult.PASS
            }
        } else {
            return ActionResult.SUCCESS
        }
    }

    override fun getBaseDimensions(pose: EntityPose): EntityDimensions =
        when (pose) {
            EntityPose.SLEEPING -> SLEEPING_DIMENSIONS
            EntityPose.SITTING -> SITTING_DIMENSIONS
            else -> STANDING_DIMENSIONS
        }

    override fun tick() {
        if (!world.isClient && isAlive) {
            controller.updateTargets()
            controller.tickErrands()
        }
        super.tick()
    }

    override fun tickMovement() {
        this.tickHandSwing()
        super.tickMovement()
    }

    override fun remove(reason: RemovalReason) {
        // CHECK if settlement data was trully removed
        super.remove(reason)
    }

    override fun damage(
        world: ServerWorld,
        source: DamageSource,
        amount: Float,
    ): Boolean = super.damage(world, source, amount)

    override fun damageArmor(
        damageSource: DamageSource,
        amount: Float,
    ) {
        val slots = arrayOf(EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD)
        if (amount <= 0.0f) return
        val damage: Int = if ((amount / 4.0f) < 1.0f) 1 else (amount / 4.0f).toInt()
        slots.forEachIndexed { i, slot ->
            val stack = inventory.getArmor(i)
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
        if (!world.isClient) {
            SettlementAccessor.leaveSettlement(controller)
        }
        super.onDeath(damageSource)
    }

    fun getErrandPosition(): Optional<BlockPos> = dataTracker.get(ERRAND_POSITION)

    fun setErrandPosition(pos: BlockPos) {
        dataTracker.set(ERRAND_POSITION, Optional.of(pos))
    }

    fun clearErrandPosition() {
        dataTracker.set(ERRAND_POSITION, Optional.empty())
    }

    // TODO: create scheduler
    fun canSleep(): Boolean = world.getTimeOfDay() % 24000.0f / 1000 > 12

    // TODO: make animation trigger only if fully submerged
    override fun updateSwimming() {
        if (!world.isClient) {
            val height = this.getSwimHeight()
            if (this.isTouchingWater() && this.getFluidHeight(FluidTags.WATER) > height) {
                this.setSwimming(true)
            } else {
                this.setSwimming(false)
            }
        }
    }

    override fun isInSwimmingPose(): Boolean = this.isSwimming()

    override fun getSwimHeight(): Double = 0.4

    override fun canPickUpLoot(): Boolean = false

    fun canAcceptStack(stack: ItemStack): Boolean = inventory.canInsert(stack) && controller.profession.tags.any { stack.isIn(it) }

    fun pickItem(entity: ItemEntity) {
        val stack = entity.stack
        val originalCount = stack.count
        val remainingStack = inventory.addStack(stack)
        this.sendPickup(entity, originalCount - remainingStack.count)

        if (remainingStack.isEmpty) {
            entity.discard()
        } else {
            stack.setCount(remainingStack.getCount())
        }

        val i = inventory.findItem(stack)
        if (i != LACKING) {
            handleLookup(stack, i)
        }
    }

    fun pickStack(stack: ItemStack) {
        val itemToFind = stack.copy()
        val remainingStack = inventory.addStack(stack)
        val i = inventory.findItem(itemToFind)

        if (i != LACKING) {
            handleLookup(stack, i)
        }
        dropStack(world as ServerWorld, remainingStack)
    }

    fun handleLookup(
        stack: ItemStack,
        index: Int,
    ) {
        val action = ItemPredicate.getActionFromStack(stack) ?: return
        val existingLookup = controller.items[action]

        if (existingLookup == null || existingLookup == UNVERIFIED) {
            LOGGER.info("Assigning item at index {} to action {}", index, action)
            controller.items[action] = index
            if (action == Action.Type.WEAR) {
                controller.pushErrand(Action.Type.WEAR)
            }
        } else {
            val old =
                when (existingLookup) {
                    EQUIPPED -> {
                        val slot = this.getPreferredEquipmentSlot(stack)
                        if (slot != EquipmentSlot.MAINHAND) {
                            this.getEquippedStack(slot)
                        } else {
                            val set = setOf(Action.Type.PLANT, Action.Type.POWDER, Action.Type.EAT, Action.Type.DRINK, Action.Type.DEFEND)
                            if (action in set) controller.stackInOffHand else controller.stackInMainHand
                        }
                    }
                    else -> inventory.getStack(existingLookup)
                }
            // TODO: implement logic for beter equipment checking 
            val prefersNew = true
            if (prefersNew) {
                LOGGER.info("New item for action {} is better. Swapping. = {}", action, old)
                controller.items[Action.Type.STORE] = existingLookup
                controller.items[action] = index
                if (action == Action.Type.WEAR) {
                    controller.pushErrand(Action.Type.WEAR)
                }
            } else {
                controller.items[Action.Type.STORE] = index
            }
        }
    }

    override fun getEquippedStack(slot: EquipmentSlot): ItemStack =
        when (slot.type) {
            EquipmentSlot.Type.HAND -> inventory.getHeld(slot.entitySlotId)
            EquipmentSlot.Type.HUMANOID_ARMOR -> inventory.getArmor(slot.entitySlotId)
            else -> ItemStack.EMPTY
        }

    override fun equipStack(
        slot: EquipmentSlot,
        stack: ItemStack,
    ) {
        when (slot.type) {
            EquipmentSlot.Type.HAND -> {
                this.onEquipStack(slot, inventory.setHeld(slot.entitySlotId, stack), stack)
            }
            EquipmentSlot.Type.HUMANOID_ARMOR -> {
                this.onEquipStack(slot, inventory.setArmor(slot.entitySlotId, stack), stack)
            }
            else -> ItemStack.EMPTY
        }
    }

    fun tryItemLookup(
        type: Action.Type,
        predicate: (ItemStack) -> Boolean,
        slot: EquipmentSlot,
    ): Boolean =
        controller.items[type]?.let { i ->
            tryEquip(type, predicate, slot, i)
        } ?: run {
            controller.items[type] = UNVERIFIED
            false
        }

    private fun tryEquip(
        type: Action.Type,
        predicate: (ItemStack) -> Boolean,
        slot: EquipmentSlot,
        index: Int,
    ): Boolean {
        if (index == LACKING) return false
        if (index == EQUIPPED) return true

        if (index == UNVERIFIED) {
            val foundIndex = inventory.findItem(predicate)
            if (foundIndex < 0) {
                controller.items[type] = LACKING
                return false
            }
            return tryEquip(type, predicate, slot, foundIndex)
        }

        val equipped =
            when (slot) {
                EquipmentSlot.MAINHAND -> getStackInHand(Hand.MAIN_HAND)
                EquipmentSlot.OFFHAND -> getStackInHand(Hand.OFF_HAND)
                else -> return false
            }

        val equippedStack = equipped
        if (!equipped.isEmpty && predicate(equippedStack)) {
            controller.items[type] = EQUIPPED
            return true
        }

        if (index >= 0) {
            val new = inventory.takeItem(predicate, index)
            if (new.isEmpty) {
                controller.items[type] = LACKING
                return false
            }
            val old = equipped.copy()
            if (!old.isEmpty) {
                ItemPredicate.getActionFromStack(old)?.let {
                    controller.items[it] = index
                }
            }
            equipStack(slot, new)
            inventory.setStack(index, old)
            controller.items[type] = EQUIPPED
            return true
        }

        return false
    }

    // TODO: destroy a fraction of items in entity inventory
    override fun dropInventory(world: ServerWorld) {
        inventory.getItems().forEach { item ->
            dropStack(world, item)
        }
        inventory.clear()
    }

    fun getState(): Int = dataTracker.get(STATE)

    fun setState(state: Int) {
        dataTracker.set(STATE, state)
    }

    fun setSkin(id: Int) {
        dataTracker.set(SKIN, id)
    }

    fun getSkin(): Int = dataTracker.get(SKIN)

    override fun readCustomDataFromNbt(nbt: NbtCompound) {
        super.readCustomDataFromNbt(nbt)

        if (nbt.contains("Skin")) {
            this.setSkin(nbt.getInt("Skin").get())
        }

        if (nbt.contains(ERRAND_X) &&
            nbt.contains(ERRAND_Y) &&
            nbt.contains(ERRAND_Z)
        ) {
            val pos = BlockPos(nbt.getInt(ERRAND_X).get(), nbt.getInt(ERRAND_Y).get(), nbt.getInt(ERRAND_Z).get())
            this.setErrandPosition(pos)
        }

        if (nbt.contains(INVENTORY)) {
            nbt.getCompound(INVENTORY)?.ifPresent {
                inventory.readNbt(it)
            }
        }

        if (nbt.contains(CONTROLLER)) {
            val tag = nbt.getCompound(CONTROLLER)
            val codec = EntityController.readNbt(this)
            codec
                .decode(NbtOps.INSTANCE, tag.get())
                .resultOrPartial { err ->
                    LOGGER.error("Decode controller failed: $err")
                }.ifPresent { loaded ->
                    this.controller.loadNbt(loaded.first)
                }
        }
    }

    override fun writeCustomDataToNbt(nbt: NbtCompound) {
        super.writeCustomDataToNbt(nbt)

        nbt.putInt("Skin", this.getSkin())

        this.getErrandPosition().ifPresent { pos ->
            nbt.putInt(ERRAND_X, pos.getX())
            nbt.putInt(ERRAND_Y, pos.getY())
            nbt.putInt(ERRAND_Z, pos.getZ())
        }

        nbt.put(INVENTORY, inventory.writeNbt())

        EntityController
            .readNbt(this)
            .encodeStart(NbtOps.INSTANCE, controller)
            .resultOrPartial { err -> LOGGER.error("Encode controller failed: $err") }
            ?.ifPresent { tag ->
                nbt.put(CONTROLLER, tag)
            }
    }

    fun getDebugData(): List<String>? = controller.debugData()

    companion object {
        const val CONTROLLER = "Controller"
        const val INVENTORY = "Inventory"
        const val ERRAND_X = "ErrandX"
        const val ERRAND_Y = "ErrandY"
        const val ERRAND_Z = "ErrandZ"

        const val LACKING = -1
        const val EQUIPPED = -2
        const val UNVERIFIED = -3

        private val SKIN: TrackedData<Int> =
            DataTracker.registerData(
                AbstractVillagerEntity::class.java,
                TrackedDataHandlerRegistry.INTEGER,
            )

        val STATE: TrackedData<Int> =
            DataTracker.registerData(
                AbstractVillagerEntity::class.java,
                TrackedDataHandlerRegistry.INTEGER,
            )

        val ERRAND_POSITION: TrackedData<Optional<BlockPos>> =
            DataTracker.registerData(
                AbstractVillagerEntity::class.java,
                TrackedDataHandlerRegistry.OPTIONAL_BLOCK_POS,
            )

        val SITTING_DIMENSIONS: EntityDimensions = EntityDimensions.fixed(0.6f, 1.35f)
        val STANDING_DIMENSIONS: EntityDimensions = EntityDimensions.fixed(0.6f, 1.8f)

        fun createAttributes(): DefaultAttributeContainer.Builder =
            MobEntity
                .createMobAttributes()
                .add(EntityAttributes.MAX_HEALTH, 25.0)
                .add(EntityAttributes.ATTACK_DAMAGE, 0.5)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.25)
                .add(EntityAttributes.FOLLOW_RANGE, 32.0)
    }
}
