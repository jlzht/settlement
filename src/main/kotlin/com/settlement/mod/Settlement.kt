package com.settlement.mod

import com.settlement.mod.item.ModItems
import com.settlement.mod.block.entity.ModBlockEntities
import com.settlement.mod.entity.ModEntities
// import com.settlement.mod.client.item.VillageModelPredicateProviders
import com.settlement.mod.network.SettlementDebugDataPacket
import com.settlement.mod.network.VillagerDebugPacket
import com.settlement.mod.block.ModBlocks
import com.settlement.mod.command.ModCommands
import com.settlement.mod.component.ModComponentTypes
import com.settlement.mod.entity.mob.AbstractVillagerEntity
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import org.slf4j.LoggerFactory

const val MODID = "settlement"
val LOGGER = LoggerFactory.getLogger(MODID)

object Settlement : ModInitializer {
    override fun onInitialize() {
        ModComponentTypes.initialize()
        ModCommands.initialize()
        ModEntities.initialize()
        ModBlocks.initialize()
        ModBlockEntities.initialize()
        ModItems.initialize()

        PayloadTypeRegistry.playS2C().register(SettlementDebugDataPacket.ID, SettlementDebugDataPacket.CODEC)
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
    }
}
