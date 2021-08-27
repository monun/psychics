package com.github.muqhc.psychics.ability.powerofflame

import io.github.monun.psychics.*
import io.github.monun.psychics.attribute.EsperAttribute
import io.github.monun.psychics.attribute.EsperStatistic
import io.github.monun.psychics.damage.Damage
import io.github.monun.psychics.damage.DamageType
import io.github.monun.psychics.damage.psychicDamage
import io.github.monun.psychics.util.TargetFilter
import io.github.monun.tap.config.Config
import io.github.monun.tap.config.Name
import io.github.monun.tap.event.EntityProvider
import io.github.monun.tap.event.TargetEntity
import io.github.monun.tap.fake.Movement
import io.github.monun.tap.fake.Trail
import io.github.monun.tap.math.normalizeAndLength
import net.kyori.adventure.text.Component.text
import org.bukkit.*
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import kotlin.math.*


@Name("power-of-flame")
class PowerOfFlameConcept: AbilityConcept() {

    private fun AttackData(ATK: Double, knockback: Double) : Map<String,Double> {
        return mapOf("ATK" to ATK, "knockback" to knockback)
    }

    @Config
    var flameSpeed = 1.0

    @Config
    var flameOverStackATK = 2.0

    @Config
    var flameOverStackKnockback = 2.0

    @Config
    var explosionType = DamageType.BLAST

    @Config
    var ignitionType = DamageType.FIRE

    @Config
    var flameCostPerTickForOne = 0.2

    @Config
    var dotDamagePeriodTicks = 20

    @Config
    var maxFlame = 5

    @Config
    var explosionAtkEachCount = listOf<Map<String,Double>>(
        AttackData(0.5, 0.9),
        AttackData(1.2, 1.8),
        AttackData(2.1, 2.7),
        AttackData(3.2, 3.6),
        AttackData(4.5, 4.5)
    )

    @Config
    var ignitionAtkEachCount = listOf<Map<String,Double>>(
        AttackData(0.1, 0.11),
        AttackData(0.17, 0.12),
        AttackData(0.28, 0.13),
        AttackData(0.43, 0.14),
        AttackData(0.62, 0.15)
    )

    @Config
    var nonFlameExplosionDamage = Damage(explosionType, EsperStatistic.Companion.of(EsperAttribute.ATTACK_DAMAGE to 0.3))

    @Config
    var nonFlameExplosionKnockback = 0.3

    init {
        type = AbilityType.ACTIVE
        displayName = "Power-Of-Flame"
        cooldownTime = 10
        cost = 20.0
        range = 50.0

        wand = ItemStack(Material.BLAZE_POWDER,1).apply {
            editMeta {
                displayName = "${ChatColor.GOLD}${ChatColor.BOLD}Heart Of Flame"
            }
        }
        supplyItems = listOf(
            wand!!
        )

        description = listOf(
            text("${ChatColor.BOLD}${ChatColor.GOLD}우클릭 ${ChatColor.RESET}${ChatColor.GRAY}불꽃 탄환 발사"),
            text("${ChatColor.BOLD}${ChatColor.GOLD}좌클릭 ${ChatColor.RESET}${ChatColor.GRAY}폭발 탄환 발사"),
            text(""),
            text("불꽃 탄환은 상대에게 불꽃 하나를 걸어두며,"),
            text("상대는 불꽃의 개수에 따라 지속 대미지를 입습니다다."),
            text("걸어 놓은 각 불꽃들은 소량의 마나를 소모 합니다."),
            text(""),
            text("만약 불꽃이 꽉 찬 상대가 볼꽃 탄환을 맞으면,"),
            text("탄환은 아주 소량의 넉백과 대미지를 주게 됩니다."),
            text(""),
            text("폭발 탄환은 상대에 걸린 불꽃을 모두 소진 시키며,"),
            text("동시에 걸려있던 불꽃의 개수에 따라 대미지와 넉백을 줍니다."),
            text(""),
            text("불꽃이 없는 상대에게도 소량의 대미지와 넉백을 줍니다."),
            text(""),
            text("※만약 마나를 모두 소진하면 ${ChatColor.BOLD}${ChatColor.RED}불꽃이 모두 꺼집니다."),
        )
    }
}

class PowerOfFlame: ActiveAbility<PowerOfFlameConcept>() {

    var triggers : MutableMap<LivingEntity,FlameOnPlayer> = mutableMapOf()

    init {
        targeter = {
            esper.player.eyeLocation
        }
    }

    override fun onInitialize() {
        concept.run {
            var failAttribute = mutableListOf<String>()
            if (ignitionAtkEachCount.count()==maxFlame) return@run else failAttribute.add("ignition-atk-each-count")
            if (explosionAtkEachCount.count()==maxFlame) return@run else failAttribute.add("explosion-atk-each-count")
            throw Exception(
                "Failed to load Attribute $failAttribute \n" +
                "ATK setting count must be same with \"max-flame\" (not same : ".apply {
                    failAttribute.forEach {
                        plus("$it.count() ≠ max-flame")
                    }
                } + " )"
            )
        }
    }

    override fun onEnable() {
        psychic.registerEvents(EventListener())
        psychic.runTaskTimer(Task(), 0L, 1L)
    }

    override fun onDisable() {
        triggers.clear()
    }

    override fun onCast(event: PlayerEvent, action: WandAction, target: Any?) {
        // target = targetEntity
    }


    inner class EventListener : Listener {
        @EventHandler
        fun onPlayerInteract(event: PlayerInteractEvent) {
            val action = event.action
            if (action == Action.PHYSICAL) return
            if (test() != TestResult.Success) return
            if (event.item != concept.wand) return
            if (concept.wand?.type?.let { event.player.getCooldown(it) }!! > 0) return

            if (action == Action.LEFT_CLICK_BLOCK || action == Action.LEFT_CLICK_AIR) {
                psychic.consumeMana(concept.cost)
                val eyeLocation = esper.player.eyeLocation
                val projectile = FlameProjectile(false)
                psychic.launchProjectile(eyeLocation, projectile) // 투사체 발사
                projectile.velocity = eyeLocation.direction.multiply(concept.flameSpeed)
            }

            if (action == Action.RIGHT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR) {
                psychic.consumeMana(concept.cost)
                val eyeLocation = esper.player.eyeLocation
                val projectile = FlameProjectile(true)
                psychic.launchProjectile(eyeLocation, projectile) // 투사체 발사
                projectile.velocity = eyeLocation.direction.multiply(concept.flameSpeed)
            }

            esper.player.run {
                concept.wand?.let { setCooldown(it.type,concept.cooldownTime.toInt()) }
            }

        }

        @EventHandler
        @TargetEntity(KillerProvider::class)
        fun onPlayerKill(event: EntityDeathEvent) {
            triggers.remove(event.entity)
        }

        @EventHandler
        fun onPlayerDeath(event: PlayerDeathEvent) {
            triggers.clear()
        }
    }

    inner class FlameOnPlayer(val target : LivingEntity) {
        var tick = 0
        var count = 1
        fun update() {
            tick++
            for (i in 1..count) {
                val flameloc: Location = target.eyeLocation.clone()
                val scale = 1
                val dy = cos(tick.toDouble() * 0.01 + (PI*2/concept.maxFlame*i))
                val controlVal = sin(tick.toDouble() * 0.01 / 2 + (PI*2/concept.maxFlame*i))
                val dx = sin(tick.toDouble() * 0.01 + (PI*2/concept.maxFlame*i)) * controlVal
                val dz = cos(tick.toDouble() * 0.01 + (PI*2/concept.maxFlame*i)) * controlVal
                flameloc.x += dx * scale
                flameloc.y += dy * scale + 2
                flameloc.z += dz * scale
                target.eyeLocation.world.spawnParticle(Particle.FLAME, flameloc, 1, 0.0, 0.0, 0.0, 0.0)
            }
            if (count > concept.maxFlame) {
                target.psychicDamage(
                    Damage(concept.ignitionType, EsperStatistic.Companion.of(EsperAttribute.ATTACK_DAMAGE to concept.flameOverStackATK)),
                    esper.player.location,
                    concept.flameOverStackKnockback
                )
                count = concept.maxFlame
            }
            if (target.isDead) {
                triggers.remove(target)
            }
        }
        fun attack() {
            val attackData = concept.ignitionAtkEachCount[count-1]
            val damageAmount = attackData["ATK"]!! * esper.getAttribute(EsperAttribute.ATTACK_DAMAGE)
            target.psychicDamage(
                this@PowerOfFlame,
                concept.ignitionType,
                damageAmount,
                esper.player,
                esper.player.location,
                attackData["knockback"]!!
            )
            psychic.consumeMana(concept.flameCostPerTickForOne*count)
        }
    }

    private inner class FlameProjectile(val isIgnition: Boolean) : PsychicProjectile(200, concept.range) {

        override fun onMove(movement: Movement) {
            //실제 발사체의 이동
        }

        override fun onTrail(trail: Trail) {
            // trail.velocity는 월드간 이동하거나 움직이지 않을때 null이 할당됩니다.
            trail.velocity?.let { velocity ->
                val from = trail.from

                val length = velocity.normalizeAndLength() // normalize and return length

                // 직선 경로 관통 계산
                from.world.rayTrace(
                    from,
                    velocity, // normalized
                    length,
                    FluidCollisionMode.NEVER,
                    true,
                    1.0, // 발사체 크기
                    TargetFilter(esper.player) // Psychics 기본 적(hostile) 필터
                )?.let { rayTraceResult ->  // 관통 계산 결과
                    val hitEntity = rayTraceResult.hitEntity

                    if (hitEntity is LivingEntity) { // auto check null
                        if (isIgnition) {
                            if (hitEntity in triggers.keys) {
                                triggers[hitEntity]!!.count += 1
                            } else {
                                triggers[hitEntity] = FlameOnPlayer(hitEntity)
                            }
                        } else {
                            if (hitEntity in triggers.keys) {
                                val trigger = triggers[hitEntity]
                                hitEntity.psychicDamage(
                                    this@PowerOfFlame,
                                    concept.explosionType,
                                    concept.explosionAtkEachCount[trigger!!.count-1]["ATK"]!!*esper.getAttribute(EsperAttribute.ATTACK_DAMAGE),
                                    esper.player,
                                    from,
                                    concept.explosionAtkEachCount[trigger.count-1]["knockback"]!!
                                )
                                triggers.remove(hitEntity)
                            } else {
                                hitEntity.psychicDamage(
                                    concept.nonFlameExplosionDamage,
                                    from,
                                    concept.nonFlameExplosionKnockback
                                )
                            }
                            trail.to.world.spawnParticle(Particle.EXPLOSION_LARGE, trail.to, 1)
                        }
                    }

                    remove() // 발사체 제거
                }

                val to = trail.to
                if (isIgnition) {
                    to.world.spawnParticle(Particle.FLAME, to, 1) //파티클 소환
                } else {
                    to.world.spawnParticle(Particle.EXPLOSION_NORMAL, to, 1) //파티클 소환
                }
            }
        }
    }


    fun debuggingFunc() {
        //esper.player.sendActionBar("")
    }

    inner class Task : Runnable {
        private var tick = 0
        override fun run() { // Task: Calling per tick
            tick++
            debuggingFunc()

            triggers.values.forEach {
                it.update()
            }

            if (psychic.mana < 1.0) {
                triggers.clear()
                esper.player.sendActionBar("${ChatColor.RED}${ChatColor.BOLD}!! FLAME OUT !!")
            }

            if (tick < concept.dotDamagePeriodTicks) return
            tick = 0

            triggers.values.forEach {
                it.attack()
            }
        }
    }
}

class KillerProvider : EntityProvider<EntityDeathEvent> {
    override fun getFrom(event: EntityDeathEvent): Entity? {
        return event.entity.killer
    }
}