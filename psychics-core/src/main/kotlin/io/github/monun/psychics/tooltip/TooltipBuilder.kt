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

package io.github.monun.psychics.tooltip

import io.github.monun.psychics.attribute.EsperStatistic
import io.github.monun.psychics.damage.Damage
import io.github.monun.psychics.format.decimalFormat
import net.kyori.adventure.text.BuildableComponent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.Component.*
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.inventory.ItemStack
import java.util.*

class TooltipBuilder {
    private var title: Component? = null
    private val headers = ArrayList<Component>()
    private val body = ArrayList<Component>()
    private val footers = ArrayList<Component>()

    private val templates = TreeMap<String, TextComponent.Builder.() -> Unit>(naturalOrder())

    fun title(component: Component) {
        title = component
    }

    fun header(component: Component) = headers.add(component)

    fun body(component: Component) = body.add(component)

    fun body(components: Collection<Component>) = body.addAll(components)

    fun footer(component: Component) = footers.add(component)

    fun template(name: String, applier: TextComponent.Builder.() -> Unit) {
        require(name.matches(Regex("[\\w\\-_]+"))) { "Template name cannot contain special characters" }
        require(name !in this.templates) { "Template name already in use" }

        templates[name] = applier
    }

    fun build(includeTitle: Boolean = true): List<Component> {
        val headers = headers
        val body = body
        val footers = footers

        val components = ArrayList<Component>()

        if (includeTitle) {
            title?.let { components += it }
        }

        components.addAllIfNotEmpty(headers) { add(empty()) }
        components.addAllIfNotEmpty(body) { add(empty()) }
        components.addAllIfNotEmpty(footers) { add(empty()) }

        components.replaceAll { component ->
            if (component is BuildableComponent<*, *>) {
                component.toBuilder().applyDeep {
                    if (it is TextComponent.Builder) {
                        val content = it.content()

                        if (content.matches(Regex("^<[\\w\\-_]+>$"))) {
                            val name = content.substring(1, content.count() - 1)

                            templates[name]?.let { template ->
                                template(it)
                            }
                        }
                    }
                }.build()
            } else {
                component
            }
        }

        return components
    }

    fun applyTo(item: ItemStack): ItemStack {
        item.itemMeta = item.itemMeta.apply {
            title?.let { displayName(it) }
            lore(build(false))
        }

        return item
    }
}

private fun <T> MutableList<T>.addAllIfNotEmpty(c: Collection<T>, onAdd: MutableList<T>.() -> Unit) {
    if (c.isNotEmpty()) {
        onAdd()
        addAll(c)
    }
}

fun TooltipBuilder.stats(color: TextColor, name: String, value: String, unit: String? = null) {
    header(
        text().decoration(TextDecoration.ITALIC, false)
            .append(text().color(color).decorate(TextDecoration.BOLD).content(name))
            .append(space())
            .append(text().color(NamedTextColor.WHITE).content(value)).also {
                if (unit != null) {
                    it.append(space())
                        .append(text().content(unit).color(NamedTextColor.WHITE))
                }
            }.build()
    )
}

fun TooltipBuilder.stats(value: Number, deco: () -> Pair<Pair<TextColor, String>, String?>) {
    if (value.toDouble() == 0.0) return

    val pair = deco()
    val first = pair.first
    val color = first.first
    val name = first.second
    val unit = pair.second

    stats(color, name, value.decimalFormat(), unit)
}

fun TooltipBuilder.stats(stats: EsperStatistic?, deco: () -> Pair<Pair<TextColor, String>, String>) {
    if (stats == null) return

    val pair = deco()
    val first = pair.first
    val color = first.first
    val name = first.second
    val template = pair.second

    header(
        text().decoration(TextDecoration.ITALIC, false)
            .append(text().content(name).color(color).decorate(TextDecoration.BOLD))
            .append(space())
            .append(text("<$template>"))
            .append(stats.toComponent())
            .build()
    )
}

fun TooltipBuilder.stats(prefix: Component?, damage: Damage?, deco: () -> Pair<TextColor, String>) {
    if (damage == null) return

    val pair = deco()
    val color = pair.first
    val template = pair.second

    header(
        text().decoration(TextDecoration.ITALIC, false).also {
            if (prefix != null) {
                it.append(prefix).append(space())
            }
        }.append(text().content(damage.type.i18Name).color(color).decorate(TextDecoration.BOLD))
            .append(space())
            .append(text().content("<$template>").color(NamedTextColor.WHITE))
            .append(damage.stats.toComponent())
            .build()
    )
}
fun TooltipBuilder.stats(damage: Damage?, deco: () -> Pair<TextColor, String>) = stats(null, damage, deco)

fun TooltipBuilder.template(name: String, value: String) {
    template(name) {
        content(value)
    }
}

fun TooltipBuilder.template(name: String, value: Number) {
    template(name) {
        content(value.decimalFormat())
    }
}