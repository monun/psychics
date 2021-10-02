package com.github.muqhc.psychics.ability.necromancer

import io.github.monun.psychics.PsychicProjectile
import io.github.monun.psychics.ActiveAbility.WandAction
import io.github.monun.psychics.attribute.EsperStatistic
import io.github.monun.psychics.damage.{Damage, DamageType}
import io.github.monun.psychics.scalasupport.ScalaDamageSupportKt.{scala_psychicDamage_aDamage, scala_psychicDamage_simple}
import io.github.monun.psychics.tooltip.TooltipBuilder
import io.github.monun.psychics.util.TargetFilter
import io.github.monun.psychics.{AbilityConcept, ActiveAbility, Channel, _}
import io.github.monun.tap.config.{Config, Name}
import io.github.monun.tap.fake.Trail
import kotlin.jvm.functions
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.{NamedTextColor, TextDecoration}
import org.bukkit.attribute.Attribute
import org.bukkit.block.Block
import org.bukkit.{Color, Material, util, _}
import org.bukkit.entity.{Entity, EntityType, LivingEntity, Mob, Monster}
import org.bukkit.event.{EventHandler, Listener}
import org.bukkit.event.player.{PlayerEvent, PlayerInteractEvent}
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.{PotionEffect, PotionEffectType}

import java.lang
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters._
import scala.math.{Pi, cos, sin}


@Name("necromancer")
class AbilityConceptNecromancer extends AbilityConcept {

    @Config
    var periodicDamage = new Damage(DamageType.RANGED,EsperStatistic.deserialize(
        Map("ATK" -> 0.8).asJava
    ))

    @Config
    var attackPeriodTicks = 13

    @Config
    var orbSpeed = 1.8

    @Config
    var orbGravity = 0.1

    @Config
    var orbSize = 1.0

    @Config
    var slowAmplifier = 3

    @Config
    var slowDurationTicks = 60

    @Config
    var deadAreaHoldingTicks = 200

    @Config
    var deadAreaMinMaxRange = List(1.2,6.0).asJava

    @Config
    var deadAreaIncreasePerTick = 0.13

    /**<h1><hr><font color="yellow">Deep Config</font></h1>*/
    var spawnEntityAtEachRange = Map(
        2.6 -> EntityType.ZOMBIE,
        5.7 -> EntityType.SKELETON,
        9.5 -> EntityType.BLAZE,
    )

    //region Set Common Init

    this setType AbilityType.ACTIVE
    this setDisplayName "Necromancer"

    this setCost 50
    this setCooldownTime 100

    this setCastingTime 500

    this setRange 64

    this setDamage new Damage(DamageType.RANGED,EsperStatistic.deserialize(
    Map("ATK" -> 4.0).asJava
    ))

    this setWand new ItemStack(Material.DIAMOND,1)

    this setSupplyItems List(getWand).asJava

    val predescription = List(
        "투사체가 떨어진 자리에 죽음의 지역을 만듭니다.",
        "   죽음의 지역은 지역안에 엔티티에게",
        "   지속적인 피해와 구속 효과를 줍니다.",
        "   죽음의 지역은 일정크기 까지 서서히 커집니다.",
        "",
        "투사체가 떨어진 자리에서 가장 가까운 몹과의 거리에 따라",
        "   소환되는 몬스터가 달라집니다. (너무 멀면 소환 안 됨.)",
        "",
        "해당 몬스터는 가장 가까운 엔티티를 타겟팅 합니다."
    )
    this setDescription predescription.map(text).asJava


    //endregion

    override def onRenderTooltip(tooltip: TooltipBuilder, stats: functions.Function1[_ >: EsperStatistic, lang.Double]): Unit = {
        tooltip.header(
            text().color(NamedTextColor.DARK_PURPLE).content("죽음의 지역 지속 피해 ").decorate(TextDecoration.BOLD)
                .append(
                    text().color(NamedTextColor.DARK_RED).append(text(periodicDamage.toString))
                ).build()
        )
    }
}

class AbilityNecromancer extends ActiveAbility[AbilityConceptNecromancer] with Listener {

    def thisFilter(filteringEntity: LivingEntity = null): LivingEntity => Boolean = (entity: LivingEntity) => new TargetFilter(esper.getPlayer,
        Bukkit.getScoreboardManager.getMainScoreboard.getEntryTeam(esper.getPlayer.name.toString)).test(entity) && entity != filteringEntity

    def spawnEntityAtEachRange: Map[Double, EntityType] = concept.spawnEntityAtEachRange

    def RangeGetters: Map[Double => Boolean, EntityType] =
        spawnEntityAtEachRange map {
            case (r,m) => ((x: Double) => x <= r) -> m
        }

    def esper: Esper = getEsper
    def concept: AbilityConceptNecromancer = getConcept

    val deadAreas: ListBuffer[DeadArea] = ListBuffer[DeadArea]()

    override def onInitialize(): Unit = {

    }

    override def onEnable() {
        psychic.runTaskTimer(new Task(), 0, 1)
    }

    override def onCast(event: PlayerEvent, action: WandAction, target: Any): Unit = {

        psychic consumeMana concept.getCost
        this setCooldownTime concept.getCooldownTime

        val location = esper.getPlayer.getEyeLocation
        val projectile = new DeadOrbProjectile()

        psychic.launchProjectile(location, projectile)
        projectile setVelocity location.getDirection.multiply(concept.orbSpeed)

        location.getWorld.playSound(location, Sound.ENTITY_BLAZE_SHOOT, 1.0F, 0.1F)

    }

    class DeadArea(location: Location, holdingTicks: Int, minrange : Double, maxrange : Double, increasePerTick : Double, summoningMonster : EntityType) {
        var tick: Int = holdingTicks
        var range: Double = minrange

        lazy val processes: ListBuffer[() => Any] = ListBuffer[() => Any](
            rangeEffect,
            ()=>{
                if (range < maxrange) range += increasePerTick
                else promises += (() => processes remove 1)
            },
                ()=>{ if (tick % concept.attackPeriodTicks == 0) promises += (()=>attack(concept.periodicDamage, 0.01)) }
        )

        val promises: ListBuffer[() => Any] = ListBuffer[() => Any]()

        promises += (()=>attack(concept.getDamage, concept.getKnockback))

        def rangeEffect(): Unit = {
            var effectloc: Location = location.clone()
            var x = tick*Pi*0.53
            effectloc setX (effectloc.getX + sin(x) * range)
            effectloc setY  (effectloc.getY + 2)
            effectloc setZ (effectloc.getZ + cos(x) * range)
            location.getWorld.spawnParticle(
                    Particle.DRAGON_BREATH,effectloc,4,0.0,0.1,0.0,0.03)
            effectloc = location.clone()
            x = -x
            effectloc setX (effectloc.getX + sin(x) * range)
            effectloc setY  (effectloc.getY + 2)
            effectloc setZ (effectloc.getZ + cos(x) * range)
            location.getWorld.spawnParticle(
                Particle.DRAGON_BREATH,effectloc,4,0.0,0.1,0.0,0.03)
        }

        def getUndead: Mob = {
            summoningMonster match {
                case entityType: EntityType if (entityType != null) =>
                    val entity = location.getWorld.spawnEntity(location, summoningMonster).asInstanceOf[Mob]
                    processes += (() => {
                        val nearestKV = nearest(entity.getLocation,20,thisFilter(entity))
                        if (nearestKV == null) return null
                        entity setTarget nearestKV._2
                    })
                    entity
                case _ => null
            }
        }

        val undead: LivingEntity = getUndead
        if (undead != null) undead.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.37)


        def update(): Unit = {
            tick = tick - 1
            if (tick < 0) {
                deadAreas -= this
                undead.remove()
            }
            processes foreach (_ ())
            promises foreach (_ ())
            promises.clear()
        }

        def attack(damage: Damage, knockback : Double): Unit = {
            nearbyentity(location,range,thisFilter(undead)) foreach (target=>{
                scala_psychicDamage_aDamage(AbilityNecromancer.this, target, damage, location, knockback)
                val potionEffect = new PotionEffect(
                        PotionEffectType.SLOW,
                        concept.slowDurationTicks,
                        concept.slowAmplifier,
                false,
                false,
                false
                )
                target.addPotionEffect(potionEffect)
            })
        }

    }

    class DeadOrbProjectile() extends PsychicProjectile(1200, concept.getRange) {
        override def onPreUpdate(): Unit = {
            this setVelocity new util.Vector(getVelocity.getX, getVelocity.getY - concept.orbGravity, getVelocity.getZ)
        }

        override def onTrail(trail: Trail): Unit = {
            val velocity = trail.getVelocity
            val length = velocity.normalize().length()

            if (length > 0.0) {
                val start = trail.getFrom
                val world = start.getWorld

                world.spawnParticle(
                        Particle.DRAGON_BREATH,
                        trail.getTo,
                32,
                0.0,
                0.0,
                0.0,
                0.013,
                null,
                true
                )

                val result = world.rayTrace(
                        start, velocity, length, FluidCollisionMode.NEVER, true, concept.orbSize / 2.0,
                new TargetFilter(esper.getPlayer,Bukkit.getScoreboardManager.getMainScoreboard.getEntryTeam(esper.getPlayer.name.toString))
                )

                if (result == null) return ;

                val hitLocation = result.getHitPosition.toLocation(world)

                remove()

                world.spawnParticle(
                        Particle.DRAGON_BREATH,
                        hitLocation,
                32,
                0.2,
                1.0,
                0.2,
                4.0,
                null,
                true
                )

                val nearestKV = nearest(hitLocation,spawnEntityAtEachRange.keys.max)
                def willSpawnEntity(): EntityType = {
                    if (nearestKV == null) return null
                    for (getter <- RangeGetters) {
                        if (getter._1(nearestKV._1)) return getter._2
                    }
                    null
                }
                val areaLoc = hitLocation.clone()
                areaLoc setY hitLocation.getY + 2
                deadAreas += new DeadArea(hitLocation, concept.deadAreaHoldingTicks,
                        concept.deadAreaMinMaxRange.get(0), concept.deadAreaMinMaxRange.get(1), concept.deadAreaIncreasePerTick,
                        willSpawnEntity())


                world.playSound(hitLocation, Sound.ENTITY_ENDERMAN_SCREAM, 2.0F, 0.1F)

            }
        }
    }

    class Task extends Runnable {
        override def run(): Unit = {
            deadAreas foreach (area => area.update())
        }
    }

    def nearbyentity(location: Location, range: Double, filter: LivingEntity => Boolean = thisFilter()): List[LivingEntity] = {
        val nearbyRaw = location.getWorld.getEntities
        var nearby = List[Entity]()
        var i = 0
        while (nearbyRaw.size() > i) {
            nearby = nearbyRaw.get(i) :: nearby
            i += 1
        }
        nearby collect {
            case e: LivingEntity => e
        } filter (e => location.distance(e.getLocation()) <= range) filter filter
    }
    def nearbyentityMap(location: Location, range: Double, filter: LivingEntity => Boolean = thisFilter()): Map[Double,LivingEntity] = {
        val nearbyRaw = location.getWorld.getEntities
        var nearby = List[Entity]()
        var i = 0
        while (nearbyRaw.size() > i) {
            nearby = nearbyRaw.get(i) :: nearby
            i += 1
        }
        (nearby collect {
            case e: LivingEntity => e
        } filter (e => location.distance(e.getLocation()) <= range) filter filter map
            (e => location.distance(e.getLocation()) -> e)).toMap
    }

    def nearest(location: Location, range: Double, filter: LivingEntity => Boolean = thisFilter()): (Double, LivingEntity) = {
        val nearby = nearbyentityMap(location, range, filter)
        if (nearby.keys.isEmpty) return null
        nearby.keys.min -> nearby(nearby.keys.min)
    }

}