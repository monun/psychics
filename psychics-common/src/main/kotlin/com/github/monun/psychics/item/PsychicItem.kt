package com.github.monun.psychics.item

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.Component.space
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import java.util.*

object PsychicItem {
    val boundTag =
        text().content("능력귀속").decoration(TextDecoration.ITALIC, false).decorate(TextDecoration.BOLD)
            .color(NamedTextColor.RED).build()

    val psionicsTag =
        text().content("초월").decoration(TextDecoration.ITALIC, false).color(NamedTextColor.GRAY).build()

    internal val enchantabilities: Map<Material, Int> = EnumMap<Material, Int>(Material::class.java).apply {
        val armors = Material.values().filter { item ->
            item.isItem && item.equipmentSlot.let { slot ->
                slot != EquipmentSlot.HAND && slot != EquipmentSlot.OFF_HAND
            }
        }

        fun putAllArmors(name: String, value: Int) {
            armors.filter { it.name.startsWith(name) }.forEach {
                this[it] = value
            }
        }

        putAllArmors("LEATHER", 15)
        putAllArmors("CHAINMAIL", 12)
        putAllArmors("IRON", 9)
        putAllArmors("GOLDEN", 25)
        putAllArmors("DIAMOND", 10)
        putAllArmors("TURTLE", 9)
        putAllArmors("NETHERITE", 15)
    }
}

var ItemStack.isPsychicbound: Boolean
    get() = itemMeta.isPsychicbound
    set(value) {
        itemMeta = itemMeta.apply {
            isPsychicbound = value
        }
    }

var ItemMeta.isPsychicbound
    get() = lore()?.let { PsychicItem.boundTag in it } ?: false
    set(value) {
        val lore = lore() ?: ArrayList<Component>()
        lore.remove(PsychicItem.boundTag)
        if (value) lore.add(PsychicItem.boundTag)
        lore(lore)
    }

fun Inventory.removeAllPsychicbounds() {
    for (i in 0 until count()) {
        val item = getItem(i)

        if (item != null && item.isPsychicbound) {
            setItem(i, null)
        }
    }
}

var ItemStack.psionicsLevel
    get() = itemMeta.transcendenceLevel
    set(value) {
        itemMeta = itemMeta.apply {
            transcendenceLevel = value
        }
    }

var ItemMeta.transcendenceLevel
    get() = lore()?.find { lore ->
        lore is TextComponent && lore.content() == PsychicItem.psionicsTag.content()
    }?.let { tag ->
        tag.children().firstOrNull()?.color()?.value()
    } ?: 0
    set(value) {
        val lore = lore() ?: ArrayList<Component>()
        lore.removeIf { it is TextComponent && it.content() == PsychicItem.psionicsTag.content() }

        if (value > 0) {
            lore.add(
                0, PsychicItem.psionicsTag.children(
                    listOf(
                        space().color(TextColor.color(value)),
                        text().content(value.toRomanNumerals()).color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false).build()
                    )
                )
            )
        }

        lore(lore)
    }

private fun Int.toRomanNumerals() = when (this) {
    1 -> "I"
    2 -> "II"
    3 -> "III"
    4 -> "IV"
    5 -> "V"
    6 -> "VI"
    7 -> "VII"
    8 -> "VIII"
    9 -> "IX"
    10 -> "X"
    else -> toString()
}

fun Inventory.addItemNonDuplicate(items: Collection<ItemStack>) {
    out@ for (item in items) {
        for (invItem in this) {
            if (invItem == null)
                continue
            if (invItem.isSimilarLore(item))
                continue@out
        }

        addItem(item)
    }
}

private fun ItemStack.isSimilarLore(other: ItemStack): Boolean {
    val meta = itemMeta
    val otherMeta = other.itemMeta

    return type == other.type && data == other.data && meta.displayName() == itemMeta.displayName() && meta.lore() == otherMeta.lore()
}

val Material.enchantability: Int
    get() = PsychicItem.enchantabilities[this] ?: 0