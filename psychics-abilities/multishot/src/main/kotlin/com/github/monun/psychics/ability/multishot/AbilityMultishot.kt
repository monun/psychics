package com.github.monun.psychics.ability.multishot

import com.github.monun.psychics.Ability
import com.github.monun.psychics.AbilityConcept
import com.github.monun.psychics.AbilityType
import com.github.monun.psychics.attribute.EsperAttribute
import com.github.monun.psychics.attribute.EsperStatistic
import com.github.monun.psychics.damage.Damage
import com.github.monun.psychics.damage.DamageType
import com.github.monun.psychics.tooltip.TooltipBuilder
import com.github.monun.psychics.tooltip.addStats
import com.github.monun.tap.config.Config
import com.github.monun.tap.event.EntityProvider
import com.github.monun.tap.event.TargetEntity
import com.github.monun.tap.math.toRadians
import org.bukkit.Location
import org.bukkit.entity.AbstractArrow
import org.bukkit.entity.Arrow
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.SpectralArrow
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.potion.PotionType
import org.bukkit.util.Vector

class AbilityMultishotConcept : AbilityConcept() {
    @Config
    var multishot = 2

    @Config
    var arrowAngle = 2.5

    @Config
    var damagePerPower = Damage(DamageType.RANGED, EsperStatistic.of(EsperAttribute.ATTACK_DAMAGE to 0.1))

    init {
        type = AbilityType.ACTIVE
        displayName = "멀티샷"
        damage = Damage(
            DamageType.RANGED, EsperStatistic.of(
                EsperAttribute.ATTACK_DAMAGE to 0.5
            )
        )
        description = listOf(
            "활을 사용 시 추가로 화살을 발사합니다.",
            "화살은 적에게 적중 시 피격 후 무적시간을 무시하고",
            "<damage>의 피해를 입힙니다.",
            "힘 인챈트당 <damage-per-power>의 추가피해를 입힙니다."
        )
    }

    override fun onRenderTooltip(tooltip: TooltipBuilder, stats: (EsperStatistic) -> Double) {
        tooltip.addTemplates("damage-per-power" to stats(damagePerPower.stats))
    }
}

class AbilityMultishot : Ability<AbilityMultishotConcept>(), Listener {
    override fun onEnable() {
        psychic.registerEvents(this)
    }

    @EventHandler(ignoreCancelled = true)
    fun onPlayerShootBow(event: EntityShootBowEvent) {
        val arrow = event.projectile.let { if (it !is Arrow) return; it }
        val location = arrow.location
        val velocity = arrow.velocity
        val speed = velocity.length().toFloat()

        for (i in 0 until concept.multishot) {
            val angle = (i.inc() * concept.arrowAngle).toRadians()
            psychic.runTask({
                multishot(arrow, location, angle, speed)
            }, (i.inc() * 1).toLong())
        }
    }

    @TargetEntity(EntityProvider.EntityDamageByEntity.Shooter::class)
    @EventHandler(ignoreCancelled = true)
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        // TODO 데미지 계산식 추가
    }

    @TargetEntity(EntityProvider.ProjectileHit.Shooter::class)
    @EventHandler(ignoreCancelled = true)
    fun onProjectileHit(event: ProjectileHitEvent) {
        if (event.entity is Arrow) {
            event.hitEntity?.let {
                if (it is LivingEntity) {
                    it.noDamageTicks = 0
                }
            }
        }
    }

    private fun multishot(origin: Arrow, loc: Location, angle: Double, speed: Float) {
        val world = loc.world
        val yAngle = loc.yaw.toDouble().toRadians()
        val xAngle = -loc.pitch.toDouble().toRadians()

        fun Vector.rotate(): Vector {
            rotateAroundX(xAngle).rotateAroundY(yAngle)
            return this
        }

        val c = origin.javaClass
        val left = Vector(0.0, 0.0, 1.0).rotateAroundY(angle).rotate()
        val right = Vector(0.0, 0.0, 1.0).rotateAroundY(-angle).rotate()

        world.spawnArrow(loc, left, speed, 0.0F, c).copyMetadata(origin)
        world.spawnArrow(loc, right, speed, 0.0F, c).copyMetadata(origin)
    }
}

private fun Arrow.copyMetadata(origin: Arrow) {
    isCritical = origin.isCritical
    origin.basePotionData.let { potionData ->
        val type = potionData.type
        if (type != PotionType.UNCRAFTABLE) {
            basePotionData = potionData
        }
    }

    if (origin.hasCustomEffects()) {
        val customEffects = origin.customEffects
        customEffects.forEach { addCustomEffect(it, false) }
    }

    if (this is SpectralArrow && origin is SpectralArrow ) {
        glowingTicks = origin.glowingTicks
    }
    shooter = origin.shooter
    damage = origin.damage
    pickupStatus = AbstractArrow.PickupStatus.CREATIVE_ONLY
    pierceLevel = origin.pierceLevel
}