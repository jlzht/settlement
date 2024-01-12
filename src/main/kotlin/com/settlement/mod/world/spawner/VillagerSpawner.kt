package com.settlement.mod.world.spawner

import com.settlement.mod.Settlement
import com.settlement.mod.entity.mob.AbstractVillagerEntity
import net.minecraft.entity.SpawnReason
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.registry.tag.BiomeTags
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.random.Random
import net.minecraft.world.GameRules
import net.minecraft.world.Heightmap
import net.minecraft.world.biome.Biome
import net.minecraft.world.spawner.SpecialSpawner

class VillagerSpawner : SpecialSpawner {
    private var cooldown = 0

    override fun spawn(
        world: ServerWorld,
        spawnMonsters: Boolean,
        spawnAnimals: Boolean,
    ): Int {
        if (!world.gameRules.getBoolean(GameRules.DO_PATROL_SPAWNING)) {
            return 0
        }
        val random: Random = world.random
        cooldown--
        if (cooldown > 0) {
            return 0
        }
        if (!world.isDay) {
            cooldown += 2000 + random.nextInt(4000)
            return 0
        }
        cooldown += 3000

        val i: Int = world.players.size
        if (i < 1) {
            return 0
        }
        if (random.nextInt(2) != 0) {
            return 0
        }

        val playerEntity: PlayerEntity = world.players[random.nextInt(i)]
        if (playerEntity.isSpectator) {
            return 0
        }
        val j: Int = (72 + random.nextInt(24)) * if (random.nextBoolean()) -1 else 1
        val k: Int = (72 + random.nextInt(24)) * if (random.nextBoolean()) -1 else 1
        val mutable: BlockPos.Mutable = playerEntity.blockPos.mutableCopy().move(j, 0, k)
        val m: Int = 10
        if (!world.isRegionLoaded(mutable.x - m, mutable.z - m, mutable.x + m, mutable.z + m)) {
            return 0
        }

        val registryEntry: RegistryEntry<Biome> = world.getBiome(mutable)
        if (registryEntry.isIn(BiomeTags.IS_OCEAN)) {
            return 0
        }
        var n: Int = 0
        for (p in 0 until 3) {
            mutable.setY(world.getTopPosition(Heightmap.Type.WORLD_SURFACE, mutable).getY())
            if (!spawnVillager(world, mutable)) {
            }
            mutable.setX(mutable.getX() + random.nextInt(5) - random.nextInt(5))
            mutable.setZ(mutable.getZ() + random.nextInt(5) - random.nextInt(5))
            n++
            if (random.nextInt(2 * n) != 0) {
                break
            }
        }
        return n
    }

    private fun spawnVillager(
        world: ServerWorld,
        pos: BlockPos,
    ): Boolean {
        val entity: AbstractVillagerEntity? = Settlement.VILLAGER.create(world)
        if (entity != null) {
            entity.setPosition(pos.getX().toDouble(), pos.getY().toDouble(), pos.getZ().toDouble())
            entity.initialize(world, world.getLocalDifficulty(pos), SpawnReason.EVENT, null, null)
            world.spawnEntityAndPassengers(entity)
            return true
        }
        return false
    }
}
