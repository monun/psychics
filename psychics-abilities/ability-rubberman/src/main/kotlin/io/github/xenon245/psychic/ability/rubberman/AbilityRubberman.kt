package io.github.xenon245.psychic.ability.rubberman

import io.github.monun.psychics.Ability
import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.attribute.EsperAttribute
import io.github.monun.psychics.attribute.EsperStatistic
import io.github.monun.psychics.damage.Damage
import io.github.monun.psychics.damage.DamageType
import io.github.monun.psychics.tooltip.TooltipBuilder
import io.github.monun.psychics.util.TargetFilter
import io.github.monun.tap.config.Config
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.FluidCollisionMode
import org.bukkit.Material
import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack

class AbilityConceptRubberman : AbilityConcept() {

    @Config
    var rubberMaxDistance = 40.0

    init {
        wand = ItemStack(Material.STICK)
        description = listOf(
            text("막대기를 허공에 휘두를 시 멀리 있는 상대를 공격 할 수 있습니다.")
        )
        damage = Damage.of(DamageType.MELEE, EsperAttribute.ATTACK_DAMAGE to 1.0)
        knockback = 0.5
    }

    override fun onRenderTooltip(tooltip: TooltipBuilder, stats: (EsperStatistic) -> Double) {
        tooltip.header(
            text().color(NamedTextColor.AQUA).content("최대 사거리 ").decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false)
                .append(text().color(NamedTextColor.GREEN).content(rubberMaxDistance.toInt().toString())).build()
        )
    }
}

class AbilityRubberman : Ability<AbilityConceptRubberman>(), Listener, Runnable {
    override fun onEnable() {
        psychic.registerEvents(this)
        psychic.runTaskTimer(this, 0L, 1L)
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (event.item!!.isSimilar(concept.wand) && event.action.isLeftClick) {
            esper.player.world.rayTrace(
                esper.player.eyeLocation,
                esper.player.eyeLocation.direction,
                concept.rubberMaxDistance,
                FluidCollisionMode.NEVER,
                true,
                0.3,
                TargetFilter(esper.player)
            )?.let { result ->
                val entity = result.hitEntity
                if (entity != null && entity is LivingEntity) {
                    entity.psychicDamage()
                }
            }
        }
    }

    override fun run() {
        if (esper.player.inventory.contains(concept.wand)) {
            esper.player.inventory.run {
                val iterator = iterator()
                while (iterator.hasNext()) {
                    val next = iterator.next()

                    if (next.isSimilar(concept.wand)) {
                        next.itemMeta = next.itemMeta.apply {
                            val lore = lore() ?: ArrayList<Component>()
                            lore.add(0, text().content("허공에 휘두를 때: ").decoration(TextDecoration.ITALIC, false)
                                .color(NamedTextColor.GRAY).build())
                            lore.add(1, text().content("${concept.rubberMaxDistance.toInt()} 공격 거리").decoration(TextDecoration.ITALIC, false)
                                .color(NamedTextColor.DARK_GREEN).build())
                            lore.add(3, text().content("주로 사용하는 손에 있을 때: ").decoration(TextDecoration.ITALIC, false)
                                .color(NamedTextColor.GRAY).build())
                            lore.add(4, text().content("${concept.damage?.stats.toString()} 공격 피해").decoration(TextDecoration.ITALIC, false)
                                .color(NamedTextColor.DARK_GREEN).build())
                            lore(lore)
                        }
                    }
                }
            }
        }
    }
}