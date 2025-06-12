package com.settlement.mod.item

import com.settlement.mod.MODID
import com.settlement.mod.client.render.armor.StrawHatRenderer
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents
import net.minecraft.item.Item
import net.minecraft.item.ItemGroups
import net.minecraft.item.Items
import net.minecraft.item.equipment.ArmorMaterial
import net.minecraft.item.equipment.EquipmentAssetKeys
import net.minecraft.item.equipment.EquipmentType
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.tag.ItemTags
import net.minecraft.sound.SoundEvents
import net.minecraft.util.Identifier

object ModItems {
    val EMERALD_TOKEN = register("emerald_token", ::Item, Item.Settings())
    val EMERALD_BUNDLE = register("emerald_bundle", ::Item, Item.Settings())

    @JvmField
    val HAND_BELL = register("hand_bell", ::HandBellItem, Item.Settings().maxDamage(64))

    val WHEAT_HAT =
        register(
            "wheat_hat",
            ::Item,
            Item.Settings().armor(ModArmorMaterials.WHEAT, EquipmentType.HELMET),
        )

    fun initialize() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register {
            it.add(EMERALD_TOKEN)
            it.add(EMERALD_BUNDLE)
        }

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register {
            it.add(HAND_BELL)
        }

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register {
            it.add(WHEAT_HAT)
        }
    }

    fun initializeClient() {
        ArmorRenderer.register(StrawHatRenderer(), WHEAT_HAT)
    }

    private fun <T : Item> register(
        id: String,
        constructor: (Item.Settings) -> T,
        settings: Item.Settings,
    ): Item =
        Items.register(
            RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MODID, id)),
            constructor,
            settings,
        )
}

object ModArmorMaterials {
    val WHEAT =
        ArmorMaterial(
            4,
            mapOf(
                EquipmentType.HELMET to 3,
                EquipmentType.CHESTPLATE to 8,
                EquipmentType.LEGGINGS to 6,
                EquipmentType.BOOTS to 3,
            ),
            10,
            SoundEvents.ITEM_ARMOR_EQUIP_WOLF,
            0.0f,
            0.0f,
            ItemTags.REPAIRS_LEATHER_ARMOR,
            RegistryKey.of(EquipmentAssetKeys.REGISTRY_KEY, Identifier.of(MODID, "wheat")),
        )
}
