package com.settlement.mod.client.render.entity.model

import com.settlement.mod.action.ActionState
import com.settlement.mod.entity.mob.AbstractVillagerEntity
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.model.Dilation
import net.minecraft.client.model.ModelPart
import net.minecraft.client.model.ModelPartBuilder
import net.minecraft.client.model.ModelPartData
import net.minecraft.client.model.ModelTransform
import net.minecraft.client.model.TexturedModelData
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.render.entity.model.BipedEntityModel
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.Arm
import net.minecraft.util.math.MathHelper

@Environment(EnvType.CLIENT)
class AbstractVillagerEntityModel(
    private val root: ModelPart,
) : BipedEntityModel<AbstractVillagerEntity>(root) {
    private var sitOffset = false

    init {
        hat.visible = false
    }

    companion object {
        fun getTexturedModelData(): TexturedModelData {
            val meshdefinition = BipedEntityModel.getModelData(Dilation.NONE, 0.0F)
            var modelPartData: ModelPartData = meshdefinition.getRoot()
            val head: ModelPartData =
                modelPartData.addChild(
                    "head",
                    ModelPartBuilder.create().uv(0, 0).cuboid(-4.0F, -10.0F, -4.0F, 8.0F, 10.0F, 8.0F),
                    ModelTransform.NONE,
                )
            head.addChild(
                "nose",
                ModelPartBuilder.create().uv(24, 0).cuboid(-1.0F, -1.0F, -6.0F, 2.0F, 4.0F, 2.0F, Dilation(0.0F)),
                ModelTransform.pivot(0.0F, -2.0F, 0.0F),
            )
            modelPartData.addChild(
                "hat",
                ModelPartBuilder.create().uv(0, 0).cuboid(-4.5F, -11.0F, -4.5F, 9F, 11F, 9F, Dilation(0.0F)),
                ModelTransform.pivot(0.0F, 0.0F, 0.0F),
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
                ModelTransform.pivot(0.0F, 0.0F, 0.0F),
            )
            modelPartData.addChild(
                "left_leg",
                ModelPartBuilder.create().uv(0, 22).cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, Dilation(0.0F)),
                ModelTransform.pivot(2.0F, 12.0F, 0.0F),
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
                ModelTransform.pivot(-2.0F, 12.0F, 0.0F),
            )
            modelPartData.addChild(
                "right_arm",
                ModelPartBuilder.create().uv(44, 22).cuboid(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, Dilation(0.0F)),
                ModelTransform.pivot(-5.0F, 2.0F, 0.0F),
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
                ModelTransform.pivot(5.0F, 2.0F, 0.0F),
            )
            return TexturedModelData.of(meshdefinition, 64, 64)
        }

        fun getOuterArmorLayer(): TexturedModelData {
            val modelData = BipedEntityModel.getModelData(Dilation(1.0f), 0.0f)
            val modelPartData = modelData.getRoot()
            modelPartData.addChild(
                "head",
                ModelPartBuilder.create().uv(0, 0).cuboid(-4.0f, -10.0f, -4.0f, 8.0f, 10.0f, 8.0f, Dilation(0.5F)),
                ModelTransform.pivot(0.0f, 1.0f, 0.0f),
            )
            return TexturedModelData.of(modelData, 64, 32)
        }

        fun getInnerArmorLayer(): TexturedModelData {
            val modelData = BipedEntityModel.getModelData(Dilation(0.5f), 0.0f)
            return TexturedModelData.of(modelData, 64, 32)
        }
    }

    override fun getHead(): ModelPart = head

    override fun setArmAngle(
        arm: Arm,
        matrices: MatrixStack,
    ) {
        getArm(arm).rotate(matrices)
    }

    override fun setAngles(
        entity: AbstractVillagerEntity,
        f: Float,
        g: Float,
        h: Float,
        i: Float,
        j: Float,
    ) {
        super.setAngles(entity, f, g, h, i, j)
        if (entity.isSitting()) {
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
            sitOffset = true
        } else {
            sitOffset = false
        }

        if (entity.isSwimming()) {
            this.lerpAngle(this.leaningPitch, this.head.pitch, -0.7853982f / 4)
        } else {
            this.lerpAngle(this.leaningPitch, this.head.pitch, j * ((Math.PI.toFloat()) / 180))
        }

        this.head.yaw = i * (Math.PI / 180).toFloat()
        this.head.pitch = j * (Math.PI / 180).toFloat()
        when (entity.getState()) {
            ActionState.DISAGREE.ordinal -> {
                this.head.roll = 0.3f * MathHelper.sin(0.45f * h)
                this.head.pitch = 0.4f
            }
            ActionState.AGREE.ordinal -> {
                this.head.pitch = 0.3f * MathHelper.sin(0.25f * h)
            }
            ActionState.TALK.ordinal -> {
            }
            ActionState.OFFER.ordinal -> { }
            ActionState.SWEAT.ordinal -> { }
            else -> {
                this.head.roll = 0.0f
            }
        }
    }

    override fun render(
        matrices: MatrixStack,
        vertices: VertexConsumer,
        light: Int,
        overlay: Int,
        r: Float,
        g: Float,
        b: Float,
        a: Float,
    ) {
        if (sitOffset) {
            matrices.translate(0.0, 0.6, 0.0)
        }
        super.render(matrices, vertices, light, overlay, r, g, b, a)
    }
}
