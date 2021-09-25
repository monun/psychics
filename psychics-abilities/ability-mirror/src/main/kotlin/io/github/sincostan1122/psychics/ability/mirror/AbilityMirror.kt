package io.github.sincostan1122.psychics.ability.mirror


import io.github.monun.psychics.ActiveAbility
import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.attribute.EsperAttribute
import io.github.monun.psychics.attribute.EsperStatistic
import io.github.monun.tap.config.Config
import io.github.monun.tap.config.Name
import io.github.monun.tap.fake.FakeEntity
import net.kyori.adventure.text.Component.text
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.entity.ArmorStand
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerEvent
import org.bukkit.event.player.PlayerVelocityEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType


@Name("mirror")
class AbilityConceptMirror : AbilityConcept() {


    init {
        cost = 0.0
        durationTime = 1000L
        cooldownTime = 10000L
        wand = ItemStack(Material.IRON_INGOT)
        displayName = "응수"

        description = listOf(
            text("사용 시 ${durationTime / 1000.0}초간 응수 상태가 됩니다."),
            text("응수 상태 동안은 이동 속도가 감소되고 공격력, 공격 속도가 감소합니다."),
            text("단, 받는 피해가 줄어들고 넉백 효과에 면역이 생깁니다."),
            text("응수 상태 동안 넉백 효과를 받을 시 잠시 공격력이 크게 증가합니다.")
        )
    }
}
class AbilityMirror : ActiveAbility<AbilityConceptMirror>(), Listener {

    override fun onEnable() {
        psychic.registerEvents(this)
        psychic.runTaskTimer(this::durationEffect, 0L, 1L)
    }


    override fun onCast(event: PlayerEvent, action: WandAction, target: Any?) {

        esper.player.addPotionEffect(
            PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, (concept.durationTime / 50.0).toInt(), 5, false, false, false)
        )
        esper.player.addPotionEffect(
            PotionEffect(PotionEffectType.SLOW, (concept.durationTime / 50.0).toInt(), 5, false, false, false)
        )
        esper.player.addPotionEffect(
            PotionEffect(PotionEffectType.SLOW_DIGGING, (concept.durationTime / 50.0).toInt(), 20, false, false, false)
        )
        esper.player.addPotionEffect(
            PotionEffect(PotionEffectType.WEAKNESS, (concept.durationTime / 50.0).toInt(), 10, false, false, false)
        )
        @EventHandler(ignoreCancelled = true)
        fun onVelocity(event: PlayerVelocityEvent) {
            if (durationTime > 0L) {
                esper.player.removePotionEffect(PotionEffectType.SLOW_DIGGING)
                esper.player.removePotionEffect(PotionEffectType.WEAKNESS)
                esper.player.addPotionEffect(
                    PotionEffect(PotionEffectType.INCREASE_DAMAGE, (concept.durationTime / 50.0).toInt(), 10, false, false, false)
                )
                event.isCancelled = true
                val player = esper.player
                val location = player.location
                val world = location.world

                world.spawnParticle(Particle.FIREWORKS_SPARK, location.x, location.y, location.z, 5, 0.25, 0.0, 0.25, 0.0, null, true)
            }
        }
        cooldownTime = concept.cooldownTime

    }
    private fun durationEffect() {
        if (durationTime <= 0L) return

        val player = esper.player
        val location = player.location
        val world = location.world

        world.spawnParticle(Particle.LAVA, location.x, location.y, location.z, 5, 0.25, 0.0, 0.25, 0.0, null, true)
    }




}