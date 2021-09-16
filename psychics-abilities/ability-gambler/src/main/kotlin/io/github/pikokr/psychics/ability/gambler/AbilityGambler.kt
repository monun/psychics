package io.github.pikokr.psychics.ability.gambler

import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.ActiveAbility
import io.github.monun.psychics.Channel
import io.github.monun.psychics.attribute.EsperAttribute
import io.github.monun.psychics.damage.Damage
import io.github.monun.psychics.damage.DamageType
import io.github.monun.tap.config.Name
import net.kyori.adventure.text.Component.text
import org.bukkit.*
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerEvent
import org.bukkit.inventory.ItemStack

// 지정한 대상에게 폭죽 효과와 피해를 입히는 능력
@Name("gambler")
class AbilityConceptGambler : AbilityConcept() {
    init {
        cooldownTime = 10000L
        castingTime = 5000L
        range = 64.0
        cost = 10.0
        description = listOf(
            text("일정 확률로 근처 플레이어에게 순간이동 합니다")
        )
        wand = ItemStack(Material.MAGMA_CREAM)
    }
}

class AbilityGambler : ActiveAbility<AbilityConceptGambler>(), Listener {
    override fun onCast(event: PlayerEvent, action: WandAction, target: Any?) {
        val concept = concept

        psychic.consumeMana(concept.cost)
        event.player.sendMessage("와 샌즈")
    }

    override fun onChannel(channel: Channel) {
        val loc = esper.player.eyeLocation.apply {
            yaw = (channel.remainingTime/4).toFloat()
            pitch = 0.0f
            add(direction.multiply(2))
        }

        esper.player.world.spawnParticle(Particle.TOTEM, loc, 1, 0.0, 0.0, 0.0, 0.0)

        esper.player.sendMessage(channel.remainingTime.toString())
    }

//    companion object {
//        private val effect = FireworkEffect.builder().with(FireworkEffect.Type.BURST).withColor(Color.RED).build()
//
//        private fun LivingEntity.playPsychicEffect() {
//            world.playFirework(location, effect)
//        }
//    }

//    override fun onInitialize() {
//        targeter = {
//            val player = esper.player
//            val start = player.eyeLocation
//            val world = start.world
//
//            world.rayTrace(
//                start,
//                start.direction,
//                concept.range,
//                FluidCollisionMode.NEVER,
//                true,
//                0.5,
//                player.hostileFilter()
//            )?.hitEntity
//        }
//    }
//
//    override fun onChannel(channel: Channel) {
//        val player = esper.player
//        val location = player.location.apply { y += 3.0 }
//
//        location.world.spawnParticle(
//            Particle.FLAME,
//            location,
//            10,
//            0.0,
//            0.0,
//            0.0,
//            0.03
//        )
//    }
//
//    override fun onCast(event: PlayerEvent, action: WandAction, target: Any?) {
//        if (target !is LivingEntity) return
//
//        val concept = concept
//
//        psychic.consumeMana(concept.cost)
//        cooldownTime = concept.cooldownTime
//
//        target.psychicDamage()
//        target.playPsychicEffect()
//    }
}