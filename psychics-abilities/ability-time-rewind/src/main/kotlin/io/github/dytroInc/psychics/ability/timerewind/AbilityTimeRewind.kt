package io.github.dytroInc.psychics.ability.timerewind

import io.github.monun.psychics.Ability
import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.AbilityType
import io.github.monun.psychics.TestResult
import io.github.monun.tap.config.Config
import io.github.monun.tap.config.Name
import net.kyori.adventure.text.Component.text
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.*

// 지정한 대상에게 폭죽 효과와 피해를 입히는 능력
@Name("time-rewind")
class AbilityConceptTimeRewind : AbilityConcept() {
    @Config
    var rewindTimeAmount = 10.0
    init {
        type = AbilityType.ACTIVE
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

class AbilityTimeRewind : Ability<AbilityConceptTimeRewind>(), Listener {
    val link = TimeLink()
    override fun onEnable() {
        psychic.registerEvents(this)
        psychic.runTaskTimer(this::tick, 0, 1)
    }
    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        val action = event.action
        if(action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            event.item?.let { item ->
                if (item.type == Material.CLOCK) {
                    val player = esper.player
                    val result = test()
                    if (result != TestResult.Success) {
                        player.sendActionBar(result.message(this))
                        return
                    } else if (!psychic.consumeMana(concept.cost)) {
                        player.sendActionBar(TestResult.FailedCost.message(this))
                        return
                    }
                    cooldownTime = concept.cooldownTime
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

                }
            }
        }
    }

    private fun tick() {
        val p = esper.player
        link.add(TimeFrame(p.health, p.location))
    }

    data class TimeFrame(val health: Double, val location: Location)
    inner class TimeLink : LinkedList<TimeFrame>() {
        override fun add(element: TimeFrame): Boolean {
            if(size >= concept.rewindTimeAmount * 20) poll()
            return super.add(element)
        }
    }
}