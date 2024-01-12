package com.settlement.mod.entity.mob

import com.settlement.mod.LOGGER
import com.settlement.mod.action.Action
import com.settlement.mod.action.ActionState
import com.settlement.mod.entity.ai.goal.ActGoal
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

// TODO:
// - add bit masking for reaching O(1) errand lookup, instead of set looking O(n)
// - improve data tracking state for animation handling
// - make villagers unleashable
// - create isPicking setters/getters
class AbstractVillagerEntity(
    entityType: EntityType<out AbstractVillagerEntity>,
    world: World?,
) : PathAwareEntity(entityType, world),
    ExtendedScreenHandlerFactory {
    val inventory: VillagerInventory = VillagerInventory(this)
    lateinit var profession: Profession
    val errandManager: ErrandManager = ErrandManager()
    var path: Path? = null
    val item = mutableMapOf<Action.Type, Int>()

    init {
        this.getNavigation().setCanSwim(true)
        (this.getNavigation() as MobNavigation).setCanPathThroughDoors(true)
    }

    override fun initDataTracker() {
        this.dataTracker.startTracking(STATE, 0)
        super.initDataTracker()
    }

    override fun initialize(
        world: ServerWorldAccess,
        difficulty: LocalDifficulty,
        spawnReason: SpawnReason,
        entityData: EntityData?,
        entityNbt: NbtCompound?,
    ): EntityData? {
        SettlementAccessor.setProfession(this)

        return super.initialize(world, difficulty, spawnReason, entityData, entityNbt)
    }

    override fun initGoals() {
        goalSelector.add(0, ReactGoal(this))
        goalSelector.add(0, ActGoal(this))
    }

    fun pushErrand(
        cid: Action.Type,
        pos: BlockPos? = null,
    ): Boolean {
        val action = Action.get(cid)
        val priority = action.scan(this, pos)
        if (priority < 0) return false
        errandManager.push(action.type, cid, pos, priority, action.isUnique)
        return true
    }

    override fun getDimensions(pose: EntityPose): EntityDimensions =
        when (pose) {
            EntityPose.SLEEPING -> SLEEPING_DIMENSIONS
            EntityPose.SITTING -> SITTING_DIMENSIONS
            else -> STANDING_DIMENSIONS
        }

    override fun createNavigation(world: World): EntityNavigation = VillagerNavigation(this, world)

    private var working: Boolean = false

    fun isWorking(): Boolean = this.working

    fun setWorking(working: Boolean) {
        this.working = working
    }

    // used for combat profession for distinguish neutral mobs from hostiles
    private var fighting: Boolean = false

    fun isFighting(): Boolean = fighting

    fun setFighting(fighting: Boolean) {
        this.fighting = fighting
        if (fighting) {
            val idleSet = setOf(Action.Type.TALK, Action.Type.WANDER, Action.Type.PICK, Action.Type.SEEK)
            this.errandManager.remove(idleSet)
            val combatSet = setOf(Action.Type.CHARGE, Action.Type.AIM, Action.Type.ATTACK, Action.Type.DEFEND, Action.Type.FLEE)
            if (!combatSet.any { this.errandManager.has(it) }) {
                this.profession.TARGET_HANDLER(this)
            }
        } else {
            val combatSet =
                setOf(Action.Type.CHARGE, Action.Type.AIM, Action.Type.ATTACK, Action.Type.DEFEND, Action.Type.FLEE, Action.Type.STRAFE)
            this.errandManager.remove(combatSet)
        }
    }

    override fun wakeUp() {
        super.wakeUp()
    }

    override fun sleep(pos: BlockPos) {
        super.sleep(pos)
    }

    fun canSleep(): Boolean = this.world.getTimeOfDay() % 24000.0f / 1000 > 12

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
                errandManager.satiation = component.getHunger().toFloat()
            }
            stack.decrement(1)
        }
        super.eatFood(world, stack)
        return stack
    }

    // TODO: make animation trigger only if full submerged
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

    override fun isInSwimmingPose(): Boolean = this.isSwimming()

    override fun getSwimHeight(): Double = 1.2

    override fun getUnscaledRidingOffset(vehicle: Entity): Float = -0.6f

    override fun getHandItems(): Iterable<ItemStack> = this.inventory.getHeldItems()

    override fun getArmorItems(): Iterable<ItemStack> = this.inventory.getArmorItems()

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

    fun isProfessionInitialized() = ::profession.isInitialized

    fun setProfession(type: ProfessionType) {
        this.profession = Profession.get(type)
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
        if (!this.world.isClient) {
            val stack: ItemStack = player.getStackInHand(hand)
            if (!stack.isOf(Items.VILLAGER_SPAWN_EGG) && isAlive && !isSleeping) {
                return ActionResult.success(this.profession.CLICK_HANDLER(this, player))
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

    private var picking = false

    fun setPicking(picking: Boolean) {
        this.picking = picking
    }

    fun isPicking(): Boolean = this.picking

    override fun canPickUpLoot(): Boolean = picking

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
            val stack = this.getArmorItem(i)
            if (damageSource.isIn(DamageTypeTags.IS_FIRE) &&
                stack.getItem().isFireproof() ||
                !(ItemPredicate.ARMOR(stack.getItem()))
            ) {
                continue
            }
            stack.damage(
                damage,
                this,
                { e -> e.sendEquipmentBreakStatus(EquipmentSlot.fromTypeIndex(EquipmentSlot.Type.ARMOR, i)) },
            )
        }
    }

    fun pickUpItem(item: ItemEntity) {
        val stack = item.stack
        if (this.canGather(stack)) {
            val canInsert = this.inventory.canInsert(stack)
            if (!canInsert) return
            val originalCount = stack.count
            val remainingStack = this.inventory.addStack(stack)
            this.sendPickup(item, originalCount - remainingStack.count)
            if (remainingStack.isEmpty) {
                item.discard()
            } else {
                stack.setCount(remainingStack.getCount())
            }
            if (ItemPredicate.ARMOR(item.stack.item)) {
                this.tryEquipArmor()
            }
        }
    }

    fun tryInsert(stack: ItemStack) {
        if (this.inventory.canInsert(stack)) {
            this.inventory.addStack(stack)
        } else {
            this.dropStack(stack)
        }
    }

    fun tryEquipArmor() {
        val itemTaken = this.inventory.takeItem(ItemPredicate.ARMOR)
        if (itemTaken != ItemStack.EMPTY) {
            val equipmentSlot = MobEntity.getPreferredEquipmentSlot(itemTaken)
            val stack = this.getEquippedStack(equipmentSlot)
            val prefersNew = ItemPredicate.prefersNewEquipment(itemTaken, stack)
            if (prefersNew) {
                this.tryInsert(stack)
                this.equipStack(equipmentSlot, itemTaken)
            }
        }
    }

    fun tryEquip(
        predicate: (Item) -> Boolean,
        slot: EquipmentSlot,
        i: Int = -1,
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

        val item = if (id != -1) this.inventory.takeItem(predicate, i) else this.inventory.takeItem(predicate)
        if (item != ItemStack.EMPTY) {
            if (equipped != ItemStack.EMPTY) {
                this.tryInsert(equipped)
            }
            this.equipStack(slot, item)
            return true
        }
        return false
    }
    // TODO: destroy a faction of items in entity inventory
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

        if (nbt.contains(ERRAND_MANAGER, NbtElement.COMPOUND_TYPE.toInt())) {
            this.errandManager.readNbt(nbt.getCompound(ERRAND_MANAGER))
        }
        if (!isProfessionInitialized()) {
            this.setProfession(ProfessionType.values()[nbt.getInt(VILLAGER_PROFESSION)])
            this.profession.INVENTORY_HANDLER(this) // quick fix
        }
    }

    override fun writeCustomDataToNbt(nbt: NbtCompound) {
        super.writeCustomDataToNbt(nbt)
        nbt.put(INVENTORY_KEY, inventory.writeNbt())
        nbt.put(ERRAND_MANAGER, errandManager.writeNbt())
        nbt.putInt(VILLAGER_PROFESSION, profession.type.ordinal)
    }

    fun getDebugData(): List<String>? = this.errandManager.getDebugData()

    companion object {
        const val INVENTORY_KEY = "Inventory"
        const val VILLAGER_PROFESSION = "VillagerProfession"
        const val ERRAND_MANAGER = "ErrandManager"

        val SITTING_DIMENSIONS: EntityDimensions = EntityDimensions.fixed(0.6f, 1.35f)
        val STANDING_DIMENSIONS: EntityDimensions = EntityDimensions.fixed(0.6f, 1.95f)

        // generalize this in a enum that tracks states
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
