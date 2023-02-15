package io.github.anblus.psychics.ability.tolerance

import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.AbilityType
import io.github.monun.psychics.ActiveAbility
import io.github.monun.psychics.attribute.EsperStatistic
import io.github.monun.psychics.tooltip.TooltipBuilder
import io.github.monun.psychics.tooltip.stats
import io.github.monun.psychics.util.friendlyFilter
import io.github.monun.tap.config.Config
import io.github.monun.tap.config.Name
import io.github.monun.tap.event.EntityProvider
import io.github.monun.tap.event.TargetEntity
import io.github.monun.tap.math.toRadians
import io.github.monun.tap.trail.TrailSupport
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerEvent
import org.bukkit.inventory.ItemStack
import kotlin.math.cos
import kotlin.math.sin

// 아픔 나누기
@Name("tolerance")
class AbilityConceptTolerance : AbilityConcept() {

    @Config
    val damageMultipleFromTeam = 0.5

    @Config
    val damageMultiple = 0.5

    @Config
    val attackMultiple = 0.5

    init {
        displayName = "포용"
        type = AbilityType.ACTIVE
        cost = 20.0
        cooldownTime = 5000L
        durationTime = 5000L
        range = 5.0
        description = listOf(
            text("사용 시 주위 아군이 피해를 입었을 경우"),
            text("피해의 일부분을 자신이 대신 받습니다."),
            text("사용 중엔 받는 피해와 주는 피해가 감소합니다.")
        )
        wand = ItemStack(Material.IRON_INGOT)
    }

    override fun onRenderTooltip(tooltip: TooltipBuilder, stats: (EsperStatistic) -> Double) {
        tooltip.stats(damageMultipleFromTeam) { NamedTextColor.GREEN to "아군 피해" to "배" }
        tooltip.stats(damageMultiple) { NamedTextColor.GOLD to "받는 피해" to "배" }
        tooltip.stats(attackMultiple) { NamedTextColor.RED to "주는 피해" to "배" }
    }
}

class AbilityTolerance : ActiveAbility<AbilityConceptTolerance>(), Listener {
    private var eventEntity = mutableListOf<Entity>()

    private var effectCycle = 0

    override fun onEnable() {
        psychic.runTaskTimer(this::onDuration, 0L, 1L)
    }

    private fun onDuration() {
        if (durationTime > 0) {
            val player = esper.player
            val location = player.location
            val world = location.world

            if (effectCycle == 0) {
                effectCycle = 20

                repeat(36) { i ->
                    val radian = (10.0 * i).toRadians()
                    val cos = cos(radian)
                    val sin = sin(radian)
                    val offsetX = concept.range * cos
                    val offsetZ = concept.range * sin

                    world.spawnParticle(Particle.DAMAGE_INDICATOR, location.x + offsetX, location.y, location.z + offsetZ, 1, 0.0, 0.0, 0.0, 0.4)
                }
            } else effectCycle --


            eventEntity.forEach { team ->
                psychic.plugin.entityEventManager.unregisterEvent(team, this)
            }
            eventEntity.clear()

            location.getNearbyEntities(concept.range, concept.range, concept.range).filter { player.friendlyFilter().test(it) }.plus(player).forEach { team ->
                psychic.plugin.entityEventManager.registerEvents(team, this)
                eventEntity.add(team)
            }
        } else {
            eventEntity.forEach { team ->
                psychic.plugin.entityEventManager.unregisterEvent(team, this)
            }
            eventEntity.clear()
            effectCycle = 0
        }
    }

    override fun onDisable() {
        eventEntity.forEach { team ->
            psychic.plugin.entityEventManager.unregisterEvent(team, this)
        }
    }

    override fun onCast(event: PlayerEvent, action: WandAction, target: Any?) {
        val player = event.player
        val location = player.location
        val world = location.world

        cooldownTime = concept.cooldownTime
        psychic.consumeMana(concept.cost)
        durationTime = concept.durationTime

        world.playSound(location, Sound.BLOCK_BELL_USE, 2.0f, 0.5f)
    }

    @EventHandler(ignoreCancelled = true)
    fun onDeath(event: PlayerDeathEvent) {
        val entity = event.entity
        val player = esper.player

        if (entity == player) {
            durationTime = 0L
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onDamage(event: EntityDamageByEntityEvent) {
        val damage = event.finalDamage
        if (damage <= 0) return

        val entity = event.entity
        val player = esper.player

        if (entity == player) {
            if (durationTime > 0) event.damage *= concept.damageMultiple
        } else {
            if (player.isValid) {
                event.damage *= concept.damageMultipleFromTeam
                player.noDamageTicks = 0
                if (event.damager is Player) {
                    player.killer = event.damager as Player
                }
                player.damage(damage * (1.0 - concept.damageMultipleFromTeam) * concept.damageMultiple)

                val world = entity.world
                val from = entity.boundingBox.center.toLocation(world)
                val to = player.boundingBox.center.toLocation(world)

                TrailSupport.trail(from, to, 0.1) { w, x, y, z ->
                    w.spawnParticle(Particle.REVERSE_PORTAL, x, y, z, 1, 0.0, 0.0, 0.0, 0.1)
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    @TargetEntity(EntityProvider.EntityDamageByEntity.Damager::class)
    fun onAttack(event: EntityDamageByEntityEvent) {
        val entity = event.entity
        val player = esper.player

        if (entity == player) {
            if (durationTime > 0) event.damage *= concept.attackMultiple
        }
    }
}






