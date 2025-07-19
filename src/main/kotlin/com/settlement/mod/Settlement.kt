package com.settlement.mod

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import com.settlement.mod.item.ModItems
import com.settlement.mod.block.entity.ModBlockEntities
import com.settlement.mod.block.entity.EnchantedBellBlockEntity
import com.settlement.mod.entity.ModEntities
import net.fabricmc.fabric.api.resource.ResourceManagerHelper
// import com.settlement.mod.client.item.VillageModelPredicateProviders
import net.minecraft.resource.ResourceType
import com.settlement.mod.network.SettlementDebugDataPacket
import com.settlement.mod.network.ClientSyncModelPacket
import com.settlement.mod.network.VillagerDebugPacket
import com.settlement.mod.block.ModBlocks
import com.settlement.mod.command.ModCommands
import com.settlement.mod.component.ModComponentTypes
import com.settlement.mod.entity.mob.AbstractVillagerEntity
import com.settlement.mod.structure.Structure
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import org.slf4j.LoggerFactory

const val MODID = "settlement"
val LOGGER = LoggerFactory.getLogger(MODID)

object Settlement : ModInitializer {
    override fun onInitialize() {
        SettlementConfig.ensureFolders()

        // TODO: load config data from JSON

        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(ItemPriceManager)

        ModComponentTypes.initialize()
        ModCommands.initialize()
        ModEntities.initialize()
        ModBlocks.initialize()
        ModBlockEntities.initialize()
        ModItems.initialize()

        Structure.initialize()

        PayloadTypeRegistry.playS2C().register(SettlementDebugDataPacket.ID, SettlementDebugDataPacket.CODEC)
        PayloadTypeRegistry.playS2C().register(ClientSyncModelPacket.ID, ClientSyncModelPacket.CODEC)
        PayloadTypeRegistry.playS2C().register(VillagerDebugPacket.ID, VillagerDebugPacket.CODEC)

        // TODO: find a better way to make this auto-update
        ServerTickEvents.END_WORLD_TICK.register({ server ->
            server.players.forEach { player ->
                player
                    .getWorld()
                    .getOtherEntities(player, player.boundingBox.expand(16.0, 16.0, 16.0))
                    .filter { it is AbstractVillagerEntity }
                    .forEach { villager ->
                        val uuid = villager.getUuid()
                        (villager as AbstractVillagerEntity).getDebugData()?.let { list ->
                            VillagerDebugPacket.sendToClient(player, uuid, list)
                        }
                    }
            }
        })

        // tells to client which model to use
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            val config = SettlementConfig.useAlternativeModel
            val packet = ClientSyncModelPacket(config)
            ServerPlayNetworking.send(handler.player, packet)
        }
    }
}
