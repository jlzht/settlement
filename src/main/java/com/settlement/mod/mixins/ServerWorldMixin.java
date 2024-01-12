package com.settlement.mod.mixin;

import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.world.PersistentStateManager;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.MinecraftServer;
import java.util.concurrent.Executor;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.level.storage.LevelStorage;
import com.settlement.mod.world.SettlementManager;
import net.minecraft.world.level.ServerWorldProperties;
import net.minecraft.server.WorldGenerationProgressListener;
import net.minecraft.world.spawner.SpecialSpawner;
import net.minecraft.util.math.random.RandomSequencesState;
import org.jetbrains.annotations.Nullable;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

@Mixin(ServerWorld.class)
public abstract class ServerWorldMixin {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(
        MinecraftServer server,
        Executor workerExecutor,
        LevelStorage.Session session,
        ServerWorldProperties properties,
        RegistryKey<World> worldKey,
        DimensionOptions dimensionOptions,
        WorldGenerationProgressListener worldGenerationProgressListener,
        boolean debugWorld,
        long seed,
        List<SpecialSpawner> spawners,
        boolean shouldTickTime,
        @Nullable RandomSequencesState randomSequencesState,
        CallbackInfo ci
    ) {

        ServerWorld world = (ServerWorld) (Object) this;
        SettlementManager.Companion.setWorld(world.getDimensionEntry(), world);
    }
}
