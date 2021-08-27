package com.github.muqhc.psychics.ability.firetotem

import io.github.monun.psychics.*
import io.github.monun.psychics.attribute.EsperAttribute
import io.github.monun.psychics.attribute.EsperStatistic
import io.github.monun.psychics.damage.DamageType
import io.github.monun.psychics.damage.psychicDamage
import io.github.monun.psychics.item.isPsychicbound
import io.github.monun.psychics.tooltip.TooltipBuilder
import io.github.monun.psychics.tooltip.template
import io.github.monun.tap.config.Config
import io.github.monun.tap.config.Name
import io.github.monun.tap.event.EntityProvider
import io.github.monun.tap.event.TargetEntity
import io.github.monun.tap.fake.FakeEntity
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.Component.text
import org.bukkit.*
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.player.PlayerEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.util.EulerAngle
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin


@Name("totem")
class FireTotemConcept : AbilityConcept() {

    @Config
    var totemCostPerTick = 0.6

    @Config
    var totemTile = ItemStack(Material.RED_BANNER)

    @Config
    var totemRange = 6.0

    @Config
    var totemKnockback = 0.2

    @Config
    var defaultDamage = 1.0

    @Config
    var damagePerTargetCount = 0.5

    @Config
    var totemAttackPeriodTick = 10

    @Config
    var totemDamageType = DamageType.FIRE

    @Config
    var takeBackManaPerTotemTick = 0.2

    @Config
    var takeBackCooldownTicks = 400

    @Config
    var atkWithPlayer = 0.5

    @Config
    var showDamageMessage = true

    init {
        type = AbilityType.ACTIVE // ability type
        displayName = "MyAbility" // displayName
        cooldownTime = 20 // cooldownTicks
        cost = 10.0 // mana cost
        wand = ItemStack(Material.BLAZE_ROD,1)
        wand?.apply {
            itemMeta.apply {
                isPsychicbound = true
                setDisplayName("${ChatColor.BOLD}${ChatColor.WHITE}Wand : ${ChatColor.GOLD}FireTotem")
            }
        }
        supplyItems = listOf(
            wand!!
        )

        /*  val totemDamageAmount = concept.run { defaultDamage + ((damagePerTargetCount * entitiesInTotem.count())) }
            val damageAmount = totemDamageAmount + ( esper.getAttribute(EsperAttribute.ATTACK_DAMAGE) * concept.atkWithPlayer )  */

        var predescription = listOf(
            "${ChatColor.BOLD}${ChatColor.AQUA}우클릭 ${ChatColor.RESET}${ChatColor.GRAY} 토템 설치",
            "우클릭한 위치에 토템을 소환합니다.",
            "갯수제한 없이 소환 할 수 있습니다.(여러 개 소환 가능)",
            "",
            "토템들은 마나를 각각 지속적으로 소량의 마나를 소모 합니다.",
            "토템들은 범위 안쪽에 지속적으로 대미지를 입힙니다.",
            "토템들에게 영향을 받는 몹 수가 많을 수록 더 큰 데미지를 줍니다.",
            "",
            "${ChatColor.BOLD}${ChatColor.AQUA}좌클릭 ${ChatColor.RESET}${ChatColor.GRAY} 토템 전부 회수",
            "각 토템들이 소환되있던 시간에 따라 보통의 마나를 돌려받습니다.",
            "",
            "※${ChatColor.GRAY}만약 마나를 모두 소진한다면 토템들이 ${ChatColor.BOLD}${ChatColor.RED}자동으로 ${ChatColor.RESET}${ChatColor.GRAY}회수됩니다.",
        )

        description = ArrayList<Component>().apply {
            predescription.forEach { add(text(it)) }
        }
    }
    override fun onRenderTooltip(tooltip: TooltipBuilder, stats: (EsperStatistic) -> Double) {
        tooltip.template("totemDamage",stats(EsperStatistic.Companion.of(EsperAttribute.ATTACK_DAMAGE to atkWithPlayer))) // 툴팁 템플릿 추가
    }
}

class FireTotem : ActiveAbility<FireTotemConcept>() {

    lateinit var totems : LinkedList<Totem>
    lateinit var entitiesInTotem : MutableList<LivingEntity>

    init {
        targeter = {
            esper.player.eyeLocation
        }
    }

    override fun onCast(event: PlayerEvent, action: WandAction, target: Any?) {
        // target = targetEntity
    }

    fun takeBack() {
        esper.player.playSound(esper.player.eyeLocation,Sound.ENTITY_SPLASH_POTION_BREAK,1f,1f)

        val cooldown = concept.takeBackCooldownTicks.toInt()
        esper.player.run {
            concept.wand?.let { setCooldown(it.type,cooldown) }
        }

        onDisable()
    }

    override fun onAttach() {
        totems = LinkedList()
        entitiesInTotem = mutableListOf()
        psychic.mana = esper.getAttribute(EsperAttribute.MANA)
    }

    override fun onEnable() {

        psychic.registerEvents(EventListener())
        psychic.runTaskTimer(Task(), 0L, 1L)

    }

    override fun onDisable() {
        totems.run {
            forEach { it.remove() }
            clear()
        }
        entitiesInTotem.clear()
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
                if (totems.count() == 0){
                    esper.player.sendActionBar("${ChatColor.RED}${ChatColor.BOLD}NO TOTEM")
                    return
                }

                var takeBackMana = 0.0
                totems.forEach { takeBackMana += it.tick*concept.takeBackManaPerTotemTick }
                esper.player.sendActionBar("${ChatColor.GREEN}${ChatColor.BOLD} GET BACK ${takeBackMana.roundToInt()} mana")
                takeBack()
            }

            if (action == Action.RIGHT_CLICK_BLOCK) { // Event: Right Click Block
                esper.player.playSound(esper.player.eyeLocation,Sound.BLOCK_NETHERITE_BLOCK_PLACE,1f,1f)

                psychic.consumeMana(concept.cost)

                val cooldown = concept.cooldownTime.toInt()
                esper.player.run {
                    concept.wand?.let { setCooldown(it.type,cooldown) }
                }

                val location: Location = event.clickedBlock?.location!!
                location.x += 0.5
                location.y += 0
                location.z += 0.5

                totems.add(Totem(location.clone()))

            }

        }

        @EventHandler
        fun onPlayerSneak(event: PlayerToggleSneakEvent) { // Event: Sneak
            if (event.isSneaking) { // Event: Down Sneak

            } else { // Event: Release Sneak

            }
        }

        @EventHandler
        @TargetEntity(KillerProvider::class)
        fun onPlayerKill(event: EntityDeathEvent) { // Event: Killing Entity at Entity
            // Empty
        }
    }


    inner class Totem (val location: Location) {
        var tick = 0

        var entities: Set<FakeEntity> = setOf(
            spawnArmorStandForTotem(0.0),
            spawnArmorStandForTotem(PI),
            spawnArmorStandForTotem(PI/2),
            spawnArmorStandForTotem(PI+(PI/2))
        )


        lateinit var entityNear: Array<LivingEntity>

        fun updata() {
            val flameloc: Location = location.clone()
            flameloc.x = flameloc.x + sin(tick.toDouble()*2) * concept.totemRange
            flameloc.y += 2
            flameloc.z = flameloc.z + cos(tick.toDouble()*2) * concept.totemRange
            location.world.spawnParticle(Particle.FLAME,flameloc,1,0.0,0.0,0.0,0.0)

            tick++
            entityNear = nearbyentity(location,concept.totemRange)
            entitiesInTotem.addAll(entityNear)
            psychic.consumeMana(concept.totemCostPerTick)
        }

        fun attack(damageAmount : Double) {
            entityNear.forEach {
                it.psychicDamage(
                    this@FireTotem,
                    concept.totemDamageType,
                    damageAmount,
                    esper.player,
                    location,
                    concept.totemKnockback
                )
                it.fireTicks = 11
            }
        }

        fun remove() {
            psychic.mana += tick*concept.takeBackManaPerTotemTick
            entities.forEach {
                it.remove()
            }
            entities = setOf()
        }

        fun spawnArmorStandForTotem(angleY: Double): FakeEntity {
            return psychic.spawnFakeEntity(location,
                ArmorStand::class.java
            ).apply {
                updateMetadata<ArmorStand> {
                    isMarker = true
                    isVisible = false
                    headPose = EulerAngle(0.0, angleY, 0.0)
                }
                updateEquipment {
                    helmet = concept.totemTile
                }
            }
        }

        fun showDamage(damageAmount : Double) {
            esper.player.sendActionBar("${ChatColor.RED}${ChatColor.BOLD}Totems Damage $damageAmount")
        }
    }

    fun debuggingFunc() {
        //esper.player.sendActionBar("")
    }

    inner class Task : Runnable {
        private var tick = 0
        override fun run() { // Task: Calling per tick
            debuggingFunc()

            if (psychic.mana < 1) {
                var takeBackMana = 0.0
                totems.forEach { takeBackMana += it.tick*concept.takeBackManaPerTotemTick }
                esper.player.sendActionBar("${ChatColor.RED}${ChatColor.BOLD} GET BACK ${takeBackMana.roundToInt()} mana")
                takeBack()
                return
            }

            tick++
            entitiesInTotem.clear()
            totems.forEach {
                it.updata()
            }
            if (tick < concept.totemAttackPeriodTick) return
            tick = 0

            val totemDamageAmount = concept.run { defaultDamage + ((damagePerTargetCount * entitiesInTotem.count())) }
            val damageAmount = totemDamageAmount + ( esper.getAttribute(EsperAttribute.ATTACK_DAMAGE) * concept.atkWithPlayer )

            totems.forEach {
                it.attack(damageAmount)
            }

            if (concept.showDamageMessage) totems.last.showDamage(damageAmount)
        }
    }

    fun nearbyentity(location: Location, range: Double): Array<LivingEntity> {
        val entity: MutableList<LivingEntity> = mutableListOf()
        for (e in location.world.entities) if (location.distance(e.location) <= range) if (e is LivingEntity) entity.add(e)
        entity.remove(esper.player)
        entity.removeIf {
            it is ArmorStand
        }
        return entity.toTypedArray()
    }
}



class KillerProvider : EntityProvider<EntityDeathEvent> {
    override fun getFrom(event: EntityDeathEvent): Entity? {
        return event.entity.killer
    }
}
