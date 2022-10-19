package io.github.anblus.psychics.ability.randomegg

import io.github.monun.psychics.Ability
import io.github.monun.psychics.AbilityConcept
import io.github.monun.psychics.AbilityType
import io.github.monun.tap.config.Name
import net.kyori.adventure.text.Component.text
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.event.player.PlayerEggThrowEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector
import kotlin.math.round
import kotlin.random.Random.Default.nextDouble
import kotlin.random.Random.Default.nextInt

// 나 전투력 300만, 좀비라고.
@Name("random-egg")
class AbilityConceptRandomEgg : AbilityConcept() {

    init {
        displayName = "이스터에그"
        type = AbilityType.PASSIVE
        description = listOf(
            text("달걀에서 꼭 닭이 태어날 필요는 없습니다."),
        )
        wand = ItemStack(Material.EGG)
    }

}

class AbilityRandomEgg : Ability<AbilityConceptRandomEgg>(), Listener {

    val nameList = arrayOf("아기 닭", "아기 돼지", "아기 소", "아기 양", "아기 좀비",
        "스켈레톤", "폭탄", "철 주괴", "번개", "소환사",
        "눈 골렘 무리", "다이아몬드", "아기 말", "화살 폭탄", "아기 좀비 무리",
        "크리퍼", "충전된 크리퍼", "조약돌", "달걀", "용암",
        "거미줄", "내구도 0 밀치기 Ⅹ 나무 검", "아기 주민", "파괴수", "마녀",
        "침략 무리", "슈퍼 좀비", "다이아몬드 블럭", "앵무새 무리", "판다",
        "폭탄 대포", "카트들", "엔더맨", "좀비 피글린 군단", "가디언",
        "고양이", "여우", "물", "가루눈", "경험치 병",
        "거인", "염소 무리", "라마 무리", "슬라임", "길들여진 늑대",
        "빵", "바다의 심장", "마법이 부여된 황금 사과", "케이크", "위더",
        "분노한 늑대 무리", "벌 무리", "무작위 포션 효과", "무작위 문구", "거미",
        "팬텀 떼", "셜커", "아기 버섯소", "슈퍼 스켈레톤", "슈퍼 크리퍼",
        "살인마 토끼 무리", "엔더 드래곤", "기반암")

    val ratioList = arrayOf(500, 400, 400, 400, 250,
        245, 112, 278, 80, 25,
        193, 46, 343, 68, 83,
        170, 125, 310, 145, 74,
        63, 100, 248, 36, 141,
        23, 90, 27, 159, 118,
        59, 107, 150, 60, 91,
        140, 135, 184, 103, 118,
        33, 121, 142, 94, 178,
        246, 64, 39, 131, 5,
        72, 84, 167, 55, 221,
        128, 89, 46, 85, 70,
        95, 1, 8)

    val numberList = arrayOf(arrayOf(1, 1), arrayOf(1, 1), arrayOf(1, 1), arrayOf(1, 1), arrayOf(1, 1),
        arrayOf(1, 1), arrayOf(1, 1), arrayOf(2, 7), arrayOf(1, 1), arrayOf(1, 1),
        arrayOf(6, 8), arrayOf(2, 4), arrayOf(1, 1), arrayOf(40, 50), arrayOf(6, 8),
        arrayOf(1, 1), arrayOf(1, 1), arrayOf(40, 130), arrayOf(3, 4), arrayOf(1, 1),
        arrayOf(1, 1), arrayOf(1, 1), arrayOf(1, 1), arrayOf(1, 1), arrayOf(1, 1),
        arrayOf(1, 1), arrayOf(1, 1), arrayOf(1, 1), arrayOf(5, 9), arrayOf(1, 1),
        arrayOf(6, 12), arrayOf(1, 1), arrayOf(1, 1), arrayOf(18, 24), arrayOf(1, 2),
        arrayOf(1, 1), arrayOf(1, 1), arrayOf(1, 1), arrayOf(1, 1), arrayOf(15, 20),
        arrayOf(1, 1), arrayOf(5, 8), arrayOf(3, 5), arrayOf(1, 1), arrayOf(1, 3),
        arrayOf(2, 12), arrayOf(1, 1), arrayOf(1, 1), arrayOf(1, 1), arrayOf(1, 1),
        arrayOf(6, 8), arrayOf(8, 12), arrayOf(1, 1), arrayOf(1, 1), arrayOf(1, 1),
        arrayOf(2, 5), arrayOf(1, 1), arrayOf(1, 1), arrayOf(1, 1), arrayOf(1, 1),
        arrayOf(2, 3), arrayOf(1, 1), arrayOf(1, 1))

    val contentList = arrayOf(EntityType.CHICKEN, EntityType.PIG, EntityType.COW, EntityType.SHEEP, EntityType.ZOMBIE,
        EntityType.SKELETON, EntityType.PRIMED_TNT, Material.IRON_INGOT, null, EntityType.EVOKER,
        EntityType.SNOWMAN, Material.DIAMOND, EntityType.HORSE, EntityType.ARROW, EntityType.ZOMBIE,
        EntityType.CREEPER, EntityType.CREEPER, Material.COBBLESTONE, Material.EGG, Material.LAVA,
        Material.COBWEB, Material.WOODEN_SWORD, EntityType.VILLAGER, EntityType.RAVAGER, EntityType.WITCH,
        null, EntityType.ZOMBIE, Material.DIAMOND_BLOCK, EntityType.PARROT, EntityType.PANDA,
        EntityType.PRIMED_TNT, EntityType.MINECART, EntityType.ENDERMAN, EntityType.ZOMBIFIED_PIGLIN, EntityType.GUARDIAN,
        EntityType.CAT, EntityType.FOX, Material.WATER, Material.POWDER_SNOW, EntityType.THROWN_EXP_BOTTLE,
        EntityType.GIANT, EntityType.GOAT, EntityType.LLAMA, EntityType.SLIME, EntityType.WOLF,
        Material.BREAD, Material.HEART_OF_THE_SEA, Material.ENCHANTED_GOLDEN_APPLE, Material.CAKE, EntityType.WITHER,
        EntityType.WOLF, EntityType.BEE, EntityType.SPLASH_POTION, EntityType.ARMOR_STAND, EntityType.SPIDER,
        EntityType.PHANTOM, EntityType.SHULKER, EntityType.MUSHROOM_COW, EntityType.SKELETON, EntityType.CREEPER,
        EntityType.RABBIT, EntityType.ENDER_DRAGON, Material.BEDROCK)

    val blockList = arrayOf(Material.LAVA, Material.COBWEB, Material.DIAMOND_BLOCK, Material.WATER, Material.POWDER_SNOW,
        Material.CAKE, Material.BEDROCK)

    val babyList = arrayOf(EntityType.CHICKEN, EntityType.PIG, EntityType.COW, EntityType.SHEEP, EntityType.ZOMBIE,
        EntityType.HORSE, EntityType.VILLAGER, EntityType.MUSHROOM_COW)

    var sumNumber: Int = 0

    var ratioRangeList = ArrayList<Int>()

    var percentList = ArrayList<Double>()

    val potionList = "ABSORPTION, BAD_OMEN, BLINDNESS, CONDUIT_POWER, CONFUSION, DAMAGE_RESISTANCE, DOLPHINS_GRACE, FAST_DIGGING, FIRE_RESISTANCE, GLOWING, HARM, HEAL, HEALTH_BOOST, HERO_OF_THE_VILLAGE, HUNGER, INCREASE_DAMAGE, INVISIBILITY, JUMP, LEVITATION, LUCK, NIGHT_VISION, POISON, REGENERATION, SATURATION, SLOW, SLOW_DIGGING, SLOW_FALLING, SPEED, UNLUCK, WATER_BREATHING, WEAKNESS, WITHER".split(", ")

    val messageList = arrayOf("빵빵 터지는 TNT 대포가 나오길 바라셨나요? 여기 웃음이 빵빵 터지는 문구가 있습니다!", "이 문구는 달걀 하나의 가치가 있습니다.", "닭이 먼저인가, 달걀이 먼저인가? <- " +
            "글쎄요, 그 전에 먼저 달걀에서 닭이 안 나오는 걸요.", "달걀을 마구잡이로 난사 하지 마시고 내용물 하나 하나를 주의 깊게 봐보세요. 이 문구도요!",
        "만약 무작위 문구가 뜬 게 꽝이라고 생각 하셨다면, 제대로 생각하셨습니다.", "굳이 이 문구 목록들을 다 찾아보려 하시지 마세요... 당신이 본 그것들이 문구의 전부일 수도 있잖아요!",
        "당신은 무척 운이 좋군요. 이 문구를 띄움으로써 무시무시한 위더의 소환을 면했으니까요.", "전부터 궁금했던건데, 달걀프라이와 달걀후라이. 둘 중 무엇이 맞아요?", "머리에 달걀을 맞은 사람들은 모두 200년 안에 사망에 이르렀습니다.",
        "달걀은 껍데기일까요, 껍질일까요?", "달걀의 모양이 타원형인 이유에 대해서는 수많은 가설이 있습니다.")

    override fun onEnable() {
        val ratio = ratioList
        val max = ratio.size - 1
        psychic.registerEvents(this)
        sumNumber = ratioList.sum()
        var currentSum = 0
        for (i in 0..max) {
            ratioRangeList.add(ratio[i] + currentSum)
            currentSum += ratio[i]
        }
        for (i in 0..max) {
            percentList.add(round((ratio[i].toDouble() / sumNumber.toDouble()) * 10000) / 100)
        }
    }

    fun checkConsonant(word: String): String {
        val lastLetter = word[word.length - 1]
        val uni = lastLetter.code
        return if ((uni - 44032) % 28 != 0) "이" else "가"
    }

    @EventHandler
    fun onThrowing(event: PlayerEggThrowEvent) {
        val egg = event.egg
        val player = esper.player
        val world = player.world
        event.isHatching = false
        val random = nextInt(1, sumNumber)
        var result = 0
        repeat(ratioList.size) { i ->
            if (i == 0) {
                if (random in 1..ratioRangeList[i]) result = 0
            } else {
                if (random in ratioRangeList[i - 1]..ratioRangeList[i]) result = i
            }
        }
        val percent = percentList[result]
        var color: String
        val center = sumNumber / ratioList.size
        val rare = center.toDouble() / ratioList[result]
        when (rare) {
            in 0.0..0.7-> color = "${ChatColor.GRAY}"
            in 0.7..1.3 -> color = "${ChatColor.GREEN}"
            in 1.3..1.8 -> color = "${ChatColor.BLUE}${ChatColor.BOLD}"
            in 1.8..2.2 -> color = "${ChatColor.DARK_PURPLE}${ChatColor.BOLD}${ChatColor.ITALIC}"
            else -> color = "${ChatColor.YELLOW}${ChatColor.BOLD}${ChatColor.ITALIC}${ChatColor.UNDERLINE}"
        }
        player.sendMessage("알에서 ${color}${nameList[result]}${ChatColor.WHITE}${checkConsonant(nameList[result])} 부화하였다. (${color}${percent}${ChatColor.WHITE}%)")
        val location = egg.location
        if (contentList[result] is Material) {
            if (contentList[result] in blockList) {
                var block = location.block
                if (block.type != Material.BEDROCK) {
                    block.setType(contentList[result] as Material)
                }
            } else {
                var item = world.dropItem(location, ItemStack(contentList[result] as Material, nextInt(numberList[result][0],numberList[result][1]+1))).apply { velocity = Vector(0, -1, 0) }
                if (nameList[result] == "내구도 0 밀치기 Ⅹ 나무 검") {
                    val damage = item.itemStack.itemMeta as Damageable
                    item.itemStack.itemMeta = damage.apply { setDamage(59) }
                    item.itemStack.addUnsafeEnchantment(Enchantment.KNOCKBACK, 10)
                }
            }
        } else if (contentList[result] is EntityType) {
            repeat(nextInt(numberList[result][0],numberList[result][1]+1)) {
                var entity = world.spawnEntity(location, contentList[result] as EntityType, CreatureSpawnEvent.SpawnReason.EGG)
                if (nameList[result] == "슈퍼 좀비") {
                    entity = entity as Zombie
                    entity.customName = "${ChatColor.DARK_PURPLE}${ChatColor.BOLD}슈퍼 좀비"
                    entity.equipment.setItemInMainHand(ItemStack(Material.DIAMOND_SWORD))
                    entity.isCustomNameVisible = true
                    entity.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 999999, 2))
                    entity.equipment.setItemInOffHand(ItemStack(Material.EGG, 5))
                    entity.equipment.setBoots(ItemStack(Material.DIAMOND_BOOTS))
                    entity.equipment.setLeggings(ItemStack(Material.DIAMOND_LEGGINGS))
                    entity.equipment.setChestplate(ItemStack(Material.DIAMOND_CHESTPLATE))
                    entity.equipment.setHelmet(ItemStack(Material.DIAMOND_HELMET))
                    entity.equipment.itemInMainHandDropChance = 0.0f
                    entity.equipment.itemInOffHandDropChance = 1.0f
                    entity.equipment.bootsDropChance = 0.0f
                    entity.equipment.leggingsDropChance = 0.0f
                    entity.equipment.chestplateDropChance = 0.0f
                    entity.equipment.helmetDropChance = 0.0f
                } else if (nameList[result] == "슈퍼 스켈레톤") {
                    entity = entity as Skeleton
                    entity.customName = "${ChatColor.DARK_GRAY}${ChatColor.BOLD}슈퍼 스켈레톤"
                    entity.equipment.setItemInMainHand(ItemStack(Material.BOW).apply {
                        addEnchantment(Enchantment.ARROW_DAMAGE, 3)
                        addEnchantment(Enchantment.ARROW_FIRE, 1)
                        addEnchantment(Enchantment.ARROW_KNOCKBACK, 2)
                    })
                    entity.isCustomNameVisible = true
                    entity.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 999999, 3))
                    entity.equipment.setItemInOffHand(ItemStack(Material.EGG, 5))
                    entity.equipment.setBoots(ItemStack(Material.IRON_BOOTS))
                    entity.equipment.setLeggings(ItemStack(Material.IRON_LEGGINGS))
                    entity.equipment.setChestplate(ItemStack(Material.IRON_CHESTPLATE))
                    entity.equipment.setHelmet(ItemStack(Material.IRON_HELMET))
                    entity.equipment.itemInMainHandDropChance = 0.0f
                    entity.equipment.itemInOffHandDropChance = 1.0f
                    entity.equipment.bootsDropChance = 0.0f
                    entity.equipment.leggingsDropChance = 0.0f
                    entity.equipment.chestplateDropChance = 0.0f
                    entity.equipment.helmetDropChance = 0.0f
                } else if (nameList[result] == "슈퍼 크리퍼") {
                    entity = entity as Creeper
                    entity.customName = "${ChatColor.DARK_GREEN}${ChatColor.BOLD}슈퍼 크리퍼"
                    entity.isPowered = true
                    entity.isCustomNameVisible = true
                    entity.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 999999, 4))
                    entity.equipment.setItemInOffHand(ItemStack(Material.EGG, 5))
                    entity.equipment.itemInOffHandDropChance = 1.0f
                    entity.maxHealth = 1.0
                    entity.explosionRadius = 10

                } else if (contentList[result] in babyList) {
                    entity = entity as Ageable
                    entity.setBaby()
                } else if (entity.type == EntityType.PRIMED_TNT) {
                    entity = entity as TNTPrimed
                    if (nameList[result] == "폭탄 대포") {
                        if (it == 0) entity.fuseTicks = 50
                        else {
                            entity.fuseTicks = 70
                            entity.velocity = Vector(nextDouble(0.2) - 0.1, nextDouble(0.2), nextDouble(0.2) - 0.1)
                        }
                    } else entity.fuseTicks = 50
                } else if (entity.type == EntityType.ARROW) {
                    entity.velocity = Vector(nextDouble(2.4) - 1.2, nextDouble(1.0), nextDouble(2.4) - 1.2)
                }
                else if (nameList[result] == "충전된 크리퍼") {
                    entity = entity as Creeper
                    entity.isPowered = true
                } else if (entity.type == EntityType.MINECART) {
                    world.spawnEntity(location, EntityType.MINECART_CHEST, CreatureSpawnEvent.SpawnReason.EGG).apply {velocity = Vector(nextDouble(1.0) - 0.5, 0.2, nextDouble(1.0) - 0.5)}
                    world.spawnEntity(location, EntityType.MINECART_FURNACE, CreatureSpawnEvent.SpawnReason.EGG).apply {velocity = Vector(nextDouble(1.0) - 0.5, 0.2, nextDouble(1.0) - 0.5)}
                    world.spawnEntity(location, EntityType.MINECART_HOPPER, CreatureSpawnEvent.SpawnReason.EGG).apply {velocity = Vector(nextDouble(1.0) - 0.5, 0.2, nextDouble(1.0) - 0.5)}
                } else if (entity.type == EntityType.THROWN_EXP_BOTTLE) entity.velocity = Vector(nextDouble(0.4) - 0.2, 1.0, nextDouble(0.4) - 0.2)
                else if (entity.type == EntityType.SLIME) {
                    entity = entity as Slime
                    entity.size = nextInt(8, 12)
                } else if (entity.type == EntityType.WOLF) {
                    entity = entity as Wolf
                    if (nameList[result] == "길들여진 늑대") entity.owner = player
                    else psychic.runTaskTimer({ (entity as Wolf).isAngry = true }, 0L, 20L)
                } else if (entity.type == EntityType.PHANTOM) {
                    entity = entity as Phantom
                    entity.size = nextInt(1, 5)
                } else if (entity.type == EntityType.RABBIT) {
                    entity = entity as Rabbit
                    entity.rabbitType = Rabbit.Type.THE_KILLER_BUNNY
                } else if (entity.type == EntityType.SPLASH_POTION) {
                    entity.velocity = Vector(0.0, -10.0, 0.0)
                    val potionType = PotionEffectType.getByName(potionList[nextInt(0,potionList.size)]) as PotionEffectType
                    player.sendMessage("효과: ${ChatColor.BOLD}${potionType.name}")
                    val potion = PotionEffect(potionType, 600, 1)
                    location.getNearbyEntities(4.0, 2.0, 4.0
                    ).forEach { target ->
                        if (target is LivingEntity) {
                            target.addPotionEffect(potion)
                        }
                    }
                } else if (entity.type == EntityType.ARMOR_STAND) {
                    entity.customName = "${ChatColor.BOLD}${messageList[nextInt(0, messageList.size)]}"
                    entity.isCustomNameVisible = true
                    entity = entity as ArmorStand
                    entity.isMarker = true
                    entity.isVisible = false
                    entity.setGravity(false)
                    psychic.runTask({entity.remove()}, 200L)
                }
            }
        } else if (contentList[result] == null) {
            if (nameList[result] == "번개") {
                world.strikeLightning(location)
            } else if (nameList[result] == "침략 무리") {
                world.spawnEntity(location, EntityType.RAVAGER, CreatureSpawnEvent.SpawnReason.EGG)
                repeat(nextInt(2,4)) {
                    world.spawnEntity(location, EntityType.VINDICATOR, CreatureSpawnEvent.SpawnReason.EGG)
                }
                repeat(nextInt(4,7)) {
                    world.spawnEntity(location, EntityType.PILLAGER, CreatureSpawnEvent.SpawnReason.EGG)
                }
                repeat(nextInt(1,3)) {
                    world.spawnEntity(location, EntityType.WITCH, CreatureSpawnEvent.SpawnReason.EGG)
                }
            }
        }

    }
}




