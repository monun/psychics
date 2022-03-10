package io.github.sincostan1122.psychics.ability.satellite


import io.github.monun.psychics.Ability
import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.attribute.EsperAttribute
import io.github.monun.psychics.attribute.EsperStatistic
import io.github.monun.psychics.damage.Damage
import io.github.monun.psychics.damage.DamageType
import io.github.monun.psychics.tooltip.TooltipBuilder
import io.github.monun.psychics.util.hostileFilter
import io.github.monun.tap.config.Config
import io.github.monun.tap.config.Name
import io.github.monun.tap.fake.FakeEntity
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerEvent
import org.bukkit.event.player.PlayerVelocityEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.BoundingBox


@Name("satellite")
class AbilityConceptSatellite : AbilityConcept() {


    init {

        damage = Damage.of(DamageType.RANGED, EsperStatistic.of(EsperAttribute.ATTACK_DAMAGE to 2.0))
        displayName = "위성"

        description = listOf(
            text("한개의 위성이 주변을 돕니다."),
            text("위성에 닿은 적은 원거리 피해를 입습니다."),

        )
    }

}
class AbilitySatellite : Ability<AbilityConceptSatellite>(), Listener {
    var stloc = 1
    var locx = 0.0
    var locz = 0.0
    var firstst: FakeEntity? = null

    override fun onEnable() {
        psychic.registerEvents(this)
        firstst = psychic.spawnFakeEntity(esper.player.location, Shulker::class.java).apply {
            updateMetadata<Shulker> {
                isVisible = true
            }
        }
        psychic.runTaskTimer({
            if (stloc == 12) {
                stloc = 1
            }
            else{
                stloc++
            }
            movest()
            damagef()

        },0L, 1L)
    }
    fun damagef() {
        val loc = firstst?.location
        val box = loc?.let { BoundingBox.of(it, 1.0, 1.5, 1.0) }
        val hostiles = box?.let { loc?.world.getNearbyEntities(it, esper.player.hostileFilter()) }

        if (hostiles != null) {
            for (entity in hostiles) {
                (entity as LivingEntity).psychicDamage()
            }
        }
    }
    fun movest() {
        if(stloc == 1) {
            locx = 0.0
            locz = 5.0
        }
        else if(stloc == 2) {
            locx = 2.5
            locz = 4.33
        }
        else if(stloc == 3) {
            locx = 4.33
            locz = 2.5
        }
        else if(stloc == 4) {
            locx = 5.0
            locz = 0.0
        }
        else if(stloc == 5) {
            locx = 4.33
            locz = -2.5
        }
        else if(stloc == 6) {
            locx = 2.5
            locz = -4.33
        }
        else if(stloc == 7) {
            locx = 0.0
            locz = -5.0
        }
        else if(stloc == 8) {
            locx = -2.5
            locz = -4.33
        }
        else if(stloc == 9) {
            locx = -4.33
            locz = -2.5
        }
        else if(stloc == 10) {
            locx = -5.0
            locz = 0.0
        }
        else if(stloc == 11) {
            locx = -4.33
            locz = 2.5
        }
        else if(stloc == 12) {
            locx = -2.5
            locz = 4.33
        }

        firstst?.moveTo(stlocz(locx, locz).apply{y += 0.5})
    }
    private fun stlocx(vx : Double) = esper.player.location.apply{x += vx}
    private fun stlocz(vx : Double, vz : Double) = stlocx(vx).apply{z += vz}

    override fun onDisable() {
        // 제거
        firstst?.let { fakeEntity ->
            fakeEntity.remove()
            this.firstst = null
        }
    }


}