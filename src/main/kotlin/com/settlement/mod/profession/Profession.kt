package com.settlement.mod.profession

import com.settlement.mod.action.Action
import com.settlement.mod.entity.mob.EntityController
import com.settlement.mod.registry.tag.ModItemTags
import com.settlement.mod.screen.TradingScreenHandler
import com.settlement.mod.structure.Structure
import com.settlement.mod.util.BlockUtils
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.item.Item
import net.minecraft.registry.tag.ItemTags
import net.minecraft.registry.tag.TagKey
import net.minecraft.screen.ScreenHandler

sealed class Profession {
    abstract val type: Type

    abstract val tags: Array<TagKey<Item>>

    open val home: Structure.Type = Structure.Type.HOUSE
    open val free: Structure.Type = Structure.Type.CAMPFIRE // make a randomized getter
    open val work: Structure.Type = Structure.Type.NONE

    open fun getScreenFactory(): ((syncId: Int, inv: PlayerInventory) -> ScreenHandler)? = null

    // this will be used to make entity interact with its surroundings
    open fun blockHandler(ctrl: EntityController) {}

    open fun hostileHandler(ctrl: EntityController) {
        ctrl.target?.let {
            BlockUtils
                .findFleeBlock(ctrl.entity, it)
                ?.let { pos -> ctrl.pushErrand(Action.Type.FLEE, pos) }
        }
    }

    open fun neutralHandler(ctrl: EntityController) {
        ctrl.target?.let { target ->
            if (ctrl.random.nextInt(8000) == 0) {
                ctrl.pushErrand(Action.Type.YIELD)
            }
            // add chance to look instead of always look
            ctrl.pushErrand(Action.Type.LOOK)
        }
    }

    enum class Type(
        val instance: Profession,
    ) {
        UNEMPLOYED(Unemployed),
        GATHERER(Gatherer),
        HUNTER(Hunter),
        FARMER(Farmer),
        LUMBERJACK(Lumberjack),
        FISHERMAN(Fisherman),
        CHEF(Chef),
        SHEPHERD(Shepherd),
        MINER(Miner),
        TRAVELLER(Traveller),
        MERCHANT(Merchant),
        RECRUIT(Recruit),
        GUARD(Guard),
        CLERIC(Cleric),
    }
}

object Unemployed : Profession() {
    override val type = Profession.Type.UNEMPLOYED
    override val tags =
        arrayOf(
            ModItemTags.FOODS,
        )
}

object Gatherer : Profession() {
    override val type = Profession.Type.GATHERER
    override val tags =
        arrayOf(
            ModItemTags.FOODS,
        )
    override val home = Structure.Type.NONE
}

object Traveller : Profession() {
    override val type = Profession.Type.TRAVELLER
    override val tags =
        arrayOf(
            ModItemTags.FOODS,
        )
    override val home = Structure.Type.NONE
    override val work = Structure.Type.NONE
}

object Hunter : Profession() {
    override val type = Profession.Type.HUNTER
    override val tags =
        arrayOf(
            ModItemTags.FOODS,
            ModItemTags.BASIC_COMBAT,
        )
    override val home = Structure.Type.NONE
    override val work = Structure.Type.KITCHEN
}

object Farmer : Profession() {
    override val type = Profession.Type.FARMER
    override val tags =
        arrayOf(
            ModItemTags.FOODS,
            ModItemTags.HOES,
            ModItemTags.SEEDS,
            ModItemTags.FERTILIZERS,
        )
    override val work = Structure.Type.FARM
}

object Lumberjack : Profession() {
    override val type = Profession.Type.LUMBERJACK
    override val tags =
        arrayOf(
            ModItemTags.FOODS,
        )
    override val work = Structure.Type.TREE
}

object Fisherman : Profession() {
    override val type = Profession.Type.FISHERMAN
    override val tags =
        arrayOf(
            ModItemTags.FOODS,
            ModItemTags.RODS,
            ModItemTags.FISHES,
        )
    override val work = Structure.Type.POND
}

object Chef : Profession() {
    override val type = Profession.Type.CHEF
    override val tags =
        arrayOf(
            ModItemTags.FOODS,
            ModItemTags.COOKABLES,
            ModItemTags.FUELS,
        )
    override val work = Structure.Type.KITCHEN
}

object Shepherd : Profession() {
    override val type = Profession.Type.SHEPHERD
    override val tags =
        arrayOf(
            ModItemTags.FOODS,
        )
    override val work = Structure.Type.PEN

    override fun neutralHandler(ctrl: EntityController) {
        if (!ctrl.pushErrand(Action.Type.SHEAR)) {
            super.neutralHandler(ctrl)
        }
    }
}

object Miner : Profession() {
    override val type = Profession.Type.MINER
    override val tags =
        arrayOf(
            ModItemTags.FOODS,
            ModItemTags.ORES,
            ItemTags.PICKAXES,
            ItemTags.SHOVELS,
        )
    override val work = Structure.Type.TUNNEL
}

object Merchant : Profession() {
    override val type = Profession.Type.MERCHANT
    override val tags = arrayOf(ModItemTags.FOODS)

    override fun getScreenFactory(): ((syncId: Int, inv: PlayerInventory) -> ScreenHandler)? = ::TradingScreenHandler
}

object Recruit : Profession() {
    override val type = Profession.Type.RECRUIT
    override val tags =
        arrayOf(
            ModItemTags.FOODS,
            ModItemTags.BASIC_COMBAT,
        )
    override val home = Structure.Type.BARRACK

    override fun hostileHandler(ctrl: EntityController) {
        ctrl.target?.let {
            ctrl.pushErrand(Action.Type.ATTACK)
            super.hostileHandler(ctrl)
        }
        // recruits always attack first, then fallback to fleeing
    }
}

object Guard : Profession() {
    override val type = Profession.Type.GUARD
    override val tags =
        arrayOf(
            ModItemTags.FOODS,
            ModItemTags.ARMORS,
            ModItemTags.ADVANCED_COMBAT,
        )
    override val home = Structure.Type.BARRACK

    override fun hostileHandler(ctrl: EntityController) {
        ctrl.target?.let {
            val dist = ctrl.getDistanceTo(it)
            when {
                dist >= 18f ->
                    listOf(
                        Action.Type.CHARGE,
                        Action.Type.AIM,
                        Action.Type.ATTACK,
                    ).any { ctrl.pushErrand(it) }

                dist < 4f -> ctrl.pushErrand(Action.Type.DASH)

                else -> {
                    if (dist < 6f && !ctrl.containsErrand(Action.Type.STRAFE)) {
                        ctrl.pushErrand(Action.Type.STRAFE)
                    }

                    if (ctrl.random.nextInt(3) != 0 ||
                        !ctrl.pushErrand(Action.Type.DEFEND)
                    ) {
                        ctrl.pushErrand(Action.Type.ATTACK)
                    }
                }
            }
            super.hostileHandler(ctrl)
        }
    }
}

object Cleric : Profession() {
    override val type = Profession.Type.CLERIC
    override val tags =
        arrayOf(
            ModItemTags.FOODS,
        )
    override val home
        get() = arrayOf(Structure.Type.BARRACK, Structure.Type.HOUSE).random()

    override fun hostileHandler(ctrl: EntityController) {
        ctrl.target?.let { tgt ->
            if (ctrl.getDistanceTo(tgt) >= 15f) {
                listOf(Action.Type.CAST, Action.Type.THROW)
                    .forEach { ctrl.pushErrand(it, null) }
            }
            super.hostileHandler(ctrl)
        }
    }
}
