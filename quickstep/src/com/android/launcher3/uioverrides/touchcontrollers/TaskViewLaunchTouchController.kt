/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.uioverrides.touchcontrollers

import android.content.Context
import android.graphics.Rect
import android.view.MotionEvent
import com.android.app.animation.Interpolators.ZOOM_IN
import com.android.launcher3.LauncherAnimUtils
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.Utilities.EDGE_NAV_BAR
import com.android.launcher3.Utilities.boundToRange
import com.android.launcher3.Utilities.debugLog
import com.android.launcher3.Utilities.isRtl
import com.android.launcher3.anim.AnimatorPlaybackController
import com.android.launcher3.display.DisplayController
import com.android.launcher3.touch.BaseSwipeDetector
import com.android.launcher3.touch.SingleAxisSwipeDetector
import com.android.launcher3.util.FlingBlockCheck
import com.android.launcher3.util.TouchController
import com.android.quickstep.LockedTaskManager
import com.android.quickstep.views.RecentsDismissUtils
import com.android.quickstep.views.RecentsView
import com.android.quickstep.views.RecentsViewContainer
import com.android.quickstep.views.TaskView
import java.util.function.Consumer
import kotlin.math.abs
import kotlin.math.roundToInt

/** Touch controller which handles dragging task view cards for launch. */
class TaskViewLaunchTouchController<CONTAINER>
@JvmOverloads
constructor(
    private val container: CONTAINER,
    private val onAnimationCreatedCallback: Consumer<AnimatorPlaybackController>? = null,
) : TouchController, SingleAxisSwipeDetector.Listener
    where CONTAINER : Context, CONTAINER : RecentsViewContainer {
    private val tempRect = Rect()
    private val flingBlockCheck = FlingBlockCheck()
    private val recentsView: RecentsView<*, *> = container.getOverviewPanel()
    private val detector: SingleAxisSwipeDetector =
        SingleAxisSwipeDetector(
            container as Context,
            this,
            recentsView.pagedOrientationHandler.upDownSwipeDirection,
        )
    private val isRtl = isRtl(container.resources)
    private val downDirection = recentsView.pagedOrientationHandler.getDownDirection(isRtl)

    private var taskBeingDragged: TaskView? = null
    private var launchEndDisplacement: Float = 0f
    private var maxLockDisplacement: Float = 0f
    private var playbackController: AnimatorPlaybackController? = null
    private var verticalFactor: Int = 0
    private var canInterceptTouch = false
    private var isLockGestureActive: Boolean = false
    private var wasLockedBeforeDrag = false
    private var hasLockThresholdHapticRun = false
    private var isLockPillShown = false
    private var wasTouchCancelled = false

    private fun isSwipeDownToLockEnabled(): Boolean =
        LauncherPrefs.get(container).get(LauncherPrefs.SWIPE_DOWN_TO_LOCK)

    private fun canInterceptTouch(ev: MotionEvent): Boolean =
        when {
            // Don't intercept swipes on the nav bar, as user might be trying to go home during a
            // task dismiss animation.
            (ev.edgeFlags and EDGE_NAV_BAR) != 0 -> {
                debugLog(TAG, "Not intercepting edge swipe on nav bar.")
                false
            }

            !isSwipeDownToLockEnabled() && !recentsView.shouldSwipeDownLaunchTaskView(taskBeingDragged) -> {
                // Already logged in RecentsViewUtils.
                false
            }

            !DisplayController.getNavigationMode(container).hasGestures -> {
                debugLog(TAG, "Not intercepting touch, not gesture mode.")
                false
            }

            else -> true
        }

    override fun onControllerInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (
            (ev.action == MotionEvent.ACTION_UP || ev.action == MotionEvent.ACTION_CANCEL) &&
                playbackController == null && !isLockGestureActive
        ) {
            clearState()
        }
        if (ev.action == MotionEvent.ACTION_DOWN) {
            canInterceptTouch = onActionDown(ev)
            if (!canInterceptTouch) {
                clearState()
                return false
            }
        }
        // Ignore other actions if touch intercepting has not been enabled in an ACTION_DOWN event.
        if (!canInterceptTouch) {
            return false
        }
        onControllerTouchEvent(ev)
        val downDirectionIsNegative = downDirection == SingleAxisSwipeDetector.DIRECTION_NEGATIVE
        val wasInitialTouchDown =
            (downDirectionIsNegative && !detector.wasInitialTouchPositive()) ||
                (!downDirectionIsNegative && detector.wasInitialTouchPositive())
        return detector.isDraggingState && wasInitialTouchDown
    }

    override fun onControllerTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) wasTouchCancelled = false
        if (ev.actionMasked == MotionEvent.ACTION_CANCEL) wasTouchCancelled = true
        return detector.onTouchEvent(ev)
    }

    private fun onActionDown(ev: MotionEvent): Boolean {
        taskBeingDragged =
            recentsView.taskViews
                .firstOrNull {
                    recentsView.isTaskViewVisible(it) && container.dragLayer.isEventOverView(it, ev)
                }
                ?.also {
                    verticalFactor =
                        recentsView.pagedOrientationHandler.getTaskDragDisplacementFactor(isRtl)
                }
        if (!canInterceptTouch(ev)) {
            return false
        }
        detector.setDetectableScrollConditions(downDirection, /* ignoreSlop= */ false)
        return true
    }

    override fun onDragStart(start: Boolean, startDisplacement: Float) {
        val taskBeingDragged = taskBeingDragged ?: return
        debugLog(TAG, "Handling touch event.")

        isLockGestureActive = isSwipeDownToLockEnabled()
        if (isLockGestureActive) {
            val packageName = taskBeingDragged.firstTask?.key?.packageName
            wasLockedBeforeDrag = packageName != null &&
                LockedTaskManager.getInstance(container).isPackageLocked(packageName)
            showLockPill(wasLockedBeforeDrag)
            isLockPillShown = true
            hasLockThresholdHapticRun = false
            maxLockDisplacement = recentsView.pagedOrientationHandler
                .getSecondaryDimension(taskBeingDragged) * LOCK_DISPLACEMENT_FRACTION * verticalFactor
        } else {
            val secondaryLayerDimension: Int =
                recentsView.pagedOrientationHandler.getSecondaryDimension(container.getDragLayer())
            val maxDuration = 2L * secondaryLayerDimension
            recentsView.clearPendingAnimation()
            val pendingAnimation =
                recentsView.createTaskLaunchAnimation(taskBeingDragged, maxDuration, ZOOM_IN)
            // Since the thumbnail is what is filling the screen, based the end displacement on it.
            taskBeingDragged.getThumbnailBounds(tempRect, /* relativeToDragLayer= */ true)
            launchEndDisplacement =
                recentsView.pagedOrientationHandler
                    .getTaskLaunchLength(secondaryLayerDimension, tempRect)
                    .toFloat() * verticalFactor
            playbackController =
                pendingAnimation.createPlaybackController()?.apply {
                    onAnimationCreatedCallback?.accept(this)
                    dispatchOnStart()
                }
        }
    }

    override fun onDrag(displacement: Float): Boolean {
        if (isLockGestureActive) {
            val taskBeingDragged = taskBeingDragged ?: return false
            val clampedDisplacement = if (verticalFactor > 0) {
                displacement.coerceIn(0f, maxLockDisplacement)
            } else {
                displacement.coerceIn(maxLockDisplacement, 0f)
            }
            taskBeingDragged.secondaryDismissTranslationProperty.setValue(taskBeingDragged, clampedDisplacement)
            if (taskBeingDragged.isRunningTask) {
                recentsView.runActionOnRemoteHandles { remoteTargetHandle ->
                    remoteTargetHandle.taskViewSimulator.taskSecondaryTranslation.value = clampedDisplacement
                }
                recentsView.redrawLiveTile()
            }
            val threshold = abs(LOCK_THRESHOLD_FRACTION * maxLockDisplacement)
            val current = abs(clampedDisplacement)
            val atThreshold = current >= threshold
            container.actionsView?.setLockPillAtThreshold(atThreshold)
            if (atThreshold && !hasLockThresholdHapticRun) {
                hasLockThresholdHapticRun = true
                recentsView.performHapticFeedback(android.view.HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE)
            } else if (!atThreshold && hasLockThresholdHapticRun) {
                hasLockThresholdHapticRun = false
            }
            return true
        } else {
            playbackController?.setPlayFraction(
                boundToRange(displacement / launchEndDisplacement, 0f, 1f)
            )
            return true
        }
    }

    override fun onDragEnd(velocity: Float) {
        if (isLockGestureActive) {
            val taskBeingDragged = taskBeingDragged ?: return
            val currentDisplacement =
                taskBeingDragged.secondaryDismissTranslationProperty.get(taskBeingDragged)
            val isBeyondLockThreshold =
                abs(currentDisplacement) >= abs(LOCK_THRESHOLD_FRACTION * maxLockDisplacement)

            if (isBeyondLockThreshold && isLockPillShown && !wasTouchCancelled) {
                val packageName = taskBeingDragged.firstTask?.key?.packageName
                if (packageName != null) {
                    LockedTaskManager.getInstance(container).setPackageLocked(
                        packageName, !wasLockedBeforeDrag
                    )
                    taskBeingDragged.updateLockState(packageName)
                }
            }

            hideLockPill()

            val dismissLength = abs(maxLockDisplacement).roundToInt()
            recentsView.runTaskDismissSettlingSpringAnimation(
                taskBeingDragged,
                false,
                RecentsDismissUtils.DismissedTaskData(
                    velocity,
                    dismissLength,
                    0f,
                    (LOCK_THRESHOLD_FRACTION * dismissLength).roundToInt(),
                ),
                false,
                false,
            )?.addEndListener {
                if (taskBeingDragged.isRunningTask) {
                    recentsView.runActionOnRemoteHandles { remoteTargetHandle ->
                        remoteTargetHandle.taskViewSimulator.taskSecondaryTranslation.value = 0f
                    }
                    recentsView.redrawLiveTile()
                }
                taskBeingDragged.translationZ = 0f
                taskBeingDragged.isBeingDismissed = false
                clearState()
            }
        } else {
            val playbackController = playbackController ?: return

            val isBeyondLaunchThreshold =
                abs(playbackController.progressFraction) > abs(LAUNCH_THRESHOLD_FRACTION)
            val velocityIsNegative = !recentsView.pagedOrientationHandler.isGoingUp(velocity, isRtl)
            val isFlingingTowardsLaunch = detector.isFling(velocity) && velocityIsNegative
            val isFlingingTowardsRestState = detector.isFling(velocity) && !velocityIsNegative
            val isLaunching =
                isFlingingTowardsLaunch || (isBeyondLaunchThreshold && !isFlingingTowardsRestState)

            val progress = playbackController.progressFraction
            var animationDuration =
                BaseSwipeDetector.calculateDuration(
                    velocity,
                    if (isLaunching) (1 - progress) else progress,
                )
            if (detector.isFling(velocity) && flingBlockCheck.isBlocked && !isLaunching) {
                animationDuration *= LauncherAnimUtils.blockedFlingDurationFactor(velocity).toLong()
            }

            playbackController.setEndAction(this::clearState)
            playbackController.startWithVelocity(
                container,
                isLaunching,
                velocity,
                launchEndDisplacement,
                animationDuration,
            )
        }
    }

    private fun showLockPill(isCurrentlyLocked: Boolean) {
        container.actionsView?.showLockPill(isCurrentlyLocked)
    }

    private fun hideLockPill() {
        container.actionsView?.hideLockPill()
    }

    private fun clearState() {
        detector.finishedScrolling()
        detector.setDetectableScrollConditions(0, false)
        if (isLockGestureActive) {
            taskBeingDragged?.let {
                it.secondaryDismissTranslationProperty.setValue(it, 0f)
                if (it.isRunningTask) {
                    recentsView.runActionOnRemoteHandles { remoteTargetHandle ->
                        remoteTargetHandle.taskViewSimulator.taskSecondaryTranslation.value = 0f
                    }
                    recentsView.redrawLiveTile()
                }
                it.translationZ = 0f
            }
            hideLockPill()
        }
        isLockPillShown = false
        isLockGestureActive = false
        wasTouchCancelled = false
        taskBeingDragged = null
        playbackController = null
    }

    companion object {
        private const val TAG = "TaskViewLaunchTouchController"
        private const val LAUNCH_THRESHOLD_FRACTION: Float = 0.5f
        private const val LOCK_DISPLACEMENT_FRACTION = 0.4f
        private const val LOCK_THRESHOLD_FRACTION = 0.5f
    }
}
