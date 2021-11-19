package io.github.dytroInc.psychics.ability.ignition

import io.github.monun.psychics.Ability
import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.util.hostileFilter
import io.github.monun.tap.config.Name
import io.github.monun.tap.event.EntityProvider
import io.github.monun.tap.event.TargetEntity
import net.kyori.adventure.text.Component.text
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.inventory.ItemStack

// 피해 준 적을 불에 태우는 능력
@Name("ignition")
class AbilityConceptIgnition : AbilityConcept() {
    init {
        durationTime = 3000L
        description = listOf(
            text("마나를 소모해서 피해 받은 적을 태웁니다."),
        )
        wand = ItemStack(Material.FLINT_AND_STEEL)
        displayName = "점화"
        cost = 5.0
    }
}

class AbilityIgnition : Ability<AbilityConceptIgnition>(), Listener {
    override fun onEnable() {
        psychic.registerEvents(this)
    }

    @EventHandler
    @TargetEntity(EntityProvider.EntityDamageByEntity.Damager::class)
    fun onDamage(event: EntityDamageByEntityEvent) {
        handleDamageEvent(event)
    }

    @EventHandler
    @TargetEntity(EntityProvider.EntityDamageByEntity.Shooter::class)
    fun onShoot(event: EntityDamageByEntityEvent) {
        handleDamageEvent(event)
    }

    private fun handleDamageEvent(event: EntityDamageEvent) {
        val entity = event.entity
        if (esper.player.hostileFilter().test(entity)) {
            if (psychic.consumeMana(concept.cost)) {
                entity.fireTicks = (concept.durationTime / 50.0).toInt()
            }
        }
    }
}