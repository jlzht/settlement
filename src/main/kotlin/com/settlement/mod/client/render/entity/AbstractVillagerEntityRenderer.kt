package com.settlement.mod.client.render.entity

import com.settlement.mod.MODID
import com.settlement.mod.client.render.entity.model.AbstractVillagerEntityModel
import com.settlement.mod.entity.mob.AbstractVillagerEntity
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.render.entity.BipedEntityRenderer
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer
import net.minecraft.client.render.entity.model.BipedEntityModel
import net.minecraft.client.render.entity.state.BipedEntityRenderState
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.item.CrossbowItem
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.item.consume.UseAction
import net.minecraft.util.Arm
import net.minecraft.util.Hand
import net.minecraft.util.Identifier

@Environment(EnvType.CLIENT)
class AbstractVillagerEntityRenderState : BipedEntityRenderState() {
    var action = AbstractVillagerEntity.Companion.State.NONE
    var isSitting = false
}

@Environment(EnvType.CLIENT)
class AbstractVillagerEntityRenderer(
    ctx: EntityRendererFactory.Context,
) : BipedEntityRenderer<AbstractVillagerEntity, AbstractVillagerEntityRenderState, AbstractVillagerEntityModel<AbstractVillagerEntityRenderState>>(
        ctx,
        AbstractVillagerEntityModel(ctx.getPart(AbstractVillagerEntityModel.LAYER)),
        0.5F,
    ) {
    init {
        this.addFeature(
            ArmorFeatureRenderer(
                this,
                AbstractVillagerEntityModel(ctx.getPart(AbstractVillagerEntityModel.ARMOR_INNER)),
                AbstractVillagerEntityModel(ctx.getPart(AbstractVillagerEntityModel.ARMOR_OUTER)),
                ctx.getEquipmentRenderer(),
            ),
        )
    }

    override fun updateRenderState(
        entity: AbstractVillagerEntity,
        state: AbstractVillagerEntityRenderState,
        f: Float,
    ) {
        super.updateRenderState(entity, state, f)
		    BipedEntityRenderer.updateBipedRenderState(entity, state, f, this.itemModelResolver)
		    state.leftArmPose = AbstractVillagerEntityRenderer.getArmPose(entity, Arm.LEFT)
		    state.rightArmPose = AbstractVillagerEntityRenderer.getArmPose(entity, Arm.RIGHT)
        state.isSitting = entity.isSitting()
        state.action = entity.getState()
    }

    override fun setupTransforms(
        state: AbstractVillagerEntityRenderState,
        matrices: MatrixStack,
        bodyYaw: Float,
        baseHeight: Float,
    ) {
        super.setupTransforms(state, matrices, bodyYaw, baseHeight)
        if (state.isSitting) {
            matrices.translate(0.0, -0.6, 0.0)
        }
    }

    override fun createRenderState(): AbstractVillagerEntityRenderState = AbstractVillagerEntityRenderState()

    companion object {
        val TEXTURE = Identifier.of(MODID, "textures/entity/villager.png")

        private fun getArmPose(
            entity: AbstractVillagerEntity,
            arm: Arm,
        ): BipedEntityModel.ArmPose {
            val mainStack = entity.getStackInHand(Hand.MAIN_HAND)
            val offStack = entity.getStackInHand(Hand.OFF_HAND)

            val mainPose = getArmPose(entity, mainStack, Hand.MAIN_HAND)
            var offPose = getArmPose(entity, offStack, Hand.OFF_HAND)
            if (mainPose.isTwoHanded) {
                offPose = if (offStack.isEmpty) BipedEntityModel.ArmPose.EMPTY else BipedEntityModel.ArmPose.ITEM
            }

            return if (entity.mainArm == arm) mainPose else offPose
        }

        private fun getArmPose(
            entity: AbstractVillagerEntity,
            stack: ItemStack,
            hand: Hand,
        ): BipedEntityModel.ArmPose {
            if (stack.isEmpty) return BipedEntityModel.ArmPose.EMPTY

            if (stack.isOf(Items.CROSSBOW) && CrossbowItem.isCharged(stack)) {
                return BipedEntityModel.ArmPose.CROSSBOW_HOLD
            }

            if (entity.activeHand == hand && entity.itemUseTimeLeft > 0) {
                return when (stack.useAction) {
                    UseAction.BLOCK -> BipedEntityModel.ArmPose.BLOCK
                    UseAction.BOW -> BipedEntityModel.ArmPose.BOW_AND_ARROW
                    UseAction.SPEAR -> BipedEntityModel.ArmPose.THROW_SPEAR
                    UseAction.CROSSBOW -> BipedEntityModel.ArmPose.CROSSBOW_CHARGE
                    UseAction.SPYGLASS -> BipedEntityModel.ArmPose.SPYGLASS
                    UseAction.TOOT_HORN -> BipedEntityModel.ArmPose.TOOT_HORN
                    UseAction.BRUSH -> BipedEntityModel.ArmPose.BRUSH
                    else -> BipedEntityModel.ArmPose.ITEM
                }
            }
            return BipedEntityModel.ArmPose.ITEM
        }
    }

    override fun getTexture(state: AbstractVillagerEntityRenderState): Identifier = TEXTURE
}
