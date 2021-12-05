package io.github.dytroInc.psychics.ability.quickwitted

import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.ActiveAbility
import io.github.monun.psychics.attribute.EsperStatistic
import io.github.monun.psychics.tooltip.TooltipBuilder
import io.github.monun.tap.config.Name
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import kotlin.random.Random.Default.nextInt

// 수학 문제 맞추면 효과 II 주는 능력
@Name("quick-witted")
class AbilityConceptQuickWitted : AbilityConcept() {
    init {
        cooldownTime = 50000L
        durationTime = 30000L
        description = listOf(
            text("발동하면 랜덤 수학 문제가 나옵니다."),
            text("문제를 마인크래프트 채팅으로 답합니다."),
            text("종류에 따라 얻는 버프도 다릅니다.")
        )
        wand = ItemStack(Material.PAPER)
        displayName = "두뇌 회전"
    }

    override fun onRenderTooltip(tooltip: TooltipBuilder, stats: (EsperStatistic) -> Double) {
        tooltip.header(
            text().color(NamedTextColor.DARK_AQUA).content("덧셈 & 뺄셈 ").decoration(TextDecoration.ITALIC, false)
                .decorate(
                    TextDecoration.BOLD
                )
                .append(
                    text().color(NamedTextColor.AQUA).content("속도")
                ).build()
        )
        tooltip.header(
            text().color(NamedTextColor.DARK_AQUA).content("곱셈 & 나눗셈 ").decoration(TextDecoration.ITALIC, false)
                .decorate(
                    TextDecoration.BOLD
                )
                .append(
                    text().color(NamedTextColor.AQUA).content("포화")
                ).build()
        )
        tooltip.header(
            text().color(NamedTextColor.DARK_AQUA).content("1차 방정식 ").decoration(TextDecoration.ITALIC, false).decorate(
                TextDecoration.BOLD
            )
                .append(
                    text().color(NamedTextColor.AQUA).content("저항")
                ).build()
        )
    }
}

class AbilityQuickWitted : ActiveAbility<AbilityConceptQuickWitted>(), Listener {
    companion object {
        fun createProblem() = when (nextInt(8)) {
            in 0..1 -> {
                val a = nextInt(30, 300)
                val b = nextInt(30, 300)
                MathProblem(MathProblems.ADDITION_SUBTRACTION, "$a + $b = ?", a + b)
            }
            in 2..3 -> {
                val a = nextInt(30, 300)
                val b = nextInt(30, 300)
                MathProblem(MathProblems.ADDITION_SUBTRACTION, "$a - $b = ?", a - b)
            }
            in 4..5 -> {
                val a = nextInt(3, 20)
                val b = nextInt(-10, 10)
                MathProblem(MathProblems.MULTIPLICATION_DIVISION, "$a x $b = ?", a * b)
            }
            6 -> {
                val b = nextInt(1, 30)
                val a = b * nextInt(2, 8)
                MathProblem(MathProblems.MULTIPLICATION_DIVISION, "$a ÷ $b = ?", a / b)
            }
            else -> {
                val a = nextInt(3, 10)
                val b = nextInt(3, 10)
                val c = nextInt(-5, 5)
                val d = nextInt(2, 5)
                MathProblem(MathProblems.LINEAR_EQUATION, "x를 구하시오) $c(x + $b) + $d = ${c * (a + b) + d}", a)
            }
        }
    }

    override fun onEnable() {
        psychic.registerEvents(this)
    }

    var currentProblem: MathProblem? = null

    override fun onCast(event: PlayerEvent, action: WandAction, target: Any?) {
        val player = event.player
        if (currentProblem != null) return player.sendActionBar(text("문제를 먼저 풀어야합니다.", NamedTextColor.RED))
        cooldownTime = concept.cooldownTime
        psychic.consumeMana(concept.cost)
        createProblem().let {
            currentProblem = it
            player.sendMessage(
                text().color(NamedTextColor.GOLD).content("문제: ").decorate(TextDecoration.BOLD)
                    .append(
                        text().color(NamedTextColor.WHITE).content(it.question).decoration(TextDecoration.BOLD, false)
                    )
                    .build()
            )
        }
    }

    @EventHandler
    fun onAnswer(event: AsyncChatEvent) {
        println(event.player.name)
        val message = PlainTextComponentSerializer.plainText().serialize(event.message())
        println(message)
        message.toIntOrNull()?.let {
            currentProblem?.let { problem ->
                val player = event.player
                if (problem.answer == it) {
                    player.sendMessage(text("정답을 맞췄습니다!", NamedTextColor.GREEN))
                    psychic.runTask(object : BukkitRunnable() {
                        override fun run() {
                            player.addPotionEffect(
                                PotionEffect(
                                    problem.problem.effectType, (concept.durationTime / 50.0).toInt(), 1
                                ) // 효과 2만큼 주기
                            )
                        }
                    }, 0)
                } else {
                    player.sendMessage(text("틀렸습니다!", NamedTextColor.RED))
                }
                currentProblem = null
            }
        }
    }

    enum class MathProblems(val effectType: PotionEffectType) {
        ADDITION_SUBTRACTION(PotionEffectType.SPEED),
        MULTIPLICATION_DIVISION(PotionEffectType.SATURATION),
        LINEAR_EQUATION(PotionEffectType.DAMAGE_RESISTANCE)
    }

    data class MathProblem(val problem: MathProblems, val question: String, val answer: Int)
}