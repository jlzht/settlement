package com.settlement.mod.client.render.armor

import com.settlement.mod.MODID
import com.settlement.mod.client.render.armor.model.StrawHatModel
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.OverlayTexture
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.entity.model.BipedEntityModel
import net.minecraft.client.render.entity.state.BipedEntityRenderState
import net.minecraft.client.render.entity.state.EntityRenderState
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.entity.EquipmentSlot
import net.minecraft.item.ItemStack
import net.minecraft.util.Identifier
import net.minecraft.util.math.RotationAxis

class StrawHatRenderer : ArmorRenderer {
    private var model: StrawHatModel<EntityRenderState>? = null

    override fun render(
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
        stack: ItemStack,
        entity: BipedEntityRenderState,
        slot: EquipmentSlot,
        light: Int,
        contextModel: BipedEntityModel<BipedEntityRenderState>,
    ) {
        if (slot != EquipmentSlot.HEAD) return
        if (model == null) {
            val modelPart = MinecraftClient.getInstance().getLoadedEntityModels().getModelPart(StrawHatModel.LAYER)
            model = StrawHatModel<EntityRenderState>(modelPart)
        } else {
            matrices.push()
            val head = contextModel.head

            matrices.translate(head.originX / 16.0f, head.originY / 16.0f, head.originZ / 16.0f)
            matrices.multiply(RotationAxis.POSITIVE_Z.rotation(head.roll))
            matrices.multiply(RotationAxis.POSITIVE_Y.rotation(head.yaw))
            matrices.multiply(RotationAxis.POSITIVE_X.rotation(head.pitch))
            matrices.translate(-head.originX / 16.0f, -head.originY / 16.0f, -head.originZ / 16.0f)
            if (entity.sneaking) {
                matrices.translate(0.0, -1.65, 0.0)
            } else {
                matrices.translate(0.0, -1.95, 0.0)
            }
            matrices.scale(1.1f, 1.1f, 1.1f)

            val consumer = vertexConsumers.getBuffer(RenderLayer.getEntityCutout(TEXTURE))
            model!!.render(matrices, consumer, light, OverlayTexture.DEFAULT_UV, -1)

            matrices.pop()
        }
    }

    companion object {
        val TEXTURE = Identifier.of(MODID, "textures/models/armor/straw_hat.png")
    }
}
