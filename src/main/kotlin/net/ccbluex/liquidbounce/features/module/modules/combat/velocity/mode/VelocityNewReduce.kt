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
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.TickPacketProcessEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.sequenceHandler
import net.ccbluex.liquidbounce.features.blink.BlinkManager
import net.ccbluex.liquidbounce.features.blink.BlinkManager.Action
import net.ccbluex.liquidbounce.features.blink.TrackedEntityPosition
import net.ccbluex.liquidbounce.features.blink.esp.BlinkEspBox
import net.ccbluex.liquidbounce.features.blink.esp.BlinkEspData
import net.ccbluex.liquidbounce.features.blink.esp.BlinkEspModel
import net.ccbluex.liquidbounce.features.blink.esp.BlinkEspNone
import net.ccbluex.liquidbounce.features.blink.esp.BlinkEspWireframe
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura
import net.ccbluex.liquidbounce.features.module.modules.combat.velocity.ModuleVelocity
import net.ccbluex.liquidbounce.features.module.modules.combat.velocity.mode.VelocityNewReduce.LagValueGroup.unchangedPing
import net.ccbluex.liquidbounce.lang.translation
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.RotationTarget
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.aiming.features.MovementCorrection
import net.ccbluex.liquidbounce.utils.aiming.features.processors.anglesmooth.impl.AccelerationAngleSmooth
import net.ccbluex.liquidbounce.utils.aiming.features.processors.anglesmooth.impl.InterpolationAngleSmooth
import net.ccbluex.liquidbounce.utils.aiming.features.processors.anglesmooth.impl.LinearAngleSmooth
import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.ccbluex.liquidbounce.utils.client.chat
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
import net.minecraft.network.chat.MutableComponent
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
        object AttackCountForHVelocity : ValueGroup("AttackCountForHVelocity") {
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
            tree(AttackCountForHVelocity)
        }

        val mode by enumChoice("Mode", AttackMode.PER_TICK)
        val forceSetDirection by boolean("ForceSetDirection", true)
        val attackWhen by enumChoice("AttackWhen", AttackWhen.NOT_SPRINTING)
        val forceAdapt by boolean("ForceAdapt", true)
        val noVanillaAttack by boolean("NoVanillaAttack", false)
        val horizontal by float("Horizontal", 0.6f, 0f..1f)
        val vertical by float("Vertical", 0.6f, 0f..1f)
    }

    private object LagValueGroup : ToggleableValueGroup(this, "Lag", true) {
        val mode by enumChoice("Mode", LagMode.IN_AIR)
        val flushOnLandingWithTarget by boolean("FlushOnLandingWithTarget", true)
        val scanTargetRange by float("ScanTargetRange", 6f, 0f..20f)
        val maxDelay by int("MaxDelay", 30, 0..200, "ticks")
        val unchangedPing by boolean("UnchangedPing", true)
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
        fun notify(message: MutableComponent) {
            if (this.enabled) {
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
    private var attackCount = 0
    private var receiveDamage = false
    private var shouldLag = false
    private var lagTicks = 0
    private var releaseReason: ReleaseReason? = null
    private var wasInAir = false

    val backtrackBlocked: Boolean
        get() = shouldLag || attackCount > 0

    val ownsIncomingBlinkQueue: Boolean
        get() = shouldLag

    private val isInAir: Boolean
        get() = !player.onGround() && !player.isInLiquid

    @Suppress("unused")
    private val packetEventHandler = handler<PacketEvent> { event ->
        val packet = event.packet
        if (packet.isLocalPlayerDamage()) {
            receiveDamage = true
        }

        if (packet.isLocalPlayerVelocity() && receiveDamage) {
            receiveDamage = false
            val hVelocity = hypot(player.deltaMovement.x, player.deltaMovement.z)
            findTarget()

            if (LagValueGroup.enabled && (LagValueGroup.mode == LagMode.ALWAYS
                    || (LagValueGroup.mode == LagMode.IN_AIR && isInAir))) {
                if (renderTarget != null) {
                    renderTargetPos = TrackedEntityPosition(renderTarget!!)
                }
                if (!LagValueGroup.requireKillAura) {
                    shouldLag = true
                    lagTicks = LagValueGroup.maxDelay
                    if (isInAir) {
                        wasInAir = true
                    }
                    Debug.notify(translation(
                        "liquidbounce.module.velocity.messages.newReduceLagging")
                    )
                } else if (ModuleKillAura.running) {
                    shouldLag = true
                    lagTicks = LagValueGroup.maxDelay
                    if (isInAir) {
                        wasInAir = true
                    }
                    Debug.notify(translation(
                        "liquidbounce.module.velocity.messages.newReduceLagging")
                    )
                }
            }

            if (target != null && !shouldLag) {
                setAttackCount()
                Debug.notify(
                    translation(
                        "liquidbounce.module.velocity.messages.newReduceHorizontalVelocity",
                        hVelocity
                    )
                )
                Debug.notify(
                    translation(
                        "liquidbounce.module.velocity.messages.newReduceSetAttackCount",
                        attackCount
                    )
                )
            }
        }

        if (shouldLag) {
            if (packet is ClientboundPlayerPositionPacket) {
                releaseReason = ReleaseReason.FLAGGED
                return@handler
            }
            if (renderTargetPos != null && renderTarget != null) {
                renderTargetPos!!.handlePacket(packet, world, renderTarget!!)
            }
        }
    }

    @Suppress("unused")
    private val queuePacketHandler = handler<BlinkPacketEvent> { event ->
        val packet = event.packet
        if (!shouldLag || event.origin != TransferOrigin.INCOMING) {
            return@handler
        } else if (unchangedPing && packet is ClientboundKeepAlivePacket) {
            return@handler
        }

        event.action = Action.QUEUE
    }

    @Suppress("unused")
    private val tickHandler = handler<GameTickEvent> {
        if (attackCount > 0) {
            findTarget()

            when (AttackValueGroup.mode) {
                AttackMode.ONE_TIME -> {
                    if (target != null && target in world.entitiesForRendering()) {
                        val attackResults = ArrayList<Boolean>()
                        for (i in 1..attackCount) {
                            attackResults.add(attackCurrentTarget(target!!))
                        }

                        if (attackResults.size == attackCount) {
                            Debug.notify(translation(
                                "liquidbounce.module.velocity.messages.newReduceOneTimeAttack"
                                ,attackCount)
                            )
                        } else {
                            Debug.notify(translation(
                                "liquidbounce.module.velocity.messages.newReduceOneTimeCorruptAttack"
                                ,attackResults.size
                                ,attackResults - attackResults.size)
                            )
                        }

                        attackCount = 0
                        target = null
                    } else {
                        attackCount = 0
                        target = null
                        Debug.notify(translation(
                            "liquidbounce.module.velocity.messages.newReduceOneTimeDidNotAttack"
                            ,attackCount))
                    }
                }
                AttackMode.PER_TICK -> {
                    attackCount--

                    if (target != null && target in world.entitiesForRendering()) {
                        if (AutoRotate.canRotate) {
                            rotate(target!!)
                        }
                        val attackResult = attackCurrentTarget(target!!)
                        if (attackCount == 0) {
                            target = null
                        }

                        if (attackResult) {
                            Debug.notify(translation(
                                "liquidbounce.module.velocity.messages.newReducePerTickAttack"
                                ,attackCount)
                            )
                        } else {
                            Debug.notify(translation(
                                "liquidbounce.module.velocity.messages.newReducePerTickDidNotAttack"
                                ,attackCount)
                            )
                        }
                    }
                    else {
                        if (attackCount == 0) {
                            target = null
                        }
                        Debug.notify(translation(
                            "liquidbounce.module.velocity.messages.newReducePerTickDidNotAttack"
                            ,attackCount)
                        )
                    }
                }
            }
        }

        if (shouldLag && releaseReason == null) {
            lagTicks--
            findTarget()

            when {
                lagTicks == 0 -> releaseReason = ReleaseReason.DELAY_END
                player.abilities.flying -> releaseReason = ReleaseReason.SPECTATOR
                renderTarget != null
                    && renderTarget !in world.entitiesForRendering() -> releaseReason = ReleaseReason.OUT_OF_RANGE
                renderTargetPos?.let { pos ->
                    player.distanceToSqr(pos.base) > LagValueGroup.scanTargetRange.sq()
                } == true -> releaseReason = ReleaseReason.OUT_OF_RANGE
                target != null && (LagValueGroup.flushOnLandingWithTarget && wasInAir && !isInAir) ->
                    releaseReason = ReleaseReason.LANDING_WITH_TARGET
            }
        }
    }

    @Suppress("unused")
    private val tickPacketProcessHandler = sequenceHandler<TickPacketProcessEvent> {
        if (releaseReason != null) {
            shouldLag = false
            lagTicks = 0
            BlinkManager.flush(TransferOrigin.INCOMING)
            resetRenderState()
            when (releaseReason) {
                ReleaseReason.LANDING_WITH_TARGET -> {
                    setAttackCount()
                    Debug.notify(
                        translation(
                            "liquidbounce.module.velocity.messages.newReduceLandingWithTargetStopLagging"
                        )
                    )

                    val hVelocity = hypot(player.deltaMovement.x, player.deltaMovement.z)
                    Debug.notify(
                        translation(
                            "liquidbounce.module.velocity.messages.newReduceHorizontalVelocity",
                            hVelocity
                        )
                    )
                    Debug.notify(
                        translation(
                            "liquidbounce.module.velocity.messages.newReduceSetAttackCount",
                            attackCount
                        )
                    )
                }
                ReleaseReason.DELAY_END -> {
                    setAttackCount()
                    Debug.notify(
                        translation(
                            "liquidbounce.module.velocity.messages.newReduceDelayEndStopLagging"
                        )
                    )

                    val hVelocity = hypot(player.deltaMovement.x, player.deltaMovement.z)
                    Debug.notify(
                        translation(
                            "liquidbounce.module.velocity.messages.newReduceHorizontalVelocity",
                            hVelocity
                        )
                    )
                    Debug.notify(
                        translation(
                            "liquidbounce.module.velocity.messages.newReduceSetAttackCount",
                            attackCount
                        )
                    )
                }
                else -> {
                    Debug.notify(
                        translation(
                            "liquidbounce.module.velocity.messages.newReduce"
                                + releaseReason!!.tag + "StopLagging"
                        )
                    )
                }
            }
            releaseReason = null
        }
    }

    @Suppress("unused")
    private val movementInputEventHandler = handler<MovementInputEvent> { event ->
        if (attackCount > 0 && AttackValueGroup.forceSetDirection) {
            event.directionalInput = DirectionalInput.FORWARDS
        }
    }

    override fun enable() {
        shouldLag = false
        lagTicks = 0
        target = null
        resetRenderState()
        attackCount = 0
        receiveDamage = false
        releaseReason = null
    }

    override fun disable() {
        shouldLag = false
        lagTicks = 0
        BlinkManager.flush(TransferOrigin.INCOMING)
        target = null
        resetRenderState()
        attackCount = 0
        receiveDamage = false
        releaseReason = null
    }

    private fun attackCurrentTarget(targetEntity: Entity) : Boolean {
        if (AttackValueGroup.attackWhen == AttackWhen.NOT_SPRINTING) {
            if (AttackValueGroup.forceAdapt) {
                player.isSprinting = false
            }

            if (!player.isSprinting) {
                attackEntity(targetEntity, SwingMode.DO_NOT_HIDE, AttackValueGroup.noVanillaAttack)
                player.deltaMovement = player.deltaMovement.multiply(
                    AttackValueGroup.horizontal, AttackValueGroup.vertical, AttackValueGroup.horizontal
                )
                return true
            }
        } else if (AttackValueGroup.attackWhen == AttackWhen.SPRINTING) {
            if (AttackValueGroup.forceAdapt) {
                player.isSprinting = true
            }

            if (player.isSprinting) {
                attackEntity(targetEntity, SwingMode.DO_NOT_HIDE, AttackValueGroup.noVanillaAttack)
                player.deltaMovement = player.deltaMovement.multiply(
                    AttackValueGroup.horizontal, AttackValueGroup.vertical, AttackValueGroup.horizontal
                )
                player.isSprinting = false
                return true
            }
        } else if (AttackValueGroup.attackWhen == AttackWhen.NO_REQUIREMENT){
            attackEntity(targetEntity, SwingMode.DO_NOT_HIDE, AttackValueGroup.noVanillaAttack)
            player.deltaMovement = player.deltaMovement.multiply(
                AttackValueGroup.horizontal, AttackValueGroup.vertical, AttackValueGroup.horizontal
            )

            if (player.isSprinting) {
                player.isSprinting = false
            }
            return true
        }
        return false
    }

    private fun findTarget() {
        if (trySetKillAuraTargetAsTarget()) return

        target = findEntityInCrosshair(
            ModuleKillAura.range.interactionRange.toDouble(),
            RotationManager.currentRotation ?: player.rotation
        ) { !it.isRemoved && it.shouldBeAttacked() }?.entity

        if (!shouldLag) {
            renderTarget = target
        }

        if (target == null) {
            val nearestEntity = world.entitiesForRendering()
                .filterIsInstance<LivingEntity> { !it.isRemoved && it.shouldBeAttacked() }
                .minByOrNull { it.distanceTo(player) }

            renderTarget = nearestEntity
            if (nearestEntity != null && shouldLag && AutoRotate.canRotate
                && nearestEntity.squaredBoxedDistanceTo(player)
                <= ModuleKillAura.range.interactionRange.toDouble().sq()) {
                rotate(nearestEntity)
                target = nearestEntity
            }
        }
    }

    private fun getEspData(): BlinkEspData? {
        if (!shouldLag) {
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

    private fun setAttackCount() {
        val hVelocity = hypot(player.deltaMovement.x, player.deltaMovement.z)
        attackCount = when {
            hVelocity <= 0.0625 -> AttackValueGroup.AttackCountForHVelocity.hVelocity0To0dot0625
            hVelocity <= 0.125 -> AttackValueGroup.AttackCountForHVelocity.hVelocity0dot0625To0dot125
            hVelocity <= 0.25 -> AttackValueGroup.AttackCountForHVelocity.hVelocity0dot125To0dot25
            hVelocity <= 0.375 -> AttackValueGroup.AttackCountForHVelocity.hVelocity0dot25To0dot375
            hVelocity <= 0.5 -> AttackValueGroup.AttackCountForHVelocity.hVelocity0dot375To0dot5
            hVelocity <= 0.625 -> AttackValueGroup.AttackCountForHVelocity.hVelocity0dot5To0dot625
            hVelocity <= 0.75 -> AttackValueGroup.AttackCountForHVelocity.hVelocity0dot625To0dot75
            hVelocity <= 0.875 -> AttackValueGroup.AttackCountForHVelocity.hVelocity0dot75To0dot875
            hVelocity <= 1.0 -> AttackValueGroup.AttackCountForHVelocity.hVelocity0dot875To1dot0
            hVelocity <= 1.125 -> AttackValueGroup.AttackCountForHVelocity.hVelocity1dot0To1dot125
            else -> AttackValueGroup.AttackCountForHVelocity.hVelocity1dot125Plus
        }
    }

    private fun trySetKillAuraTargetAsTarget(): Boolean {
        if (ModuleKillAura.running && ModuleKillAura.targetTracker.target != null) {
            if (!shouldLag) {
                renderTarget = ModuleKillAura.targetTracker.target
            }
            if (ModuleKillAura.targetTracker.target!!.squaredBoxedDistanceTo(player)
                <= ModuleKillAura.range.interactionRange.toDouble().sq()) {
                target = ModuleKillAura.targetTracker.target
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

    private enum class AttackWhen(override val tag: String) : Tagged {
        SPRINTING("Sprinting"),
        NOT_SPRINTING("NotSprinting"),
        NO_REQUIREMENT("NoRequirement")
    }

    private enum class LagMode(override val tag: String) : Tagged {
        ALWAYS("Always"),
        IN_AIR("InAir")
    }

    private enum class ReleaseReason(override val tag: String) : Tagged {
        FLAGGED("Flagged"),
        SPECTATOR("Spectator"),
        OUT_OF_RANGE("OutOfRange"),
        DELAY_END("DelayEnd"),
        LANDING_WITH_TARGET("LandingWithTarget")
    }
}
