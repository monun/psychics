package io.github.sincostan1122.psychics.ability.teleport


import io.github.monun.psychics.ActiveAbility
import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.attribute.EsperAttribute
import io.github.monun.psychics.attribute.EsperStatistic
import io.github.monun.tap.config.Config
import io.github.monun.tap.config.Name
import io.github.monun.tap.fake.FakeEntity
import net.kyori.adventure.text.Component.text
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.entity.ArmorStand
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerEvent
import org.bukkit.inventory.ItemStack


@Name("Flash")
class AbilityConceptFlash : AbilityConcept() {
    @Config
    var trange = 0.0

    init {
        cost = 50.0
        cooldownTime = 10L
        wand = ItemStack(Material.DIAMOND)
        displayName = "점멸"

        description = listOf(
            text("일정 거리를 순간이동 합니다."),
            text("순간이동 거리는 현재 마나량에 비례합니다.")
        )
    }
}
class AbilityFlash : ActiveAbility<AbilityConceptFlash>(), Listener {
    var fakeEntity: FakeEntity? = null


    override fun onEnable() {
        psychic.registerEvents(this)
        fakeEntity = psychic.spawnFakeEntity(teleportloc(), ArmorStand::class.java).apply {
            updateMetadata<ArmorStand> {
                isVisible = false
            }
        }
        val world = teleportloc().world
        psychic.runTaskTimer({
            fakeEntity?.moveTo(teleportloc())
            concept.trange =  esper.getStatistic(EsperStatistic.of(EsperAttribute.MANA to 0.1))
            world.spawnParticle(Particle.COMPOSTER, teleportloc(), 3)
        }, 0L, 1L)
    }
    override fun onDisable() {
        // 제거
        fakeEntity?.let { fakeEntity ->
            fakeEntity.remove()
            this.fakeEntity = null
        }
    }

    override fun onCast(event: PlayerEvent, action: WandAction, target: Any?) {
        val concept = concept
        psychic.consumeMana(concept.cost)
        cooldownTime = concept.cooldownTime

        esper.player.teleport(teleportloc())
    }

    private fun teleportloc(): Location {
        return esper.player.eyeLocation.apply {
            add(direction.multiply(concept.trange))
        }
    }



}
