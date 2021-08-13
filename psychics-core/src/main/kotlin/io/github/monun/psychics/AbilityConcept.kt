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

import com.google.common.collect.ImmutableList
import io.github.monun.psychics.attribute.EsperStatistic
import io.github.monun.psychics.damage.Damage
import io.github.monun.psychics.tooltip.TooltipBuilder
import io.github.monun.psychics.tooltip.removeDefaultItemDecorations
import io.github.monun.psychics.tooltip.stats
import io.github.monun.psychics.tooltip.template
import io.github.monun.tap.config.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.Component.space
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import org.bukkit.boss.BarColor
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.inventory.ItemStack
import java.util.logging.Logger

@Name("common")
open class AbilityConcept {
    lateinit var name: String
        private set

    lateinit var container: AbilityContainer
        private set

    lateinit var psychicConcept: PsychicConcept
        private set

    lateinit var logger: Logger
        private set

    /**
     * 표시 이름 (I18N)
     */
    @Config(required = false)
    lateinit var displayName: String
        protected set

    /**
     * 능력의 타입
     * * PASSIVE - 자동으로 적용되는 능력
     * * ACTIVE - 직접 사용하는 능력
     * * TOGGLE - 활성/비활성 가능한 능력
     *
     * Ability의 기본값은 PASSIVE이며
     * ActiveAbility의 기본값은 ACTIVE입니다.
     */
    @Config
    var type = AbilityType.PASSIVE
        protected set

    /**
     * 필요 레벨
     */
    @Config(required = false)
    @RangeInt(min = 0)
    var levelRequirement = 0

    /**
     * 재사용 대기시간
     */
    @Config(required = false)
    @RangeInt(min = 0)
    var cooldownTime = 0L
        protected set

    /**
     * 마나 소모
     */
    @Config(required = false)
    @RangeDouble(min = 0.0)
    var cost = 0.0
        protected set

    /**
     * 시전 시간
     */
    @Config(required = false)
    @RangeInt(min = 0)
    var castingTime = 0L
        protected set

    /**
     * 시전 시간 -> 집중 시간
     * 스킬을 시전 시 외부에서 중단 가능
     */
    @Config(required = false)
    var interruptible = false
        protected set

    /**
     * 시전 상태 바 색상
     */
    @Config(required = false)
    var castingBarColor: BarColor? = null

    /**
     * 지속 시간
     */
    @Config(required = false)
    var durationTime = 0L
        protected set

    /**
     * 사거리
     */
    @Config(required = false)
    @RangeDouble(min = 0.0)
    var range = 0.0
        protected set

    /**
     * 피해량
     */
    @Config(required = false)
    var damage: Damage? = null
        protected set

    /**
     * 넉백
     */
    @Config(required = false)
    var knockback = 0.0

    /**
     * 치유량
     */
    @Config(required = false)
    var healing: EsperStatistic? = null
        protected set

    @Config("wand", required = false)
    private var _wand: ItemStack? = null

    internal val internalWand
        get() = _wand

    /**
     * 능력과 상호작용하는 [ItemStack]
     */
    var wand
        get() = _wand?.clone()
        protected set(value) {
            _wand = value?.clone()
        }

    @Config("supply-items")
    private var _supplyItems: List<ItemStack> = ImmutableList.of()

    /**
     * 기본 공급 아이템
     */
    var supplyItems: List<ItemStack>
        get() = _supplyItems.map { it.clone() }
        protected set(value) {
            _supplyItems = ImmutableList.copyOf(value.map { it.clone() })
        }

    @Config("description")
    private var descriptionRaw: List<String> = ArrayList(0)

    var description: List<Component> = emptyList()

    internal fun initialize(
        name: String,
        container: AbilityContainer,
        psychicConcept: PsychicConcept,
        config: ConfigurationSection
    ): Boolean {
        this.name = name
        this.container = container
        this.psychicConcept = psychicConcept
        if (!this::displayName.isInitialized)
            this.displayName = container.description.name

        if (ActiveAbility::class.java.isAssignableFrom(container.abilityClass)) {
            type = AbilityType.ACTIVE
        }

        description = description.removeDefaultItemDecorations()

        val gson = GsonComponentSerializer.gson()
        descriptionRaw = description.map { gson.serialize(it) }

        val ret = ConfigSupport.compute(this, config, true)

        this._supplyItems = ImmutableList.copyOf(_supplyItems)
        this.description = descriptionRaw.map { gson.deserialize(it) }

        return ret
    }

    internal fun renderTooltip(supplyStats: (EsperStatistic) -> Double = { 0.0 }): TooltipBuilder {
        val tooltip = TooltipBuilder().apply {
            title(
                text().decoration(TextDecoration.ITALIC, false).decorate(TextDecoration.BOLD)
                    .append(text().content(displayName).color(NamedTextColor.GOLD))
                    .append(space())
                    .append(text().content(type.name).color(type.color))
                    .build()
            )

            stats(levelRequirement) { NamedTextColor.GREEN to "필요 레벨" to null }
            stats(cooldownTime / 1000.0) { NamedTextColor.AQUA to "재사용 대기시간" to "초" }
            stats(cost) { NamedTextColor.DARK_AQUA to "마나 소모" to null }
            stats(castingTime / 1000.0) { NamedTextColor.BLUE to "${if (interruptible) "집중" else "시전"} 시간" to "초" }
            stats(durationTime / 1000.0) { NamedTextColor.DARK_GREEN to "지속시간" to "초" }
            stats(range) { NamedTextColor.LIGHT_PURPLE to "사거리" to "블록" }
            stats(healing) { NamedTextColor.GREEN to "치유량" to "healing" }
            stats(damage) { NamedTextColor.DARK_PURPLE to "damage" }

            body(description)

            if (_supplyItems.isNotEmpty()) {
                footer(text().content("클릭하여 아이템을 지급받으세요").color(NamedTextColor.DARK_GREEN).build())
            }

            template("display-name", displayName)
            template("level-requirement", levelRequirement)
            template("cooldown-time", cooldownTime / 1000.0)
            template("cost", cost)
            template("casting-time", castingTime / 1000.0)
            template("range", range)
            template("duration", durationTime / 1000.0)
            template("damage", damage?.let { supplyStats(it.stats) } ?: 0.0)
            template("healing", healing?.let { supplyStats(it) } ?: 0.0)
        }

        runCatching { onRenderTooltip(tooltip, supplyStats) }.onFailure { it.printStackTrace() }
        container.description.author?.let { author ->
            tooltip.footer(text().color(NamedTextColor.GRAY).content("by. ").append(text().content(author)).build())
        }

        return tooltip
    }

    internal fun createAbilityInstance(): Ability<*> {
        return container.abilityClass.getConstructor().newInstance().apply {
            initConcept(this@AbilityConcept)
        }
    }

    /**
     * 필드 변수 적용 후 호출
     */
    open fun onInitialize() {}

    /**
     * 툴팁 요청 시 호출
     */
    open fun onRenderTooltip(tooltip: TooltipBuilder, stats: (EsperStatistic) -> Double) {}
}