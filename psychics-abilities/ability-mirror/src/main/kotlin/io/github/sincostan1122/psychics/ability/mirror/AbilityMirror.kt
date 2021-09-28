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
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerEvent
import org.bukkit.event.player.PlayerVelocityEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType


@Name("mirror")
class AbilityConceptMirror : AbilityConcept() {

    @Config
    var burftime = 1

    init {
        cost = 0.0
        durationTime = 1000L
        cooldownTime = 10000L
        wand = ItemStack(Material.IRON_INGOT)
        displayName = "응수"

        description = listOf(
            text("사용 시 ${durationTime / 1000.0}초간 응수 상태가 됩니다."),
            text("시전중 이동 속도, 공격력, 공격 속도가 감소합니다."),
            text("단, 받는 피해가 줄어들고 넉백 효과에 면역이 생깁니다."),
            text("응수 상태 동안 넉백 효과를 받을 시 다음 효과를 얻습니다:") ,
            text("이동 속도, 공격력, 공격 속도 감소효과 해제"),
            text("공격력,이동 속도, 공격 속도 ${burftime}초 증가")
        )
    }
}
class AbilityMirror : ActiveAbility<AbilityConceptMirror>(), Listener {

    override fun onEnable() {
        psychic.registerEvents(this)

    }

    private fun particleloc(): Location {
        return esper.player.location
    }
    override fun onCast(event: PlayerEvent, action: WandAction, target: Any?) {
        val world = particleloc().world
        world.spawnParticle(Particle.SPELL_INSTANT, particleloc(), 30)



        esper.player.addPotionEffect(
            PotionEffect(PotionEffectType.SLOW, (concept.durationTime / 50.0).toInt(), 5, false, false, false)
        )
        esper.player.addPotionEffect(
            PotionEffect(PotionEffectType.SLOW_DIGGING, (concept.durationTime / 50.0).toInt(), 20, false, false, false)
        )
        esper.player.addPotionEffect(
            PotionEffect(PotionEffectType.WEAKNESS, (concept.durationTime / 50.0).toInt(), 10, false, false, false)
        )

        cooldownTime = concept.cooldownTime
        durationTime = concept.durationTime

    }

    @EventHandler(ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageEvent) {
        if (durationTime > 0L) {
            val damage = event.damage * 0.1
            event.damage = damage
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onVelocity(event: PlayerVelocityEvent) {

        if (durationTime > 0L) {
            event.isCancelled = true


            esper.player.removePotionEffect(PotionEffectType.SLOW_DIGGING)
            esper.player.removePotionEffect(PotionEffectType.WEAKNESS)
            esper.player.removePotionEffect(PotionEffectType.SLOW)
            esper.player.addPotionEffect(
                PotionEffect(PotionEffectType.INCREASE_DAMAGE, concept.burftime * 20, 5, false, false, false)
            )
            esper.player.addPotionEffect(
                PotionEffect(PotionEffectType.SPEED, concept.burftime * 20, 5, false, false, false)
            )
            esper.player.addPotionEffect(
                PotionEffect(PotionEffectType.FAST_DIGGING, concept.burftime * 20, 5, false, false, false)
            )


            val player = esper.player
            val location = player.location
            val world = location.world

            world.spawnParticle(Particle.LAVA, particleloc(), 20)

        }
    }


}