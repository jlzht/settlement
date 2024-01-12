package com.settlement.mod.mixin;

import java.util.List;
import com.settlement.mod.world.SettlementManager;
import com.settlement.mod.world.Settlement;
import com.settlement.mod.item.ModItems;
import com.settlement.mod.screen.Response;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.block.BellBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.world.World;
import net.minecraft.block.BlockState;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;
import net.minecraft.util.ActionResult;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

@Mixin(BellBlock.class)
public abstract class BellBlockMixin {
    @Inject(method = "ring", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;incrementStat(Lnet/minecraft/util/Identifier;)V", shift = At.Shift.AFTER), cancellable = true)
    public void ring(World world, BlockState state, BlockHitResult hitResult, @Nullable PlayerEntity player, boolean checkHitPos, CallbackInfoReturnable<Boolean> cir) {
        if (player != null) {
            ItemStack itemStack = player.getMainHandStack();
            if (itemStack.isOf(Items.NAME_TAG) && itemStack.hasCustomName()) {
                Settlement settlement = SettlementManager.Companion.getInstance().addSettlement(itemStack.getName().getString(), hitResult.getBlockPos(), player);
                if (settlement != null) {
                    itemStack.decrement(1);
                } else {
                    cir.cancel();
                }
            } else if (itemStack.isOf(ModItems.HAND_BELL)) {
                SettlementManager manager = SettlementManager.Companion.getInstance();
                List<Settlement> settlements = manager.getSettlements();
                Settlement settlement = null;
                for (Settlement s : settlements) {
                    if (s.getPos().equals(hitResult.getBlockPos())) {
                        settlement = s;
                        break;
                    }
                }

                if (settlement != null) {
                    NbtCompound nbt = itemStack.getOrCreateNbt();
                    String currentBind = nbt.contains("Bind") ? nbt.getString("Bind") : "";
                    if (currentBind.equals(settlement.getName())) {
                        nbt.putString("Bind", "");
                        Response.UNBINDED_SETTLEMENT.send(player, settlement.getName());
                    } else if (currentBind.equals("")) {
                        nbt.putString("Bind", settlement.getName());
                        Response.BINDED_TO_SETTLEMENT.send(player, settlement.getName());
                    } else {
                        Response.ALREADY_BINDED_TO_A_SETTLEMENT.send(player);
                    }
                }
            }
        }
    }
}
