package com.settlement.mod.client.render.entity

import net.minecraft.client.MinecraftClient
import net.minecraft.client.font.TextRenderer
import com.settlement.mod.action.Action
import com.settlement.mod.action.Errand
import com.settlement.mod.MODID
import com.mojang.blaze3d.systems.RenderSystem
import com.settlement.mod.SettlementClient
import net.minecraft.client.render.VertexConsumerProvider
import com.settlement.mod.client.render.entity.model.AbstractVillagerEntityModel
import com.settlement.mod.entity.mob.AbstractVillagerEntity
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.render.entity.BipedEntityRenderer
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer
import net.minecraft.client.render.entity.feature.HeadFeatureRenderer
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer
import net.minecraft.client.render.entity.model.BipedEntityModel
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.text.Text
import net.minecraft.item.CrossbowItem
import net.minecraft.item.Items
import net.minecraft.util.Arm
import net.minecraft.util.Hand
import net.minecraft.util.Identifier
import net.minecraft.util.UseAction

@Environment(EnvType.CLIENT)
class AbstractVillagerEntityRenderer(
    ctx: EntityRendererFactory.Context,
) : BipedEntityRenderer<AbstractVillagerEntity, BipedEntityModel<AbstractVillagerEntity>>(
        ctx,
        AbstractVillagerEntityModel(ctx.getPart(SettlementClient.VILLAGER_LAYER)),
        0.5F,
    ) {
    init {
        this.addFeature(HeadFeatureRenderer(this, ctx.modelLoader, ctx.heldItemRenderer))
        this.addFeature(HeldItemFeatureRenderer(this, ctx.heldItemRenderer))
        this.addFeature(
            ArmorFeatureRenderer(
                this,
                AbstractVillagerEntityModel(ctx.getPart(SettlementClient.VILLAGER_ARMOR_INNER)),
                AbstractVillagerEntityModel(ctx.getPart(SettlementClient.VILLAGER_ARMOR_OUTER)),
                ctx.getModelManager(),
            ),
        )
    }

    override fun render(
        villager: AbstractVillagerEntity,
        f: Float,
        g: Float,
        matrixStack: MatrixStack,
        vertexConsumerProvider: VertexConsumerProvider,
        i: Int,
    ) {
        this.setModelPose(villager)
        super.render(villager, f, g, matrixStack, vertexConsumerProvider, i)
    }

    private fun setModelPose(villager: AbstractVillagerEntity) {
        model.sneaking = villager.isInSneakingPose()

        var mainhand = getArmPose(villager, Hand.MAIN_HAND)
        var offhand = getArmPose(villager, Hand.OFF_HAND)

        if (mainhand.isTwoHanded) {
            offhand = if (villager.offHandStack.isEmpty) BipedEntityModel.ArmPose.EMPTY else BipedEntityModel.ArmPose.ITEM
        }

        if (villager.mainArm == Arm.RIGHT) {
            model.rightArmPose = mainhand
            model.leftArmPose = offhand
        } else {
            model.rightArmPose = offhand
            model.leftArmPose = mainhand
        }
    }

    private fun getArmPose(
        villager: AbstractVillagerEntity,
        hand: Hand,
    ): BipedEntityModel.ArmPose {
        val itemStack = villager.getStackInHand(hand)
        if (itemStack.isEmpty) {
            return BipedEntityModel.ArmPose.EMPTY
        }
        if (villager.activeHand == hand && villager.itemUseTimeLeft > 0) {
            when (itemStack.useAction) {
                UseAction.BLOCK -> return BipedEntityModel.ArmPose.BLOCK
                UseAction.BOW -> return BipedEntityModel.ArmPose.BOW_AND_ARROW
                UseAction.SPEAR -> return BipedEntityModel.ArmPose.THROW_SPEAR
                UseAction.CROSSBOW -> if (hand == villager.activeHand) return BipedEntityModel.ArmPose.CROSSBOW_CHARGE
                UseAction.SPYGLASS -> return BipedEntityModel.ArmPose.SPYGLASS
                UseAction.TOOT_HORN -> return BipedEntityModel.ArmPose.TOOT_HORN
                UseAction.BRUSH -> return BipedEntityModel.ArmPose.BRUSH
                else -> {}
            }
        } else if (!villager.handSwinging && itemStack.isOf(Items.CROSSBOW) && CrossbowItem.isCharged(itemStack)) {
            return BipedEntityModel.ArmPose.CROSSBOW_HOLD
        }
        return BipedEntityModel.ArmPose.ITEM
    }

    private val TEXTURE = Identifier(MODID, "textures/entity/villager.png")

    override fun getTexture(entity: AbstractVillagerEntity): Identifier = TEXTURE

    override fun scale(
        entity: AbstractVillagerEntity,
        matrixStack: MatrixStack,
        f: Float,
    ) {
        matrixStack.scale(0.9375f, 0.9375f, 0.9375f)
    }
}
