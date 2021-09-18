/*
 * Copyright (c) 2020 monun
 *
 *  Licensed under the General Public License, Version 3.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/gpl-3.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.github.monun.psychics.plugin

import com.destroystokyo.paper.event.inventory.PrepareResultEvent
import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent
import io.github.monun.psychics.*
import io.github.monun.psychics.item.enchantability
import io.github.monun.psychics.item.isPsychicbound
import io.github.monun.psychics.item.psionicsLevel
import io.github.monun.psychics.item.removeAllPsychicbounds
import io.github.monun.tap.fake.FakeEntityServer
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.enchantment.EnchantItemEvent
import org.bukkit.event.entity.EntityRegainHealthEvent
import org.bukkit.event.entity.ItemSpawnEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.*
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.random.Random.Default.nextFloat
import kotlin.random.Random.Default.nextInt

class EventListener(
    private val psychicManager: PsychicManager,
    private val fakeEntityServer: FakeEntityServer
) : Listener {
    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        psychicManager.addPlayer(player)
        fakeEntityServer.addPlayer(player)

        if (player.esper.psychic == null) {
            player.inventory.removeAllPsychicbounds()
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val player = event.player
        psychicManager.removePlayer(player)
        fakeEntityServer.removePlayer(player)
    }

    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        val action = event.action
        val hand = event.hand
        val item = event.item

        if (action != Action.PHYSICAL && hand == EquipmentSlot.HAND && item != null) {
            val player = event.player
            val wandAction =
                if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) ActiveAbility.WandAction.LEFT_CLICK
                else ActiveAbility.WandAction.RIGHT_CLICK

            player.esper.psychic?.castByWand(event, wandAction, item)
        }
    }

    @EventHandler
    fun onInteractEntity(event: PlayerInteractEntityEvent) {
        val player = event.player
        val item = player.inventory.itemInMainHand

        if (item.type != Material.AIR) {
            player.esper.psychic?.castByWand(event, ActiveAbility.WandAction.RIGHT_CLICK, item)
        }
    }

    //psychicbound
    @EventHandler(ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.whoClicked.gameMode == GameMode.CREATIVE) return

        val type = event.inventory.type

        if (type != InventoryType.CRAFTING) {
            event.currentItem?.let { item ->
                if (item.isPsychicbound) {
                    event.isCancelled = true
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onItemSpawn(event: ItemSpawnEvent) {
        if (event.entity.itemStack.isPsychicbound) event.isCancelled = true
    }

    @EventHandler(ignoreCancelled = true)
    fun onPlayerDropItem(event: PlayerDropItemEvent) {
        val item = event.itemDrop
        if (item.itemStack.isPsychicbound) {
            if (event.player.isSneaking) {
                item.remove()
            } else {
                event.isCancelled = true
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onEntityRegainHealth(event: EntityRegainHealthEvent) {
        val entity = event.entity

        if (entity is Player && entity.killer != null) {
            event.isCancelled = true
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onEnchantItem(event: EnchantItemEvent) {
        val item = event.item
        val enchantability = item.type.enchantability

        if (enchantability <= 0) return
        if (nextInt(3) == 0) return // 1/3 확률로 인챈트 실패

        val randEnchantability = 1 + nextInt(enchantability / 4 + 1) + nextInt(enchantability / 4 + 1)
        val k = event.expLevelCost + randEnchantability
        val randBonusPercent = 1.0F + (nextFloat() + nextFloat() - 1.0F) * 0.15F
        var finalLevel = round(k * randBonusPercent)

        if (finalLevel < 1.0F) finalLevel = 1.0F

        item.psionicsLevel = when(finalLevel.toInt()) {
            in 1 until 12 -> 1
            in 12 until 23 -> 2
            in 23 until 34 -> 3
            in 34 until 45 -> 4
            else -> 0
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onPrepareResult(event: PrepareResultEvent) {
        val inv = event.inventory
        val invType = event.inventory.type

        @Suppress("USELESS_ELVIS")
        if (invType == InventoryType.GRINDSTONE) {
            event.result?.apply {
                psionicsLevel = 0
            }
        } else if (invType == InventoryType.ANVIL) {
            event.result?.apply {
                val contents = inv.storageContents
                val first = contents[0] ?: return@apply
                val second = contents[1] ?: return@apply
                var level = max(first.psionicsLevel, second.psionicsLevel)

                if (level > 0) {
                   if (first == second)
                       level++

                    psionicsLevel = min(5, level)
                }
            }
        }
    }

    @EventHandler
    fun onPlayerPostRespawn(event: PlayerPostRespawnEvent) {
        val player = event.player
        psychicManager.getEsper(player)?.let { esper ->
            esper.psychic?.let { psychic ->
                psychic.abilities.forEach { ability ->
                    ability.updateCooldown()
                }
            }
        }
    }

    private val Player.esper: Esper
        get() = requireNotNull(psychicManager.getEsper(this))
}

private fun Psychic.castByWand(event: PlayerEvent, action: ActiveAbility.WandAction, item: ItemStack) {
    esper.psychic?.let { psychic ->
        val ability = psychic.getAbilityByWand(item)

        if (ability is ActiveAbility) {
            val result = ability.tryCast(event, action)

            if (result !== TestResult.Success) {
                result.message(ability)?.let { message ->
                    esper.player.sendActionBar(message)
                }
            }
        }
    }
}