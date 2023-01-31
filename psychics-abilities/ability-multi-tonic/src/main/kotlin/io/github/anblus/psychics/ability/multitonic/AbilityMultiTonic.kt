package io.github.anblus.psychics.ability.multitonic

import io.github.monun.psychics.Ability
import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.AbilityType
import io.github.monun.psychics.TestResult
import io.github.monun.tap.config.Config
import io.github.monun.tap.config.Name
import net.kyori.adventure.text.Component.text
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import kotlin.math.round
import kotlin.random.Random.Default.nextInt

// 다양한 버프 습득
@Name("multi-tonic")
class AbilityConceptMultiTonic : AbilityConcept() {

    @Config
    val vitaminB = 11

    @Config
    val vitaminC = 9

    @Config
    val vitaminD = 10

    @Config
    val calcium = 6

    @Config
    val magnesium = 5

    @Config
    val zinc = 7

    @Config
    val iron = 8

    @Config
    val abilityAmountPerDay = 8

    init {
        displayName = "종합영양제"
        type = AbilityType.ACTIVE
        cost = 40.0
        cooldownTime = 10000L
        castingTime = 1000L
        description = listOf(
            text("${ChatColor.BOLD}${ChatColor.GOLD}복용 방법"),
            text(" - 하루 ${abilityAmountPerDay}회 식후 우클릭 복용"),
            text("${ChatColor.BOLD}${ChatColor.DARK_RED}주의사항"),
            text(" - 영양소별 영양섭취기준(권장섭취량, 충분섭취량 등)을"),
            text("   지켜서 복용하도록 하십시오."),
            text(" - 다량을 일시에 복용하지 마십시오."),
            text(" - 충분한 양의 마나와 함께 복용하십시오."),
            text("${ChatColor.BOLD}${ChatColor.GRAY}*복용으로 인한 알레르기 반응이나 심각한 부작용이 발생 시"),
            text("${ChatColor.BOLD}${ChatColor.GRAY} 제작자에게 상담하여 도움을 받으십시오."),
            text("${ChatColor.BOLD}${ChatColor.GRAY}*영양 성분은 능력 좌클릭에 나와있습니다."),
        )
        wand = ItemStack(Material.AMETHYST_SHARD)

    }

}

class AbilityMultiTonic : Ability<AbilityConceptMultiTonic>(), Listener {
    var nutritions = ArrayList<Int>()

    val nutritionsName = arrayOf("비타민 B", "비타민 C", "비타민 D", "칼슘", "마그네슘", "아연", "철분")

    val nutritionsColor = arrayOf(ChatColor.GREEN, ChatColor.GOLD, ChatColor.RED, ChatColor.WHITE, ChatColor.DARK_RED, ChatColor.LIGHT_PURPLE, ChatColor.DARK_GRAY)

    var nutritionsPercent = ArrayList<Double>()

    var nutritionsEffectExplain = arrayOf("육체 피로 개선 | 눈 피로 개선", "면역력 증진", "우울감 감소", "강력한 골격 구조 형성 | 식욕 조절", "근육 강화", "세포막 항산화 | 화상 회복 도움", "산소 전달")

    var eatingTime = 0

    var usingCount = 0

    var vitaminDEffect = 0

    override fun onEnable() {
        psychic.registerEvents(this)
        nutritions.add(concept.vitaminB)
        nutritions.add(concept.vitaminC)
        nutritions.add(concept.vitaminD)
        nutritions.add(concept.calcium)
        nutritions.add(concept.magnesium)
        nutritions.add(concept.zinc)
        nutritions.add(concept.iron)
        val sum = nutritions.sum()
        for (i in 0..6) {
            nutritionsPercent.add(round((nutritions[i].toDouble() / sum.toDouble()) * 10000) / 100)
        }
        psychic.runTaskTimer({
            if (eatingTime > 0) {
                eatingTime -= 1
            }
            if (esper.player.world.time in 0L..10L) {
                usingCount = 0
            }
        }, 0L, 1L)
        psychic.runTaskTimer({
            if (vitaminDEffect > 0) {
                psychic.mana += vitaminDEffect
            }
        }, 0L, 20L)
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val action = event.action

        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            event.item?.let { item ->
                if (item.type == concept.wand?.type) {
                        val player = esper.player
                        var text = "-- 영양 성분 --\n"
                        for (i in 0..6) text =
                            text + "${nutritionsColor[i]}${nutritionsName[i]}${ChatColor.WHITE}: ${nutritionsPercent[i]}%\n"
                        text = text + "${ChatColor.WHITE}-----------"
                        player.sendMessage(text)
                }
            }
        }
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            event.item?.let { item ->
                if (item.type == concept.wand?.type) {
                    val player = esper.player
                    val result = test()

                    if (result != TestResult.Success) {
                        result.message(this)?.let { player.sendActionBar(it) }
                        return
                    }

                    if (eatingTime <= 0) {
                        player.sendActionBar("식후에 복용해야한다.")
                        return
                    }

                    if (usingCount >= concept.abilityAmountPerDay && concept.abilityAmountPerDay > 0) {
                        player.sendActionBar("오늘의 복용 적정량을 넘겼다.")
                        return
                    }

                    player.world.spawnParticle(Particle.BLOCK_DUST, player.boundingBox.center.toLocation(player.world), 6, 0.2, 0.2, 0.2, 0.0, Material.AMETHYST_BLOCK.createBlockData(), true)
                    player.world.playSound(player.location, Sound.ENTITY_GENERIC_EAT, 1.0F, 0.1F)
                    cooldownTime = concept.cooldownTime
                    psychic.mana -= concept.cost
                    usingCount += 1
                    eatingTime = 0
                    var sum = nutritions.sum()
                    var currentSum = 0
                    var nutritionsRange = ArrayList<Int>()
                    for (i in 0..6) {
                        nutritionsRange.add(nutritions[i] + currentSum)
                        currentSum += nutritions[i]
                    }
                    val random = nextInt(1, sum)
                    var randomResult = 0
                    when (random) {
                        in 1..nutritionsRange[0] -> randomResult = 1
                        in nutritionsRange[0]+1.. nutritionsRange[1] -> randomResult = 2
                        in nutritionsRange[1]+1.. nutritionsRange[2] -> randomResult = 3
                        in nutritionsRange[2]+1.. nutritionsRange[3] -> randomResult = 4
                        in nutritionsRange[3]+1.. nutritionsRange[4] -> randomResult = 5
                        in nutritionsRange[4]+1.. nutritionsRange[5] -> randomResult = 6
                        in nutritionsRange[5]+1.. nutritionsRange[6] -> randomResult = 7
                    }
                    player.sendMessage("${nutritionsColor[randomResult-1]}${nutritionsName[randomResult-1]}${ChatColor.WHITE}의 효능이 느껴진다!\n" +
                            "${nutritionsColor[randomResult-1]}${nutritionsName[randomResult-1]}${ChatColor.WHITE}의 효과: ${nutritionsEffectExplain[randomResult-1]}")
                    if (randomResult == 1) {
                        var potion = player.getPotionEffect(PotionEffectType.FAST_DIGGING)
                        if (potion == null) {
                            player.addPotionEffect(PotionEffect(PotionEffectType.FAST_DIGGING, 1200, 1, false, false))
                        } else {
                            player.addPotionEffect(PotionEffect(PotionEffectType.FAST_DIGGING, potion.duration + 1200, potion.amplifier + 1, false, false))
                        }
                        potion = player.getPotionEffect(PotionEffectType.NIGHT_VISION)
                        if (potion == null) {
                            player.addPotionEffect(PotionEffect(PotionEffectType.NIGHT_VISION, 1200, 1, false, false))
                        } else {
                            player.addPotionEffect(PotionEffect(PotionEffectType.NIGHT_VISION, potion.duration + 1200, potion.amplifier + 1, false, false))
                        }
                    } else if (randomResult == 2) {
                        var potion = player.getPotionEffect(PotionEffectType.DAMAGE_RESISTANCE)
                        if (potion == null) {
                            player.addPotionEffect(PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 1200, 0, false, false))
                        } else {
                            player.addPotionEffect(PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, potion.duration + 1200, potion.amplifier + 1, false, false))
                        }
                    } else if (randomResult == 3) {
                        var potion = player.getPotionEffect(PotionEffectType.SPEED)
                        if (potion == null) {
                            player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 800, 1, false, false))
                        } else {
                            player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, potion.duration + 800, potion.amplifier + 1, false, false))
                        }
                        vitaminDEffect += 1
                        psychic.runTask({ vitaminDEffect -= 1}, 800L)
                    } else if (randomResult == 4) {
                        var potion = player.getPotionEffect(PotionEffectType.HEALTH_BOOST)
                        if (potion == null) {
                            player.addPotionEffect(PotionEffect(PotionEffectType.HEALTH_BOOST, 1600, 0, false, false))
                        } else {
                            player.addPotionEffect(PotionEffect(PotionEffectType.HEALTH_BOOST, potion.duration + 1600, potion.amplifier + 1, false, false))
                        }
                        potion = player.getPotionEffect(PotionEffectType.SATURATION)
                        if (potion == null) {
                            player.addPotionEffect(PotionEffect(PotionEffectType.SATURATION, 600, 0, false, false))
                        } else {
                            player.addPotionEffect(PotionEffect(PotionEffectType.SATURATION, potion.duration + 600, potion.amplifier + 1, false, false))
                        }
                    } else if (randomResult == 5) {
                        var potion = player.getPotionEffect(PotionEffectType.INCREASE_DAMAGE)
                        if (potion == null) {
                            player.addPotionEffect(PotionEffect(PotionEffectType.INCREASE_DAMAGE, 1200, 0, false, false))
                        } else {
                            player.addPotionEffect(PotionEffect(PotionEffectType.INCREASE_DAMAGE, potion.duration + 1200, potion.amplifier + 1, false, false))
                        }
                    } else if (randomResult == 6) {
                        var potion = player.getPotionEffect(PotionEffectType.REGENERATION)
                        if (potion == null) {
                            player.addPotionEffect(PotionEffect(PotionEffectType.REGENERATION, 800, 0, false, false))
                        } else {
                            player.addPotionEffect(PotionEffect(PotionEffectType.REGENERATION, potion.duration + 800, potion.amplifier + 1, false, false))
                        }
                        potion = player.getPotionEffect(PotionEffectType.FIRE_RESISTANCE)
                        if (potion == null) {
                            player.addPotionEffect(PotionEffect(PotionEffectType.FIRE_RESISTANCE, 1200, 1, false, false))
                        } else {
                            player.addPotionEffect(PotionEffect(PotionEffectType.FIRE_RESISTANCE, potion.duration + 1200, potion.amplifier + 1, false, false))
                        }
                    } else {
                        var potion = player.getPotionEffect(PotionEffectType.JUMP)
                        if (potion == null) {
                            player.addPotionEffect(PotionEffect(PotionEffectType.JUMP, 1200, 1, false, false))
                        } else {
                            player.addPotionEffect(PotionEffect(PotionEffectType.JUMP, potion.duration + 1200, potion.amplifier + 1, false, false))
                        }
                        potion = player.getPotionEffect(PotionEffectType.SPEED)
                        if (potion == null) {
                            player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 1200, 0, false, false))
                        } else {
                            player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, potion.duration + 1200, potion.amplifier + 1, false, false))
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    fun eating(event: PlayerItemConsumeEvent) {
        eatingTime = 400
    }
}





