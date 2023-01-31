package io.github.anblus.psychics.ability.blastball

import io.github.monun.psychics.*
import io.github.monun.psychics.attribute.EsperAttribute
import io.github.monun.psychics.attribute.EsperStatistic
import io.github.monun.psychics.damage.Damage
import io.github.monun.psychics.damage.DamageType
import io.github.monun.psychics.tooltip.TooltipBuilder
import io.github.monun.psychics.tooltip.stats
import io.github.monun.psychics.util.hostileFilter
import io.github.monun.tap.config.Config
import io.github.monun.tap.config.Name
import io.github.monun.tap.fake.FakeEntity
import io.github.monun.tap.fake.Movement
import io.github.monun.tap.math.toRadians
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.FluidCollisionMode
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.LivingEntity
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Vector

// 튕기는 폭탄
@Name("blast-ball")
class AbilityConceptBlastBall : AbilityConcept() {

    @Config
    val ballSpeed = 1.0

    @Config
    val ballGravity = 0.04

    @Config
    val ballSpeedMultipleOnCollision = 0.7

    @Config
    val ballSpeedMultipleOnUpdate = 0.97

    @Config
    val ballSize = 0.75

    @Config
    val blastKnockBack = 0.7

    @Config
    val blastTick = 100

    @Config
    val blastRange = 3.0

    init {
        displayName = "폭발 공"
        type = AbilityType.ACTIVE
        cooldownTime = 1000L
        cost = 20.0
        damage = Damage.of(DamageType.BLAST, EsperStatistic.of(EsperAttribute.ATTACK_DAMAGE to 5.0))
        description = listOf(
            text("능력 사용시 바라보는 방향으로 폭발하는 공을 투척합니다."),
            text("투척한 공은 블록에 튕기다가 일정 시간이 지나거나"),
            text("완전히 멈췄을 경우 폭발합니다.")
        )
        wand = ItemStack(Material.SLIME_BALL)
    }

    override fun onRenderTooltip(tooltip: TooltipBuilder, stats: (EsperStatistic) -> Double) {
        tooltip.stats(blastTick.toDouble() / 20.0) { NamedTextColor.DARK_RED to "폭발 시간" to "초" }
        tooltip.stats(blastRange) { NamedTextColor.LIGHT_PURPLE to "폭발 범위" to "블록" }
    }
}

class AbilityBlastBall : ActiveAbility<AbilityConceptBlastBall>(), Listener {
    companion object {
        private val addXY = arrayOf(Vector(0.0, 0.0, 0.0), Vector(0.25, 0.25, 0.25), Vector(-0.25, 0.25, 0.25), Vector(0.25, 0.25, -0.25), Vector(-0.25, 0.25, -0.25),
            Vector(0.25, -0.25, 0.25), Vector(-0.25, -0.25, 0.25), Vector(0.25, -0.25, -0.25), Vector(-0.25, -0.25, -0.25))
    }

    private var ballProjectileList = arrayListOf<BallProjectile>()

    override fun onCast(event: PlayerEvent, action: WandAction, target: Any?) {
        val player = esper.player

        if (!psychic.consumeMana(concept.cost)) return player.sendActionBar(TestResult.FailedCost.message(this))
        cooldownTime = concept.cooldownTime

        player.swingMainHand()

        val location = player.eyeLocation
        val projectile = BallProjectile().apply {
            val list = mutableListOf<FakeEntity>()
            addXY.forEach { addVector ->
                if (addVector == Vector(0.0, 0.0, 0.0)) {
                    list.add(
                        this@AbilityBlastBall.psychic.spawnFakeEntity(
                            location.clone().add(addVector),
                            ArmorStand::class.java
                        ).apply {
                            updateMetadata<ArmorStand> {
                                isVisible = false
                                isMarker = true
                            }
                            updateEquipment {
                                helmet = ItemStack(Material.TNT)
                            }
                        })
                } else {
                    list.add(
                        this@AbilityBlastBall.psychic.spawnFakeEntity(
                            location.clone().add(addVector),
                            ArmorStand::class.java
                        ).apply {
                            updateMetadata<ArmorStand> {
                                isVisible = false
                                isMarker = true
                            }
                            updateEquipment {
                                helmet = ItemStack(Material.SLIME_BLOCK)
                            }
                        })
                }
            }
            balls = list
            velocity = location.direction.clone().multiply(concept.ballSpeed)
        }

        psychic.launchProjectile(location, projectile)
        ballProjectileList.add(projectile)

        val loc = player.location
        loc.world.playSound(loc, Sound.ENTITY_SLIME_SQUISH, 2.0F, 1.5F)
    }


    inner class BallProjectile : PsychicProjectile(concept.blastTick, 9999.0) {
        lateinit var balls: MutableList<FakeEntity>

        private var normalVector: Vector? = null

        override fun onPreUpdate() {
            velocity = velocity.multiply(concept.ballSpeedMultipleOnUpdate)

            val dir = velocity.normalize()
            val length = velocity.length()

            if (length > 0.0) {
                val start = location.add(velocity.normalize().multiply(concept.ballSize))
                val from = location.subtract(velocity.normalize().multiply(concept.ballSize))
                val world = start.world

                world.spawnParticle(Particle.SLIME, from, 1, 0.0, 0.0, 0.0, 0.01)

                val range = concept.ballSize * 1.5
                if (location.getNearbyEntities(range, range, range).any { entity -> esper.player.hostileFilter().test(entity) }) {
                    blast()
                    remove()
                }

                val hitResult = world.rayTraceBlocks(
                    start, dir, length, FluidCollisionMode.NEVER, true
                )

                var hitLocation = hitResult?.hitPosition?.toLocation(world)

                hitLocation?.distance(start)?.also {
                    if (it <= 0.001) {
                        hitLocation = hitResult?.hitPosition?.toLocation(world)
                    }
                }

                hitResult?.hitBlockFace?.direction?.let { normal ->
                    if (length >= concept.ballGravity) {
                        normalVector = normal
                        val volume = (length * 2).coerceAtMost(2.0).toFloat()
                        world.playSound(hitLocation!!, Sound.ENTITY_SLIME_JUMP, volume, 1.5F)
                    } else {
                        blast()
                        remove()
                    }
                }?: run {
                    velocity = velocity.apply { y -= concept.ballGravity}
                }
            } else {
                blast()
                remove()
            }
        }

        override fun onMove(movement: Movement) {
            balls.forEachIndexed { i, ball ->
                if (i == 0) {
                    ball.moveTo(movement.to.clone().apply { y -= 1.62 })
                } else {
                    ball.moveTo(movement.to.clone().add(addXY[i].rotateAroundX((((i-1) * 45.0) + ticks).toRadians()).rotateAroundY((((i-1) * 45.0) + ticks).toRadians()).rotateAroundZ((((i-1) * 45.0) + ticks).toRadians())).apply { y -= 1.62 })
                }
            }
        }

        override fun onPostUpdate() {
            normalVector?.let { normal ->
                velocity = velocity.reflect(normal)
                velocity = velocity.multiply(concept.ballSpeedMultipleOnCollision)
                normalVector = null
            }
        }

        private fun blast() {
            val world = location.world
            val location = location
            val range = concept.blastRange
            world.playSound(location, Sound.BLOCK_SLIME_BLOCK_BREAK, 2.0F, 1.0F)
            world.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 0.6F, 1.2F)
            world.spawnParticle(Particle.EXPLOSION_LARGE, location, 16, range / 2.0, range / 2.0, range / 2.0, 0.0)
            location.getNearbyEntities(
                range, range, range
            ).filter { entity -> esper.player.hostileFilter().test(entity) }.forEach { entity ->
                if (entity is LivingEntity) {
                    entity.psychicDamage(knockback = concept.blastKnockBack)
                }
            }
        }

        override fun onRemove() {
            if (ticks >= maxTicks) blast()
            balls.forEach { it.remove() }
        }
    }

    private fun Vector.reflect(normal: Vector): Vector = add(normal.clone().multiply(2.0 * clone().multiply(-1.0).dot(normal)))

}






