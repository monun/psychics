package io.github.pikokr.psychics.ability.gambler

import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.ActiveAbility
import io.github.monun.psychics.Channel
import io.github.monun.psychics.TestResult
import io.github.monun.tap.config.Config
import io.github.monun.tap.config.Name
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.*
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerEvent
import org.bukkit.inventory.ItemStack
import kotlin.random.Random

// 특정 확률로 근처 유저에게 텔레포트하는 능력
@Name("gambler")
class AbilityConceptGambler : AbilityConcept() {
    @Config
    var percentage = 50.0f

    init {
        cooldownTime = 10000L
        castingTime = 5000L
        cost = 70.0
        description = listOf(
            text("일정 확률로 근처 플레이어에게 순간이동 합니다")
        )
        wand = ItemStack(Material.MAGMA_CREAM)
    }
}

class AbilityGambler : ActiveAbility<AbilityConceptGambler>(), Listener {
    override fun onCast(event: PlayerEvent, action: WandAction, target: Any?) {
        val concept = concept

        psychic.consumeMana(concept.cost)

        val p = esper.player.world.getNearbyEntities(esper.player.location, 50.0, 50.0, 50.0) {
            it is Player && it != esper.player
        }

        if (Random.nextDouble(0.0, 100.0) >= concept.percentage && p.isNotEmpty()) {
            val pl = p.first()
            esper.player.world.playEffect(esper.player.location, Effect.ENDER_SIGNAL, 0)
            esper.player.teleport(pl)
            pl.world.playEffect(pl.location, Effect.ENDER_SIGNAL, 0)
            esper.player.world.playSound(esper.player.location, Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f)
        } else {
            if (p.isEmpty()) {
                esper.player.sendMessage(text("근처에 플레이어가 없습니다", NamedTextColor.RED))
            }
            esper.player.world.createExplosion(esper.player.location, 2.0f, false, false)
        }
    }

    override fun test(): TestResult {
        val result = super.test()

        esper.player.world.playSound(esper.player.location, Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.PLAYERS, 1.0f, 2.0f)

        return result
    }

    override fun onChannel(channel: Channel) {

        val loc = esper.player.eyeLocation.apply {
            yaw = (channel.remainingTime / 4).toFloat()
            pitch = 0.0f
            y += 2
            add(direction.multiply(2))
        }


        esper.player.world.spawnParticle(Particle.TOTEM, loc, 1, 0.0, 0.0, 0.0, 0.0)
    }
}