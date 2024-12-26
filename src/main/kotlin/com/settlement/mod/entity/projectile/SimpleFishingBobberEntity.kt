package com.settlement.mod.entity.projectile

import com.settlement.mod.Settlement
import com.settlement.mod.entity.mob.AbstractVillagerEntity
import com.settlement.mod.profession.Fisherman
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.entity.MovementType
import net.minecraft.entity.projectile.ProjectileEntity
import net.minecraft.entity.projectile.ProjectileUtil
import net.minecraft.fluid.FluidState
import net.minecraft.nbt.NbtCompound
import net.minecraft.network.listener.ClientPlayPacketListener
import net.minecraft.network.packet.Packet
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket
import net.minecraft.registry.tag.FluidTags
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.EntityHitResult
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.random.Random
import net.minecraft.world.World

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
        this.ignoreCameraFrustum = true
    }

    constructor(thrower: AbstractVillagerEntity, world: World, luckOfTheSeaLevel: Int, lureLevel: Int) : this(
        Settlement.SIMPLE_FISHING_BOBBER,
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
        this.setFishHook(this)
    }

    private fun setFishHook(rod: SimpleFishingBobberEntity?) {
        this.getOwner()?.let { owner ->
            owner as AbstractVillagerEntity
            if (owner.isProfessionInitialized() && owner.profession is Fisherman) (owner.profession as Fisherman).setFishHook(rod)
        }
    }

    fun setCaughtFish() {
        this.getOwner()?.let { owner ->
            owner as AbstractVillagerEntity
            if (owner.isProfessionInitialized() && owner.profession is Fisherman) (owner.profession as Fisherman).setCaughtFish()
        }
        this.discard()
    }

    override fun initDataTracker() {}

    override fun shouldRender(distance: Double): Boolean = distance < 2048.0

    override fun updateTrackedPositionAndAngles(
        x: Double,
        y: Double,
        z: Double,
        yaw: Float,
        pitch: Float,
        interpolationSteps: Int,
    ) {}

    override fun tick() {
        super.tick()
        if (fishingTicks >= 300) {
            this.setCaughtFish()
            return
        }
        val owner = this.getOwner()
        if (owner == null) {
            remove(RemovalReason.DISCARDED)
            return
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

    override fun canHit(entity: Entity?): Boolean {
        return false
        // super.canHit(entity)
    }

    override fun onEntityHit(entityHitResult: EntityHitResult) {
        // super.onEntityHit(entityHitResult)
    }

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
        this.setFishHook(null)
        super.remove(reason)
    }

    override fun onRemoved() {
        this.setFishHook(null)
    }

    override fun canUsePortals(): Boolean = false

    override fun createSpawnPacket(): Packet<ClientPlayPacketListener> {
        val entity: Entity? = this.getOwner()
        return EntitySpawnS2CPacket(this, entity?.id ?: id)
    }

    override fun onSpawnPacket(packet: EntitySpawnS2CPacket) {
        super.onSpawnPacket(packet)
        if (this.getOwner() == null) {
            kill()
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
