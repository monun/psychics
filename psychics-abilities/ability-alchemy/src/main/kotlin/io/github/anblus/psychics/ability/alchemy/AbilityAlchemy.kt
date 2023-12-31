package io.github.anblus.psychics.ability.alchemy

import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent
import io.github.monun.psychics.Ability
import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.AbilityType
import io.github.monun.psychics.TestResult
import io.github.monun.psychics.attribute.EsperAttribute
import io.github.monun.psychics.attribute.EsperStatistic
import io.github.monun.psychics.damage.Damage
import io.github.monun.psychics.damage.DamageType
import io.github.monun.psychics.util.hostileFilter
import io.github.monun.tap.config.Name
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.ChatColor
import org.bukkit.Color
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.entity.Entity
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.PotionSplashEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.PotionMeta
import kotlin.math.round

// 다양한 물약 제조
@Name("alchemy")
class AbilityConceptAlchemy : AbilityConcept() {

    init {
        displayName = "연금술"
        type = AbilityType.ACTIVE
        cost = 25.0
        damage = Damage.of(DamageType.BLAST, EsperStatistic.of(EsperAttribute.ATTACK_DAMAGE to 3.0))
        healing = EsperStatistic.of(EsperAttribute.ATTACK_DAMAGE to 3.0)
        description = listOf(
            text("금 주괴 하나를 소모하여 여러 종류의 물약을 제조합니다."),
            text(""),
            text("${ChatColor.BOLD}좌클릭"),
            text("  현재 선택한 물약을 제조합니다."),
            text("${ChatColor.BOLD}우클릭"),
            text("  다른 물약을 선택합니다."),
            text("   - ${ChatColor.DARK_PURPLE}고통 ${ChatColor.WHITE}물약"),
            text("   - ${ChatColor.RED}치유 ${ChatColor.WHITE}물약"),
            text("   - ${ChatColor.BLUE}마나 ${ChatColor.WHITE}물약")
        )
        wand = ItemStack(Material.GOLD_INGOT)

    }

}

class AbilityAlchemy : Ability<AbilityConceptAlchemy>(), Listener {
    companion object {
        private val damageBottle = (ItemStack(Material.SPLASH_POTION)).apply {
            itemMeta = (itemMeta as PotionMeta).apply {
                displayName(
                    text().color(NamedTextColor.WHITE).content("고통 물약").decoration(TextDecoration.ITALIC, false).build()
                )
                color = Color.PURPLE
            }
            addItemFlags(ItemFlag.HIDE_POTION_EFFECTS)
        }
        private val healBottle = (ItemStack(Material.POTION)).apply {
            itemMeta = (itemMeta as PotionMeta).apply {
                displayName(
                    text().color(NamedTextColor.WHITE).content("치유 물약").decoration(TextDecoration.ITALIC, false).build()
                )
                color = Color.RED
            }
            addItemFlags(ItemFlag.HIDE_POTION_EFFECTS)
        }
        private val manaBottle = (ItemStack(Material.POTION)).apply {
            itemMeta = (itemMeta as PotionMeta).apply {
                displayName(
                    text().color(NamedTextColor.WHITE).content("마나 물약").decoration(TextDecoration.ITALIC, false).build()
                )
                color = Color.BLUE
                addItemFlags(ItemFlag.HIDE_POTION_EFFECTS)
            }
        }
        private val potionName = arrayOf("${ChatColor.DARK_PURPLE}${ChatColor.BOLD}고통 물약", "${ChatColor.RED}${ChatColor.BOLD}치유 물약", "${ChatColor.BLUE}${ChatColor.BOLD}마나 물약")
    }
    var eventEntity = mutableListOf<Entity>()

    var selectedPotionNumber: Int = 0

    override fun onEnable() {
        psychic.runTaskTimer({
            val players = esper.player.world.players
            players.run {
                for (player in this) {
                    if (player in eventEntity) break
                    psychic.plugin.entityEventManager.registerEvents(player, this@AbilityAlchemy)
                    eventEntity.add(player)
                }
            }
        }, 0L, 1L)
    }

    override fun onDisable() {
        eventEntity.forEach { player ->
            psychic.plugin.entityEventManager.unregisterEvent(player, this@AbilityAlchemy)
        }
        eventEntity.clear()
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = esper.player
        if (event.player != player) return

        val action = event.action

        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            event.item?.let { item ->
                if (item.type == concept.wand?.type) {
                    if (!psychic.consumeMana(concept.cost)) return player.sendActionBar(TestResult.FailedCost.message(this))
                    if (player.gameMode != GameMode.CREATIVE) item.amount--
                    var potion: ItemStack? = null
                    when (selectedPotionNumber) {
                        0 -> potion = damageBottle.apply {
                                lore(
                                    listOf(
                                        text(" "),
                                        text().content("사용 시:")
                                            .color(NamedTextColor.DARK_PURPLE)
                                            .decoration(TextDecoration.ITALIC, false)
                                            .build(),
                                        text()
                                            .content("-${round(esper.getStatistic(concept.damage?.stats as EsperStatistic) * 10.0) / 10.0} 체력 회복")
                                            .color(NamedTextColor.RED)
                                            .decoration(TextDecoration.ITALIC, false)
                                            .build()
                                    )
                                )
                            }
                        1 -> potion = healBottle.apply {
                                lore(
                                    listOf(
                                        text(" "),
                                        text().content("사용 시:")
                                            .color(NamedTextColor.DARK_PURPLE)
                                            .decoration(TextDecoration.ITALIC, false)
                                            .build(),
                                        text()
                                            .content("+${round(esper.getStatistic(concept.healing as EsperStatistic) * 10.0) / 10.0} 체력 회복")
                                            .color(NamedTextColor.BLUE)
                                            .decoration(TextDecoration.ITALIC, false)
                                            .build()
                                    )
                                )
                            }
                        2 -> potion = manaBottle.apply {
                                lore(
                                    listOf(
                                        text(" "),
                                        text().content("사용 시:")
                                            .color(NamedTextColor.DARK_PURPLE)
                                            .decoration(TextDecoration.ITALIC, false)
                                            .build(),
                                        text()
                                            .content("+${concept.cost.toInt()} 마나 회복")
                                            .color(NamedTextColor.BLUE)
                                            .decoration(TextDecoration.ITALIC, false)
                                            .build()
                                    )
                                )
                            }
                    }
                    potion?.let { player.inventory.addItem(it) }
                }
            }
        } else if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            event.item?.let { item ->
                if (item.type == concept.wand?.type) {
                    selectedPotionNumber += 1
                    if (selectedPotionNumber > 2) selectedPotionNumber = 0
                    player.sendActionBar(text("${potionName[selectedPotionNumber]}${ChatColor.RESET}으로 선택되었습니다."))
                }
            }
        }
    }

    @EventHandler
    fun onPotionLaunch(event: PlayerLaunchProjectileEvent) {
        if (event.itemStack.displayName() == damageBottle.displayName()) {
            psychic.runTask({
                val entity: Entity = event.projectile
                psychic.plugin.entityEventManager.registerEvents(entity, this@AbilityAlchemy)
                eventEntity.add(entity)
            }, 1L)
        }
    }

    @EventHandler
    fun onPotionSplash(event: PotionSplashEvent) {
        val potion = event.potion
        if (potion.item.displayName() == damageBottle.displayName()) {
            psychic.plugin.entityEventManager.unregisterEvent(potion, this@AbilityAlchemy)
            eventEntity.remove(potion)
            event.affectedEntities
                .filter { esper.player.hostileFilter().test(it) }
                .forEach { it.psychicDamage()}
        }
    }

    @EventHandler
    fun onDrinkPotion(event: PlayerItemConsumeEvent) {
        val player = event.player
        if (event.item.isSimilar(healBottle)) {
            player.psychicHeal()
            if (player.gameMode != GameMode.CREATIVE) event.replacement = ItemStack(Material.AIR)
        } else if (event.item.isSimilar(manaBottle)) {
            psychic.manager.getEsper(player)?.psychic?.run {
                mana += this@AbilityAlchemy.concept.cost
            }
            if (player.gameMode != GameMode.CREATIVE) event.replacement = ItemStack(Material.AIR)
        }
    }
}






