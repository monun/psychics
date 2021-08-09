package io.github.dytroInc.psychics.ability.swordblender

import io.github.monun.psychics.Ability
import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.AbilityType
import io.github.monun.psychics.TestResult
import io.github.monun.psychics.attribute.EsperAttribute
import io.github.monun.psychics.attribute.EsperStatistic
import io.github.monun.psychics.damage.Damage
import io.github.monun.psychics.damage.DamageType
import io.github.monun.psychics.tooltip.TooltipBuilder
import io.github.monun.psychics.tooltip.stats
import io.github.monun.psychics.tooltip.template
import io.github.monun.psychics.util.hostileFilter
import io.github.monun.tap.config.Config
import io.github.monun.tap.config.Name
import io.github.monun.tap.fake.FakeEntity
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.FluidCollisionMode
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.util.EulerAngle
import org.bukkit.util.Vector
import kotlin.math.ceil

// 칼을 들고 돌아서 주위 적을 죽이는 능력.
@Name("sword-blender")
class AbilityConceptSwordBlender : AbilityConcept() {

    @Config
    var golden = Damage.of(DamageType.MELEE, EsperAttribute.ATTACK_DAMAGE to 3.0)

    @Config
    var iron = Damage.of(DamageType.MELEE, EsperAttribute.ATTACK_DAMAGE to 3.5)

    @Config
    var diamond = Damage.of(DamageType.MELEE, EsperAttribute.ATTACK_DAMAGE to 4.0)

    @Config
    var netherite = Damage.of(DamageType.MELEE, EsperAttribute.ATTACK_DAMAGE to 5.0)

    @Config
    var rotateSpeed = 45

    @Config
    var gravity = 0.2
    init {
        range = 2.0
        knockback = 0.8
        cooldownTime = 15000L
        displayName = "칼 믹서기"
        durationTime = 3000L
        wand = ItemStack(Material.IRON_SWORD).apply {
            editMeta {
                addItemFlags(*ItemFlag.values())
            }
        }
        description = listOf(
            text("칼을 들고 몸을 돌려서 주위 적들을 죽입니다.")
        )
        type = AbilityType.ACTIVE
        cost = 25.0
    }

    override fun onRenderTooltip(tooltip: TooltipBuilder, stats: (EsperStatistic) -> Double) {
        tooltip.stats(text("금").color(NamedTextColor.WHITE), golden) { NamedTextColor.GOLD to "golden" }
        tooltip.stats(text("철").color(NamedTextColor.WHITE), iron) { NamedTextColor.WHITE to "iron" }
        tooltip.stats(text("다이아몬드").color(NamedTextColor.WHITE), diamond) { NamedTextColor.AQUA to "diamond" }
        tooltip.stats(text("네더라이트").color(NamedTextColor.WHITE), netherite) { NamedTextColor.RED to "netherite" }

        tooltip.template("iron", stats(iron.stats))
        tooltip.template("golden", stats(golden.stats))
        tooltip.template("diamond", stats(diamond.stats))
        tooltip.template("netherite", stats(netherite.stats))
    }

    fun findSwordDamage(type: Material?) = when (type) {
        Material.GOLDEN_SWORD -> golden
        Material.IRON_SWORD -> iron
        Material.DIAMOND_SWORD -> diamond
        Material.NETHERITE_SWORD -> netherite
        else -> null
    }
}

class AbilitySwordBlender : Ability<AbilityConceptSwordBlender>(), Listener {

    var useTime = 0L
    var currentDamage: Damage? = null

    var originalGameMode = GameMode.SURVIVAL

    private var fakeHuman: FakeEntity? = null


    override fun onEnable() {
        psychic.registerEvents(this)
        psychic.runTaskTimer(this::tick, 0, 1)
        if (cooldownTime > 0) {
            updateSwordCooldown((cooldownTime / 50L).toInt())
        }
    }

    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        val player = esper.player
        val inventory = player.inventory
        val action = event.action
        if(action == Action.RIGHT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR) {
            concept.findSwordDamage(event.item?.type)?.let {
                if(useTime == 0L) {
                    val result = test()

                    if (result != TestResult.Success) {
                        esper.player.sendActionBar(result.message(this))
                        return
                    }
                    if(psychic.consumeMana(concept.cost)) {
                        cooldownTime = concept.cooldownTime
                        updateSwordCooldown((cooldownTime / 50L).toInt())
                        currentDamage = it

                        originalGameMode = player.gameMode

                        player.gameMode = GameMode.SPECTATOR
                        fakeHuman = psychic.spawnFakeEntity(player.location, ArmorStand::class.java).apply {
                            updateMetadata<ArmorStand> {
                                setArms(true)
                                leftArmPose = EulerAngle(90.0, 325.0, 0.0)
                                rightArmPose = EulerAngle(90.0, 25.0, 0.0)
                                isInvulnerable = true
                                setBasePlate(false)
                            }
                            updateEquipment {
                                setItemInMainHand(inventory.itemInMainHand)
                                setItemInOffHand(inventory.itemInOffHand)
                                boots = inventory.boots?: ItemStack(Material.LEATHER_BOOTS)
                                leggings = inventory.leggings?: ItemStack(Material.LEATHER_LEGGINGS)
                                chestplate = inventory.chestplate?: ItemStack(Material.LEATHER_CHESTPLATE)
                                helmet = ItemStack(Material.PLAYER_HEAD).apply {
                                    itemMeta = (itemMeta as SkullMeta).apply {
                                        owningPlayer = player
                                    }
                                }
                            }
                        }
                        useTime = (concept.durationTime / 50)
                    }
                }
            }
        }
    }

    @EventHandler
    fun swap(event: PlayerSwapHandItemsEvent) {
        if(useTime > 0) event.isCancelled = true
    }
    @EventHandler
    fun changeHotbar(event: PlayerItemHeldEvent) {
        if(useTime > 0) event.isCancelled = true
    }
    @EventHandler
    fun move(event: PlayerMoveEvent) {
        if(useTime > 0) event.isCancelled = true
    }

    fun tick() {
        if(useTime > 0) {
            val player = esper.player
            fakeHuman?.let {
                player.teleport(it.location.add(0.0, 2.5, 0.0).apply {
                    pitch = 90f
                    yaw = 0f
                })
                val location = it.location.apply {
                    yaw += concept.rotateSpeed
                    y = world.rayTrace(
                        this,
                        Vector(0.0, -1.0, 0.0),
                        concept.gravity,
                        FluidCollisionMode.NEVER,
                        true,
                        1.0
                    ) { true }.run {
                        if(this == null) (y - concept.gravity) else ceil(hitPosition.y).let { ny ->
                            if(clone().apply { y = ny }.block.isSolid) ny + 1.0 else ny
                        }
                    }
                }
                it.moveTo(location)
                if(concept.rotateSpeed > 0) {
                    if(useTime % (360L / (concept.rotateSpeed * 2)) == 0L || useTime == (durationTime  / 50L)) {
                        it.bukkitEntity.getNearbyEntities(concept.range, 1.5, concept.range).forEach { e ->
                            if(player.hostileFilter().test(e)) {
                                if(e is LivingEntity) {
                                    e.psychicDamage(currentDamage!!)
                                    player.swingMainHand()
                                }
                            }
                        }
                    }
                }
            }
            useTime--
            if(useTime == 0L) {
                player.teleport(fakeHuman?.location?: player.location)
                fakeHuman?.remove()

                player.gameMode = originalGameMode
            }
        }
    }
    private fun updateSwordCooldown(cooldownTicks: Int) {
        val player = esper.player
        player.setCooldown(Material.GOLDEN_SWORD, cooldownTicks)
        player.setCooldown(Material.IRON_SWORD, cooldownTicks)
        player.setCooldown(Material.DIAMOND_SWORD, cooldownTicks)
        player.setCooldown(Material.NETHERITE_SWORD, cooldownTicks)
    }
}
