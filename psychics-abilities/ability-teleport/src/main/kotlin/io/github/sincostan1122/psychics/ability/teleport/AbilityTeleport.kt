package io.github.sincostan1122.psychics.ability.teleport

import io.github.monun.psychics.ActiveAbility
import io.github.monun.psychics.AbilityConcept
import io.github.monun.tap.config.Config
import io.github.monun.tap.config.Name
import io.github.monun.tap.fake.FakeEntity
import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.entity.ArmorStand
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerEvent
import org.bukkit.inventory.ItemStack



@Name("Teleport")
class AbilityConceptTeleport : AbilityConcept() {
    @Config
    var isportalon = 0

    init {
        cooldownTime = 1200000
        wand = ItemStack(Material.GOLDEN_HOE)
        displayName = "순간이동"

        description = listOf(
            Component.text("포탈이 없을 시: 현재 위치에 포탈을 남깁니다."),
            Component.text("포탈이 있을 시: 포탈로 순간이동합니다."),
            Component.text("포탈은 파괴되고 쿨타임이 돕니다.")
        )
    }
}
class AbilityTeleport : ActiveAbility<AbilityConceptTeleport>(), Listener {
    var fakeEntity: FakeEntity? = null
    var locklocation = esper.player.location


    override fun onEnable() {
        psychic.registerEvents(this)
        fakeEntity = psychic.spawnFakeEntity(playerloc(), ArmorStand::class.java).apply {
            updateMetadata<ArmorStand> {
                isVisible = false
            }
        }
        val world = playerloc().world
        psychic.runTaskTimer({
            if (concept.isportalon == 1) {
                world.spawnParticle(Particle.DRIPPING_OBSIDIAN_TEAR, teleportloc(), 5)
                world.spawnParticle(Particle.ENCHANTMENT_TABLE, teleportloc(), 5)
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

    override fun onCast(event: PlayerEvent, action: WandAction, target: Any?) {
        val concept = concept
        if(concept.isportalon == 0) {
            locklocation = playerloc()
            concept.isportalon = 1
        }
        else if(concept.isportalon == 1) {
            esper.player.teleport(teleportloc())
            concept.isportalon = 0
            cooldownTime = concept.cooldownTime
        }



    }
    private fun teleportloc(): Location {

        return(locklocation)
    }
    private fun playerloc(): Location {
        return(esper.player.location)
    }



}
