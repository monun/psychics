package io.github.monun.psychics.scalasupport

import io.github.monun.psychics.Ability
import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.damage.Damage
import io.github.monun.psychics.damage.DamageType
import io.github.monun.psychics.damage.psychicDamage
import io.github.monun.psychics.damage.psychicHeal
import org.bukkit.Location
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player

fun scala_psychicDamage(
    ability: Ability<out AbilityConcept>,
    target: LivingEntity,
    damageType: DamageType,
    damage: Double,
    damager: Player,
    knockbackSource: Location? = damager.location,
    knockbackForce: Double = 0.0
) = target.psychicDamage(ability, damageType, damage, damager, knockbackSource, knockbackForce)

fun scala_psychicDamage_aDamage(
    ability: Ability<out AbilityConcept>,
    target: LivingEntity,
    damage: Damage? = ability.concept.damage,
    knockbackLocation: Location? = ability.esper.player.location,
    knockback: Double = ability.concept.knockback
) = target.psychicDamage(
    ability,
    damage!!.type,
    damage = ability.esper.getStatistic(damage.stats),
    damager = ability.esper.player,
    knockbackSource = knockbackLocation,
    knockbackForce = knockback
)

fun scala_psychicDamage_simple(
    ability: Ability<out AbilityConcept>,
    target: LivingEntity
) = target.psychicDamage(
    ability,
    ability.concept.damage!!.type,
    damage = ability.esper.getStatistic(ability.concept.damage!!.stats),
    damager = ability.esper.player,
    knockbackSource = ability.esper.player.location,
    knockbackForce = ability.concept.knockback
)

fun scala_psychicHeal(
    ability: Ability<out AbilityConcept>,
    target: LivingEntity,
    amount: Double,
    healer: Player
) = target.psychicHeal(ability, amount, healer)