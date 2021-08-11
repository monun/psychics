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

package io.github.monun.psychics

import io.github.monun.psychics.attribute.EsperStatistic
import io.github.monun.psychics.damage.Damage
import io.github.monun.psychics.damage.psychicDamage
import io.github.monun.psychics.damage.psychicHeal
import io.github.monun.psychics.format.decimalFormat
import io.github.monun.psychics.util.TargetFilter
import io.github.monun.psychics.util.Times
import io.github.monun.tap.ref.getValue
import io.github.monun.tap.ref.weaky
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.Component.space
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Location
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.LivingEntity
import org.bukkit.event.player.PlayerEvent
import kotlin.math.max

abstract class Ability<T : AbilityConcept> {

    lateinit var concept: T
        private set

    var cooldownTime: Long = 0L
        get() {
            return max(0L, field - Times.current)
        }
        set(value) {
            checkState()

            val times = max(0L, value)
            field = Times.current + times
            updateCooldown((value / 50L).toInt())
        }

    internal fun updateCooldown(ticks: Int = (cooldownTime / 50L).toInt()) {
        val wand = concept.wand
        if (wand != null) esper.player.setCooldown(wand.type, ticks)
    }

    var durationTime: Long = 0L
        get() {
            return max(0L, field - Times.current)
        }
        set(value) {
            checkState()

            val times = max(0L, value)
            field = Times.current + times
        }

    lateinit var psychic: Psychic

    val esper
        get() = psychic.esper

    @Suppress("UNCHECKED_CAST")
    internal fun initConcept(concept: AbilityConcept) {
        this.concept = concept as T
    }

    internal fun initPsychic(psychic: Psychic) {
        val delegate by weaky(psychic)
        this.psychic = delegate
    }

    open fun test(): TestResult {
        val psychic = psychic

        if (!psychic.isEnabled) return TestResult.FailedDisabled
        if (esper.player.level < concept.levelRequirement) return TestResult.FailedLevel
        if (cooldownTime > 0L) return TestResult.FailedCooldown
        if (psychic.mana < concept.cost) return TestResult.FailedCost

        return TestResult.Success
    }

    internal fun save(config: ConfigurationSection) {
        config[COOLDOWN_TIME] = cooldownTime

        runCatching {
            onSave(config)
        }
    }

    internal fun load(config: ConfigurationSection) {
        cooldownTime = max(0L, config.getLong(COOLDOWN_TIME))

        runCatching {
            onLoad(config)
        }
    }

    companion object {
        private const val COOLDOWN_TIME = "cooldown-time"
    }

    /**
     * 초기화 후 호출됩니다.
     */
    open fun onInitialize() {}

    /**
     * 플레이어에게 적용 후 호출됩니다.
     */
    open fun onAttach() {}

    /**
     * 플레이어로부터 해제 후 호출됩니다.
     */
    open fun onDetach() {}

    /**
     * 정보를 디스크에 저장 할 때 호출됩니다.
     */
    open fun onSave(config: ConfigurationSection) {}

    /**
     * 정보를 디스크로부터 불러 올 때 호출됩니다.
     */
    open fun onLoad(config: ConfigurationSection) {}

    /**
     * 능력이 활성화 될 때 호출됩니다.
     */
    open fun onEnable() {}

    /**
     * 능력이 비활성화 될 때 호출됩니다.
     */
    open fun onDisable() {}

    fun checkState() {
        psychic.checkState()
    }

    fun checkEnabled() {
        psychic.checkEnabled()
    }

    /**
     * 능력을 사용 후 재사용 대기시간과 마나를 설정합니다.
     */
    fun exhaust() {
        checkEnabled()

        cooldownTime = concept.cooldownTime
        psychic.consumeMana(concept.cost)
    }

    /**
     * [LivingEntity]에게 피해를 입힙니다.
     *
     * 기본 인수로 [AbilityConcept]에 정의된 변수를 사용합니다.
     *
     * @exception IllegalArgumentException [AbilityConcept.damage] 인수가 정의되어 있지 않을 때 발생
     */
    fun LivingEntity.psychicDamage(
        damage: Damage = requireNotNull(concept.damage) { "Damage is not defined" },
        knockbackLocation: Location? = esper.player.location,
        knockback: Double = concept.knockback
    ) {
        val type = damage.type
        val amount = esper.getStatistic(damage.stats)

        psychicDamage(this@Ability, type, amount, esper.player, knockbackLocation, knockback)
    }

    /**
     * [LivingEntity]를 치유합니다.
     *
     * 기본 인수로 [AbilityConcept]에 정의된 변수를 사용합니다.
     *
     * @exception IllegalArgumentException [AbilityConcept.healing] 인수가 정의되어 있지 않을 때 발생
     */
    fun LivingEntity.psychicHeal(
        heal: EsperStatistic = requireNotNull(concept.healing) { "Healing is not defined" },
    ) {
        val amount = esper.getStatistic(heal)

        psychicHeal(this@Ability, amount, esper.player)
    }

    /**
     * [LivingEntity]를 치유합니다.
     */
    fun LivingEntity.psychicHeal(
        amount: Double
    ) {
        psychicHeal(this@Ability, amount, esper.player)
    }

}

abstract class ActiveAbility<T : AbilityConcept> : Ability<T>() {
    var targeter: (() -> Any?)? = null

    override fun test(): TestResult {
        if (psychic.channeling != null) return TestResult.FailedChannel

        return super.test()
    }

    open fun tryCast(
        event: PlayerEvent,
        action: WandAction,
        castingTime: Long = concept.castingTime,
        cost: Double = concept.cost,
        targeter: (() -> Any?)? = this.targeter
    ): TestResult {
        val result = test()

        if (result === TestResult.Success) {
            var target: Any? = null

            if (targeter != null) {
                target = targeter.invoke() ?: return TestResult.FailedTarget
            }

            return if (psychic.mana >= concept.cost) {
                cast(event, action, castingTime, target)
                TestResult.Success
            } else {
                TestResult.FailedCost
            }
        }

        return result
    }

    protected fun cast(
        event: PlayerEvent,
        action: WandAction,
        castingTime: Long,
        target: Any? = null
    ) {
        checkState()

        if (castingTime > 0) {
            psychic.startChannel(this, event, action, castingTime, target)
        } else {
            onCast(event, action, target)
        }
    }

    abstract fun onCast(event: PlayerEvent, action: WandAction, target: Any?)

    open fun onChannel(channel: Channel) {}

    open fun onInterrupt(channel: Channel) {}

    enum class WandAction {
        LEFT_CLICK,
        RIGHT_CLICK
    }
}

fun Ability<*>.targetFilter(): TargetFilter {
    return TargetFilter(esper.player)
}

sealed class TestResult {
    object Success : TestResult() {
        override fun message(ability: Ability<*>) = text("성공")
    }

    object FailedLevel : TestResult() {
        override fun message(ability: Ability<*>) =
            text().content("레벨이 부족합니다").decorate(TextDecoration.BOLD)
                .append(space())
                .append(text(ability.concept.levelRequirement))
                .append(text().content("레벨"))
                .build()
    }

    object FailedDisabled : TestResult() {
        override fun message(ability: Ability<*>) =
            text().content("능력을 사용 할 수 없습니다").decorate(TextDecoration.BOLD).build()
    }

    object FailedCooldown : TestResult() {
        override fun message(ability: Ability<*>) =
            text().content("아직 준비되지 않았습니다").decorate(TextDecoration.BOLD).build()
    }

    object FailedCost : TestResult() {
        override fun message(ability: Ability<*>) =
            text().content("마나가 부족합니다").decorate(TextDecoration.BOLD)
                .append(space())
                .append(text(ability.concept.cost.decimalFormat()))
                .build()
    }

    object FailedTarget : TestResult() {
        override fun message(ability: Ability<*>) =
            text().content("대상 혹은 위치가 지정되지 않았습니다").decorate(TextDecoration.BOLD)
                .build()
    }

    object FailedChannel : TestResult() {
        override fun message(ability: Ability<*>) =
            text().content("시전중인 스킬이 있습니다").decorate(TextDecoration.BOLD).build()
    }

    abstract fun message(ability: Ability<*>): Component
}