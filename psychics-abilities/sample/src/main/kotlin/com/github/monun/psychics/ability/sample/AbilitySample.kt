package com.github.monun.psychics.ability.sample

import com.github.monun.psychics.AbilityConcept
import com.github.monun.psychics.ActiveAbility
import com.github.monun.psychics.TestResult
import com.github.monun.psychics.attribute.EsperAttribute
import com.github.monun.psychics.attribute.EsperStatistic
import com.github.monun.psychics.damage.Damage
import com.github.monun.psychics.damage.DamageType
import com.github.monun.psychics.util.TargetFilter
import com.github.monun.psychics.util.hostileFilter
import net.kyori.adventure.text.Component.text
import org.bukkit.FluidCollisionMode
import org.bukkit.Material
import org.bukkit.entity.LivingEntity
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerEvent
import org.bukkit.inventory.ItemStack

class AbilityConceptSample : AbilityConcept() {
    init {
        cooldownTime = 1000L
        castingTime = 1000L
        range = 64.0
        damage = Damage(DamageType.RANGED, EsperStatistic.of(EsperAttribute.ATTACK_DAMAGE to 1.0))
        description = listOf(
            text("능력 설명")
        )
        wand = ItemStack(Material.STICK)
    }
}

class AbilitySample : ActiveAbility<AbilityConceptSample>(), Listener {
    override fun onInitialize() {
        targeter = {
            val player = esper.player
            val start = player.eyeLocation
            val world = start.world

            world.rayTrace(
                start,
                start.direction,
                concept.range,
                FluidCollisionMode.NEVER,
                true,
                0.5,
                player.hostileFilter()
            )?.hitEntity
        }
    }

    override fun onCast(event: PlayerEvent, action: WandAction, target: Any?) {
        if (target !is LivingEntity) return
        if (action != WandAction.LEFT_CLICK) return

        val concept = concept
        val damage = concept.damage ?: return

        target.psychicDamage(damage, knockback = 1.0)

        psychic.consumeMana(concept.cost)
        cooldownTime = concept.cooldownTime
    }
}