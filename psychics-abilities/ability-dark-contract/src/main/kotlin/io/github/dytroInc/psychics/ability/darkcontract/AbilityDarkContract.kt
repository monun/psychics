package io.github.dytroInc.psychics.ability.darkcontract

import io.github.monun.psychics.Ability
import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.AbilityType
import io.github.monun.psychics.util.hostileFilter
import io.github.monun.tap.config.Name
import io.github.monun.tap.event.EntityProvider
import io.github.monun.tap.event.TargetEntity
import net.kyori.adventure.text.Component.text
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Monster
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

// 주석으로 설명하기 힘든 능력
@Name("dark-contract")
class AbilityConceptDarkContract : AbilityConcept() {

    init {
        type = AbilityType.ACTIVE
        cooldownTime = 1000L
        description = listOf(
            text("칼을 우클릭해 능력을 발동하면 플레이어나 몬스터를"),
            text("죽일 때까진 기본적으로 구속과 나약함을 받지만"),
            text("몬스터 또는 플레이어를 때릴 때 마다 잠시 기존"),
            text("디버프가 사라지고 속도를 얻고 주는 피해도 늘어납니다."),
        )
        wand = ItemStack(Material.DIAMOND_SWORD)
        displayName = "어둠의 계약"
    }

    fun isSword(type: Material): Boolean {
        return when (type) {
            Material.WOODEN_SWORD, Material.STONE_SWORD, Material.GOLDEN_SWORD, Material.IRON_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD -> true
            else -> false
        }
    }
}

class AbilityDarkContract : Ability<AbilityConceptDarkContract>(), Listener {

    override fun onEnable() {
        psychic.registerEvents(this)
        psychic.runTaskTimer(this::tick, 0, 1)
        updateCooldown(cooldownTime.toInt())
    }

    private fun tick() {
        if (cooldownTime <= 0L) return

        val player = esper.player
        val location = player.location.apply { y += 2.0 }
        val world = location.world
        world.spawnParticle(Particle.CRIT_MAGIC, location.x, location.y, location.z, 2, 0.5, 0.0, 0.5, 0.0, null, true)
        if (!player.hasPotionEffect(PotionEffectType.SPEED)) player.addPotionEffects(
            mutableListOf(
                PotionEffect(PotionEffectType.SLOW, 6, 2), PotionEffect(PotionEffectType.WEAKNESS, 6, 0)
            )
        )
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    @TargetEntity(EntityProvider.EntityDamageByEntity.Damager::class)
    fun onDamage(event: EntityDamageByEntityEvent) {
        if (cooldownTime > 0) {
            val entity = event.entity
            if (esper.player.hostileFilter()
                    .test(entity) && (entity is Player || entity is Monster) && entity is LivingEntity && event.finalDamage > 0
            ) {
                esper.player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 20, 4))
                esper.player.removePotionEffect(PotionEffectType.SLOW)
                esper.player.removePotionEffect(PotionEffectType.WEAKNESS)
                event.damage *= 1.4
            }
        }
    }

    @EventHandler
    @TargetEntity(EntityProvider.EntityDeath.Killer::class)
    fun onKill(event: EntityDeathEvent) {
        if (cooldownTime > 0) {
            val entity = event.entity
            if (entity is Player || entity is Monster) {
                updateCooldown(0)
                cooldownTime = 0L
            }
        }
    }

    @EventHandler
    fun onDeath(event: PlayerDeathEvent) {
        if (cooldownTime > 0) {
            updateCooldown(0)
            cooldownTime = 0L
        }
    }

    @EventHandler
    fun onRightClick(event: PlayerInteractEvent) {
        val action = event.action
        if (cooldownTime > 0) return
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            event.item?.let {
                if (concept.isSword(it.type)) {
                    cooldownTime = Int.MAX_VALUE.toLong()
                    updateCooldown(Int.MAX_VALUE)
                    val player = event.player
                    player.world.playSound(
                        player.location,
                        Sound.ENTITY_EVOKER_PREPARE_ATTACK,
                        SoundCategory.PLAYERS,
                        2f,
                        1f
                    )
                }

            }

        }
    }

    private fun updateCooldown(cooldownTicks: Int) {
        val player = esper.player
        player.setCooldown(Material.WOODEN_SWORD, cooldownTicks)
        player.setCooldown(Material.STONE_SWORD, cooldownTicks)
        player.setCooldown(Material.GOLDEN_SWORD, cooldownTicks)
        player.setCooldown(Material.IRON_SWORD, cooldownTicks)
        player.setCooldown(Material.DIAMOND_SWORD, cooldownTicks)
        player.setCooldown(Material.NETHERITE_SWORD, cooldownTicks)
    }
}