package io.github.dytroInc.psychics.ability.swordlanding

import io.github.monun.psychics.Ability
import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.AbilityType
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
import org.bukkit.FluidCollisionMode
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Vector

// 주변 아군들의 속도 증가 + 힐 능력
@Name("sword-landing")
class AbilityConceptSwordLanding : AbilityConcept() {
    @Config
    var golden = Damage.of(DamageType.BLAST, EsperAttribute.ATTACK_DAMAGE to 5.0)

    @Config
    var iron = Damage.of(DamageType.BLAST, EsperAttribute.ATTACK_DAMAGE to 8.0)

    @Config
    var diamond = Damage.of(DamageType.BLAST, EsperAttribute.ATTACK_DAMAGE to 11.0)

    @Config
    var netherite = Damage.of(DamageType.BLAST, EsperAttribute.ATTACK_DAMAGE to 14.0)

    @Config
    var default = Damage.of(DamageType.BLAST, EsperAttribute.ATTACK_DAMAGE to 1.0)

    init {
        type = AbilityType.ACTIVE
        cooldownTime = 50000L
        range = 5.0
        description = listOf(
            text("검을 땅에서 네 블록 떨어진 공중에서 우클릭하면"),
            text("땅으로 낙하하면서 주변에게 데미지를 줍니다.")
        )
        wand = ItemStack(Material.IRON_SWORD)
        displayName = "낙하 공격"
        knockback = 2.5
    }

    override fun onRenderTooltip(tooltip: TooltipBuilder, stats: (EsperStatistic) -> Double) {
        tooltip.stats(text("기본").color(NamedTextColor.WHITE), default) { NamedTextColor.YELLOW to "default" }
        tooltip.stats(text("금").color(NamedTextColor.WHITE), golden) { NamedTextColor.GOLD to "golden" }
        tooltip.stats(text("철").color(NamedTextColor.WHITE), iron) { NamedTextColor.WHITE to "iron" }
        tooltip.stats(text("다이아몬드").color(NamedTextColor.WHITE), diamond) { NamedTextColor.AQUA to "diamond" }
        tooltip.stats(text("네더라이트").color(NamedTextColor.WHITE), netherite) { NamedTextColor.RED to "netherite" }

        tooltip.template("default", stats(default.stats))
        tooltip.template("golden", stats(golden.stats))
        tooltip.template("iron", stats(iron.stats))
        tooltip.template("diamond", stats(diamond.stats))
        tooltip.template("netherite", stats(netherite.stats))
    }

    fun findDamage(type: Material): Damage {
        return when (type) {
            Material.GOLDEN_SWORD -> golden
            Material.IRON_SWORD -> iron
            Material.DIAMOND_SWORD -> diamond
            Material.NETHERITE_SWORD -> netherite
            else -> default
        }
    }

    fun hasDamage(type: Material): Boolean {
        return when (type) {
            Material.GOLDEN_SWORD, Material.IRON_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD -> true
            else -> false
        }
    }
}

class AbilitySwordLanding : Ability<AbilityConceptSwordLanding>(), Listener {

    override fun onEnable() {
        psychic.registerEvents(this)
        psychic.runTaskTimer(this::tick, 0, 1)
    }

    var isLanding = false

    @EventHandler
    fun onRightClick(event: PlayerInteractEvent) {
        val action = event.action
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            event.item?.let {
                if (concept.hasDamage(it.type)) {
                    val player = esper.player

                    val result = test()
                    if (result != TestResult.Success) {
                        result.message(this)?.let { msg -> player.sendActionBar(msg) }
                        return
                    }
                    val hitBlock = player.world.rayTraceBlocks(
                        player.location,
                        Vector(0, -1, 0),
                        4.0,
                        FluidCollisionMode.NEVER
                    )?.hitBlock
                    if (hitBlock != null) return esper.player.sendActionBar(text("지상에서 네 블록 떨어지지 않았습니다."))
                    if (isLanding) return esper.player.sendActionBar(text("지금은 낙하 중입니다."))

                    cooldownTime = concept.cooldownTime
                    val cooldownTicks = (concept.cooldownTime / 50).toInt()
                    updateCooldown(cooldownTicks)
                    isLanding = true
                }

            }

        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onDamage(event: EntityDamageEvent) {
        if (event.cause == EntityDamageEvent.DamageCause.FALL && (isLanding || System.currentTimeMillis() - lastLandTime < 100)) event.damage =
            0.0
    }

    @EventHandler
    fun onDeath(event: PlayerDeathEvent) {
        isLanding = false
    }

    var lastLandTime = 0L

    private fun tick() {
        if (isLanding) {
            val player = esper.player
            player.velocity = Vector(0, -5, 0)
            if ((player as Entity).isOnGround) {
                lastLandTime = System.currentTimeMillis()
                val range = concept.range
                player.getNearbyEntities(range, range, range)
                    .mapNotNull { it as? LivingEntity }
                    .filter { player.hostileFilter().test(it) }
                    .forEach {
                        it.psychicDamage(
                            concept.findDamage(player.inventory.itemInMainHand.type),
                            knockback = concept.knockback
                        )
                    }
                player.spawnParticle(
                    Particle.EXPLOSION_LARGE,
                    player.location.add(0.0, 0.5, 0.0),
                    50,
                    range,
                    0.0,
                    range,
                    0.01
                )
                isLanding = false
            }
        }
    }

    private fun updateCooldown(cooldownTicks: Int) {
        val player = esper.player
        player.setCooldown(Material.GOLDEN_SWORD, cooldownTicks)
        player.setCooldown(Material.IRON_SWORD, cooldownTicks)
        player.setCooldown(Material.DIAMOND_SWORD, cooldownTicks)
        player.setCooldown(Material.NETHERITE_SWORD, cooldownTicks)
    }
}