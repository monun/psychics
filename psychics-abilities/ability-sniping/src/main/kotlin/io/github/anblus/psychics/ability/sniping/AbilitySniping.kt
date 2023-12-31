package io.github.anblus.psychics.ability.sniping

import io.github.monun.psychics.Ability
import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.AbilityType
import io.github.monun.psychics.TestResult
import io.github.monun.psychics.attribute.EsperAttribute
import io.github.monun.psychics.attribute.EsperStatistic
import io.github.monun.psychics.damage.Damage
import io.github.monun.psychics.damage.DamageType
import io.github.monun.psychics.damage.psychicDamage
import io.github.monun.psychics.tooltip.TooltipBuilder
import io.github.monun.psychics.tooltip.stats
import io.github.monun.psychics.util.TargetFilter
import io.github.monun.tap.config.Config
import io.github.monun.tap.config.Name
import io.github.monun.tap.trail.TrailSupport
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.*
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.ItemStack
import kotlin.math.PI

// 마인크래프트 속 FPS 게임
@Name("sniping")
class AbilityConceptSniping : AbilityConcept() {

    @Config
    val headMultiple = 2.0

    @Config
    val bulletScale = 0.0

    init {
        displayName = "저격"
        type = AbilityType.ACTIVE
        cooldownTime = 5000L
        cost = 10.0
        range = 256.0
        damage = Damage.of(DamageType.RANGED, EsperAttribute.ATTACK_DAMAGE to 4.0)
        description = listOf(
            text("능력 아이템을 든 채 좌클릭을 누르거나 손을 바꿀시"),
            text("머리 판정을 가진 이펙트를 발사합니다.")
        )
        wand = ItemStack(Material.SPYGLASS)
    }

    override fun onRenderTooltip(tooltip: TooltipBuilder, stats: (EsperStatistic) -> Double) {
        tooltip.stats(headMultiple) { NamedTextColor.YELLOW to "헤드샷 배수" to "배" }
    }
}

class AbilitySniping : Ability<AbilityConceptSniping>(), Listener {

    override fun onEnable() {
        psychic.registerEvents(this)
    }

    private fun shoot() {
        cooldownTime = concept.cooldownTime
        psychic.consumeMana(concept.cost)

        val player = esper.player
        val range = concept.range
        val start = player.eyeLocation
        val direction = start.direction
        val world = start.world

        world.playSound(
            player.location,
            Sound.ENTITY_FIREWORK_ROCKET_BLAST,
            SoundCategory.PLAYERS,
            1.5F,
            0.1F
        )

        val hitResult = world.rayTrace(
            start,
            direction,
            range,
            FluidCollisionMode.NEVER,
            true,
            concept.bulletScale,
            TargetFilter(player)
        )

        var to = start.clone().add(direction.clone().multiply(range))

        if (hitResult != null) {
            to = hitResult.hitPosition.toLocation(world)

            hitResult.hitEntity?.let { target ->
                if (target is LivingEntity) {
                    val box = target.boundingBox
                    val eyeLoc = target.eyeLocation
                    val bodyLoc = box.center.toLocation(world)
                    val hitPos = hitResult.hitPosition.toLocation(world)
                    val eyeDistance = eyeLoc.distance(hitPos)
                    val bodyDistance = bodyLoc.distance(hitPos)

                    val damage = concept.damage
                    val damageType = damage?.type ?: DamageType.RANGED
                    val damageAmount = damage?.stats?.let { esper.getStatistic(it) } ?: 0.0

                    if (eyeDistance <= bodyDistance) {
                        world.spawnParticle(Particle.FALLING_LAVA, bodyLoc, 24, 0.5, 0.5, 0.5, 0.0)

                        world.playSound(
                            target.location,
                            Sound.ENTITY_PLAYER_ATTACK_CRIT,
                            SoundCategory.PLAYERS,
                            1.5F,
                            1.0F
                        )

                        target.psychicDamage(
                            this@AbilitySniping,
                            damageType,
                            damageAmount * concept.headMultiple,
                            player,
                            player.location,
                            concept.knockback
                        )
                    } else {
                        if (target is Player && target.isBlocking && direction.angle(target.eyeLocation.direction) > PI / 2.0) {
                            world.playSound(
                                target.location,
                                Sound.ITEM_SHIELD_BLOCK,
                                SoundCategory.BLOCKS,
                                1.0F,
                                1.0F
                            )
                        } else {
                            target.psychicDamage(
                                this@AbilitySniping,
                                damageType,
                                damageAmount,
                                player,
                                player.location,
                                concept.knockback
                            )
                        }
                    }
                }
            }
        }

        TrailSupport.trail(start, to, 0.2) { w, x, y, z ->
            w.spawnParticle(Particle.SMOKE_NORMAL, x, y, z, 1, 0.0, 0.0, 0.0, 0.0)
        }
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val action = event.action

        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {

            event.item?.let { item ->
                if (item.type == Material.SPYGLASS) {
                    val player = esper.player
                    val result = test()

                    if (result != TestResult.Success) {
                        result.message(this)?.let { player.sendActionBar(it) }
                        return
                    }

                    shoot()
                }
            }
        }
    }

    @EventHandler
    fun onPlayerSwapHandItems(event: PlayerSwapHandItemsEvent) {
        event.offHandItem?.let { item ->
            if (item.type == Material.SPYGLASS) {
                val player = esper.player
                val result = test()

                if (result != TestResult.Success) {
                    result.message(this)?.let { player.sendActionBar(it) }
                    return
                }

                shoot()
                event.isCancelled = true
            }
        }
    }
}






