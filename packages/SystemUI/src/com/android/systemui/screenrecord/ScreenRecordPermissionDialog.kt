/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.systemui.screenrecord

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.os.UserHandle
import android.view.MotionEvent.ACTION_MOVE
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Switch
import androidx.annotation.LayoutRes
import com.android.systemui.R
import com.android.systemui.media.MediaProjectionAppSelectorActivity
import com.android.systemui.media.MediaProjectionCaptureTarget
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.settings.UserContextProvider

/** Dialog to select screen recording options */
class ScreenRecordPermissionDialog(
    context: Context,
    private val hostUserHandle: UserHandle,
    private val controller: RecordingController,
    private val activityStarter: ActivityStarter,
    private val userContextProvider: UserContextProvider,
    private val onStartRecordingClicked: Runnable?
) :
    BaseScreenSharePermissionDialog(
        context,
        createOptionList(),
        appName = null,
        R.drawable.ic_screenrecord,
        R.color.screenrecord_icon_color
    ) {
    private lateinit var tapsSwitch: Switch
    private lateinit var tapsSwitchContainer: ViewGroup
    private lateinit var tapsView: View
    private lateinit var audioSwitch: Switch
    private lateinit var audioSwitchContainer: ViewGroup
    private lateinit var options: Spinner
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setDialogTitle(R.string.screenrecord_permission_dialog_title)
        setTitle(R.string.screenrecord_title)
        setStartButtonText(R.string.screenrecord_permission_dialog_continue)
        setStartButtonOnClickListener { v: View? ->
            onStartRecordingClicked?.run()
            if (selectedScreenShareOption.mode == ENTIRE_SCREEN) {
                requestScreenCapture(/* captureTarget= */ null)
            }
            if (selectedScreenShareOption.mode == SINGLE_APP) {
                val intent = Intent(context, MediaProjectionAppSelectorActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                // We can't start activity for result here so we use result receiver to get
                // the selected target to capture
                intent.putExtra(
                    MediaProjectionAppSelectorActivity.EXTRA_CAPTURE_REGION_RESULT_RECEIVER,
                    CaptureTargetResultReceiver()
                )

                intent.putExtra(
                    MediaProjectionAppSelectorActivity.EXTRA_HOST_APP_USER_HANDLE,
                    hostUserHandle
                )
                activityStarter.startActivity(intent, /* dismissShade= */ true)
            }
            dismiss()
        }
        setCancelButtonOnClickListener { dismiss() }
        initRecordOptionsView()
    }

    @LayoutRes override fun getOptionsViewLayoutId(): Int = R.layout.screen_record_options

    @SuppressLint("ClickableViewAccessibility")
    private fun initRecordOptionsView() {
        audioSwitch = requireViewById(R.id.screenrecord_audio_switch)
        tapsSwitch = requireViewById(R.id.screenrecord_taps_switch)
        audioSwitchContainer = requireViewById(R.id.screenrecord_audio_switch_container)
        tapsSwitchContainer = requireViewById(R.id.screenrecord_taps_switch_container)

        // Add these listeners so that the switch only responds to movement
        // within its target region, to meet accessibility requirements
        audioSwitch.setOnTouchListener { _, event -> event.action == ACTION_MOVE }
        tapsSwitch.setOnTouchListener { _, event -> event.action == ACTION_MOVE }

        tapsView = requireViewById(R.id.show_taps)
        audioSwitchContainer.setOnClickListener { audioSwitch.toggle() }
        tapsSwitchContainer.setOnClickListener { tapsSwitch.toggle() }

        updateTapsViewVisibility()
        options = requireViewById(R.id.screen_recording_options)
        val a: ArrayAdapter<*> =
            ScreenRecordingAdapter(context, android.R.layout.simple_spinner_dropdown_item, MODES)
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        options.adapter = a
        options.setOnItemClickListenerInt { _: AdapterView<*>?, _: View?, _: Int, _: Long ->
            audioSwitch.isChecked = true
        }

        // disable redundant Touch & Hold accessibility action for Switch Access
        options.accessibilityDelegate =
            object : View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(
                    host: View,
                    info: AccessibilityNodeInfo
                ) {
                    info.removeAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_LONG_CLICK)
                    super.onInitializeAccessibilityNodeInfo(host, info)
                }
            }
        options.isLongClickable = false
    }

    override fun onItemSelected(adapterView: AdapterView<*>?, view: View, pos: Int, id: Long) {
        super.onItemSelected(adapterView, view, pos, id)
        updateTapsViewVisibility()
    }

    private fun updateTapsViewVisibility() {
        tapsView.visibility = if (selectedScreenShareOption.mode == SINGLE_APP) GONE else VISIBLE
    }

    /**
     * Starts screen capture after some countdown
     *
     * @param captureTarget target to capture (could be e.g. a task) or null to record the whole
     *   screen
     */
    private fun requestScreenCapture(captureTarget: MediaProjectionCaptureTarget?) {
        val userContext = userContextProvider.userContext
        val showTaps = selectedScreenShareOption.mode != SINGLE_APP && tapsSwitch.isChecked
        val audioMode =
            if (audioSwitch.isChecked) options.selectedItem as ScreenRecordingAudioSource
            else ScreenRecordingAudioSource.NONE
        val startIntent =
            PendingIntent.getForegroundService(
                userContext,
                RecordingService.REQUEST_CODE,
                RecordingService.getStartIntent(
                    userContext,
                    Activity.RESULT_OK,
                    audioMode.ordinal,
                    showTaps,
                    captureTarget
                ),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        val stopIntent =
            PendingIntent.getService(
                userContext,
                RecordingService.REQUEST_CODE,
                RecordingService.getStopIntent(userContext),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        controller.startCountdown(DELAY_MS, INTERVAL_MS, startIntent, stopIntent)
    }

    private inner class CaptureTargetResultReceiver() :
        ResultReceiver(Handler(Looper.getMainLooper())) {
        override fun onReceiveResult(resultCode: Int, resultData: Bundle) {
            if (resultCode == Activity.RESULT_OK) {
                val captureTarget =
                    resultData.getParcelable(
                        MediaProjectionAppSelectorActivity.KEY_CAPTURE_TARGET,
                        MediaProjectionCaptureTarget::class.java
                    )

                // Start recording of the selected target
                requestScreenCapture(captureTarget)
            }
        }
    }

    companion object {
        private val MODES =
            listOf(
                ScreenRecordingAudioSource.INTERNAL,
                ScreenRecordingAudioSource.MIC,
                ScreenRecordingAudioSource.MIC_AND_INTERNAL
            )
        private const val DELAY_MS: Long = 3000
        private const val INTERVAL_MS: Long = 1000
        private fun createOptionList(): List<ScreenShareOption> {
            return listOf(
                ScreenShareOption(
                    SINGLE_APP,
                    R.string.screen_share_permission_dialog_option_single_app,
                    R.string.screenrecord_permission_dialog_warning_single_app
                ),
                ScreenShareOption(
                    ENTIRE_SCREEN,
                    R.string.screen_share_permission_dialog_option_entire_screen,
                    R.string.screenrecord_permission_dialog_warning_entire_screen
                )
            )
        }
    }
}
