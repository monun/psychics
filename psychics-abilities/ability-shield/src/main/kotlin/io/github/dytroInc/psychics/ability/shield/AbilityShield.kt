package io.github.dytroInc.psychics.ability.shield

import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.ActiveAbility
import io.github.monun.psychics.TestResult
import io.github.monun.psychics.attribute.EsperAttribute
import io.github.monun.psychics.attribute.EsperStatistic
import io.github.monun.psychics.tooltip.TooltipBuilder
import io.github.monun.psychics.tooltip.stats
import io.github.monun.psychics.tooltip.template
import io.github.monun.tap.config.Config
import io.github.monun.tap.config.Name
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerEvent
import org.bukkit.inventory.ItemStack
import java.text.DecimalFormat

// 피해를 흡수하는 보호막 지급
@Name("shield")
class AbilityConceptShield : AbilityConcept() {
    @Config
    val totalAbsorb = EsperStatistic.of(EsperAttribute.ATTACK_DAMAGE to 10.0)

    init {
        cooldownTime = 10000L
        durationTime = 10000L
        description = listOf(
            text("우클릭할 경우 피해를 흡수하는 보호막을 갖게 됩니다."),
        )
        wand = ItemStack(Material.IRON_NUGGET)
        displayName = "보호막"
        cost = 40.0
    }

    override fun onRenderTooltip(tooltip: TooltipBuilder, stats: (EsperStatistic) -> Double) {
        tooltip.stats(totalAbsorb) { NamedTextColor.AQUA to "총 피해 흡수량" to "total-absorb" }
        tooltip.template("total-absorb", stats(totalAbsorb))
    }
}

class AbilityShield : ActiveAbility<AbilityConceptShield>(), Listener {

    var totalAbsorb = 0.0

    override fun onEnable() {
        psychic.runTaskTimer(this::durationEffect, 0, 1)
        psychic.registerEvents(this)
    }

    override fun onCast(event: PlayerEvent, action: WandAction, target: Any?) {
        if (!psychic.consumeMana(concept.cost)) return esper.player.sendActionBar(TestResult.FailedCost.message(this))
        cooldownTime = concept.cooldownTime
        totalAbsorb = esper.getStatistic(concept.totalAbsorb)
        durationTime = concept.durationTime
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    fun onDamage(event: EntityDamageEvent) {
        val player = esper.player
        if (event.entity == player && totalAbsorb > 0 && durationTime > 0) {
            if (event.finalDamage <= 0) return
            if (event.damage >= totalAbsorb) {
                event.damage -= totalAbsorb
                totalAbsorb = 0.0
                durationTime = 0
                player.sendActionBar(text("보호막이 파괴됐습니다!"))
                player.world.playSound(player.location, Sound.ITEM_SHIELD_BREAK, SoundCategory.PLAYERS, 1.5f, 1f)
            } else {
                totalAbsorb -= event.damage
                event.damage = 0.0
                player.sendActionBar(
                    text().color(NamedTextColor.WHITE).content(DecimalFormat("#.##").format(totalAbsorb))
                )
                player.world.playSound(player.location, Sound.ITEM_SHIELD_BREAK, SoundCategory.PLAYERS, 0.5f, 1f)
            }
        }
    }

    private fun durationEffect() {
        if (durationTime <= 0L) return

        val player = esper.player
        val location = player.location.apply { y += 2.0 }
        val world = location.world

        world.spawnParticle(Particle.TOTEM, location.x, location.y, location.z, 1, 0.25, 0.0, 0.25, 0.0, null, true)
    }
}