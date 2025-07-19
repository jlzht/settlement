package com.settlement.mod.client.render.entity.model

import com.settlement.mod.MODID
import com.settlement.mod.client.render.entity.AbstractVillagerEntityRenderState
import com.settlement.mod.entity.mob.State
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
import net.minecraft.entity.EntityPose
import net.minecraft.util.Arm
import net.minecraft.util.Identifier
import net.minecraft.util.math.MathHelper

@Environment(EnvType.CLIENT)
class AbstractVillagerEntityModel(
    root: ModelPart,
) : BipedEntityModel<AbstractVillagerEntityRenderState>(root) {
    override fun getHead(): ModelPart = head

    override fun setAngles(state: AbstractVillagerEntityRenderState) {
        super.setAngles(state)
        if (state.isInPose(EntityPose.SITTING)) {
            rightArm.pitch = -0.62831855f
            rightArm.yaw = 0.0f
            rightArm.roll = 0.0f
            leftArm.pitch = -0.62831855f
            leftArm.yaw = 0.0f
            leftArm.roll = 0.0f
            rightLeg.pitch = -1.4137167f
            rightLeg.yaw = 0.31415927f
            rightLeg.roll = 0.07853982f
            leftLeg.pitch = -1.4137167f
            leftLeg.yaw = -0.31415927f
            leftLeg.roll = -0.07853982f
        }

        if (state.isSwimming) {
            head.pitch = MathHelper.lerpAngleRadians(state.leaningPitch, this.head.pitch, (-0.7853982f / 4))
        } else {
            head.pitch = MathHelper.lerpAngleRadians(state.leaningPitch, this.head.pitch, (3.14159263f / 4))
        }

        when (state.action) {
            State.AGREE.ordinal -> {
                head.pitch = 0.3f * MathHelper.sin(0.25f * state.age)
            }
            State.DISAGREE.ordinal -> {
                head.roll = 0.3f * MathHelper.sin(0.45f * state.age)
                head.pitch = 0.4f
            }
            State.TALK.ordinal -> {}
            State.OFFER.ordinal -> {
                if (state.preferredArm == Arm.RIGHT) {
                    rightArm.pitch = -0.62831855f + MathHelper.sin(0.1f * state.age)
                } else {
                    leftArm.pitch = -0.62831855f + MathHelper.sin(0.1f * state.age)
                }
            }
            State.SWEAT.ordinal -> {}
            State.GREET.ordinal -> {
                leftArm.pitch += MathHelper.sin(0.45f * state.age)
            }
            State.CAST.ordinal -> {
                rightArm.originZ = 0.0F
                rightArm.originX = -5.0F
                leftArm.originZ = 0.0F
                leftArm.originX = 5.0F
                rightArm.pitch = MathHelper.cos(state.age * 0.6662F) * 0.25F
                leftArm.pitch = MathHelper.cos(state.age * 0.6662F) * 0.25F
                rightArm.roll = (Math.PI * 3.0 / 4.0).toFloat()
                leftArm.roll = (-Math.PI * 3.0 / 4.0).toFloat()
                rightArm.yaw = 0.0F
                leftArm.yaw = 0.0F
                leftArm.pitch += MathHelper.sin(0.45f * state.age)
            }

            else -> {}
        }
    }

    override fun setVisible(visible: Boolean) {
        super.setVisible(visible)
    }

    companion object Texture {
        val LAYER = EntityModelLayer(Identifier.of(MODID, "villager"), "main")
        val ARMOR_OUTER = EntityModelLayer(Identifier.of(MODID, "villager"), "armor_outer")
        val ARMOR_INNER = EntityModelLayer(Identifier.of(MODID, "villager"), "armor_inner")
        val ALT_LAYER = EntityModelLayer(Identifier.of(MODID, "alt_villager"), "main")
        val ALT_ARMOR_OUTER = EntityModelLayer(Identifier.of(MODID, "alt_villager"), "alt_armor_outer")
        val ALT_ARMOR_INNER = EntityModelLayer(Identifier.of(MODID, "alt_villager"), "alt_armor_inner")

        fun getModelData(dilation: Dilation): ModelData {
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
                ModelPartBuilder.create().uv(24, 0).cuboid(-1.0F, -1.0F, -6.0F, 2.0F, 4.0F, 2.0F, dilation),
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
                ModelPartBuilder.create().uv(0, 22).cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, dilation),
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
                    .cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, dilation)
                    .mirrored(false),
                ModelTransform.of(-2.0f, 12.0f, 0.0f, 0.0f, 0.0f, 0.0f),
            )
            modelPartData.addChild(
                "right_arm",
                ModelPartBuilder.create().uv(44, 22).cuboid(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, dilation),
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
                    .cuboid(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, dilation)
                    .mirrored(false),
                ModelTransform.origin(5.0f, 2.0f, 0.0f),
            )
            return model
        }

        fun getTexturedModelData(): TexturedModelData = TexturedModelData.of(getModelData(Dilation.NONE), 64, 64)

        fun getOuterArmorLayer(): TexturedModelData {
            val modelData = BipedEntityModel.getModelData(Dilation(1.25f), 0.0f)
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

        fun getAltTexturedModelData(): TexturedModelData = TexturedModelData.of(BipedEntityModel.getModelData(Dilation.NONE, 0.0f), 64, 64)

        fun getAltOuterArmorLayer(): TexturedModelData {
            val modelData = BipedEntityModel.getModelData(Dilation(1.25f), 0.0f)
            return TexturedModelData.of(modelData, 64, 32)
        }

        fun getAltInnerArmorLayer(): TexturedModelData {
            val modelData = BipedEntityModel.getModelData(Dilation(0.5f), 0.0f)
            return TexturedModelData.of(modelData, 64, 32)
        }
    }
}
