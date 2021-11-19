package io.github.dytroInc.psychics.ability.battransform

import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.AbilityType
import io.github.monun.psychics.ActiveAbility
import io.github.monun.psychics.TestResult
import io.github.monun.tap.config.Name
import io.github.monun.tap.fake.FakeEntity
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.Bat
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

// 낙뎀은 없고 박쥐로 변신하는 능력.
@Name("bat-transform")
class AbilityConceptBatTransform : AbilityConcept() {
    init {
        type = AbilityType.COMPLEX
        cooldownTime = 30000L
        durationTime = 10000L
        cost = 45.0
        description = listOf(
            text("우클릭을 하면 ${durationTime / 1000}초 동안 피해 받을 때까지 박쥐로 변신합니다."),
            text("박쥐 상태에는 무적이 되며 피해를 줄 수 없습니다."),
            text("떨어질 때 피해를 받지 않습니다."),
        )
        wand = ItemStack(Material.GHAST_TEAR)
        displayName = "박쥐 변신"
    }
}

class AbilityBatTransform : ActiveAbility<AbilityConceptBatTransform>(), Listener {
    private var bat: FakeEntity? = null
    private var time = 0
    override fun onEnable() {
        psychic.registerEvents(this)
        psychic.runTaskTimer(this::tick, 0, 1)
    }

    override fun onDisable() {
        val player = esper.player
        player.allowFlight = false
        player.isFlying = false
        player.isInvulnerable = false
        bat?.remove()
        bat = null
    }

    @EventHandler
    fun onDamage(event: EntityDamageEvent) {
        val victim = event.entity
        if (victim == esper.player) {
            if (event.cause == EntityDamageEvent.DamageCause.FALL) event.isCancelled = true
        }
    }

    private fun tick() {
        if (bat != null && time > 0) {
            bat?.let {
                val player = esper.player
                it.moveTo(player.eyeLocation)
                time--
                if (it.dead || time == 0) {
                    player.allowFlight = false
                    player.isFlying = false
                    player.isInvulnerable = false
                    bat?.remove()
                    bat = null
                }
            }
        }
    }

    override fun onCast(event: PlayerEvent, action: WandAction, target: Any?) {
        val player = event.player
        if (!psychic.consumeMana(concept.cost)) return player.sendActionBar(TestResult.FailedCost.message(this))
        cooldownTime = concept.cooldownTime
        player.sendActionBar(text("박쥐로 변신했습니다.").color(NamedTextColor.DARK_RED))
        player.allowFlight = true
        player.isFlying = true
        player.isInvulnerable = true
        time = (concept.durationTime / 50).toInt()
        player.addPotionEffects(
            listOf(
                PotionEffect(
                    PotionEffectType.INVISIBILITY,
                    time,
                    1,
                    false,
                    false
                ),
                PotionEffect(
                    PotionEffectType.WEAKNESS,
                    time,
                    255,
                    false,
                    false
                )
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