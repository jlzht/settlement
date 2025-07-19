package com.settlement.mod.entity.projectile

import com.settlement.mod.data.ModLootTables
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

// TODO: add persistant bobber
class SimpleFishingBobberEntity(
    type: EntityType<out SimpleFishingBobberEntity>,
    world: World,
) : ProjectileEntity(type, world) {
    private val velocityRandom: Random = Random.create()

    private var state: BobberState = BobberState.FLYING
    private var removalTimer: Int = 0
    private var hookCountdown: Int = 0
    private var fishingTicks: Int = 0

    var isClosed = false
    lateinit var condition: () -> Boolean

    @JvmName("setupLambda")
    fun setCondition(condition: () -> Boolean) {
        this.condition = condition
    }
    constructor(thrower: AbstractVillagerEntity, pos: BlockPos, world: World) : this(
        ModEntities.SIMPLE_FISHING_BOBBER,
        world,
    ) {
        this.setOwner(thrower)
        val start = thrower.eyePos.subtract(thrower.rotationVector.multiply(0.3))
        this.refreshPositionAndAngles(start.x, start.y, start.z, thrower.yaw, thrower.pitch)
        val target = Vec3d.ofCenter(pos)

        val dx = target.x - start.x
        val dy = target.y - start.y
        val dz = target.z - start.z
        val d = Math.sqrt(dx * dx + dz * dz)
        if (d == 0.0) {
            setVelocity(0.0, 1.0, 0.0)
            return
        }

        val angle = Math.toRadians(55.0)
        val cos = Math.cos(angle)
        val sin = Math.sin(angle)
        val g = 0.03
        val denom = 2 * cos * cos * (d * Math.tan(angle) - dy)
        if (denom <= 0) {
            setVelocity(dx / d * 0.5, 0.5, dz / d * 0.5)
            return
        }

        val maxDist = 16.0
        val minFactor = 1.0
        val maxFactor = 4.0

        val t = (d / maxDist).coerceIn(0.0, 1.0)

        val distFactor = minFactor + t * (maxFactor - minFactor)

        val v0 = Math.sqrt(d * d * g / denom)

        val vy = v0 * sin

        val vh = Math.sqrt(v0 * v0 - vy * vy) * distFactor

        val horizontalDir = Vec3d(dx, 0.0, dz).normalize()

        val vx = horizontalDir.x * vh.toDouble()
        val vz = horizontalDir.z * vh.toDouble()

        val rnd = velocityRandom

        fun spread(v: Double) = v + rnd.nextTriangular(0.0, 0.0075)

        setVelocity(spread(vx), spread(vy), spread(vz))
    }

    override fun setOwner(entity: Entity?) {
        super.setOwner(entity)
    }

    override fun initDataTracker(build: DataTracker.Builder) {}

    override fun shouldRender(distance: Double): Boolean = distance < 2048.0

    override fun tick() {
        super.tick()
        if (fishingTicks >= 300) {
            (this.getOwner() as? AbstractVillagerEntity)?.let { entity ->
                entity.world.let { world ->
                    if (!world.isClient) {
                        val stack = entity.getStackInHand(Hand.MAIN_HAND)
                        world.getServer()?.let { server ->
                            val parameterSet =
                                LootWorldContext
                                    .Builder(world as ServerWorld)
                                    .build(LootContextTypes.EMPTY)

                            val lootTable = server.reloadableRegistries.getLootTable(ModLootTables.FISHERMAN)
                            lootTable.generateLoot(parameterSet).forEach {
                                val itemEntity = ItemEntity(world, this.x, this.y, this.z, it)
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
            }
            return
        }
        val owner = this.getOwner()
        if (owner == null) {
            remove(RemovalReason.DISCARDED)
            return
        } else {
            if (!owner.world.isClient && owner is AbstractVillagerEntity && condition.invoke()) {
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
        val fluidState: FluidState = world.getFluidState(blockPos)
        if (fluidState.isIn(FluidTags.WATER)) {
            f = fluidState.getHeight(world, blockPos)
        }

        if (state == BobberState.FLYING) {
            if (f > 0.0f) {
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
            if (owner.isAlive) {
                isClosed = true
            }
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
