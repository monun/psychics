package io.github.anblus.psychics.ability.shackle

import com.destroystokyo.paper.event.entity.EntityJumpEvent
import com.destroystokyo.paper.event.player.PlayerJumpEvent
import io.github.monun.psychics.*
import io.github.monun.psychics.attribute.EsperAttribute
import io.github.monun.psychics.attribute.EsperStatistic
import io.github.monun.psychics.tooltip.TooltipBuilder
import io.github.monun.psychics.tooltip.stats
import io.github.monun.psychics.util.TargetFilter
import io.github.monun.tap.config.Config
import io.github.monun.tap.config.Name
import io.github.monun.tap.fake.FakeEntity
import io.github.monun.tap.fake.Movement
import io.github.monun.tap.fake.Trail
import io.github.monun.tap.math.normalizeAndLength
import io.github.monun.tap.task.TickerTask
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.FluidCollisionMode
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityTeleportEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector
import kotlin.math.round
import kotlin.random.Random.Default.nextDouble

// 상대의 이동을 멈춘다
@Name("shackle")
class AbilityConceptShackle : AbilityConcept() {

    @Config
    val durationPerCost = EsperStatistic.of(EsperAttribute.ATTACK_DAMAGE to 1.0)

    @Config
    val maxDurationAboutOne = EsperStatistic.of(EsperAttribute.ATTACK_DAMAGE to 5.0)

    @Config
    val invalidTimeToShackle = 2.0

    init {
        displayName = "속박"
        type = AbilityType.ACTIVE
        cost = 8.0
        cooldownTime = 0L
        range = 20.0
        description = listOf(
            text("좌클릭 시 지정한 상대를 구속 시키고, 매 초 마나를 소비합니다."),
            text("구속은 상대에게 일정 시간 이상 유지되면 자동으로 해제됩니다."),
            text("구속에서 풀려난 상대에겐 곧바로 능력을 사용할 수 없습니다."),
            text("이미 속박한 상대가 있어도 다른 상대를 구속할 수 있습니다."),
            text("이 때 여러 명을 구속했다면 마나는 더욱 빨리 소모됩니다."),
            text("우클릭 시 즉시 현재 구속한 상대를 모두 해방시킵니다."),
            text("구속 상태에서 텔레포트시 구속 상태가 해제됩니다.")
        )
        wand = ItemStack(Material.STRING)

    }

    override fun onRenderTooltip(tooltip: TooltipBuilder, stats: (EsperStatistic) -> Double) {
        tooltip.stats(stats(durationPerCost)) { NamedTextColor.DARK_PURPLE to "마나 소모당 구속 시간" to "초" }
        tooltip.stats(stats(maxDurationAboutOne)) { NamedTextColor.DARK_GREEN to "최대 구속 시간" to "초" }
    }
}

class AbilityShackle : Ability<AbilityConceptShackle>(), Listener {

    private var shackledEntityList = arrayListOf<ShackledEntity>()

    private var webProjectileList = arrayListOf<WebProjectile>()

    override fun onEnable() {
        psychic.registerEvents(this)
    }

    override fun onDisable() {
        shackledEntityList.forEach { it.untie() }
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = esper.player
        if (event.player != player) return

        val action = event.action

        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            event.item?.let { item ->
                if (item.type == concept.wand?.type) {
                    val testResult = test()
                    if (testResult != TestResult.Success) {
                        testResult.message(this)?.let { player.sendActionBar(it) }
                        return
                    }

                    val world = player.world
                    val location = player.eyeLocation

                    world.rayTrace(
                        location,
                        location.direction,
                        concept.range,
                        FluidCollisionMode.NEVER,
                        true,
                        0.5,
                        TargetFilter(player)
                    )?.hitEntity?.let { entity ->
                        if (entity is LivingEntity) {
                            shackledEntityList.find { it.entity == entity }?: run {
                                webProjectileList.find { it.target == entity }?: run {
                                    cooldownTime = concept.cooldownTime

                                    val projectile = WebProjectile().apply {
                                        web =
                                            this@AbilityShackle.psychic.spawnFakeEntity(
                                                location,
                                                ArmorStand::class.java
                                            ).apply {
                                                updateMetadata<ArmorStand> {
                                                    isVisible = false
                                                    isMarker = true
                                                }
                                                updateEquipment {
                                                    helmet = ItemStack(Material.COBWEB)
                                                }
                                            }
                                        target = entity
                                        velocity = location.direction.multiply(1.5)
                                        repeat(8) {
                                            fakeWeb.add(
                                                this@AbilityShackle.psychic.spawnFakeEntity(
                                                    location,
                                                    ArmorStand::class.java
                                                ).apply {
                                                    updateMetadata<ArmorStand> {
                                                        isVisible = false
                                                        isMarker = true
                                                    }
                                                    updateEquipment {
                                                        helmet = ItemStack(Material.COBWEB)
                                                    }
                                                })
                                            fakeWebLocation.add(
                                                Vector(
                                                    nextDouble(-0.5, 0.5),
                                                    nextDouble(-0.5, 0.5),
                                                    nextDouble(-0.5, 0.5)
                                                )
                                            )
                                        }
                                    }

                                    webProjectileList.add(projectile)
                                    psychic.launchProjectile(location, projectile)

                                    world.playSound(location, Sound.ENTITY_FOX_SNIFF, 2.0F, 2.0F)
                                    return
                                }
                            }
                        }
                    }
                    player.sendActionBar(text().content("대상 혹은 위치가 지정되지 않았습니다").decorate(TextDecoration.BOLD).build())
                }
            }
        } else if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            event.item?.let { item ->
                if (item.type == concept.wand?.type) {
                    shackledEntityList.filter { !it.isInvalid }.let { list ->
                       if (list.isNotEmpty()) {
                           shackledEntityList.forEach { it.untie() }
                           cooldownTime = concept.cooldownTime
                           player.world.playSound(player.location, Sound.BLOCK_CHAIN_BREAK, 2.0f, 0.2f)
                       } else {
                           player.sendActionBar(text().content("구속을 해제할 대상이 존재하지 않습니다").decorate(TextDecoration.BOLD).build())
                       }
                    }
                }
            }
        }
    }

    inner class WebProjectile : PsychicProjectile(1200, 99999.0) {
        lateinit var web: FakeEntity

        var fakeWeb = ArrayList<FakeEntity>()

        var fakeWebLocation = ArrayList<Vector>()

        lateinit var target: LivingEntity

        override fun onPreUpdate() {
            if (target.isValid && psychic.mana >= concept.cost) {
                val targetLoc = target.boundingBox.center.toLocation(location.world)
                val distance = location.distance(targetLoc)
                val direction = targetLoc.subtract(location).multiply(1.0 / distance).toVector()
                velocity = direction.multiply(velocity.length())
                location.world.spawnParticle(Particle.WHITE_ASH, location, 1, 0.0, 0.0, 0.0, 0.0)
            } else remove()
        }
        override fun onMove(movement: Movement) {
            val to = movement.to.clone().apply { y -= 1.62 }
            web.moveTo(to)
            fakeWeb.forEachIndexed { i, entity ->
                entity.moveTo(to.clone().add(fakeWebLocation[i]))
            }
        }

        override fun onTrail(trail: Trail) {
            trail.velocity?.let { velocity ->
                val from = trail.from
                val length = velocity.normalizeAndLength()
                val world = from.world

                world.rayTrace(
                    from,
                    velocity,
                    length,
                    FluidCollisionMode.NEVER,
                    true,
                    0.1,
                    TargetFilter(esper.player)
                )?.let { result ->
                    result.hitEntity?.let {
                        remove()
                        psychic.consumeMana(concept.cost)
                        shackledEntityList.add(ShackledEntity(target))
                        world.playSound(result.hitPosition.toLocation(world), Sound.BLOCK_HONEY_BLOCK_PLACE, 2.0f, 0.7f)
                    }
                }
            }
        }

        override fun onRemove() {
            web.remove()
            fakeWeb.forEach { entity ->
                entity.remove()
            }
            webProjectileList.remove(this)
        }
    }

    inner class ShackledEntity(val entity: LivingEntity) : Listener {
        var isInvalid: Boolean = false

        private var timer: TickerTask

        private var fakeWeb = ArrayList<FakeEntity>()

        private var fakeWebLocation = ArrayList<Vector>()

        private var fakeWebYaw = ArrayList<Float>()

        private var fakeWebPitch = ArrayList<Float>()

        private var invalidTick: Int = round(concept.invalidTimeToShackle * 20.0).toInt()

        private val defaultDurationTick: Int = round(esper.getStatistic(concept.durationPerCost) * 20.0).toInt()

        private var durationTick: Int = round(esper.getStatistic(concept.durationPerCost) * 20.0).toInt()

        private var totalTick: Int = 0

        private var maxTick: Int = round(esper.getStatistic(concept.maxDurationAboutOne) * 20.0).toInt()

        init {
            repeat(8) {
                fakeWeb.add(psychic.spawnFakeEntity(entity.location, ArmorStand::class.java).apply {
                    updateMetadata<ArmorStand> {
                        isVisible = false
                        isMarker = true
                    }
                    updateEquipment {
                        helmet = ItemStack(Material.COBWEB)
                    }
                })
                fakeWebLocation.add(Vector(nextDouble(-0.5, 0.5), nextDouble(-0.5, 0.5), nextDouble(-0.5, 0.5)))
                fakeWebYaw.add(nextDouble(360.0).toFloat())
                fakeWebPitch.add(nextDouble(180.0).toFloat())
            }
            psychic.plugin.entityEventManager.registerEvents(entity, this)
            entity.sendMessage(text().content("구속되었습니다!").color(NamedTextColor.DARK_GRAY).build())
            shackle()
            timer = psychic.runTaskTimer({
                if (isInvalid) {
                    invalidTick --
                    if (invalidTick <= 0) {
                        remove()
                    }
                } else {
                    totalTick ++
                    if (totalTick >= maxTick) {
                        untie()
                    } else {
                        durationTick --
                        entity.addPotionEffect(PotionEffect(PotionEffectType.SLOW, 3, 4))
                        fakeWeb.forEachIndexed { i, web ->
                            web.moveTo(entity.boundingBox.center.toLocation(entity.world).add(fakeWebLocation[i]).apply {
                                yaw += fakeWebYaw[i]
                                pitch += fakeWebPitch[i]
                                y -= 2.0
                            })
                        }
                        if (durationTick <= 0) {
                            if (psychic.mana >= concept.cost) {
                                shackle()
                                psychic.consumeMana(concept.cost)
                                durationTick = defaultDurationTick
                            } else {
                                untie()
                            }
                        }
                    }
                }
            }, 1L , 1L)
        }

        fun untie() {
            isInvalid = true
            psychic.plugin.entityEventManager.unregisterEvent(entity, this)
            fakeWeb.forEach { entity ->
                entity.remove()
            }
        }

        private fun remove() {
            shackledEntityList.remove(this)
            timer.cancel()
        }

        private fun shackle() {
            val location = entity.location
            location.world.spawnParticle(Particle.WHITE_ASH, location, 8, 0.5, 1.0, 0.5, 0.006)
        }

        @EventHandler(ignoreCancelled = true)
        fun onEntityTeleport(event: EntityTeleportEvent) {
            untie()
        }

        @EventHandler(ignoreCancelled = true)
        fun onPlayerTeleport(event: PlayerTeleportEvent) {
            untie()
        }

        @EventHandler(ignoreCancelled = true)
        fun onEntityJump(event: EntityJumpEvent) {
            event.isCancelled = true
        }

        @EventHandler(ignoreCancelled = true)
        fun onPlayerJump(event: PlayerJumpEvent) {
            event.isCancelled = true
        }
    }
}





