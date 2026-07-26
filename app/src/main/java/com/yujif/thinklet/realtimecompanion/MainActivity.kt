package com.yujif.thinklet.realtimecompanion

import com.yujif.thinklet.realtimecompanion.audio.AssistantAudioPlayer
import com.yujif.thinklet.realtimecompanion.audio.MicrophoneStreamer
import com.yujif.thinklet.realtimecompanion.camera.CameraSessionRecorder
import com.yujif.thinklet.realtimecompanion.core.PhysicalButtonCommand
import com.yujif.thinklet.realtimecompanion.core.RealtimeCompanionUseCaseSession
import com.yujif.thinklet.realtimecompanion.core.SessionPaths
import com.yujif.thinklet.realtimecompanion.core.physicalButtonCommand
import com.yujif.thinklet.realtimecompanion.openai.RealtimeCompanionToolRequest
import com.yujif.thinklet.realtimecompanion.openai.RealtimeCompanionUseCase
import com.yujif.thinklet.realtimecompanion.openai.RealtimeSession
import com.yujif.thinklet.realtimecompanion.security.ApiKeyStore
import com.yujif.thinklet.realtimecompanion.session.HandlerRealtimeClockScheduler
import com.yujif.thinklet.realtimecompanion.session.RealtimeClockAction
import com.yujif.thinklet.realtimecompanion.session.RealtimeSessionUsageHandler
import com.yujif.thinklet.realtimecompanion.session.RealtimeSessionClockController
import com.yujif.thinklet.realtimecompanion.session.RealtimeSessionClockActionPresenter
import com.yujif.thinklet.realtimecompanion.session.RealtimeCompanionUseCaseSessionFactory
import com.yujif.thinklet.realtimecompanion.session.RealtimeSessionEventPresenter
import com.yujif.thinklet.realtimecompanion.session.SessionActiveResources
import com.yujif.thinklet.realtimecompanion.session.SessionRecordingConfig
import com.yujif.thinklet.realtimecompanion.session.SessionRecordingStartResult
import com.yujif.thinklet.realtimecompanion.session.SessionRecordingStarter
import com.yujif.thinklet.realtimecompanion.session.SessionPermissionPresenter
import com.yujif.thinklet.realtimecompanion.session.SessionRecorder
import com.yujif.thinklet.realtimecompanion.session.DEFAULT_MAX_CAMERA_BYTES
import com.yujif.thinklet.realtimecompanion.session.DEFAULT_MAX_SESSION_BYTES
import com.yujif.thinklet.realtimecompanion.session.SessionRuntimeState
import com.yujif.thinklet.realtimecompanion.session.SessionStartPresenter
import com.yujif.thinklet.realtimecompanion.session.SessionStartRecorder
import com.yujif.thinklet.realtimecompanion.session.SessionStopRecorder
import com.yujif.thinklet.realtimecompanion.session.SessionStopPresenter
import com.yujif.thinklet.realtimecompanion.session.SessionUseCaseSelectionPresenter
import com.yujif.thinklet.realtimecompanion.session.startActiveResources
import com.yujif.thinklet.realtimecompanion.tts.JapaneseTtsManager
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity(), RealtimeSession.Listener {
    private var statusText by mutableStateOf("待機中")
    private var apiKeyText by mutableStateOf("")
    private var isStoppingConsultation = false
    private var cameraKeyRepeatSeen = false
    private val sessionState =
        SessionRuntimeState<SessionPaths, SessionRecorder, RealtimeCompanionUseCaseSession>()
    private val activeResources =
        SessionActiveResources<RealtimeSession, MicrophoneStreamer, AssistantAudioPlayer, CameraSessionRecorder>()
    private var usageHandler = RealtimeSessionUsageHandler(maxCostUsd = MAX_COST_USD)
    private var selectedRealtimeUseCase = RealtimeCompanionUseCase.fromId(BuildConfig.REALTIME_COMPANION_USE_CASE)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val realtimeClockController = RealtimeSessionClockController(
        scheduler = HandlerRealtimeClockScheduler(mainHandler),
        nowMillis = { System.currentTimeMillis() },
        tickMillis = REALTIME_CLOCK_TICK_MILLIS,
        noticeMillis = ELAPSED_NOTICE_MILLIS,
        extensionIntervalMillis = EXTENSION_PROMPT_MILLIS,
        extensionTimeoutMillis = EXTENSION_TIMEOUT_MILLIS,
        onAction = { handleRealtimeClockAction(it) },
    )
    private lateinit var tts: JapaneseTtsManager
    private val apiKeyStore by lazy { ApiKeyStore(this) }

    // Polls the active recorder's storage budget once a second while a session is running.
    // Writes happen on background threads (mic, camera analysis); checking here keeps the
    // stop decision and the state it touches (statusText, TTS) on the main thread.
    private val storageGuardTick = object : Runnable {
        override fun run() {
            val recorder = sessionState.currentRecorder() ?: return
            val camera = activeResources.cameraSessionRecorder
            if (camera?.hasRecordingFailed == true ||
                recorder.shouldStopForStorage(externalBytes = camera?.currentOutputBytes ?: 0L)
            ) {
                stopConsultation("storage_limit")
            } else {
                mainHandler.postDelayed(this, STORAGE_GUARD_INTERVAL_MILLIS)
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        statusText = SessionPermissionPresenter.presentPermissionResult(
            hasRequiredPermissions = hasRequiredPermissions(),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        apiKeyStore.migrateFromLegacyPlaintext(getPreferences(Context.MODE_PRIVATE))
        apiKeyText = apiKeyStore.get()
        tts = JapaneseTtsManager(this)
        tts.initialize()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(
                        statusText = statusText,
                        useCaseName = selectedRealtimeUseCase.japaneseSpeechName,
                        apiKey = apiKeyText,
                        sessionPath = sessionState.currentSessionPathDisplay { paths ->
                            paths.sessionDir.absolutePath
                        },
                        onApiKeyChanged = {
                            apiKeyText = it
                            apiKeyStore.set(it)
                        },
                        onDeleteApiKey = {
                            apiKeyText = ""
                            apiKeyStore.clear()
                        },
                        onRequestPermissions = { requestPermissions() },
                        onStart = { startConsultation(manual = true) },
                        onStop = { stopConsultation("manual_stop") },
                    )
                }
            }
        }
        configurePhysicalButtonFocus()
        if (!hasRequiredPermissions()) {
            requestPermissions()
        }
    }

    override fun onResume() {
        super.onResume()
        configurePhysicalButtonFocus()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroy() {
        stopConsultation("activity_destroy")
        tts.shutdown()
        super.onDestroy()
    }

    // ComponentActivity.dispatchKeyEvent is a standard Activity/Window.Callback override point;
    // androidx lint's library-group-prefix check misfires on the super call here.
    @Suppress("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Injected key events (input keyevent --longpress) arrive with downTime == eventTime,
        // so a repeat ACTION_DOWN is the reliable long-press signal alongside the duration.
        if (event.keyCode == KeyEvent.KEYCODE_CAMERA &&
            event.action == KeyEvent.ACTION_DOWN &&
            event.repeatCount > 0
        ) {
            cameraKeyRepeatSeen = true
        }
        val pressDurationMillis = event.eventTime - event.downTime
        val command = physicalButtonCommand(
            keyCode = event.keyCode,
            action = event.action,
            repeatCount = event.repeatCount,
            isLongPress = cameraKeyRepeatSeen ||
                pressDurationMillis >= ViewConfiguration.getLongPressTimeout(),
            isSessionActive = activeResources.isSessionActive,
            isAwaitingExtension = realtimeClockController.isAwaitingExtension,
            cameraKeyCode = KeyEvent.KEYCODE_CAMERA,
            volumeUpKeyCode = KeyEvent.KEYCODE_VOLUME_UP,
            volumeDownKeyCode = KeyEvent.KEYCODE_VOLUME_DOWN,
            keyDownAction = KeyEvent.ACTION_DOWN,
            keyUpAction = KeyEvent.ACTION_UP,
        )
        Log.d(
            TAG,
            "dispatchKeyEvent keyCode=${event.keyCode} action=${event.action} " +
                "repeat=${event.repeatCount} pressMillis=$pressDurationMillis command=$command",
        )
        if (event.keyCode == KeyEvent.KEYCODE_CAMERA && event.action == KeyEvent.ACTION_UP) {
            cameraKeyRepeatSeen = false
        }
        if (command == null) return super.dispatchKeyEvent(event)

        when (command) {
            PhysicalButtonCommand.Start -> startConsultation(manual = true)
            PhysicalButtonCommand.Stop -> stopConsultation("physical_button")
            PhysicalButtonCommand.Extend -> extendConsultation()
            PhysicalButtonCommand.PreviousUseCase -> selectRealtimeUseCase(
                RealtimeCompanionUseCase.previousBefore(selectedRealtimeUseCase),
            )
            PhysicalButtonCommand.NextUseCase -> selectRealtimeUseCase(
                RealtimeCompanionUseCase.nextAfter(selectedRealtimeUseCase),
            )
            PhysicalButtonCommand.Ignore -> Unit
        }
        return true
    }

    private fun configurePhysicalButtonFocus() {
        takeKeyEvents(true)
        window.decorView.isFocusable = true
        window.decorView.isFocusableInTouchMode = true
        window.decorView.requestFocus()
    }

    override fun onConnected() {
        mainHandler.post {
            handleConnected()
        }
    }

    private fun handleConnected() {
        val presentation = RealtimeSessionEventPresenter.presentConnected(
            nowMillis = System.currentTimeMillis(),
        )
        statusText = presentation.statusText
        emitSessionOutputs(
            speech = presentation.speech,
            utteranceId = presentation.utteranceId,
            timelineJson = presentation.timelineJson,
        )
    }

    override fun onDisconnected(reason: String) {
        mainHandler.post {
            handleDisconnected(reason)
        }
    }

    private fun handleDisconnected(reason: String) {
        val presentation = RealtimeSessionEventPresenter.presentDisconnected(
            reason = reason,
            nowMillis = System.currentTimeMillis(),
        )
        statusText = presentation.statusText
        emitSessionOutputs(
            speech = presentation.speech,
            utteranceId = presentation.utteranceId,
            timelineJson = presentation.timelineJson,
        )
        // The realtime connection can end on its own (server closed it, network dropped) as
        // well as through a planned stopConsultation() call. Route both through the same
        // teardown so mic/camera/clock don't keep running after the connection is gone.
        // stopConsultation() itself is reentrancy-guarded, so this is a no-op when we're the
        // ones who triggered the disconnect in the first place.
        stopConsultation(reason)
    }

    override fun onAssistantAudio(bytes: ByteArray) {
        mainHandler.post {
            activeResources.playAssistantAudio(
                bytes = bytes,
                playAssistantAudio = AssistantAudioPlayer::play,
            )
        }
    }

    override fun onError(message: String) {
        mainHandler.post {
            handleError(message)
        }
    }

    private fun handleError(message: String) {
        val presentation = RealtimeSessionEventPresenter.presentError(message)
        statusText = presentation.statusText
        emitSessionOutputs(
            speech = presentation.speech,
            utteranceId = presentation.utteranceId,
            timelineJson = presentation.timelineJson,
        )
        // A connection failure leaves the same dangling mic/camera/clock as an unplanned
        // disconnect; tear the session down the same way. No-op if nothing is active (see
        // stopConsultation's guard) or if we're already tearing down (reentrancy guard).
        stopConsultation(message)
    }

    override fun onUsageEvent(json: String) {
        mainHandler.post {
            handleUsageEvent(json)
        }
    }

    private fun handleUsageEvent(json: String) {
        usageHandler.handleUsageEvent(
            json = json,
            appendUsage = {
                sessionState.appendUsage(
                    json = it,
                    appendUsage = SessionRecorder::appendUsage,
                )
            },
            speak = { speech, utteranceId -> tts.speak(speech, utteranceId) },
            stop = { stopConsultation(it) },
        )
    }

    private fun requestPermissions() {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
            ),
        )
    }

    private fun hasRequiredPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun startConsultation(manual: Boolean) {
        if (!hasRequiredPermissions()) {
            statusText = SessionPermissionPresenter.presentPermissionResult(
                hasRequiredPermissions = false,
            )
            requestPermissions()
            return
        }
        if (apiKeyText.isBlank()) {
            val presentation = SessionStartPresenter.presentMissingApiKey()
            statusText = presentation.statusText
            tts.speak(presentation.speech, presentation.utteranceId)
            return
        }
        if (activeResources.isSessionActive) return

        val startResult = SessionRecordingStarter.start(
            root = getExternalFilesDir(null) ?: filesDir,
            sessionId = createSessionId(),
            config = SessionRecordingConfig(
                model = MODEL,
                useCase = selectedRealtimeUseCase,
                frameCadenceMillis = FRAME_CADENCE_MILLIS,
                maxCostUsd = MAX_COST_USD,
                elapsedNoticeMillis = ELAPSED_NOTICE_MILLIS,
                extensionPromptMillis = EXTENSION_PROMPT_MILLIS,
                extensionTimeoutMillis = EXTENSION_TIMEOUT_MILLIS,
                minFreeBytesRequired = MIN_FREE_BYTES_REQUIRED,
            ),
        )
        val recording = when (startResult) {
            is SessionRecordingStartResult.Started -> startResult
            SessionRecordingStartResult.NotEnoughStorage -> {
                val presentation = SessionStartPresenter.presentNotEnoughStorage()
                statusText = presentation.statusText
                tts.speak(presentation.speech, presentation.utteranceId)
                return
            }
        }

        sessionState.startActiveSession(
            paths = recording.paths,
            recorder = recording.recorder,
            useCaseSession = RealtimeCompanionUseCaseSessionFactory.start(
                useCase = selectedRealtimeUseCase,
                paths = recording.paths,
            ),
        )
        usageHandler = RealtimeSessionUsageHandler(maxCostUsd = MAX_COST_USD)
        sessionState.recordStartRequested(
            manual = manual,
            nowMillis = System.currentTimeMillis(),
            recordStartRequested = SessionStartRecorder::recordStartRequested,
        )
        val presentation = SessionStartPresenter.presentStarting()
        statusText = presentation.statusText
        tts.speak(presentation.speech, presentation.utteranceId)

        try {
            activeResources.startActiveResources(
                context = this,
                lifecycleOwner = this,
                fallbackFilesDir = filesDir,
                apiKeyProvider = { apiKeyText },
                recorderProvider = { sessionState.currentRecorder() },
                listener = this,
                model = MODEL,
                useCaseContextProvider = {
                    sessionState.currentUseCasePromptContext(RealtimeCompanionUseCaseSession::promptContext)
                },
                useCase = selectedRealtimeUseCase,
                pathsProvider = { sessionState.currentPaths() },
                frameCadenceMillis = FRAME_CADENCE_MILLIS,
            )
        } catch (t: Throwable) {
            // Whatever resources did start (assistant audio, the realtime connection, the
            // microphone) were already rolled back inside startActiveResources before this
            // exception reached us. activeResources itself was never set, so stopConsultation
            // only needs to persist/record/notify the recording session that never fully started.
            Log.w(TAG, "Failed to start realtime session resources", t)
            stopConsultation("start_failed")
            return
        }
        realtimeClockController.start()
        mainHandler.postDelayed(storageGuardTick, STORAGE_GUARD_INTERVAL_MILLIS)
    }

    private fun stopConsultation(reason: String) {
        if (isStoppingConsultation) return
        if (!activeResources.isSessionActive && sessionState.currentRecorder() == null) return
        isStoppingConsultation = true
        try {
            mainHandler.removeCallbacks(storageGuardTick)
            realtimeClockController.stop()
            activeResources.stopActiveResources(
                reason = reason,
                disconnectRealtime = { realtimeSession, stopReason -> realtimeSession.disconnect(stopReason) },
                stopMicrophone = { it.stop() },
                stopAssistantAudio = { it.stop() },
                stopCamera = { it.stop() },
            )
            sessionState.persistUseCaseSession(
                context = "session_stop",
                persist = RealtimeCompanionUseCaseSession::persist,
                appendTimeline = {
                    emitSessionOutputs(
                        speech = null,
                        utteranceId = null,
                        timelineJson = it,
                    )
                },
                onFailure = { error ->
                    Log.w(TAG, "Failed to persist use-case session", error)
                },
            )
            sessionState.recordStopped(
                reason = reason,
                nowMillis = System.currentTimeMillis(),
                recordStopped = SessionStopRecorder::recordStopped,
            )
            val presentation = SessionStopPresenter.presentStopped(reason)
            emitSessionOutputs(
                speech = presentation.speech,
                utteranceId = presentation.utteranceId,
                timelineJson = null,
            )
            statusText = presentation.statusText
            sessionState.clearActiveSession()
        } finally {
            isStoppingConsultation = false
        }
    }

    private fun handleRealtimeClockAction(action: RealtimeClockAction?) {
        if (action == null) return
        val presentation = RealtimeSessionClockActionPresenter.present(
            action = action,
            nowMillis = System.currentTimeMillis(),
        )
        presentation.stopReason?.let {
            stopConsultation(it)
            return
        }
        presentation.statusText?.let { statusText = it }
        emitSessionOutputs(
            speech = presentation.speech,
            utteranceId = presentation.utteranceId,
            timelineJson = presentation.timelineJson,
        )
    }

    private fun extendConsultation() {
        realtimeClockController.extend()
    }

    private fun selectRealtimeUseCase(useCase: RealtimeCompanionUseCase) {
        selectedRealtimeUseCase = useCase
        val presentation = SessionUseCaseSelectionPresenter.presentSelected(useCase)
        statusText = presentation.statusText
        tts.speak(presentation.speech, presentation.utteranceId)
    }

    private fun createSessionId(): String {
        return SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss'Z'", Locale.US).format(Date())
    }

    override fun onToolRequest(request: RealtimeCompanionToolRequest) {
        mainHandler.post {
            handleToolRequest(request)
        }
    }

    private fun handleToolRequest(request: RealtimeCompanionToolRequest) {
        sessionState.handleUseCaseToolRequest(
            request = request,
            handleToolRequest = RealtimeCompanionUseCaseSession::handleToolRequest,
            sendFunctionCallOutput = { callId, output ->
                activeResources.sendFunctionCallOutput(
                    callId = callId,
                    output = output,
                    sendFunctionCallOutput = RealtimeSession::sendFunctionCallOutput,
                )
            },
            refreshInstructions = {
                activeResources.refreshRealtimeInstructions(
                    refreshInstructions = RealtimeSession::refreshInstructions,
                )
            },
            appendTimeline = {
                emitSessionOutputs(
                    speech = null,
                    utteranceId = null,
                    timelineJson = it,
                )
            },
            onPersistFailure = { error ->
                Log.w(TAG, "Failed to persist use-case session", error)
            },
        )
    }

    private fun emitSessionOutputs(
        speech: String?,
        utteranceId: String?,
        timelineJson: String?,
    ) {
        sessionState.emitOptionalOutputs(
            speech = speech,
            utteranceId = utteranceId,
            timelineJson = timelineJson,
            speak = tts::speak,
            appendTimeline = SessionRecorder::appendTimeline,
            onTimelineFailure = { error -> Log.w(TAG, "Failed to append timeline", error) },
        )
    }

    private companion object {
        const val TAG = "MainActivity"
        const val MODEL = "gpt-realtime-2"
        const val FRAME_CADENCE_MILLIS = 2_000L
        const val MAX_COST_USD = 2.0
        const val ELAPSED_NOTICE_MILLIS = 10 * 60 * 1_000L
        const val EXTENSION_PROMPT_MILLIS = 30 * 60 * 1_000L
        const val EXTENSION_TIMEOUT_MILLIS = 30 * 1_000L
        const val STORAGE_GUARD_INTERVAL_MILLIS = 1_000L
        const val REALTIME_CLOCK_TICK_MILLIS = 1_000L
        const val MIN_FREE_BYTES_REQUIRED = DEFAULT_MAX_SESSION_BYTES + DEFAULT_MAX_CAMERA_BYTES
    }
}

@Composable
private fun MainScreen(
    statusText: String,
    useCaseName: String,
    apiKey: String,
    sessionPath: String,
    onApiKeyChanged: (String) -> Unit,
    onDeleteApiKey: () -> Unit,
    onRequestPermissions: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Realtime Companion", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))
        Text("現在のモード: $useCaseName")
        Spacer(modifier = Modifier.height(8.dp))
        Text(statusText)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = apiKey,
            onValueChange = onApiKeyChanged,
            label = { Text("OpenAI API Key") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "実験用の BYOK です。保存したキーの保護は端末のセキュリティ機構に依存します。使用量に上限を設定した専用キーを利用してください。",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onDeleteApiKey, enabled = apiKey.isNotBlank()) {
            Text("キーを削除")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onRequestPermissions) {
                Text("権限")
            }
            Button(onClick = onStart) {
                Text("開始")
            }
            Button(onClick = onStop) {
                Text("停止")
            }
        }
        if (sessionPath.isNotBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(sessionPath, style = MaterialTheme.typography.bodySmall)
        }
    }
}
