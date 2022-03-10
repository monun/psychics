package io.github.sincostan1122.psychics.ability.illusion


import io.github.monun.psychics.Ability
import io.github.monun.psychics.AbilityConcept
import io.github.monun.tap.config.Config
import io.github.monun.tap.config.Name
import io.github.monun.tap.fake.FakeEntity
import net.kyori.adventure.text.Component.text
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.ArmorStand
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType


@Name("Illusion")
class AbilityConceptIllusion : AbilityConcept() {
    @Config
    var illusiontime = 2


    init {

        wand = ItemStack(Material.PLAYER_HEAD)
        displayName = "환상"

        description = listOf(
            text("무한히 투명 상태가 됩니다."),
            text("자신의 ${illusiontime}초전 위치에 잔상을 남깁니다."),
            text("받는 피해가 5배 증가합니다.")
        )
    }
}
class AbilityIllusion : Ability<AbilityConceptIllusion>(), Listener {
    var fakeEntity: FakeEntity? = null
    lateinit var arraylocation : Array<Location>




    override fun onEnable() {
        psychic.registerEvents(this)
        arraylocation = Array(concept.illusiontime * 20){esper.player.location}


       fakeEntity = psychic.spawnFakeEntity(arraylocation[0], ArmorStand::class.java).apply {
           updateMetadata<ArmorStand> {
               isVisible = true
           }

           updateEquipment { // 장비 업데이트
               helmet = ItemStack(Material.PLAYER_HEAD)
               chestplate = ItemStack(Material.DIAMOND_CHESTPLATE)
               leggings = ItemStack(Material.DIAMOND_LEGGINGS)
               boots = ItemStack(Material.DIAMOND_BOOTS)
           }


       }
       psychic.runTaskTimer(this::arrayreload, 0L, 1L)


   }

   private fun arrayreload() {
       val player = esper.player
       player.addPotionEffect(
           PotionEffect(PotionEffectType.INVISIBILITY, 10, 1, false, false, false)
       )
       for (i in 0..concept.illusiontime * 20 - 2) {
           arraylocation[i] = arraylocation[(i + 1)]
       }
       arraylocation[(concept.illusiontime * 20 - 1)] = esper.player.location
       fakeEntity?.moveTo(arraylocation[0])
   }

   override fun onDisable() {
       // 제거
       fakeEntity?.let { fakeEntity ->
           fakeEntity.remove()
           this.fakeEntity = null
       }
       esper.player.removePotionEffect(PotionEffectType.INVISIBILITY)
   }
   @EventHandler(ignoreCancelled = true)
   fun onEntityDamage(event: EntityDamageEvent) {

       val damage = event.damage * 5
       event.damage = damage

   }

}

