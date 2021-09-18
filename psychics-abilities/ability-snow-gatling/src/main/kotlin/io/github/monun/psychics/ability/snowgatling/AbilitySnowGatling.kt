package io.github.monun.psychics.ability.snowgatling

import com.destroystokyo.paper.event.entity.ProjectileCollideEvent
import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.ActiveAbility
import io.github.monun.psychics.attribute.EsperAttribute
import io.github.monun.psychics.damage.Damage
import io.github.monun.psychics.damage.DamageType
import io.github.monun.tap.config.Config
import io.github.monun.tap.config.Name
import io.github.monun.tap.event.EntityProvider
import io.github.monun.tap.event.TargetEntity
import io.github.monun.tap.task.TickerTask
import net.kyori.adventure.text.Component.empty
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Snowball
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.player.PlayerEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import kotlin.math.min
import kotlin.random.Random.Default.nextDouble

// 눈기관총
@Name("snow-gatling")
class AbilityConceptSnowGatling : AbilityConcept() {
    @Config
    var snowballWiggle = 0.1

    @Config
    var snowballPerTick = 3

    @Config
    var snowballSpeed = 2.0

    @Config
    var slowTicks = 20

    @Config
    var slowdownChance = 0.05

    @Config
    var maxSlowLevel = 6

    init {
        cooldownTime = 60000L
        durationTime = 5000L
        damage = Damage.of(DamageType.RANGED, EsperAttribute.ATTACK_DAMAGE to 0.3)
        wand = ItemStack(Material.SNOWBALL)
    }
}

class AbilitySnowGatling : ActiveAbility<AbilityConceptSnowGatling>(), Listener {
    override fun onEnable() {
        psychic.registerEvents(this)
    }

    @TargetEntity(EntityProvider.EntityDamageByEntity.Shooter::class)
    @EventHandler(ignoreCancelled = true)
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        val damager = event.damager

        if (damager is Snowball && damager.customName() != null) {
            val entity = event.entity

            if (entity is LivingEntity) {
                event.isCancelled = true
                entity.psychicDamage()

                val concept = concept

                if (nextDouble() < concept.slowdownChance) {
                    var slow = entity.getPotionEffect(PotionEffectType.SLOW)

                    if (slow != null) {
                        slow.withAmplifier(min(concept.maxSlowLevel, slow.amplifier + 1))
                    } else {
                        slow = PotionEffect(PotionEffectType.SLOW, concept.slowTicks, 0, false, false, true)
                    }

                    entity.addPotionEffect(slow)
                    val location = entity.location
                    val world = location.world

                    @Suppress("DEPRECATION")
                    world.spawnParticle(Particle.SPELL_INSTANT, location, 100)
                }
            }
        }
    }

    @TargetEntity(Shooter::class)
    @EventHandler(ignoreCancelled = true)
    fun onProjectileCollide(event: ProjectileCollideEvent) {
        val entity = event.entity

        if (entity is Snowball && entity.customName() != null) {
            val victim = event.collidedWith

            if (victim is LivingEntity) {
                victim.noDamageTicks = 0
            }
        }
    }

//    @TargetEntity(EntityProvider.ProjectileHit.Shooter::class)
//    @EventHandler(ignoreCancelled = true)
//    fun onProjectileCollide(event: ProjectileHitEvent) {
//        val location = event.hitBlock?.location ?: return
//        location.world.createExplosion(location, 3.0F)
//    }

    override fun onCast(event: PlayerEvent, action: WandAction, target: Any?) {
        val concept = concept

        cooldownTime = concept.cooldownTime
        durationTime = concept.durationTime

        val task = GatlingTask()
        task.task = psychic.runTaskTimer(task, 0L, 1L)
    }

    inner class GatlingTask : Runnable {
        lateinit var task: TickerTask

        override fun run() {
            if (durationTime <= 0L) {
                task.cancel()
                return
            }

            val location = esper.player.location
            val direction = location.direction.multiply(concept.snowballSpeed)
            val wiggle = concept.snowballWiggle
            val count = concept.snowballPerTick
            for (i in 0 until count) {
                val snowball = esper.player.launchProjectile(Snowball::class.java, direction.clone().apply {

                    if (wiggle > 0.0) {
                        x += nextDouble(wiggle) - wiggle / 2.0
                        y += nextDouble(wiggle) - wiggle / 2.0
                        z += nextDouble(wiggle) - wiggle / 2.0
                    }
                })

                snowball.customName(empty())
            }

            location.world.playSound(location, Sound.ENTITY_SNOWBALL_THROW, 0.2F, 0.2F)
        }
    }
}

class Shooter : EntityProvider<ProjectileCollideEvent> {
    override fun getFrom(event: ProjectileCollideEvent): Entity? {
        return event.entity.shooter.takeIf { it is Entity } as Entity?
    }
}