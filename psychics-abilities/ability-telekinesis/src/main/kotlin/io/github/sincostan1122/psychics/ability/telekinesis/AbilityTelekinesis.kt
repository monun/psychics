package io.github.sincostan1122.psychics.ability.telekinesis


import io.github.monun.psychics.ActiveAbility
import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.attribute.EsperAttribute
import io.github.monun.psychics.attribute.EsperStatistic
import io.github.monun.psychics.damage.Damage
import io.github.monun.psychics.damage.DamageType
import io.github.monun.psychics.damage.psychicDamage
import io.github.monun.psychics.util.TargetFilter
import io.github.monun.tap.config.Config
import io.github.monun.tap.config.Name
import io.github.monun.tap.fake.FakeEntity
import net.kyori.adventure.text.Component.text
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.*
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.util.BoundingBox
import kotlin.math.max


@Name("Telekinesis")
class AbilityConceptTelekinesis : AbilityConcept() {


    init {
        range = 6.0
        cost = 50.0
        durationTime = 5000L
        cooldownTime = 1000L
        wand = ItemStack(Material.EMERALD)
        damage = Damage.of(DamageType.BLAST, EsperStatistic.of(EsperAttribute.ATTACK_DAMAGE to 5.0))
        displayName = "염력"

        description = listOf(
            text("구체를 소환하여 최대 ${durationTime / 1000.0}초동안 앞으로 나아가게 합니다."),
            text("재사용하거나 지속시간이 끝날 시 구체의 위치에 폭팔을 일으킵니다.")
        )
    }
}
class AbilityTelekinesis : ActiveAbility<AbilityConceptTelekinesis>(), Listener {
    private var tnt: TNT? = null
    var playerdt = 0.0
    var fakeEntity: FakeEntity? = null
    var isskillon = 0


    override fun onEnable() {
        psychic.registerEvents(this)
        fakeEntity = psychic.spawnFakeEntity(esper.player.location, ShulkerBullet::class.java).apply {
            updateMetadata<ShulkerBullet> {
                isVisible = false
            }
        }
        val world = esper.player.world
        psychic.runTaskTimer({
            fakeEntity?.moveTo(tntloc())
            if (isskillon == 1) {
                fakeEntity!!.isVisible = true
                playerdt += 1
            }

            if (durationTime == 0L) {
                if (isskillon == 1) {
                    fakeEntity!!.isVisible = false
                    cooldownTime = concept.cooldownTime
                    isskillon = 0
                    tnt = TNT(tntloc())
                    destroy()


                }


            }
        }, 0L, 1L)
    }
    override fun onDisable() {
        // 제거
        fakeEntity?.let { fakeEntity ->
            fakeEntity.remove()
            this.fakeEntity = null
        }
    }
    private fun destroy() {
        tnt?.run {


            remove()
            tnt = null

            val location = tntloc()
            val world = location.world

            val r = max(1.0, concept.range - 2.0)
            world.spawnParticle(Particle.EXPLOSION_HUGE, location, (r * r).toInt(), r, r, r, 0.0, null, true)
            world.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 2.0F, 1.0F)

            val damage = concept.damage!!
            var amount = esper.getStatistic(damage.stats)


            val knockback = concept.knockback

            val box = BoundingBox.of(location, r, r, r)
            world.getNearbyEntities(box, TargetFilter(esper.player)).forEach { enemy ->
                if (enemy is LivingEntity) {
                    enemy.psychicDamage(this@AbilityTelekinesis, damage.type, amount, esper.player, location, knockback)
                }
            }



        }
    }
    override fun onCast(event: PlayerEvent, action: WandAction, target: Any?) {
        val concept = concept



        if(isskillon == 0) {
            playerdt = 0.0
            psychic.consumeMana(concept.cost)
            durationTime = concept.durationTime
            isskillon = 1
            fakeEntity!!.isVisible = true
        }
        else if(isskillon == 1) {
            tnt = TNT(tntloc())
            destroy()
            cooldownTime = concept.cooldownTime

            fakeEntity!!.isVisible = false
            isskillon = 0
        }


    }



    private fun tntloc(): Location {
        return esper.player.eyeLocation.apply {
            add(direction.multiply(playerdt))
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

        fun onUpdate(location: Location) {
            stand.moveTo(location)
            location.y += 0.9

            val maxFuseDistance = 5.0
            val durationTime = 1L
            val maxDurationTime = 1L
            val r = (durationTime.toDouble() / maxDurationTime.toDouble())

            location.y += r * maxFuseDistance
            location.world.spawnParticle(Particle.FLAME, location, 1, 0.0, 0.0, 0.0, 0.025, null, true)
        }

        fun remove() {
            tnt.remove()
            stand.remove()
        }
    }


}
