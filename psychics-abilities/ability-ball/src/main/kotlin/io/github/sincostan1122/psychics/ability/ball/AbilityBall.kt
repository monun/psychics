package io.github.sincostan1122.psychics.ability.ball



import io.github.monun.psychics.ActiveAbility
import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.attribute.EsperAttribute
import io.github.monun.psychics.attribute.EsperStatistic
import io.github.monun.psychics.damage.Damage
import io.github.monun.psychics.damage.DamageType
import io.github.monun.psychics.util.hostileFilter
import io.github.monun.tap.config.Name
import io.github.monun.tap.fake.FakeEntity
import net.kyori.adventure.text.Component.text
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.BoundingBox


@Name("Ball")
class AbilityConceptBall : AbilityConcept() {


    init {
        range = 1.0
        cost = 50.0
        durationTime = 5000L
        cooldownTime = 2000L
        wand = ItemStack(Material.MAGMA_CREAM)
        damage = Damage.of(DamageType.RANGED, EsperStatistic.of(EsperAttribute.ATTACK_DAMAGE to 6.5))
        displayName = "구체 조종"

        description = listOf(
            text("구체 하나가 생성됩니다."),
            text("구체에 닿은 적은 원거리 피해를 입습니다."),
            text("스킬을 사용해 구체의 위치를 조종할 수 있습니다."),
            text("웅크린 채로 스킬을 사용하면 구체가 시전자의 위치로 이동합니다."),
            text("공격력이 크게 감소합니다.")
        )
    }
}
class AbilityBall : ActiveAbility<AbilityConceptBall>(), Listener {
    lateinit var loca : Location
    var playerdt = 0.0
    var fakeEntity: FakeEntity? = null
    var objectball: FakeEntity? = null
    var isskillon = 0
    lateinit var moveloc : Location
    var num = 1
    var toplayer = 0

    override fun onEnable() {
        psychic.registerEvents(this)
        moveloc = esper.player.location
        loca = esper.player.location
        fakeEntity = psychic.spawnFakeEntity(esper.player.location, ArmorStand::class.java).apply {
            updateMetadata<ArmorStand> {
                isVisible = false
            }
        }
        objectball = psychic.spawnFakeEntity(esper.player.location, EnderCrystal::class.java).apply {
            updateMetadata<EnderCrystal> {
                isVisible = true
            }
        }
        val world = esper.player.world
        psychic.runTaskTimer({
            esper.player.addPotionEffect(
                PotionEffect(PotionEffectType.WEAKNESS, 10, 5, false, false, false)
            )

            if (isskillon == 1) {
                world.spawnParticle(Particle.COMPOSTER, ballloc(), 3)
                playerdt += 1.2
                fakeEntity?.moveTo(ballloc())
                if (durationTime == 0L) {
                    if (isskillon == 1) {
                        moveloc = objectball!!.location
                        cooldownTime = concept.cooldownTime
                        loca = objectball!!.location
                        isskillon = 2

                    }
                }
            }
            else if (isskillon == 2) {

                if (num != 11) {

                    moveloc.apply {
                        x += (ballloc().x - loca.x) / 10
                        y += (ballloc().y - loca.y) / 10
                        z += (ballloc().z - loca.z) / 10
                    }
                    objectball?.moveTo(moveloc)
                    num += 1
                }
                else {
                    isskillon = 0
                    num = 1
                }
                damagef()

            }

        }, 1L, 1L)
    }
    override fun onDisable() {
        // 제거
        fakeEntity?.let { fakeEntity ->
            fakeEntity.remove()
            this.fakeEntity = null
        }
        objectball?.let { objectball ->
            objectball.remove()
            this.objectball = null
        }
    }
    fun damagef() {
        val loc = moveloc
        val box = loc?.let { BoundingBox.of(it, 1.2, 1.2, 1.2) }
        val hostiles = box?.let { loc?.world.getNearbyEntities(it, esper.player.hostileFilter()) }

        if (hostiles != null) {
            for (entity in hostiles) {
                (entity as LivingEntity).psychicDamage()
            }
        }
    }

    override fun onCast(event: PlayerEvent, action: WandAction, target: Any?) {
        val concept = concept

        if (esper.player.isSneaking) {
            playerdt = 0.0
            psychic.consumeMana(concept.cost)
            cooldownTime = concept.cooldownTime
            moveloc = objectball!!.location
            loca = objectball!!.location
            isskillon = 2
        }

        if(isskillon == 0) {

            playerdt = 0.0
            psychic.consumeMana(concept.cost)
            durationTime = concept.durationTime
            isskillon = 1

        }
        else if(isskillon == 1) {
            moveloc = objectball!!.location
            cooldownTime = concept.cooldownTime
            loca = objectball!!.location
            isskillon = 2
        }

    }



    private fun ballloc(): Location {
        return esper.player.eyeLocation.apply {
            add(direction.multiply(playerdt))
        }
    }





}
