/*
 * Copyright (c) 2020 Noonmaru
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

package com.github.noonmaru.psychics

import com.github.noonmaru.psychics.attribute.EsperStatistic
import com.github.noonmaru.psychics.tooltip.TooltipBuilder
import com.github.noonmaru.tap.config.Config
import com.github.noonmaru.tap.config.RangeDouble
import com.github.noonmaru.tap.config.computeConfig
import com.google.common.collect.ImmutableList
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.BaseComponent
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.HoverEvent
import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.api.chat.hover.content.Text
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.boss.BarColor
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BookMeta

class PsychicConcept internal constructor() {
    companion object {
        internal const val DISPLAY_NAME = "display-name"
        internal const val HEALTH_BONUS = "health-bonus"
        internal const val HEALTH_REGEN = "health-regen"
        internal const val MANA = "mana"
        internal const val MANA_REGEN = "mana-regen"
        internal const val DESCRIPTION = "description"
    }

    lateinit var manager: PsychicManager
        private set

    lateinit var name: String
        private set

    /**
     * 표시 이름 (I18N)
     */
    @Config
    lateinit var displayName: String
        private set

    /**
     * 추가 체력
     */
    @Config
    @RangeDouble(min = 0.0, max = 32767.0 - 20.0)
    var healthBonus = 0.0
        private set

    /**
     * 추가 체력 재생
     */
    @Config
    @RangeDouble(min = 0.0, max = 32767.0)
    var healthRegenPerTick = 0.0
        private set

    /**
     * 최대 마나
     */
    @Config
    @RangeDouble(min = 0.0, max = 32767.0)
    var mana = 0.0
        private set

    /**
     * 마나 재생
     */
    @Config
    @RangeDouble(min = 0.0, max = 32767.0)
    var manaRegenPerTick = 0.0
        private set

    /**
     * 마나 색상
     */
    @Config
    var manaColor = BarColor.BLUE

    /**
     * 설명
     */
    @Config
    var description: List<String> = ImmutableList.of()
        private set

    /**
     * 능력 목록
     */
    lateinit var abilityConcepts: List<AbilityConcept>
        private set

    internal fun initialize(name: String, config: ConfigurationSection): Boolean {
        this.name = name
        this.displayName = name

        val ret = computeConfig(config)

        val maxHealthBonus = Bukkit.spigot().spigotConfig.getDouble("attribute.maxHealth", 2048.0 - 20.0)

        if (healthBonus > maxHealthBonus) {
            healthBonus = maxHealthBonus
        }

        return ret
    }

    internal fun initializeModules(manager: PsychicManager, abilityConcepts: List<AbilityConcept>) {
        this.manager = manager
        this.abilityConcepts = ImmutableList.copyOf(abilityConcepts)

        for (abilityConcept in abilityConcepts) {
            abilityConcept.runCatching { onInitialize() }
        }
    }

    internal fun renderTooltip(): TooltipBuilder {
        return TooltipBuilder().apply {
            title = "${ChatColor.GOLD}${ChatColor.BOLD}$displayName${ChatColor.RESET}"
            addStats(ChatColor.RED, "추가 체력", healthBonus)
            addStats(ChatColor.DARK_RED, "체력 재생", healthRegenPerTick * 20.0)
            addStats(ChatColor.AQUA, "마나", mana)
            addStats(ChatColor.DARK_AQUA, "마나 재생", manaRegenPerTick * 20.0)
            addDescription(description)

            addTemplates(
                DISPLAY_NAME to displayName,
                HEALTH_BONUS to healthBonus,
                HEALTH_REGEN to healthRegenPerTick * 20.0,
                MANA to mana,
                MANA_REGEN to manaRegenPerTick * 20.0
            )
        }
    }

    internal fun createInstance(): Psychic {
        return Psychic(this).apply {
            this.initialize(manager.plugin, manager)
        }
    }
}

fun PsychicConcept.createTooltipBook(stats: (EsperStatistic) -> Double): ItemStack {
    val components = ArrayList<BaseComponent>()
    val psychicDisplayName = displayName

    components += TextComponent().apply {
        isBold = true
        isUnderlined = true
        text = displayName
        hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, Text(renderTooltip().toString()))
    }

    for (ability in abilityConcepts) {
        components += TextComponent().apply {
            color = ChatColor.RESET
            isBold = true
            text = "\n\n  - "
        }
        components += TextComponent().apply {
            text = ability.displayName
            isBold = true
            isUnderlined = true
            hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, Text(ability.renderTooltip(stats).toString()))
            clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, "psychics supply ${ability.name}")
        }
    }

    val book = ItemStack(Material.WRITTEN_BOOK)
    book.itemMeta = (book.itemMeta as BookMeta).apply {
        title = psychicDisplayName
        author = "Noonmaru"
        generation = BookMeta.Generation.ORIGINAL
        spigot().addPage(components.toTypedArray())
    }

    return book
}