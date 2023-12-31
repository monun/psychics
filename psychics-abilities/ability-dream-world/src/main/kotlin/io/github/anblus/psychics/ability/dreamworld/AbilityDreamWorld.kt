package io.github.anblus.psychics.ability.dreamworld

import io.github.monun.psychics.*
import io.github.monun.psychics.attribute.EsperAttribute
import io.github.monun.psychics.attribute.EsperStatistic
import io.github.monun.psychics.damage.Damage
import io.github.monun.psychics.damage.DamageType
import io.github.monun.psychics.util.TargetFilter
import io.github.monun.psychics.util.hostileFilter
import io.github.monun.tap.config.Config
import io.github.monun.tap.config.Name
import io.github.monun.tap.effect.playFirework
import io.github.monun.tap.fake.FakeEntity
import io.github.monun.tap.fake.Movement
import io.github.monun.tap.fake.Trail
import io.github.monun.tap.math.normalizeAndLength
import net.kyori.adventure.text.Component.text
import org.bukkit.*
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Sheep
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.EulerAngle
import org.bukkit.util.Vector
import kotlin.random.Random.Default.nextDouble
import kotlin.random.Random.Default.nextInt

// 무지개를 열어요
@Name("dream-world")
class AbilityConceptDreamWorld : AbilityConcept() {

    @Config
    val rainbowSpeed = 0.8

    @Config
    val rainbowGravity = 0.01

    @Config
    val rainbowSpreadCycle = 2.0

    @Config
    val rainbowSpreadBoost = 3.0

    @Config
    val rainbowSpreadPercent = 0.4

    @Config
    val rainbowSpreadMaxTime = 7.0

    @Config
    val rainbowSheepPercent = 0.02

    init {
        displayName = "꿈나라"
        type = AbilityType.ACTIVE
        cooldownTime = 10000L
        durationTime = 40000L
        cost = 80.0
        range = 128.0
        damage = Damage.of(DamageType.MELEE, EsperStatistic.of(EsperAttribute.ATTACK_DAMAGE to 0.5))
        description = listOf(
            text("능력 사용시 무지개 투사체를 발사합니다."),
            text("무지개 투사체가 적중한 지점 주위로는 무지개"),
            text("블록들이 사방으로 일정 수준까지 퍼져 나갑니다."),
            text("시전자 자신과 아군이 무지개 위에 있을 경우 신속을,"),
            text("반대로 적들이 위에 있을 경우 구속과 피해를 입힙니다."),
            text("바뀐 무지개 블록은 지속 시간 후에 하얀 콘크리트 블록이 됩니다.")
        )
        wand = ItemStack(Material.WHITE_DYE)

    }
}

class AbilityDreamWorld : ActiveAbility<AbilityConceptDreamWorld>(), Listener {

    val rainbowBlockList = arrayOf(Material.RED_CONCRETE, Material.ORANGE_CONCRETE, Material.YELLOW_CONCRETE, Material.LIME_CONCRETE, Material.GREEN_CONCRETE,
        Material.CYAN_CONCRETE, Material.LIGHT_BLUE_CONCRETE, Material.BLUE_CONCRETE, Material.PURPLE_CONCRETE, Material.PINK_CONCRETE)

    val rainbowColorList = arrayOf(Color.RED, Color.ORANGE, Color.YELLOW, Color.LIME, Color.GREEN,
        Color.fromRGB(0, 255, 255), Color.fromRGB(173,216,230), Color.BLUE, Color.PURPLE, Color.fromRGB(255,192,203))

    val dyeColorList = arrayOf(DyeColor.RED, DyeColor.ORANGE, DyeColor.YELLOW, DyeColor.LIME, DyeColor.GREEN,
        DyeColor.CYAN, DyeColor.LIGHT_BLUE, DyeColor.BLUE, DyeColor.PURPLE, DyeColor.PINK)

    var rainbowBlockIndex = 0

    var rainbowProjectileList = arrayListOf<RainbowProjectile>()

    var rainbowVector = arrayListOf<Vector>()

    var rainbowLivingVector = arrayListOf<Vector>()

    var cycle = 0.0

    var cycleBoostCount = 0.0

    var cycleCount = 0.0

    var rainbowTime = 0.0

    val around = arrayOf(Vector(1.0, 0.0, 0.0), Vector(-1.0, 0.0, 0.0), Vector(0.0, 0.0, 1.0), Vector(0.0, 0.0, -1.0), Vector(0.0, 1.0, 0.0), Vector(0.0, -1.0, 0.0))

    override fun onEnable() {
        cycle = concept.rainbowSpreadCycle
        psychic.runTaskTimer({
            if (rainbowTime > 0) rainbowTime -= 0.05
             if (rainbowProjectileList.size >= 1) {
                 rainbowBlockIndex ++
                 if (rainbowBlockIndex >= rainbowBlockList.size) rainbowBlockIndex = 0
                 repeat(rainbowProjectileList.size) {
                     if (rainbowProjectileList[it].rainbow.dead) rainbowProjectileList.removeAt(it)
                 }
             }
            if (rainbowLivingVector.size > 0) {
                var isDead: Boolean
                if (cycleCount >= cycle) {
                    cycleCount = 0.0
                    val deleteList = arrayListOf<Vector>()
                    val world = esper.player.world
                    repeat(rainbowLivingVector.size) { i ->
                        isDead = true
                        if (rainbowTime > 0) {
                            repeat(6) { ii ->
                                val block = rainbowLivingVector[i].clone().add(around[ii]).toLocation(world).block
                                val vector = block.location.toVector()
                                if (vector !in rainbowVector && block.isSolid && block.type != Material.BEDROCK) {
                                    isDead = false
                                    if (nextDouble() <= concept.rainbowSpreadPercent) {
                                        block.type = rainbowBlockList[nextInt(0, rainbowBlockList.size)]
                                        rainbowVector.add(vector)
                                        rainbowLivingVector.add(vector)
                                        if (block.location.add(
                                                0.0,
                                                1.0,
                                                0.0
                                            ).block.type == Material.AIR && nextDouble() <= concept.rainbowSheepPercent
                                        ) world.spawnEntity(block.location.add(0.0, 1.0, 0.0), EntityType.SHEEP).apply {
                                            (this as Sheep).color = dyeColorList[nextInt(0, dyeColorList.size)]
                                            customName = "Rainbow"
                                        }
                                        psychic.runTask({
                                            if (block.location.toVector() in rainbowVector) rainbowVector.remove(block.location.toVector())
                                            if (block.location.toVector() in rainbowLivingVector) rainbowLivingVector.remove(block.location.toVector())
                                            if (block.type in rainbowBlockList) block.type = Material.WHITE_CONCRETE
                                        }, concept.durationTime / 50)
                                    }
                                }
                            }
                        }
                        if (isDead) deleteList.add(rainbowLivingVector[i])
                    }
                    for (d in deleteList) rainbowLivingVector.remove(d)
                } else cycleCount += 0.1
            } else if (rainbowTime > 0) rainbowTime = 0.0
            if (cycle < concept.rainbowSpreadCycle) {
                if (cycleBoostCount < 0.0) cycleBoostCount = 0.0
                cycleBoostCount += 0.05
                if (cycleBoostCount >= cycle) {
                    cycle += 0.1
                    cycleBoostCount = 0.0
                    if (cycle > concept.rainbowSpreadCycle) cycle = concept.rainbowSpreadCycle
                }
            }
        }, 0L, 1L)
        psychic.runTaskTimer({
            val player = esper.player
            for (entity in player.world.entities) {
                if (entity is LivingEntity) {
                    if (entity.location.add(0.0, -1.0, 0.0).block.location.toVector() in rainbowVector && entity.location.add(0.0, -1.0, 0.0).block.type in rainbowBlockList) {
                        if (player.hostileFilter().test(entity) && entity !is Sheep) {
                            entity.psychicDamage()
                            entity.addPotionEffect(PotionEffect(PotionEffectType.SLOW, 20, 1))
                        } else entity.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 20, 2))
                    }
                }
            }
        }, 1L, 12L)
    }

    override fun onCast(event: PlayerEvent, action: WandAction, target: Any?) {
        val player = esper.player

        if (rainbowTime > 0) return player.sendActionBar(text("현재 생성 중인 무지개 블록들이 있습니다."))
        if (!psychic.consumeMana(concept.cost)) return player.sendActionBar(TestResult.FailedCost.message(this))
        cooldownTime = concept.cooldownTime

        val location = player.eyeLocation
        val projectile = RainbowProjectile().apply {
            rainbow =
                this@AbilityDreamWorld.psychic.spawnFakeEntity(location, ArmorStand::class.java).apply {
                    updateMetadata<ArmorStand> {
                        isVisible = false
                        isMarker = true
                    }
                    updateEquipment {
                        helmet = ItemStack(Material.WHITE_CONCRETE)
                    }
                }
        }

        psychic.launchProjectile(location, projectile)
        rainbowProjectileList.add(projectile)
        projectile.velocity = location.direction.multiply(concept.rainbowSpeed)

        val loc = player.location
        loc.world.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_PLACE, 1.5F, 2.0F)
    }


    inner class RainbowProjectile : PsychicProjectile(12000, concept.range) {
        lateinit var rainbow: FakeEntity

        override fun onPreUpdate() {
            velocity = velocity.apply { y -= concept.rainbowGravity }
            rainbow.updateEquipment {
                helmet = ItemStack(rainbowBlockList[rainbowBlockIndex])
            }
        }

        override fun onMove(movement: Movement) {
            rainbow.moveTo(movement.to.clone().apply {
                y -= 1.62; yaw -= 90f
            })
            rainbow.updateMetadata<ArmorStand> {
                headPose = EulerAngle(ticks * -0.2, 0.0, 0.0)
            }
        }

        override fun onTrail(trail: Trail) {
            trail.velocity?.let { v ->
                val length = v.normalizeAndLength()

                if (length > 0.0) {
                    val start = trail.from
                    val world = start.world

                    world.rayTrace(
                        start, v, length, FluidCollisionMode.NEVER, true, 0.5,
                        TargetFilter(esper.player)
                    )?.hitBlock?.let { block ->
                        remove()
                        world.playFirework(block.location, FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE).withColor(rainbowColorList[rainbowBlockIndex]).build())
                        if (block.location.toVector() !in rainbowVector) {
                            rainbowVector.add(block.location.toVector())
                            rainbowLivingVector.add(block.location.toVector())
                            block.type = rainbowBlockList[nextInt(0, rainbowBlockList.size)]
                            cycle -= concept.rainbowSpreadBoost
                            rainbowTime = concept.rainbowSpreadMaxTime
                            psychic.runTask({
                                if (block.location.toVector() in rainbowVector) rainbowVector.remove(block.location.toVector())
                                if (block.location.toVector() in rainbowLivingVector) rainbowLivingVector.remove(block.location.toVector())
                                if (block.type in rainbowBlockList) block.type = Material.WHITE_CONCRETE
                            }, concept.durationTime / 50)
                        }
                    }
                }
            }
        }

        override fun onRemove() {
            rainbow.remove()
        }
    }
}






