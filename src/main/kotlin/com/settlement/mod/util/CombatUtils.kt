package com.settlement.mod.util

import com.settlement.mod.entity.mob.AbstractVillagerEntity
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.enchantment.Enchantments
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.projectile.PersistentProjectileEntity
import net.minecraft.entity.projectile.ProjectileEntity
import net.minecraft.entity.projectile.ProjectileUtil
import net.minecraft.item.CrossbowItem
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.sound.SoundEvents
import net.minecraft.util.Hand
import net.minecraft.util.math.Vec3d
import org.joml.Vector3f

object CombatUtils {
    // TODO: re-add bow check
    fun shoot(
        entity: AbstractVillagerEntity,
        target: LivingEntity,
        itemStack: ItemStack,
        hand: Hand,
        isCrossbow: Boolean,
    ) {
        val item = entity.getProjectileType(entity.getStackInHand(hand))
        val projectileEntity: PersistentProjectileEntity = ProjectileUtil.createArrowProjectile(entity, item, 1.0f)
        if (entity.random.nextFloat() < 0.05f) {
            projectileEntity.setCritical(true)
        }
        if (isCrossbow) {
            projectileEntity.setSound(SoundEvents.ITEM_CROSSBOW_HIT)
            projectileEntity.setShotFromCrossbow(true)
            val i: Byte = EnchantmentHelper.getLevel(Enchantments.PIERCING, itemStack).toByte()
            if (i > 0) projectileEntity.setPierceLevel(i)
            CrossbowItem.setCharged(itemStack, false)
        }
        shootTo(entity, target, projectileEntity, 1.0f, 1.6f)
        entity.world.spawnEntity(projectileEntity)
        itemStack.damage(1, entity, { e -> e.sendToolBreakStatus(hand) })
    }

    private fun shootTo(
        entity: AbstractVillagerEntity,
        target: LivingEntity,
        projectile: ProjectileEntity,
        multishotSpray: Float,
        speed: Float,
    ) {
        val d = target.getX() - entity.getX()
        val e = target.getZ() - entity.getZ()
        val f = Math.sqrt(d * d + e * e)
        val g = target.getBodyY(0.3333333333333333) - projectile.getY() + f * 0.2F
        val vector3f = getProjectileLaunchVelocity(entity, Vec3d(d, g, e), multishotSpray)
        projectile.setVelocity(
            vector3f.x().toDouble(),
            vector3f.y().toDouble(),
            vector3f.z().toDouble(),
            speed,
            14 - entity.getWorld().getDifficulty().getId() * 4.0f,
        )
        entity.playSound(SoundEvents.ITEM_CROSSBOW_SHOOT, 1.0f, 1.0f / (entity.random.nextFloat() * 0.4f + 0.8f))
    }

    private fun getProjectileLaunchVelocity(
        entity: AbstractVillagerEntity,
        positionDelta: Vec3d,
        multishotSpray: Float,
    ): Vector3f {
        val vector3f = positionDelta.toVector3f().normalize()
        var vector3f2 = Vector3f(vector3f).cross(Vector3f(0.0f, 0.0f, 1.0f))
        if (vector3f2.lengthSquared() <= 1.0E-7) {
            val vec3d = entity.getOppositeRotationVector(1.0f)
            vector3f2 = Vector3f(vector3f).cross(vec3d.toVector3f())
        }
        val vector3f3 = vector3f.rotateAxis(0.0f, vector3f2.x, vector3f2.y, vector3f2.z)
        return vector3f.rotateAxis(multishotSpray * (Math.PI.toFloat() / 180), vector3f3.x, vector3f3.y, vector3f3.z)
    }
}
