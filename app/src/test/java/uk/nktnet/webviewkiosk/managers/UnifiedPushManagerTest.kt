package uk.nktnet.webviewkiosk.managers

import android.content.Context
import android.os.Looper
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.unifiedpush.android.connector.INSTANCE_DEFAULT
import org.unifiedpush.android.connector.data.PublicKeySet
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage
import uk.nktnet.webviewkiosk.config.HistoryEntry
import uk.nktnet.webviewkiosk.config.SystemSettings
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.config.UserSettingsKeys
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundClearHistoryCommand
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundCommandMessage
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundGoBackCommand
import uk.nktnet.webviewkiosk.config.remote.inbound.InboundSettingsMessage
import uk.nktnet.webviewkiosk.config.unifiedpush.UnifiedPushEndpoint
import uk.nktnet.webviewkiosk.config.unifiedpush.UnifiedPushPublicKeySet
import uk.nktnet.webviewkiosk.managers.RemoteMessageManager.RemoteMessage
import uk.nktnet.webviewkiosk.managers.RemoteMessageManager.RemoteMessage.Source
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class UnifiedPushManagerTest {
    private lateinit var context: Context
    private lateinit var settings: SystemSettings
    private lateinit var userSettings: UserSettings
    private val initialHome = "https://example.com/original"
    private val updatedHome = "https://example.com/updated"
    private val currentInstance = "current-registration"
    private val initialEndpoint = UnifiedPushEndpoint(
        UnifiedPushPublicKeySet("saved-auth", "saved-key"),
        "https://push.example.com/current",
        temporary = false,
        redacted = false,
    )

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(UserSettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences("system_settings", Context.MODE_PRIVATE)
            .edit().clear().commit()
        settings = SystemSettings(context)
        userSettings = UserSettings(context)
        userSettings.unifiedPushEnabled = true
        userSettings.unifiedPushInstance = currentInstance
        userSettings.unifiedPushProcessUnencryptedMessages = false
        userSettings.unifiedPushStoreEndpointCredentials = true
        userSettings.mqttUsername = "operator"
        userSettings.homeUrl = initialHome
        settings.unifiedpushEndpoint = initialEndpoint
        seedHistory()
        UnifiedPushManager.clearLogs()
    }

    @After
    fun tearDown() {
        dispatchCommands {}
        runBlocking {
            val marker = "settings-drain-" + UUID.randomUUID()
            val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                RemoteMessageManager.settingsAppliedFlow.first { it.message.messageId == marker }
            }
            try {
                RemoteMessageManager.emitSettingsApplied(
                    InboundSettingsMessage(messageId = marker),
                    Source.UNIFIEDPUSH,
                )
                withTimeout(5_000) { collector.join() }
            } finally {
                collector.cancel()
            }
        }
        ToastManager.cancel()
    }

    @Test
    fun disabledPushCannotExecuteCommands() {
        userSettings.unifiedPushEnabled = false
        val observed = dispatchCommands { send(commandPayload()) }

        assertTrue(observed.isEmpty())
        assertEquals(2, settings.historyStack.size)
    }

    @Test
    fun currentEncryptedCommandExecutesWithoutAnActivityOrMqttHost() {
        val observed = dispatchCommands { send(commandPayload()) }

        assertEquals(1, observed.size)
        assertTrue(observed.single().message is InboundClearHistoryCommand)
        assertEquals(Source.UNIFIEDPUSH, observed.single().source)
        assertEquals(listOf("second"), settings.historyStack.map { it.id })
        assertEquals(0, settings.historyIndex)
    }

    @Test
    fun plaintextCommandIsRejectedWithoutOptIn() {
        val observed = dispatchCommands { send(commandPayload(), decrypted = false) }

        assertTrue(observed.isEmpty())
        assertEquals(2, settings.historyStack.size)
    }

    @Test
    fun plaintextCommandIsAcceptedWithOptIn() {
        userSettings.unifiedPushProcessUnencryptedMessages = true
        val observed = dispatchCommands { send(commandPayload(), decrypted = false) }

        assertEquals(1, observed.size)
        assertEquals(1, settings.historyStack.size)
    }

    @Test
    fun staleCommandCannotExecuteEvenWhenPayloadTargetsMatch() {
        val observed = dispatchCommands {
            send(
                commandPayload(listOf(settings.appInstanceId), listOf("operator")),
                instance = "old-registration",
            )
        }

        assertTrue(observed.isEmpty())
        assertEquals(2, settings.historyStack.size)
    }

    @Test
    fun staleSettingsCannotChangePreferences() {
        send(settingsPayload(), instance = "old-registration")

        assertEquals(initialHome, userSettings.homeUrl)
    }

    @Test
    fun changedInstanceIsCheckedOnEveryDelivery() {
        send(settingsPayload())
        assertEquals(updatedHome, userSettings.homeUrl)

        userSettings.unifiedPushInstance = "replacement-registration"
        send(settingsPayload(home = initialHome), instance = currentInstance)
        assertEquals(updatedHome, userSettings.homeUrl)

        send(settingsPayload(home = initialHome), instance = "replacement-registration")
        assertEquals(initialHome, userSettings.homeUrl)
    }

    @Test
    fun omittedNullAndEmptyTargetsAllowBroadcastCommands() {
        val payloads = listOf(
            commandPayload(),
            commandPayload(emptyList(), emptyList()),
            """{"type":"command","command":"clear_history","interact":false,"targetInstances":null,"targetUsernames":null}""",
        )
        for (payload in payloads) {
            seedHistory()
            val observed = dispatchCommands { send(payload) }

            assertEquals(1, observed.size)
            assertEquals(1, settings.historyStack.size)
        }
    }

    @Test
    fun commandRequiresAMatchingAppInstanceWhenTargetsAreSpecified() {
        val observed = dispatchCommands {
            send(commandPayload(listOf("another-device"), listOf("operator")))
        }

        assertTrue(observed.isEmpty())
        assertEquals(2, settings.historyStack.size)
    }

    @Test
    fun commandRequiresAMatchingUsernameWhenTargetsAreSpecified() {
        val observed = dispatchCommands {
            send(commandPayload(listOf(settings.appInstanceId), listOf("another-user")))
        }

        assertTrue(observed.isEmpty())
        assertEquals(2, settings.historyStack.size)
    }

    @Test
    fun commandAcceptsMembershipInBothTargetSets() {
        val observed = dispatchCommands {
            send(commandPayload(
                listOf("another-device", settings.appInstanceId),
                listOf("another-user", "operator"),
            ))
        }

        assertEquals(1, observed.size)
        assertEquals(1, settings.historyStack.size)
    }

    @Test
    fun registrationInstanceDoesNotSubstituteForThePayloadAppInstanceId() {
        val observed = dispatchCommands {
            send(commandPayload(listOf(currentInstance), listOf("operator")))
        }

        assertTrue(observed.isEmpty())
        assertEquals(2, settings.historyStack.size)
    }

    @Test
    fun settingsRequireAMatchingAppInstance() {
        send(settingsPayload(listOf("another-device"), listOf("operator")))

        assertEquals(initialHome, userSettings.homeUrl)
    }

    @Test
    fun settingsRequireAMatchingUsername() {
        send(settingsPayload(listOf(settings.appInstanceId), listOf("another-user")))

        assertEquals(initialHome, userSettings.homeUrl)
    }

    @Test
    fun omittedNullAndEmptyTargetsAllowBroadcastSettings() {
        val payloads = listOf(
            settingsPayload(),
            settingsPayload(emptyList(), emptyList()),
            JSONObject(settingsPayload())
                .put("targetInstances", JSONObject.NULL)
                .put("targetUsernames", JSONObject.NULL)
                .toString(),
        )
        for (payload in payloads) {
            userSettings.homeUrl = initialHome
            send(payload)
            assertEquals(updatedHome, userSettings.homeUrl)
        }
    }

    @Test
    fun acceptedSettingsApplyAndPublishTheirSourceAndReloadPolicy() = runBlocking {
        val applied = mutableListOf<RemoteMessage<InboundSettingsMessage>>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            RemoteMessageManager.settingsAppliedFlow.take(1).collect { applied.add(it) }
        }
        try {
            send(settingsPayload(listOf(settings.appInstanceId), listOf("operator")))
            withTimeout(5_000) { collector.join() }

            assertEquals(updatedHome, userSettings.homeUrl)
            assertEquals(Source.UNIFIEDPUSH, applied.single().source)
            assertEquals("settings-message", applied.single().message.messageId)
            assertEquals(false, applied.single().message.reloadActivity)
            assertEquals(false, applied.single().message.showToast)
        } finally {
            collector.cancel()
        }
    }

    @Test
    fun disabledPushCannotApplySettings() {
        userSettings.unifiedPushEnabled = false
        send(settingsPayload())

        assertEquals(initialHome, userSettings.homeUrl)
    }

    @Test
    fun plaintextSettingsFollowTheSameOptInPolicy() {
        send(settingsPayload(), decrypted = false)
        assertEquals(initialHome, userSettings.homeUrl)

        userSettings.unifiedPushProcessUnencryptedMessages = true
        send(settingsPayload(), decrypted = false)
        assertEquals(updatedHome, userSettings.homeUrl)
    }

    @Test
    fun malformedCommandsCannotExecuteOrPoisonTheNextDelivery() {
        val observed = dispatchCommands {
            for (payload in listOf(
                """{"type":"command","command":"unknown"}""",
                """{"type":"command","command":"go_to_url"}""",
                """{"type":"command","command":"clear_history","targetInstances":{}}""",
            )) {
                send(payload)
            }
            send(commandPayload())
        }

        assertEquals(1, observed.size)
        assertEquals(1, settings.historyStack.size)
        assertEquals(3, UnifiedPushManager.debugLogHistory.count { it.tag == "message handler error" })
    }

    @Test
    fun malformedSettingsCannotMutatePreferencesOrPoisonTheNextDelivery() {
        for (payload in listOf(
            """{"type":"settings","data":{"settings":[]}}""",
            """{"type":"settings","data":{"settings":"invalid"}}""",
            """{"type":"settings","targetUsernames":{},"data":{"settings":{}}}""",
        )) {
            send(payload)
            assertEquals(initialHome, userSettings.homeUrl)
        }
        send(settingsPayload())
        assertEquals(updatedHome, userSettings.homeUrl)
    }

    @Test
    fun emptyMalformedAndUnsupportedEnvelopesDoNotDispatchCommands() {
        val observed = dispatchCommands {
            for (payload in listOf(
                "", " ", "[]", "{",
                """{}""",
                """{"type":"unsupported"}""",
                """{"type":"request","requestType":"get_status"}""",
            )) {
                send(payload)
            }
        }

        assertTrue(observed.isEmpty())
        assertEquals(2, settings.historyStack.size)
        assertEquals(initialHome, userSettings.homeUrl)
    }

    @Test
    fun staleEndpointCannotReplaceCurrentEndpointCredentials() {
        UnifiedPushManager.handleNewEndpoint(context, endpoint(), "old-registration")

        assertEquals(initialEndpoint, settings.unifiedpushEndpoint)
    }

    @Test
    fun staleUnregistrationCannotEraseCurrentEndpointCredentials() {
        UnifiedPushManager.handleUnregistered(context, "old-registration")

        assertEquals(initialEndpoint, settings.unifiedpushEndpoint)
    }

    @Test
    fun currentEndpointStoresCredentialsAndTemporaryStatusWhenAllowed() {
        UnifiedPushManager.handleNewEndpoint(context, endpoint(), currentInstance)

        assertEquals(
            UnifiedPushEndpoint(
                UnifiedPushPublicKeySet("new-auth", "new-public-key"),
                "https://push.example.com/new",
                temporary = true,
                redacted = false,
            ),
            settings.unifiedpushEndpoint,
        )
    }

    @Test
    fun currentEndpointRedactsCredentialsWhenStorageIsDisabled() {
        userSettings.unifiedPushStoreEndpointCredentials = false
        UnifiedPushManager.handleNewEndpoint(context, endpoint(), currentInstance)

        assertEquals(
            UnifiedPushEndpoint(
                UnifiedPushPublicKeySet("(redacted)", "(redacted)"),
                "(redacted)",
                temporary = true,
                redacted = true,
            ),
            settings.unifiedpushEndpoint,
        )
    }

    @Test
    fun currentUnregistrationClearsCredentialsEvenAfterMessagesAreDisabled() {
        userSettings.unifiedPushEnabled = false
        UnifiedPushManager.handleUnregistered(context, currentInstance)

        assertNull(settings.unifiedpushEndpoint)
    }

    @Test
    fun emptyConfiguredInstanceUsesTheConnectorDefault() {
        userSettings.unifiedPushInstance = ""
        assertEquals(INSTANCE_DEFAULT, UnifiedPushManager.getInstance(context))
        send(settingsPayload(), instance = INSTANCE_DEFAULT)

        assertEquals(updatedHome, userSettings.homeUrl)
    }

    @Test
    fun instanceVariablesAreExpandedBeforeAcceptingACallback() {
        val template = "registration-" + '$' + "{APP_INSTANCE_ID}"
        userSettings.unifiedPushInstance = template
        val resolved = "registration-" + settings.appInstanceId
        assertEquals(resolved, UnifiedPushManager.getInstance(context))

        send(settingsPayload(), instance = template)
        assertEquals(initialHome, userSettings.homeUrl)
        send(settingsPayload(), instance = resolved)
        assertEquals(updatedHome, userSettings.homeUrl)
    }

    private fun seedHistory() {
        settings.historyStack = listOf(
            HistoryEntry("first", "https://example.com/first", 100),
            HistoryEntry("second", "https://example.com/second", 200),
        )
        settings.historyIndex = 1
    }

    private fun endpoint() = PushEndpoint(
        "https://push.example.com/new",
        PublicKeySet("new-public-key", "new-auth"),
        true,
    )

    private fun send(
        payload: String,
        decrypted: Boolean = true,
        instance: String = currentInstance,
    ) = UnifiedPushManager.handleMessage(
        context,
        PushMessage(payload.toByteArray(Charsets.UTF_8), decrypted),
        instance,
    )

    private fun commandPayload(
        instances: List<String>? = null,
        usernames: List<String>? = null,
    ) = buildJsonObject {
        put("type", "command")
        put("command", "clear_history")
        put("messageId", "command-message")
        put("interact", false)
        if (instances != null) putJsonArray("targetInstances") { instances.forEach { add(it) } }
        if (usernames != null) putJsonArray("targetUsernames") { usernames.forEach { add(it) } }
    }.toString()

    private fun settingsPayload(
        instances: List<String>? = null,
        usernames: List<String>? = null,
        home: String = updatedHome,
    ) = buildJsonObject {
        put("type", "settings")
        put("messageId", "settings-message")
        put("reloadActivity", false)
        put("showToast", false)
        if (instances != null) putJsonArray("targetInstances") { instances.forEach { add(it) } }
        if (usernames != null) putJsonArray("targetUsernames") { usernames.forEach { add(it) } }
        putJsonObject("data") {
            putJsonObject("settings") { put(UserSettingsKeys.WebContent.HOME_URL, home) }
        }
    }.toString()

    private fun dispatchCommands(deliver: () -> Unit): List<RemoteMessage<InboundCommandMessage>> =
        runBlocking {
            val marker = "drain-" + UUID.randomUUID()
            val observed = mutableListOf<RemoteMessage<InboundCommandMessage>>()
            val collector = launch(Dispatchers.Unconfined) {
                RemoteMessageManager.commandsFlow.takeWhile {
                    it.message.messageId != marker
                }.collect { observed.add(it) }
            }
            try {
                deliver()
                // This FIFO marker completes after all preceding native and UI dispatch.
                RemoteMessageManager.emitCommand(
                    InboundGoBackCommand(messageId = marker, interact = false),
                    Source.UNIFIEDPUSH,
                )
                awaitDispatch(collector)
                observed.toList()
            } finally {
                collector.cancel()
            }
        }

    private suspend fun awaitDispatch(collector: Job) {
        withTimeout(5_000) {
            while (!collector.isCompleted) {
                shadowOf(Looper.getMainLooper()).idle()
                delay(1)
            }
            collector.join()
        }
    }
}
