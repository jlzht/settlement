package com.settlement.mod.command

import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.settlement.mod.network.SettlementDebugDataPacket
import com.settlement.mod.world.SettlementManager
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text

object  ModCommands {
    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                LiteralArgumentBuilder
                    .literal<ServerCommandSource>("settlement")
                    .then(
                        LiteralArgumentBuilder
                            .literal<ServerCommandSource>("debug")
                            .then(
                                RequiredArgumentBuilder
                                    .argument<ServerCommandSource, Boolean>("state", BoolArgumentType.bool())
                                    .executes { context ->
                                        context.source.player?.let { player ->
                                            val state = BoolArgumentType.getBool(context, "state")
                                            SettlementManager.findNearestSettlement(player)?.let { settlement ->
                                                if (state) {
                                                    SettlementDebugDataPacket.sendToClient(player, settlement.getDebugData())
                                                    context.source.sendMessage(
                                                        Text.literal(
                                                            "Enabled rendering of debug overlay for settlement: ${settlement.name}",
                                                        ),
                                                    )
                                                } else {
                                                    context.source.sendMessage(
                                                        Text.literal(
                                                            "Disabled rendering of debug overlay for settlement: ${settlement.name}",
                                                        ),
                                                    )
                                                }
                                            }
                                            1
                                        } ?: run {
                                            context.source.sendError(Text.literal("Command must be executed by a player."))
                                            0
                                        }
                                    },
                            ),
                    ),
            )
        }
    }
}
