package io.github.anblus.psychics.ability.electricshockwave

import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.AbilityType
import io.github.monun.psychics.ActiveAbility
import io.github.monun.psychics.Channel
import io.github.monun.psychics.attribute.EsperAttribute
import io.github.monun.psychics.attribute.EsperStatistic
import io.github.monun.psychics.damage.Damage
import io.github.monun.psychics.damage.DamageType
import io.github.monun.psychics.tooltip.TooltipBuilder
import io.github.monun.psychics.tooltip.stats
import io.github.monun.psychics.util.TargetFilter
import io.github.monun.psychics.util.hostileFilter
import io.github.monun.tap.config.Config
import io.github.monun.tap.config.Name
import io.github.monun.tap.task.TickerTask
import io.github.monun.tap.trail.TrailSupport
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.*
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import kotlin.math.pow
import kotlin.random.Random.Default.nextInt

// 연쇄 반응 무리 말살
@Name("electric-shock-wave")
class AbilityConceptElectricShockWave : AbilityConcept() {

    @Config
    val maxReaction = 10

    @Config
    val reactionIntervalTick = 20

    @Config
    val waveThickness = 0.4

    @Config
    val secondRange = 8.0

    @Config
    val blindnessDuration = 40

    @Config
    val targetingThreeWayRandomHealthDistance = "distance"

    init {
        displayName = "전격파"
        type = AbilityType.ACTIVE
        castingTime = 1000L
        cost = 50.0
        range = 16.0
        damage = Damage.of(DamageType.RANGED, EsperAttribute.ATTACK_DAMAGE to 5.0)
        description = listOf(
            text("사용 시 바라보는 방향에 전격파를 발사합니다."),
            text("전격파에 맞은 적은 피해를 입음과 동시에 실명에 걸리고"),
            text("잠시 뒤 근처 시전자 기준 적에게 똑같이 연쇄 반응을 일으킵니다.")
        )
        wand = ItemStack(Material.BLAZE_ROD)

    }

    override fun onRenderTooltip(tooltip: TooltipBuilder, stats: (EsperStatistic) -> Double) {
        tooltip.stats(maxReaction) { NamedTextColor.RED to "최대 반응 횟수" to "번" }
        tooltip.stats(reactionIntervalTick / 20.0) { NamedTextColor.YELLOW to "반응 간격" to "초" }
        tooltip.stats(secondRange.toInt()) { NamedTextColor.GREEN to "반응 사거리" to "블록" }
    }
}

class AbilityElectricShockWave : ActiveAbility<AbilityConceptElectricShockWave>(), Listener {
    private var timer: TickerTask? = null

    override fun onChannel(channel: Channel) {
        val player = esper.player
        val world = player.world
        val location = player.eyeLocation

        world.playSound(
            location,
            Sound.ENTITY_BEE_POLLINATE,
            SoundCategory.HOSTILE,
            2.0F,
            2.0F
        )
        world.spawnParticle(Particle.WAX_ON, location.apply { y += 1.0 }, 2, 0.4, 0.0, 0.4, 0.4)
        player.addPotionEffect(
            PotionEffect(PotionEffectType.SLOW, 2, 4)
        )
    }

    override fun onCast(event: PlayerEvent, action: WandAction, target: Any?) {
        timer?.run { return }
        psychic.consumeMana(concept.cost)
        cooldownTime = ((concept.maxReaction + 1) * concept.reactionIntervalTick * 50).toLong()

        val player = esper.player
        var range = concept.range
        var start = player.eyeLocation
        val direction = start.direction
        var world = start.world
        var isFirst = true
        var waveTarget: LivingEntity? = null
        var to: Location? = null
        var reactionCount = 0
        var thickness = concept.waveThickness

        world.playSound(
            player.location,
            Sound.ENTITY_EVOKER_PREPARE_SUMMON,
            SoundCategory.HOSTILE,
            2.0F,
            2.0F
        )

        timer = psychic.runTaskTimer({
            if (!isFirst) {
                range = concept.secondRange
                thickness = concept.waveThickness * 0.2

                world = waveTarget!!.world
                start = waveTarget!!.boundingBox.center.toLocation(world)
                val targetList = ArrayList<LivingEntity>()
                start.getNearbyEntities(
                    range, range, range
                ).minus(waveTarget).filter { entity -> player.hostileFilter().test(entity as Entity) }
                    .forEach { entity ->
                        if (entity is LivingEntity) {
                            val targetLoc = entity.boundingBox.center.toLocation(world)
                            val distance = start.distance(targetLoc)
                            if (distance <= 0) {
                                targetList.add(entity)
                            } else {
                                val targetDir = targetLoc.subtract(start).multiply(1.0 / distance).toVector()
                                world.rayTrace(
                                    start,
                                    targetDir,
                                    range,
                                    FluidCollisionMode.NEVER,
                                    true,
                                    thickness * 2.0
                                ) { testEntity -> testEntity == entity}?.hitEntity?.let {
                                    targetList.add(entity)
                                }
                            }
                        }
                    }
                if (targetList.isEmpty()) {
                    waveTarget = null
                } else {
                    waveTarget = when (concept.targetingThreeWayRandomHealthDistance) {
                        "Distance", "distance" -> {
                            targetList.sortedBy { entity -> start.distance(entity.location) }[0]
                        }
                        "Health", "health" -> {
                            targetList.sortedBy { entity -> entity.health }[0]
                        }
                        else -> {
                            targetList.shuffled()[0]
                        }
                    }
                    to = waveTarget!!.boundingBox.center.toLocation(world)
                }
            } else {
                isFirst = false
                val hitResult = world.rayTrace(
                    start,
                    direction,
                    range,
                    FluidCollisionMode.NEVER,
                    true,
                    thickness,
                    TargetFilter(player)
                )

                to = start.clone().add(direction.clone().multiply(range))

                if (hitResult != null) {
                    to = hitResult.hitPosition.toLocation(world)

                    hitResult.hitEntity?.let { entity ->
                        if (entity is LivingEntity) {
                            waveTarget = entity
                        }
                    }
                }
            }

            to?.let { to ->
                if (isFirst) start.add(to.clone().toVector().normalize().multiply(thickness * 4))
                TrailSupport.trail(start, to, thickness) { w, x, y, z ->
                    if (nextInt(0, 2) == 0) w.spawnParticle(Particle.DUST_COLOR_TRANSITION, x, y, z, (thickness * 10).pow(2).toInt(), thickness, thickness, thickness, 0.01, Particle.DustTransition(Color.fromRGB(255, 178, 0), Color.fromRGB(255, 203, 66), 2.0f))
                    else w.spawnParticle(Particle.DUST_COLOR_TRANSITION, x, y, z, (thickness * 10).pow(2).toInt(), thickness, thickness, thickness, 0.01, Particle.DustTransition(Color.fromRGB(255, 203, 66), Color.fromRGB(255, 178, 0), 2.0f))
                }
            }
            to = null

            waveTarget?.let { waveTarget ->
                waveTarget.psychicDamage()
                waveTarget.addPotionEffects(
                    listOf(PotionEffect(PotionEffectType.BLINDNESS, concept.blindnessDuration, 0),
                        PotionEffect(PotionEffectType.GLOWING, concept.reactionIntervalTick, 0))
                )
                world.playSound(
                    waveTarget.location,
                    Sound.ENTITY_FIREWORK_ROCKET_TWINKLE,
                    SoundCategory.NEUTRAL,
                    2.0F,
                    2.0F
                )
            }?: run {
                cancel()
            }

            if (reactionCount >= concept.maxReaction) cancel()
            reactionCount += 1
        }, 0L, concept.reactionIntervalTick.toLong())
    }

    private fun cancel() {
        timer!!.cancel()
        timer = null
        cooldownTime = 0L
    }
}






