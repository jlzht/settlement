package com.settlement.mod

import com.settlement.mod.screen.ModScreens
import com.settlement.mod.item.ModItems
import com.settlement.mod.client.render.debug.SettlementDebugRenderer
import com.settlement.mod.entity.ModEntities
import com.settlement.mod.client.render.debug.VillagerDebugRenderer
// import com.settlement.mod.client.item.VillageModelPredicateProviders
import com.settlement.mod.network.SettlementDebugDataPacket
import com.settlement.mod.network.VillagerDebugPacket
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.MinecraftClient

@Environment(EnvType.CLIENT)
object SettlementClient : ClientModInitializer {
    override fun onInitializeClient() {
        ModScreens.initializeClient()
        ModEntities.initializeClient()
        ModItems.initializeClient()

        this.registerDebugRenderer()
    }

    private fun registerDebugRenderer() {
        val instance = MinecraftClient.getInstance()
        val settlementDebugRenderer = SettlementDebugRenderer(instance)
        val villagerDebugRenderer = VillagerDebugRenderer(instance)

        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(settlementDebugRenderer)
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(villagerDebugRenderer)

        ClientPlayNetworking.registerGlobalReceiver(SettlementDebugDataPacket.ID) { payload, context ->
            context.client().execute {
                settlementDebugRenderer.updateDebugData(payload.data)
            }
        }
        ClientPlayNetworking.registerGlobalReceiver(VillagerDebugPacket.ID) { payload, context ->
            context.client().execute {
                villagerDebugRenderer.updateDebugData(payload.data)
            }
        }
    }
}
