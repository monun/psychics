package io.github.deopbap.psychics.ability.rush

import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.ActiveAbility
import io.github.monun.psychics.Channel
import io.github.monun.psychics.attribute.EsperAttribute
import io.github.monun.psychics.damage.Damage
import io.github.monun.psychics.damage.DamageType
import io.github.monun.psychics.util.hostileFilter
import io.github.monun.tap.config.Config
import io.github.monun.tap.config.Name
import io.github.monun.tap.effect.playFirework
import io.github.monun.tap.math.normalizeAndLength
import net.kyori.adventure.text.Component.text
import org.bukkit.*
import org.bukkit.entity.LivingEntity
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector

// 빠르게 달리는 능력
@Name("rush")
class AbilityConceptRush : AbilityConcept() {

    @Config
    var speedAmplifier = 8

    @Config
    var collisionRange = 0.3

    init {
        displayName = "돌진"
        cost = 0.0
        cooldownTime = 5000L
        durationTime = 3000L
        damage = Damage.of(DamageType.MELEE, EsperAttribute.ATTACK_DAMAGE to 4.0)
        knockback = 2.0
        description = listOf(
            text("빠르게 달립니다. 엔티티에 부딪치면"),
            text("피해를 주며 달리기를 멈춥니다.")
        )
        wand = ItemStack(Material.FEATHER)
    }
}

class AbilityRush : ActiveAbility<AbilityConceptRush>(), Listener {
    override fun onEnable() {
        psychic.runTaskTimer(this::durationEffect, 0L, 1L)
    }

    private fun durationEffect() {
        if (durationTime <= 0L) return

        val concept = concept

        val player = esper.player
        val location = player.location
        val direction = location.direction.apply { y = 0.0 }
        val world = location.world
        val length = player.velocity.normalizeAndLength()

        world.rayTrace(
            location,
            direction,
            length,
            FluidCollisionMode.NEVER,
            true,
            concept.collisionRange,
            player.hostileFilter()
        )?.let { result ->
            val hitLocation = result.hitPosition.toLocation(world)
            result.hitEntity?.let { entity ->
                if (entity is LivingEntity) {
                    durationTime = 0L
                    player.removePotionEffect(PotionEffectType.SPEED)

                    entity.psychicDamage()

                    world.playSound(
                        hitLocation,
                        Sound.BLOCK_BASALT_BREAK,
                        2.0F,
                        0.0F
                    )
                }
            }
        }

        world.spawnParticle(
            Particle.CLOUD,
            location.subtract(direction.apply { y = -1.0 }.multiply(0.5)),
            3,
            0.0, 0.0, 0.0,
            0.3
        )
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
    }
}