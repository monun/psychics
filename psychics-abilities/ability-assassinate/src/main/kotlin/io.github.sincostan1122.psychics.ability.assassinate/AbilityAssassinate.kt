package io.github.sincostan1122.psychics.ability.assassinate



import io.github.monun.psychics.ActiveAbility
import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.attribute.EsperAttribute
import io.github.monun.psychics.attribute.EsperStatistic
import io.github.monun.tap.config.Config
import io.github.monun.tap.config.Name
import net.kyori.adventure.text.Component.text
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType


@Name("assassin")
class AbilityConceptAssassinate : AbilityConcept() {

    @Config
    var isskillon = 0

    init {
        cost = 0.0
        durationTime = 5000L
        cooldownTime = 20000L
        wand = ItemStack(Material.GOLD_INGOT)
        displayName = "은신/습격"

        description = listOf(
            text("사용 시 ${durationTime / 1000.0}초간 은신합니다."),
            text("시전중 이동 속도가 상승하지만, 공격력, 공격 속도가 감소합니다."),
            text("이때 이동 속도 증가량은 공격력에 비례합니다."),
            text("지속시간이 끝나거나 재사용할 시 은신이 풀립니다."),
            text("만약 재사용하였다면, 공격 속도와 공격력이 증가합니다."),
            text("공격 속도와 공격력 증가량도 공격력에 비례합니다.")


        )
    }
}
class AbilityAssassinate : ActiveAbility<AbilityConceptAssassinate>(), Listener {
    override fun onInitialize() {
        cooldownTime = 0
    }
    override fun onEnable() {
        psychic.registerEvents(this)

        psychic.runTaskTimer({
            if (durationTime == 0L) {
                if (concept.isskillon == 1) {
                    cooldownTime = concept.cooldownTime
                    concept.isskillon = 0
                }


            }
        },0L, 1L)
    }

    override fun onCast(event: PlayerEvent, action: WandAction, target: Any?) {

        if(concept.isskillon == 0) {
            val world = esper.player.location.world
            world.spawnParticle(Particle.SPELL_MOB_AMBIENT, esper.player.location, 30)

            esper.player.addPotionEffect(
                PotionEffect(PotionEffectType.INVISIBILITY, (concept.durationTime / 50.0).toInt(), 1, false, false, false)
            )
            esper.player.addPotionEffect(
                PotionEffect(PotionEffectType.SPEED, (concept.durationTime / 50.0).toInt(), 2 +  esper.getStatistic(EsperStatistic.of(EsperAttribute.ATTACK_DAMAGE to 1.0)).toInt(), false, false, false)
            )
            esper.player.addPotionEffect(
                PotionEffect(PotionEffectType.SLOW_DIGGING, (concept.durationTime / 50.0).toInt(), 20, false, false, false)
            )
            esper.player.addPotionEffect(
                PotionEffect(PotionEffectType.WEAKNESS, (concept.durationTime / 50.0).toInt(), 20, false, false, false)
            )
            durationTime = concept.durationTime
            concept.isskillon = 1
        }
        else if(concept.isskillon == 1) {
            val world = esper.player.location.world
            world.spawnParticle(Particle.CLOUD, esper.player.location, 5)

            esper.player.removePotionEffect(PotionEffectType.SLOW_DIGGING)
            esper.player.removePotionEffect(PotionEffectType.WEAKNESS)
            esper.player.removePotionEffect(PotionEffectType.SPEED)
            esper.player.removePotionEffect(PotionEffectType.INVISIBILITY)

            esper.player.addPotionEffect(
                PotionEffect(PotionEffectType.INCREASE_DAMAGE, 40, 2 +  esper.getStatistic(EsperStatistic.of(EsperAttribute.ATTACK_DAMAGE to 1.0)).toInt(), false, false, false)
            )
            esper.player.addPotionEffect(
                PotionEffect(PotionEffectType.FAST_DIGGING,40, 2 +  esper.getStatistic(EsperStatistic.of(EsperAttribute.ATTACK_DAMAGE to 1.0)).toInt(), false, false, false)
            )
            concept.isskillon = 0
            cooldownTime = concept.cooldownTime

        }

    }





}