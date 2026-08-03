/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */

package net.ccbluex.liquidbounce.features.module.modules.misc

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.delay
import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.suspendHandler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories

import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket
import kotlin.time.Duration.Companion.milliseconds

object ModuleAutoGG : ClientModule("AutoGG", ModuleCategories.MISC) {
    private val messages by textList("Messages", mutableListOf("GG"))
    private val titleTextRecognization by textList("Title Text Recognization", mutableListOf(
        "victory",
        "defeat"
    ))
    private val pattern by enumChoice("Pattern", AutoGGPattern.LINEAR)
    private val triggerDelay by intRange("TriggerDelay", 100..100, 0..5000)

    private val index = atomic(0)

    @Suppress("unused")
    private val packetHandler = suspendHandler<PacketEvent> { event ->
        val packet = event.packet
        if (packet is ClientboundSetTitleTextPacket){
            if (titleTextRecognization.any { packet.text.string.contains(it, true) }) {
                delay(triggerDelay.random().milliseconds)
                when (pattern) {
                    AutoGGPattern.RANDOM ->
                        network.sendChat(messages.random())
                    AutoGGPattern.LINEAR ->
                        network.sendChat(messages[index.getAndIncrement() % messages.size])
                }
            }
        }
    }

    enum class AutoGGPattern(override val tag: String) : Tagged {
        RANDOM("Random"),
        LINEAR("Linear"),
    }
}
