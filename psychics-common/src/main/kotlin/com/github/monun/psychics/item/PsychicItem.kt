package com.github.monun.psychics.item

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.Component.space
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta

object PsychicItem {
    val boundTag =
        text().content("능력귀속").decoration(TextDecoration.ITALIC, false).decorate(TextDecoration.BOLD)
            .color(NamedTextColor.RED).build()
    val psionicsTag =
        text().content("초월").decoration(TextDecoration.ITALIC, false).color(NamedTextColor.GRAY).build()
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