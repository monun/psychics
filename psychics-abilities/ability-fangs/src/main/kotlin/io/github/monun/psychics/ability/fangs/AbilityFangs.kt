package io.github.monun.psychics.ability.fangs

import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.ActiveAbility
import io.github.monun.psychics.attribute.EsperAttribute
import io.github.monun.psychics.damage.Damage
import io.github.monun.psychics.damage.DamageType
import io.github.monun.psychics.util.Times
import io.github.monun.psychics.util.hostileFilter
import io.github.monun.tap.config.Config
import io.github.monun.tap.config.Name
import io.github.monun.tap.fake.FakeEntity
import net.kyori.adventure.text.Component.text
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Entity
import org.bukkit.entity.EvokerFangs
import org.bukkit.entity.LivingEntity
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.util.BoundingBox
import java.util.*

@Name("fangs")
class AbilityConceptFangs : AbilityConcept() {
    @Config
    var fangsSpace = 1.5

    @Config
    var fangsHeight = 1.5

    @Config
    var fangsWidth = 2.0

    @Config
    var fangPerTime = 40L

    init {
        cooldownTime = 500L
        range = 16.0
        cost = 20.0
        damage = Damage.of(DamageType.RANGED, EsperAttribute.ATTACK_DAMAGE to 2.0)
        knockback = 0.5
        description = listOf(
            text("지정한 방향으로 송곳니를 소환합니다.")
        )
        wand = ItemStack(Material.STICK)
    }
}

class AbilityFangs : ActiveAbility<AbilityConceptFangs>(), Listener {

    private lateinit var effectQueue: PriorityQueue<Fang>
    private lateinit var damageQueue: PriorityQueue<Fang>
    private lateinit var removeQueue: PriorityQueue<Fang>

    override fun onEnable() {
        effectQueue = PriorityQueue()
        damageQueue = PriorityQueue()
        removeQueue = PriorityQueue()

        psychic.runTaskTimer(this::onUpdate, 0L, 1L)
    }

    override fun onDisable() {
        fun PriorityQueue<Fang>.destroy() {
            forEach { it.fakeEntity.remove() }
            clear()
        }

        effectQueue.destroy()
        damageQueue.destroy()
        removeQueue.destroy()
    }

    override fun onCast(event: PlayerEvent, action: WandAction, target: Any?) {
        val concept = concept
        val player = esper.player
        val location = player.location

        val vector = location.direction.multiply(concept.fangsSpace)
        val count = (concept.range / concept.fangsSpace).toInt()
        val currentTime = Times.current
        val damaged = HashSet<Entity>()

        repeat(count) {
            val loc = location.add(vector).clone()
            val fakeEntity = psychic.spawnFakeEntity(location.clone().apply { y -= 0.3 }, EvokerFangs::class.java)
            val effectTime = currentTime + concept.fangPerTime * it
            effectQueue.offer(
                Fang(
                    loc,
                    fakeEntity,
                    effectTime,
                    damaged
                )
            )
        }

        exhaust()
    }

    private fun onUpdate() {
        val current = Times.current

        effectQueue.execute(current) { fang ->
            fang.fakeEntity.playEffect(4) // 물기
            fang.nextRunTime += 250L
            damageQueue.offer(fang)
        }

        damageQueue.execute(current) { fang ->
            val r = concept.fangsWidth / 2.0
            val loc = fang.location
            val box = BoundingBox.of(loc, r, concept.fangsHeight, r)
            val hostiles = loc.world.getNearbyEntities(box, esper.player.hostileFilter())

            for (entity in hostiles) {
                if (fang.damaged.add(entity))
                    (entity as LivingEntity).psychicDamage()

            }

            fang.nextRunTime += 1000L
            removeQueue.offer(fang)
        }

        removeQueue.execute(current) {
            it.fakeEntity.remove()
        }
    }

    private class Fang(
        val location: Location,
        val fakeEntity: FakeEntity,
        var nextRunTime: Long,
        val damaged: MutableSet<Entity>
    ) : Comparable<Fang> {
        override fun compareTo(other: Fang): Int {
            return nextRunTime.compareTo(other.nextRunTime)
        }
    }

    private fun PriorityQueue<Fang>.execute(current: Long, executor: (Fang) -> Unit) {
        while (isNotEmpty()) {
            val fang = peek()

            if (current < fang.nextRunTime)
                break

            remove()
            executor(fang)
        }
    }
}