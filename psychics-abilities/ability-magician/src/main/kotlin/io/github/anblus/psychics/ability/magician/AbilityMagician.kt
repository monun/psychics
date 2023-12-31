package io.github.anblus.psychics.ability.magician

import io.github.monun.psychics.Ability
import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.AbilityType
import io.github.monun.psychics.TestResult
import io.github.monun.psychics.attribute.EsperAttribute
import io.github.monun.psychics.attribute.EsperStatistic
import io.github.monun.psychics.damage.Damage
import io.github.monun.psychics.damage.DamageType
import io.github.monun.psychics.item.isPsychicbound
import io.github.monun.psychics.util.hostileFilter
import io.github.monun.tap.config.Name
import io.github.monun.tap.event.EntityProvider
import io.github.monun.tap.event.TargetEntity
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.*
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector
import kotlin.random.Random.Default.nextDouble
import kotlin.random.Random.Default.nextInt

// 짜잔~ 마술이 아니라 마법이랍니다~
@Name("magician")
class AbilityConceptMagician : AbilityConcept() {

    init {
        displayName = "마술"
        type = AbilityType.PASSIVE
        cost = 20.0
        cooldownTime = 2000L
        damage = Damage.of(DamageType.MELEE, EsperStatistic.of(EsperAttribute.ATTACK_DAMAGE to 4.0))
        description = listOf(
            text("능력 아이템을 들고 상대를 타격 시 무작위의"),
            text("효과와 함께 능력 데미지를 입힙니다."),
            text(""),
            text("${ChatColor.ITALIC}${ChatColor.LIGHT_PURPLE}효과는 다음 중 하나가 발동됩니다."),
            text(" - 실명 적용"),
            text(" - 수 많은 토끼 소환"),
            text(" - 번개 생성"),
            text(" - 보는 방향 전환"),
            text(" - 동물 탑승"),
            text(" - 무작위 위치로 강제 이동"),
        )
        wand = ItemStack(Material.STICK)
        supplyItems = listOf(ItemStack(Material.STICK).apply {
            val meta = itemMeta
            meta.displayName(text("마술봉", NamedTextColor.AQUA, TextDecoration.BOLD))
            meta.isPsychicbound = true
            itemMeta = meta
        })
    }

}

class AbilityMagician : Ability<AbilityConceptMagician>(), Listener {

    val magicEntityList = arrayOf(EntityType.PIG, EntityType.COW, EntityType.SHEEP, EntityType.CHICKEN)

    override fun onEnable() {
        psychic.registerEvents(this)
    }

    @EventHandler
    @TargetEntity(EntityProvider.EntityDamageByEntity.Damager::class)
    fun onDamaged(event: EntityDamageByEntityEvent) {
        val entity = event.entity
        if (esper.player.hostileFilter().test(entity) && entity is LivingEntity) {
            if (esper.player.inventory.itemInMainHand == concept.supplyItems[0]) {
                val player = esper.player
                val result = test()

                if (result != TestResult.Success) {
                    result.message(this)?.let { player.sendActionBar(it) }
                    return
                }

                cooldownTime = concept.cooldownTime
                psychic.mana -= concept.cost
                val world = entity.world
                world.spawnParticle(Particle.DRAGON_BREATH, entity.boundingBox.center.toLocation(world), 12, 0.3, 0.3, 0.3, 0.0)
                world.playSound(entity.location, Sound.ENTITY_ENDERMAN_TELEPORT, 2.0F, 2.0F)
                event.isCancelled = true
                entity.psychicDamage()
                val randomResult = nextInt(1,7) - 1
                if (randomResult == 0) {
                    entity.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 50, 1, true, true))
                } else if (randomResult == 1) {
                    repeat(nextInt(8, 12)) {
                        world.spawnEntity(entity.eyeLocation, EntityType.RABBIT).apply {
                            velocity = Vector(nextDouble(1.0) - 0.5, 0.3, nextDouble(1.0) - 0.5)
                        }
                    }
                } else if (randomResult == 2) {
                    world.strikeLightningEffect(entity.location)
                } else if (randomResult == 3) {
                    entity.setRotation(entity.location.yaw + 180f, entity.location.pitch + 180f)
                } else if (randomResult == 4) {
                    val typeList = magicEntityList
                    val randomEntityType = typeList[nextInt(0,typeList.size-1)]
                    var target: Entity
                    if (entity.passengers.size > 0) { target = entity.passengers.last() }
                    else { target = entity }
                    target.addPassenger(world.spawnEntity(entity.location, randomEntityType))
                } else {
                    var randomLoc: Location
                    do{
                        randomLoc = entity.location.clone().apply {
                            x += nextDouble(10.0) - 5.0
                            y += nextDouble(5.0)
                            z += nextDouble(10.0) - 5.0
                        }
                    } while (randomLoc.block.type != Material.AIR)
                    world.rayTrace(
                        randomLoc,
                        Vector(0, -1, 0),
                        100.0,
                        FluidCollisionMode.NEVER,
                        true,
                        1.0
                    ) { it == null }?. let { result ->
                        if (result.hitBlock != null) randomLoc = result.hitPosition.toLocation(world)
                    }
                    entity.teleport(
                        randomLoc.apply {
                            yaw = entity.location.yaw
                            pitch = entity.location.pitch
                        }
                    )
                }
            }
        }
    }
}





