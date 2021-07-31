package io.github.monun.psychics.tooltip

import net.kyori.adventure.text.BuildableComponent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration

fun List<Component>.removeDefaultItemDecorations() = map { component ->
    if (component is BuildableComponent<*, *>) {
        component.toBuilder().colorIfAbsent(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE).build()
    } else
        component
}
