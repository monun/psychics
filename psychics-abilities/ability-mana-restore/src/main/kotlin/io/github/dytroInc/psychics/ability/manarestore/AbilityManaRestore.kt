package io.github.dytroInc.psychics.ability.manarestore

import io.github.monun.psychics.Ability
import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.attribute.EsperStatistic
import io.github.monun.psychics.tooltip.TooltipBuilder
import io.github.monun.tap.config.Config
import io.github.monun.tap.config.Name
import io.github.monun.tap.event.EntityProvider
import io.github.monun.tap.event.TargetEntity
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.inventory.ItemStack

// 피해를 주거나 받을 때 피해의 1.2배 (기본 수치) 만큼 마나가 회복됩니다.
@Name("mana-restore")
class AbilityConceptManaRestore : AbilityConcept() {
    @Config
    val restoredManaRate = 1.2

    init {
        description = listOf(
            text("피해를 주거나 받을 때 피해에 따라서 마나를 회복합니다.")
        )
        wand = ItemStack(Material.NETHER_STAR)
        displayName = "마나 회복"
    }

    override fun onRenderTooltip(tooltip: TooltipBuilder, stats: (EsperStatistic) -> Double) {
        tooltip.header(
            text().color(NamedTextColor.DARK_GREEN).content("마나 회복량 ").decoration(TextDecoration.ITALIC, false)
                .decorate(TextDecoration.BOLD)
                .append(
                    text()
                        .color(NamedTextColor.WHITE)
                        .content("피해의 ${restoredManaRate}배")
                )
                .build()
        )
    }
}

class AbilityManaRestore : Ability<AbilityConceptManaRestore>(), Listener {
    override fun onEnable() {
        psychic.registerEvents(this)
    }

    @EventHandler(ignoreCancelled = true)
    fun onTakeDamage(event: EntityDamageByEntityEvent) {
        val player = event.entity
        if (player == esper.player) {
            psychic.mana += (event.finalDamage * concept.restoredManaRate)
        }
    }

    @EventHandler(ignoreCancelled = true)
    @TargetEntity(value = EntityProvider.EntityDamageByEntity.Damager::class)
    fun onGiveDamage(event: EntityDamageByEntityEvent) {
        val player = event.damager
        if (player == esper.player) {
            psychic.mana += (event.finalDamage * concept.restoredManaRate)
        }
    }

    @EventHandler(ignoreCancelled = true)
    @TargetEntity(value = EntityProvider.EntityDamageByEntity.Shooter::class)
    fun onGiveDamageByShooting(event: EntityDamageByEntityEvent) {
        val player = event.damager
        if (player == esper.player) {
            psychic.mana += (event.finalDamage * concept.restoredManaRate)
        }
    }
}