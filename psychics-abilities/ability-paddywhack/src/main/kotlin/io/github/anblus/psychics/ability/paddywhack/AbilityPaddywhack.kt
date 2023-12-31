package io.github.anblus.psychics.ability.paddywhack

import io.github.monun.psychics.Ability
import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.AbilityType
import io.github.monun.psychics.attribute.EsperStatistic
import io.github.monun.psychics.tooltip.TooltipBuilder
import io.github.monun.psychics.tooltip.stats
import io.github.monun.tap.config.Config
import io.github.monun.tap.config.Name
import io.github.monun.tap.event.EntityProvider
import io.github.monun.tap.event.TargetEntity
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.boss.BossBar
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerVelocityEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

// 맞을 수록 강해진다
@Name("paddywhack")
class AbilityConceptPaddywhack : AbilityConcept() {

    @Config
    val stressDecreaseStartTick = 100

    @Config
    val stressDecreaseAmountPerQuarter = 3

    @Config
    val rageProgress = 0.75

    @Config
    val damageMaxMultiple = 2.0

    @Config
    val stressIncreasePerDamage = 7

    @Config
    val rageSpeedAmplifier = 3

    init {
        displayName = "노발대발"
        type = AbilityType.PASSIVE
        description = listOf(
            text("분노 수치가 생깁니다."),
            text("분노 수치는 엔티티에 의해 피해를 입었을 경우 증가합니다."),
            text("분노 수치가 높으면 높을 수록 주는 피해가 증가하며,"),
            text("분노 수치가 충분히 높아지면 격분 상태가 되어 신속 효과와"),
            text("넉백 무시의 효과를 얻습니다."),
            text("분노 수치가 증가하지 않은 채로 일정 시간이 지나면 "),
            text("분노 수치는 자동으로 감소됩니다.")
        )
        wand = ItemStack(Material.BLAZE_POWDER)

    }

    override fun onRenderTooltip(tooltip: TooltipBuilder, stats: (EsperStatistic) -> Double) {
        tooltip.stats(stressDecreaseAmountPerQuarter * 4) { NamedTextColor.DARK_GREEN to "초당 분노 감소" to null }
        tooltip.stats(stressIncreasePerDamage) { NamedTextColor.DARK_PURPLE to "피해당 분노 증가" to null }
        tooltip.stats(damageMaxMultiple) { NamedTextColor.GOLD to "최대 대미지 배수" to "배" }
        tooltip.stats(rageProgress * 100.0) { NamedTextColor.DARK_RED to "격분 도달 수치" to null }
        tooltip.stats(rageSpeedAmplifier) { NamedTextColor.DARK_AQUA to "격분 신속 레벨" to "레벨" }
    }
}

class AbilityPaddywhack : Ability<AbilityConceptPaddywhack>(), Listener {

    private var stressBar: BossBar? = null

    private var stress: Int = 0

    private var stressTime: Int = 0

    private var stressDecreaseCycle: Int = 0

    private var isRaged: Boolean = false

    override fun onEnable() {
        psychic.registerEvents(this)

        val player = esper.player
        stressBar = Bukkit.createBossBar("$stress / 100", BarColor.YELLOW, BarStyle.SEGMENTED_10).apply { addPlayer(player) }

        psychic.runTaskTimer({
            if (stress > 0 && stressTime == 0) {
                if (stressDecreaseCycle == 0) {
                    stressDecreaseCycle = 5
                    stress = (stress - concept.stressDecreaseAmountPerQuarter).coerceAtLeast(0)
                } else stressDecreaseCycle -= 1
            } else {
                stressTime -= 1
                stressDecreaseCycle = 0
            }

            stressBar?.apply {
                if (!isRaged && progress >= concept.rageProgress) {
                    isRaged = true
                    color = BarColor.RED
                } else if (isRaged && progress < concept.rageProgress) {
                    isRaged = false
                    color = BarColor.YELLOW
                }
                setTitle("$stress / 100")
                progress = stress.toDouble() / 100.0
                isVisible = player.isValid
            }

            if (isRaged) player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 3, concept.rageSpeedAmplifier - 1))
        }, 0L, 1L)

        psychic.runTaskTimer({
            if (isRaged) player.world.spawnParticle(Particle.DUST_COLOR_TRANSITION, player.location, 16, 0.4, 1.5, 0.4, 0.0, Particle.DustTransition(Color.RED, Color.RED, 1.0f))
            if ((stress.toDouble() * 0.1).toInt() != 0) player.world.spawnParticle(Particle.VILLAGER_ANGRY, player.location.apply { y += 2.0 }, (stress.toDouble() * 0.1).toInt(), 0.4, 0.0, 0.4, 0.2)
        }, 0L, 5L)
    }

    override fun onDisable() {
        stressBar?.removeAll()
    }

    @EventHandler(ignoreCancelled = true)
    fun onDamaged(event: EntityDamageByEntityEvent) {
        if (event.finalDamage == 0.0) return
        stress = (stress + concept.stressIncreasePerDamage).coerceAtMost(100)
        stressTime = concept.stressDecreaseStartTick
    }

    @EventHandler(ignoreCancelled = true)
    @TargetEntity(EntityProvider.EntityDamageByEntity.Damager::class)
    fun onDamage(event: EntityDamageByEntityEvent) {
        event.damage *= 1.0 + (stress.toDouble() * ((concept.damageMaxMultiple - 1.0) / 100))
    }

    @EventHandler(ignoreCancelled = true)
    fun onVelocity(event: PlayerVelocityEvent) {
        if (isRaged) event.isCancelled = true
    }

    @EventHandler(ignoreCancelled = true)
    fun onDead(event: PlayerDeathEvent) {
        stress = 0
    }
}






