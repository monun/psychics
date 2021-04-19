package com.github.monun.psychics.item

import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta

object PsychicItem {
    val boundTag =
        text().content("Psychicbound").decoration(TextDecoration.ITALIC, false).decorate(TextDecoration.BOLD)
            .color(NamedTextColor.RED).build()
}

var ItemStack.isPsychicbound: Boolean
    get() = lore()?.let { PsychicItem.boundTag in it } ?: false
    set(value) {
        val meta = itemMeta
        val lore = meta.lore() ?: emptyList()

        if (value) {
            if (PsychicItem.boundTag in lore) return

            meta.lore(lore.toMutableList().apply { add(PsychicItem.boundTag) })
        } else {
            val i = lore.indexOf(PsychicItem.boundTag); if (i == -1) return

            meta.lore(lore.toMutableList().apply { removeAt(i) })
        }

        itemMeta = meta
    }

var ItemMeta.isPsychicbound
    get() = lore()?.let { PsychicItem.boundTag in it } ?: false
    set(value) {
        val lore = lore() ?: emptyList()

        if (value) {
            if (PsychicItem.boundTag in lore) return

            lore(lore.toMutableList().apply { add(PsychicItem.boundTag) })
        } else {
            val i = lore.indexOf(PsychicItem.boundTag); if (i == -1) return

            lore(lore.toMutableList().apply { removeAt(i) })
        }
    }

fun Inventory.removeAllPsychicbounds() {
    for (i in 0 until count()) {
        val item = getItem(i)

        if (item != null && item.isPsychicbound) {
            setItem(i, null)
        }
    }
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