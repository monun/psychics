package io.github.anblus.psychics.ability.mediation

import io.github.monun.psychics.Ability
import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.AbilityType
import io.github.monun.psychics.TestResult
import io.github.monun.psychics.attribute.EsperAttribute
import io.github.monun.psychics.attribute.EsperStatistic
import io.github.monun.psychics.tooltip.TooltipBuilder
import io.github.monun.psychics.tooltip.stats
import io.github.monun.psychics.tooltip.template
import io.github.monun.tap.config.Config
import io.github.monun.tap.config.Name
import io.github.monun.tap.trail.TrailSupport
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.*
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack

// 킹메이커
@Name("mediation")
class AbilityConceptMediation : AbilityConcept() {

    @Config
    val totalRange = EsperStatistic.of(EsperAttribute.ATTACK_DAMAGE to 12.0)

    init {
        displayName = "중개"
        type = AbilityType.ACTIVE
        cooldownTime = 200L
        cost = 10.0
        description = listOf(
            text("좌클릭 시 대상에게 마나를 마나 소모량만큼 부여하고, "),
            text("우클릭 시 대상으로부터 마나 소모량만큼의 마나를 뺏어옵니다."),
            text("능력의 인식 사거리는 초월에 비례합니다."),
            text(""),
            text("*마나 재생을 0으로 하는 것을 추천드립니다.").color(NamedTextColor.GRAY)
        )
        wand = ItemStack(Material.PAPER)
    }

    override fun onRenderTooltip(tooltip: TooltipBuilder, stats: (EsperStatistic) -> Double) {
        tooltip.stats(totalRange) { NamedTextColor.LIGHT_PURPLE to "사거리" to "total-range" }
        tooltip.template("total-range", stats(totalRange))
    }
}

class AbilityMediation : Ability<AbilityConceptMediation>(), Listener {

    override fun onEnable() {
        psychic.registerEvents(this)
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val action = event.action

        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            event.item?.let { item ->
                if (item.type == concept.wand?.type) {
                    val player = esper.player
                    val start = player.eyeLocation
                    val world = start.world
                    val range = esper.getStatistic(concept.totalRange)


                    world.rayTrace(
                        start,
                        start.direction,
                        range,
                        FluidCollisionMode.NEVER,
                        true,
                        0.8
                    ) { entity -> entity != player } ?.let { result ->
                        val target = result.hitEntity
                        if (target !is Player) return
                        val targetEsper = esper.manager.getEsper(target)
                        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
                            if ((targetEsper?.psychic?.mana ?: return) >= (targetEsper.psychic?.concept?.mana ?: return)) {
                                player.sendActionBar("대상의 마나가 이미 가득 차있습니다.")
                                return
                            }

                            val testResult = test()

                            if (testResult != TestResult.Success) {
                                testResult.message(this)?.let { player.sendActionBar(it) }
                                return
                            }

                            targetEsper.psychic?.let {
                                it.mana += concept.cost
                            }
                            psychic.mana -= concept.cost

                            val to = target.boundingBox.center.toLocation(world)
                            TrailSupport.trail(start, to, 0.4) { w, x, y, z ->
                                w.spawnParticle(Particle.WATER_WAKE, x, y, z, 1, 0.0, 0.0, 0.0, 0.0)
                            }
                            world.playSound(
                                target.location,
                                Sound.BLOCK_LARGE_AMETHYST_BUD_PLACE,
                                SoundCategory.BLOCKS,
                                2.0F,
                                0.1F
                            )
                        } else {

                            if (cooldownTime > 0) {
                                player.sendActionBar("아직 준비되지 않았습니다")
                                return
                            }
                            if (psychic.mana >= psychic.concept.mana) {
                                player.sendActionBar("마나가 이미 가득 차있습니다.")
                                return
                            }
                            if ((targetEsper?.psychic?.mana ?: return) < concept.cost) {
                                player.sendActionBar("대상의 마나가 ${concept.cost.toInt()} 미만입니다.")
                                return
                            }

                            targetEsper.psychic?.let {
                                it.mana -= concept.cost
                            }
                            psychic.mana += concept.cost

                            val to = target.boundingBox.center.toLocation(world)
                            TrailSupport.trail(start, to, 0.4) { w, x, y, z ->
                                w.spawnParticle(Particle.DUST_COLOR_TRANSITION, x, y, z, 1, 0.0, 0.0, 0.0, 0.0, Particle.DustTransition(Color.RED, Color.RED, 1.0f))
                            }
                            world.playSound(
                                target.location,
                                Sound.ITEM_TRIDENT_RETURN,
                                SoundCategory.PLAYERS,
                                2.0F,
                                0.1F
                            )
                        }
                        cooldownTime = concept.cooldownTime
                    }
                }

            }
        }
    }
}






