package io.github.dytroInc.psychics.ability.mortician

import io.github.monun.psychics.Ability
import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.TestResult
import io.github.monun.psychics.util.friendlyFilter
import io.github.monun.tap.config.Config
import io.github.monun.tap.config.Name
import io.github.monun.tap.event.EntityProvider
import io.github.monun.tap.event.TargetEntity
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Tag
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.text.DecimalFormat

// 죽이고 나서 꽃을 우클릭하면 전체 아군들에게 버프 지급
@Name("mortician")
class AbilityConceptMortician : AbilityConcept() {
    @Config
    val waitingTime = 10000

    init {
        cooldownTime = 20000
        durationTime = 45000
        displayName = "장의사"
        wand = ItemStack(Material.TOTEM_OF_UNDYING).apply {
            itemMeta = itemMeta?.apply {
                removeItemFlags(*ItemFlag.values())
            }
        }
        description = listOf(
            text("플레이어를 죽이고 ${waitingTime / 1000.0}초 내에 꽃을 우클릭하면,"),
            text("전체 아군들에게 ${durationTime / 1000.0}초 동안 지속되는 포화 3과 힘 2를 지급합니다.")
        )
    }
}

class AbilityMortician : Ability<AbilityConceptMortician>(), Listener {

    var remainingTime = 0

    override fun onEnable() {
        psychic.registerEvents(this)
        psychic.runTaskTimer(this::tick, 0, 1)
    }

    private fun tick() {
        if (remainingTime > 0) {
            esper.player.sendActionBar(text("꽃을 ${DecimalFormat("#.#").format(remainingTime / 20.0)}초 내에 우클릭하세요."))
            remainingTime--
        }
    }

    @EventHandler
    @TargetEntity(EntityProvider.EntityDeath.Killer::class)
    fun onKillPlayer(event: PlayerDeathEvent) {
        if (cooldownTime > 0) return esper.player.sendMessage(TestResult.FailedCooldown.message(this))
        cooldownTime = concept.cooldownTime
        remainingTime = (concept.waitingTime / 50.0).toInt()
    }

    @EventHandler
    fun onRightClick(event: PlayerInteractEvent) {
        if (event.action == Action.RIGHT_CLICK_BLOCK || event.action == Action.RIGHT_CLICK_AIR) {
            event.item?.let { item ->
                if (Tag.FLOWERS.isTagged(item.type) && remainingTime > 0) {
                    remainingTime = 0
                    item.amount--
                    val player = esper.player
                    player.sendActionBar(text())
                    Bukkit.getOnlinePlayers().filter { player.friendlyFilter().test(it) || it == player }.forEach {
                        it.sendMessage(text("장의사에게서 포화를 받았습니다.", NamedTextColor.GOLD))
                        it.addPotionEffects(
                            mutableListOf(
                                PotionEffect(
                                    PotionEffectType.SATURATION,
                                    (concept.durationTime / 50.0).toInt(),
                                    2
                                ), // 포화 3 지급
                                PotionEffect(
                                    PotionEffectType.INCREASE_DAMAGE,
                                    (concept.durationTime / 50.0).toInt(),
                                    1
                                ), // 힘 2 지급
                            )
                        )
                    }
                }
            }
        }
    }
}