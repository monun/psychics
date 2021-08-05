package io.github.monun.psychics.ability.sample

import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.ActiveAbility
import io.github.monun.psychics.Channel
import io.github.monun.psychics.attribute.EsperAttribute
import io.github.monun.psychics.damage.Damage
import io.github.monun.psychics.damage.DamageType
import io.github.monun.psychics.util.hostileFilter
import io.github.monun.tap.config.Name
import io.github.monun.tap.effect.playFirework
import net.kyori.adventure.text.Component.text
import org.bukkit.*
import org.bukkit.entity.LivingEntity
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerEvent
import org.bukkit.inventory.ItemStack

// 지정한 대상에게 폭죽 효과와 피해를 입히는 능력
@Name("sample")
class AbilityConceptSample : AbilityConcept() {
    init {
        cooldownTime = 5000L
        castingTime = 1000L
        range = 64.0
        cost = 10.0
        damage = Damage.of(DamageType.RANGED, EsperAttribute.ATTACK_DAMAGE to 4.0)
        description = listOf(
            text("지정한 대상에게 폭발을 일으킵니다.")
        )
        wand = ItemStack(Material.STICK)
    }
}

class AbilitySample : ActiveAbility<AbilityConceptSample>(), Listener {
    companion object {
        private val effect = FireworkEffect.builder().with(FireworkEffect.Type.BURST).withColor(Color.RED).build()

        private fun LivingEntity.playPsychicEffect() {
            world.playFirework(location, effect)
        }
    }

    override fun onInitialize() {
        targeter = {
            val player = esper.player
            val start = player.eyeLocation
            val world = start.world

            world.rayTrace(
                start,
                start.direction,
                concept.range,
                FluidCollisionMode.NEVER,
                true,
                0.5,
                player.hostileFilter()
            )?.hitEntity
        }
    }

    override fun onChannel(channel: Channel) {
        val player = esper.player
        val location = player.location.apply { y += 3.0 }

        location.world.spawnParticle(
            Particle.FLAME,
            location,
            10,
            0.0,
            0.0,
            0.0,
            0.03
        )
    }

    override fun onCast(event: PlayerEvent, action: WandAction, target: Any?) {
        if (target !is LivingEntity) return

        val concept = concept

        psychic.consumeMana(concept.cost)
        cooldownTime = concept.cooldownTime

        target.psychicDamage()
        target.playPsychicEffect()
    }
}