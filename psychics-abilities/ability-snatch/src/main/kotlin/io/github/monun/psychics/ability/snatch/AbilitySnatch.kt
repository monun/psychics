package io.github.monun.psychics.ability.snatch

import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.ActiveAbility
import io.github.monun.psychics.Channel
import io.github.monun.tap.config.Config
import io.github.monun.tap.config.Name
import org.bukkit.*
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.LivingEntity
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.util.BoundingBox

// 지정한 위치의 개체를 자신의 앞으로 끌어오기
@Name("snatch")
class AbilityConceptSnatch : AbilityConcept() {

    @Config
    var snatchWidth = 8.0

    @Config
    var snatchHeight = 3.0

    init {
        cooldownTime = 20000L
        castingTime = 1500L
        range = 64.0
        wand = ItemStack(Material.FISHING_ROD)
    }
}

class AbilitySnatch : ActiveAbility<AbilityConceptSnatch>(WandAction.LEFT_CLICK), Listener {
    init {
        targeter = {
            val player = esper.player
            val start = player.eyeLocation
            val direction = start.direction
            val world = start.world

            world.rayTrace(
                start,
                direction,
                concept.range,
                FluidCollisionMode.NEVER,
                true,
                0.5
            ) { entity ->
                entity !== player && entity is LivingEntity
            }?.let { result ->
                result.hitEntity?.location ?: result.hitPosition.toLocation(world)
            }
        }
    }

    override fun onChannel(channel: Channel) {
        val location = channel.target as Location
        val world = location.world
        val x = location.x
        val y = location.y
        val z = location.z
        val width = concept.snatchWidth / 2.0
        val height = concept.snatchHeight

        world.spawnParticle(
            Particle.DRAGON_BREATH,
            x, y, z,
            32,
            width, height, width,
            0.0,
            null,
            true
        )
    }

    override fun onCast(event: PlayerEvent, action: WandAction, target: Any?) {
        if (target == null) return

        val concept = concept

        cooldownTime = concept.cooldownTime
        psychic.consumeMana(concept.cost)

        val player = esper.player
        val location = target as Location
        val x = location.x
        val y = location.y
        val z = location.z
        val w = concept.snatchWidth / 2.0
        val h = concept.snatchHeight
        val box = BoundingBox(x - w, y, z - w, x + w, y + h, z + w)
        val world = location.world
        val snatchLocation = esper.player.location.apply {
            pitch = 0.0F
            add(direction.multiply(1.5))
        }

        world.spawnParticle(
            Particle.CLOUD,
            x, y, z,
            128,
            w, w, w,
            0.1,
            null, true
        )

        world.playSound(snatchLocation, Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.MASTER, 1.0F, 1.0F)

        world.getNearbyEntities(box) { entity ->
            entity !== player && entity is LivingEntity && entity !is ArmorStand
        }.forEach { entity ->
            val entityLocation = entity.location
            entity.teleport(
                snatchLocation.apply {
                    yaw = entityLocation.yaw
                    pitch = entityLocation.pitch
                }
            )
        }
    }
}