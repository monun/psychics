package io.github.monun.psychics.ability.golemstance

import io.github.monun.psychics.Ability
import io.github.monun.psychics.AbilityConcept
import io.github.monun.tap.config.Name
import net.kyori.adventure.text.Component.text
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerVelocityEvent

// 지정한 대상에게 폭죽 효과와 피해를 입히는 능력
@Name("golem-stance")
class AbilityConceptGolemStance : AbilityConcept() {
    init {
        description = listOf(
            text("모든 넉백을 무시합니다.")
        )
    }
}

class AbilityGolemStance : Ability<AbilityConceptGolemStance>(), Listener {
    override fun onEnable() {
        psychic.registerEvents(this)
    }

    @EventHandler(ignoreCancelled = true)
    fun onVelocity(event: PlayerVelocityEvent) {
        event.isCancelled = true
    }
}