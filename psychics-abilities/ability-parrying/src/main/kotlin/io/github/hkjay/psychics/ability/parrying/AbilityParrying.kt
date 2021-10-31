package io.github.hkjay.psychics.ability.parrying

import io.github.monun.psychics.Ability
import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.attribute.EsperAttribute
import io.github.monun.psychics.attribute.EsperStatistic
import io.github.monun.psychics.damage.Damage
import io.github.monun.psychics.damage.DamageType
import io.github.monun.psychics.damage.psychicDamage
import io.github.monun.psychics.tooltip.TooltipBuilder
import io.github.monun.psychics.util.TargetFilter
import io.github.monun.tap.config.Config
import io.github.monun.tap.config.Name
import io.github.monun.tap.effect.playFirework
import io.github.monun.tap.event.EntityProvider
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.*
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.potion.PotionType
import org.bukkit.util.BoundingBox
import kotlin.math.max

@Name("parrying")
class AbilityConceptParrying : AbilityConcept() {

    @Config
    var damageReflectRadius = 1.0

    @Config
    var resistanceTime = 1000L

    @Config
    var recoveryDurability = 150

    @Config
    var lossDurability = 100
    init {
        cooldownTime = 5000L
        durationTime = 400L
        range = 5.0
        cost = 10.0
        description = listOf(
            text("방패를 사용시 ${lossDurability}의내구도를 소비하고"),
            text("잠시동안 모든 피해를 무시합니다"),
            text("성공적으로 방어시 주변에 받은피해의 ${damageReflectRadius}배 만큼의 피해를 입히고"),
            text("잠시동안 무적상태가 됩니다"),
            text("또한 ${recoveryDurability}만큼의 내구도를 회복합니다")
        )
        wand = ItemStack(Material.SHIELD)
    }
    override fun onRenderTooltip(tooltip: TooltipBuilder, stats: (EsperStatistic) -> Double) {
        tooltip.header(
            text().color(NamedTextColor.GREEN).content("무적지속시간 ").decoration(TextDecoration.ITALIC, false).decorate(TextDecoration.BOLD)
                .append(
                    text((resistanceTime/1000.0)))
                        .append(text().content("초"))
                .build()
        )
    }
}

class AbilityParrying : Ability<AbilityConceptParrying>(), Listener {
    companion object {
        private val effect = FireworkEffect.builder().with(FireworkEffect.Type.BURST).withColor(Color.YELLOW).build()

        private fun LivingEntity.playPsychicEffect() {
            world.playFirework(location, effect)
        }
    }

    override fun onEnable(){
        psychic.registerEvents(ParryingL())
        psychic.registerEvents(this)
    }
    @EventHandler
    fun onDamage(event: EntityDamageEvent){
        val entity = event.entity
        if(entity == esper.player) {
            if(durationTime > 0) {
                val eDamage = event.damage
                event.isCancelled = true
                esper.player.playPsychicEffect()
                val loc = esper.player.location
                val world = loc.world
                val r = max(1.0, concept.range - 2.0)
                val box = BoundingBox.of(loc, r, r, r)
                val damageType = DamageType.RANGED
                val damageAmount = eDamage * concept.damageReflectRadius
                val item = if(esper.player.inventory.itemInOffHand.type == concept.wand?.type) esper.player.inventory.itemInOffHand else esper.player.inventory.itemInMainHand
                if(item.type == concept.wand?.type) {
                    item.apply {
                        durability = ((durability - concept.recoveryDurability).toShort())
                    }
                }
                world.getNearbyEntities(box, TargetFilter(esper.player)).forEach { enemy ->
                    if (enemy is LivingEntity) {
                        enemy.psychicDamage(
                            this@AbilityParrying,
                            damageType,
                            damageAmount,
                            esper.player,
                            esper.player.location,
                        )
                    }
                }
                esper.player.sendMessage(damageAmount.toString())
                esper.player.addPotionEffect(PotionEffect(PotionEffectType.DAMAGE_RESISTANCE,
                    (concept.resistanceTime/50).toInt(),100,false,false,false))
                durationTime = 0
                cooldownTime = 0
            }
        }
    }

    inner class ParryingL : Listener {
        @EventHandler
        fun doParrying(event: PlayerInteractEvent){
            val action = event.action
            if(action == Action.RIGHT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR) {
                val item = event.item
                if(item?.type == concept.wand?.type) {
                item?.apply {
                    if(durability == 335.toShort()) {
                        esper.player.sendActionBar("내구도가 부족합니다")
                        return
                    }
                }
                    if(cooldownTime > 0) return
                    durationTime = concept.durationTime
                    cooldownTime = concept.cooldownTime
                    item?.apply {
                        val losingDurability = when{
                            getEnchantmentLevel(Enchantment.DURABILITY) == 1 -> concept.lossDurability * 0.75
                            getEnchantmentLevel(Enchantment.DURABILITY) == 2 -> concept.lossDurability * 0.5
                            getEnchantmentLevel(Enchantment.DURABILITY) == 3 -> concept.lossDurability * 0.25
                            else -> concept.lossDurability
                        }
                        durability = (((durability + losingDurability.toShort()).toShort()))
                        if(durability > 335) durability = 335.toShort()
                    }
                }
            }
        }
    }

}