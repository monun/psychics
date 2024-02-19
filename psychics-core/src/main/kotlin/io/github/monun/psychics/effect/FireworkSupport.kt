package io.github.monun.psychics.effect

import org.bukkit.FireworkEffect
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.plugin.Plugin
import org.bukkit.entity.Firework
import org.bukkit.inventory.meta.FireworkMeta
import org.bukkit.metadata.FixedMetadataValue

fun World.spawnFirework(
    x: Double, y: Double, z: Double, effect: FireworkEffect, plugin: Plugin,
    hasDamage: Boolean = false, power: Int = 0, ticksToDetonate: Int = 0
): Firework {
    val location = Location(this, x, y, z)
    val firework: Firework = location.world.spawn(location, Firework::class.java)
    val fireworkMeta: FireworkMeta = firework.fireworkMeta
    fireworkMeta.addEffect(effect)
    fireworkMeta.power = power
    firework.fireworkMeta = fireworkMeta
    firework.ticksToDetonate = ticksToDetonate
    if (!hasDamage) {
        firework.disableDamage(plugin)
    }
    return firework
}
fun World.spawnFirework(
    loc: Location,
    effect: FireworkEffect, plugin: Plugin,
    hasDamage: Boolean = false, power: Int = 0, ticksToDetonate: Int = 0
) =
    spawnFirework(loc.x, loc.y, loc.z, effect, plugin, hasDamage, power, ticksToDetonate)

val NO_DAMAGE_FIREWORK_FLAG = "NoDamageFirework"

fun Firework.disableDamage(plugin: Plugin) {
    setMetadata(NO_DAMAGE_FIREWORK_FLAG, FixedMetadataValue(plugin, true))
}


