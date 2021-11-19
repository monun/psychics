package io.github.dytroInc.psychics.ability.drill

import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.ActiveAbility
import io.github.monun.psychics.TestResult
import io.github.monun.tap.config.Config
import io.github.monun.tap.config.Name
import net.kyori.adventure.text.Component.text
import org.bukkit.Material
import org.bukkit.Tag
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.util.NumberConversions

// 앞 블록을 캐면서 빠르게 앞으로 돌진하는 능력 + 낙뎀 없는 능력
@Name("drill")
class AbilityConceptDrill : AbilityConcept() {
    @Config
    val speed = 1.2f

    init {
        cooldownTime = 10000L
        durationTime = 1000L
        range = 1.0
        description = listOf(
            text("앞에 있는 블록을 캐면서 빠르게 앞으로 돌진합니다."),
            text("낙뎀을 전혀 받지 않습니다.")
        )
        wand = ItemStack(Material.IRON_SHOVEL)
        displayName = "드릴"
        cost = 30.0
    }
}

class AbilityDrill : ActiveAbility<AbilityConceptDrill>(), Listener {
    override fun onEnable() {
        psychic.runTaskTimer(this::tick, 0, 1)
        psychic.registerEvents(this)
    }

    override fun onCast(event: PlayerEvent, action: WandAction, target: Any?) {
        if (!psychic.consumeMana(concept.cost)) return esper.player.sendActionBar(TestResult.FailedCost.message(this))
        cooldownTime = concept.cooldownTime
        durationTime = concept.durationTime
    }

    private fun tick() {
        if (durationTime > 0) {
            val range = NumberConversions.ceil(concept.range)
            val player = esper.player
            player.velocity = esper.player.location.direction.multiply(concept.speed).apply { if (y > 0) y /= 5.0 }
            for (dx in -range..range) {
                for (dz in -range..range) {
                    for (dy in -range..(range + 1)) {
                        player.location.clone().add(dx.toDouble(), dy.toDouble(), dz.toDouble()).block.let {
                            if (it.type == Material.BEDROCK) return@let
                            if (it.type == Material.AIR) return@let
                            if (Tag.BASE_STONE_NETHER.isTagged(it.type) || Tag.BASE_STONE_OVERWORLD.isTagged(it.type)) it.type =
                                Material.AIR else it.breakNaturally(true)
                        }
                    }
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onDamage(event: EntityDamageEvent) {
        if (event.entity == esper.player) {
            if (event.cause == EntityDamageEvent.DamageCause.FALL) {
                event.isCancelled = true
            }
        }
    }
}