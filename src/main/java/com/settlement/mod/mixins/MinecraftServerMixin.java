package com.settlement.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import java.util.function.BooleanSupplier;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import com.google.common.collect.ImmutableList;
import net.minecraft.world.spawner.SpecialSpawner;
import net.minecraft.world.level.ServerWorldProperties;
import net.minecraft.village.ZombieSiegeManager;
import net.minecraft.world.spawner.PatrolSpawner;
import net.minecraft.world.spawner.PhantomSpawner;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.registry.Registry;
import com.settlement.mod.world.spawner.VillagerSpawner;
import com.settlement.mod.world.SettlementManager;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.server.WorldGenerationProgressListener;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {

  @Unique private long ticksUntilSettlementsUpdate = 100;
  // I just forgot about this
  @Redirect(method = "createWorlds", remap = false, at = @At( value = "INVOKE", target = "com/google/common/collect/ImmutableList", ordinal = 0))
  private <T> ImmutableList<SpecialSpawner> coolSpawner(T a, T b, T c, T e, T d) {
      // WanderingTraderManager, CatSpawner and PatrolSpawner will not be needed
      return ImmutableList.of( new PatrolSpawner(), new VillagerSpawner(), new PatrolSpawner(), new ZombieSiegeManager(), new PatrolSpawner() );
  }

  @Inject(method = "createWorlds", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;initScoreboard(Lnet/minecraft/world/PersistentStateManager;)V", shift = At.Shift.AFTER), locals = LocalCapture.CAPTURE_FAILHARD)
  private void initSettlementManager(WorldGenerationProgressListener worldGenerationProgressListener, CallbackInfo ci, @Local PersistentStateManager persistentStateManager) {
      SettlementManager settlementManager = persistentStateManager.getOrCreate(SettlementManager.getPersistentStateType(), "settlements");
      SettlementManager.Companion.setInstance(settlementManager);
  }

  @Inject(method = "tickWorlds", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;getWorlds()Ljava/lang/Iterable;", shift = At.Shift.AFTER))
  private void tickSettlements(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
      if (--this.ticksUntilSettlementsUpdate == 0L) {
          SettlementManager.Companion.tick();
          ticksUntilSettlementsUpdate = 100;
      }
  }
}
