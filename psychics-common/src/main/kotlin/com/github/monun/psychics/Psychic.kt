/*
 * Copyright (c) 2020 monun
 *
 *  Licensed under the General Public License, Version 3.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/gpl-3.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.github.monun.psychics

import com.github.monun.psychics.plugin.PsychicPlugin
import com.github.monun.psychics.util.Times
import com.github.monun.tap.event.RegisteredEntityListener
import com.github.monun.tap.fake.FakeEntity
import com.github.monun.tap.fake.FakeProjectileManager
import com.github.monun.tap.ref.UpstreamReference
import com.github.monun.tap.task.Ticker
import com.github.monun.tap.task.TickerTask
import com.google.common.collect.ImmutableList
import net.md_5.bungee.api.ChatColor
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.attribute.Attribute
import org.bukkit.block.data.BlockData
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.boss.BossBar
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Entity
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerEvent
import org.bukkit.inventory.ItemStack
import java.util.*
import kotlin.math.max

class Psychic internal constructor(
    val concept: PsychicConcept
) {
    lateinit var plugin: PsychicPlugin
        private set

    lateinit var manager: PsychicManager
        private set

    /**
     * 마나량입니다.
     *
     * @exception IllegalArgumentException 유효하지 않은 객체일때 발생
     */
    var mana = 0.0
        set(value) {
            checkState()

            if (value != field) {
                val max = concept.mana
                field = value.coerceIn(0.0, max)
                updateManaBar()
            }
        }

    /**
     * 능력이 활성화된 시간입니다.
     *
     * 단위 = millis
     */
    var times = 0L
        private set

    /**
     * 능력 목록입니다.
     */
    val abilities: List<Ability<*>>

    /**
     * 현재 시전중인 채널입니다.
     *
     * @see Channel
     */
    var channeling: Channel? = null
        private set

    /**
     * 능력 활성화 여부입니다.
     *
     * @exception IllegalArgumentException 유효하지 않은 객체일때 발생
     */
    var isEnabled = false
        set(value) {
            checkState()

            if (field != value) {
                field = value

                if (value) {
                    onEnable()
                } else {
                    onDisable()
                }
            }
        }

    /**
     * 능력 유효상태입니다.
     */
    var valid = true
        private set

    private lateinit var esperRef: UpstreamReference<Esper>

    /**
     * 능력이 부여된 [Esper]입니다.
     */
    val esper: Esper
        get() = esperRef.get()

    private var manaBar: BossBar? = null

    private lateinit var castingBar: BossBar

    private lateinit var ticker: Ticker

    private lateinit var projectiles: FakeProjectileManager

    private lateinit var listeners: ArrayList<RegisteredEntityListener>

    private lateinit var fakeEntities: MutableSet<FakeEntity>

    private var prevUpdateTime = 0L

    init {
        abilities = ImmutableList.copyOf(concept.abilityConcepts.map { concept ->
            concept.createAbilityInstance()
        })
    }

    internal fun initialize(plugin: PsychicPlugin, manager: PsychicManager) {
        this.plugin = plugin
        this.manager = manager

        for (ability in abilities) {
            ability.initPsychic(this)
            ability.runCatching { onInitialize() }.onFailure { it.printStackTrace() }
        }
    }

    internal fun attach(esper: Esper) {
        require(!this::esperRef.isInitialized) { "Cannot redefine epser" }

        val player = esper.player

        esperRef = UpstreamReference(esper)
        castingBar = Bukkit.createBossBar(null, BarColor.WHITE, BarStyle.SOLID).apply {
            addPlayer(player)
            isVisible = false
        }
        ticker = Ticker.precision()
        projectiles = FakeProjectileManager()
        listeners = arrayListOf()
        fakeEntities = Collections.newSetFromMap(WeakHashMap<FakeEntity, Boolean>())

        if (concept.mana > 0.0) {
            manaBar = Bukkit.createBossBar(null, concept.manaColor, BarStyle.SEGMENTED_10).apply {
                addPlayer(player)
                isVisible = true
            }
        }

        updateManaBar()

        for (ability in abilities) {
            ability.runCatching { onAttach() }
        }
    }

    private fun detach() {
        manaBar?.removeAll()
        castingBar.removeAll()

        for (ability in abilities) {
            ability.runCatching {
                cooldownTime = 0L
                onDetach()
            }.onFailure(Throwable::printStackTrace)
        }
        esperRef.clear()
    }

    private fun onEnable() {
        isEnabled = true
        prevUpdateTime = Times.current

        for (ability in abilities) {
            ability.runCatching { onEnable() }.onFailure(Throwable::printStackTrace)
        }
    }

    private fun onDisable() {
        castingBar.isVisible = false
        ticker.cancelAll()
        projectiles.clear()
        listeners.run {
            for (registeredEntityListener in this) {
                registeredEntityListener.unregister()
            }
            clear()
        }
        fakeEntities.run {
            for (fakeEntity in this) {
                fakeEntity.remove()
            }
            clear()
        }
        for (ability in abilities) {
            ability.runCatching { onDisable() }.onFailure(Throwable::printStackTrace)
        }
    }

    private fun updateManaBar() {
        manaBar?.let { bar ->
            val current = mana
            val max = concept.mana

            bar.progress = current / max
            bar.setTitle("${current.toInt()} / ${max.toInt()}")
        }
    }

    companion object {
        internal const val NAME = "name"
        private const val MANA = "mana"
        private const val TIMES = "time"
        private const val ENABLED = "enabled"
        private const val ABILITIES = "abilities"
    }

    internal fun save(config: ConfigurationSection) {
        config[NAME] = concept.name
        config[MANA] = mana
        config[TIMES] = times
        config[ENABLED] = isEnabled

        val abilitiesSection = config.createSection(ABILITIES)

        for (ability in abilities) {
            val abilityName = ability.concept.name
            val abilitySection = abilitiesSection.createSection(abilityName)

            ability.save(abilitySection)
        }
    }

    internal fun load(config: ConfigurationSection) {
        mana = config.getDouble(MANA).coerceIn(0.0, concept.mana)
        times = max(0L, config.getLong(TIMES))

        config.getConfigurationSection(ABILITIES)?.let { abilitiesSection ->
            for (ability in abilities) {
                val abilityName = ability.concept.name
                val abilitySection = abilitiesSection.getConfigurationSection(abilityName)

                if (abilitySection != null)
                    ability.load(abilitySection)
            }
        }

        isEnabled = config.getBoolean(ENABLED)
    }

    /**
     * [AbilityConcept.wand] 속성이 같은 [Ability]를 반환합니다.
     */
    fun getAbilityByWand(item: ItemStack): Ability<*>? {
        for (ability in abilities) {
            val wand = ability.concept.internalWand

            if (wand != null && wand.isSimilar(item))
                return ability
        }

        return null
    }

    /**
     * 태스크를 delay만큼 후에 실행합니다.
     *
     * 능력이 비활성화 될 때 취소됩니다.
     *
     * @exception IllegalArgumentException 유효하지 않은 객체일때 발생
     * @exception IllegalArgumentException 활성화되지 않은 객체일때 발생
     */
    fun runTask(runnable: Runnable, delay: Long): TickerTask {
        checkState()
        checkEnabled()

        return ticker.runTask(runnable, delay)
    }

    /**
     * 태스크를 delay만큼 후 period마다 주기적으로 실행합니다.
     *
     * 능력이 비활성화 될 때 취소됩니다.
     *
     * @exception IllegalArgumentException 유효하지 않은 객체일때 발생
     * @exception IllegalArgumentException 활성화되지 않은 객체일때 발생
     */
    fun runTaskTimer(runnable: Runnable, delay: Long, period: Long): TickerTask {
        checkState()
        checkEnabled()

        return ticker.runTaskTimer(runnable, delay, period)
    }

    /**
     * 이벤트를 등록합니다.
     *
     * 범위는 해당 [Psychic]이 부여된 [org.bukkit.entity.Player]객체로 국한됩니다.
     *
     * 능력이 비활성화 될 때 해제됩니다.
     *
     * @exception IllegalArgumentException 유효하지 않은 객체일때 발생
     * @exception IllegalArgumentException 활성화되지 않은 객체일때 발생
     */
    fun registerEvents(listener: Listener) {
        checkState()
        checkEnabled()

        listeners.add(plugin.entityEventManager.registerEvents(esper.player, listener))
    }

    /**
     * 발사체를 발사합니다.
     *
     * 능력이 비활성화 될 때 제거됩니다.
     *
     * @exception IllegalArgumentException 유효하지 않은 객체일때 발생
     * @exception IllegalArgumentException 활성화되지 않은 객체일때 발생
     */
    fun launchProjectile(location: Location, projectile: PsychicProjectile) {
        checkState()
        checkEnabled()

        projectile.psychic = this
        projectiles.launch(location, projectile)
    }

    /**
     * 가상 [Entity]를 생성합니다.
     *
     * 능력이 비활성화 될 때 제거됩니다.
     *
     * @exception IllegalArgumentException 유효하지 않은 객체일때 발생
     * @exception IllegalArgumentException 활성화되지 않은 객체일때 발생
     */
    fun spawnFakeEntity(location: Location, entityClass: Class<out Entity>): FakeEntity {
        checkState()
        checkEnabled()

        val fakeEntity = manager.plugin.fakeEntityServer.spawnEntity(location, entityClass)
        fakeEntities.add(fakeEntity)

        return fakeEntity
    }

    /**
     * 가상 [org.bukkit.entity.FallingBlock]을 생성합니다.
     *
     * 능력이 비활성화 될 때 제거됩니다.
     *
     * @exception IllegalArgumentException 유효하지 않은 객체일때 발생
     * @exception IllegalArgumentException 활성화되지 않은 객체일때 발생
     */
    fun spawnFakeFallingBlock(location: Location, blockData: BlockData): FakeEntity {
        checkState()
        checkEnabled()

        val fakeEntity = manager.plugin.fakeEntityServer.spawnFallingBlock(location, blockData)
        fakeEntities.add(fakeEntity)

        return fakeEntity
    }

    /**
     * 마나를 소모합니다.
     *
     * 전달한 양보다 적다면 소모되지 않습니다.
     *
     * @return 소모 성공 여부
     */
    fun consumeMana(amount: Double): Boolean {
        if (mana >= amount) {
            mana -= amount

            return true
        }

        return false
    }

    internal fun startChannel(
        ability: ActiveAbility<*>,
        event: PlayerEvent,
        wandAction: ActiveAbility.WandAction,
        castingTime: Long,
        target: Any?
    ) {
        interruptChannel()

        channeling = Channel(ability, event, wandAction, castingTime, target)
        castingBar.apply {
            val abilityConcept = ability.concept
            setTitle("${ChatColor.BOLD}${concept.displayName}")
            color = abilityConcept.castingBarColor ?: BarColor.YELLOW
            progress = 0.0
            isVisible = true
        }
    }

    /**
     * 현재 실행중인 [Channel]을 중지시킵니다.
     */
    fun interruptChannel() {
        channeling?.let { channel ->
            channel.interrupt()
            channeling = null
        }
    }

    internal fun destroy() {
        isEnabled = false
        detach()
        valid = false
    }

    internal fun update() {
        val currentTime = Times.current
        val elapsedTime = currentTime - prevUpdateTime
        prevUpdateTime = currentTime
        times += elapsedTime

        regenMana(elapsedTime)
        regenHealth(elapsedTime)

        ticker.run()
        projectiles.update()

        channeling?.let { channel ->
            val remainingTime = channel.remainingTime

            castingBar.progress = 1.0 - remainingTime.toDouble() / channel.ability.concept.castingTime.toDouble()

            if (remainingTime > 0L) {
                channel.channel()
            } else {
                castingBar.setTitle(null)
                castingBar.isVisible = false
                channeling = null
                channel.cast()
            }
        }
    }

    private fun regenMana(elapsedTime: Long) {
        mana = (mana + concept.manaRegenPerSecond * (elapsedTime / 1000.0)).coerceIn(0.0, concept.mana)
    }

    private fun regenHealth(elapsedTime: Long) {
        val player = esper.player
        val health = player.health

        if (health > 0.0) {
            val maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0
            player.health =
                (player.health + concept.healthRegenPerSecond * (elapsedTime / 1000.0)).coerceIn(
                    0.0,
                    maxHealth
                )
        }
    }

    /**
     * 능력 유효 여부를 체크합니다.
     *
     * @exception IllegalArgumentException 유효하지 않은 객체일때 발생
     */
    fun checkState() {
        require(valid) { "Invalid Psychic@${System.identityHashCode(this).toString(16)}" }
    }

    /**
     * 능력 활성화 여부를 체크합니다.
     *
     * @exception IllegalArgumentException 활성화되지 않은 객체일때 발생
     */
    fun checkEnabled() {
        require(isEnabled) { "Disabled Psychic@${System.identityHashCode(this).toString(16)}" }
    }
}

/**
 * 시전중인 스킬정보를 담고있는 클래스입니다.
 */
class Channel internal constructor(
    val ability: ActiveAbility<*>,
    val event: PlayerEvent,
    val action: ActiveAbility.WandAction,
    castingTime: Long,
    val target: Any? = null
) {
    private val channelTime = Times.current + castingTime

    /**
     * 시전까지 남은시간
     */
    val remainingTime
        get() = max(0, channelTime - Times.current)

    internal fun channel() {
        ability.runCatching { onChannel(this@Channel) }.onFailure(Throwable::printStackTrace)
    }

    internal fun cast() {
        ability.runCatching { onCast(event, action, target) }.onFailure(Throwable::printStackTrace)
    }

    internal fun interrupt() {
        ability.runCatching { onInterrupt(this@Channel) }.onFailure(Throwable::printStackTrace)
    }
}