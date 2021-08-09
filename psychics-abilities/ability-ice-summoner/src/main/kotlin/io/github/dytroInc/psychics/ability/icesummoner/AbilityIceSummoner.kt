package io.github.dytroInc.psychics.ability.icesummoner

import com.destroystokyo.paper.event.entity.EntityJumpEvent
import com.destroystokyo.paper.event.player.PlayerJumpEvent
import io.github.monun.psychics.Ability
import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.PsychicProjectile
import io.github.monun.psychics.TestResult
import io.github.monun.psychics.attribute.EsperAttribute
import io.github.monun.psychics.damage.Damage
import io.github.monun.psychics.damage.DamageType
import io.github.monun.psychics.util.TargetFilter
import io.github.monun.psychics.util.hostileFilter
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
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector
import java.util.*

// 지정한 대상에게 폭죽 효과와 피해를 입히는 능력
@Name("ice-summoner")
class AbilityConceptIceSummoner : AbilityConcept() {
    @Config
    val snowballRange = 10
    @Config
    val speed = 5.0
    @Config
    val maxFrozenTime = 10.0
    init {
        cooldownTime = 25000L
        range = 128.0
        cost = 45.0
        damage = Damage.of(DamageType.RANGED, EsperAttribute.ATTACK_DAMAGE to 5.0)
        description = listOf(
            text("우클릭 씨에 보는 방향으로 투사체를 날립니다."),
            text("투사체가 적중한 곳의 주위 블록들은 얼려지고"),
            text("주위 엔티티들은 ${maxFrozenTime}초 동안 얼려집니다.")
        )
        displayName = "얼음술사"
        wand = ItemStack(Material.STICK)
    }
}

class AbilityIceSummoner : Ability<AbilityConceptIceSummoner>(), Listener {
    companion object {
        private val ignoredBlocks: EnumSet<Material> = EnumSet.of(
            Material.PACKED_ICE, Material.BEDROCK
        )
        private val waterPlants: EnumSet<Material> = EnumSet.of(
            Material.SEAGRASS, Material.TALL_SEAGRASS, Material.KELP, Material.KELP_PLANT
        )
    }

    var frozenEntities = ArrayList<UUID>()
    var frozenTime = 0
    override fun onEnable() {
        psychic.registerEvents(this)
        psychic.runTaskTimer(this::tick, 0, 1)
    }
    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        val action = event.action
        if(action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            event.item?.let { item ->
                if(item.type == Material.STICK) {
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
                    frozenEntities.clear()
                    frozenTime = (concept.maxFrozenTime * 20.0).toInt()
                    val location = player.eyeLocation
                    val projectile = IceProjectile().apply {
                        projectile =
                            this@AbilityIceSummoner.psychic.spawnFakeEntity(location, ArmorStand::class.java).apply {
                                updateMetadata<ArmorStand> {
                                    isVisible = false
                                    isMarker = true
                                }
                                updateEquipment {
                                    helmet = ItemStack(Material.SNOW_BLOCK)
                                }
                            }
                    }

                    psychic.launchProjectile(location, projectile)
                    projectile.velocity = location.direction.multiply(concept.speed)

                    val loc = player.location
                    loc.world.playSound(loc, Sound.ENTITY_SNOWBALL_THROW, 1.0F, 1.0F)
                }
            }
        }
    }

    private fun tick() {
        if(frozenTime > 0) {
            frozenEntities.forEach {
                Bukkit.getEntity(it)?.let { entity ->
                    entity.freezeTicks = entity.maxFreezeTicks
                }
            }
            frozenTime--
            if(frozenTime == 0) frozenEntities.clear()
        }
    }

    @EventHandler
    fun onJump(event: EntityJumpEvent) {
        val entity = event.entity
        if(entity.uniqueId in frozenEntities) event.isCancelled = true
    }
    @EventHandler
    fun onJump(event: PlayerJumpEvent) {
        val entity = event.player
        if(entity.uniqueId in frozenEntities) event.isCancelled = true
    }

    inner class IceProjectile : PsychicProjectile(1200, concept.range) {
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
                                Particle.SNOWFLAKE,
                                hitLocation,
                                64,
                                0.1,
                                0.1,
                                0.1,
                                5.0,
                                null,
                                true
                            )
                            world.playSound(hitLocation, Sound.ENTITY_PLAYER_HURT_FREEZE, 2.0f, 0.1f)
                            val range = concept.snowballRange
                            it.toLocation(world).getNearbyEntities(
                                range.toDouble(), range.toDouble(), range.toDouble()
                            ).forEach { entity ->
                                if(esper.player.hostileFilter().test(entity))  {
                                    frozenEntities.add(entity.uniqueId)
                                    if(entity is LivingEntity) {
                                        entity.psychicDamage()
                                        val time = (concept.maxFrozenTime * 20.0).toInt()
                                        entity.addPotionEffects(mutableListOf(
                                            PotionEffect(PotionEffectType.SLOW, time, 100),
                                            PotionEffect(PotionEffectType.SLOW_DIGGING, time, 100),
                                            PotionEffect(PotionEffectType.WEAKNESS, time, 100)
                                        ))
                                    }
                                }

                            }
                            for(x in -range..range) {
                                for(y in -range..range) {
                                    for(z in -range..range) {
                                        val vector = it.clone().add(Vector(x, y, z))
                                        vector.toLocation(world).block.let { b ->
                                            if(b.isSolid && b.type !in ignoredBlocks) {
                                                b.type = Material.SNOW_BLOCK
                                            } else if(b.isLiquid) {
                                                b.type = Material.PACKED_ICE
                                            } else if(b.type in waterPlants) {
                                                b.type = Material.PACKED_ICE
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
                Particle.SNOWFLAKE,
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
    }
}