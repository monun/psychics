package io.github.sincostan1122.psychics.ability.control



import io.github.monun.psychics.ActiveAbility
import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.Channel
import io.github.monun.psychics.attribute.EsperAttribute
import io.github.monun.psychics.attribute.EsperStatistic
import io.github.monun.tap.config.Config
import io.github.monun.tap.config.Name
import io.github.monun.tap.fake.FakeEntity
import io.github.monun.tap.fake.invisible
import net.kyori.adventure.text.Component.text
import org.bukkit.FluidCollisionMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.BoundingBox


@Name("control")
class AbilityConceptControl : AbilityConcept() {

    init {
        cost = 0.0
        range = 50.0
        castingTime = 500L
        durationTime = 5000L
        cooldownTime = 30000L
        wand = ItemStack(Material.DIAMOND_SWORD)
        displayName = "지배"



        description = listOf(
            text("대상 하나를 지정하여 ${durationTime/ 1000}초 동안 조종합니다."),
            text("조종하는 동안 육체에서 빠져나와 무적 상태가 됩니다."),
            text("조종당하는 대상은 지속시간 동안 시야가 제한됩니다."),
            text("만약 아무것도 없는 지면이나 벽에 시전했을 경우, 제자리에 고정됩니다.")

        )
    }
}
class AbilityControl : ActiveAbility<AbilityConceptControl>(), Listener {
    var pbody: FakeEntity? = null
    var isskillon = 0
    lateinit var bodyloc : Location
    lateinit var victim : LivingEntity
    init{
        targeter = {
            val player = esper.player
            val start = player.eyeLocation
            val direction = start.direction
            val world = start.world

            world.rayTrace(
                start,
                direction,
                concept.range,
                FluidCollisionMode.NEVER,
                true,
                0.5
            ) { entity ->
                entity !== player && entity is LivingEntity
            }?.let { result ->
                result.hitEntity?.location ?: result.hitPosition.toLocation(world)
            }
        }
    }
    override fun onChannel(channel: Channel) {
        val location = channel.target as Location
        val world = location.world



        world.spawnParticle(Particle.SOUL, location, 5)
    }
    override fun onEnable() {

        psychic.registerEvents(this)

        pbody = psychic.spawnFakeEntity(esper.player.location, ArmorStand::class.java).apply {
            updateMetadata<ArmorStand> {
                isVisible = false
            }

            updateEquipment { // 장비 업데이트
                helmet = ItemStack(Material.PLAYER_HEAD)
                chestplate = ItemStack(Material.DIAMOND_CHESTPLATE)
                leggings = ItemStack(Material.DIAMOND_LEGGINGS)
                boots = ItemStack(Material.DIAMOND_BOOTS)
            }



        }
        victim = esper.player
        psychic.runTaskTimer({
            if (isskillon == 0) {
                pbody?.moveTo(esper.player.location)
                pbody!!.isVisible = false

            }
            else if (isskillon == 1) {
                pbody!!.isVisible = true
                esper.player.invisible = true
                val world = esper.player.world
                controlling(victim)
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, esper.player.location, 5)
                esper.player.sendActionBar("육체로 돌아가기까지 ${(durationTime / 1000L).toInt()}초...")

                if (durationTime == 0L) {
                    esper.player.invisible = false
                    isskillon = 0
                    esper.player.teleport(bodyloc)
                    victim = esper.player
                }
            }
        },0L, 1L)
    }

    override fun onCast(event: PlayerEvent, action: WandAction, target: Any?) {
        if (target == null) return

        cooldownTime = concept.cooldownTime
        val player = esper.player
        val location = target as Location
        val x = location.x
        val y = location.y
        val z = location.z
        val w = 1
        val h = 1
        val box = BoundingBox(x - w, y, z - w, x + w, y + h, z + w)
        val world = location.world
        durationTime = concept.durationTime
        bodyloc = esper.player.location


        esper.player.addPotionEffect(
            PotionEffect(PotionEffectType.INVISIBILITY, (concept.durationTime / 50L).toInt(), 1, false, false, false)
        )
        esper.player.addPotionEffect(
            PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, (concept.durationTime / 50L).toInt(), 10, false, false, false)
        )
        esper.player.addPotionEffect(
            PotionEffect(PotionEffectType.WEAKNESS, (concept.durationTime / 50L).toInt(), 1, false, false, false)
        )
        esper.player.addPotionEffect(
            PotionEffect(PotionEffectType.SLOW_DIGGING, (concept.durationTime / 50L).toInt(), 10, false, false, false)
        )

        world.getNearbyEntities(box) { entity ->
            entity is LivingEntity && entity !is ArmorStand
        }.forEach { entity ->


            victim = entity as LivingEntity
            esper.player.teleport(victim.location)
            if (entity == player) {
                entity.sendActionBar(text("지배당하고 있습니다..."))
            }
        }
        isskillon = 1
    }
    fun controlling(entity: LivingEntity) {
        entity.teleport(esper.player.location)
        entity.addPotionEffect(
            PotionEffect(PotionEffectType.BLINDNESS, 10, 5, false, false, false)
        )
        entity.addPotionEffect(
            PotionEffect(PotionEffectType.WEAKNESS, 10, 10, false, false, false)
        )
        entity.addPotionEffect(
            PotionEffect(PotionEffectType.SLOW_DIGGING, 10, 10, false, false, false)
        )
    }

}