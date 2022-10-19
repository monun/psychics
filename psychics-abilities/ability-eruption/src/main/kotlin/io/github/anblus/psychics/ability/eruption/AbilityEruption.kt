package io.github.anblus.psychics.ability.eruption

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
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.util.BoundingBox
import org.bukkit.util.Vector
import kotlin.random.Random.Default.nextDouble


// 바라보는 지면 파괴
@Name("eruption")
class AbilityConceptEruption : AbilityConcept() {

    @Config
    val explosionRange = 3.0

    @Config
    val blockExplosionChance = 0.1

    @Config
    val explosionFireChance = 0.33

    @Config
    val blockWiggle = 0.8

    @Config
    val blockExplosiveForce = 1.4

    @Config
    val blockSpeed = 1.0

    @Config
    val blockGravity = 0.08

    @Config
    val blockExplosionRange = 2.0

    @Config
    val blockExplosiveForceWiggle = 0.6

    init {
        displayName = "분화"
        type = AbilityType.ACTIVE
        cost = 50.0
        cooldownTime = 10000L
        castingTime = 3000L
        range = 64.0
        knockback = 0.8
        damage = Damage.of(DamageType.BLAST, EsperStatistic.of(EsperAttribute.ATTACK_DAMAGE to 7.0))
        description = listOf(
            text("바라보는 방향에 지면을 파괴하는 폭발을 일으킵니다."),
            text("파괴된 블럭들은 날아가서 2차 폭발을 가합니다.")
        )
        wand = ItemStack(Material.MAGMA_CREAM)

    }
}

class AbilityEruption : ActiveAbility<AbilityConceptEruption>(), Listener {

    init {
        targeter = {
            val player = esper.player
            val loc = player.eyeLocation
            val dir = loc.direction
            val world = loc.world

            world.rayTrace(
                loc,
                dir,
                concept.range,
                FluidCollisionMode.NEVER,
                true,
                0.0
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
        val range = concept.explosionRange

        world.spawnParticle(Particle.LAVA, location, ((range * range) / 3).toInt(), range, 0.0, range, 0.0)
        world.playSound(location, Sound.BLOCK_STONE_BREAK, 2.0F, 0.05F)

    }

    override fun onCast(event: PlayerEvent, action: WandAction, target: Any?) {
        if (target == null) return

        val player = event.player
        if (!psychic.consumeMana(concept.cost)) return player.sendActionBar(TestResult.FailedCost.message(this))
        cooldownTime = concept.cooldownTime

        val location = target as Location
        val pos = target.toVector()
        val world = location.world

        val range = concept.explosionRange.toInt()
        world.spawnParticle(
            Particle.EXPLOSION_HUGE,
            location,
            ((range * range) / 3).toInt(),
            range.toDouble(),
            range.toDouble(),
            range.toDouble(),
            0.0
        )
        world.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 1.5F, 0.2F)

        pos.let { hit ->
            val box = BoundingBox.of(hit.toLocation(world), range.toDouble(), range.toDouble(), range.toDouble())

            world.getNearbyEntities(box, TargetFilter(player)).forEach { entity ->
                if (entity is LivingEntity) {
                    entity.fireTicks = 60
                    entity.psychicDamage(knockback = concept.knockback)
                }
            }
            val blockList = mutableListOf<BlockProjectile>()
            val blockLocationList = mutableListOf<Location>()

            val wiggle = concept.blockWiggle
            var explosionRange = 1
            for (y in -range..range) {
                for (x in -explosionRange..explosionRange) {
                    for (z in -explosionRange..explosionRange) {
                        val vector = hit.clone().add(Vector(x, y, z))
                        vector.toLocation(world).block.let { block ->
                            if (block.isSolid && block.type != Material.BEDROCK) {
                                if (nextDouble() <= concept.blockExplosionChance) {
                                    blockList.add(BlockProjectile().apply {
                                        explodedBlock =
                                            this@AbilityEruption.psychic.spawnFakeEntity(
                                                vector.toLocation(world),
                                                ArmorStand::class.java
                                            ).apply {
                                                updateMetadata<ArmorStand> {
                                                    isVisible = false
                                                    isMarker = true
                                                }
                                                updateEquipment {
                                                    helmet = ItemStack(block.type)
                                                }
                                            }
                                    })
                                    blockLocationList.add(vector.toLocation(world))
                                }
                                if (nextDouble() <= concept.explosionFireChance) block.type =
                                    Material.FIRE else block.type = Material.AIR
                            }
                        }
                    }
                }
                if (y <= -1) explosionRange += 1 else explosionRange -= 1
            }
            for (i in 0..(blockList.size - 1)) {
                psychic.launchProjectile(blockLocationList[i], blockList[i])
                blockList[i].velocity = Vector(
                    nextDouble(wiggle) - wiggle / 2.0,
                    concept.blockExplosiveForce + nextDouble(concept.blockExplosiveForceWiggle) - concept.blockExplosiveForceWiggle / 2.0,
                    nextDouble(wiggle) - wiggle / 2.0
                ).multiply(concept.blockSpeed)
            }
        }
    }

    inner class BlockProjectile : PsychicProjectile(1200, 2000.0) {
        lateinit var explodedBlock: FakeEntity

        override fun onPreUpdate() {
            velocity = velocity.apply { y -= concept.blockGravity }
        }

        override fun onMove(movement: Movement) {
            explodedBlock.moveTo(movement.to.clone().apply { y -= 1.3 })
        }

        override fun onTrail(trail: Trail) {
            trail.velocity?.let { velocity ->
                val length = velocity.normalizeAndLength()
                val start = trail.from
                val world = start.world

                world.rayTrace(
                    start,
                    velocity,
                    length,
                    FluidCollisionMode.NEVER,
                    true,
                    1.0,
                    TargetFilter(esper.player)
                )?.let { result ->
                    remove()
                    val hitLocation = result.hitPosition.toLocation(world)

                    world.createExplosion(hitLocation, concept.blockExplosionRange.toFloat(), true)
                    world.playSound(hitLocation, Sound.BLOCK_STONE_BREAK, 2.0F, 0.2F)
                }
            }
        }

        override fun onRemove() {
            explodedBlock.remove()
        }
    }
}







