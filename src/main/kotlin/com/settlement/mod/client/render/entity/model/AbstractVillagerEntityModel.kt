package com.settlement.mod.client.render.entity.model

import com.settlement.mod.MODID
import com.settlement.mod.client.render.entity.AbstractVillagerEntityRenderState
import com.settlement.mod.entity.mob.AbstractVillagerEntity
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.model.Dilation
import net.minecraft.client.model.ModelData
import net.minecraft.client.model.ModelPart
import net.minecraft.client.model.ModelPartBuilder
import net.minecraft.client.model.ModelPartData
import net.minecraft.client.model.ModelTransform
import net.minecraft.client.model.TexturedModelData
import net.minecraft.client.render.entity.model.BipedEntityModel
import net.minecraft.client.render.entity.model.EntityModelLayer
import net.minecraft.util.Identifier
import net.minecraft.util.math.MathHelper

@Environment(EnvType.CLIENT)
class AbstractVillagerEntityModel<T : AbstractVillagerEntityRenderState>(
    root: ModelPart,
) : BipedEntityModel<T>(root) {
    init {
        hat.visible = false
    }

    companion object {
        val LAYER = EntityModelLayer(Identifier.of(MODID, "villager"), "main")
        val ARMOR_OUTER = EntityModelLayer(Identifier.of(MODID, "villager"), "armor_outer")
        val ARMOR_INNER = EntityModelLayer(Identifier.of(MODID, "villager"), "armor_inner")

        fun getTexturedModelData(): TexturedModelData {
            val model = ModelData()
            var modelPartData: ModelPartData = model.getRoot()
            val head: ModelPartData =
                modelPartData.addChild(
                    "head",
                    ModelPartBuilder.create().uv(0, 0).cuboid(-4.0F, -10.0F, -4.0F, 8.0F, 10.0F, 8.0F),
                    ModelTransform.NONE,
                )
            head.addChild(
                "nose",
                ModelPartBuilder.create().uv(24, 0).cuboid(-1.0F, -1.0F, -6.0F, 2.0F, 4.0F, 2.0F, Dilation(0.0F)),
                ModelTransform.of(0.0f, -2.0f, 0.0f, 0.0f, 0.0f, 0.0f),
            )
            head.addChild(
                "hat",
                ModelPartBuilder
                    .create()
                    .uv(0, 0)
                    .cuboid(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f),
                ModelTransform.NONE,
            )
            modelPartData.addChild(
                "body",
                ModelPartBuilder
                    .create()
                    .uv(
                        16,
                        20,
                    ).cuboid(-4.0F, 0.0F, -3.0F, 8.0F, 12.0F, 6.0F)
                    .uv(0, 38)
                    .cuboid(-4.0f, 0.0f, -3.0f, 8.0f, 20.0f, 6.0f, Dilation(0.5F)),
                ModelTransform.of(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f),
            )
            modelPartData.addChild(
                "left_leg",
                ModelPartBuilder.create().uv(0, 22).cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, Dilation(0.0F)),
                ModelTransform.origin(2.0f, 12.0f, 0.0f),
            )
            modelPartData.addChild(
                "right_leg",
                ModelPartBuilder
                    .create()
                    .uv(
                        0,
                        22,
                    ).mirrored()
                    .cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, Dilation(0.0F))
                    .mirrored(false),
                ModelTransform.of(-2.0f, 12.0f, 0.0f, 0.0f, 0.0f, 0.0f),
            )
            modelPartData.addChild(
                "right_arm",
                ModelPartBuilder.create().uv(44, 22).cuboid(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, Dilation(0.0F)),
                ModelTransform.origin(-5.0f, 2.0f, 0.0f),
            )
            modelPartData.addChild(
                "left_arm",
                ModelPartBuilder
                    .create()
                    .uv(
                        44,
                        22,
                    ).mirrored()
                    .cuboid(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, Dilation(0.0F))
                    .mirrored(false),
                ModelTransform.origin(5.0f, 2.0f, 0.0f),
            )
            return TexturedModelData.of(model, 64, 64)
        }

        fun getOuterArmorLayer(): TexturedModelData {
            val modelData = BipedEntityModel.getModelData(Dilation(1.0f), 0.0f)
            val modelPartData = modelData.getRoot()
            modelPartData
                .addChild(
                    "head",
                )
            modelPartData.addChild(
                "head",
                ModelPartBuilder.create().uv(0, 0).cuboid(-4.0f, -10.0f, -4.0f, 8.0f, 8.0f, 8.0f, Dilation(0.5f)),
                ModelTransform.of(0.0f, 0.0f, 0.0f, 0f, 0f, 0f),
            )
            return TexturedModelData.of(modelData, 64, 32)
        }

        fun getInnerArmorLayer(): TexturedModelData {
            val modelData = BipedEntityModel.getModelData(Dilation(1.0f), 0.0f)
            val modelPartData = modelData.getRoot()
            modelPartData
                .addChild(
                    "head",
                    ModelPartBuilder.create().uv(0, 0).cuboid(-4.0f, -10.0f, -4.0f, 8.0f, 8.0f, 8.0f, Dilation(0.5f)),
                    ModelTransform.of(0.0f, 0.0f, 0.0f, 0f, 0f, 0f),
                )
            return TexturedModelData.of(modelData, 64, 32)
        }
    }

    override fun getHead(): ModelPart = head

    override fun setAngles(state: T) {
        super.setAngles(state)
        if (state.isSitting) {
            this.rightArm.pitch = -0.62831855f
            this.rightArm.yaw = 0.0f
            this.rightArm.roll = 0.0f
            this.leftArm.pitch = -0.62831855f
            this.leftArm.yaw = 0.0f
            this.leftArm.roll = 0.0f
            this.rightLeg.pitch = -1.4137167f
            this.rightLeg.yaw = 0.31415927f
            this.rightLeg.roll = 0.07853982f
            this.leftLeg.pitch = -1.4137167f
            this.leftLeg.yaw = -0.31415927f
            this.leftLeg.roll = -0.07853982f
        }

        if (state.isSwimming) {
            this.head.pitch = MathHelper.lerpAngleRadians(state.leaningPitch, this.head.pitch, (-0.7853982f / 4))
        } else {
            this.head.pitch = MathHelper.lerpAngleRadians(state.leaningPitch, this.head.pitch, (3.14159263f / 4))
        }

        when (state.action) {
            AbstractVillagerEntity.Companion.State.AGREE -> {
                this.head.pitch = 0.3f * MathHelper.sin(0.25f * state.age)
            }
            AbstractVillagerEntity.Companion.State.DISAGREE -> {
                this.head.roll = 0.3f * MathHelper.sin(0.45f * state.age)
                this.head.pitch = 0.4f
            }
            AbstractVillagerEntity.Companion.State.TALK -> {}
            AbstractVillagerEntity.Companion.State.OFFER -> {
                this.leftArm.pitch = -0.62831855f + MathHelper.sin(0.05f * state.age)
            }
            AbstractVillagerEntity.Companion.State.SWEAT -> {}
            AbstractVillagerEntity.Companion.State.GREET -> {
                this.leftArm.pitch += MathHelper.sin(0.45f * state.age)
            }
            else -> {}
        }
    }
}
