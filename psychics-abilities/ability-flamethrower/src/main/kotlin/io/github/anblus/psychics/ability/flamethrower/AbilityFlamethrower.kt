package io.github.anblus.psychics.ability.flamethrower

import io.github.monun.psychics.*
import io.github.monun.psychics.attribute.EsperAttribute
import io.github.monun.psychics.attribute.EsperStatistic
import io.github.monun.psychics.damage.Damage
import io.github.monun.psychics.damage.DamageType
import io.github.monun.psychics.util.TargetFilter
import io.github.monun.tap.config.Config
import io.github.monun.tap.config.Name
import io.github.monun.tap.fake.FakeEntity
import io.github.monun.tap.fake.Movement
import io.github.monun.tap.fake.Trail
import io.github.monun.tap.math.normalizeAndLength
import io.github.monun.tap.task.TickerTask
import net.kyori.adventure.text.Component.text
import org.bukkit.FluidCollisionMode
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector
import java.util.*
import kotlin.math.max
import kotlin.random.Random.Default.nextDouble
import kotlin.random.Random.Default.nextInt

// 화염 방사!
@Name("flamethrower")
class AbilityConceptFlamethrower : AbilityConcept() {

    @Config
    val blameSpeed = 0.8

    @Config
    val blameWiggle = 0.6

    @Config
    val blockFiredPercent = 0.03

    init {
        displayName = "화염 방사"
        type = AbilityType.ACTIVE
        cost = 10.0
        range = 12.0
        cooldownTime = 1000L
        damage = Damage.of(DamageType.FIRE, EsperStatistic.of(EsperAttribute.ATTACK_DAMAGE to 0.25))
        description = listOf(
            text("바라보는 방향으로 상대를 불태우는 투사체를 방사합니다."),
            text("매 초 마나를 소비하고 사용 중에 좌클릭을 누르거나"),
            text("혹은 손에 들고 있지 않을 시 사용을 중지합니다."),
            text("사용 중엔 구속이 걸립니다.")
        )
        wand = ItemStack(Material.REPEATER)

    }

}

class AbilityFlamethrower : Ability<AbilityConceptFlamethrower>(), Listener {
    var isUsing = false

    var manaTask: TickerTask? = null

    var blamedEntities = ArrayList<UUID>()

    override fun onEnable() {
        psychic.registerEvents(this)
        psychic.runTaskTimer(this::onHalfTicks, 0L, 2L)
        psychic.runTaskTimer({
             if (isUsing) {
                 esper.player.addPotionEffect(
                     PotionEffect(PotionEffectType.SLOW, 5, 1, false, false, false)
                 )
                 if (psychic.mana < concept.cost) {
                     psychic.runTask({
                         if (isUsing) {
                             isUsing = false
                             cooldownTime = concept.cooldownTime
                             manaTask?.cancel()
                         }
                     }, 20L)
                 } else if (esper.player.inventory.itemInMainHand.type != concept.wand?.type && esper.player.inventory.itemInOffHand.type != concept.wand?.type) {
                     isUsing = false
                     cooldownTime = concept.cooldownTime
                     manaTask?.cancel()
                 }
             }
        }, 0L, 1L)
        psychic.runTaskTimer({
            if (blamedEntities.size != 0) blamedEntities.clear()
        }, 0L, 5L)
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val action = event.action

        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            event.item?.let { item ->
                if (item.type == concept.wand?.type) {
                    val player = esper.player
                    val result = test()

                    if (result != TestResult.Success) {
                        result.message(this)?.let { player.sendActionBar(it) }
                        return
                    }
                    if (isUsing) {
                        isUsing = false
                        cooldownTime = concept.cooldownTime
                        manaTask?.cancel()
                    } else {
                        isUsing = true
                        manaTask = psychic.runTaskTimer({
                            psychic.mana -= concept.cost
                        }, 0L, 20L)
                    }
                }
            }
        }
    }
    fun onHalfTicks() {
        if (isUsing) {
            val player = esper.player
            var location = player.location.apply {
                y += 1.0
            }
            location = location.add(location.direction.multiply(3))
            val blameList = mutableListOf<BlameProjectile>()
            val shotAmount = 12
            for (i in 0..shotAmount) {
                blameList.add(BlameProjectile().apply {
                    blame =
                        this@AbilityFlamethrower.psychic.spawnFakeEntity(location, ArmorStand::class.java).apply {
                            updateMetadata<ArmorStand> {
                                isVisible = false
                                isMarker = true
                            }
                            updateEquipment {
                                helmet = ItemStack(Material.BLAZE_POWDER)
                            }
                        }
                })
            }
            val wiggle = concept.blameWiggle
            for (i in 0..shotAmount) {
                blameList[i].velocity = location.direction.apply {

                    if (wiggle > 0.0) {
                        x += nextDouble(wiggle) - wiggle / 2.0
                        y += nextDouble(wiggle) - wiggle / 2.0
                        z += nextDouble(wiggle) - wiggle / 2.0
                    }
                }.multiply(concept.blameSpeed)
                psychic.launchProjectile(location, blameList[i])
            }

            val loc = player.location
            loc.world.playSound(loc, Sound.ITEM_FIRECHARGE_USE, 3.0F, 0.3F)
        }
    }

    inner class BlameProjectile : PsychicProjectile(1200, concept.range) {
        lateinit var blame: FakeEntity

        override fun onMove(movement: Movement) {
            blame.moveTo(movement.to.clone().apply {
                y -= 1.62
                yaw -= nextInt(1,180).toFloat()
                pitch -= nextInt(1,180).toFloat()
            })
        }

        override fun onTrail(trail: Trail) {
            trail.velocity?.let { velocity ->
                val from = trail.from
                val length = velocity.normalizeAndLength()
                val world = from.world

                from.world.rayTrace(
                    from,
                    velocity,
                    length,
                    FluidCollisionMode.ALWAYS,
                    true,
                    0.7,
                    TargetFilter(esper.player)
                )?.let { rayTraceResult ->
                    if (rayTraceResult.hitBlock != null) {
                        remove()
                        val x = arrayOf(1, -1, 0, 0, 0, 0)
                        val y = arrayOf(0, 0, 1, -1, 0, 0)
                        val z = arrayOf(0, 0, 0, 0, 1, -1)
                        for (i in 0..5) {
                            rayTraceResult.hitPosition.clone().add(Vector(x[i], y[i], z[i]))
                                .toLocation(world).block?.let { b ->
                                    if (b.type == Material.AIR) {
                                        if (nextDouble() <= concept.blockFiredPercent) {
                                            b.type = Material.FIRE
                                        }
                                    }
                                }
                        }
                    }
                    rayTraceResult.hitEntity?.let { entity ->
                        if (entity is LivingEntity) {
                            if (entity.uniqueId !in blamedEntities) {
                                entity.psychicDamage()
                                entity.fireTicks = max(entity.fireTicks, 50)
                                blamedEntities.add(entity.uniqueId)
                            }
                        }
                    }
                }
            }
        }

        override fun onRemove() {
            blame.remove()
        }
    }
}





