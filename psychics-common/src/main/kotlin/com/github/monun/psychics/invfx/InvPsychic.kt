package com.github.monun.psychics.invfx

import com.github.monun.invfx.InvFX
import com.github.monun.invfx.InvScene
import com.github.monun.psychics.PsychicConcept
import com.github.monun.psychics.attribute.EsperStatistic
import com.github.monun.psychics.item.addItemNonDuplicate
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.md_5.bungee.api.ChatColor
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

object InvPsychic {

    private val previousItem =
        ItemStack(Material.END_CRYSTAL).apply {
            itemMeta = itemMeta.apply {
                displayName(
                    text().color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)
                        .decorate(TextDecoration.BOLD).content("←").build()
                )
            }
        }
    private val nextItem =
        ItemStack(Material.END_CRYSTAL).apply {
            itemMeta = itemMeta.apply {
                displayName(
                    text().color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)
                        .decorate(TextDecoration.BOLD).content("→").build()
                )
            }
        }

    fun create(psychicConcept: PsychicConcept, stats: (EsperStatistic) -> Double): InvScene {
        return InvFX.scene(1, "${ChatColor.BOLD}Psychic") {
            panel(0, 0, 9, 1) {
                onInit {
                    it.setItem(0, 0, psychicConcept.renderTooltip().applyTo(ItemStack(Material.ENCHANTED_BOOK)))
                }
                listView(2, 0, 5, 1, true, psychicConcept.abilityConcepts) {
                    transform { it.renderTooltip(stats).applyTo(it.wand ?: ItemStack(Material.BOOK)) }
                    onClickItem { _, _, _, clicked, event ->
                        event.whoClicked.inventory.addItemNonDuplicate(clicked.supplyItems)
                    }
                }.let { view ->
                    button(1, 0) {
                        onInit {
                            it.item = previousItem
                        }
                        onClick { _, _ ->
                            view.index--
                        }
                    }
                    button(7, 0) {
                        onInit { it.item = nextItem }

                        onClick { _, _ ->
                            view.index++
                        }
                    }
                }
            }.let { panel ->
                panel.setItem(8, 0, ItemStack(Material.DARK_OAK_SIGN).apply {
                    itemMeta = itemMeta.apply {
                        displayName(
                            text().color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false)
                                .decorate(TextDecoration.BOLD).content("도움말").build()
                        )
                        lore(
                            listOf<Component>(
                                text().color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)
                                    .content("능력과 스킬의 정보를 확인하세요.").build(),
                                text().color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)
                                    .content("← → 버튼을 눌러 스크롤하세요").build(),
                                text().color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)
                                    .content("능력과 스킬의 정보를 확인하세요.").build(),
                                text().color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)
                                    .content("지급 아이템이 있는 경우 능력을").build(),
                                text().color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)
                                    .content("클릭하여 얻을 수 있습니다.").build()
                            )
                        )
                    }
                })
            }
        }
    }
}