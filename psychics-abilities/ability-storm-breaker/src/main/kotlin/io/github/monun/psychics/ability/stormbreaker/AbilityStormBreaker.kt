package io.github.monun.psychics.ability.stormbreaker

import io.github.monun.psychics.*
import io.github.monun.psychics.attribute.EsperAttribute
import io.github.monun.psychics.attribute.EsperStatistic
import io.github.monun.psychics.damage.Damage
import io.github.monun.psychics.damage.DamageType
import io.github.monun.psychics.tooltip.TooltipBuilder
import io.github.monun.psychics.tooltip.stats
import io.github.monun.psychics.tooltip.template
import io.github.monun.psychics.util.TargetFilter
import io.github.monun.tap.config.Config
import io.github.monun.tap.config.Name
import io.github.monun.tap.fake.FakeEntity
import io.github.monun.tap.fake.Movement
import io.github.monun.tap.fake.Trail
import io.github.monun.tap.math.normalizeAndLength
import io.github.monun.tap.math.toRadians
import io.github.monun.tap.trail.TrailSupport
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.*
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.EulerAngle
import org.bukkit.util.Vector
import kotlin.random.Random.Default.nextDouble
import kotlin.random.Random.Default.nextFloat

// 도끼를 던져 번개를 내리치고 순간이동 할 수 있음
@Name("storm-breaker")
class AbilityConceptStormBreaker : AbilityConcept() {
    @Config
    var lightningRadius = 2.0

    @Config
    var iron = Damage.of(DamageType.MELEE, EsperAttribute.ATTACK_DAMAGE to 2.0)

    @Config
    var golden = Damage.of(DamageType.MELEE, EsperAttribute.ATTACK_DAMAGE to 2.5)

    @Config
    var diamond = Damage.of(DamageType.MELEE, EsperAttribute.ATTACK_DAMAGE to 3.0)

    @Config
    var netherite = Damage.of(DamageType.MELEE, EsperAttribute.ATTACK_DAMAGE to 4.0)

    @Config
    var axeSpeed = 2.0

    @Config
    var axeGravity = 0.04

    @Config
    var slowAmplifier = 3

    @Config
    var slowDurationTicks = 60

    init {
        type = AbilityType.ACTIVE
        range = 256.0
        cooldownTime = 10L * 1000L
        durationTime = 5L * 1000L
        wand = ItemStack(Material.LIGHTNING_ROD).apply {
            editMeta {
                addItemFlags(*ItemFlag.values())
            }
        }
        displayName = "스톰브레이커"
        description = listOf(
            text("도끼를 우클릭 시 도끼를 던져 적중한 곳에"),
            text("번개를 내리쳐 피해를 입히고 둔화시킵니다."),
            text("도끼는 지속시간동안 유지되며 지속시간 이내에"),
            text("스킬을 재사용시 도끼 위치로 순간이동합니다.")
        )
    }

    override fun onRenderTooltip(tooltip: TooltipBuilder, stats: (EsperStatistic) -> Double) {
        tooltip.stats(text("철").color(NamedTextColor.WHITE), iron) { NamedTextColor.WHITE to "iron" }
        tooltip.stats(text("금").color(NamedTextColor.WHITE), golden) { NamedTextColor.GOLD to "golden" }
        tooltip.stats(text("다이아몬드").color(NamedTextColor.WHITE), diamond) { NamedTextColor.AQUA to "diamond" }
        tooltip.stats(text("네더라이트").color(NamedTextColor.WHITE), netherite) { NamedTextColor.RED to "netherite" }

        tooltip.template("iron", stats(iron.stats))
        tooltip.template("golden", stats(golden.stats))
        tooltip.template("diamond", stats(diamond.stats))
        tooltip.template("netherite", stats(netherite.stats))
    }

    fun findDamage(type: Material): Damage? {
        return when (type) {
            Material.IRON_AXE -> iron
            Material.GOLDEN_AXE -> golden
            Material.DIAMOND_AXE -> diamond
            Material.NETHERITE_AXE -> netherite
            else -> null
        }
    }
}

class AbilityStormBreaker : Ability<AbilityConceptStormBreaker>() {
    private var hittedAxe: FakeEntity<ArmorStand>? = null

    override fun onEnable() {
        psychic.registerEvents(AxeListener())
        psychic.runTaskTimer(this::tick, 0L, 1L)

        if (cooldownTime > 0) {
            updateCooldown((cooldownTime / 50L).toInt())
        }
    }

    override fun onDisable() {
        hittedAxe = null
    }

    override fun test(): TestResult {
        if (psychic.channeling != null)
            return TestResult.FailedChannel

        return super.test()
    }

    private fun tick() {
        hittedAxe?.let {
            if (durationTime <= 0L) {
                hittedAxe = null
                it.remove()

                val loc = it.location.apply { y += 0.5 }
                val world = loc.world
                world.spawnParticle(Particle.CLOUD, loc.x, loc.y, loc.z, 32, 0.0, 0.0, 0.0, 0.1)
                world.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_BREAK, SoundCategory.MASTER, 2.0F, 2.0F)
            }
        }
    }

    private fun updateCooldown(cooldownTicks: Int) {
        val player = esper.player
        player.setCooldown(Material.IRON_AXE, cooldownTicks)
        player.setCooldown(Material.GOLDEN_AXE, cooldownTicks)
        player.setCooldown(Material.DIAMOND_AXE, cooldownTicks)
        player.setCooldown(Material.NETHERITE_AXE, cooldownTicks)
    }

    inner class AxeListener : Listener {
        @EventHandler
        fun onPlayerInteract(event: PlayerInteractEvent) {
            val action = event.action
            if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
                event.item?.let { item ->
                    concept.findDamage(item.type)?.let { damage ->
                        if (durationTime > 0) {
                            durationTime = 0
                            hittedAxe?.let { axe ->
                                hittedAxe = null
                                axe.remove()
                                val player = esper.player
                                val pLoc = player.location
                                val world = player.world

                                val loc = axe.location.apply {
                                    val loc = player.location
                                    yaw = loc.yaw
                                    pitch = loc.pitch
                                    y += 0.5
                                }
                                player.teleport(loc)
                                world.spawnParticle(Particle.CLOUD, pLoc.apply { y += 1.0 }, 32, 0.0, 0.0, 0.0, 0.25)
                                world.playSound(
                                    loc,
                                    Sound.ENTITY_ENDERMAN_TELEPORT,
                                    SoundCategory.MASTER,
                                    1.0F,
                                    0.3F + nextFloat() * 0.2F
                                )
                                world.spawnParticle(Particle.CLOUD, loc.apply { y += 1.0 }, 32, 0.0, 0.0, 0.0, 0.25)
                            }
                            return
                        } else {
                            val result = test()

                            if (result != TestResult.Success) {
                                result.message(this@AbilityStormBreaker)?.let { esper.player.sendActionBar(it) }
                                return
                            }

                            cooldownTime = concept.cooldownTime
                            val cooldownTicks = (concept.cooldownTime / 50).toInt()
                            updateCooldown(cooldownTicks)

                            val location = esper.player.eyeLocation
                            val projectile = AxeProjectile(damage, item).apply {
                                axe = this@AbilityStormBreaker.psychic.spawnFakeEntity(
                                    location.clone().apply { y -= 1.62 },
                                    ArmorStand::class.java
                                ).apply {
                                    velocity = location.direction.multiply(concept.axeSpeed)

                                    updateMetadata {
                                        isMarker = true
                                        isVisible = false
                                    }
                                    updateEquipment {
                                        helmet = item.clone()
                                    }
                                }
                            }

                            psychic.launchProjectile(location, projectile)
                            esper.player.swingMainHand()
                        }
                    }
                }
            }
        }
    }

    inner class AxeProjectile(
        private val damage: Damage, private val item: ItemStack
    ) : PsychicProjectile(1200, concept.range) {
        var axe: FakeEntity<ArmorStand>? = null

        override fun onPreUpdate() {
            velocity = velocity.apply { y -= concept.axeGravity }
        }

        override fun onMove(movement: Movement) {
            val to = movement.to
            axe?.let { axe ->
                axe.moveTo(to.clone().apply { y -= 1.62; yaw -= 90.0F })
                axe.updateMetadata {
                    headPose = EulerAngle(0.0, 0.0, ticks * -0.5)
                }
            }
        }

        override fun onTrail(trail: Trail) {
            trail.velocity?.let { v ->
                val from = trail.from
                val world = from.world

                TrailSupport.trail(from, trail.to, 0.25) { w, x, y, z ->
                    w.spawnParticle(Particle.END_ROD, x, y, z, 1, 0.0, 0.0, 0.0, 0.025, null, true)
                }

                val length = v.normalizeAndLength()
                if (length == 0.0) return

                val filter = TargetFilter(esper.player)
                world.rayTrace(
                    from,
                    v,
                    length,
                    FluidCollisionMode.NEVER,
                    true,
                    1.0,
                    filter,
                )?.let { rayTraceResult ->
                    durationTime = concept.durationTime

                    val hitLocation = rayTraceResult.hitPosition.toLocation(world)
                    axe?.let { axe ->
                        this.axe = null
                        axe.moveTo(hitLocation.clone().apply { y -= 0.5; yaw = from.yaw - 90.0F })
                        axe.updateMetadata {
                            headPose = EulerAngle(0.0, 0.0, (-180.0).toRadians())
                        }
                        hittedAxe = axe
                    }

                    val particleVector = Vector()

                    for (i in 0 until 64) {
                        particleVector.copy(v).apply {
                            x += nextDouble() - 0.5
                            y = nextDouble()
                            z += nextDouble() - 0.5
                        }
                        world.spawnParticle(
                            Particle.ITEM_CRACK,
                            hitLocation,
                            0,
                            particleVector.x,
                            particleVector.y,
                            particleVector.z,
                            1.0,
                            item

                        )
                    }

                    remove()

                    val radius = concept.lightningRadius
                    val knockback = concept.knockback
                    world.strikeLightningEffect(hitLocation)
                    val potionEffect = PotionEffect(
                        PotionEffectType.SLOW,
                        concept.slowDurationTicks,
                        concept.slowAmplifier,
                        false,
                        false,
                        false
                    )
                    world.getNearbyEntities(hitLocation, radius, radius, radius, filter).forEach { target ->
                        if (target is LivingEntity) {
                            target.addPotionEffect(potionEffect)
                            target.psychicDamage(damage, hitLocation, knockback)
                        }
                    }
                }
            }
        }

        override fun onRemove() {
            axe?.remove()
        }
    }
}

