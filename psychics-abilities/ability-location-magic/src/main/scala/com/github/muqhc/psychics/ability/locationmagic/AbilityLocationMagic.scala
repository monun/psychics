package com.github.muqhc.psychics.ability.locationmagic

import io.github.monun.psychics.ActiveAbility.WandAction
import io.github.monun.psychics.scalasupport.ScalaFireworkSupportKt.playFirework
import io.github.monun.psychics._
import io.github.monun.tap.config.{Config, Name}
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.{Entity, LivingEntity}
import org.bukkit.event.player.PlayerEvent
import org.bukkit.inventory.ItemStack
import org.bukkit._

import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters._


@Name("location-magic")
class AbilityConceptLocationMagic extends AbilityConcept {

    @Config
    var costPerTargetCount = 10

    @Config
    var cooldownTimePerTargetCount = 1000

    //region Set Common Init

    this setType AbilityType.ACTIVE
    this setDisplayName "Location Magic"

    this setCost 20
    this setCooldownTime 1000

    this setCastingTime 1000

    this setRange 64

    this setWand new ItemStack(Material.STICK,1)

    this setSupplyItems List(getWand).asJava

    val predescription = List(
        "시전시간 동안 지정된 엔티티들의 위치들을",
        "지정순으로 각 뒷순서의 위치로 바꾸어 버립니다.",
        "",
        "소모 마나와 쿨타임은 타겟팅 수에 비례합니다."
    )
    this setDescription predescription.map(text).asJava


    //endregion

}

class AbilityLocationMagic extends ActiveAbility[AbilityConceptLocationMagic] {

    def esper: Esper = getEsper
    def concept: AbilityConceptLocationMagic = getConcept

    val effect: FireworkEffect = FireworkEffect.builder().withColor(Color.PURPLE).`with`(FireworkEffect.Type.BALL_LARGE).build()

    def playPsychicEffect(target: LivingEntity): Unit = {
        playFirework(target.getWorld,target.getLocation(),effect,128.0)
    }


    val targets : ListBuffer[LivingEntity] = ListBuffer.empty
    def getTargetsLocations: ListBuffer[Location] = targets map { e: LivingEntity => e.getLocation }

    def getTarget: Entity = {
        val player = esper.getPlayer
        val start = player.getEyeLocation
        val world = start.getWorld

        world.rayTrace(
            start,
            start.getDirection,
            concept.getRange,
            FluidCollisionMode.NEVER,
            true,
            0.5,
            (t: Entity) => t != player
        ).getHitEntity
    }


    override def onInitialize(): Unit = {

    }


    override def onChannel(channel: Channel): Unit = {
        getTarget match {
            case target: LivingEntity => if (!(targets contains target)) targets += target
            case _ =>
        }
        targets foreach (e=>if(e != null)e.getWorld.spawnParticle(Particle.PORTAL,e.getLocation,10,0,0,0,0.03))
    }

    override def onCast(event: PlayerEvent, action: WandAction, target: Any): Unit = {
        if (targets.isEmpty) return ;
        if ((concept.costPerTargetCount * targets.length + concept.getCost) > psychic.getMana)
            esper.getPlayer.sendActionBar(text("too much targets! (not Enough Mana.)") color NamedTextColor.DARK_PURPLE)

        val targetsLocations = getTargetsLocations
        targetsLocations += targetsLocations.head
        targetsLocations remove 0

        val targetsWithLocations = targets zip targetsLocations
        targetsWithLocations foreach {
            case (entity, location: Location) =>
                entity teleport location
                playPsychicEffect(entity)
        }

        psychic consumeMana (concept.costPerTargetCount * targets.length + concept.getCost)
        this setCooldownTime (concept.cooldownTimePerTargetCount * targets.length + concept.getCooldownTime)

        targets.clear()

    }

}