package io.github.anblus.psychics.ability.architect

import io.github.monun.psychics.Ability
import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.AbilityType
import io.github.monun.psychics.TestResult
import io.github.monun.psychics.attribute.EsperAttribute
import io.github.monun.psychics.attribute.EsperStatistic
import io.github.monun.psychics.tooltip.TooltipBuilder
import io.github.monun.psychics.tooltip.stats
import io.github.monun.tap.config.Config
import io.github.monun.tap.config.Name
import io.github.monun.tap.fake.FakeEntity
import io.github.monun.tap.math.toRadians
import io.github.monun.tap.task.TickerTask
import net.kyori.adventure.text.Component.space
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.*
import org.bukkit.block.Block
import org.bukkit.entity.ArmorStand
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.util.EulerAngle
import org.bukkit.util.Vector
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

// 건축
@Name("architect")
class AbilityConceptArchitect : AbilityConcept() {

    @Config
    val maxCost = 50.0

    @Config
    val maxChargeTick = 100

    @Config
    val cubeRange = 16.0

    @Config
    val buildingMaterial = Material.OAK_PLANKS

    @Config
    val narrowLongBridgeMaxLength = 35.0

    @Config
    val wideShortBridgeMaxLength = 15.0

    @Config
    val cubeMaxRadius = 7.0

    @Config
    val buildingWaitBlockInterval = 10.0

    @Config
    val buildingIntervalTick = 2

    @Config
    val maxBuildingEfficiency = 3.0

    init {
        displayName = "건축"
        type = AbilityType.ACTIVE
        cooldownTime = (maxChargeTick * 50.0 + 5000).toLong()
        description = listOf(
            text("${ChatColor.GOLD}좌클릭 | 건설"),
            text("  클릭 시 설계된 구조대로 건설 준비를 시작합니다."),
            text("  준비 시간이 길어질수록 구조물의 크기가 더욱 증가합니다."),
            text("  중간에 재클릭 시 크기에 따라 마나를 소모하고 건설을 시작합니다."),
            text("${ChatColor.GOLD}우클릭 | 설계"),
            text("  다른 구조를 선택합니다."),
            text("   - ${ChatColor.GREEN}좁고 긴 다리"),
            text("   - ${ChatColor.RED}넓고 짧은 다리"),
            text("   - ${ChatColor.YELLOW}정육면체")
        )
        wand = ItemStack(Material.BRICK)
    }

    override fun onRenderTooltip(tooltip: TooltipBuilder, stats: (EsperStatistic) -> Double) {
        tooltip.stats(maxCost) { NamedTextColor.DARK_AQUA to "최대 마나 소모" to null }
        tooltip.stats(maxChargeTick / 20.0) { NamedTextColor.BLUE to "최대 준비 시간" to "초" }
        tooltip.stats(stats(EsperStatistic.of(EsperAttribute.ATTACK_DAMAGE to 1.0)).coerceAtMost(maxBuildingEfficiency)) { NamedTextColor.LIGHT_PURPLE to "최대 크기 배수" to "배" }
    }
}

class AbilityArchitect : Ability<AbilityConceptArchitect>(), Listener {
    companion object {
        private val structureName = arrayOf("${ChatColor.DARK_GREEN}좁고 긴 다리", "${ChatColor.DARK_BLUE}넓고 짧은 다리", "${ChatColor.DARK_RED}정육면체")
    }

    private var isCharging: Boolean = false

    private var chargingTotalTick: Int = 0

    private var chosenStructureIndex: Int = 0

    private var cubeRayLocation: Location? = null

    private var bridgeLocation: Location? = null
    private var bridgeDirection: Vector? = null

    private var level: Double = 0.0

    private var timer: TickerTask? = null

    private var builtBlocks = mutableListOf<Block>()

    private var isBuilding: Boolean = false

    private var worker: FakeEntity? = null

    private var armRising: Boolean = false

    override fun onEnable() {
        psychic.registerEvents(this)
        psychic.runTaskTimer(this::onTick, 0L, 1L)
    }

    override fun onDisable() {

    }

    private fun onTick() {
        val player = esper.player

        if (isCharging) {
            chargingTotalTick ++

            val percent = chargingTotalTick.toDouble() / concept.maxChargeTick
            val cost = percent * concept.maxCost
            val multiple = esper.getStatistic(EsperStatistic.of(EsperAttribute.ATTACK_DAMAGE to 1.0)).coerceAtMost(concept.maxBuildingEfficiency)

            worker?.let { worker ->
                worker.updateMetadata<ArmorStand> {
                    if (!armRising && rightArmPose.x >= -PI / 6) {
                        armRising = true
                        player.world.playSound(worker.location, Sound.BLOCK_SMITHING_TABLE_USE, 2.0f, 0.1f)
                    } else if (rightArmPose.x < -PI / 2) {
                        armRising = false
                    }
                    rightArmPose = if (armRising) EulerAngle(rightArmPose.x - 0.08, 0.0, 0.0) else EulerAngle(rightArmPose.x + 0.08, 0.0, 0.0)
                }
            }

            player.sendActionBar(text("${(percent * 100).toInt()}% 준비 | 마나 소모 ${cost.toInt()}"))

            level = when (chosenStructureIndex) {
                0 -> concept.narrowLongBridgeMaxLength / concept.maxChargeTick * chargingTotalTick * multiple
                1 -> concept.wideShortBridgeMaxLength / concept.maxChargeTick * chargingTotalTick * multiple
                2 -> concept.cubeMaxRadius / concept.maxChargeTick * chargingTotalTick * multiple
                else -> 0.0
            }
            if (cost >= psychic.mana || chargingTotalTick >= concept.maxChargeTick) startBuild()
        }
    }

    private fun startBuild() {
        val player = esper.player
        val maxLevel = ceil(level).toInt()
        val maxFigureForDelay = when (chosenStructureIndex) {
            0 -> concept.narrowLongBridgeMaxLength
            1 -> concept.wideShortBridgeMaxLength
            2 -> concept.cubeMaxRadius
            else -> concept.buildingWaitBlockInterval
        }

        player.world.playSound(worker!!.location, Sound.UI_STONECUTTER_TAKE_RESULT, 2.0f, 0.1f)
        worker?.remove()

        psychic.mana -= chargingTotalTick.toDouble() / concept.maxChargeTick * concept.maxCost

        isCharging = false
        isBuilding = true
        chargingTotalTick = 0
        worker = null

        var level = 0
        var tick = 0.0
        timer = psychic.runTaskTimer({
            while (tick < 1.0) {
                level ++
                build(level)
                if (level >= maxLevel) {
                    endBuild()
                }
                tick += concept.buildingWaitBlockInterval / maxFigureForDelay
            }
            tick = 0.0
        }, 0L, concept.buildingIntervalTick.toLong())
    }

    private fun build(level: Int) {
        val blocks = mutableListOf<Block>()
        when (chosenStructureIndex) {
            0 -> {
                val location = bridgeLocation!!.clone().add(bridgeDirection!!.clone().multiply(level))
                location.direction = bridgeDirection!!
                val block = location.block
                val yaw = location.yaw.toDouble()
                val cos = cos(yaw.toRadians())
                val sin = sin(yaw.toRadians())

                blocks.add(block)
                for (i in -5..5) {
                    val multiple = i.toDouble() / 10
                    val addBlock = location.clone().apply {
                        x += cos * multiple
                        z += sin * multiple
                    }.block
                    if (addBlock !in blocks) blocks.add(addBlock)
                }
            }
            1 -> {
                val location = bridgeLocation!!.clone().add(bridgeDirection!!.clone().multiply(level))
                location.direction = bridgeDirection!!
                val block = location.block
                val yaw = location.yaw.toDouble()
                val cos = cos(yaw.toRadians())
                val sin = sin(yaw.toRadians())

                blocks.add(location.clone().apply {
                    x += cos * 2
                    y += 1
                    z += sin * 2
                }.block)
                blocks.add(location.clone().apply {
                    x -= cos * 2
                    y += 1
                    z -= sin * 2
                }.block)
                blocks.add(block)
                for (i in -20..20) {
                    val multiple = i.toDouble() / 10
                    val addBlock = location.clone().apply {
                        x += cos * multiple
                        z += sin * multiple
                    }.block
                    if (addBlock !in blocks) blocks.add(addBlock)
                }
            }
            2 -> {
                val radius = level / 2
                for (x in -radius..radius) {
                    for (y in -radius..radius) {
                        for (z in -radius..radius) {
                            blocks.add(cubeRayLocation!!.clone().add(Vector(x, y, z)).block)
                        }
                    }
                }
            }
        }

        blocks.forEach { block ->
            if (!block.isSolid) {
                if (block !in builtBlocks) {
                    if (block.location.getNearbyEntities(0.5, 1.0, 0.5).isEmpty()) {
                        block.type = concept.buildingMaterial
                        builtBlocks.add(block)
                    }
                }
            }
        }
    }

    private fun endBuild() {
        cooldownTime = 0L

        timer!!.cancel()
        level = 0.0
        isBuilding = false
        timer = null
        bridgeDirection = null
        bridgeLocation = null
        cubeRayLocation = null
        builtBlocks.clear()
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = esper.player
        val action = event.action

        if ((event.item?.type ?: return) == concept.wand!!.type) {
            if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
                if (isCharging || isBuilding) {
                    return
                }
                chosenStructureIndex += 1
                if (chosenStructureIndex == structureName.size) chosenStructureIndex = 0
                player.sendActionBar(text("${structureName[chosenStructureIndex]}${ChatColor.RESET}로 선택되었습니다."))
            } else if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
                if (isCharging) {
                    startBuild()
                } else {
                    val result = test()
                    if (result != TestResult.Success) {
                        result.message(this)?.let { player.sendActionBar(it) }
                        return
                    }

                    if (psychic.mana < concept.maxCost / concept.maxChargeTick * 10) {
                        player.sendActionBar(text("최소 마나가 필요합니다").decorate(TextDecoration.BOLD)
                            .append(space())
                            .append(text(concept.maxCost / concept.maxChargeTick * 10)))
                        return
                    }

                    if (isBuilding) {
                        player.sendActionBar(text("건설 중인 건축물이 있습니다").decorate(TextDecoration.BOLD))
                        return
                    }

                    val location = player.eyeLocation.apply { y -= 1 }
                    val direction = location.direction
                    val world = location.world

                    when (chosenStructureIndex) {
                        in 0..1 -> {
                            bridgeLocation = location
                            bridgeDirection = direction
                        }
                        2 -> {
                            cubeRayLocation = world.rayTraceBlocks(location, direction, concept.cubeRange, FluidCollisionMode.ALWAYS, true)?.hitPosition?.toLocation(world)
                            if (cubeRayLocation == null) {
                                player.sendActionBar(text("대상 혹은 위치가 지정되지 않았습니다").decorate(TextDecoration.BOLD))
                                return
                            }
                        }
                    }

                    cooldownTime = concept.cooldownTime

                    worker = psychic.spawnFakeEntity(player.location, ArmorStand::class.java).apply {
                        updateMetadata<ArmorStand> {
                            isMarker = true
                            setArms(true)
                            setBasePlate(false)
                            location.yaw = 0f
                            rightArmPose = EulerAngle((-45.0).toRadians(), location.yaw.toDouble().toRadians(), 0.0)
                        }
                        updateEquipment {
                            helmet = ItemStack(Material.LEATHER_HELMET)
                            chestplate = ItemStack(Material.LEATHER_CHESTPLATE)
                            leggings = ItemStack(Material.LEATHER_LEGGINGS)
                            boots = ItemStack(Material.LEATHER_BOOTS)
                            setItemInMainHand(ItemStack(Material.WOODEN_AXE))
                        }
                    }
                    isCharging = true
                }
            }
        }
    }
}






