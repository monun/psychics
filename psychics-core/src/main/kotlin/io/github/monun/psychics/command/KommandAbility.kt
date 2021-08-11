package io.github.monun.psychics.command

import io.github.monun.invfx.openFrame
import io.github.monun.kommand.PluginKommand
import io.github.monun.psychics.esper
import io.github.monun.psychics.invfx.InvPsychic
import net.kyori.adventure.text.Component.text

object KommandAbility {
    fun register(kommand: PluginKommand) {
        kommand.register("ability") {
            requires { isPlayer }
            executes {
                val player = player
                val esper = player.esper
                val psychic = esper.psychic

                if (psychic == null) {
                    feedback(text("능력이 없습니다."))
                } else {
                    player.openFrame(InvPsychic.create(psychic.concept, esper::getStatistic))
                }
            }
        }
    }
}