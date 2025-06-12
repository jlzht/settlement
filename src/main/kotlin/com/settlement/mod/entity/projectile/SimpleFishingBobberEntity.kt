package com.settlement.mod.entity.projectile

import com.settlement.mod.entity.ModEntities
import com.settlement.mod.entity.mob.AbstractVillagerEntity
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.entity.ItemEntity
import net.minecraft.entity.MovementType
import net.minecraft.entity.data.DataTracker
import net.minecraft.entity.projectile.ProjectileEntity
import net.minecraft.entity.projectile.ProjectileUtil
import net.minecraft.fluid.FluidState
import net.minecraft.loot.LootTables
import net.minecraft.loot.context.LootContextParameters
import net.minecraft.loot.context.LootContextTypes
import net.minecraft.loot.context.LootWorldContext
import net.minecraft.nbt.NbtCompound
import net.minecraft.network.listener.ClientPlayPacketListener
import net.minecraft.network.packet.Packet
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket
import net.minecraft.registry.tag.FluidTags
import net.minecraft.server.network.EntityTrackerEntry
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.random.Random
import net.minecraft.world.World

// TODO:
//  - pass errand pos in order for bobber to reach point
//  - tweak item drops
class SimpleFishingBobberEntity(
    type: EntityType<out SimpleFishingBobberEntity>,
    world: World,
    luckOfTheSeaLevel: Int,
    lureLevel: Int,
) : ProjectileEntity(type, world) {
    private val velocityRandom: Random = Random.create()
    private var state: BobberState = BobberState.FLYING
    private var luckOfTheSeaLevel: Int = 0
    private var lureLevel: Int = 0
    private var removalTimer: Int = 0
    private var hookCountdown: Int = 0
    private var fishingTicks: Int = 0

    init {
        this.luckOfTheSeaLevel = maxOf(0, luckOfTheSeaLevel)
        this.lureLevel = maxOf(0, lureLevel)
    }

    constructor(thrower: AbstractVillagerEntity, world: World, luckOfTheSeaLevel: Int, lureLevel: Int) : this(
        ModEntities.SIMPLE_FISHING_BOBBER,
        world,
        luckOfTheSeaLevel,
        lureLevel,
    ) {
        this.setOwner(thrower)
        val f: Float = thrower.getPitch()
        val g: Float = thrower.headYaw
        val h: Float = MathHelper.cos(-g * (Math.PI.toFloat() / 180) - Math.PI.toFloat())
        val i: Float = MathHelper.sin(-g * (Math.PI.toFloat() / 180) - Math.PI.toFloat())
        val j: Float = -MathHelper.cos(-f * (Math.PI.toFloat() / 180))
        val k: Float = MathHelper.sin(-f * (Math.PI.toFloat() / 180))
        val d: Double = thrower.x - i * 0.3
        val e: Double = thrower.eyeY
        val l: Double = thrower.z - h * 0.3
        this.refreshPositionAndAngles(d, e, l, g, f)
        var vec3d: Vec3d = Vec3d(-i.toDouble(), MathHelper.clamp(-(k / j).toDouble(), -5.0, 5.0), -h.toDouble())
        var m: Double = vec3d.length()
        vec3d =
            vec3d.multiply(
                0.5 / m + this.velocityRandom.nextTriangular(0.5, 0.0103365),
                0.5 / m + this.velocityRandom.nextTriangular(0.5, 0.0103365),
                0.5 / m + this.velocityRandom.nextTriangular(0.5, 0.0103365),
            )
        this.velocity = vec3d
    }

    override fun setOwner(entity: Entity?) {
        super.setOwner(entity)
    }

    override fun initDataTracker(build: DataTracker.Builder) {}

    override fun shouldRender(distance: Double): Boolean = distance < 2048.0

    override fun tick() {
        super.tick()
        if (fishingTicks >= 300) {
            this.getOwner()?.let { entity ->
                entity as AbstractVillagerEntity
                val world = entity.world
                val stack = entity.getStackInHand(Hand.MAIN_HAND)
                if (!entity.world.isClient) {
                    val lootWorldContext =
                        LootWorldContext
                            .Builder(this.getWorld() as ServerWorld)
                            .add(LootContextParameters.ORIGIN, this.pos)
                            .add(LootContextParameters.TOOL, stack)
                            .add(LootContextParameters.THIS_ENTITY, this)
                            // .luck(luckOfTheSeaLevel.toFloat() + entity.luck.toFloat())
                            .build(LootContextTypes.FISHING)
                    world.getServer()?.let { server ->
                        val loot = server.getReloadableRegistries().getLootTable(LootTables.FISHING_GAMEPLAY)
                        val lootList = loot.generateLoot(lootWorldContext)
                        for (itemStack in lootList) {
                            val itemEntity = ItemEntity(world, this.x, this.y, this.z, itemStack)
                            val d = entity.x - this.x
                            val e = entity.y - this.y
                            val f = entity.z - this.z
                            val g = 0.1
                            itemEntity.setVelocity(d * g, e * g + Math.sqrt(Math.sqrt(d * d + e * e + f * f)) * 0.08, f * g)
                            world.spawnEntity(itemEntity)
                        }
                        this.discard()
                    }
                }
            }
            return
        }
        val owner = this.getOwner()
        if (owner == null) {
            remove(RemovalReason.DISCARDED)
            return
        } else {
            if (owner is AbstractVillagerEntity && !owner.world.isClient && !owner.isWorking()) {
                this.discard()
                return
            }
        }
        if (isOnGround) {
            removalTimer++
            if (removalTimer >= 50) {
                this.discard()
                return
            }
        } else {
            removalTimer = 0
        }
        var f = 0.0f
        val blockPos: BlockPos = blockPos
        val fluidState: FluidState = world.getFluidState(blockPos)
        if (fluidState.isIn(FluidTags.WATER)) {
            f = fluidState.getHeight(world, blockPos)
        }
        val bl: Boolean = f > 0.0f
        if (state == BobberState.FLYING) {
            if (bl) {
                velocity = velocity.multiply(0.15, 0.2, 0.15)
                state = BobberState.BOBBING
                return
            }
            checkForCollision()
        } else {
            if (state == BobberState.BOBBING) {
                val vec3d: Vec3d = velocity
                var d: Double = y + vec3d.y - blockPos.y - f
                if (MathHelper.abs(d.toFloat()) < 0.01) {
                    d += Math.signum(d) * 0.1
                }
                fishingTicks++
                velocity = Vec3d(vec3d.x * 0.9, vec3d.y - d * random.nextFloat() * 0.2, vec3d.z * 0.9)
            }
        }
        if (!fluidState.isIn(FluidTags.WATER)) {
            velocity = velocity.add(0.0, -0.03, 0.0)
        }
        move(MovementType.SELF, velocity)
        if (state == BobberState.FLYING && (isOnGround || horizontalCollision)) {
            velocity = Vec3d.ZERO
        }
        velocity = velocity.multiply(0.92)
        refreshPosition()
    }

    private fun checkForCollision() {
        val hitResult: HitResult = ProjectileUtil.getCollision(this) { super.canHit(it) }
        onCollision(hitResult)
    }

    override fun canHit(entity: Entity?) = false

    override fun onBlockHit(blockHitResult: BlockHitResult) {
        super.onBlockHit(blockHitResult)
        velocity = velocity.normalize().multiply(blockHitResult.squaredDistanceTo(this))
    }

    override fun writeCustomDataToNbt(nbt: NbtCompound) {}

    override fun readCustomDataFromNbt(nbt: NbtCompound) {}

    override fun handleStatus(status: Byte) {
        super.handleStatus(status)
    }

    override fun getMoveEffect(): Entity.MoveEffect = Entity.MoveEffect.NONE

    override fun remove(reason: Entity.RemovalReason?) {
        this.getOwner()?.let { owner ->
            owner as AbstractVillagerEntity
            if (owner.isAlive) owner.setWorking(false)
        }
        super.remove(reason)
    }

    override fun onRemoved() {}

    override fun canUsePortals(allowVehicles: Boolean): Boolean = false

    override fun createSpawnPacket(entityTrackerEntry: EntityTrackerEntry): Packet<ClientPlayPacketListener> {
        val entity: Entity? = this.getOwner()
        return EntitySpawnS2CPacket(this, entityTrackerEntry, entity?.id ?: id)
    }

    override fun onSpawnPacket(packet: EntitySpawnS2CPacket) {
        super.onSpawnPacket(packet)
        if (this.getOwner() == null) {
            this.discard()
        }
    }

    enum class BobberState {
        FLYING,
        BOBBING,
    }

    enum class PositionType {
        ABOVE_WATER,
        INSIDE_WATER,
        INVALID,
    }
}
