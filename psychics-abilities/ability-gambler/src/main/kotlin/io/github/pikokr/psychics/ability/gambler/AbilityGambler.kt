package io.github.pikokr.psychics.ability.gambler

import io.github.monun.psychics.*
import io.github.monun.psychics.attribute.EsperAttribute
import io.github.monun.psychics.damage.Damage
import io.github.monun.psychics.damage.DamageType
import io.github.monun.tap.config.Config
import io.github.monun.tap.config.Name
import io.github.monun.tap.event.EntityProvider
import io.github.monun.tap.event.TargetEntity
import io.github.monun.tap.fake.FakeEntity
import net.kyori.adventure.text.Component.text
import org.bukkit.*
import org.bukkit.entity.Item
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import kotlin.random.Random

@Name("gambler")
class AbilityConceptGambler : AbilityConcept() {
    @Config
    var teleportPercentage = 70.0f

    @Config
    var percentage = 75.0f

    @Config
    var failDamage = Damage.Companion.of(DamageType.RANGED, EsperAttribute.ATTACK_DAMAGE to 2.0)

    @Config
    var successDamage = 2.0

    init {
        cooldownTime = 10000L
        durationTime = 10000L
        description = listOf(
            text("일정 확률로 엔티티를 강하게 공격"),
            text("또는 자신이 피해를 얻습니다."),
            text("시전 중에 우클릭 할 시에는"),
            text("일정 확률로 랜덤 플레이어에게 TP"),
            text("또는 피해를 입습니다.")
        )
        wand = ItemStack(Material.GHAST_TEAR)
    }
}

class AbilityGambler : Ability<AbilityConceptGambler>() {
    var teleportUsed = false
    var pearl: FakeEntity? = null

    inner class GamblerListener : Listener {
        @EventHandler(ignoreCancelled = true)
        @TargetEntity(EntityProvider.EntityDamageByEntity.Damager::class)
        fun onEntityDamage(event: EntityDamageByEntityEvent) {
            if (durationTime > 0) {
                if (Random.nextDouble(0.0, 100.0) > concept.percentage) {
                    event.damage *= concept.successDamage
                } else {
                    event.isCancelled = true
                    esper.player.psychicDamage(concept.failDamage)
                }
            }
        }

        @EventHandler
        fun interact(e: PlayerInteractEvent) {
            val action = e.action
            if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
                e.item?.let { item ->
                    if (item.type != concept.wand?.type) return
                    if (durationTime > 0 && !teleportUsed) {
                        randomTeleport()
                        teleportUsed = true
                    } else {
                        val result = test()
                        if (result != TestResult.Success) {
                            esper.player.sendActionBar(result.message(this@AbilityGambler))
                            return
                        }
                        psychic.consumeMana(concept.cost)
                        cooldownTime = concept.cooldownTime
                        val cooldownTicks = (concept.cooldownTime / 50).toInt()
                        updateCooldown(cooldownTicks)
                        rotation = 0
                        durationTime = concept.durationTime
                        esper.player.world.playSound(
                            esper.player.location,
                            Sound.ENTITY_PLAYER_LEVELUP,
                            SoundCategory.PLAYERS,
                            1.0f,
                            1.0f
                        )

                        pearl = psychic.spawnFakeEntity(esper.player.eyeLocation.clone().apply {
                            y += 3
                        }, Item::class.java).apply {
                            updateMetadata<Item> {
                                itemStack = ItemStack(Material.ENDER_PEARL)
                                setGravity(false)
                                setCanMobPickup(false)
                                setCanPlayerPickup(false)
                            }
                        }
                    }
                }
            }
        }
    }

    fun updateCooldown(cooldownTicks: Int) {
        concept.wand?.let {
            esper.player.setCooldown(it.type, cooldownTicks)
        }
    }

    var rotation: Long = 0L

    fun randomTeleport() {
        val concept = concept

        val p = Bukkit.getOnlinePlayers().filter { it != esper.player }.shuffled()

        if (Random.nextDouble(0.0, 100.0) > concept.teleportPercentage && p.isNotEmpty()) {
            val pl = p.first()
            esper.player.world.playEffect(esper.player.location, Effect.ENDER_SIGNAL, 0)
            esper.player.teleport(pl)
            pl.world.playEffect(pl.location, Effect.ENDER_SIGNAL, 0)
            esper.player.world.playSound(
                esper.player.location,
                Sound.ENTITY_ENDERMAN_TELEPORT,
                SoundCategory.PLAYERS,
                1.0f,
                1.0f
            )
        } else {
            concept.failDamage.let { damage ->
                val player = esper.player
                player.psychicDamage(damage)
            }
        }
    }

    override fun onEnable() {
        psychic.registerEvents(GamblerListener())
        psychic.runTaskTimer(this::tick, 0L, 1L)
        if (cooldownTime > 0) {
            updateCooldown((cooldownTime / 50L).toInt())
        }
    }

    private fun tick() {
        if (durationTime > 0) {
            val loc = esper.player.eyeLocation.clone().apply {
                yaw = (rotation * 10).toFloat()
                pitch = 0.0f
                y += 2
                add(direction.multiply(2))
            }

            if (!teleportUsed) {
                esper.player.world.spawnParticle(Particle.TOTEM, loc, 1, 0.0, 0.0, 0.0, 0.0)

                esper.player.world.spawnParticle(Particle.TOTEM, esper.player.eyeLocation.clone().apply {
                    yaw = (rotation * 10).toFloat() + 180.0f
                    pitch = 0.0f
                    y += 2
                    add(direction.multiply(2))
                }, 1, 0.0, 0.0, 0.0, 0.0)

                if (rotation == 36L) {
                    rotation = 0
                } else {
                    rotation += 1
                }
            }
            pearl?.let {
                it.moveTo(esper.player.location.clone().apply {
                    y += 3
                })
            }
        } else {
            rotation = 0
            teleportUsed = false
            pearl?.apply {
                    location.world.spawnParticle(Particle.ITEM_CRACK, location, 10, 0.0, 0.0, 0.0, 0.5, (bukkitEntity as Item).itemStack)
                esper.player.world.playSound(
                    location,
                    Sound.BLOCK_AMETHYST_BLOCK_BREAK,
                    SoundCategory.PLAYERS,
                    1.0f,
                    1.0f
                )
                remove()
            }
            pearl = null
        }
    }
}