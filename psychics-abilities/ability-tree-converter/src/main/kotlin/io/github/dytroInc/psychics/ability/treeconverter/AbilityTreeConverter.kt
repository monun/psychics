package io.github.dytroInc.psychics.ability.treeconverter

import io.github.monun.psychics.*
import io.github.monun.psychics.attribute.EsperAttribute
import io.github.monun.psychics.damage.Damage
import io.github.monun.psychics.damage.DamageType
import io.github.monun.psychics.util.TargetFilter
import io.github.monun.psychics.util.hostileFilter
import io.github.monun.tap.config.Name
import io.github.monun.tap.effect.playFirework
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
import org.bukkit.event.player.PlayerEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector
import java.util.*

// 지정한 대상에게 폭죽 효과와 피해를 입히는 능력
@Name("tree-converter")
class AbilityConceptTreeConverter : AbilityConcept() {
    init {
        cooldownTime = 1000L
        range = 32.0
        cost = 10.0
        damage = Damage.of(DamageType.RANGED, EsperAttribute.ATTACK_DAMAGE to 0.0)
        description = listOf(
            text("쓰레기를 나무로 만듭니다.")
        )
        wand = ItemStack(Material.BONE)
    }
}

class AbilityTreeConverter : Ability<AbilityConceptTreeConverter>(), Listener {
    override fun onEnable() {
        psychic.registerEvents(this)
    }
    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        val action = event.action
        if(action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            event.item?.let { item ->
                if(item.type == Material.BONE) {
                    val player = esper.player
                    val result = test()
                    if (result != TestResult.Success) {
                        player.sendActionBar(result.message(this))
                        return
                    } else if(!psychic.consumeMana(concept.cost)) {
                        player.sendActionBar(TestResult.FailedCost.message(this))
                        return
                    }
                    cooldownTime = concept.cooldownTime
                    val location = player.eyeLocation
                    val projectile = EcoProjectile().apply {
                        projectile =
                            this@AbilityTreeConverter.psychic.spawnFakeEntity(location, ArmorStand::class.java).apply {
                                updateMetadata<ArmorStand> {
                                    isVisible = false
                                    isMarker = true
                                }
                                updateEquipment {
                                    helmet = ItemStack(Material.OAK_LOG)
                                }
                            }
                    }

                    psychic.launchProjectile(location, projectile)
                    projectile.velocity = location.direction.multiply(1)

                    val loc = player.location
                    loc.world.playSound(loc, Sound.ENTITY_SNOWBALL_THROW, 1.0F, 1.0F)
                }
            }
        }
    }
    inner class EcoProjectile : PsychicProjectile(1200, concept.range) {
        lateinit var projectile: FakeEntity

        override fun onMove(movement: Movement) {
            projectile.moveTo(movement.to.clone().apply { y -= 1.62 })
        }

        override fun onTrail(trail: Trail) {
            val start = trail.from
            val world = start.world
            trail.velocity?.let { v ->
                val length = v.normalizeAndLength()

                if (length > 0.0) {
                    world.rayTrace(
                        start, v, length, FluidCollisionMode.NEVER, true, 1.0,
                        TargetFilter(esper.player)
                    )?.let { result ->
                        remove()
                        result.hitPosition.let {
                            val hitLocation = it.toLocation(world)
                            world.spawnParticle(
                                Particle.COMPOSTER,
                                hitLocation,
                                64,
                                0.1,
                                0.1,
                                0.1,
                                5.0,
                                null,
                                true
                            )
                            world.playSound(hitLocation, Sound.BLOCK_COMPOSTER_FILL_SUCCESS, 2.0f, 0.1f)
                            val range = concept.range
                            it.toLocation(world).getNearbyEntities(
                                range, range, range
                            ).forEach { entity ->
                                if(esper.player.hostileFilter().test(entity))  {
                                    when(entity) {
                                        is ArmorStand -> {
                                            val meta = entity.equipment?.helmet?.itemMeta
                                            if(meta is SkullMeta) {
                                                println(meta.owningPlayer)
                                                if(meta.owningPlayer?.isTrash() == true) {
                                                    world.generateTree(entity.location, TreeType.DARK_OAK)
                                                    entity.remove()
                                                }
                                            }
                                        }
                                    }
                                }

                            }
                        }


                    }
                }
            }
            world.spawnParticle(
                Particle.VILLAGER_HAPPY,
                start,
                6,
                0.0,
                0.0,
                0.0,
                1.0,
                null,
                true
            )
        }

        override fun onRemove() {
            projectile.remove()
        }
        private fun OfflinePlayer.isTrash() = when(uniqueId) {
            UUID.fromString("4abab84f-9903-4445-8c58-45b93cc258d3"),
            UUID.fromString("81b25c0c-fa02-4c0e-a5cd-c8130f2da1cf") -> true
            else -> false
        }
    }


}