package io.github.monun.psychics.invfx

import io.github.monun.psychics.PsychicConcept
import io.github.monun.psychics.attribute.EsperStatistic
import io.github.monun.psychics.item.addItemNonDuplicate
import io.github.monun.invfx.InvFX
import io.github.monun.invfx.frame.InvFrame
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
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

    private val helpItem =
        ItemStack(Material.DARK_OAK_SIGN).apply {
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
        }

    fun create(psychicConcept: PsychicConcept, stats: (EsperStatistic) -> Double): InvFrame {
        return InvFX.frame(1, text("PSYCHICS")) {
            item(0, 0, psychicConcept.renderTooltip().applyTo(ItemStack(Material.ENCHANTED_BOOK)))
            item(8, 0, helpItem)
            list(2, 0, 5, 1, true, { psychicConcept.abilityConcepts }) {
                transform { it.renderTooltip(stats).applyTo(it.wand ?: ItemStack(Material.BOOK)) }
                onClickItem { _, _, (item, _), event ->
                    event.whoClicked.inventory.addItemNonDuplicate(item.supplyItems)
                }
            }.let { list ->
                slot(1, 0) {
                    item = previousItem
                    onClick { list.index-- }
                }
                slot(7, 0) {
                    item = nextItem
                    onClick { list.index++ }
                }
            }
        }
    }
}