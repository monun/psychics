package io.github.dytroInc.psychics.ability.battransform

import io.github.monun.psychics.Ability
import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.AbilityType
import io.github.monun.psychics.TestResult
import io.github.monun.tap.config.Name
import io.github.monun.tap.fake.FakeEntity
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.Bat
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

// 지정한 대상에게 폭죽 효과와 피해를 입히는 능력
@Name("bat-transform")
class AbilityConceptBatTransform : AbilityConcept() {
    init {
        type = AbilityType.COMPLEX
        cooldownTime = 25000L
        durationTime = 15000L
        cost = 20.0
        description = listOf(
            text("우클릭을 하면 ${durationTime / 1000}초 동안 피해 받을 때까지 박쥐로 변신합니다."),
            text("떨어질 때 피해를 받지 않습니다.")
        )
        wand = ItemStack(Material.GHAST_TEAR)
        displayName = "박쥐 변신"
    }
}

class AbilityBatTransform : Ability<AbilityConceptBatTransform>(), Listener {
    private var bat: FakeEntity? = null
    private var time = 0
    override fun onEnable() {
        psychic.registerEvents(this)
        psychic.runTaskTimer(this::tick, 0, 1)
    }
    @EventHandler
    fun onDamage(event: EntityDamageEvent) {
        val victim = event.entity
        if(victim == esper.player) {
            if(event.cause == EntityDamageEvent.DamageCause.FALL) event.isCancelled = true
        }
    }
    private fun tick() {
        if(bat != null && time > 0) {
            bat?.let {
                val player = esper.player
                it.moveTo(player.eyeLocation)
                time--
                if(it.dead || time == 0) {
                    player.allowFlight = false
                    player.isFlying = false
                    bat?.remove()
                    bat = null
                }
            }
        }
    }
    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        val action = event.action
        if(action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            event.item?.let { item ->
                if (item.type == Material.GHAST_TEAR) {
                    val player = esper.player
                    val result = test()
                    if (result != TestResult.Success) {
                        player.sendActionBar(result.message(this))
                        return
                    } else if (!psychic.consumeMana(concept.cost)) {
                        player.sendActionBar(TestResult.FailedCost.message(this))
                        return
                    }
                    cooldownTime = concept.cooldownTime
                    player.sendActionBar(text("박쥐로 변신했습니다.").color(NamedTextColor.DARK_RED))
                    player.allowFlight = true
                    player.isFlying = true
                    time = (concept.durationTime / 50).toInt()
                    player.addPotionEffect(
                        PotionEffect(
                            PotionEffectType.INVISIBILITY,
                            time,
                            1,
                            false,
                            false
                        )
                    )
                    bat = this.psychic.spawnFakeEntity(player.eyeLocation, Bat::class.java).apply {
                        updateMetadata<Bat> {
                            setAI(false)
                            isAwake = true
                        }
                    }
                }
            }
        }
    }
}