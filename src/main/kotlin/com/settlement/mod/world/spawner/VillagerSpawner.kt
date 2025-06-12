package com.settlement.mod.world.spawner

import com.settlement.mod.entity.ModEntities
import com.settlement.mod.entity.mob.AbstractVillagerEntity
import net.minecraft.entity.SpawnReason
import net.minecraft.registry.tag.BiomeTags
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.world.GameRules
import net.minecraft.world.Heightmap
import net.minecraft.world.spawner.SpecialSpawner

class VillagerSpawner : SpecialSpawner {
    private var cooldown = 0

    override fun spawn(
        world: ServerWorld,
        spawnMonsters: Boolean,
        spawnAnimals: Boolean,
    ) {
        if (!world.gameRules.getBoolean(GameRules.DO_PATROL_SPAWNING)) {
            return
        }

        val random = world.random
        cooldown--
        if (cooldown > 0) return

        if (!world.isDay) {
            cooldown += 2000 + random.nextInt(4000)
            return
        }

        cooldown += 3000

        val playerCount = world.players.size
        if (playerCount < 1) return
        if (random.nextInt(2) != 0) return

        val player = world.players[random.nextInt(playerCount)]
        if (player.isSpectator) return

        val dx = (72 + random.nextInt(24)) * if (random.nextBoolean()) -1 else 1
        val dz = (72 + random.nextInt(24)) * if (random.nextBoolean()) -1 else 1

        val pos = player.blockPos.mutableCopy().move(dx, 0, dz)
        val radius = 10

        if (!world.isRegionLoaded(pos.x - radius, pos.z - radius, pos.x + radius, pos.z + radius)) {
            return
        }

        val biome = world.getBiome(pos)
        if (biome.isIn(BiomeTags.IS_OCEAN)) return

        var attempts = 0
        for (p in 0 until 3) {
            pos.setY(world.getTopPosition(Heightmap.Type.WORLD_SURFACE, pos).y)
            spawnVillager(world, pos)
            pos.x += random.nextInt(5) - random.nextInt(5)
            pos.z += random.nextInt(5) - random.nextInt(5)
            attempts++
            if (random.nextInt(2 * attempts) != 0) break
        }
    }

    private fun spawnVillager(
        world: ServerWorld,
        pos: BlockPos,
    ): Boolean {
        val entity: AbstractVillagerEntity? = ModEntities.VILLAGER.create(world, SpawnReason.EVENT)
        if (entity != null) {
            entity.setPosition(pos.getX().toDouble(), pos.getY().toDouble(), pos.getZ().toDouble())
            entity.initialize(world, world.getLocalDifficulty(pos), SpawnReason.EVENT, null)
            world.spawnEntityAndPassengers(entity)
            return true
        }
        return false
    }
}
