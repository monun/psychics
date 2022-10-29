package io.github.anblus.psychics.ability.electrician

import io.github.monun.psychics.*
import io.github.monun.psychics.attribute.EsperAttribute
import io.github.monun.psychics.attribute.EsperStatistic
import io.github.monun.psychics.damage.Damage
import io.github.monun.psychics.damage.DamageType
import io.github.monun.psychics.tooltip.TooltipBuilder
import io.github.monun.psychics.tooltip.stats
import io.github.monun.psychics.tooltip.template
import io.github.monun.psychics.util.TargetFilter
import io.github.monun.tap.config.Config
import io.github.monun.tap.config.Name
import io.github.monun.tap.fake.FakeEntity
import io.github.monun.tap.fake.Movement
import io.github.monun.tap.fake.Trail
import io.github.monun.tap.fake.invisible
import io.github.monun.tap.math.normalizeAndLength
import io.github.monun.tap.math.toRadians
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.*
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Vector
import java.util.*
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.sin

// 전기를 다루는 능력
@Name("electrician")
class AbilityConceptElectrician : AbilityConcept() {

    @Config
    val manaRegenPerHalf = 4

    @Config
    val manaExtinctionPerHalf = 5

    @Config
    val manaUseFrequencyIncreasePerMana = 7

    @Config
    val powerGenerationCost = 40.0

    @Config
    val powerGenerationRange = 6.0

    @Config
    val powerGenerationDamage = Damage.of(DamageType.BLAST, EsperStatistic.of(EsperAttribute.ATTACK_DAMAGE to 4.0))

    @Config
    val powerGenerationKnockback = 0.6

    @Config
    val powerTransmissionCost = 20.0

    @Config
    val lightningSpeed = 0.8

    @Config
    val electronSpeed = 0.8

    @Config
    val electronGravity = 0.04

    init {
        displayName = "전기술사"
        type = AbilityType.ACTIVE
        cooldownTime = 500L
        description = listOf(
            text("${ChatColor.YELLOW}웅크리기 | 충전"),
            text("  마나를 충전합니다."),
            text("  오랫동안 마나의 활동이 빈번할 경우 마나가 천천히 누전됩니다."),
            text("  충전 중 데미지를 받았을 경우 마나가 전부 누전됩니다."),
            text("${ChatColor.RED}좌클릭 | 발전"),
            text("  플레이어의 위치 기준 사방으로 전격을 날려 닿은 적들에게"),
            text("  데미지와 넉백을 입힙니다."),
            text("${ChatColor.GOLD}우클릭 | 송전"),
            text("  이동 방향으로 빠르게 포물선을 그리며 짧은 거리를 도약합니다. "),
            text("  능력 사용 중에는 낙하 데미지를 받지 않습니다."),
            text("${ChatColor.GRAY}*마나 재생을 0으로 하는 것을 추천드립니다.")
        )
        wand = ItemStack(Material.YELLOW_DYE)

    }
    
    override fun onRenderTooltip(tooltip: TooltipBuilder, stats: (EsperStatistic) -> Double) {
        tooltip.stats(manaRegenPerHalf * 2) { NamedTextColor.YELLOW to "초당 마나 회복" to null }
        tooltip.stats(manaExtinctionPerHalf * 2) { NamedTextColor.GREEN to "초당 마나 누전" to null }
        tooltip.stats(powerGenerationCost) { NamedTextColor.DARK_AQUA to "발전 마나 소모" to null }
        tooltip.stats(powerGenerationRange) { NamedTextColor.LIGHT_PURPLE to "발전 사거리" to "블록" }
        tooltip.stats(text("발전").color(NamedTextColor.DARK_PURPLE).decorate(TextDecoration.BOLD), powerGenerationDamage) { NamedTextColor.DARK_PURPLE to "powerDamage" }
        tooltip.template("powerDamage", stats(powerGenerationDamage.stats))
        tooltip.stats(powerTransmissionCost) { NamedTextColor.DARK_AQUA to "송전 마나 소모" to null }
    }
}

class AbilityElectrician : Ability<AbilityConceptElectrician>(), Listener {
    companion object {
        private val sinPerTwentyAngle: Array<Double> = Array((360 / 20)) {i -> sin((i * 20.0).toRadians())}
        private val cosPerTwentyAngle: Array<Double> = Array((360 / 20)) {i -> cos((i * 20.0).toRadians())}
    }

    private var manaRegenTick: Int = 10

    private var manaUseFrequency: Int = 0

    private var manaExtinctionTick: Int = 0

    private var isCharging: Boolean = false

    private var entitiesAffectedByLightning: MutableList<UUID> = mutableListOf()

    private var isTransmissing: Boolean = false


    override fun onEnable() {
        psychic.registerEvents(this)
        psychic.runTaskTimer(this::onTick, 0L, 1L)
    }

    private fun onTick() {
        val player = esper.player
        if (player.isSneaking && (player as Entity).isOnGround && player.inventory.itemInMainHand.type == concept.wand?.type && psychic.mana < psychic.concept.mana) {
            if (manaRegenTick <= 0) {
                isCharging = true
                manaRegenTick = 10
                manaUseFrequency += concept.manaRegenPerHalf * concept.manaUseFrequencyIncreasePerMana
                psychic.mana += concept.manaRegenPerHalf
            } else manaRegenTick -= 1
        } else if (isCharging) {
            isCharging = false
            manaRegenTick = 10
        }
        if (psychic.mana > 0 && manaUseFrequency <= 0) {
            if (manaExtinctionTick <= 0) {
                manaExtinctionTick = 10
                psychic.mana -= concept.manaExtinctionPerHalf

            } else manaExtinctionTick -= 1
        } else manaExtinctionTick = 0
        if (manaUseFrequency > 0) manaUseFrequency -= 1
    }

    @EventHandler
    fun onEntityDamage(event: EntityDamageEvent) {
        if (isCharging) {
            val player = esper.player
            val world = player.world
            val location = player.location

            psychic.mana = 0.0

            repeat(3) {
                psychic.runTask({world.playSound(location, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 2.0F, 2.0F)}, (it * 4).toLong())
            }
        }
    }
    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val action = event.action

        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            event.item?.let { item ->
                if (item.type == concept.wand?.type) {
                    val player = esper.player
                    val testResult = test()
                    if (testResult != TestResult.Success) {
                        testResult.message(this)?.let { player.sendActionBar(it) }
                        return
                    }
                    if (!psychic.consumeMana(concept.powerGenerationCost)) return player.sendActionBar(TestResult.FailedCost.message(this))

                    cooldownTime = concept.cooldownTime
                    manaUseFrequency += concept.manaUseFrequencyIncreasePerMana * concept.powerGenerationCost.toInt()

                    val world = player.world
                    val location = player.eyeLocation

                    entitiesAffectedByLightning.clear()

                    world.playSound(location, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.5F, 3.0F)

                    for (yAngle in -90..90 step 20) {
                        for (i in sinPerTwentyAngle.indices) {
                            val projectile = LightningProjectile().apply {
                                lightning =
                                    this@AbilityElectrician.psychic.spawnFakeEntity(location, ArmorStand::class.java)
                                        .apply {
                                            updateMetadata<ArmorStand> {
                                                isVisible = false
                                                isMarker = true
                                            }
                                            updateEquipment {
                                                helmet = ItemStack(Material.YELLOW_GLAZED_TERRACOTTA)
                                            }
                                        }
                                velocity = Vector(sinPerTwentyAngle[i] * (1 - (yAngle.toDouble().absoluteValue / 90.0)),
                                    yAngle.toDouble() / 90.0,
                                    cosPerTwentyAngle[i] * (1 - (yAngle.toDouble().absoluteValue / 90.0))).
                                    multiply(concept.lightningSpeed)
                            }
                            psychic.launchProjectile(location, projectile)
                        }
                    }
                }
            }
        }
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            event.item?.let { item ->
                if (item.type == concept.wand?.type) {
                    val player = esper.player
                    val testResult = test()
                    if (testResult != TestResult.Success) {
                        testResult.message(this)?.let { player.sendActionBar(it) }
                        return
                    }
                    if (isTransmissing) return player.sendActionBar(TestResult.FailedChannel.message(this))
                    if (!psychic.consumeMana(concept.powerTransmissionCost)) return player.sendActionBar(TestResult.FailedCost.message(this))

                    cooldownTime = concept.cooldownTime
                    manaUseFrequency += concept.manaUseFrequencyIncreasePerMana * concept.powerTransmissionCost.toInt()
                    isTransmissing = true

                    val location = player.eyeLocation
                    val projectile = ElectronProjectile().apply {
                        electron =
                            this@AbilityElectrician.psychic.spawnFakeEntity(location, ArmorStand::class.java)
                                .apply {
                                    updateMetadata<ArmorStand> {
                                        invisible = true
                                        isMarker = true
                                    }
                                }
                        velocity = location.direction.apply {
                            y = (0.5 * y + 0.5).coerceAtMost(1.0)
                        }.multiply(concept.electronSpeed)
                    }
                    psychic.launchProjectile(location, projectile)
                }
            }
        }
    }
    inner class LightningProjectile : PsychicProjectile(1200, concept.powerGenerationRange) {
        lateinit var lightning: FakeEntity

        override fun onMove(movement: Movement) {
            lightning.moveTo(movement.to.clone().apply { y -= 1.62 })
        }

        override fun onTrail(trail: Trail) {
            trail.velocity?.let { velocity ->
                val from = trail.from
                val length = velocity.normalizeAndLength()
                val world = from.world
                world.rayTrace(
                    from,
                    velocity,
                    length,
                    FluidCollisionMode.NEVER,
                    true,
                    1.0,
                    TargetFilter(esper.player)
                )?.let { rayTraceResult ->
                    val hitLocation = rayTraceResult.hitPosition.toLocation(world)

                    rayTraceResult.hitEntity?.let { entity ->
                        if (entity is LivingEntity) {
                            if (entity.uniqueId !in entitiesAffectedByLightning) {
                                entity.psychicDamage(damage = concept.powerGenerationDamage, knockback = concept.powerGenerationKnockback)
                                entitiesAffectedByLightning.add(entity.uniqueId)
                                world.spawnParticle(Particle.DUST_COLOR_TRANSITION, hitLocation, 24, 0.5, 0.5, 0.5, Particle.DustTransition(Color.YELLOW, Color.YELLOW, 1.0f))
                                world.playSound(hitLocation, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 2.0F, 2.0F)
                            }
                        }
                    }

                    if (rayTraceResult.hitBlock != null) remove()
                }
            }
        }

        override fun onRemove() {
            lightning.remove()
        }
    }

    inner class ElectronProjectile : PsychicProjectile(200, 1000.0) {
        lateinit var electron: FakeEntity

        override fun onPreUpdate() {
            velocity = velocity.apply { y -= concept.electronGravity }
        }

        override fun onMove(movement: Movement) {
            electron.moveTo(movement.to.clone())

        }

        override fun onTrail(trail: Trail) {
            trail.velocity?.let { velocity ->
                val from = trail.from
                val length = velocity.normalizeAndLength()
                val world = from.world

                world.rayTrace(
                    from,
                    velocity,
                    length,
                    FluidCollisionMode.NEVER,
                    true,
                    0.5,
                ) { target -> target != esper.player} ?.let { rayTraceResult ->
                    remove()

                    isTransmissing = false

                    val hitLocation = rayTraceResult.hitPosition.toLocation(world)
                    esper.player.teleport(hitLocation.apply {
                        yaw = from.yaw
                        pitch = from.pitch
                    })

                    world.spawnParticle(Particle.SCRAPE, hitLocation, 32, 1.0, 0.2, 1.0, 1.0)
                    world.playSound(hitLocation, Sound.BLOCK_SOUL_SAND_PLACE, 2.0F, 0.1F)

                } ?: run {
                    val to = from.clone().add(velocity.clone().multiply(length))
                    esper.player.teleport(to)
                }
            }
        }

        override fun onRemove() {
            electron.remove()
            isTransmissing = false
        }
    }

}





