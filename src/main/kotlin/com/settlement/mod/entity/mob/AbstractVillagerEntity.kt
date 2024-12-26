package com.settlement.mod.entity.mob

import com.settlement.mod.LOGGER
import com.settlement.mod.action.Action
import com.settlement.mod.action.ActionState
import com.settlement.mod.action.Position
import com.settlement.mod.entity.ai.goal.ActGoal
import com.settlement.mod.entity.ai.goal.PercieveGoal
import com.settlement.mod.entity.ai.goal.PlanGoal
import com.settlement.mod.entity.ai.goal.ReactGoal
import com.settlement.mod.entity.ai.pathing.VillagerNavigation
import com.settlement.mod.item.ItemPredicate
import com.settlement.mod.profession.Profession
import com.settlement.mod.profession.ProfessionType
import com.settlement.mod.screen.TradingScreenHandler
import com.settlement.mod.world.SettlementAccessor
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityData
import net.minecraft.entity.EntityDimensions
import net.minecraft.entity.EntityPose
import net.minecraft.entity.EntityType
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.ItemEntity
import net.minecraft.entity.SpawnReason
import net.minecraft.entity.ai.pathing.EntityNavigation
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
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtElement
import net.minecraft.network.PacketByteBuf
import net.minecraft.registry.tag.DamageTypeTags
import net.minecraft.registry.tag.FluidTags
import net.minecraft.screen.ScreenHandler
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.UseAction
import net.minecraft.util.math.BlockPos
import net.minecraft.world.LocalDifficulty
import net.minecraft.world.ServerWorldAccess
import net.minecraft.world.World

class AbstractVillagerEntity(
    entityType: EntityType<out AbstractVillagerEntity>,
    world: World?,
) : PathAwareEntity(entityType, world),
    ExtendedScreenHandlerFactory {
    val inventory: VillagerInventory = VillagerInventory()
    lateinit var profession: Profession
    lateinit var errandProvider: ErrandProvider // put Provider inside manager
    lateinit var errandManager: ErrandManager

    init {
        this.getNavigation().setCanSwim(false)
        (this.getNavigation() as MobNavigation).setCanPathThroughDoors(true)
    }

    override fun initDataTracker() {
        this.dataTracker.startTracking(STATE, 0)
        super.initDataTracker()
    }

    fun isProfessionInitialized(): Boolean {
        return ::profession.isInitialized
    }

    fun isErrandManagerInitialized(): Boolean {
        return ::errandManager.isInitialized
    }
    
    fun isErrandProviderInitialized(): Boolean {
        return ::errandProvider.isInitialized
    }

    override fun initialize(
        world: ServerWorldAccess,
        difficulty: LocalDifficulty,
        spawnReason: SpawnReason,
        entityData: EntityData?,
        entityNbt: NbtCompound?,
    ): EntityData? {

        SettlementAccessor.setProfession(this)
        errandManager = ErrandManager()
        errandProvider = ErrandProvider()

        return super.initialize(world, difficulty, spawnReason, entityData, entityNbt)
    }

    override fun initGoals() {
        targetSelector.add(0, PercieveGoal(this))
        goalSelector.add(0, ReactGoal(this))
        goalSelector.add(0, PlanGoal(this))
        goalSelector.add(0, ActGoal(this))
    }

    override fun getDimensions(pose: EntityPose): EntityDimensions =
        when (pose) {
            EntityPose.SLEEPING -> SLEEPING_DIMENSIONS
            EntityPose.SITTING -> SITTING_DIMENSIONS
            else -> STANDING_DIMENSIONS
        }

    override fun createNavigation(world: World): EntityNavigation = VillagerNavigation(this, world)

    override fun wakeUp() {
        super.wakeUp()
    }

    override fun sleep(pos: BlockPos) {
        super.sleep(pos)
    }
    
    fun canSleep(): Boolean = this.world.getTimeOfDay() % 24000.0f / 1000 > 12

    private var fighting: Boolean = false

    fun isFighting(): Boolean = fighting

    fun setFighting(fighting: Boolean) {
        this.fighting = fighting
    }

    fun isSitting(): Boolean = this.pose == EntityPose.SITTING

    fun sit(pos: BlockPos?) {
        pos?.let { p ->
            val target = p.toCenterPos()
            this.setPosition(target.getX(), target.getY() - 0.3, target.getZ())
            this.setPose(EntityPose.SITTING)
        } ?: run {
            val target = blockPos.toCenterPos()
            this.setPosition(target.getX(), target.getY() - 0.3, target.getZ())
            this.setPose(EntityPose.SITTING)
        }
    }

    fun getUp() {
        if (this.pose == EntityPose.STANDING) return

        if (this.isSleeping()) {
            this.wakeUp()
        }

        if (this.isSitting()) {
            this.setPose(EntityPose.STANDING)
        }
    }

    fun isEating(): Boolean {
        val stack = this.getActiveItem()
        return stack.getUseAction() == UseAction.EAT
    }

    override fun eatFood(
        world: World,
        stack: ItemStack,
    ): ItemStack {
        world.playSound(
            null,
            this.getX(),
            this.getY(),
            this.getZ(),
            SoundEvents.ENTITY_PLAYER_BURP,
            SoundCategory.PLAYERS,
            0.5F,
            world.random.nextFloat() * 0.1F + 0.9F,
        )
        if (stack.isFood()) {
            stack.getItem().getFoodComponent()?.let { component ->
                LOGGER.info("Component: {}", component.getHunger())
            }
            stack.decrement(1)
        }
        super.eatFood(world, stack)
        return stack
    }

    override fun updateSwimming() {
        if (!this.getWorld().isClient) {
            val height = this.getSwimHeight()
            if (this.isTouchingWater() && height != 0.5 && this.getFluidHeight(FluidTags.WATER) > height) {
                this.setSwimming(true)
            } else {
                this.setSwimming(false)
            }
        }
    }

    override fun isInSwimmingPose(): Boolean = isOnWater

    override fun getSwimHeight(): Double = if (this.horizontalCollision) 0.5 else 1.2

    fun pushErrand(
        cid: Action.Type,
        pos: BlockPos? = null,
    ): Boolean {
        val priority = (Action.get(cid) as Position).scan(this, pos)
        if (priority < 0) return false
        errandManager.push(cid, pos, priority)
        return true
    }

    override fun getUnscaledRidingOffset(vehicle: Entity): Float = -0.6f

    override fun getHandItems(): Iterable<ItemStack> = this.inventory.getHeldItems()

    override fun getArmorItems(): Iterable<ItemStack> = this.inventory.getArmorItems()

    private fun getArmorItem(id: Int): ItemStack = this.inventory.getArmorBySlot(id)

    private fun getHandItem(id: Int): ItemStack = this.inventory.getHeldItems()[id]

    private fun setArmorItem(
        id: Int,
        itemStack: ItemStack,
    ): ItemStack = this.inventory.setArmorField(id, itemStack)

    private fun setHandItem(
        id: Int,
        itemStack: ItemStack,
    ): ItemStack = this.inventory.setHeldField(id, itemStack)

    override fun getEquippedStack(slot: EquipmentSlot): ItemStack =
        when (slot.type) {
            EquipmentSlot.Type.HAND -> this.getHandItem(slot.entitySlotId)
            EquipmentSlot.Type.ARMOR -> this.getArmorItem(slot.entitySlotId)
            else -> ItemStack.EMPTY
        }

    override fun equipStack(
        slot: EquipmentSlot,
        stack: ItemStack,
    ) {
        this.processEquippedStack(stack)
        when (slot.type) {
            EquipmentSlot.Type.HAND -> {
                this.onEquipStack(slot, this.setHandItem(slot.entitySlotId, stack), stack)
            }
            EquipmentSlot.Type.ARMOR -> {
                this.onEquipStack(slot, this.setArmorItem(slot.entitySlotId, stack), stack)
            }
            else -> ItemStack.EMPTY
        }
    }

    fun setProfession(type: ProfessionType) {
        this.profession = Profession.get(this, type)
    }

    override fun writeScreenOpeningData(
        player: ServerPlayerEntity,
        buf: PacketByteBuf,
    ) {
        buf.writeBlockPos(this.getBlockPos())
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
        val itemStack: ItemStack = player.getStackInHand(hand)
        if (!itemStack.isOf(Items.VILLAGER_SPAWN_EGG) && isAlive && !isSleeping) {
            // if (!world.isClient) {
            //    if (this.profession.type == ProfessionType.MERCHANT) {
            //        val optionalInt = player.openHandledScreen(this)
            //    }
            // }
            return ActionResult.success(this.getWorld().isClient)
        }
        return super.interactMob(player, hand)
    }

    private var isOnWater: Boolean = false
    var energy: Double = 20.0
    var hunger: Double = 20.0
    var ticksToDecreaseHunger = 0
    var foodTickTimer = 0
    // TOOD: make a NeedsManager class
    override fun tick() {
        isOnWater = this.isSwimming()
        super.tick()
        isOnWater = false
        // TODO: think how to include saturation in this system
        if (hunger > 1 && --ticksToDecreaseHunger < 0) {
            ticksToDecreaseHunger = 1200
            hunger--
        }

        if (this.age % 20 == 0 && hunger > 1 && this.getHealth() > 0.0f && this.getHealth() < this.getMaxHealth()) {
            if (hunger > 1) {
                this.heal(1.0f)
                hunger = hunger - 1
            }
        }

        if (++foodTickTimer > 80) {
            if (hunger <= 0 && this.getHealth() > 1.0f) {
                this.damage(this.getDamageSources().starve(), 1.0f)
            }
            foodTickTimer = 0
        }

        if (energy < hunger && hunger > 1) {
            energy = energy + 1
            hunger = hunger - 1
        }
    }

    override fun tickMovement() {
        this.tickHandSwing()
        super.tickMovement()
    }

    override fun canImmediatelyDespawn(distanceSquared: Double): Boolean = false

    override fun remove(reason: RemovalReason) {
        super.remove(reason)
    }

    fun setState(state: ActionState) {
        this.dataTracker.set(STATE, state.ordinal)
    }

    fun getState(): Int = this.dataTracker.get(STATE)

    fun handleState(): ActionState =
        when (getState()) {
            ActionState.DISAGREE.ordinal -> ActionState.DISAGREE
            ActionState.AGREE.ordinal -> ActionState.AGREE
            ActionState.TALK.ordinal -> ActionState.TALK
            ActionState.OFFER.ordinal -> ActionState.OFFER
            ActionState.SWEAT.ordinal -> ActionState.SWEAT
            else -> throw IllegalArgumentException("Unknown animation state: ${getState()}")
        }

    override fun onDeath(damageSource: DamageSource) {
        LOGGER.info("Villager {} died, message: {}", this as Any, damageSource.getDeathMessage(this).string)
        this.dropInventory()
        if (!this.getWorld().isClient) {
            SettlementAccessor.leaveSettlement(this)
        }
        super.onDeath(damageSource)
    }

    override fun canPickUpLoot(): Boolean = true

    override fun canEquip(stack: ItemStack): Boolean = true

    override fun canGather(stack: ItemStack): Boolean =
        this.profession.desiredItems.any { p -> p(stack.item) } && this.inventory.canInsert(stack)

    override fun loot(item: ItemEntity) {
        this.pickUpItem(item)
    }

    override fun damageArmor(
        damageSource: DamageSource,
        amount: Float,
    ) {
        val slots = intArrayOf(0, 1, 2, 3)
        if (amount <= 0.0f) {
            return
        }
        val damage: Int = if ((amount / 4.0f) < 1.0f) 1 else (amount / 4.0f).toInt()
        for (i in slots) {
            val itemStack = this.getArmorItem(i)
            if (damageSource.isIn(DamageTypeTags.IS_FIRE) &&
                itemStack.getItem().isFireproof() ||
                !(ItemPredicate.ARMOR(itemStack.getItem()))
            ) {
                continue
            }
            itemStack.damage(
                damage,
                this,
                { e -> e.sendEquipmentBreakStatus(EquipmentSlot.fromTypeIndex(EquipmentSlot.Type.ARMOR, i)) },
            )
        }
    }

    fun pickUpItem(item: ItemEntity) {
        val itemStack = item.stack
        if (this.canGather(itemStack)) {
            val canInsert = this.inventory.canInsert(itemStack)
            if (!canInsert) return
            val originalCount = itemStack.count
            val remainingStack = this.inventory.addStack(itemStack)
            this.sendPickup(item, originalCount - remainingStack.count)
            if (remainingStack.isEmpty) {
                item.discard()
            } else {
                itemStack.setCount(remainingStack.getCount())
            }
            if (ItemPredicate.ARMOR(item.stack.item)) {
                this.tryEquipArmor()
            }
        }
    }

    fun tryInsert(itemStack: ItemStack) {
        if (this.inventory.canInsert(itemStack)) {
            this.inventory.addStack(itemStack)
        } else {
            this.dropStack(itemStack)
        }
    }

    fun tryEquipArmor() {
        val itemTaken = this.inventory.takeItem(ItemPredicate.ARMOR)
        if (itemTaken != ItemStack.EMPTY) {
            val equipmentSlot = MobEntity.getPreferredEquipmentSlot(itemTaken)
            val itemStack = this.getEquippedStack(equipmentSlot)
            val prefersNew = ItemPredicate.prefersNewEquipment(itemTaken, itemStack)
            if (prefersNew) {
                this.tryInsert(itemStack)
                this.equipStack(equipmentSlot, itemTaken)
            }
        }
    }

    fun tryEquip(
        predicate: (Item) -> Boolean,
        slot: EquipmentSlot,
    ): Boolean {
        val equipped: ItemStack =
            when (slot) {
                EquipmentSlot.MAINHAND -> this.getStackInHand(Hand.MAIN_HAND)
                EquipmentSlot.OFFHAND -> this.getStackInHand(Hand.OFF_HAND)
                else -> ItemStack.EMPTY
            }

        if (predicate(equipped.getItem())) {
            return true
        }

        val item = this.inventory.takeItem(predicate)
        if (item != ItemStack.EMPTY) {
            if (equipped != ItemStack.EMPTY) {
                this.tryInsert(equipped)
            }
            this.equipStack(slot, item)
            return true
        }
        return false
    }

    override fun dropInventory() {
        this.inventory.getItems().forEach { item ->
            this.dropStack(item)
        }
        this.inventory.clear()
    }

    override fun readCustomDataFromNbt(nbt: NbtCompound) {
        super.readCustomDataFromNbt(nbt)
        
        if (nbt.contains(INVENTORY_KEY, NbtElement.LIST_TYPE.toInt())) {
            this.inventory.readNbt(nbt.getList(INVENTORY_KEY, NbtElement.COMPOUND_TYPE.toInt()))
        }

        if (!isProfessionInitialized()) this.setProfession(ProfessionType.values()[nbt.getInt(VILLAGER_PROFESSION)])
        if (!isErrandManagerInitialized()) this.errandManager = ErrandManager.fromNbt(nbt.getCompound(ERRAND_MANAGER))
        if (!isErrandProviderInitialized()) this.errandProvider = ErrandProvider.fromNbt(nbt.getCompound(ERRAND_PROVIDER))
    }

    override fun writeCustomDataToNbt(nbt: NbtCompound) {
        super.writeCustomDataToNbt(nbt)
        nbt.put(INVENTORY_KEY, this.inventory.writeNbt())
        nbt.putInt(VILLAGER_PROFESSION, this.profession.type.ordinal)
        nbt.put(ERRAND_MANAGER, errandManager.toNbt())
        nbt.put(ERRAND_PROVIDER, errandProvider.toNbt())
    }

    companion object {
        const val INVENTORY_KEY = "Inventory"
        const val VILLAGER_PROFESSION = "VillagerProfession"
        const val ERRAND_MANAGER = "ErrandManager"
        const val ERRAND_PROVIDER = "ErrandProvider"

        val SITTING_DIMENSIONS: EntityDimensions = EntityDimensions.fixed(0.6f, 1.35f)
        val STANDING_DIMENSIONS: EntityDimensions = EntityDimensions.fixed(0.6f, 1.95f)

        // Generalize this in a enum that tracks states
        val STATE: TrackedData<Int> =
            DataTracker.registerData(
                AbstractVillagerEntity::class.java,
                TrackedDataHandlerRegistry.INTEGER,
            )

        fun createCustomVillagerAttributes(): DefaultAttributeContainer.Builder =
            PathAwareEntity
                .createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 0.5)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0)
    }
}
