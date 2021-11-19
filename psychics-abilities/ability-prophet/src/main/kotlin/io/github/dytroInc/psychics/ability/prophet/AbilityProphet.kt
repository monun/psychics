package io.github.dytroInc.psychics.ability.prophet

import io.github.monun.psychics.Ability
import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.AbilityType
import io.github.monun.psychics.TestResult
import io.github.monun.psychics.util.hostileFilter
import io.github.monun.tap.config.Name
import net.kyori.adventure.text.Component.text
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

// 쉬프트하면 마나를 소모해서 사거리 내 적에게 발광 부여
@Name("prophet")
class AbilityConceptProphet : AbilityConcept() {
    init {
        type = AbilityType.PASSIVE
        cost = 1.0
        range = 50.0
        description = listOf(
            text("쉬프트하는 동안 사거리 내에 있는 적인"),
            text("플레이어들에게 발광을 부여합니다."),
        )
        wand = ItemStack(Material.ENCHANTED_GOLDEN_APPLE)
        displayName = "선지자"
    }
}

class AbilityProphet : Ability<AbilityConceptProphet>(), Listener {
    private var tick = 0
    override fun onEnable() {
        psychic.registerEvents(this)
        psychic.runTaskTimer(this::tick, 0, 1)
    }


    private fun tick() {
        val player = esper.player
        if (player.isSneaking && player.gameMode != GameMode.SPECTATOR) {
            if (tick == 0) {
                if (!psychic.consumeMana(concept.cost)) return player.sendActionBar(TestResult.FailedCost.message(this))
                player.location.getNearbyPlayers(concept.range).filter { player.hostileFilter().test(it) }.forEach {
                    it.addPotionEffect(PotionEffect(PotionEffectType.GLOWING, 10, 100))
                }
                tick = 5
            } else {
                tick--
            }
        } else if (tick > 0) {
            tick = 0
        }
    }
}