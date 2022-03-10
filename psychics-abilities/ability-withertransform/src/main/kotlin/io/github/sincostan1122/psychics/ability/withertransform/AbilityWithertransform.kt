package io.github.sincostan1122.psychics.ability.withertransform

import io.github.monun.psychics.*
import io.github.monun.psychics.attribute.EsperAttribute
import io.github.monun.psychics.attribute.EsperStatistic
import io.github.monun.psychics.damage.Damage
import io.github.monun.psychics.damage.DamageType
import io.github.monun.psychics.damage.psychicDamage
import io.github.monun.psychics.tooltip.TooltipBuilder
import io.github.monun.psychics.util.TargetFilter
import io.github.monun.tap.config.Config
import io.github.monun.tap.config.Name
import io.github.monun.tap.event.EntityProvider
import io.github.monun.tap.event.TargetEntity
import io.github.monun.tap.fake.FakeEntity
import io.github.monun.tap.fake.Movement
import io.github.monun.tap.fake.Trail
import io.github.monun.tap.math.normalizeAndLength
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.*
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.player.PlayerEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerVelocityEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.BoundingBox
import kotlin.math.max


@Name("Withertransform")
class AbilityConceptWithertransform : AbilityConcept() {

    @Config
    var skullSpeed = 5.0

    @Config
    var skullSize = 2.0

    @Config
    var witherAm = 1

    @Config
    var witherDuration = 5.0

    @Config
    var transformDuration = 5.0

    @Config
    var explosionDamage = 4.0

    @Config
    var explosionRange = 7.0

    @Config
    var explosionRange2 = 3.0

    @Config
    var healthPlusAM = 25

    @Config
    var requireSoul = 10

    @Config
    var durationPlus = 3000

    init {
        type = AbilityType.ACTIVE
        range = 128.0
        durationTime = 15000L
        cooldownTime = 1500L
        wand = ItemStack(Material.WITHER_ROSE)
        damage = Damage.of(DamageType.BLAST, EsperStatistic.of(EsperAttribute.ATTACK_DAMAGE to 5.0))
        displayName = "위더 강림"

        description = listOf(
            text("${ChatColor.GOLD}인간 형상일때:"),
            text("시전하기 위해 위더 해골 3개, 소울샌드 4개,"),
            text("그리고 영혼 ${requireSoul}개를 제물로 바쳐야 합니다."),
            text("웅크린 채로 우클릭하여 바칠 수 있습니다."),
            text("시전 시 주변에 폭팔을 일으키며 추가 체력을 얻고."),
            text("위더로 변신합니다."),
            text(""),
            text("${ChatColor.GRAY}위더 형상일때:"),
            text("낙하 피해를 받지 않고 비행이 가능해집니다."),
            text("위더 장미를 우클릭할 시 위더 해골을 발사합니다."),
            text("위더 해골은 폭팔을 일으키고 시듦 효과를 부여합니다."),
            text("생명체를 처치할 시 지속시간이 늘어납니다.")
        )
    }
    override fun onRenderTooltip(tooltip: TooltipBuilder, stats: (EsperStatistic) -> Double) {
        tooltip.header(
            text().content("폭팔 피해량 ").color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)
                .append(text().content((explosionDamage * 100.0).toInt().toString())).append(text().content("%")).build()
        )
        tooltip.header(
            text().content("시듦 LV.").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(text().content(witherAm.toString())).build()
        )
        tooltip.header(
            text().content("처치 시 지속시간 증가량 ").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(text().content((durationPlus / 1000).toString())).append(text().content("초")).build()
        )
    }
}
class AbilityWithertransform : Ability<AbilityConceptWithertransform>(), Listener {
    private var wither : FakeEntity? = null
    private var tnt: TNT? = null
    private var withertnt: TNT? = null
    private var transformLevel = 0
    private var skull = 0
    private var soulsand = 0
    private var soul = 0
    lateinit var anchoredLoc : Location
    override fun onEnable() {
        psychic.registerEvents(this)

        val world = esper.player.world
        psychic.runTaskTimer({
            var location = esper.player.location
            scanCreative()
            if(transformLevel == 0) {
                esper.player.sendActionBar(text().content("바친 위더 해골:").append(text().content(skull.toString()))
                    .append(text().content(" "))
                    .append(text().content("바친 영혼 모래:").append(text().content(soulsand.toString())))
                    .append(text().content(" "))
                    .append(text().content("모은 영혼:").append(text().content(soul.toString()))))
                if(soulsand == 4 && skull == 3 && soul == concept.requireSoul) {
                    transformLevel = 1
                }
            }
            if(transformLevel == 1) {

                esper.player.sendActionBar("당신의 신체에 위더가 강림하려 합니다...")
                world.spawnParticle(Particle.SMOKE_NORMAL, location.x, location.y, location.z, 5, 1.0, 1.0, 1.0, 0.0, null, true)
            }
            if(transformLevel == 2)  {
                giveEffect()
                esper.player.addPotionEffect(
                    PotionEffect(PotionEffectType.ABSORPTION, 999999999, concept.healthPlusAM, false, false)
                )

                esper.player.teleport(anchoredLoc)
                esper.player.sendActionBar("당신의 신체에 위더가 강림하고 있습니다...")
                world.spawnParticle(Particle.SMOKE_NORMAL, location.x, location.y, location.z, 20, 1.0, 1.0, 1.0, 0.0, null, true)
                wither?.moveTo(esper.player.location)
                if(durationTime == 0L) {
                    tnt = TNT(esper.player.location)
                    destroy()
                    durationTime = concept.durationTime
                    transformLevel = 3
                }
            }
            if(transformLevel == 3) {
                giveEffect()
                esper.player.sendActionBar(text().content("본래 모습으로 돌아오기까지 ").append(text().content((durationTime / 1000L).toString()))
                    .append(text().content("초")))
                world.spawnParticle(Particle.SMOKE_NORMAL, location.x, location.y, location.z, 10, 0.5, 0.5, 0.5, 0.0, null, true)
                esper.player.allowFlight = true
                esper.player.isFlying = true
                wither?.moveTo(esper.player.location)
                if(durationTime == 0L) {
                    wither?.remove()
                    wither = null
                    esper.player.allowFlight = false
                    esper.player.isFlying = false
                    scanCreative()
                    transformLevel = 0
                    esper.player.removePotionEffect(PotionEffectType.ABSORPTION)
                }
            }

        }, 0L, 1L)
    }
    override fun onDisable() {
        transformLevel = 0
        skull = 0
        soulsand = 0
        soul = 0
        val player = esper.player

        player.allowFlight = false
        player.isFlying = false
        scanCreative()
        wither?.remove()
        wither = null
    }

    fun scanCreative() {
        if(esper.player.gameMode == GameMode.CREATIVE) {
            esper.player.allowFlight = true
        }
    }
    fun giveEffect() {
        esper.player.addPotionEffects(
            listOf(
                PotionEffect(
                    PotionEffectType.INVISIBILITY,
                    10,
                    1,
                    false,
                    false
                ),
                PotionEffect(
                    PotionEffectType.WEAKNESS,
                    10,
                    255,
                    false,
                    false
                )
            )

        )
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val action = event.action

        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            if(transformLevel == 0 && esper.player.isSneaking) {
                event.item?.let { item ->
                    if (item.type == Material.SOUL_SAND) {
                        if(soulsand < 4) {
                            val player = esper.player
                            if(cooldownTime > 0L) return
                            cooldownTime = 80L

                            if (player.gameMode != GameMode.CREATIVE) item.amount--

                            soulsand++
                            var location = player.location
                            esper.player.world.spawnParticle(Particle.CLOUD, location.x, location.y, location.z, 5, 1.0, 1.0, 1.0, 0.0, null, true)
                            esper.player.eyeLocation.world.playSound(esper.player.eyeLocation, Sound.ENTITY_BLAZE_AMBIENT , 1.0F, 0.1F)
                        }

                    }
                    if (item.type == Material.WITHER_SKELETON_SKULL) {
                        if(skull < 3) {
                            if(cooldownTime > 0L) return
                            cooldownTime = 80L
                            val player = esper.player


                            if (player.gameMode != GameMode.CREATIVE) item.amount--

                            skull++
                            var location = player.location
                            esper.player.world.spawnParticle(Particle.CLOUD, location.x, location.y, location.z, 5, 1.0, 1.0, 1.0, 0.0, null, true)
                            esper.player.eyeLocation.world.playSound(esper.player.eyeLocation, Sound.ENTITY_BLAZE_AMBIENT , 1.0F, 0.1F)
                        }

                    }
                }
            }


            event.item?.let { item ->
                if (item.type == Material.WITHER_ROSE) {
                    val player = esper.player
                    if(transformLevel == 1) {
                        soulsand = 0
                        skull = 0
                        soul = 0
                        wither = this.psychic.spawnFakeEntity(player.location, Wither::class.java).apply {
                            updateMetadata<Wither> {
                                setAI(false)

                            }
                        }
                        anchoredLoc = esper.player.location
                        durationTime = (concept.transformDuration * 1000).toLong()
                        transformLevel = 2
                        esper.player.eyeLocation.world.playSound(esper.player.eyeLocation, Sound.ENTITY_WITHER_SPAWN , 1.0F, 0.1F)
                    }
                    if(transformLevel == 3) {
                        if(cooldownTime > 0L) return
                        cooldownTime = concept.cooldownTime
                        val location = player.eyeLocation
                        val projectile = SkullProjectile().apply {
                            skull =
                                this@AbilityWithertransform.psychic.spawnFakeEntity(location, ArmorStand::class.java).apply {
                                    updateMetadata<ArmorStand> {
                                        isVisible = false
                                    }
                                    updateEquipment {
                                        helmet = ItemStack(Material.WITHER_SKELETON_SKULL)
                                    }
                                }
                        }
                        cooldownTime = concept.cooldownTime
                        psychic.launchProjectile(location, projectile)
                        projectile.velocity = location.direction.multiply(concept.skullSpeed)

                        val loc = player.location
                        loc.world.playSound(loc, Sound.ENTITY_WITHER_SHOOT, 1.0F, 0.1F)
                    }
                }
            }


        }
    }

    @EventHandler
    @TargetEntity(EntityProvider.EntityDeath.Killer::class)
    fun onKill(event: EntityDeathEvent) {
        if(transformLevel == 0) {
            if(soul < concept.requireSoul) {
                esper.player.location.world.spawnParticle(Particle.SOUL, event.entity.location, 5, 0.5, 0.5, 0.5, 0.0, null, true)
                soul++
            }
        }
        if (transformLevel == 3) {
            esper.player.location.world.spawnParticle(Particle.SOUL, event.entity.location, 5, 0.5, 0.5, 0.5, 0.0, null, true)
            durationTime += concept.durationPlus
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onVelocity(event: PlayerVelocityEvent) {
        if (transformLevel >= 2) {
            event.isCancelled = true
        }

    }
    private fun destroy() {
        tnt?.run {
            remove()
            tnt = null

            val location = esper.player.location
            val world = location.world

            val r = max(1.0, concept.explosionRange- 2.0)
            world.spawnParticle(Particle.EXPLOSION_HUGE, location, (r * r).toInt(), r, r, r, 0.0, null, true)
            world.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 2.0F, 1.0F)

            val damage = concept.damage!!
            var amount = esper.getStatistic(damage.stats)


            val knockback = concept.knockback

            val box = BoundingBox.of(location, r, r, r)
            world.getNearbyEntities(box, TargetFilter(esper.player)).forEach { enemy ->
                if (enemy is LivingEntity) {
                    enemy.psychicDamage(this@AbilityWithertransform, damage.type, amount * concept.explosionDamage, esper.player, location, knockback)
                }
            }



        }
    }

    private fun witherdestroy(location : Location) {
        withertnt?.run {
            remove()
            withertnt = null

            val world = location.world

            val r = max(1.0, concept.explosionRange2- 2.0)
            world.spawnParticle(Particle.EXPLOSION_HUGE, location, (r * r).toInt(), r, r, r, 0.0, null, true)
            world.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 2.0F, 1.0F)

            val damage = concept.damage!!
            var amount = esper.getStatistic(damage.stats)


            val knockback = concept.knockback

            val box = BoundingBox.of(location, r, r, r)
            world.getNearbyEntities(box, TargetFilter(esper.player)).forEach { enemy ->
                if (enemy is LivingEntity) {
                    enemy.psychicDamage(this@AbilityWithertransform, damage.type, amount, esper.player, location, knockback)
                    enemy.addPotionEffect(
                        PotionEffect(PotionEffectType.WITHER,
                            concept.witherDuration.toInt() * 20, concept.witherAm, false, false)
                    )

                }
            }



        }
    }

    inner class TNT(location: Location) {
        private val stand: FakeEntity
        private val tnt: FakeEntity

        init {
            val psychic = psychic
            stand = psychic.spawnFakeEntity(location, ArmorStand::class.java).apply {
                updateMetadata<ArmorStand> {
                    isMarker = true
                    isInvisible = true
                }
            }
            tnt = psychic.spawnFakeEntity(location, TNTPrimed::class.java).apply {
                updateMetadata<TNTPrimed> {
                    fuseTicks = 1
                }
            }
            stand.addPassenger(tnt)
        }

        fun remove() {
            tnt.remove()
            stand.remove()
        }
    }
    inner class SkullProjectile : PsychicProjectile(1200, concept.range) {
        lateinit var skull: FakeEntity

        override fun onMove(movement: Movement) {
            skull.moveTo(movement.to.clone().apply { y -= 1.62 })
        }

        override fun onTrail(trail: Trail) {
            trail.velocity?.let { v ->
                val length = v.normalizeAndLength()

                if (length > 0.0) {
                    val start = trail.from
                    val world = start.world

                    world.rayTrace(
                        start, v, length, FluidCollisionMode.NEVER, true, concept.skullSize / 2.0,
                        TargetFilter(esper.player)
                    )?.let { result ->
                        remove()

                        val hitLocation = result.hitPosition.toLocation(world)

                        result.hitEntity?.let { entity ->
                            if (entity is LivingEntity) {
                                entity.psychicDamage()
                            }
                        }
                        withertnt = TNT(hitLocation)
                        witherdestroy(hitLocation)
                    }
                }
            }
        }

        override fun onRemove() {
            skull.remove()
        }
    }
}
