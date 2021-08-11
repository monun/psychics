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

package io.github.monun.psychics.command

import io.github.monun.invfx.openFrame
import io.github.monun.kommand.KommandSource
import io.github.monun.kommand.node.LiteralNode
import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.PsychicConcept
import io.github.monun.psychics.PsychicManager
import io.github.monun.psychics.esper
import io.github.monun.psychics.invfx.InvPsychic
import io.github.monun.psychics.item.PsychicItem
import io.github.monun.psychics.item.addItemNonDuplicate
import io.github.monun.psychics.item.psionicsLevel
import io.github.monun.psychics.plugin.PsychicsPlugin
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.Player

internal object KommandPsychic {
    private lateinit var plugin: PsychicsPlugin
    lateinit var manager: PsychicManager

    internal fun initModule(plugin: PsychicsPlugin, manager: PsychicManager) {
        this.plugin = plugin
        this.manager = manager
    }

    fun register(root: LiteralNode) {
        root.apply {
            val psychicConceptArgument = dynamic { _, input ->
                manager.getPsychicConcept(input)
            }.apply {
                suggests {
                    suggest(manager.psychicConceptsByName.keys)
                }
            }
            val abilityArgument = dynamic { context, input ->
                context.source.playerOrNull?.let { player ->
                    player.esper.psychic?.abilities?.find { it.concept.name == input }
                }
            }

            then("attach") {
                then("players" to players()) {
                    then("psychic" to psychicConceptArgument) {
                        executes {
                            attach(it["players"], it["psychic"])
                        }
                    }
                }
            }
            then("detach") {
                then("player" to player()) {
                    executes {
                        detach(it["player"])
                    }
                }
            }
            then("info") {
                requires { playerOrNull != null }
                then("psychic" to psychicConceptArgument) {
                    executes {
                        info(it["psychic"])
                    }
                }
                executes {
                    val psychic = manager.getEsper(player)?.psychic

                    if (psychic == null) sender.sendMessage("능력이 없습니다.")
                    else info(psychic.concept)
                }
            }
            then("supply") {
                requires { playerOrNull != null }
                executes {
                    supply()
                }
                then("ability" to abilityArgument) {
                    executes {
                        supply(it["ability"])
                    }
                }
            }
            then("enchant") {
                then("level" to int(0, 5)) {
                    requires { playerOrNull != null }
                    executes {
                        enchant(it["level"])
                    }
                }
            }
            then("reload") {
                executes {
                    plugin.reloadPsychics()
                    broadcast(text().content("Psychics reload complete.").color(NamedTextColor.GREEN))
                }
            }
        }
    }

    private fun KommandSource.info(psychicConcept: PsychicConcept) {
        player.openFrame(InvPsychic.create(psychicConcept, player.esper::getStatistic))
    }

    private fun KommandSource.attach(players: List<Player>, psychicConcept: PsychicConcept) {
        for (player in players) {
            requireNotNull(manager.getEsper(player)).attachPsychic(psychicConcept).isEnabled = true
            feedback(text().content(player.name).append(text("'s ability =")).append(text(psychicConcept.name)))
        }
    }

    private fun KommandSource.detach(player: Player) {
        player.esper.detachPsychic()
        feedback(text().content(player.name).append(text("'s ability = NONE")))
    }

    private fun KommandSource.supply() {
        val esper = requireNotNull(manager.getEsper(player))
        esper.psychic?.let {
            for (abilityConcept in it.concept.abilityConcepts) {
                supply(abilityConcept)
            }
        }
    }

    private fun KommandSource.supply(abilityConcept: AbilityConcept) {
        player.inventory.addItemNonDuplicate(abilityConcept.supplyItems)
    }

    private fun KommandSource.enchant(level: Int) {
        val item = player.inventory.itemInMainHand

        if (item.type == Material.AIR) {
            player.sendMessage(text("인챈트할 아이템을 손에 들어주세요."))
            return
        }

        item.psionicsLevel = level
        feedback(text("아이템에 $level 레벨 ${PsychicItem.psionicsTag.content()}(을)를 부여했습니다."))
    }
}
