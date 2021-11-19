package io.github.dytroInc.psychics.ability.resistance

import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.ActiveAbility
import io.github.monun.psychics.TestResult
import io.github.monun.psychics.attribute.EsperAttribute
import io.github.monun.psychics.attribute.EsperStatistic
import io.github.monun.psychics.util.hostileFilter
import io.github.monun.tap.config.Name
import net.kyori.adventure.text.Component.text
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.entity.LivingEntity
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

// 사거리 내 팀원 치유 + 시전자 나약함
@Name("resistance")
class AbilityConceptResistance : AbilityConcept() {
    init {
        cooldownTime = 1000L
        displayName = "저항"
        wand = ItemStack(Material.IRON_INGOT)
        range = 5.0
        cost = 5.0
        healing = EsperStatistic.of(EsperAttribute.ATTACK_DAMAGE to 2.0)
        description = listOf(
            text("발동할 경우, 사거리 내 팀원들을 치유하고"),
            text("시전자에게 대기 시간 동안 나약함을 지급합니다.")
        )
    }
}

class AbilityResistance : ActiveAbility<AbilityConceptResistance>(), Listener {

    override fun onEnable() {
        psychic.registerEvents(this)
    }

    override fun onCast(event: PlayerEvent, action: WandAction, target: Any?) {
        val player = esper.player
        if (!psychic.consumeMana(concept.cost)) return player.sendActionBar(TestResult.FailedCost.message(this))
        cooldownTime = concept.cooldownTime
        player.addPotionEffect(PotionEffect(PotionEffectType.WEAKNESS, (concept.cooldownTime / 50.0).toInt(), 0))
        player.world.playSound(player.location, Sound.ENTITY_SHULKER_HURT_CLOSED, SoundCategory.PLAYERS, 2.0f, 0.1f)
        player.location.getNearbyEntities(concept.range, concept.range, concept.range)
            .filterNot { player.hostileFilter().test(it) }.forEach {
            if (it is LivingEntity) {
                it.psychicHeal(concept.healing!!)
            }
        }
    }
}