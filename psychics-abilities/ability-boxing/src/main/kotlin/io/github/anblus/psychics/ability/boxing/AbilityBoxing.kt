package io.github.anblus.psychics.ability.boxing

import io.github.monun.psychics.Ability
import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.AbilityType
import io.github.monun.psychics.util.hostileFilter
import io.github.monun.tap.config.Config
import io.github.monun.tap.config.Name
import io.github.monun.tap.effect.playFirework
import io.github.monun.tap.event.EntityProvider
import io.github.monun.tap.event.TargetEntity
import net.kyori.adventure.text.Component.text
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.FireworkEffect
import org.bukkit.Material
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.boss.BossBar
import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import kotlin.math.absoluteValue


// 리듬 펀치
@Name("boxing")
class AbilityConceptBoxing : AbilityConcept() {

    @Config
    val timingRule = 0.05

    @Config
    val damageMaxMultiple = 2.0

    init {
        displayName = "권투"
        type = AbilityType.PASSIVE
        durationTime = 5000L
        description = listOf(
            text("지속 시간 안에 적을 연속해서 공격시 콤보 간격이 정해집니다."),
            text("공격시 공격 시기가 콤보 간격에 가까울수록 적에게"),
            text("주는 피해가 늘어납니다. 또한 두 시간 차이가 "),
            text("일정 시간 이하일 경우 추가로 적을 잠시동안 실명시킵니다."),
            text("콤보 간격이 정해진 이후 지속 시간 안에 적에게 피해를"),
            text("입히지 않은 경우 콤보 간격은 초기화 됩니다.")
        )
        wand = ItemStack(Material.FIRE_CHARGE)

    }

}

class AbilityBoxing : Ability<AbilityConceptBoxing>(), Listener {
    private var rhythmBar: BossBar? = null

    private var startBar: BossBar? = null

    var firstAttack = 0L

    var comboRhythm = 0L

    var currentRhythm = 0L

    override fun onEnable() {
        psychic.registerEvents(this)
        val player = esper.player
        rhythmBar = Bukkit.createBossBar("▼", BarColor.RED, BarStyle.SEGMENTED_20).apply {
            addPlayer(player)
            isVisible = false
        }
        startBar = Bukkit.createBossBar("콤보 시작", BarColor.YELLOW, BarStyle.SOLID).apply {
            addPlayer(player)
            isVisible = false
        }
        psychic.runTaskTimer({
            if (firstAttack != 0L) {
                if (System.currentTimeMillis() - firstAttack > concept.durationTime) {
                    firstAttack = 0L
                }
            }
            if (comboRhythm != 0L) {
                currentRhythm += 50L
                if (currentRhythm > comboRhythm * 2) {
                    comboRhythm = 0L
                    currentRhythm = 0L
                }
            }
            rhythmBar?.let { bar ->
                if (comboRhythm != 0L && !bar.isVisible) {
                    bar.isVisible = true
                } else if (bar.isVisible) {
                    if (comboRhythm == 0L) {
                        bar.isVisible = false
                    } else {
                        bar.progress = currentRhythm.toDouble() / (comboRhythm.toDouble() * 2.0)
                    }
                }
            }
            startBar?.let { bar ->
                if (firstAttack != 0L && !bar.isVisible) {
                    bar.isVisible = true
                } else if (bar.isVisible) {
                    if (firstAttack == 0L) {
                        bar.isVisible = false
                    } else {
                        bar.progress =
                            (System.currentTimeMillis() - firstAttack).toDouble() / concept.durationTime.toDouble()
                    }
                }
            }
        }, 0L, 1L)
    }

    override fun onDisable() {
        rhythmBar?.removeAll()
        startBar?.removeAll()
    }

    @EventHandler
    @TargetEntity(EntityProvider.EntityDamageByEntity.Damager::class)
    fun onDamaged(event: EntityDamageByEntityEvent) {
        val entity = event.entity
        if (esper.player.hostileFilter().test(entity) && entity is LivingEntity && event.finalDamage > 0) {
            if (comboRhythm == 0L) {
                if (firstAttack == 0L) {
                    firstAttack = System.currentTimeMillis()
                } else {
                    comboRhythm = System.currentTimeMillis() - firstAttack
                    firstAttack = 0L
                }
            } else {
                val timingMatch = (0.5 - (currentRhythm.toDouble() / (comboRhythm.toDouble() * 2.0))).absoluteValue
                val world = entity.world
                val loc = entity.location
                if (timingMatch <= concept.timingRule) {
                    entity.addPotionEffect(
                        PotionEffect(PotionEffectType.BLINDNESS, 30, 4)
                    )
                    entity.addPotionEffect(
                        PotionEffect(PotionEffectType.SLOW, 30, 4)
                    )
                    world.playFirework(
                        loc,
                        FireworkEffect.builder().with(FireworkEffect.Type.BALL).withColor(Color.RED).build()
                    )
                    event.damage *= (1.0).coerceAtLeast(concept.damageMaxMultiple)
                } else if (timingMatch <= (concept.timingRule * 1.5)) {
                    world.playFirework(
                        loc,
                        FireworkEffect.builder().with(FireworkEffect.Type.BALL).withColor(Color.ORANGE).build()
                    )
                    event.damage *= (1.0).coerceAtLeast(concept.damageMaxMultiple / 5 * 4)
                } else if (timingMatch <= (concept.timingRule * 2.0)) {
                    world.playFirework(
                        loc,
                        FireworkEffect.builder().with(FireworkEffect.Type.BALL).withColor(Color.YELLOW).build()
                    )
                    event.damage *= (1.0).coerceAtLeast(concept.damageMaxMultiple / 5 * 3)
                }
                currentRhythm = 0L
            }
        }
    }
}






