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
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mixin(ZombieEntity.class)
public abstract class ZombieEntityMixin extends HostileEntity {
        protected ZombieEntityMixin(EntityType<? extends ZombieEntity> entityType, World world) {
                super(entityType, world);
        }
        @Inject(method = "Lnet/minecraft/entity/mob/ZombieEntity;initCustomGoals()V", at = @At("HEAD"), cancellable = true)
	      private void initGoals(CallbackInfo info) {
          this.targetSelector.add(3, new ActiveTargetGoal<AbstractVillagerEntity>(this, AbstractVillagerEntity.class, true));
        }
}
