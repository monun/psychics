package io.github.monun.psychics.ability.vampire

import io.github.monun.psychics.Ability
import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.attribute.EsperStatistic
import io.github.monun.psychics.tooltip.TooltipBuilder
import io.github.monun.tap.config.Config
import io.github.monun.tap.config.Name
import io.github.monun.tap.event.EntityProvider
import io.github.monun.tap.event.TargetEntity
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.ArmorStand
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.player.PlayerAttemptPickupItemEvent
import org.bukkit.inventory.ItemStack

@Name("vampire")
class AbilityConceptVampire : AbilityConcept() {
    @Config
    var healByDamage = 0.1

    @Config
    var orbTicksLived = 6000 - (20 * 30)

    @Config
    var orbPickupDelay = 10

    @Config
    var orbVelocity = 0.35

    init {
        displayName = "흡혈"

        description = listOf(
            text("적에게 피해를 입히면 주위에 핏방울이 생성됩니다."),
            text("핏방울 근처로 이동하면 사라지며 체력을 회복합니다.")
        )
    }

    override fun onRenderTooltip(tooltip: TooltipBuilder, stats: (EsperStatistic) -> Double) {
        tooltip.header(
            text().color(NamedTextColor.GREEN).content("치유량 ").decoration(TextDecoration.ITALIC, false).decorate(TextDecoration.BOLD)
                .append(
                    text().color(NamedTextColor.RED).content("피해의 ").append(text((healByDamage * 100).toInt()))
                        .append(text().content("%"))
                ).build()
        )
    }
}

class AbilityVampire : Ability<AbilityConceptVampire>(), Listener {
    companion object {
        val orbName = text().content("핏방울").color(NamedTextColor.RED).build()
    }

    override fun onEnable() {
        psychic.registerEvents(this)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    @TargetEntity(EntityProvider.EntityDamageByEntity.Damager::class)
    fun onAttack(event: EntityDamageByEntityEvent) {
        val entity = event.entity

        if (entity is ArmorStand) return

        val concept = concept
        val damage = event.finalDamage
        val healing = damage * concept.healByDamage
        val location = entity.location.apply { y = entity.boundingBox.maxY }
        val player = esper.player
        val playerLocation = player.location
        val velocity = playerLocation.direction.apply { y += 1.0 }.normalize().multiply(concept.orbVelocity)

        location.world.dropItem(location, ItemStack(Material.REDSTONE).apply {
            editMeta {
                it.displayName(text(healing))
            }
        }).apply {
            this.owner = player.uniqueId
            this.ticksLived = concept.orbTicksLived
            this.pickupDelay = concept.orbPickupDelay
            this.velocity = velocity
            this.isCustomNameVisible = true
            setCanMobPickup(false)
            customName(orbName)
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onPickupItem(event: PlayerAttemptPickupItemEvent) {
        val item = event.item
        if (item.customName() == orbName) {
            val itemStack = item.itemStack

            if (itemStack.type == Material.REDSTONE) {
                itemStack.itemMeta.displayName()?.let {
                    if (it is TextComponent) {
                        val content = it.content()
                        content.toDoubleOrNull()?.let { amount ->
                            event.isCancelled = true
                            item.remove()
                            esper.player.psychicHeal(amount)

                            val box = item.boundingBox
                            val location = item.location.apply { y = box.centerY }
                            val world = location.world

                            world.spawnParticle(Particle.ITEM_CRACK, location, 32, 0.0, 0.0, 0.0, 0.1, itemStack)
                            world.playSound(location, Sound.ENTITY_ITEM_PICKUP, 1.0F, 0.1F)
                        }
                    }
                }
            }
        }
    }
}