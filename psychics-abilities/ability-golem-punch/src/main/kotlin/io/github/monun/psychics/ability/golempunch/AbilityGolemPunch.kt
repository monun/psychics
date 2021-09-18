package io.github.monun.psychics.ability.golempunch

import io.github.monun.psychics.Ability
import io.github.monun.psychics.AbilityConcept
import io.github.monun.tap.config.Name
import io.github.monun.tap.event.EntityProvider
import io.github.monun.tap.event.TargetEntity
import net.kyori.adventure.text.Component.text
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent

// 타격시 넉백을 위로 변환
@Name("golem-punch")
class AbilityConceptGolemPunch : AbilityConcept() {
    init {
        description = listOf(
            text("공격시 넉백 방향을 위로 바꿉니다.")
        )
    }
}

class AbilityGolemPunch : Ability<AbilityConceptGolemPunch>(), Listener {
    override fun onEnable() {
        psychic.registerEvents(this)
    }

    @TargetEntity(EntityProvider.EntityDamageByEntity.Damager::class)
    @EventHandler(ignoreCancelled = true)
    fun onAttack(event: EntityDamageByEntityEvent) {
        val target = event.entity

        psychic.runTask({
            target.velocity = target.velocity.apply {
                y = length()
                x = 0.0
                z = 0.0
            }
        }, 0L)
    }
}