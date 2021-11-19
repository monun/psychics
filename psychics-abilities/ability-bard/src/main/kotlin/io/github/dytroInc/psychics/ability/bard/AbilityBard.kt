package io.github.dytroInc.psychics.ability.bard

import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.ActiveAbility
import io.github.monun.psychics.attribute.EsperAttribute
import io.github.monun.psychics.attribute.EsperStatistic
import io.github.monun.psychics.tooltip.TooltipBuilder
import io.github.monun.psychics.util.friendlyFilter
import io.github.monun.tap.config.Config
import io.github.monun.tap.config.Name
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.entity.LivingEntity
import org.bukkit.event.player.PlayerEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

// 주변 아군들의 속도 증가 + 힐 능력
@Name("bard")
class AbilityConceptBard : AbilityConcept() {
    @Config
    val speedAmplifier = 2

    init {
        cooldownTime = 12000L
        durationTime = 5000L
        range = 5.0
        description = listOf(
            text("근처 아군들에게 음악을 연주해서 행복함을 느끼게 합니다.")
        )
        wand = ItemStack(Material.STICK)
        healing = EsperStatistic.of(EsperAttribute.ATTACK_DAMAGE to 15.0)
        displayName = "음유시인"
    }

    override fun onRenderTooltip(tooltip: TooltipBuilder, stats: (EsperStatistic) -> Double) {
        tooltip.header(
            text().color(NamedTextColor.YELLOW).content("행복함 ").decoration(TextDecoration.ITALIC, false)
                .decorate(TextDecoration.BOLD)
                .append(
                    text()
                        .color(NamedTextColor.WHITE)
                        .content("체력을 치유하고 속도 ${speedAmplifier}를 ${durationTime / 1000.0}초 동안 받습니다.")
                )
                .build()
        )
    }
}

class AbilityBard : ActiveAbility<AbilityConceptBard>() {

    override fun onCast(event: PlayerEvent, action: WandAction, target: Any?) {
        cooldownTime = concept.cooldownTime
        durationTime = concept.durationTime
        val player = event.player
        player.getNearbyEntities(concept.range, concept.range, concept.range)
            .filter { player.friendlyFilter().test(it) }
            .map { it as LivingEntity }
            .plus(player)
            .forEach {
                it.psychicHeal(concept.healing!!)
                it.addPotionEffect(
                    PotionEffect(
                        PotionEffectType.SPEED,
                        (durationTime / 50).toInt(),
                        concept.speedAmplifier - 1
                    )
                )
            }
        player.world.playSound(
            player.location,
            Sound.BLOCK_NOTE_BLOCK_GUITAR,
            SoundCategory.PLAYERS,
            2f,
            1f
        )
    }
}