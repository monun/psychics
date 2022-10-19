package io.github.anblus.psychics.ability.drone

import io.github.monun.psychics.*
import io.github.monun.psychics.attribute.EsperAttribute
import io.github.monun.psychics.attribute.EsperStatistic
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
import io.github.monun.tap.task.TickerTask
import net.kyori.adventure.text.Component.text
import org.bukkit.*
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Bee
import org.bukkit.entity.LivingEntity
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.EulerAngle
import org.bukkit.util.Vector

// 날아다니면서 폭격
@Name("drone")
class AbilityConceptDrone : AbilityConcept() {
    @Config
    val explosionRange = 2.0

    @Config
    val missileSpeed = 0.5

    @Config
    val missileCool = 2.0

    @Config
    val flySpeed = 0.04f

    init {
        displayName = "드론"
        type = AbilityType.ACTIVE
        cooldownTime = 20000L
        durationTime = 10000L
        range = 24.0
        cost = 50.0
        damage = Damage.of(DamageType.RANGED, EsperStatistic.of(EsperAttribute.ATTACK_DAMAGE to 3.0))
        description = listOf(
            text("능력 사용 시 지속 시간 동안 비행이 가능하며"),
            text("주위 적들에게 폭발하는 유도 미사일을 발사합니다."),
            text("또한 능력 사용 중에는 나약함 디버프가 부여됩니다.")
        )
        wand = ItemStack(Material.BLACK_DYE)
    }

}

class AbilityDrone : ActiveAbility<AbilityConceptDrone>(), Listener {

    var using = false

    var stand: FakeEntity? = null

    var standTask: TickerTask? = null

    var bee: FakeEntity? = null


    override fun onEnable() {
        psychic.registerEvents(this)
        psychic.runTaskTimer({
            if (using && durationTime <= 0L) abilityCancel()
        }, 0L, 1L)
        psychic.runTaskTimer(this::onAttack, 0L, (concept.missileCool * 20).toLong())
        psychic.runTaskTimer({
            if (using) {
                val player = esper.player
                val world = player.world
                val location = player.boundingBox.center.toLocation(world)
                world.playSound(location, Sound.BLOCK_SAND_STEP, 2.0F, 2.0F)
            }
        }, 0L, 3L)
    }

    override fun onCast(event: PlayerEvent, action: WandAction, target: Any?) {
        val player = event.player
        if (!psychic.consumeMana(concept.cost)) return player.sendActionBar(TestResult.FailedCost.message(this))
        cooldownTime = concept.cooldownTime
        durationTime = concept.durationTime
        using = true
        player.allowFlight = true
        player.flySpeed = concept.flySpeed
        player.addPotionEffect(PotionEffect(PotionEffectType.WEAKNESS, (concept.durationTime / 50).toInt(), 255))
        stand = psychic.spawnFakeEntity(player.location, ArmorStand::class.java).apply {
            updateMetadata<ArmorStand> {
                isMarker = true
                isInvisible = true
            }
        }
        standTask = psychic.runTaskTimer({
            stand?.moveTo(player.location.add(0.0, 2.0, 0.0))
        }, 0L, 1L)
        bee = psychic.spawnFakeEntity(player.location, Bee::class.java)
        stand?.addPassenger(bee as FakeEntity)
    }

    fun onDeath(event: PlayerDeathEvent) {
        if (using) abilityCancel()
    }

    fun abilityCancel() {
        val player = esper.player
        using = false
        if (player.gameMode == GameMode.SURVIVAL || player.gameMode == GameMode.ADVENTURE) {
            player.allowFlight = false
            player.isFlying = false
        }
        player.flySpeed = 0.1f
        stand?.remove()
        standTask?.cancel()
        bee?.remove()
    }

    fun onAttack() {
        if (using) {
            val player = esper.player
            val location = player.eyeLocation
            val range = concept.range
            location.getNearbyEntities(
                range.toDouble(), range.toDouble(), range.toDouble()
            ).filter { entity -> player.hostileFilter().test(entity) }
                .forEach { entity ->
                    if (entity is LivingEntity) {
                        val world = location.world
                        val targetLoc = entity.boundingBox.center.toLocation(world)
                        val distance = location.distance(targetLoc)
                        val direction = targetLoc.subtract(location).multiply(1.0 / distance).toVector()
                        world.rayTrace(
                            location,
                            direction,
                            range,
                            FluidCollisionMode.NEVER,
                            true,
                            1.0
                        ) { target -> target == entity }?.let { result ->
                            if (result.hitEntity == entity) {
                                world.playSound(location, Sound.BLOCK_DEEPSLATE_BREAK, 2.0F, 0.1F)
                                val projectile = MissileProjectile().apply {
                                    missile =
                                        this@AbilityDrone.psychic.spawnFakeEntity(location, ArmorStand::class.java).apply {
                                            updateMetadata<ArmorStand> {
                                                isVisible = false
                                                isMarker = true
                                            }
                                            updateEquipment {
                                                helmet = ItemStack(Material.CONDUIT)
                                            }
                                        }
                                    target = entity
                                    dir = direction
                                }
                                psychic.launchProjectile(location, projectile)
                                projectile.velocity = location.direction.multiply(concept.missileSpeed)
                            }
                        }
                    }
                }
        }
    }
    inner class MissileProjectile : PsychicProjectile(1200, concept.range * 1.5) {
        lateinit var missile: FakeEntity

        lateinit var target: LivingEntity

        lateinit var dir: Vector

        override fun onPreUpdate() {
            if (!target.isDead) {
                val targetLoc = target.boundingBox.center.toLocation(location.world)
                val distance = location.distance(targetLoc)
                val direction = targetLoc.subtract(location).multiply(1.0 / distance).toVector()
                velocity = direction.multiply(velocity.length())
            }
            location.world.spawnParticle(Particle.FLAME, location, 1, 0.0, 0.0, 0.0, 0.0)
        }
        override fun onMove(movement: Movement) {
            missile.moveTo(movement.to.clone().apply { y -= 1.62 })
            missile.updateMetadata<ArmorStand> {
                headPose = EulerAngle(ticks * 0.06, 0.0, ticks * 0.1)
            }
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
                    0.1,
                    TargetFilter(esper.player)
                )?.let { remove() }
            }
        }

        override fun onRemove() {
            val world = location.world
            val hitLocation = location
            val range = concept.explosionRange
            world.playSound(hitLocation, Sound.ENTITY_GENERIC_EXPLODE, 1.5F, 0.1F)
            world.spawnParticle(Particle.EXPLOSION_LARGE, hitLocation, 4, range / 2, range / 2, range / 2, 0.0)
            hitLocation.getNearbyEntities(
                range.toDouble(), range.toDouble(), range.toDouble()
            ).filter { entity -> esper.player.hostileFilter().test(entity) }.forEach { entity ->
                if (entity is LivingEntity) {
                    entity.psychicDamage()
                }
            }
            missile.remove()
        }
    }
}






