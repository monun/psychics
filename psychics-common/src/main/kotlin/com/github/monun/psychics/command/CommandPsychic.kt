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

package com.github.monun.psychics.command

import com.github.monun.invfx.openWindow
import com.github.monun.kommand.KommandBuilder
import com.github.monun.kommand.KommandContext
import com.github.monun.kommand.argument.*
import com.github.monun.kommand.sendFeedback
import com.github.monun.psychics.*
import com.github.monun.psychics.invfx.InvPsychic
import com.github.monun.psychics.item.PsychicItem
import com.github.monun.psychics.item.addItemNonDuplicate
import com.github.monun.psychics.item.psionicsLevel
import com.github.monun.psychics.plugin.PsychicPlugin
import com.github.monun.tap.util.updateFromGitHubMagically
import net.kyori.adventure.text.Component.text
import net.md_5.bungee.api.ChatColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

internal object CommandPsychic {
    private lateinit var plugin: PsychicPlugin
    lateinit var manager: PsychicManager

    private lateinit var psychics: KommandArgument<PsychicConcept>

    internal fun initModule(plugin: PsychicPlugin, manager: PsychicManager) {
        this.plugin = plugin
        this.manager = manager

        val psychics = manager.psychicConceptsByName

        this.psychics = MapArgument(psychics::get, psychics::keys)
    }

    fun register(builder: KommandBuilder) {
        builder.apply {
            then("update") {
                executes {
                    plugin.updateFromGitHubMagically("monun", "psychics", "Psychics.jar", it.sender::sendMessage)
                }
            }
            then("attach") {
                then("players" to playerTarget()) {
                    then("psychic" to psychics) {
                        executes {
                            attach(it.sender, it.parseArgument("players"), it.parseArgument("psychic"))
                        }
                    }
                }
            }
            then("detach") {
                then("player" to player()) {
                    executes {
                        detach(it.sender, it.parseArgument("player"))
                    }
                }
            }
            then("info") {
                require { this is Player }
                then("psychic" to psychics) {
                    executes {
                        info(it.sender as Player, it.parseArgument("psychic"))
                    }
                }
                executes {
                    val sender = it.sender as Player
                    val psychic = manager.getEsper(sender)?.psychic

                    if (psychic == null) sender.sendMessage("능력이 없습니다.")
                    else info(sender, psychic.concept)
                }
            }
            then("supply") {
                require { this is Player }
                executes {
                    supply(it.sender as Player)
                }
                then("ability" to AbilityConceptArgument) {
                    executes {
                        supply(it.sender as Player, it.parseArgument("ability"))
                    }
                }
            }
            then("enchant") {
                then("level" to integer(0, 5)) {
                    require {this is Player}
                    executes {
                        enchant(it.sender as Player, it.parseArgument<Int>("level"))
                    }
                }
            }
            then("reload") {
                executes {
                    plugin.reloadPsychics()

                    Bukkit.broadcast(text("${ChatColor.GREEN}Psychics reload complete."), "psychics.reload")
                }
            }
        }
    }

    private fun info(sender: Player, psychicConcept: PsychicConcept) {
        sender.openWindow(InvPsychic.create(psychicConcept, sender.esper::getStatistic))
    }

    private fun attach(sender: CommandSender, players: List<Player>, psychicConcept: PsychicConcept) {
        for (player in players) {
            requireNotNull(manager.getEsper(player)).attachPsychic(psychicConcept).isEnabled = true
            sender.sendFeedback("${player.name}'s ability = ${psychicConcept.name}")
        }
    }

    private fun detach(sender: CommandSender, player: Player) {
        player.esper.detachPsychic()
        sender.sendFeedback("${player.name}'s ability = NONE")
    }

    private fun supply(sender: Player) {
        val esper = requireNotNull(manager.getEsper(sender))
        esper.psychic?.let {
            for (abilityConcept in it.concept.abilityConcepts) {
                supply(sender, abilityConcept)
            }
        }
    }

    private fun supply(sender: Player, abilityConcept: AbilityConcept) {
        sender.inventory.addItemNonDuplicate(abilityConcept.supplyItems)
    }

    private fun enchant(sender: Player, level: Int) {
        val item = sender.inventory.itemInMainHand

        if (item.type == Material.AIR) {
            sender.sendFeedback { text("인챈트할 아이템을 손에 들어주세요.")}
            return
        }

        item.psionicsLevel = level
        sender.sendFeedback { text("아이템에 $level 레벨 ${PsychicItem.psionicsTag.content()}(을)를 부여했습니다.")}
    }
}

object AbilityConceptArgument : KommandArgument<AbilityConcept> {
    override fun parse(context: KommandContext, param: String): AbilityConcept? {
        val sender = context.sender

        if (sender is Player) {
            return CommandPsychic.manager.getEsper(sender)?.psychic?.concept?.abilityConcepts?.find { it.name == param }
        }

        return null
    }

    override fun suggest(context: KommandContext, target: String): Collection<String> {
        val sender = context.sender

        if (sender is Player) {
            return CommandPsychic.manager.getEsper(sender)?.psychic?.concept?.abilityConcepts?.suggest(target) { it.name }
                ?: emptyList()
        }

        return emptyList()
    }
}