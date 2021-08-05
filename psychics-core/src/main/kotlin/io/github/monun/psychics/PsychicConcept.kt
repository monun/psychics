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
import io.github.monun.psychics.tooltip.TooltipBuilder
import io.github.monun.psychics.tooltip.removeDefaultItemDecorations
import io.github.monun.psychics.tooltip.stats
import io.github.monun.psychics.tooltip.template
import io.github.monun.tap.config.Config
import io.github.monun.tap.config.ConfigSupport
import io.github.monun.tap.config.RangeDouble
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.boss.BarColor
import org.bukkit.configuration.ConfigurationSection

class PsychicConcept internal constructor() {
    companion object {
        internal const val DISPLAY_NAME = "display-name"
        internal const val HEALTH_BONUS = "health-bonus"
        internal const val HEALTH_REGEN = "health-regen"
        internal const val MANA = "mana"
        internal const val MANA_REGEN = "mana-regen"
    }

    lateinit var manager: PsychicManager
        private set

    lateinit var name: String
        private set

    /**
     * 표시 이름 (I18N)
     */
    @Config
    lateinit var displayName: String
        private set

    /**
     * 추가 체력
     */
    @Config
    @RangeDouble(min = 0.0, max = 32767.0 - 20.0)
    var healthBonus = 0.0
        private set

    /**
     * 추가 체력 재생
     */
    @Config
    @RangeDouble(min = 0.0, max = 32767.0)
    var healthRegenPerSecond = 0.0
        private set

    /**
     * 최대 마나
     */
    @Config
    @RangeDouble(min = 0.0, max = 32767.0)
    var mana = 0.0
        private set

    /**
     * 마나 재생
     */
    @Config
    @RangeDouble(min = 0.0, max = 32767.0)
    var manaRegenPerSecond = 0.0
        private set

    /**
     * 마나 색상
     */
    @Config
    var manaColor = BarColor.BLUE

    /**
     * 설명
     */
    @Config("description")
    private var descriptionRaw: List<String> = ArrayList(0)

    private var description: List<Component> = listOf(
        text("설명")
    )

    /**
     * 능력 목록
     */
    lateinit var abilityConcepts: List<AbilityConcept>
        private set

    internal fun initialize(name: String, config: ConfigurationSection): Boolean {
        this.name = name
        this.displayName = name

        description = description.removeDefaultItemDecorations()

        val gson = GsonComponentSerializer.gson()
        descriptionRaw = description.map { gson.serialize(it) }

        val ret = ConfigSupport.compute(this, config)

        val maxHealthBonus = Bukkit.spigot().spigotConfig.getDouble("attribute.maxHealth", 2048.0 - 20.0)

        if (healthBonus > maxHealthBonus) {
            healthBonus = maxHealthBonus
        }

        return ret
    }

    internal fun initializeModules(manager: PsychicManager, abilityConcepts: List<AbilityConcept>) {
        this.manager = manager
        this.abilityConcepts = ImmutableList.copyOf(abilityConcepts)

        for (abilityConcept in abilityConcepts) {
            abilityConcept.runCatching { onInitialize() }
        }
    }

    internal fun renderTooltip(): TooltipBuilder {
        return TooltipBuilder().apply {
            title(
                text().content(displayName).decoration(TextDecoration.ITALIC, false).decorate(TextDecoration.BOLD)
                    .color(NamedTextColor.GOLD).build()
            )

            stats(healthBonus) { NamedTextColor.RED to "추가체력" to null }
            stats(healthRegenPerSecond) { NamedTextColor.DARK_RED to "체력재생" to null }
            stats(mana) { NamedTextColor.AQUA to "마나" to null }
            stats(manaRegenPerSecond) { NamedTextColor.DARK_AQUA to "마나재생" to null }

            body(description)

            template(DISPLAY_NAME, displayName)
            template(HEALTH_BONUS, healthBonus)
            template(HEALTH_REGEN, healthRegenPerSecond)
            template(MANA, mana)
            template(MANA_REGEN, manaRegenPerSecond)
        }
    }

    internal fun createInstance(): Psychic {
        val manager = manager

        return Psychic(this).apply {
            this.initialize(manager.plugin, manager)
        }
    }
}