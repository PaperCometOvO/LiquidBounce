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

import net.ccbluex.fastutil.filterIsInstance
import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.event.events.BlinkPacketEvent
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.TickPacketProcessEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.sequenceHandler
import net.ccbluex.liquidbounce.features.blink.BlinkManager
import net.ccbluex.liquidbounce.features.blink.TrackedEntityPosition
import net.ccbluex.liquidbounce.features.blink.esp.BlinkEspBox
import net.ccbluex.liquidbounce.features.blink.esp.BlinkEspData
import net.ccbluex.liquidbounce.features.blink.esp.BlinkEspModel
import net.ccbluex.liquidbounce.features.blink.esp.BlinkEspNone
import net.ccbluex.liquidbounce.features.blink.esp.BlinkEspWireframe
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura
import net.ccbluex.liquidbounce.features.module.modules.combat.velocity.ModuleVelocity
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.RotationTarget
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.features.MovementCorrection
import net.ccbluex.liquidbounce.utils.aiming.features.processors.anglesmooth.impl.AccelerationAngleSmooth
import net.ccbluex.liquidbounce.utils.aiming.features.processors.anglesmooth.impl.InterpolationAngleSmooth
import net.ccbluex.liquidbounce.utils.aiming.features.processors.anglesmooth.impl.LinearAngleSmooth
import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.client.notification
import net.ccbluex.liquidbounce.utils.combat.attackEntity
import net.ccbluex.liquidbounce.utils.combat.shouldBeAttacked
import net.ccbluex.liquidbounce.utils.entity.rotation
import net.ccbluex.liquidbounce.utils.entity.squaredBoxedDistanceTo
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.ccbluex.liquidbounce.utils.math.multiply
import net.ccbluex.liquidbounce.utils.math.sq
import net.ccbluex.liquidbounce.utils.movement.DirectionalInput
import net.ccbluex.liquidbounce.utils.network.isLocalPlayerDamage
import net.ccbluex.liquidbounce.utils.network.isLocalPlayerVelocity
import net.ccbluex.liquidbounce.utils.raytracing.findEntityInCrosshair
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import kotlin.math.hypot

/**
 * Attack Reduce
 */
object VelocityNewReduce : VelocityMode("NewReduce") {
    private object AttackValueGroup : ValueGroup("Attack") {
        object ReduceTicksForHVelocity : ValueGroup("ReduceTicksForHVelocity") {
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

        init {
            tree(ReduceTicksForHVelocity)
        }

        val attackMode by enumChoice("AttackMode", AttackMode.PER_TICK)
        val attackTargetRange by floatRange("AttackTargetRange", 2f..3f, 0f..6f)
        val keepSprintAttack by boolean("KeepSprintAttack", false)
        val forceSetDirection by boolean("ForceSetDirection", true)
        val horizontal by float("Horizontal", 0.6f, 0f..1f)
        val vertical by float("Vertical", 0.6f, 0f..1f)
    }

    private object LagValueGroup : ValueGroup("Lag") {
        val lagInAir by boolean("LagInAir", true)
        val lagTargetRange by float("LagTargetRange", 6f, 0f..20f)
        val lagMaxDelay by int("LagMaxDelay", 30, 0..200, "ticks")
        val requireKillAura by boolean("RequireKillAura", true)
    }

    private object AutoRotate : ToggleableValueGroup(this, "AutoRotate", true) {
        val rotationTime by int("RotationTime", 2, 0..20, "ticks")
        val angleSmooth = choices("AngleSmooth", 0) {
            val linear = LinearAngleSmooth(it)
            val interpolation = InterpolationAngleSmooth(it)
            arrayOf(
                linear,
                interpolation,
                AccelerationAngleSmooth(it)
            )
        }
        val notDuringKillAura by boolean("NotDuringKillAura", false)

        val canRotate: Boolean
            get() = enabled
                && (!notDuringKillAura
                || !ModuleKillAura.running
                || ModuleKillAura.targetTracker.target == null)
    }

    private object Debug : ToggleableValueGroup(this, "Debug", false) {
        val chatMessage by boolean("ChatMessage", false)
        val notification by boolean("Notification", false)

        fun notify(message: String) {
            if (!this.enabled) {
                return
            }
            if (notification) {
                notification(ModuleVelocity.name, message, NotificationEvent.Severity.INFO)
            }
            if (chatMessage) {
                chat(message)
            }
        }
    }

    init {
        tree(AttackValueGroup)
        tree(LagValueGroup)
        tree(AutoRotate)
        tree(Debug)
    }

    @Suppress("unused")
    private val espMode = modes("BlinkEsp", 2) {
        arrayOf(
            BlinkEspBox(it, ::getEspData),
            BlinkEspModel(it, getEspData = ::getEspData),
            BlinkEspWireframe(it, ::getEspData),
            BlinkEspNone(it),
        )
    }.apply {
        doNotIncludeAlways()
    }

    private var target: Entity? = null
    private var renderTarget: Entity? = null
    private var renderTargetPos: TrackedEntityPosition? = null
    var reduceTicks = 0
        private set
    private var receiveDamage = false
    private var lagTicks = -1
    private var releaseReason: ReleaseReason? = null

    private val canLag: Boolean
        get() = !LagValueGroup.requireKillAura || ModuleKillAura.running

    val backtrackBlocked: Boolean
        get() = lagTicks >= 0 || reduceTicks > 0

    val ownsIncomingBlinkQueue: Boolean
        get() = lagTicks >= 0

    private val isInAir: Boolean
        get() = !player.onGround() && !player.isInLiquid

    @Suppress("unused")
    private val packetEventHandler = handler<PacketEvent> { event ->
        if (event.origin != TransferOrigin.INCOMING) return@handler

        val packet = event.packet

        if (lagTicks >= 0) {
            if (packet is ClientboundPlayerPositionPacket) {
                releaseReason = ReleaseReason.FLAG
            }
            val trackedTargetPosition = renderTargetPos
            val trackedTarget = renderTarget
            if (trackedTargetPosition != null && trackedTarget != null) {
                trackedTargetPosition.handlePacket(packet, world, trackedTarget)
            }
            return@handler
        }

        if (ModuleVelocity.pause > 0) return@handler

        if (packet.isLocalPlayerDamage()) {
            receiveDamage = true
        }

        if (packet.isLocalPlayerVelocity() && receiveDamage) {
            receiveDamage = false
            findTarget()

            if (renderTarget == null && !(LagValueGroup.lagInAir && isInAir)) return@handler

            if (target == null || !player.isSprinting || (LagValueGroup.lagInAir && isInAir)) {
                Debug.notify(when {
                    !player.isSprinting -> "Lag... (not sprinting)"
                    else -> "Lag..."
                })
                val rt = renderTarget
                if (rt != null) {
                    renderTargetPos = TrackedEntityPosition(rt)
                }
                lagTicks = LagValueGroup.lagMaxDelay
            } else if (target != null) {
                setReduceTicks()
                Debug.notify("Attack count: $reduceTicks")
            }
        }
    }

    @Suppress("unused")
    private val queuePacketHandler = handler<BlinkPacketEvent> { event ->
        if (lagTicks >= 0 && event.origin == TransferOrigin.INCOMING && event.packet !is ClientboundKeepAlivePacket) {
            event.action = BlinkManager.Action.QUEUE
        }
    }

    @Suppress("unused")
    private val tickHandler = handler<GameTickEvent> {
        if (reduceTicks > 0) {
            if (target == null || target !in world.entitiesForRendering()) {
                reduceTicks = 0
                target = null
                return@handler
            }

            when (AttackValueGroup.attackMode) {
                AttackMode.ONE_TIME -> {
                    for (i in 1..reduceTicks) {
                        if (target !in world.entitiesForRendering()) break
                        attackCurrentTarget(target!!)
                    }
                    reduceTicks = 0
                    target = null
                }

                AttackMode.PER_TICK -> {
                    reduceTicks--

                    if (target!!.squaredBoxedDistanceTo(player)
                        <= AttackValueGroup.attackTargetRange.endInclusive.sq()) {
                        if (AutoRotate.canRotate) {
                            rotate(target!!)
                        }
                        attackCurrentTarget(target!!)
                        if (reduceTicks == 0) {
                            target = null
                        }
                    }
                    else {
                        Debug.notify("Unable to attack")
                        if (reduceTicks == 0) {
                            target = null
                        }
                    }
                }
            }
        }
        if (lagTicks > 0) lagTicks--
    }

    @Suppress("unused")
    private val tickPacketProcessHandler = sequenceHandler<TickPacketProcessEvent> {
        val reason = releaseReason ?: return@sequenceHandler
        BlinkManager.flush(TransferOrigin.INCOMING)
        lagTicks = -1
        resetRenderState()
        if (reason == ReleaseReason.TARGET_REACHED) {
            Debug.notify("Finish lag")
            setReduceTicks()
        } else {
            Debug.notify("Finish lag (${reason.debugSuffix})")
        }
        releaseReason = null
    }

    @Suppress("unused")
    private val movementInputEventHandler = handler<MovementInputEvent> { event ->
        if (reduceTicks > 0 && AttackValueGroup.forceSetDirection) {
            event.directionalInput = DirectionalInput.FORWARDS
        }

        if (lagTicks >= 0 && releaseReason == null) {
            findTarget()

            when {
                lagTicks == 0 -> releaseReason = ReleaseReason.MAX_DELAY
                player.abilities.flying -> releaseReason = ReleaseReason.SPECTATOR
                renderTarget !in world.entitiesForRendering() -> releaseReason = ReleaseReason.OUT_OF_RANGE
                renderTargetPos?.let { pos ->
                    player.distanceToSqr(pos.base) > LagValueGroup.lagTargetRange.sq()
                } == true -> releaseReason = ReleaseReason.OUT_OF_RANGE
                target != null && (!LagValueGroup.lagInAir || !isInAir) -> {
                    setReduceTicks()
                    releaseReason = ReleaseReason.TARGET_REACHED
                    if (AttackValueGroup.forceSetDirection) {
                        event.directionalInput = DirectionalInput.FORWARDS
                    }
                }
            }

            if (releaseReason != null && releaseReason != ReleaseReason.TARGET_REACHED) {
                if (player.onGround() && player.isSprinting) {
                    player.isSprinting = false
                }
            }
        }
    }

    override fun enable() {
        target = null
        resetRenderState()
        reduceTicks = 0
        receiveDamage = false
        lagTicks = -1
        releaseReason = null
    }

    override fun disable() {
        if (lagTicks >= 0) {
            BlinkManager.flush(TransferOrigin.INCOMING)
        }
        target = null
        resetRenderState()
        reduceTicks = 0
        receiveDamage = false
        lagTicks = -1
        releaseReason = null
    }

    private fun attackCurrentTarget(targetEntity: Entity) {
        if (player.isSprinting) {
            attackEntity(targetEntity, SwingMode.DO_NOT_HIDE, AttackValueGroup.keepSprintAttack)
            player.deltaMovement = player.deltaMovement.multiply(
                AttackValueGroup.horizontal, AttackValueGroup.vertical, AttackValueGroup.horizontal
            )
            player.isSprinting = false
        }
    }

    private fun findTarget() {
        if (trySetKillAuraTargetAsTarget()) return

        target = findEntityInCrosshair(
            AttackValueGroup.attackTargetRange.endInclusive.toDouble(),
            RotationManager.currentRotation ?: player.rotation
        ) { !it.isRemoved && it.shouldBeAttacked() }?.entity

        if (lagTicks == -1) {
            renderTarget = target
        }
        if (target != null) return

        val nearestEntity = world.entitiesForRendering()
            .filterIsInstance<LivingEntity> { !it.isRemoved && it.shouldBeAttacked() }
            .minByOrNull { it.distanceTo(player) }

        renderTarget = nearestEntity
        if (nearestEntity == null) return

        if (lagTicks >= 0 && AutoRotate.canRotate
            && nearestEntity.squaredBoxedDistanceTo(player) <= AttackValueGroup.attackTargetRange.start.sq()) {
            rotate(nearestEntity)
            target = nearestEntity
        }
    }

    private fun getEspData(): BlinkEspData? {
        if (lagTicks == -1) {
            return null
        }
        val rt = renderTarget ?: return null
        val rtp = renderTargetPos ?: return null
        return BlinkEspData(rt, rtp.base, rt.rotation)
    }

    private fun resetRenderState() {
        renderTarget = null
        renderTargetPos = null
    }

    private fun rotate(targetEntity: Entity) {
        val rotation = Rotation.lookingAt(point = targetEntity.boundingBox.center, from = player.eyePosition)
        RotationManager.setRotationTarget(
            RotationTarget(
                rotation = rotation,
                entity = targetEntity,
                processors = listOf(AutoRotate.angleSmooth.activeMode),
                ticksUntilReset = AutoRotate.rotationTime,
                resetThreshold = 2f,
                considerInventory = false,
                movementCorrection = MovementCorrection.STRICT
            ),
            priority = Priority.IMPORTANT_FOR_USAGE_2,
            provider = ModuleVelocity
        )
    }

    private fun setReduceTicks() {
        val hVelocity = hypot(player.deltaMovement.x, player.deltaMovement.z)
        reduceTicks = when {
            hVelocity <= 0.0625 -> AttackValueGroup.ReduceTicksForHVelocity.hVelocity0To0dot0625
            hVelocity <= 0.125 -> AttackValueGroup.ReduceTicksForHVelocity.hVelocity0dot0625To0dot125
            hVelocity <= 0.25 -> AttackValueGroup.ReduceTicksForHVelocity.hVelocity0dot125To0dot25
            hVelocity <= 0.375 -> AttackValueGroup.ReduceTicksForHVelocity.hVelocity0dot25To0dot375
            hVelocity <= 0.5 -> AttackValueGroup.ReduceTicksForHVelocity.hVelocity0dot375To0dot5
            hVelocity <= 0.625 -> AttackValueGroup.ReduceTicksForHVelocity.hVelocity0dot5To0dot625
            hVelocity <= 0.75 -> AttackValueGroup.ReduceTicksForHVelocity.hVelocity0dot625To0dot75
            hVelocity <= 0.875 -> AttackValueGroup.ReduceTicksForHVelocity.hVelocity0dot75To0dot875
            hVelocity <= 1.0 -> AttackValueGroup.ReduceTicksForHVelocity.hVelocity0dot875To1dot0
            hVelocity <= 1.125 -> AttackValueGroup.ReduceTicksForHVelocity.hVelocity1dot0To1dot125
            else -> AttackValueGroup.ReduceTicksForHVelocity.hVelocity1dot125Plus
        }
    }

    private fun trySetKillAuraTargetAsTarget(): Boolean {
        if (ModuleKillAura.running && ModuleKillAura.targetTracker.target != null) {
            val killAuraTarget = ModuleKillAura.targetTracker.target
            if (lagTicks == -1) {
                renderTarget = killAuraTarget
            }
            if (!canLag
                || killAuraTarget!!.squaredBoxedDistanceTo(player)
                <= AttackValueGroup.attackTargetRange.endInclusive.sq()) {
                target = killAuraTarget
            }
            return true
        } else {
            return false
        }
    }

    private enum class AttackMode(override val tag: String) : Tagged {
        ONE_TIME("OneTime"),
        PER_TICK("PerTick")
    }

    private enum class ReleaseReason(val debugSuffix: String?) {
        TARGET_REACHED(null),
        FLAG("flag"),
        SPECTATOR("spectator"),
        OUT_OF_RANGE("out of range"),
        MAX_DELAY("max delay"),
    }
}
