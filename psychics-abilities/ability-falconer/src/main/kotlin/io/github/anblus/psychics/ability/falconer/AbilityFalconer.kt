package io.github.anblus.psychics.ability.falconer

import io.github.monun.psychics.*
import io.github.monun.psychics.attribute.EsperAttribute
import io.github.monun.psychics.attribute.EsperStatistic
import io.github.monun.psychics.damage.Damage
import io.github.monun.psychics.damage.DamageType
import io.github.monun.psychics.tooltip.TooltipBuilder
import io.github.monun.psychics.tooltip.stats
import io.github.monun.psychics.tooltip.template
import io.github.monun.psychics.util.TargetFilter
import io.github.monun.tap.config.Config
import io.github.monun.tap.config.Name
import io.github.monun.tap.fake.FakeEntity
import io.github.monun.tap.trail.TrailSupport
import net.kyori.adventure.text.Component.space
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.*
import org.bukkit.attribute.Attribute
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityTargetEvent
import org.bukkit.event.player.PlayerEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.scoreboard.Team
import org.bukkit.util.Vector
import kotlin.random.Random.Default.nextInt

// 팬텀 부리기
@Name("falconer")
class AbilityConceptFalconer : AbilityConcept() {

    @Config
    val spawnLimitOfPhantom = 6

    @Config
    val phantomTeleportRange = 12.0

    @Config
    val phantomAttackDamage = EsperStatistic.of(EsperAttribute.ATTACK_DAMAGE to 4.0)

    @Config
    val largePhantomMaxHealth = 10.0

    @Config
    val coolTimeInReuse = 10000L

    init {
        displayName = "매잡이"
        type = AbilityType.ACTIVE
        cost = 20.0
        cooldownTime = 500L
        castingTime = 3000L
        range = 36.0
        description = listOf(
            text("${ChatColor.DARK_GRAY}좌클릭 | 사냥"),
            text("  소환된 팬텀들에게 지정한 상대를 공격하라고 명령합니다."),
            text("  이미 지정된 상대를 다시 한번 지정할 시 명령을 해제합니다."),
            text("  누군가를 이미 지정한 상태에서는 추가로 쿨타임이 부여됩니다."),
            text("${ChatColor.GOLD}우클릭 | 조련"),
            text("  마나를 소모하여 자신을 따르는 팬텀을 소환합니다."),
            text("  동시에 소환 가능한 팬텀의 수는 제한되어 있습니다."),
            text("  팬텀의 체력은 매우 낮으며, 공격력은 초월에 비례합니다."),
            text("  팬텀은 주인과의 거리가 멀어질 시 주인에게 텔레포트 합니다."),
            space(),
            text("${ChatColor.GRAY}*팬텀의 이름 앞에 붙는 단어는 어떠한 효과도 없습니다. 하나 빼고요.")
        )
        wand = ItemStack(Material.BONE)
    }
    
    override fun onRenderTooltip(tooltip: TooltipBuilder, stats: (EsperStatistic) -> Double) {
        tooltip.stats(spawnLimitOfPhantom) { NamedTextColor.DARK_GREEN to "팬텀 소환 제한" to "마리" }
        tooltip.stats(phantomTeleportRange.toInt()) { NamedTextColor.DARK_RED to "텔레포트 거리" to "블록" }
        tooltip.stats(text("팬텀").color(NamedTextColor.DARK_PURPLE).decorate(TextDecoration.BOLD), Damage.of(DamageType.MELEE, phantomAttackDamage)) { NamedTextColor.DARK_PURPLE to "phantomDamage" }
        tooltip.template("phantomDamage", stats(phantomAttackDamage))
    }
}

class AbilityFalconer : ActiveAbility<AbilityConceptFalconer>(), Listener {
    private var prefix = arrayListOf("충실한", "귀여운", "멋진", "듬직한", "매인 척 하는", "장난꾸러기", "고집 있는", "신중한", "용맹한", "뭔가 유달리 강해 보이는",
        "날렵한", "의욕이 넘치는", "겁 많은", "자기애가 넘치는", "운 좋은", "뚫고 나온 악몽, 하늘의 악마, 밤의 저승사자...라고 불리는", "외로움을 타는", "카리스마 있는", "겉과 속이 다른", "거대한")

    private var phantoms: MutableList<Mob> = mutableListOf()

    private var commonTarget: LivingEntity? = null

    private var fakePhantom: FakeEntity? = null

    private var fakeBone: FakeEntity? = null

    override fun onEnable() {
        psychic.registerEvents(this)
        psychic.runTaskTimer(this::onTick, 0L, 1L)
    }

    override fun onDisable() {
        fakePhantom?.let {
            it.remove()
            fakePhantom = null
            fakeBone?.remove()
            fakeBone = null
        }
        phantoms.forEach { phantom ->
            phantom.remove()
            psychic.plugin.entityEventManager.unregisterEvent(phantom, this)
        }
        phantoms.clear()
    }

    private fun onTick() {
        val player = esper.player
        commonTarget?.let { target ->
            if (!target.isValid || phantoms.isEmpty()) {
                if (phantoms.isNotEmpty()) player.sendMessage(text().content("표적이 처치되었습니다!").color(NamedTextColor.DARK_RED).build())
                commonTarget = null
            }
        }

        if (phantoms.isNotEmpty()) {
            phantoms.forEach { phantom -> if (phantom.target != commonTarget) phantom.target = commonTarget }
            if (commonTarget == null) {
                val world = player.world
                val center = world.rayTrace(player.location, Vector(0, 1, 0), 2.5, FluidCollisionMode.SOURCE_ONLY, true, 1.0) { false }?.hitPosition?.toLocation(world)?.apply { y -= 0.5 } ?: player.location.add(Vector(0.0, 2.5, 0.0))

                phantoms.forEach { phantom ->
                    val location = phantom.location
                    if (location.distance(center) >= concept.phantomTeleportRange) {
                        phantom.teleport(center)
                    }
                }
            }
        }
    }

    override fun onChannel(channel: Channel) {
        val player = esper.player
        val world = player.world
        val location = world.rayTrace(player.location, Vector(0, 1, 0), 2.5, FluidCollisionMode.SOURCE_ONLY, true, 1.0) { false }?.hitPosition?.toLocation(world)?.apply { y -= 0.5 } ?: player.location.add(Vector(0.0, 2.5, 0.0))

        fakeBone?.let { bone ->
            if (nextInt(0, 30) == 0) world.playSound(location, Sound.ENTITY_PARROT_AMBIENT, SoundCategory.VOICE, 2.0F, 0.1F)
            bone.updateMetadata<Item> {
                setRotation(server.currentTick.toFloat(), 0f)
            }
            fakePhantom!!.moveTo(fakePhantom!!.location.subtract(Vector(0.0, 0.3, 0.0)))
        }?: run {
            world.playSound(location, Sound.ENTITY_PARROT_AMBIENT, SoundCategory.VOICE, 2.0F, 0.1F)
            fakeBone = psychic.spawnFakeEntity(location, Item::class.java).apply {
                updateMetadata<Item> {
                    itemStack = ItemStack(Material.BONE)
                    thrower = player.uniqueId
                    setGravity(false)
                }
            }
            fakePhantom = psychic.spawnFakeEntity(location.add(Vector(0.0, 6.0 * (concept.castingTime.toDouble() / 1000), 0.0)).apply { pitch = -90f }, Phantom::class.java).apply {
                updateMetadata<Phantom> {
                    val random = if (prefix.isNotEmpty()) prefix.random() else "평범한"
                    prefix.remove(random)

                    if (random == "거대한") size = 5
                    customName = "$random 팬텀"
                    isCustomNameVisible = true
                }
            }
        }
    }

    override fun onInterrupt(channel: Channel) {
        fakeBone?.let { bone ->
            fakePhantom!!.updateMetadata<Phantom> {
                prefix.add(customName!!.dropLast(3))
            }
            fakePhantom!!.remove()
            bone.remove()
            fakePhantom = null
            fakeBone = null
        }
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = esper.player
        if (event.player != player) return

        val action = event.action

        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            if (event.item?.type == concept.wand?.type) {
                if (phantoms.isEmpty()) {
                    player.sendActionBar(text().content("명령을 내릴 팬텀이 존재하지 않습니다").decorate(TextDecoration.BOLD).build())
                    return
                }

                val testResult = test()
                if (testResult != TestResult.Success && testResult != TestResult.FailedCost) {
                    testResult.message(this)?.let { player.sendActionBar(it) }
                    return
                }

                val world = player.world
                val location = player.eyeLocation

                world.rayTrace(
                    location,
                    location.direction,
                    concept.range,
                    FluidCollisionMode.NEVER,
                    true,
                    0.8
                ) { target ->
                    TargetFilter(player).test(target) && target is LivingEntity && target !in phantoms
                }?.hitEntity?.let { target ->
                    if (commonTarget != null) cooldownTime = concept.coolTimeInReuse
                    if (target as LivingEntity != commonTarget) {
                        commonTarget = target
                        target.sendMessage(text().content("${player.name}의 팬텀들의 표적이 되었습니다!").color(NamedTextColor.DARK_GRAY).build())
                        player.sendMessage(text().content("팬텀들에게 ${target.name}의 공격을 명령했습니다!").color(NamedTextColor.DARK_GRAY).build())

                        val targetPos = target.boundingBox.center.toLocation(world)
                        phantoms.forEach { phantom ->
                            TrailSupport.trail(phantom.eyeLocation, targetPos, 0.2) { w, x, y, z ->
                                w.spawnParticle(Particle.DUST_COLOR_TRANSITION, x, y, z, 1, 0.0, 0.0, 0.0, 0.05, Particle.DustTransition(Color.fromRGB(255, 122, 122), Color.fromRGB(255, 64, 64), 0.8f))
                            }
                        }

                        world.playSound(location, Sound.ENTITY_DOLPHIN_PLAY, SoundCategory.VOICE, 2.0F, 0.6F)
                    } else {
                        commonTarget = null
                        world.playSound(location, Sound.ENTITY_DOLPHIN_PLAY, SoundCategory.VOICE, 2.0F, 1.4F)
                        target.sendMessage(text().content("${player.name}의 팬텀들의 표적에서 벗어났습니다.").color(TextColor.color(122, 122, 122)).build())
                        player.sendMessage(text().content("팬텀들에게 ${target.name}의 공격에 대한 명령을 해제했습니다.").color(TextColor.color(122, 122, 122)).build())
                    }
                    return
                }
                player.sendActionBar(text().content("대상 혹은 위치가 지정되지 않았습니다").decorate(TextDecoration.BOLD).build())
            }
        }
    }

    override fun tryCast(
        event: PlayerEvent,
        action: WandAction,
        castingTime: Long,
        cost: Double,
        targeter: (() -> Any?)?
    ): TestResult {
        if (action == WandAction.LEFT_CLICK) return TestResult.FailedAction

        if (phantoms.size >= concept.spawnLimitOfPhantom) {
            esper.player.sendActionBar(text().content("소환할 수 있는 최대 팬텀 수에 도달했습니다").decorate(TextDecoration.BOLD).build())
            return TestResult.FailedAction
        }

        return super.tryCast(event, action, castingTime, cost, targeter)
    }

    override fun onCast(event: PlayerEvent, action: WandAction, target: Any?) {
        fakeBone?.let { bone ->
            val player = esper.player
            val world = player.world

            cooldownTime = concept.cooldownTime
            psychic.consumeMana(concept.cost)

            commonTarget?.let { target ->
                player.sendMessage(text().content("조련에 의해 ${target.name}의 공격에 대한 명령이 해제됐습니다.").color(TextColor.color(122, 122, 122)).build())
                commonTarget = null
            }

            val location = bone.location

            world.spawnParticle(Particle.HEART, location, 6, 0.75, 0.75, 0.75, 0.08)

            val phantom = (world.spawnEntity(location, EntityType.PHANTOM) as Phantom).apply {
                var random = "평범한"
                fakePhantom!!.updateMetadata<Phantom> {
                    random = customName!!.dropLast(3)
                }

                if (random == "거대한") {
                    size = 5
                    getAttribute(Attribute.GENERIC_MAX_HEALTH)?.baseValue = concept.largePhantomMaxHealth
                } else {
                    getAttribute(Attribute.GENERIC_MAX_HEALTH)?.baseValue = 0.1
                }

                getAttribute(Attribute.GENERIC_ATTACK_DAMAGE)?.baseValue = esper.getStatistic(concept.phantomAttackDamage)
                getAttribute(Attribute.GENERIC_FOLLOW_RANGE)?.baseValue = concept.range * 2

                health = getAttribute(Attribute.GENERIC_MAX_HEALTH)?.baseValue!!

                customName = "${player.name}의 $random 팬텀"
                isCustomNameVisible = true
                isSilent = true
                setShouldBurnInDay(false)
            }

            val team: Team? = Bukkit.getScoreboardManager().mainScoreboard.getEntryTeam(player.name)
            team?.addEntry(phantom.uniqueId.toString())
            phantoms.add(phantom)
            psychic.plugin.entityEventManager.registerEvents(phantom, this)

            fakePhantom!!.remove()
            bone.remove()
            fakePhantom = null
            fakeBone = null
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onDeath(event: EntityDeathEvent) {
        val entity = event.entity
        if (entity !is Player) {
            phantoms.remove(entity)
            psychic.plugin.entityEventManager.unregisterEvent(entity, this)

            val name = entity.customName!!.split(" ").drop(1).joinToString(" ")
            prefix.add(name.dropLast(3))

            if (phantoms.isEmpty()) {
                entity.killer?.let {
                    esper.player.sendMessage(text("마지막 남은 ${name}이 ${it.name}에게 살해당했습니다").decorate(TextDecoration.BOLD))
                }?: esper.player.sendMessage(text("마지막 남은 ${name}이 죽었습니다").decorate(TextDecoration.BOLD))
            } else {
                entity.killer?.let {
                    esper.player.sendMessage(text("${name}이 ${it.name}에게 살해당했습니다"))
                }?: esper.player.sendMessage(text("${name}이 죽었습니다"))
            }

            event.drops.clear()
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onDamaged(event: EntityDamageEvent) {
        if (event.entity !is Player) {
            if (event.cause == EntityDamageEvent.DamageCause.SUFFOCATION) event.isCancelled = true
        }
    }

    @EventHandler
    fun onTarget(event: EntityTargetEvent) {
        event.isCancelled = true
    }
}






