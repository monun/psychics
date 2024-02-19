package io.github.monun.psychics.ability.magicarchery

import io.github.monun.psychics.Ability
import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.TestResult
import io.github.monun.psychics.attribute.EsperAttribute
import io.github.monun.psychics.damage.Damage
import io.github.monun.psychics.damage.DamageType
import io.github.monun.psychics.damage.psychicDamage
import io.github.monun.psychics.effect.spawnFirework
import io.github.monun.psychics.util.TargetFilter
import io.github.monun.tap.config.Name
//import io.github.monun.tap.effect.playFirework
import io.github.monun.tap.trail.TrailSupport
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.*
import org.bukkit.entity.Arrow
import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionType
import kotlin.random.Random.Default.nextFloat

// 화살을 발사시 직선으로 날아가는 화살 발사
@Name("magic-archery")
class AbilityConceptMagicArchery : AbilityConcept() {
    var arrowSize = 1.0

    init {
        range = 64.0
        cost = 25.0
        knockback = 1.0
        damage = Damage.of(DamageType.RANGED, EsperAttribute.ATTACK_DAMAGE to 2.0)
        wand = ItemStack(Material.BOW)
        description = listOf(
            text("일반화살 대신 마법화살을 발사합니다."),
            text("마법화살은 화살을 소비하지 않으며 직선으로 날아가"),
            text("적중한 적에게 피해를 입힙니다."),
            text("피해량과 사거리는 활시위 당김에 비례합니다.").color(NamedTextColor.GRAY),
            text("마법화살은 활의 인챈트 효과를 받습니다.").color(NamedTextColor.GRAY)
        )
    }
}

class AbilityMagicArchery : Ability<AbilityConceptMagicArchery>(), Listener {
    override fun onEnable() {
        psychic.registerEvents(this)
    }

    @EventHandler(ignoreCancelled = true)
    fun onPlayerShootBow(event: EntityShootBowEvent) {
        val projectile = event.projectile

        if (projectile is Arrow) {
            if (projectile.basePotionData.type != PotionType.UNCRAFTABLE || projectile.hasCustomEffects()) return

            val player = esper.player
            val result = test()

            if (result != TestResult.Success) {
                result.message(this)?.let { player.sendActionBar(it) }

                event.isCancelled = true
                return
            }

            event.setConsumeItem(false)
            projectile.remove()

            val concept = concept

            cooldownTime = concept.cooldownTime
            psychic.consumeMana(concept.cost)

            val force = event.force
            val range = concept.range * force
            val start = player.eyeLocation
            val direction = start.direction
            val world = start.world

            world.playSound(
                player.location,
                Sound.ENTITY_ARROW_SHOOT,
                SoundCategory.PLAYERS,
                1.0F,
                0.8F + nextFloat() * 0.4F
            )

            val hitResult = world.rayTrace(
                start,
                direction,
                range,
                FluidCollisionMode.NEVER,
                true,
                concept.arrowSize,
                TargetFilter(player)
            )

            var to = start.clone().add(direction.clone().multiply(range))

            if (hitResult != null) {
                to = hitResult.hitPosition.toLocation(world)

                hitResult.hitEntity?.let { target ->
                    if (target is LivingEntity) {
                        val damage = concept.damage
                        val damageType = damage?.type ?: DamageType.RANGED
                        var damageAmount = damage?.stats?.let { esper.getStatistic(it) } ?: 0.0

                        damageAmount *= projectile.damage / 2
                        damageAmount *= force

                        target.psychicDamage(
                            this@AbilityMagicArchery,
                            damageType,
                            damageAmount,
                            player,
                            player.location,
                            concept.knockback + projectile.knockbackStrength
                        )


                        target.addPotionEffects(projectile.customEffects)

                        if (projectile.fireTicks > 0) {
                            target.fireTicks = 100
                        }
                    }

                    val box = target.boundingBox
                    val effect = FireworkEffect.builder().with(FireworkEffect.Type.BURST)
                        .withColor(if (force == 1.0F) Color.RED else Color.ORANGE).build()
                    world.spawnFirework(box.centerX, box.maxY + 0.5, box.centerZ, effect, psychic.plugin)
                }
            }

            TrailSupport.trail(start, to, 0.4) { w, x, y, z ->
                w.spawnParticle(Particle.CRIT, x, y, z, 1, 0.0, 0.0, 0.0, 0.0)
            }
        }
    }
}