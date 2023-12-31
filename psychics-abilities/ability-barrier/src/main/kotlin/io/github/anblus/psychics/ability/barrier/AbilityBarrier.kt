package io.github.anblus.psychics.ability.barrier

import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.AbilityType
import io.github.monun.psychics.ActiveAbility
import io.github.monun.psychics.attribute.EsperAttribute
import io.github.monun.psychics.attribute.EsperStatistic
import io.github.monun.psychics.damage.Damage
import io.github.monun.psychics.damage.DamageType
import io.github.monun.psychics.tooltip.TooltipBuilder
import io.github.monun.psychics.tooltip.stats
import io.github.monun.psychics.util.hostileFilter
import io.github.monun.tap.config.Config
import io.github.monun.tap.config.Name
import io.github.monun.tap.trail.TrailSupport
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.*
import org.bukkit.entity.LivingEntity
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Vector
import java.util.*

// 진로 차단
@Name("barrier")
class AbilityConceptBarrier : AbilityConcept() {

    @Config
    val manaPerMeter = 10

    @Config
    val interval = 4.0

    init {
        displayName = "방벽"
        type = AbilityType.ACTIVE
        cooldownTime = 500L
        durationTime = 5000L
        range = 24.0
        damage = Damage.of(DamageType.MELEE, EsperAttribute.ATTACK_DAMAGE to 5.0)
        description = listOf(
            text("능력을 처음 사용 하였을 때 바라본 위치를 기준으로"),
            text("능력을 다시 사용 하였을 때 바라본 위치까지 연결 되는"),
            text("방벽을 지을 수 있습니다."),
            text("방벽은 시전자와 아군을 제외한 모든 생물의 이동을"),
            text("차단함과 동시에 만일 적일시 추가로 데미지를 입힙니다."),
            text("${ChatColor.GRAY}*지나치게 빠른 엔티티의 이동은 막지 못합니다.")
        )
        wand = ItemStack(Material.PHANTOM_MEMBRANE)

    }
    override fun onRenderTooltip(tooltip: TooltipBuilder, stats: (EsperStatistic) -> Double) {
        tooltip.stats(manaPerMeter) { NamedTextColor.AQUA to "미터 당 마나 소모" to null }
    }
}

class AbilityBarrier : ActiveAbility<AbilityConceptBarrier>(), Listener {
    var buildingTime = 0

    var firstLoc: Location? = null

    var firstList = arrayListOf<Location>()

    var secondList = arrayListOf<Location>()

    var timeList = arrayListOf<Int>()

    var playerList = arrayListOf<UUID>()

    var playerTimeList = arrayListOf<Int>()

    override fun onEnable() {
        psychic.registerEvents(this)
        psychic.runTaskTimer({
            if (buildingTime > 0) buildingTime -= 1
            var delCount = 0
            repeat(timeList.size) {
                val i = it - delCount
                timeList[i] -= 1
                if (timeList[i] <= 0) {
                    delCount += 1
                    firstList.removeAt(i)
                    secondList.removeAt(i)
                    timeList.removeAt(i)
                }
            }
            delCount = 0
            repeat(playerTimeList.size) {
                val i = it - delCount
                playerTimeList[i] -= 1
                if (playerTimeList[i] <= 0) {
                    delCount += 1
                    playerList.removeAt(i)
                    playerTimeList.removeAt(i)
                }
            }
        }, 0L, 1L)
        psychic.runTaskTimer({
            if (buildingTime > 0) {
                val player = esper.player
                val world = player.world
                val loc = player.eyeLocation
                world.rayTrace(
                    loc,
                    loc.direction,
                    concept.range,
                    FluidCollisionMode.NEVER,
                    true,
                    0.0
                ) { entity ->
                    entity == null
                }?.hitPosition?.toLocation(world)?.let {
                    firstLoc?.let { firstLoc ->
                        val secondLoc = Vector(it.x, firstLoc.y, it.z).toLocation(world)
                        val distance = firstLoc.distance(secondLoc)
                        player.sendActionBar("필요 마나양 ${(distance * concept.manaPerMeter).toInt()}")
                        val interval = concept.interval / 10
                        TrailSupport.trail(firstLoc, firstLoc.clone().add(0.0, distance, 0.0), interval) { w, x, y, z ->
                            w.spawnParticle(Particle.COMPOSTER, x, y, z, 1, 0.0, 0.0, 0.0, 0.0)
                        }
                        TrailSupport.trail(firstLoc, secondLoc, interval) { w, x, y, z ->
                            w.spawnParticle(Particle.COMPOSTER, x, y, z, 1, 0.0, 0.0, 0.0, 0.0)
                        }
                        TrailSupport.trail(firstLoc.clone().add(0.0, distance, 0.0), secondLoc.clone().add(0.0, distance, 0.0), interval) { w, x, y, z ->
                            w.spawnParticle(Particle.COMPOSTER, x, y, z, 1, 0.0, 0.0, 0.0, 0.0)
                        }
                        TrailSupport.trail(secondLoc, secondLoc.clone().add(0.0, distance, 0.0), interval) { w, x, y, z ->
                            w.spawnParticle(Particle.COMPOSTER, x, y, z, 1, 0.0, 0.0, 0.0, 0.0)
                        }
                    }
                }
            }
        }, 0L, 8L)
        psychic.runTaskTimer({
            if (timeList.size >= 1) {
                repeat(timeList.size) { i ->
                    if (timeList[i] > 25) {
                        val firstLoc = firstList[i]
                        val secondLoc = secondList[i]
                        val distance = (firstLoc.distance(secondLoc) * 10).toInt()
                        val interval = concept.interval
                        for (y in 0..distance step interval.toInt()) {
                            TrailSupport.trail(
                                firstLoc.clone().add(0.0, y.toDouble() / 10, 0.0),
                                secondLoc.clone().add(0.0, y.toDouble() / 10, 0.0),
                                interval / 10
                            ) { w, x, y, z ->
                                w.spawnParticle(Particle.SCRAPE, x, y, z, 1, 0.0, 0.0, 0.0, 0.0)
                            }
                        }
                    }
                }
            }
        }, 0L, 5L)
        psychic.runTaskTimer({
            if (timeList.size >= 1) {
                repeat(timeList.size) { i ->
                    val firstLoc = firstList[i]
                    val secondLoc = secondList[i]
                    val distance = (firstLoc.distance(secondLoc) * 10).toInt()
                    val interval = concept.interval
                    for (y in 0..distance step interval.toInt()) {
                        val player = esper.player
                        val world = player.world
                        val direction = secondLoc.clone().subtract(firstLoc).multiply(1.0 / (distance / 10)).toVector()
                        world.rayTrace(
                            firstLoc.clone().add(0.0, y.toDouble() / 10, 0.0),
                            direction,
                            distance.toDouble() / 10,
                            FluidCollisionMode.NEVER,
                            true,
                            0.32
                        ) { target -> target is LivingEntity && player.hostileFilter().test(target)}?.let { result ->
                            val entity = result.hitEntity
                            if (entity != null) {
                                if (entity.uniqueId !in playerList) {
                                    val pos = result.hitPosition.toLocation(world)
                                    val hitDistance = entity.location.distance(pos)
                                    val hitDirection = entity.location.subtract(pos).multiply(1.0 / hitDistance).subtract(direction.multiply(0.5)).toVector().setY(0.2)
                                    entity.velocity = hitDirection
                                    playerList.add(entity.uniqueId)
                                    playerTimeList.add(6)
                                    if (entity is LivingEntity) entity.psychicDamage()
                                }
                            }
                        }
                    }
                }
            }
        }, 0L, 1L)

    }

    override fun onCast(event: PlayerEvent, action: WandAction, target: Any?) {
        val player = event.player
        if (buildingTime == 0) {
            val loc = player.eyeLocation
            val world = player.world
            world.rayTrace(
                loc,
                loc.direction,
                concept.range,
                FluidCollisionMode.NEVER,
                true,
                0.0
            ) { entity ->
                entity == null
            }?.hitPosition.let { result ->
                if (result == null) {
                    player.sendActionBar("거리가 너무 멉니다.")
                } else {
                    firstLoc = result.toLocation(world)
                    buildingTime = 90
                }
            }
        } else {
            val world = player.world
            val loc = player.eyeLocation
            world.rayTrace(
                loc,
                loc.direction,
                concept.range,
                FluidCollisionMode.NEVER,
                true,
                0.0
            ) { entity ->
                entity == null
            }?.hitPosition?.toLocation(world)?.let {
                firstLoc?.let { firstLoc ->
                    val secondLoc = Vector(it.x, firstLoc.y, it.z).toLocation(world)
                    val distance = firstLoc.distance(secondLoc)
                    val cost = (distance * concept.manaPerMeter).toInt()
                    if (cost > psychic.mana) {
                        player.sendActionBar("필요한 마나양이 초과됩니다.")
                    } else {
                        buildingTime = 0
                        cooldownTime = concept.cooldownTime
                        psychic.mana -= cost
                        firstList.add(firstLoc)
                        secondList.add(secondLoc)
                        timeList.add((concept.durationTime / 50L).toInt())
                    }
                }
            }
        }
    }
}






