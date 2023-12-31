package io.github.anblus.psychics.ability.windshot

import io.github.monun.psychics.*
import io.github.monun.psychics.attribute.EsperAttribute
import io.github.monun.psychics.attribute.EsperStatistic
import io.github.monun.psychics.damage.Damage
import io.github.monun.psychics.damage.DamageType
import io.github.monun.psychics.util.TargetFilter
import io.github.monun.tap.config.Config
import io.github.monun.tap.config.Name
import io.github.monun.tap.fake.FakeEntity
import io.github.monun.tap.fake.Movement
import io.github.monun.tap.fake.Trail
import io.github.monun.tap.math.normalizeAndLength
import net.kyori.adventure.text.Component.text
import org.bukkit.FluidCollisionMode
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.LivingEntity
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerEvent
import org.bukkit.inventory.ItemStack
import kotlin.random.Random.Default.nextDouble

// 장풍 일으키기
@Name("wind-shot")
class AbilityConceptWindShot : AbilityConcept() {

    @Config
    val windSpeed = 1.0

    @Config
    val windWiggle = 0.06

    init {
        displayName = "장풍"
        type = AbilityType.ACTIVE
        cost = 15.0
        range = 128.0
        knockback = 1.2
        cooldownTime = 1000L
        castingTime = 1000L
        damage = Damage.of(DamageType.RANGED, EsperStatistic.of(EsperAttribute.ATTACK_DAMAGE to 0.1))
        description = listOf(
            text("바라보는 방향으로 장풍을 일으킵니다.")
        )
        wand = ItemStack(Material.STICK)

    }

}

class AbilityWindShot : ActiveAbility<AbilityConceptWindShot>(), Listener {
    override fun onEnable() {
        psychic.registerEvents(this)
    }

    override fun onChannel(channel: Channel) {
        val player = esper.player
        val location = player.location.apply { y += 3.0 }

        location.world.spawnParticle(
            Particle.CLOUD,
            location,
            32,
            0.5,
            0.0,
            0.5,
            0.03
        )
    }

    override fun onCast(event: PlayerEvent, action: WandAction, target: Any?) {
        val player = event.player
        if (!psychic.consumeMana(concept.cost)) return player.sendActionBar(TestResult.FailedCost.message(this))
        cooldownTime = concept.cooldownTime

        val location = player.eyeLocation
        val windList = mutableListOf<WindProjectile>()

        for (i in 0..8) {
            windList.add(WindProjectile().apply {
                wind =
                    this@AbilityWindShot.psychic.spawnFakeEntity(location, ArmorStand::class.java).apply {
                        updateMetadata<ArmorStand> {
                            isVisible = false
                            isMarker = true
                        }
                        updateEquipment {
                            helmet = ItemStack(Material.WHITE_CONCRETE_POWDER)
                        }
                    }
            })
        }
        val wiggle = concept.windWiggle
        for (i in 0..8) {
            psychic.launchProjectile(location, windList[i])
            windList[i].velocity = location.direction.apply {

                if (wiggle > 0.0) {
                    x += nextDouble(wiggle) - wiggle / 2.0
                    y += nextDouble(wiggle) - wiggle / 2.0
                    z += nextDouble(wiggle) - wiggle / 2.0
                }
            }.multiply(concept.windSpeed)
        }

        val loc = player.location
        loc.world.playSound(loc, Sound.ENTITY_TNT_PRIMED, 3.0F, 2.0F)
    }

    inner class WindProjectile : PsychicProjectile(1200, concept.range) {
        lateinit var wind: FakeEntity

        override fun onMove(movement: Movement) {
            wind.moveTo(movement.to.clone().apply { y -= 1.62 })
        }

        override fun onTrail(trail: Trail) {
            trail.velocity?.let { velocity ->
                val from = trail.from
                val length = velocity.normalizeAndLength()
                val world = from.world

                from.world.rayTrace(
                    from,
                    velocity,
                    length,
                    FluidCollisionMode.NEVER,
                    true,
                    0.7,
                    TargetFilter(esper.player)
                )?.let { rayTraceResult ->
                    if (rayTraceResult.hitBlock != null) remove()
                    val hitLocation = rayTraceResult.hitPosition.toLocation(world)
                    world.spawnParticle(
                        Particle.SNOWFLAKE,
                        hitLocation,
                        24,
                        0.2,
                        0.2,
                        0.2,
                        1.0
                    )

                    rayTraceResult.hitEntity?.let { entity ->
                        if (entity is LivingEntity) {
                            entity.psychicDamage(knockback = concept.knockback)
                        }
                    }

                    world.playSound(hitLocation, Sound.BLOCK_SAND_BREAK, 2.0F, 2.0F)


                }
            }
        }

        override fun onRemove() {
            wind.remove()
        }
    }
}





