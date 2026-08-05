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

import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.ccbluex.liquidbounce.utils.combat.attackEntity
import net.ccbluex.liquidbounce.utils.combat.shouldBeAttacked
import net.ccbluex.liquidbounce.utils.entity.rotation
import net.ccbluex.liquidbounce.utils.math.multiply
import net.ccbluex.liquidbounce.utils.network.isLocalPlayerVelocity
import net.ccbluex.liquidbounce.utils.raytracing.findEntityInCrosshair
import net.minecraft.world.entity.Entity
import kotlin.math.hypot

object VelocityHypixelReduce : VelocityMode("Hypixel Reduce") {
    object ReduceTicksForHVelocity : ValueGroup("ReduceTicksForHVelocity") {
        val hVelocity0To0_0625 by int("0To0 0625", 3, 0..10)
        val hVelocity0_0625To0_125 by int("0 0625To0 125", 4, 0..10)
        val hVelocity0_125To0_25 by int("0 125To0 25", 4, 0..10)
        val hVelocity0_25To0_375 by int("0 25To0 375", 5, 0..10)
        val hVelocity0_375To0_5 by int("0 375To0 5", 6, 0..10)
        val hVelocity0_5To0_625 by int("0 5To0 625", 6, 0..10)
        val hVelocity0_625To0_75 by int("0 625To0 75", 7, 0..10)
        val hVelocity0_75To0_875 by int("0 75To0 875", 7, 0..10)
        val hVelocity0_875To1_0 by int("0 875To1 0", 8, 0..10)
        val hVelocity1_0To1_125 by int("1 0To1 125", 8, 0..10)
        val hVelocity1_125Plus by int("1 125Plus", 9, 0..10)
    }

    private var reduceTicks = 0
    private var target: Entity? = null

    init {
        tree(ReduceTicksForHVelocity)
    }

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent> { event ->
        val packet = event.packet

        if (packet.isLocalPlayerVelocity(false)) {
            val hVelocity = hypot(player.deltaMovement.x, player.deltaMovement.z)
            reduceTicks = when {
                hVelocity <= 0.0625 -> ReduceTicksForHVelocity.hVelocity0To0_0625
                hVelocity <= 0.125 -> ReduceTicksForHVelocity.hVelocity0_0625To0_125
                hVelocity <= 0.25 -> ReduceTicksForHVelocity.hVelocity0_125To0_25
                hVelocity <= 0.375 -> ReduceTicksForHVelocity.hVelocity0_25To0_375
                hVelocity <= 0.5 -> ReduceTicksForHVelocity.hVelocity0_375To0_5
                hVelocity <= 0.625 -> ReduceTicksForHVelocity.hVelocity0_5To0_625
                hVelocity <= 0.75 -> ReduceTicksForHVelocity.hVelocity0_625To0_75
                hVelocity <= 0.875 -> ReduceTicksForHVelocity.hVelocity0_75To0_875
                hVelocity <= 1.0 -> ReduceTicksForHVelocity.hVelocity0_875To1_0
                hVelocity <= 1.125 -> ReduceTicksForHVelocity.hVelocity1_0To1_125
                else -> ReduceTicksForHVelocity.hVelocity1_125Plus
            }
        }
    }

    @Suppress("unused")
    private val tickHandler = handler<GameTickEvent> {
        if (reduceTicks > 0) {
            target = if (ModuleKillAura.running && ModuleKillAura.targetTracker.target != null) {
                ModuleKillAura.targetTracker.target
            } else {
                findEntityInCrosshair(
                    ModuleKillAura.range.interactionRange.toDouble()
                    ,RotationManager.currentRotation ?: player.rotation
                ) { !it.isRemoved && it.shouldBeAttacked() }?.entity
            }
            if (player.isSprinting && !player.isInLiquid) {
                attackEntity(target!!, SwingMode.DO_NOT_HIDE, true)
                player.deltaMovement = player.deltaMovement.multiply(
                    factorX = 0.6,
                    factorZ = 0.6
                )
                player.isSprinting = false
            }

            reduceTicks--
            if (reduceTicks == 0) {
                target = null
            }
        }
    }

    override fun enable() {
        reduceTicks = 0
        target = null
    }

    override fun disable() {
        reduceTicks = 0
        target = null
    }
}
