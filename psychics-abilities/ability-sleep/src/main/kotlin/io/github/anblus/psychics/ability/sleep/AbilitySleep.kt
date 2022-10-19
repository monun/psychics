package io.github.anblus.psychics.ability.sleep

import io.github.monun.psychics.Ability
import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.AbilityType
import io.github.monun.tap.config.Name
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerBedEnterEvent
import org.bukkit.inventory.ItemStack

// 그만좀 자
@Name("sleep")
class AbilityConceptSleep : AbilityConcept() {

    init {
        displayName = "수면"
        type = AbilityType.PASSIVE
        description = listOf(
            text("침대에 눕고 일어날시 강제로 밤낮을 바꿔버립니다."),
            text(""),
            text("하암~ 잘 잤다.").color(NamedTextColor.GRAY)
        )
        wand = ItemStack(Material.RED_BED)

    }
}

class AbilitySleep : Ability<AbilityConceptSleep>(), Listener {

    override fun onEnable() {
        psychic.registerEvents(this)
    }

    @EventHandler
    fun onBedLeave(event: PlayerBedEnterEvent) {
        val world = event.player.world
        event.useBed()
        world.time += 12000L
    }
}
