package io.github.dytroInc.psychics.ability.timerewind

import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.ActiveAbility
import io.github.monun.psychics.TestResult
import io.github.monun.tap.config.Config
import io.github.monun.tap.config.Name
import net.kyori.adventure.text.Component.text
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.event.player.PlayerEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.*

// 10초(기본 값) 전으로 체력과 위치가 되돌아가는 능력.
@Name("time-rewind")
class AbilityConceptTimeRewind : AbilityConcept() {
    @Config
    var rewindTimeAmount = 10.0

    init {
        cooldownTime = 20000L
        cost = 10.0
        description = listOf(
            text("우클릭하면 ${rewindTimeAmount}초 전으로 체력과 위치가 되돌아갑니다."),
            text("시간 역행하고 나서 1초 동안은 무적이 됩니다.")
        )
        wand = ItemStack(Material.CLOCK)
        displayName = "시간 역행"
    }
}

class AbilityTimeRewind : ActiveAbility<AbilityConceptTimeRewind>() {
    private val link = TimeLink()
    override fun onEnable() {
        psychic.runTaskTimer(this::tick, 0, 1)
    }

    private fun tick() {
        val player = esper.player
        link.add(TimeFrame(player.health, player.location))
    }

    override fun onCast(event: PlayerEvent, action: WandAction, target: Any?) {
        val player = event.player
        cooldownTime = concept.cooldownTime
        if (psychic.consumeMana(concept.cost)) {
            val (health, location) = link.first
            player.health = health
            player.addPotionEffect(
                PotionEffect(
                    PotionEffectType.DAMAGE_RESISTANCE,
                    20, 200, false, false
                )
            )
            player.teleport(location)
            player.playSound(
                player.location,
                Sound.ENTITY_BAT_TAKEOFF,
                1.0f,
                1.0f
            )
            player.world.spawnParticle(
                Particle.GLOW,
                player.location,
                64,
                0.1,
                0.1,
                0.1,
                3.0,
                null,
                true
            )
            player.noDamageTicks = 20
        } else {
            player.sendActionBar(TestResult.FailedCost.message(this))
        }
    }

    data class TimeFrame(val health: Double, val location: Location)
    inner class TimeLink : LinkedList<TimeFrame>() {
        override fun add(element: TimeFrame): Boolean {
            if (size >= concept.rewindTimeAmount * 20) poll()
            return super.add(element)
        }
    }
}