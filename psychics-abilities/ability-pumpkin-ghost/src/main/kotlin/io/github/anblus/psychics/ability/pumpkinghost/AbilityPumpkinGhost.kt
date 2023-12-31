package io.github.anblus.psychics.ability.pumpkinghost

import io.github.monun.psychics.*
import io.github.monun.psychics.attribute.EsperAttribute
import io.github.monun.psychics.attribute.EsperStatistic
import io.github.monun.psychics.damage.Damage
import io.github.monun.psychics.damage.DamageType
import io.github.monun.psychics.tooltip.TooltipBuilder
import io.github.monun.psychics.tooltip.stats
import io.github.monun.psychics.util.TargetFilter
import io.github.monun.psychics.util.hostileFilter
import io.github.monun.tap.config.Config
import io.github.monun.tap.config.Name
import io.github.monun.tap.fake.FakeEntity
import io.github.monun.tap.fake.Movement
import io.github.monun.tap.fake.Trail
import io.github.monun.tap.math.normalizeAndLength
import io.github.monun.tap.task.TickerTask
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.*
import org.bukkit.block.data.Directional
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector

// 호박 함정
@Name("pumpkin-ghost")
class AbilityConceptPumpkinGhost : AbilityConcept() {

    @Config
    val pumpkinSpeed = 0.8

    @Config
    val costPerHalf = 0.25

    @Config
    val detectionPreparationTick = 100

    @Config
    val detectionSize = 0.5

    @Config
    val explosionRange = 2.0

    @Config
    val glowingTick = 100

    init {
        displayName = "호박 유령"
        type = AbilityType.ACTIVE
        cost = 20.0
        range = 6.0
        damage = Damage.of(DamageType.BLAST, EsperStatistic.of(EsperAttribute.ATTACK_DAMAGE to 6.0))
        knockback = 0.2
        description = listOf(
            text("좌클릭 시 마나를 소모해 유령이 깃든 잭오랜턴 하나를 얻습니다."),
            text("유령이 깃든 잭오랜턴은 설치 후 일정 시간이 지나면 탐지를 시작합니다."),
            text("탐지 중인 잭오랜턴의 앞에 적이 있을 경우 즉시 앞으로 돌진해 폭발합니다."),
            text("탐지 중인 잭오랜턴은 매 초 마나를 소모합니다. 마나가 없을 시 소멸합니다."),
            text("폭발에 맞은 적은 소량의 넉백과 함께 발광 효과가 부여됩니다."),
            text("우클릭 시 탐지 중인 잭오랜턴을 모두 소멸시킵니다.")
        )
        wand = ItemStack(Material.BLAZE_ROD)
    }

    override fun onRenderTooltip(tooltip: TooltipBuilder, stats: (EsperStatistic) -> Double) {
        tooltip.stats(costPerHalf * 2) { NamedTextColor.AQUA to "초당 마나 소모" to null }
        tooltip.stats(detectionPreparationTick.toDouble() / 20.0) { NamedTextColor.YELLOW to "탐지 준비 시간" to "초" }
    }
}

class AbilityPumpkinGhost : Ability<AbilityConceptPumpkinGhost>(), Listener {
    companion object {
        private val hauntedPumpkin = ItemStack(Material.JACK_O_LANTERN).apply {
            itemMeta = itemMeta.apply {
                displayName(
                    text().color(NamedTextColor.WHITE).content("호박 유령").decoration(TextDecoration.ITALIC, false).build()
                )
                lore(
                    listOf(
                        text().content("유령이 깃들었다. (호박 유령 능력 전용)")
                            .color(NamedTextColor.GOLD)
                            .decoration(TextDecoration.ITALIC, false)
                            .build()
                    )
                )
            }
        }
    }

    private var pumpkinList = arrayListOf<Pumpkin>()

    override fun onEnable() {
        psychic.registerEvents(this)
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val action = event.action

        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            event.item?.let { item ->
                if (item.type == concept.wand?.type) {
                    val player = esper.player

                    val result = test()
                    if (result != TestResult.Success) {
                        result.message(this)?.let { player.sendActionBar(it) }
                        return
                    }

                    psychic.consumeMana(concept.cost)
                    cooldownTime = concept.cooldownTime

                    player.inventory.addItem(hauntedPumpkin)
                    player.playSound(player.location, Sound.ENTITY_GHAST_SCREAM, 1.5f, 2.0f)
                }
            }
        } else if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            event.item?.let { item ->
                if (item.type == concept.wand?.type) {
                    val player = esper.player

                    if (pumpkinList.isEmpty()) {
                        player.sendActionBar(text().content("소멸 시킬 호박이 존재하지 않습니다").decorate(TextDecoration.BOLD).build())
                        return
                    }

                    repeat(pumpkinList.size) {
                        pumpkinList[0].pumpkinRemove()
                    }
                    player.playSound(player.location, Sound.BLOCK_CHAIN_BREAK, 2.0f, 0.2f)
                }
            }
        }
    }

    @EventHandler
    fun onPlace(event: BlockPlaceEvent) {
        if (!event.itemInHand.isSimilar(hauntedPumpkin)) return
        val location = event.block.location.apply { x += 0.5; z += 0.5; y += 0.5}
        psychic.runTask({
            if (location.block.type == Material.JACK_O_LANTERN) {
                val world = location.world

                pumpkinList.add(Pumpkin(location))
                world.spawnParticle(Particle.SOUL, location, 8, 0.5, 0.5, 0.5, 0.03)
                world.playSound(location, Sound.ENTITY_PARROT_IMITATE_GHAST, 1.5f, 0.1f)
            }
        }, concept.detectionPreparationTick.toLong())
    }

    inner class Pumpkin(private val location: Location) {
        private var timer: TickerTask? = null

        private var manaDecreaseCycle: Int = 10

        private val direction: Vector = (location.block.blockData as Directional).facing.direction

        init {
            timer = psychic.runTaskTimer(this::onTick, 1L , 1L)
        }

        private fun onTick() {
            manaDecreaseCycle --
            if (manaDecreaseCycle <= 0) {
                if (psychic.mana < concept.costPerHalf) {
                    pumpkinRemove()
                    return
                }
                psychic.mana -= concept.costPerHalf
                manaDecreaseCycle = 10
                location.world.spawnParticle(Particle.SOUL, location, 1, 0.5, 0.5, 0.5, 0.03)
            }

            if (location.block.type != Material.JACK_O_LANTERN) {
                pumpkinRemove()
                return
            }

            location.world.rayTrace(
                location.clone().add(direction),
                direction,
                concept.range,
                FluidCollisionMode.NEVER,
                true,
                concept.detectionSize,
                TargetFilter(esper.player)
            )?.hitEntity?.run {
                val rotatedLocation = this@Pumpkin.location.clone().setDirection(direction)
                val projectile = PumpkinProjectile().apply {
                    pumpkin =
                        this@AbilityPumpkinGhost.psychic.spawnFakeEntity(rotatedLocation, ArmorStand::class.java).apply {
                            updateMetadata<ArmorStand> {
                                isVisible = false
                                isMarker = true
                            }
                            updateEquipment {
                                helmet = ItemStack(Material.JACK_O_LANTERN)
                            }
                        }
                    velocity = direction.multiply(concept.pumpkinSpeed)
                }

                psychic.launchProjectile(rotatedLocation, projectile)

                this@Pumpkin.pumpkinRemove()
                return
            }

        }

        fun pumpkinRemove() {
            pumpkinList.remove(this)
            location.block.type = Material.AIR
            location.world.spawnParticle(Particle.SOUL, location, 8, 0.5, 0.5, 0.5, 0.03)
            timer!!.cancel()
            return
        }
    }

    inner class PumpkinProjectile : PsychicProjectile(1200, concept.range) {
        lateinit var pumpkin: FakeEntity

        override fun onMove(movement: Movement) {
            pumpkin.moveTo(movement.to.clone().apply { y -= 1.62 })
        }

        override fun onTrail(trail: Trail) {
            trail.velocity?.let { velocity ->
                val from = trail.from
                val length = velocity.normalizeAndLength()

                from.world.rayTrace(
                    from,
                    velocity,
                    length,
                    FluidCollisionMode.NEVER,
                    true,
                    concept.detectionSize,
                    TargetFilter(esper.player)
                )?.let { remove() }
            }
        }

        override fun onRemove() {
            val world = location.world
            val range = concept.explosionRange
            world.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 1.5F, 1.0F)
            world.spawnParticle(Particle.EXPLOSION_LARGE, location, 4, range / 2, range / 2, range / 2, 0.0)
            location.getNearbyEntities(
                range, range, range
            ).filter { entity -> esper.player.hostileFilter().test(entity) }.forEach { entity ->
                if (entity is LivingEntity) {
                    entity.psychicDamage(knockback = concept.knockback)
                    entity.addPotionEffect(
                        PotionEffect(PotionEffectType.GLOWING, concept.glowingTick, 0, false, false, false)
                    )
                }
            }
            pumpkin.remove()
        }
    }
}






