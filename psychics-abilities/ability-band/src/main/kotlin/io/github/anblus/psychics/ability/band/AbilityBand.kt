package io.github.anblus.psychics.ability.band

import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.AbilityType
import io.github.monun.psychics.ActiveAbility
import io.github.monun.psychics.Channel
import io.github.monun.psychics.attribute.EsperAttribute
import io.github.monun.psychics.attribute.EsperStatistic
import io.github.monun.psychics.tooltip.TooltipBuilder
import io.github.monun.psychics.tooltip.stats
import io.github.monun.psychics.util.hostileFilter
import io.github.monun.tap.config.Config
import io.github.monun.tap.config.Name
import io.github.monun.tap.event.EntityProvider
import io.github.monun.tap.event.TargetEntity
import io.github.monun.tap.fake.FakeEntity
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.key.Key
import net.kyori.adventure.key.Key.key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.sound.Sound.sound
import net.kyori.adventure.sound.SoundStop
import net.kyori.adventure.text.Component.space
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title.Times
import net.kyori.adventure.title.Title.title
import org.bukkit.*
import org.bukkit.attribute.Attribute
import org.bukkit.block.BlockFace
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.*
import org.bukkit.event.player.PlayerEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.LeatherArmorMeta
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scoreboard.Team
import java.time.Duration.ofNanos
import java.time.Duration.ofSeconds
import kotlin.math.pow
import kotlin.random.Random.Default.nextInt

// 범위 안에서 활동하는 몬스터 소환
@Name("band")
class AbilityConceptBand : AbilityConcept() {

    @Config
    var vocalistStat = mutableListOf(
        EsperStatistic.of(EsperAttribute.ATTACK_DAMAGE to 20.0),
        EsperStatistic.of(EsperAttribute.ATTACK_DAMAGE to 5.0),
        0.35)

    @Config
    var guitaristStat = mutableListOf(
        EsperStatistic.of(EsperAttribute.ATTACK_DAMAGE to 12.0),
        EsperStatistic.of(EsperAttribute.ATTACK_DAMAGE to 4.2),
        0.35)

    @Config
    var guitaristArrowFireTick = 50

    @Config
    var bassistStat = mutableListOf(
        EsperStatistic.of(EsperAttribute.ATTACK_DAMAGE to 16.0),
        EsperStatistic.of(EsperAttribute.ATTACK_DAMAGE to 3.0),
        0.35)

    @Config
    var bassistArrowSlowDuration = 50

    @Config
    val drummerStat = mutableListOf(
        EsperStatistic.of(EsperAttribute.ATTACK_DAMAGE to 18.0),
        EsperStatistic.of(EsperAttribute.ATTACK_DAMAGE to 3.5),
        0.35)

    @Config
    val drummerBlindnessDuration = 40

    init {
        displayName = "악단"
        type = AbilityType.ACTIVE
        cost = 40.0
        durationTime = 15000L
        cooldownTime = 15000L
        castingTime = 4000L
        range = 16.0
        description = listOf(
            text("0~4명 사이로 이루어진 악단을 소환합니다."),
            text("악단의 구성원들은 범위 안의 적들을 공격 대상으로 삼습니다."),
            text("악단의 구성원들의 최대 체력과 공격력은 초월에 비례합니다."),
            text("지속 시간이 지나면 악단의 모든 구성원들은 소멸합니다."),
            space(),
            text("악단의 구성은 다음과 같습니다."),
            text("  ${ChatColor.DARK_GREEN}보컬리스트: 좀비"),
            text("  ${ChatColor.DARK_RED}기타리스트: 스켈레톤, 화염 화살"),
            text("  ${ChatColor.GOLD}베이시스트: 스트레이, 강력한 구속 화살"),
            text("  ${ChatColor.DARK_BLUE}드러머: 위더 스켈레톤, 공격에 실명 효과"),
            )
        wand = ItemStack(Material.STICK)
    }
    
    override fun onRenderTooltip(tooltip: TooltipBuilder, stats: (EsperStatistic) -> Double) {
        tooltip.stats(stats(EsperStatistic.of(EsperAttribute.ATTACK_DAMAGE to 1.0))) { NamedTextColor.DARK_PURPLE to "능력치 배수" to "배" }
    }
}

class AbilityBand : ActiveAbility<AbilityConceptBand>(), Listener {
    companion object {
        private val key: Array<Key> = arrayOf(key("music_disc.11"), key("music_disc.13"), key("music_disc.blocks"),
            key("music_disc.cat"), key("music_disc.chirp"), key("music_disc.far"), key("music_disc.mall"), key("music_disc.mellohi"),
            key("music_disc.pigstep"), key("music_disc.stal"), key("music_disc.strad"), key("music_disc.wait"), key("music_disc.ward"),
            key("music_disc.pigstep"), key("music_disc.wait"), key("music_disc.mellohi"))

        private val title: Array<String> = arrayOf("다가오는 그림자", "불결한 예측", "또 다른 미래",
            "노을 아래에서", "고향으로 떠나며", "어딘가", "잔잔한 파도", "혁명을 앞두고" ,
            "돌이킬 수 없는", "은밀하게 위대하게", "마지막 휴양", "지루한 기다림", "죽음을 미루다",
            "위험한 추격전", "디지털 어드벤쳐", "풀리지 않는 수수께끼")

        private val pitch: Array<Float> = arrayOf(1.0F, 1.0F, 1.0F,
            1.0F, 1.0F, 1.0F, 1.0F, 0.1F,
            0.1F, 1.0F, 1.0F, 0.1F, 1.0F,
            1.5F, 2.0F, 2.0F)
    }

    private var speaker: FakeEntity? = null

    private var composition: Array<Boolean> = Array(4) {false}

    private var members: MutableList<Mob> = mutableListOf()

    private var isUsing: Boolean = false

    private var sound: Sound? = null

    private var commonTarget: LivingEntity? = null

    private var arrows: MutableList<Entity> = mutableListOf()

    override fun onEnable() {
        psychic.registerEvents(this)
        psychic.runTaskTimer(this::onTick, 0L, 1L)
    }

    override fun onDisable() {
        cancel()
    }

    override fun onInitialize() {
        targeter = {
            val player = esper.player
            val start = player.eyeLocation
            val world = start.world

            world.rayTraceBlocks(
                start,
                start.direction,
                5.0,
                FluidCollisionMode.NEVER,
                true,
            )?.let { hitResult ->
                if (hitResult.hitBlockFace == BlockFace.UP) hitResult.hitPosition.toLocation(world) else null
            }
        }
    }

    private fun onTick() {
        if (isUsing && durationTime <= 0L) cancel()
        else if (isUsing) {
            speaker?.let { speaker ->
                val location = speaker.location.apply { y += 1.0 }
                val world = location.world
                val range = concept.range
                world.spawnParticle(Particle.NOTE, location, range.toInt(), range, 0.5, range)
            }
        }
        speaker?.let { speaker ->
            speaker.moveTo(speaker.location.clone().apply {
                yaw += 5f
                pitch += 5f
            })
        }
        if (members.isNotEmpty()) {
            commonTarget = getCommonTarget()
            members.forEach { member -> member.target = commonTarget }
        }
    }

    private fun getCommonTarget(): LivingEntity? {
        return speaker?.let { speaker ->
            val player = esper.player
            val targets = player.world.getNearbyLivingEntities(speaker.location, concept.range).filter { target ->
                (target is Player || target is Monster) && player.hostileFilter().test(target) && !members.contains(target) }
            if (targets.isNotEmpty()) {
                targets.sortedBy { target -> speaker.location.distance(target.location) + if (target is Player) 0 else 10 }[0] as LivingEntity
            } else null
        }
    }

    private fun cancel() {
        isUsing = false
        speaker?.remove()
        speaker = null
        composition = Array(4) {false}
        sound?.let { sound -> esper.player.world.stopSound(SoundStop.namedOnSource(sound.name(), sound.source())) }
        sound = null
        for (member in members) {
            member.run {
                remove()
                psychic.plugin.entityEventManager.unregisterEvent(member, this@AbilityBand)
                world.spawnParticle(Particle.SNOWFLAKE, location, 16, 0.3, 1.0, 0.3, 0.03)
            }
        }
        members.clear()
        for (arrow in arrows) { psychic.plugin.entityEventManager.unregisterEvent(arrow, this@AbilityBand) }
        arrows.clear()
    }

    override fun onChannel(channel: Channel) {
        speaker?.let { speaker ->
            val location = speaker.location.apply { y += 1.0 }
            val world = location.world
            val range = concept.range
            world.spawnParticle(Particle.NOTE, location, range.toInt(), range, 0.5, range)
        }?: run {
            val location = channel.target as Location
            val world = location.world

            composition = Array(4) { nextInt(0, 2) == 0 }
            val page = composition.toInt()

            sound = sound(key[page], Sound.Source.PLAYER, 2.0f, pitch[page])

            sound?.let { sound -> world.playSound(sound, location.x, location.y, location.z) }

            world.getNearbyPlayers(location, concept.range).forEach { target -> (target as Audience).showTitle(
                title(text("악단 등장").color(NamedTextColor.DARK_GRAY),
                    text("제${page + 1}악장, ~${title[page]}~").color(NamedTextColor.WHITE).decorate(TextDecoration.ITALIC),
                    Times.of(ofNanos((5e+8).toLong()), ofSeconds(3L), ofNanos((5e+8).toLong())))
            )}

            speaker = psychic.spawnFakeEntity(location.apply { y -= 1.0 }, ArmorStand::class.java).apply {
                updateMetadata<ArmorStand> {
                    isVisible = false
                    isMarker = true
                }
                updateEquipment {
                    helmet = ItemStack(Material.NOTE_BLOCK)
                }
            }
        }
    }

    private fun Array<Boolean>.toInt(): Int {
        var result = 0
        this.forEachIndexed { i, bool -> result += 2.0.pow(i).toInt() * (if (bool) 1 else 0) }
        return result
    }


    override fun onCast(event: PlayerEvent, action: WandAction, target: Any?) {
        val player = esper.player
        val world = player.world

        isUsing = true
        durationTime = concept.durationTime
        cooldownTime = concept.cooldownTime
        psychic.consumeMana(concept.cost)

        speaker?.let { speaker ->
            val location = speaker.location.apply { y += 1.0 }
            world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, location, 480, 2.5, 0.0, 2.5, 0.08)
            world.playSound(location, "entity.bat.takeoff", SoundCategory.AMBIENT, 1.0F, 0.1F)

            repeat(4) { i ->
                if (composition[i]) {
                    var monster: Mob? = null
                    var color = DyeColor.BLACK.color
                    var name = ""
                    var stat = listOf<Any>()
                    var handItem = ItemStack(Material.IRON_SWORD)
                    when (i) {
                        0 -> {
                            color = Color.GREEN
                            name = "${ChatColor.DARK_GREEN}보컬리스트"
                            stat = concept.vocalistStat
                            handItem = ItemStack(Material.LIGHTNING_ROD)
                            monster = (world.spawnEntity(location, EntityType.ZOMBIE) as Zombie).apply {
                                getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE)?.baseValue = 0.3
                                setAdult()
                            }
                        }
                        1 -> {
                            color = Color.MAROON
                            name = "${ChatColor.DARK_RED}기타리스트"
                            stat = concept.guitaristStat
                            handItem = ItemStack(Material.BOW).apply {
                                addUnsafeEnchantment(Enchantment.THORNS, 1)
                            }
                            monster = world.spawnEntity(location, EntityType.SKELETON) as Skeleton
                        }
                        2 -> {
                            color = Color.ORANGE
                            name = "${ChatColor.GOLD}베이시스트"
                            stat = concept.bassistStat
                            handItem = ItemStack(Material.BOW).apply {
                                addUnsafeEnchantment(Enchantment.THORNS, 1)
                            }
                            monster = world.spawnEntity(location, EntityType.STRAY) as Stray
                        }
                        3 -> {
                            color = Color.NAVY
                            name = "${ChatColor.DARK_BLUE}드러머"
                            stat = concept.drummerStat
                            handItem = ItemStack(Material.TRIPWIRE_HOOK)
                            monster = (world.spawnEntity(location, EntityType.WITHER_SKELETON) as WitherSkeleton).apply {
                                getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE)?.baseValue = 0.6
                            }
                        }
                    }
                    monster?.let { mob ->
                        mob.equipment.run {
                            val meta = ItemStack(Material.LEATHER_HELMET).itemMeta as LeatherArmorMeta
                            meta.setColor(color)
                            helmet = ItemStack(Material.LEATHER_HELMET).apply { itemMeta = meta }
                            chestplate = ItemStack(Material.LEATHER_CHESTPLATE).apply { itemMeta = meta }
                            leggings = ItemStack(Material.LEATHER_LEGGINGS).apply { itemMeta = meta }
                            boots = ItemStack(Material.LEATHER_BOOTS).apply { itemMeta = meta }
                            setItemInMainHand(handItem)
                            helmetDropChance = 0f
                            chestplateDropChance = 0f
                            leggingsDropChance = 0f
                            bootsDropChance = 0f
                            itemInMainHandDropChance = 0f
                        }

                        mob.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.baseValue = esper.getStatistic(stat[0] as EsperStatistic)
                        mob.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE)?.baseValue = esper.getStatistic(stat[1] as EsperStatistic)
                        mob.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED)?.baseValue = stat[2] as Double
                        mob.getAttribute(Attribute.GENERIC_FOLLOW_RANGE)?.baseValue = concept.range * 2

                        mob.health = mob.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.baseValue!!

                        mob.customName = "${player.name} 악단의 $name"
                        mob.isCustomNameVisible = true
                        mob.isSilent = true

                        val team: Team? = Bukkit.getScoreboardManager().mainScoreboard.getEntryTeam(player.name)
                        team?.addEntry(mob.uniqueId.toString())
                        members.add(mob)
                        psychic.plugin.entityEventManager.registerEvents(mob, this)
                    }
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    @TargetEntity(EntityProvider.EntityDamageByEntity.Damager::class)
    fun onAttack(event: EntityDamageByEntityEvent) {
        val entity = event.damager
        val target = event.entity as LivingEntity
        val player = esper.player
        val team: Team? = Bukkit.getScoreboardManager().mainScoreboard.getEntryTeam(player.name)

        if (team != null) {
            if (team.hasEntry(if (target is Player) target.name else target.uniqueId.toString())) {
                event.isCancelled = true
                return
            }
        } else {
            if (target == player || members.contains(target)) {
                event.isCancelled = true
                return
            }
        }

        if (entity is WitherSkeleton) {
            target.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, concept.drummerBlindnessDuration, 0, false, false))
            psychic.runTask({target.removePotionEffect(PotionEffectType.WITHER)}, 1L)
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onShootBow(event: EntityShootBowEvent) {
        val entity = event.entity
        val arrow = event.projectile
        if (entity is Skeleton) {
            arrow.fireTicks = concept.guitaristArrowFireTick
            (arrow as Arrow).damage = esper.getStatistic(concept.guitaristStat[1] as EsperStatistic)
            psychic.runTask({
                psychic.plugin.entityEventManager.registerEvents(arrow, this)
                arrows.add(arrow)
            }, 1L)
        } else if (entity is Stray) {
            (arrow as Arrow).apply {
                color = Color.GRAY
                addCustomEffect(PotionEffect(PotionEffectType.SLOW, concept.bassistArrowSlowDuration, 3, false, false), true)
                damage = esper.getStatistic(concept.bassistStat[1] as EsperStatistic)
            }
            psychic.runTask({
                psychic.plugin.entityEventManager.registerEvents(arrow, this)
                arrows.add(arrow)
            }, 1L)
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onDeath(event: EntityDeathEvent) {
        val entity = event.entity
        if (entity !is Player) {
            members.remove(entity)
            psychic.plugin.entityEventManager.unregisterEvent(entity, this@AbilityBand)
            event.drops.clear()
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onHit(event: ProjectileHitEvent) {
        psychic.runTask({
            val arrow = event.entity
            psychic.plugin.entityEventManager.unregisterEvent(arrow, this@AbilityBand)
            arrows.remove(arrow)
        }, 1L)
    }

    @EventHandler
    fun onTarget(event: EntityTargetEvent) {
        event.isCancelled = true
    }
}






