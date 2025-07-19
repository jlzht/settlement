package com.settlement.mod.client.render.entity

import com.settlement.mod.MODID
import com.settlement.mod.SettlementConfig
import com.settlement.mod.client.render.entity.model.AbstractVillagerEntityModel
import com.settlement.mod.entity.mob.AbstractVillagerEntity
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.entity.BipedEntityRenderer
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.render.entity.LivingEntityRenderer
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer
import net.minecraft.client.render.entity.feature.ElytraFeatureRenderer
import net.minecraft.client.render.entity.feature.HeadFeatureRenderer
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer
import net.minecraft.client.render.entity.model.BipedEntityModel
import net.minecraft.client.render.entity.state.BipedEntityRenderState
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.entity.EntityPose
import net.minecraft.item.CrossbowItem
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.item.consume.UseAction
import net.minecraft.util.Arm
import net.minecraft.util.Hand
import net.minecraft.util.Identifier

@Environment(EnvType.CLIENT)
class AbstractVillagerEntityRenderState : BipedEntityRenderState() {
    var action: Int = 0
    var skin: Int = 0
}

@Environment(EnvType.CLIENT)
class AbstractVillagerEntityRenderer(
    ctx: EntityRendererFactory.Context,
) : LivingEntityRenderer<
        AbstractVillagerEntity,
        AbstractVillagerEntityRenderState,
        AbstractVillagerEntityModel,
    >(
        ctx,
        AbstractVillagerEntityModel(ctx.getPart(AbstractVillagerEntityModel.LAYER)),
        0.5f,
    ) {
    private val innerModel: AbstractVillagerEntityModel
    private val outerModel: AbstractVillagerEntityModel

    init {
        model =
            if (SettlementConfig.useAlternativeModel) {
                AbstractVillagerEntityModel(ctx.getPart(AbstractVillagerEntityModel.ALT_LAYER))
            } else {
                AbstractVillagerEntityModel(ctx.getPart(AbstractVillagerEntityModel.LAYER))
            }

        innerModel =
            if (SettlementConfig.useAlternativeModel) {
                AbstractVillagerEntityModel(ctx.getPart(AbstractVillagerEntityModel.ALT_ARMOR_INNER))
            } else {
                AbstractVillagerEntityModel(ctx.getPart(AbstractVillagerEntityModel.ARMOR_INNER))
            }

        outerModel =
            if (SettlementConfig.useAlternativeModel) {
                AbstractVillagerEntityModel(ctx.getPart(AbstractVillagerEntityModel.ALT_ARMOR_OUTER))
            } else {
                AbstractVillagerEntityModel(ctx.getPart(AbstractVillagerEntityModel.ARMOR_OUTER))
            }

        addFeature(HeadFeatureRenderer(this, ctx.getEntityModels(), HeadFeatureRenderer.HeadTransformation.DEFAULT))
        addFeature(ElytraFeatureRenderer(this, ctx.getEntityModels(), ctx.getEquipmentRenderer()))
        addFeature(HeldItemFeatureRenderer(this))
        addFeature(
            ArmorFeatureRenderer(
                this,
                innerModel,
                outerModel,
                ctx.getEquipmentRenderer(),
            ),
        )
    }

    override fun createRenderState(): AbstractVillagerEntityRenderState = AbstractVillagerEntityRenderState()

    override fun updateRenderState(
        entity: AbstractVillagerEntity,
        state: AbstractVillagerEntityRenderState,
        tickDelta: Float,
    ) {
        super.updateRenderState(entity, state, tickDelta)
        BipedEntityRenderer.updateBipedRenderState(entity, state, tickDelta, this.itemModelResolver)

        state.leftArmPose = getArmPose(entity, Arm.LEFT)
        state.rightArmPose = getArmPose(entity, Arm.RIGHT)
        state.action = entity.getState()
        state.skin = entity.getSkin()
    }

    override fun hasLabel(
        entity: AbstractVillagerEntity,
        d: Double,
    ): Boolean = false

    override fun render(
        state: AbstractVillagerEntityRenderState,
        matrixStack: MatrixStack,
        vertexConsumerProvider: VertexConsumerProvider,
        i: Int,
    ) {
        if (state.isInPose(EntityPose.SITTING)) {
            matrixStack.translate(0.0, -0.6, 0.0)
        }
        super.render(state, matrixStack, vertexConsumerProvider, i)
    }

    override fun getTexture(state: AbstractVillagerEntityRenderState): Identifier {
        if (!SettlementConfig.useAlternativeModel) {
            return TEXTURE
        } else if (SettlementConfig.failedLoadingSkins) {
            return FALLBACK
        } else {
            return SettlementConfig.skinTextures[state.skin] ?: FALLBACK
        }
    }

    override fun scale(
        state: AbstractVillagerEntityRenderState,
        matrixStack: MatrixStack,
    ) {
        matrixStack.scale(0.9375F, 0.9375F, 0.9375F)
    }

    companion object {
        private val TEXTURE = Identifier.of(MODID, "textures/entity/villager.png")
        private val FALLBACK = Identifier.of(MODID, "textures/entity/fallback.png")

        private fun getArmPose(
            ent: AbstractVillagerEntity,
            arm: Arm,
        ): BipedEntityModel.ArmPose {
            val mainStack = ent.getStackInHand(Hand.MAIN_HAND)
            val offStack = ent.getStackInHand(Hand.OFF_HAND)

            val mainPose = singlePose(ent, mainStack, Hand.MAIN_HAND)
            var offPose = singlePose(ent, offStack, Hand.OFF_HAND)

            if (mainPose.isTwoHanded) {
                offPose = if (offStack.isEmpty) BipedEntityModel.ArmPose.EMPTY else BipedEntityModel.ArmPose.ITEM
            }
            return if (ent.mainArm == arm) mainPose else offPose
        }

        private fun singlePose(
            ent: AbstractVillagerEntity,
            stack: ItemStack,
            hand: Hand,
        ): BipedEntityModel.ArmPose {
            if (stack.isEmpty) return BipedEntityModel.ArmPose.EMPTY

            if (stack.isOf(Items.CROSSBOW) && CrossbowItem.isCharged(stack)) {
                return BipedEntityModel.ArmPose.CROSSBOW_HOLD
            }

            if (ent.activeHand == hand && ent.itemUseTimeLeft > 0) {
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
}
