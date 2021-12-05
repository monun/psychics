package io.github.dytroInc.psychics.ability.horsewhipping

import io.github.monun.psychics.Ability
import io.github.monun.psychics.AbilityConcept
import io.github.monun.tap.config.Config
import io.github.monun.tap.config.Name
import net.kyori.adventure.text.Component.text
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

@Name("horse-whipping")
class AbilityConceptHorseWhipping : AbilityConcept() {
    @Config
    val speedAmplifier = 2


    init {
        durationTime = 1000L
        description = listOf(
            text("엔티티에게 피해를 입을 때 마다 신속을 받습니다.")
        )
        wand = ItemStack(Material.CARROT_ON_A_STICK)
        displayName = "말 채찍질"
    }
}

class AbilityHorseWhipping : Ability<AbilityConceptHorseWhipping>(), Listener {

    override fun onEnable() {
        psychic.registerEvents(this)
    }


    @EventHandler
    fun onTakeDamageByEntity(event: EntityDamageByEntityEvent) {
        val player = esper.player
        if (event.entity == player) {
            player.addPotionEffect(
                PotionEffect(PotionEffectType.SPEED, (concept.durationTime / 50.0).toInt(), concept.speedAmplifier - 1)
            )
        }
    }
}