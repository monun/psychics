package io.github.dytroInc.psychics.ability.leap

import com.destroystokyo.paper.event.player.PlayerJumpEvent
import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.AbilityType
import io.github.monun.psychics.ActiveAbility
import io.github.monun.psychics.TestResult
import io.github.monun.psychics.attribute.EsperAttribute
import io.github.monun.psychics.attribute.EsperStatistic
import io.github.monun.psychics.damage.Damage
import io.github.monun.psychics.damage.DamageType
import io.github.monun.psychics.tooltip.TooltipBuilder
import io.github.monun.psychics.tooltip.stats
import io.github.monun.psychics.tooltip.template
import io.github.monun.psychics.util.hostileFilter
import io.github.monun.tap.config.Config
import io.github.monun.tap.config.Name
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

// 신속을 부여하며 높게 뛰면서 착지할 시, 주위 적군들에게 구속과 피해를 입히는 능력
// 아이디어: Anblus
@Name("leap")
class AbilityConceptLeap : AbilityConcept() {

    @Config
    val slownessDurationTime = 5000L

    @Config
    val jumpPower = 0.8

    init {
        type = AbilityType.ACTIVE
        cost = 10.0
        durationTime = 8000L
        range = 2.5
        description = listOf(
            text("능력 발동 후 지속시간 내에 점프를 할 경우"),
            text("점프 강화처럼 공중으로 높게 뛸 수 있습니다."),
            text("능력 발동 후 점프 상태에서 지면에 착지할 경우"),
            text("주위 적군들에게 구속과 피해를 입힙니다."),
        )
        knockback = 0.5
        wand = ItemStack(Material.FEATHER)
        displayName = "도약"
        damage = Damage.of(DamageType.BLAST, EsperStatistic.of(EsperAttribute.ATTACK_DAMAGE to 3.0))
    }

    override fun onRenderTooltip(tooltip: TooltipBuilder, stats: (EsperStatistic) -> Double) {
        tooltip.stats(slownessDurationTime / 1000.0) { NamedTextColor.GOLD to "구속 지속 시간" to "초" }
        tooltip.template("slowness-duration-time", slownessDurationTime / 1000.0)
    }
}

class AbilityLeap : ActiveAbility<AbilityConceptLeap>(), Listener {

    var hasJumped = false
    var lastLandTime = 0L

    override fun onEnable() {
        psychic.registerEvents(this)
        psychic.runTaskTimer(this::tick, 0, 1)
    }

    override fun onCast(event: PlayerEvent, action: WandAction, target: Any?) {
        if (hasJumped) return esper.player.sendActionBar(text("지금은 점프 중입니다."))
        if (!psychic.consumeMana(concept.cost)) return esper.player.sendActionBar(TestResult.FailedCost.message(this))
        durationTime = concept.durationTime
        cooldownTime = concept.durationTime
    }

    private fun tick() {
        if (hasJumped) {
            val player = esper.player
            player.addPotionEffect(
                PotionEffect(
                    PotionEffectType.SPEED, 5, 2
                )
            )
            if ((player as Entity).isOnGround) {
                lastLandTime = System.currentTimeMillis()
                val range = concept.range
                player.getNearbyEntities(range, range, range)
                    .mapNotNull { it as? LivingEntity }
                    .filter { player.hostileFilter().test(it) }
                    .forEach {
                        it.psychicDamage(
                            concept.damage ?: Damage.Companion.of(
                                DamageType.BLAST,
                                EsperStatistic.of(EsperAttribute.ATTACK_DAMAGE to 3.0)
                            ),
                            knockback = concept.knockback
                        )
                        it.addPotionEffect(
                            PotionEffect(
                                PotionEffectType.SLOW, (concept.slownessDurationTime / 50.0).toInt(), 1
                            )
                        )
                    }
                player.spawnParticle(Particle.CLOUD, player.location.add(0.0, 0.5, 0.0), 100, range, 0.0, range, 0.01)
                player.removePotionEffect(PotionEffectType.SPEED)
                hasJumped = false
            }
        }
        if (durationTime <= 0L) return

        val player = esper.player
        val location = player.location.apply { y += 1.8 }
        val world = location.world
        world.spawnParticle(
            Particle.VILLAGER_HAPPY,
            location.x,
            location.y,
            location.z,
            4,
            0.4,
            0.0,
            0.4,
            0.0,
            null,
            true
        )
        player.addPotionEffect(
            PotionEffect(
                PotionEffectType.SPEED, 5, 2
            )
        )
    }

    @EventHandler(ignoreCancelled = true)
    fun onJump(event: PlayerJumpEvent) {
        if (durationTime > 0) {
            event.player.world.playSound(
                event.player.location, Sound.ENTITY_SLIME_JUMP, SoundCategory.MASTER, 2f, 1f
            )
            hasJumped = true
            durationTime = 0
            cooldownTime = 0
            psychic.runTask({
                event.player.velocity = event.player.velocity.apply {
                    x *= 2.5
                    y = concept.jumpPower
                    z *= 2.5
                }
            }, 0L)
        }
    }

    @EventHandler
    fun onDeath(event: PlayerDeathEvent) {
        hasJumped = false
        durationTime = 0
        cooldownTime = 0
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onDamage(event: EntityDamageEvent) {
        if (event.cause == EntityDamageEvent.DamageCause.FALL && (hasJumped || System.currentTimeMillis() - lastLandTime < 100)) event.isCancelled =
            true
    }

}