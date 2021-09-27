package com.github.muqhc.psychics.ability.samplescala

import io.github.monun.psychics.ActiveAbility.WandAction
import io.github.monun.psychics.attribute.EsperStatistic
import io.github.monun.psychics.damage.{Damage, DamageType}
import io.github.monun.psychics.scalasupport.ScalaDamageSupportKt.scala_psychicDamage_simple
import io.github.monun.psychics.scalasupport.ScalaFireworkSupportKt.playFirework
import io.github.monun.psychics.util.TargetFilterKt.hostileFilter
import io.github.monun.psychics.{AbilityConcept, ActiveAbility, Channel, _}
import io.github.monun.tap.config.{Config, Name}
import net.kyori.adventure.text.Component.text
import org.bukkit.{Color, FireworkEffect, Material, _}
import org.bukkit.entity.{Entity, LivingEntity}
import org.bukkit.event.block.Action
import org.bukkit.event.{EventHandler, Listener}
import org.bukkit.event.player.{PlayerEvent, PlayerInteractEvent}
import org.bukkit.inventory.ItemStack

import java.util
import scala.jdk.CollectionConverters._


@Name("sample-scala")
class AbilityConceptSampleScala extends AbilityConcept {

    @Config
    var sampleConfigNumber = 0.0

    @Config
    var sampleConfigList: util.List[Int] = List(0,1,2).asJava

    @Config
    var sampleConfigMapList: util.List[util.Map[String, Int]] = List(Map("a"->1).asJava,Map("b"->2).asJava).asJava

    //region Set Common Init

    this setType AbilityType.ACTIVE
    this setDisplayName "Sample for Scala Ability"

    this setCost 10
    this setCooldownTime 5000

    this setCastingTime 1000

    this setRange 64

    this setDamage new Damage(DamageType.RANGED,EsperStatistic.deserialize(
    Map("ATK" -> 4.0).asJava
    ))

    this setWand new ItemStack(Material.STICK,1)

    this setSupplyItems List(getWand).asJava

    val predescription = List(
    "지정한 대상에게 폭발을 일으킵니다."
    )
    this setDescription predescription.map(text).asJava


    //endregion

}

class AbilitySampleScala extends ActiveAbility[AbilityConceptSampleScala] with Listener {

    def esper: Esper = getEsper
    def concept: AbilityConceptSampleScala = getConcept

    val effect: FireworkEffect = FireworkEffect.builder().withColor(Color.RED).`with`(FireworkEffect.Type.BURST).build()


    def playPsychicEffect(target: LivingEntity): Unit = {
        playFirework(target.getWorld,target.getLocation(),effect,128.0)
    }

    override def onInitialize(): Unit = {
        this setTargeter (() => {
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
                hostileFilter(player)
            ).getHitEntity
        })
    }

    override def onCast(event: PlayerEvent, action: WandAction, target: Any): Unit = {

        if (!target.isInstanceOf[LivingEntity]) return ;

        val concept = getConcept

        psychic consumeMana concept.getCost
        this setCooldownTime concept.getCooldownTime

        scala_psychicDamage_simple(this, target.asInstanceOf[LivingEntity])
        playPsychicEffect(target.asInstanceOf[LivingEntity])

    }

}