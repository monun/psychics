package io.github.anblus.psychics.ability.guardian

import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.AbilityType
import io.github.monun.psychics.ActiveAbility
import io.github.monun.psychics.Channel
import io.github.monun.psychics.attribute.EsperAttribute
import io.github.monun.psychics.attribute.EsperStatistic
import io.github.monun.psychics.util.friendlyFilter
import io.github.monun.tap.config.Name
import io.github.monun.tap.trail.TrailSupport
import net.kyori.adventure.text.Component.text
import org.bukkit.FluidCollisionMode
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector
import kotlin.math.pow
import kotlin.math.sqrt


// 수호 하는 포탑
@Name("guardian")
class AbilityConceptGuardian : AbilityConcept() {

    init {
        displayName = "수호자"
        type = AbilityType.ACTIVE
        cooldownTime = 10000L
        cost = 60.0
        range = 16.0
        castingTime = 2000L
        healing = EsperStatistic.of(EsperAttribute.ATTACK_DAMAGE to 1.0)
        description = listOf(
            text("능력 발동시 현재 위치에 주위의 아군을"),
            text("지원하는 가디언을 소환합니다."),
            text("시전 중에는 움직일 수 없습니다.")
        )
        wand = ItemStack(Material.PRISMARINE_SHARD)
    }

}

class AbilityGuardian : ActiveAbility<AbilityConceptGuardian>(), Listener {
    private var guardian: LivingEntity? = null

    override fun onChannel(channel: Channel) {
        esper.player.addPotionEffect(
            PotionEffect(PotionEffectType.SLOW, 5, 4, false, false, false)
        )
    }

    override fun onEnable() {
        psychic.runTaskTimer(this::onTicks, 0L, 20L)
        psychic.runTaskTimer({
            guardian?.let { guardian ->
                val location = guardian.location
                guardian.setRotation(if (location.yaw >= 360) 0f else (location.yaw + 10f), 0f)
            }
        }, 0L, 1L)
    }

    override fun onDisable() {
        guardian?.let { it.remove() }
    }

    fun onTicks() {
        guardian?.let { guardian ->
            if (!guardian.isDead) {
                val location = guardian.eyeLocation
                val range = concept.range
                location.getNearbyEntities(
                    range.toDouble(), range.toDouble(), range.toDouble()
                ).filter { entity -> esper.player.friendlyFilter().test(entity) }
                    .plus(esper.player)
                    .forEach { entity ->
                        if (entity is LivingEntity) {
                            val world = location.world
                            val subjectX = entity.location.x - location.x
                            val subjectY = entity.location.y - location.y
                            val subjectZ = entity.location.z - location.z
                            val distance = sqrt(subjectX.pow(2) + subjectY.pow(2) + subjectZ.pow(2))
                            val direction = Vector(subjectX / distance, subjectY / distance, subjectZ / distance)
                            val hitResult = world.rayTrace(
                                location,
                                direction,
                                concept.range,
                                FluidCollisionMode.NEVER,
                                true,
                                1.0
                            ) { target -> target == entity }

                            hitResult?.let { result ->
                                if (result.hitEntity == entity) {
                                    TrailSupport.trail(location, entity.location, 0.3) { w, x, y, z ->
                                        w.spawnParticle(Particle.COMPOSTER, x, y, z, 1, 0.0, 0.0, 0.0, 0.0)
                                    }
                                    world.playSound(entity.location, Sound.BLOCK_BEACON_DEACTIVATE, 2.0F, 2.0F)
                                    entity.psychicHeal()
                                    entity.addPotionEffect(
                                        PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 40, 0, false, false, false)
                                    )
                                }
                            }
                        }
                    }
            } else {
                guardian.remove()
            }
        }
    }

    override fun onCast(event: PlayerEvent, action: WandAction, target: Any?) {
        val player = event.player
        val location = player.location
        cooldownTime = concept.cooldownTime
        psychic.consumeMana(concept.cost)

        guardian?.let { guardian ->
            guardian.remove()
        }

        guardian = player.world.spawnEntity(location, EntityType.GUARDIAN) as LivingEntity
        guardian?.let { guardian ->
            guardian.setAI(false)
            guardian.maxHealth = 0.1
            guardian.customName = "${esper.player.name}님의 수호자"
        }
    }
}








