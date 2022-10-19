package io.github.anblus.psychics.ability.worsematter

import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.AbilityType
import io.github.monun.psychics.ActiveAbility
import io.github.monun.psychics.attribute.EsperAttribute
import io.github.monun.psychics.damage.Damage
import io.github.monun.psychics.damage.DamageType
import io.github.monun.psychics.util.hostileFilter
import io.github.monun.tap.config.Name
import io.github.monun.tap.trail.TrailSupport
import net.kyori.adventure.text.Component.text
import org.bukkit.*
import org.bukkit.entity.LivingEntity
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerEvent
import org.bukkit.inventory.ItemStack

// 불 혹은 얼음이 걸려있는 상대에게 데미지
@Name("worse-matter")
class AbilityConceptWorseMatter : AbilityConcept() {
    init {
        displayName = "병상첨병"
        type = AbilityType.ACTIVE
        cooldownTime = 1000L
        range = 78.0
        damage = Damage.of(DamageType.BLAST, EsperAttribute.ATTACK_DAMAGE to 4.0)
        description = listOf(
            text("상대가 얼어 있거나 혹은 화염에 휩싸인 상태일 때"),
            text("능력을 발동하면 상대의 뒤로 순간이동을 하고 데미지를 입힙니다.")
        )
        wand = ItemStack(Material.BONE_MEAL)

    }
}

class AbilityWorseMatter : ActiveAbility<AbilityConceptWorseMatter>(), Listener {

    override fun onCast(event: PlayerEvent, action: WandAction, target: Any?) {
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
        )?.let { result ->
            val target = result.hitEntity
            if (target !is LivingEntity) return
            if (target.fireTicks <= 0 && target.freezeTicks <= 0) return

            target.fireTicks = 0
            target.freezeTicks = 0
            cooldownTime = concept.cooldownTime
            target.psychicDamage()
            val tpRay = world.rayTrace(
                target.location,
                target.location.direction.multiply(-1),
                2.0,
                FluidCollisionMode.NEVER,
                true,
                0.0
            ) { entity ->
                entity === player
            }

            var loc = target.location.clone().add(target.location.direction.multiply(-2).clone())
            if (tpRay != null) loc = tpRay.hitPosition.toLocation(world)
            player.teleport(
                loc.apply {
                    yaw = target.eyeLocation.yaw
                    pitch = target.eyeLocation.pitch
                }
            )

            val to = result.hitPosition.toLocation(world)
            TrailSupport.trail(start, to, 0.3) { w, x, y, z ->
                w.spawnParticle(Particle.SPELL_WITCH, x, y, z, 1, 0.0, 0.0, 0.0, 0.0)
            }
            world.spawnParticle(Particle.ASH, target.location, 32, 0.8, 1.0, 0.8, 0.0)
            world.playSound(
                target.location,
                Sound.ENTITY_BLAZE_SHOOT,
                SoundCategory.PLAYERS,
                1.5F,
                0.1F
            )
        }
    }

}






