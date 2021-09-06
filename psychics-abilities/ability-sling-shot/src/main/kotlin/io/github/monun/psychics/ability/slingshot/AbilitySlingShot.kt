package io.github.monun.psychics.ability.slingshot

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
import org.bukkit.*
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack

// 돌을 던지는 능력
@Name("sling-shot")
class AbilityConceptSlingShot : AbilityConcept() {
    @Config
    var stoneSpeed = 4.0

    @Config
    var stoneGravity = 0.02

    @Config
    var stoneSize = 2.0

    init {
        type = AbilityType.ACTIVE
        range = 128.0
        cooldownTime = 80L
        knockback = 0.05
        damage = Damage.of(DamageType.RANGED, EsperStatistic.of(EsperAttribute.ATTACK_DAMAGE to 0.25))
        wand = ItemStack(Material.COBBLESTONE)

        listOf(
            text("돌을 던져 피해를 입힙니다.")
        )
    }
}

class AbilitySlingShot : Ability<AbilityConceptSlingShot>(), Listener {
    override fun onEnable() {
        psychic.registerEvents(this)
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val action = event.action

        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            event.item?.let { item ->
                if (item.type == Material.COBBLESTONE) {
                    val player = esper.player
                    val result = test()

                    if (result != TestResult.Success) {
                        player.sendActionBar(result.message(this))
                        return
                    }

                    cooldownTime = concept.cooldownTime

                    if (player.gameMode != GameMode.CREATIVE) item.amount--

                    val location = player.eyeLocation
                    val projectile = CobblestoneProjectile().apply {
                        cobblestone =
                            this@AbilitySlingShot.psychic.spawnFakeEntity(location, ArmorStand::class.java).apply {
                                updateMetadata<ArmorStand> {
                                    isVisible = false
                                    isMarker = true
                                }
                                updateEquipment {
                                    helmet = ItemStack(Material.COBBLESTONE)
                                }
                            }
                    }

                    psychic.launchProjectile(location, projectile)
                    projectile.velocity = location.direction.multiply(concept.stoneSpeed)

                    val loc = player.location
                    loc.world.playSound(loc, Sound.ENTITY_SNOWBALL_THROW, 1.0F, 0.1F)
                }
            }
        }
    }

    inner class CobblestoneProjectile : PsychicProjectile(1200, concept.range) {
        lateinit var cobblestone: FakeEntity

        override fun onPreUpdate() {
            velocity = velocity.apply { y -= concept.stoneGravity }
        }

        override fun onMove(movement: Movement) {
            cobblestone.moveTo(movement.to.clone().apply { y -= 1.62 })
        }

        override fun onTrail(trail: Trail) {
            trail.velocity?.let { v ->
                val length = v.normalizeAndLength()

                if (length > 0.0) {
                    val start = trail.from
                    val world = start.world

                    world.rayTrace(
                        start, v, length, FluidCollisionMode.NEVER, true, concept.stoneSize / 2.0,
                        TargetFilter(esper.player)
                    )?.let { result ->
                        remove()

                        val hitLocation = result.hitPosition.toLocation(world)
                        world.spawnParticle(
                            Particle.BLOCK_DUST,
                            hitLocation,
                            32,
                            0.0,
                            0.0,
                            0.0,
                            4.0,
                            Material.COBBLESTONE.createBlockData(),
                            true
                        )

                        result.hitEntity?.let { entity ->
                            if (entity is LivingEntity) {
                                val knockback = if (entity.isOnGround) concept.knockback else 0.0
                                entity.psychicDamage(knockback = knockback)
                            }
                        }

                        world.playSound(hitLocation, Sound.BLOCK_STONE_BREAK, 2.0F, 2.0F)
                    }
                }
            }
        }

        override fun onRemove() {
            cobblestone.remove()
        }
    }
}