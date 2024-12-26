package com.settlement.mod.entity.ai.goal

import com.settlement.mod.action.Action
import net.minecraft.entity.ItemEntity
import com.settlement.mod.entity.mob.AbstractVillagerEntity
import com.settlement.mod.profession.Combatant
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.ai.goal.Goal
import net.minecraft.entity.mob.HostileEntity
import net.minecraft.entity.mob.Monster
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.registry.tag.FluidTags
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import java.util.EnumSet

class PercieveGoal(
    private val entity: AbstractVillagerEntity,
) : Goal() {
    private var timeWithoutVisibility: Int = 0
    private var tickCooldown: Int = 0

    init {
        this.setControls(EnumSet.of(Goal.Control.TARGET))
    }

    override fun canStart(): Boolean {
        // TODO: add special behavior for lava
        if (entity.isTouchingWater() && entity.getFluidHeight(FluidTags.WATER) > entity.getSwimHeight()) {
            val g = if (entity.horizontalCollision) 0.3f else 0.15f
            if (entity.getRandom().nextFloat() < g || entity.getAir() < 20) {
                entity.getJumpControl().setActive()
            }
        }
        // Handle attackers when sitting or sleeping
        entity.getRecentDamageSource()?.let { damageSource ->
            val attacker = damageSource.getAttacker()
            if (attacker is LivingEntity) {
                if (entity.profession is Combatant) {
                    if (!(attacker is PlayerEntity && attacker.isCreative)) {
                        entity.target = attacker
                    }
                }
                entity.setFighting(true)
                entity.setTarget(attacker)
                return false
            }
        }
        // Forgets target if it is too far
        entity.target?.let { target ->
            if (entity.squaredDistanceTo(target) > 24 * 24) {
                entity.target = null
                return false
            }
            return !target.isAlive
        } ?: run {
            // Look for new target if not sleeping
            return !entity.isSleeping()
        }
    }

    override fun shouldContinue(): Boolean = false

    override fun start() {
        entity.setFighting(false)
        if (tickCooldown > 5) {
            tickCooldown = 0
            this.getNearbyEntities()
        }
        tickCooldown++
    }

    private fun getSearchBox(distance: Double): Box = entity.boundingBox.expand(distance, 4.0, distance)

    // TODO: Identify how costly is this function and make entity lookup be relative to profession
    private fun getNearbyEntities() {
        entity
            .getWorld()
            .getOtherEntities(entity, this.getSearchBox(16.0))
            .filter { entity.getVisibilityCache().canSee(it) }
            .sortedBy { entity.squaredDistanceTo(it) }
            .let { entities ->

                var direction: Vec3d = Vec3d.ZERO
                entities.filterIsInstance<LivingEntity>().forEach { target ->
                    direction =
                        direction.add(
                            if (target is Monster) {
                                entity.pos.add(target.pos)
                            } else {
                                entity.pos.subtract(target.pos)
                            },
                        )
                }

                entities
                    .filterIsInstance<LivingEntity>()
                    .minByOrNull { target ->
                        direction.normalize().squaredDistanceTo(target.pos.normalize())
                    }?.let {
                        when (it) {
                            is PlayerEntity -> {
                                // TODO: check reputation for set attacking
                                if (!it.isCreative) entity.setTarget(it)
                            }
                            // Make it ignore creepers
                            is HostileEntity -> {
                                entity.setFighting(true)
                                entity.setTarget(it)
                            }
                            else -> entity.setTarget(it)
                        }
                        if (entity.isFighting()) return
                    }

                if (!entity.errandManager.has(Action.Type.PICK)) {
                    entities
                        .filterIsInstance<ItemEntity>()
                        .filter { it.getItemAge() > 20 }
                        .filter { entity.canGather(it.stack) }
                        .firstOrNull()
                        ?.let { item ->
                            entity.pushErrand(Action.Type.PICK, item.blockPos)
                        }
                }
            }
    }
}
