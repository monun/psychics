package io.github.anblus.psychics.ability.hoespin

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
import io.github.monun.tap.math.toRadians
import io.github.monun.tap.task.TickerTask
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
import org.bukkit.util.EulerAngle
import org.bukkit.util.Vector
import java.util.*
import kotlin.math.PI

// 돌아오지 않는 부메랑 던지듯
@Name("hoe-spin")
class AbilityConceptHoeSpin : AbilityConcept() {

    @Config
    val hoeProjectileSpeed = 0.07

    @Config
    val hoeProjectileSpinSpeed = 0.4

    @Config
    val hoeProjectileSpinAngle = 12.0

    @Config
    val invincibleTick = 15

    @Config
    val hoeProjectileSize = 0.8

    init {
        displayName = "스핀"
        type = AbilityType.ACTIVE
        cooldownTime = 500L
        cost = 10.0
        range = 128.0
        damage = Damage.of(DamageType.RANGED, EsperStatistic.of(EsperAttribute.ATTACK_DAMAGE to 1.5))
        description = listOf(
            text("능력 발동시 바라보는 방향으로 괭이 투사체를 투사합니다."),
            text("괭이 투사체는 회전하며 날아가 닿은 상대에게 피해를 입힙니다.")
        )
        wand = ItemStack(Material.STICK)

    }
}

class AbilityHoeSpin : ActiveAbility<AbilityConceptHoeSpin>(), Listener {

    private var stabbedEntityList = arrayListOf<StabbedEntity>()

    private var hoeProjectileList = arrayListOf<HoeProjectile>()

    override fun onCast(event: PlayerEvent, action: WandAction, target: Any?) {
        val player = esper.player

        if (!psychic.consumeMana(concept.cost)) return player.sendActionBar(TestResult.FailedCost.message(this))
        cooldownTime = concept.cooldownTime

        val location = player.eyeLocation
        val projectile = HoeProjectile().apply {
            hoe =
                this@AbilityHoeSpin.psychic.spawnFakeEntity(location, ArmorStand::class.java).apply {
                    updateMetadata<ArmorStand> {
                        isVisible = false
                        isMarker = true
                    }
                    updateEquipment {
                        helmet = ItemStack(Material.NETHERITE_HOE)
                    }
                }
            pitch = location.pitch.toDouble()
            originalDir = location.direction
            angle = 270.0
        }

        psychic.launchProjectile(location, projectile)
        hoeProjectileList.add(projectile)

        val loc = player.location
        loc.world.playSound(loc, Sound.ITEM_TRIDENT_THROW, 1.5F, 1.4F)
    }


    inner class HoeProjectile : PsychicProjectile(12000, concept.range) {
        lateinit var hoe: FakeEntity

        lateinit var originalDir: Vector

        var pitch: Double = 0.0

        var angle: Double = 0.0

        override fun onPreUpdate() {
            angle += concept.hoeProjectileSpinAngle
            if (angle >= 360.0) angle -= 360.0
            val direction = originalDir.clone().rotateAroundY(angle.toRadians())
            velocity = direction.multiply(concept.hoeProjectileSpinSpeed).add(originalDir.clone().multiply(concept.hoeProjectileSpeed)).apply { y *= 0.25 }
        }

        override fun onMove(movement: Movement) {
            hoe.moveTo(movement.to.clone().apply {
                y -= 1.62
            })
            hoe.updateMetadata<ArmorStand> {
                headPose = EulerAngle(PI * ((pitch / 180) + 0.5), 0.0, 0.0)
            }
        }

        override fun onTrail(trail: Trail) {
            trail.velocity?.let { v ->
                val length = v.normalizeAndLength()

                if (length > 0.0) {
                    val start = trail.from
                    val world = start.world

                    world.spawnParticle(Particle.CRIT, start, 0, 0.0, 0.0, 0.0, 0.01)

                    world.rayTrace(
                        start, v, length, FluidCollisionMode.NEVER, true, concept.hoeProjectileSize,
                        TargetFilter(esper.player)
                    )?.let { hitResult ->
                        val hitLocation = hitResult.hitPosition.toLocation(world)

                        hitResult.hitEntity?.let { target ->
                            if (target is LivingEntity) {
                                 stabbedEntityList.find { it.uuid == target.uniqueId && it.hoe == this }?: run {
                                    world.playSound(hitLocation, Sound.ITEM_TRIDENT_HIT, 2.0F, 1.6F)
                                    world.spawnParticle(Particle.FALLING_LAVA, target.boundingBox.center.toLocation(world), 9, 0.4, 0.4, 0.4, 0.003)
                                    stabbedEntityList.add(StabbedEntity(target.uniqueId, this))
                                    target.psychicDamage()
                                }
                            }
                        }?: run {
                            world.playSound(hitLocation, Sound.ITEM_TRIDENT_HIT_GROUND, 2.0F, 0.6F)
                            world.spawnParticle(Particle.ITEM_CRACK, hitLocation, 16, 0.5, 0.5, 0.5, 0.1, ItemStack(Material.NETHERITE_HOE))
                            remove()
                        }
                    }
                }
            }
        }

        override fun onRemove() {
            hoeProjectileList.remove(this)
            hoe.remove()
        }
    }

    inner class StabbedEntity(val uuid: UUID, val hoe: HoeProjectile, private var invincibleTick: Int = concept.invincibleTick) {
        private var timer: TickerTask? = null

        init {
            timer = psychic.runTaskTimer({
                invincibleTick --
                if (invincibleTick <= 0) {
                    stabbedEntityList.remove(this)
                    timer?.cancel()
                }
            }, 1L , 1L)
        }
    }
}






