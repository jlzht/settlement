package com.settlement.mod

import com.settlement.mod.command.ModCommands
import com.settlement.mod.entity.mob.AbstractVillagerEntity
import com.settlement.mod.entity.projectile.SimpleFishingBobberEntity
import com.settlement.mod.item.ModItems
import com.settlement.mod.network.VillagerDebugPacket
import com.settlement.mod.screen.TradingScreenHandler
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricDefaultAttributeRegistry
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricEntityTypeBuilder
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType
import net.minecraft.entity.EntityDimensions
import net.minecraft.entity.EntityType
import net.minecraft.entity.SpawnGroup
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.util.Identifier
import org.slf4j.LoggerFactory

const val MODID = "settlement"
val LOGGER = LoggerFactory.getLogger(MODID)

object Settlement : ModInitializer {
    val VILLAGER: EntityType<AbstractVillagerEntity> =
        Registry.register(
            Registries.ENTITY_TYPE,
            Identifier(MODID, "villager"),
            FabricEntityTypeBuilder
                .create<AbstractVillagerEntity>(SpawnGroup.CREATURE, { type, world -> AbstractVillagerEntity(type, world) })
                .dimensions(EntityDimensions.fixed(0.6f, 1.95f))
                .build(),
        )

    val SIMPLE_FISHING_BOBBER: EntityType<SimpleFishingBobberEntity> =
        Registry.register(
            Registries.ENTITY_TYPE,
            Identifier(MODID, "simple_fishing_bobber"),
            FabricEntityTypeBuilder
                .create<SimpleFishingBobberEntity>(
                    SpawnGroup.MISC,
                    { type, world -> SimpleFishingBobberEntity(type, world, 0, 0) },
                ).disableSaving()
                .disableSummon()
                .dimensions(EntityDimensions.fixed(0.25f, 0.25f))
                .trackRangeBlocks(4)
                .trackedUpdateRate(10)
                .build(),
        )

    val TRADING_SCREEN_HANDLER: ScreenHandlerType<TradingScreenHandler> =
        Registry.register(
            Registries.SCREEN_HANDLER,
            Identifier(MODID, "villager_trading"),
            ExtendedScreenHandlerType({ syncId, playerInventory, buf -> TradingScreenHandler(syncId, playerInventory, buf) }),
        )

    override fun onInitialize() {
        ModCommands.register()
        ModItems.register()

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

        FabricDefaultAttributeRegistry.register(VILLAGER, AbstractVillagerEntity.createCustomVillagerAttributes())
    }
}
