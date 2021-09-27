package io.github.monun.psychics.scalasupport

import io.github.monun.tap.effect.playFirework
import org.bukkit.FireworkEffect
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.util.Vector

fun playFirework(player: Player, x: Double, y: Double, z: Double, effect: FireworkEffect) = player.playFirework(x, y, z, effect)
fun playFirework(world: World, x: Double, y: Double, z: Double, effect: FireworkEffect, distance: Double = 128.0) = world.playFirework(x, y, z, effect, distance)
fun playFirework(world: World, pos: Vector, effect: FireworkEffect, distance: Double = 128.0) = world.playFirework(pos, effect, distance)
fun playFirework(world: World, loc: Location, effect: FireworkEffect, distance: Double = 128.0) = world.playFirework(loc, effect, distance)