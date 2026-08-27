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

package net.ccbluex.liquidbounce.features.module.modules.combat.velocity.mode

import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.utils.entity.withStrafe
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket

object VelocityZeqa : VelocityMode("Zeqa") {
    private val strafeSpeed by float("StrafeSpeed", 0.1f, 0.0f..1.0f)
    private val strafeStrength by float("StrafeStrength", 0.1f, 0.0f..1.0f)

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent> { event ->
        val packet = event.packet

        if (packet is ServerboundMovePlayerPacket) {
            packet.onGround = true
        }

        if (packet is ClientboundSetEntityMotionPacket && packet.id == player.id) {
            player.deltaMovement = player.deltaMovement.withStrafe(strafeSpeed.toDouble(), strafeStrength.toDouble())

            packet.movement.x = packet.movement.x * 0.5
            packet.movement.y = packet.movement.y * 0.3
            packet.movement.z = packet.movement.z * 0.5
        }
    }
}
