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
import net.ccbluex.liquidbounce.lang.translation
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.combat.attackEntity
import net.ccbluex.liquidbounce.utils.combat.shouldBeAttacked
import net.ccbluex.liquidbounce.utils.entity.rotation
import net.ccbluex.liquidbounce.utils.entity.squaredBoxedDistanceTo
import net.ccbluex.liquidbounce.utils.math.multiply
import net.ccbluex.liquidbounce.utils.math.sq
import net.ccbluex.liquidbounce.utils.network.isLocalPlayerDamage
import net.ccbluex.liquidbounce.utils.network.isLocalPlayerVelocity
import net.ccbluex.liquidbounce.utils.raytracing.findEntityInCrosshair
import net.minecraft.world.entity.Entity
import kotlin.math.hypot

object VelocityHypixelReduce : VelocityMode("Hypixel Reduce") {
    private object ReduceTicksForHVelocity : ValueGroup("ReduceTicksForHVelocity") {
        val hVelocity0To0dot0625 by int("0To0dot0625", 3, 0..10)
        val hVelocity0dot0625To0dot125 by int("0dot0625To0dot125", 4, 0..10)
        val hVelocity0dot125To0dot25 by int("0dot125To0dot25", 4, 0..10)
        val hVelocity0dot25To0dot375 by int("0dot25To0dot375", 5, 0..10)
        val hVelocity0dot375To0dot5 by int("0dot375To0dot5", 6, 0..10)
        val hVelocity0dot5To0dot625 by int("0dot5To0dot625", 6, 0..10)
        val hVelocity0dot625To0dot75 by int("0dot625To0dot75", 7, 0..10)
        val hVelocity0dot75To0dot875 by int("0dot75To0dot875", 7, 0..10)
        val hVelocity0dot875To1dot0 by int("0dot875To1dot0", 8, 0..10)
        val hVelocity1dot0To1dot125 by int("1dot0To1dot125", 8, 0..10)
        val hVelocity1dot125Plus by int("1dot125Plus", 9, 0..10)
    }

    private var reduceTicks = 0
    private var target: Entity? = null
    private var receiveDamage = false

    init {
        tree(ReduceTicksForHVelocity)
    }

    private val debug by boolean("Debug", false)

    private val heypixelMode by boolean("HeypixelMode", false)

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent> { event ->
        val packet = event.packet

        if (packet.isLocalPlayerDamage()) {
            receiveDamage = true
        }
        if (packet.isLocalPlayerVelocity(true) && receiveDamage) {
            receiveDamage = false
            val hVelocity = hypot(player.deltaMovement.x, player.deltaMovement.z)
            reduceTicks = when {
                hVelocity <= 0.0625 -> ReduceTicksForHVelocity.hVelocity0To0dot0625
                hVelocity <= 0.125 -> ReduceTicksForHVelocity.hVelocity0dot0625To0dot125
                hVelocity <= 0.25 -> ReduceTicksForHVelocity.hVelocity0dot125To0dot25
                hVelocity <= 0.375 -> ReduceTicksForHVelocity.hVelocity0dot25To0dot375
                hVelocity <= 0.5 -> ReduceTicksForHVelocity.hVelocity0dot375To0dot5
                hVelocity <= 0.625 -> ReduceTicksForHVelocity.hVelocity0dot5To0dot625
                hVelocity <= 0.75 -> ReduceTicksForHVelocity.hVelocity0dot625To0dot75
                hVelocity <= 0.875 -> ReduceTicksForHVelocity.hVelocity0dot75To0dot875
                hVelocity <= 1.0 -> ReduceTicksForHVelocity.hVelocity0dot875To1dot0
                hVelocity <= 1.125 -> ReduceTicksForHVelocity.hVelocity1dot0To1dot125
                else -> ReduceTicksForHVelocity.hVelocity1dot125Plus
            }
            if (debug) {
                chat(translation("liquidbounce.module.velocity.messages.hypixelReduceHorizontalVelocity", hVelocity))
                chat(translation("liquidbounce.module.velocity.messages.hypixelReduceSetReduceTicks", reduceTicks))
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

            reduceTicks--

            if (target != null
                && target!!.squaredBoxedDistanceTo(player) < ModuleKillAura.range.interactionRange.sq()) {
                if (player.isSprinting && !player.isInLiquid) {
                    attackEntity(target!!, SwingMode.DO_NOT_HIDE, !heypixelMode)
                    player.deltaMovement = player.deltaMovement.multiply(
                        factorX = 0.6,
                        factorZ = 0.6
                    )
                    player.isSprinting = false
                }

                if (reduceTicks == 0) {
                    target = null
                }

                if (debug) {
                    chat(translation("liquidbounce.module.velocity.messages.hypixelReducePerTickAttack"
                        ,reduceTicks))
                }
            } else {
                if (reduceTicks == 0) {
                    target = null
                }

                if (debug) {
                    chat(translation("liquidbounce.module.velocity.messages.hypixelReducePerTickDidNotAttack"
                        ,reduceTicks))
                }
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
