package io.github.dytroInc.psychics.ability.lunarbunny

import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.AbilityType
import io.github.monun.psychics.ActiveAbility
import io.github.monun.psychics.TestResult
import io.github.monun.tap.config.Name
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

// 랜덤 효과를 주는 송편을 만드는 능력
@Name("lunar-bunny")
class AbilityConceptLunarBunny : AbilityConcept() {
    init {
        cooldownTime = 20000L
        durationTime = 10000L
        displayName = "달토끼"
        type = AbilityType.COMPLEX
        wand = ItemStack(Material.WOODEN_HOE).apply {
            itemMeta = itemMeta?.apply {
                removeItemFlags(*ItemFlag.values())
            }
        }
        description = listOf(
            text("떡메를 들고 우클릭하면 떡을 지급합니다."),
            text("떡을 우클릭하면, 랜덤 버프를 ${durationTime / 1000.0}초 동안 줍니다.")
        )
    }
}

class AbilityLunarBunny : ActiveAbility<AbilityConceptLunarBunny>(), Listener {
    companion object {
        private val ricecake = ItemStack(Material.WHITE_DYE).apply {
            itemMeta = itemMeta?.apply {
                displayName(
                    text().color(NamedTextColor.YELLOW).content("송편").decoration(TextDecoration.ITALIC, false).build()
                )
            }
            lore(
                listOf(
                    text()
                        .content("우클릭으로 먹으면 랜덤 버프를 받습니다. [달토끼 능력 소유자 한정]")
                        .color(NamedTextColor.WHITE)
                        .decoration(TextDecoration.ITALIC, false)
                        .build()
                )
            )
        }

        private val effects = listOf(
            PotionEffectType.INCREASE_DAMAGE, // 힘
            PotionEffectType.DAMAGE_RESISTANCE, // 저항
            PotionEffectType.REGENERATION, // 재생
        )
    }

    override fun onEnable() {
        psychic.registerEvents(this)
    }

    override fun onCast(event: PlayerEvent, action: WandAction, target: Any?) {
        if (!psychic.consumeMana(concept.cost)) return esper.player.sendActionBar(TestResult.FailedCost.message(this))
        cooldownTime = concept.cooldownTime
        event.player.inventory.addItem(ricecake)

    }

    @EventHandler
    fun onConsumeRicecake(event: PlayerInteractEvent) {
        val action = event.action
        if (action == Action.RIGHT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR) {
            event.item?.let {
                if (it.isSimilar(ricecake)) {
                    it.amount--
                    esper.player.addPotionEffect(
                        PotionEffect(effects.random(), (concept.durationTime / 50.0).toInt(), 0)
                    )
                }
            }
        }
    }
}