package com.settlement.mod.mixin;

import net.minecraft.entity.ai.goal.GoalSelector;
import com.settlement.mod.entity.mob.AbstractVillagerEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.world.World;
import net.minecraft.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.entity.mob.MobEntity;

import net.minecraft.entity.ai.goal.ActiveTargetGoal;

import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.VindicatorEntity;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.IllagerEntity;
import net.minecraft.entity.mob.VexEntity;
import net.minecraft.entity.mob.VindicatorEntity;
import net.minecraft.entity.mob.WitchEntity;
import net.minecraft.entity.mob.RavagerEntity;
import net.minecraft.entity.mob.IllusionerEntity;
import net.minecraft.entity.mob.PatrolEntity;
import net.minecraft.entity.mob.PillagerEntity;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mixin({
        AbstractSkeletonEntity.class,
        ZombieEntity.class,
        IllagerEntity.class,
        IllusionerEntity.class,
        PatrolEntity.class,
        PillagerEntity.class,
        RavagerEntity.class,
        VexEntity.class,
        VindicatorEntity.class,
        WitchEntity.class
})
public abstract class ZombieEntityMixin extends HostileEntity {
        protected ZombieEntityMixin(EntityType<? extends ZombieEntity> entityType, World world) {
                super(entityType, world);
        }
        @Inject(method = "initGoals", at = @At("TAIL"))
	      private void initGoals(CallbackInfo info) {
          this.targetSelector.add(3, new ActiveTargetGoal<AbstractVillagerEntity>(this, AbstractVillagerEntity.class, true));
        }
}
