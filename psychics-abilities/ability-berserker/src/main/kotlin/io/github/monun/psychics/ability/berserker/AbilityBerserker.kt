package io.github.monun.psychics.ability.berserker

import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.ActiveAbility
import io.github.monun.tap.config.Config
import io.github.monun.tap.config.Name
import io.github.monun.tap.effect.playFirework
import org.bukkit.*
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerEvent
import org.bukkit.event.player.PlayerVelocityEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

// 지정한 대상에게 폭죽 효과와 피해를 입히는 능력
@Name("berserker")
class AbilityConceptBerserker : AbilityConcept() {

    @Config
    var speedAmplifier = 4

    @Config
    var damageOnDuration = 0.5

    init {
        cost = 0.0
        durationTime = 15000L
        cooldownTime = 60000L
        wand = ItemStack(Material.BLAZE_ROD)
    }
}

class AbilityBerserker : ActiveAbility<AbilityConceptBerserker>(), Listener {
    override fun onEnable() {
        psychic.registerEvents(this)
        psychic.runTaskTimer(this::durationEffect, 0L, 1L)
    }

    private fun durationEffect() {
        if (durationTime <= 0L) return

        val player = esper.player
        val location = player.location.apply { y += 2.0 }
        val world = location.world

        world.spawnParticle(Particle.VILLAGER_ANGRY, location.x, location.y, location.z, 4, 0.25, 0.0, 0.25, 0.0, null, true)
    }

    override fun onCast(event: PlayerEvent, action: WandAction, target: Any?) {
        val concept = concept
        psychic.consumeMana(concept.cost)
        cooldownTime = concept.cooldownTime
        durationTime = concept.durationTime

        val player = esper.player
        val ticks = (concept.durationTime / 50L).toInt()
        val potion = PotionEffect(PotionEffectType.SPEED, ticks, concept.speedAmplifier, false, false, false)
        player.addPotionEffect(potion)


        val location = player.location.apply { y += 2.0 }
        val world = location.world
        val firework = FireworkEffect.builder().with(FireworkEffect.Type.BURST).withColor(Color.RED).withFlicker().build()
        world.playFirework(location, firework)
    }

    @EventHandler(ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageEvent) {
        if (durationTime > 0L) {
            val damage = event.damage * concept.damageOnDuration
            event.damage = damage
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onVelocity(event: PlayerVelocityEvent) {
        if (durationTime > 0L) {
            event.isCancelled = true
        }
    }
}