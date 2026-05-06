package net.rodakot.posthub

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.rodakot.posthub.ui.theme.PostHUBTheme
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.ProtocolException
import java.net.URL
import java.nio.charset.Charset
import java.util.Locale

private val HttpMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")
private val RequestTabs = listOf("Params", "Headers", "Auth", "Body", "Save")
private val ResponseTabs = listOf("Body", "Headers")
private val AuthTypes = listOf("None", "Bearer", "Basic")
private val VariablePattern = Regex("""\{\{\s*([A-Za-z0-9_.-]+)\s*\}\}""")
private const val PrefName = "posthub_state"
private const val EnvironmentKey = "environment_variables"
private const val FlowKey = "request_flow"
private const val WidgetConfigKey = "widget_config"

private object AppColors {
    private val darkMode = mutableStateOf(false)

    fun useDarkTheme(enabled: Boolean) {
        if (darkMode.value != enabled) {
            darkMode.value = enabled
        }
    }

    private val isDark: Boolean get() = darkMode.value

    val Canvas: Color get() = if (isDark) Color(0xFF0E1513) else Color(0xFFF5F7F4)
    val Panel: Color get() = if (isDark) Color(0xFF151D1A) else Color(0xFFFFFFFF)
    val PanelSoft: Color get() = if (isDark) Color(0xFF1C2824) else Color(0xFFF0F4F1)
    val Ink: Color get() = if (isDark) Color(0xFFEAF3EF) else Color(0xFF17201C)
    val Muted: Color get() = if (isDark) Color(0xFFA2B2AB) else Color(0xFF66736D)
    val Border: Color get() = if (isDark) Color(0xFF2E3B36) else Color(0xFFDDE4DF)
    val Primary: Color get() = if (isDark) Color(0xFF5FE0C5) else Color(0xFF006B5B)
    val OnPrimary: Color get() = if (isDark) Color(0xFF05201B) else Color.White
    val PrimarySoft: Color get() = if (isDark) Color(0xFF14382F) else Color(0xFFE3F4EF)
    val Coral: Color get() = if (isDark) Color(0xFFFF9A83) else Color(0xFFD84B35)
    val Amber: Color get() = if (isDark) Color(0xFFE8B660) else Color(0xFFC47A12)
    val Green: Color get() = if (isDark) Color(0xFF68D8A7) else Color(0xFF147A56)
    val Red: Color get() = if (isDark) Color(0xFFFF8A8F) else Color(0xFFC7383D)
    val Code: Color get() = if (isDark) Color(0xFF07100D) else Color(0xFF101715)
    val CodeText: Color get() = Color(0xFFEAF3EF)
}

private data class RequestField(
    val id: Int,
    val key: String,
    val value: String,
    val enabled: Boolean = true
)

private data class AuthState(
    val type: String = "None",
    val bearerToken: String = "",
    val username: String = "",
    val password: String = ""
)

private data class RequestSnapshot(
    val method: String,
    val url: String,
    val params: List<RequestField>,
    val headers: List<RequestField>,
    val auth: AuthState,
    val body: String,
    val captures: List<CaptureField> = emptyList()
)

private data class CaptureField(
    val id: Int,
    val variableName: String,
    val source: String,
    val enabled: Boolean = true
)

private data class ResponseHeader(
    val name: String,
    val value: String
)

private data class RequestResult(
    val statusCode: Int?,
    val statusText: String,
    val durationMs: Long,
    val byteCount: Int,
    val finalUrl: String,
    val body: String,
    val rawBody: String,
    val bodyBytes: ByteArray = ByteArray(0),
    val headers: List<ResponseHeader>,
    val error: String? = null
)

private data class HistoryItem(
    val id: Long,
    val method: String,
    val url: String,
    val statusCode: Int?,
    val durationMs: Long,
    val bodySize: Int
)

private data class SavedRequest(
    val name: String,
    val method: String,
    val url: String,
    val params: List<RequestField> = defaultParamRows(),
    val body: String = "",
    val headers: List<RequestField> = defaultHeaderRows(),
    val auth: AuthState = AuthState(),
    val captures: List<CaptureField> = emptyList()
)

private data class FlowStep(
    val id: Long,
    val request: SavedRequest
)

private data class ClientAction(
    val title: String,
    val subtitle: String,
    val color: Color,
    val onClick: () -> Unit
)

private data class AppSection(
    val key: String,
    val title: String,
    val meta: String,
    val color: Color
)

private data class FlowRunEntry(
    val stepId: Long,
    val status: String,
    val statusCode: Int? = null,
    val durationMs: Long? = null,
    val savedVariables: Int = 0
)

private data class WidgetConfig(
    val titleTemplate: String = "PostHUB Flow",
    val subtitleTemplate: String = "{{flow_steps}} step flow",
    val statusTemplate: String = "{{status_code}}",
    val metaTemplate: String = "{{duration_ms}} ms  {{bytes}}",
    val bodyTemplate: String = "{{summary}}"
)

private fun appSections() = listOf(
    AppSection("request", "Request", "Composer", AppColors.Primary),
    AppSection("clients", "Clients", "MCI TCI", AppColors.Coral),
    AppSection("flow", "Flow", "Sequence", AppColors.Amber),
    AppSection("environment", "Env", "Variables", AppColors.Green),
    AppSection("history", "History", "Recent", AppColors.Muted)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_PostHUB)
        super.onCreate(savedInstanceState)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_LTR
        window.decorView.textDirection = View.TEXT_DIRECTION_LTR
        enableEdgeToEdge()
        setContent {
            val darkTheme = isSystemInDarkTheme()
            AppColors.useDarkTheme(darkTheme)
            PostHUBTheme(darkTheme = darkTheme, dynamicColor = false) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    ApiClientApp()
                }
            }
        }
    }
}

@Composable
private fun ApiClientApp() {
    val appContext = LocalContext.current.applicationContext
    val builtInRequests = remember { savedRequests() }
    var method by rememberSaveable { mutableStateOf("GET") }
    var url by rememberSaveable { mutableStateOf("https://jsonplaceholder.typicode.com/posts/1") }
    var activeSection by rememberSaveable { mutableStateOf("request") }
    var activeRequestTab by rememberSaveable { mutableStateOf("Params") }
    var activeResponseTab by rememberSaveable { mutableStateOf("Body") }
    var body by rememberSaveable {
        mutableStateOf(
            "{\n" +
                "  \"title\": \"PostHUB\",\n" +
                "  \"body\": \"A polished request from Android\",\n" +
                "  \"userId\": 1\n" +
                "}"
        )
    }
    var params by remember { mutableStateOf(defaultParamRows()) }
    var headers by remember { mutableStateOf(defaultHeaderRows()) }
    var auth by remember { mutableStateOf(AuthState()) }
    var captures by remember { mutableStateOf(defaultCaptureRows()) }
    var variables by remember { mutableStateOf(loadEnvironmentVariables(appContext)) }
    var flowSteps by remember {
        mutableStateOf(
            loadFlowSteps(appContext).ifEmpty { defaultFlowSteps(builtInRequests) }
        )
    }
    var widgetConfig by remember { mutableStateOf(loadWidgetConfig(appContext)) }
    var flowRun by remember { mutableStateOf<List<FlowRunEntry>>(emptyList()) }
    var response by remember { mutableStateOf<RequestResult?>(null) }
    var history by remember { mutableStateOf<List<HistoryItem>>(emptyList()) }
    var isSending by remember { mutableStateOf(false) }
    var isFlowRunning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun updateVariables(next: List<RequestField>) {
        variables = next
        saveEnvironmentVariables(appContext, next)
    }

    fun updateFlowSteps(next: List<FlowStep>) {
        flowSteps = next
        saveFlowSteps(appContext, next)
    }

    fun updateWidgetConfig(next: WidgetConfig) {
        widgetConfig = next
        saveWidgetConfig(appContext, next)
    }

    fun currentRequest(): SavedRequest {
        return SavedRequest(
            name = requestName(method, url),
            method = method,
            url = url,
            params = params,
            body = body,
            headers = headers,
            auth = auth,
            captures = captures
        )
    }

    fun addHistory(result: RequestResult, snapshot: RequestSnapshot) {
        history = listOf(
            HistoryItem(
                id = System.currentTimeMillis(),
                method = snapshot.method,
                url = result.finalUrl.ifBlank { snapshot.url },
                statusCode = result.statusCode,
                durationMs = result.durationMs,
                bodySize = result.byteCount
            )
        ) + history.take(11)
    }

    fun saveCaptures(result: RequestResult, captureRows: List<CaptureField>, currentVariables: List<RequestField>): Int {
        val capturedValues = runCatching {
            captureValues(result, captureRows)
        }.getOrDefault(emptyMap())
        if (capturedValues.isEmpty()) return 0

        updateVariables(mergeCapturedVariables(currentVariables, capturedValues))
        return capturedValues.size
    }

    fun loadSavedRequest(savedRequest: SavedRequest) {
        method = savedRequest.method
        url = savedRequest.url
        params = savedRequest.params
        body = savedRequest.body
        headers = savedRequest.headers
        auth = savedRequest.auth
        captures = savedRequest.captures.ifEmpty { defaultCaptureRows() }
        activeRequestTab = if (savedRequest.body.isBlank()) "Params" else "Body"
        activeSection = "request"
    }

    fun sendRequest() {
        if (isSending || isFlowRunning || url.isBlank()) return

        val snapshot = RequestSnapshot(
            method = method,
            url = url,
            params = params,
            headers = headers,
            auth = auth,
            body = body,
            captures = captures
        ).resolveVariables(variables.toEnvironmentMap())

        isSending = true
        response = null
        scope.launch {
            try {
                val result = executeRequest(snapshot)
                response = result
                addHistory(result, snapshot)
                saveCaptures(result, captures, variables)
                activeResponseTab = if (result.error == null) "Body" else "Headers"
            } catch (error: Exception) {
                val result = errorResult(error, snapshot.url)
                response = result
                addHistory(result, snapshot)
                activeResponseTab = "Headers"
            } finally {
                isSending = false
            }
        }
    }

    fun addCurrentRequestToFlow() {
        val next = flowSteps + FlowStep(
            id = System.currentTimeMillis(),
            request = currentRequest()
        )
        updateFlowSteps(next)
        activeSection = "flow"
    }

    fun addRequestsToFlow(requestNames: List<String>) {
        val requests = requestNames.mapNotNull { name ->
            builtInRequests.firstOrNull { it.name == name }
        }
        if (requests.isEmpty()) return

        val startId = System.currentTimeMillis()
        val next = flowSteps + requests.mapIndexed { index, request ->
            FlowStep(id = startId + index, request = request)
        }
        updateFlowSteps(next)
        activeSection = "flow"
    }

    fun runFlow() {
        if (isSending || isFlowRunning || flowSteps.isEmpty()) return

        val steps = flowSteps
        isFlowRunning = true
        response = null
        flowRun = steps.map { FlowRunEntry(stepId = it.id, status = "Pending") }

        scope.launch {
            try {
                var environmentRows = variables

                for (step in steps) {
                    flowRun = flowRun.updateFlowEntry(step.id) { copy(status = "Running") }

                    val snapshot = step.request
                        .toSnapshot()
                        .resolveVariables(environmentRows.toEnvironmentMap())
                    val result = runCatching {
                        executeRequest(snapshot)
                    }.getOrElse { error ->
                        errorResult(error, snapshot.url)
                    }
                    response = result
                    activeResponseTab = if (result.error == null) "Body" else "Headers"
                    addHistory(result, snapshot)

                    val savedCount = if (result.error == null) {
                        val captured = runCatching {
                            captureValues(result, step.request.captures)
                        }.getOrDefault(emptyMap())
                        if (captured.isNotEmpty()) {
                            environmentRows = mergeCapturedVariables(environmentRows, captured)
                            updateVariables(environmentRows)
                        }
                        captured.size
                    } else {
                        0
                    }

                    flowRun = flowRun.updateFlowEntry(step.id) {
                        copy(
                            status = if (result.error == null) "Done" else "Failed",
                            statusCode = result.statusCode,
                            durationMs = result.durationMs,
                            savedVariables = savedCount
                        )
                    }

                    if (result.error != null) {
                        val failedIndex = steps.indexOfFirst { it.id == step.id }
                        val skippedIds = steps.drop(failedIndex + 1).map { it.id }.toSet()
                        flowRun = flowRun.map {
                            if (it.stepId in skippedIds) it.copy(status = "Skipped") else it
                        }
                        break
                    }
                }
            } catch (error: Exception) {
                val activeStep = flowRun.firstOrNull { it.status == "Running" }
                    ?: flowRun.firstOrNull { it.status == "Pending" }
                val result = errorResult(error, "")
                response = result
                activeResponseTab = "Headers"
                if (activeStep != null) {
                    flowRun = flowRun.updateFlowEntry(activeStep.stepId) {
                        copy(status = "Failed", durationMs = result.durationMs)
                    }
                }
            } finally {
                isFlowRunning = false
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = AppColors.Canvas
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(AppColors.Canvas),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                AppHomeHeader(
                    activeSection = activeSection,
                    isBusy = isSending || isFlowRunning,
                    response = response,
                    flowSteps = flowSteps.size,
                    variables = variables.countEnabled(),
                    onSectionChange = { activeSection = it }
                )
            }

            when (activeSection) {
                "clients" -> {
                    item {
                        ClientPanel(
                            onLoad = { name ->
                                builtInRequests.firstOrNull { it.name == name }?.let(::loadSavedRequest)
                            },
                            onAddMciFlow = {
                                addRequestsToFlow(listOf("MCI Login", "MCI Packages"))
                            },
                            onAddMciRefreshFlow = {
                                addRequestsToFlow(listOf("MCI Refresh token", "MCI Packages"))
                            },
                    onAddTciFlow = {
                        addRequestsToFlow(listOf("TCI Open panel", "TCI Login", "TCI Panel traffic"))
                    },
                    onAddIpFlow = {
                        addRequestsToFlow(listOf("Find public IP", "IP information"))
                    }
                )
            }

                    item {
                        CollectionPanel(
                            savedRequests = builtInRequests,
                            onLoad = ::loadSavedRequest
                        )
                    }
                }

                "flow" -> {
                    item {
                        FlowPanel(
                            steps = flowSteps,
                            runEntries = flowRun,
                            isRunning = isFlowRunning,
                            widgetConfig = widgetConfig,
                            onAddCurrent = ::addCurrentRequestToFlow,
                            onRun = ::runFlow,
                            onWidgetConfigChange = ::updateWidgetConfig,
                            onLoad = { loadSavedRequest(it.request) },
                            onRemove = { step ->
                                updateFlowSteps(flowSteps.filterNot { it.id == step.id })
                            }
                        )
                    }

                    item {
                        ResponseWorkspace(
                            result = response,
                            isSending = isSending || isFlowRunning,
                            activeTab = activeResponseTab,
                            onTabChange = { activeResponseTab = it }
                        )
                    }
                }

                "environment" -> {
                    item {
                        EnvironmentPanel(
                            variables = variables,
                            onVariablesChange = ::updateVariables
                        )
                    }
                }

                "history" -> {
                    item {
                        HistoryPanel(
                            history = history,
                            onSelect = {
                                method = it.method
                                url = it.url
                                activeRequestTab = "Params"
                                activeSection = "request"
                            }
                        )
                    }
                }

                else -> {
                    item {
                        ProductHeader(
                            method = method,
                            url = url,
                            isSending = isSending || isFlowRunning,
                            onMethodChange = { method = it },
                            onUrlChange = { url = it },
                            onSend = ::sendRequest
                        )
                    }

                    item {
                        RequestWorkspace(
                            activeTab = activeRequestTab,
                            onTabChange = { activeRequestTab = it },
                            params = params,
                            onParamsChange = { params = it },
                            headers = headers,
                            onHeadersChange = { headers = it },
                            auth = auth,
                            onAuthChange = { auth = it },
                            body = body,
                            onBodyChange = { body = it },
                            captures = captures,
                            onCapturesChange = { captures = it },
                            method = method
                        )
                    }

                    item {
                        ResponseWorkspace(
                            result = response,
                            isSending = isSending || isFlowRunning,
                            activeTab = activeResponseTab,
                            onTabChange = { activeResponseTab = it }
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }
}

@Composable
private fun AppHomeHeader(
    activeSection: String,
    isBusy: Boolean,
    response: RequestResult?,
    flowSteps: Int,
    variables: Int,
    onSectionChange: (String) -> Unit
) {
    val sections = appSections()

    PanelSurface {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AppColors.Primary,
                        contentColor = AppColors.OnPrimary
                    ) {
                        Text(
                            text = "PH",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Column {
                        Text(
                            text = "PostHUB",
                            color = AppColors.Ink,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = sections.firstOrNull { it.key == activeSection }?.meta ?: "Request client",
                            color = AppColors.Muted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                StatusPill(
                    text = when {
                        isBusy -> "Working"
                        response?.statusCode != null -> response.statusCode.toString()
                        else -> "Ready"
                    },
                    color = when {
                        isBusy -> AppColors.Amber
                        response?.statusCode != null -> statusColor(response.statusCode)
                        else -> AppColors.Green
                    }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sections.forEach { section ->
                    AppSectionChip(
                        section = section,
                        selected = activeSection == section.key,
                        count = when (section.key) {
                            "flow" -> flowSteps.takeIf { it > 0 }
                            "environment" -> variables.takeIf { it > 0 }
                            else -> null
                        },
                        onClick = { onSectionChange(section.key) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppSectionChip(
    section: AppSection,
    selected: Boolean,
    count: Int?,
    onClick: () -> Unit
) {
    val color = if (selected) section.color else AppColors.Muted
    Surface(
        modifier = Modifier
            .widthIn(min = 104.dp)
            .height(52.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) section.color.copy(alpha = 0.12f) else AppColors.PanelSoft,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) section.color.copy(alpha = 0.32f) else AppColors.Border
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = section.title,
                    color = if (selected) section.color else AppColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = count?.toString() ?: section.meta,
                    color = AppColors.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun ProductHeader(
    method: String,
    url: String,
    isSending: Boolean,
    onMethodChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onSend: () -> Unit
) {
    PanelSurface {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionTitle(
                    title = "Request",
                    meta = url.hostFromUrl().ifBlank { method }
                )

                StatusPill(
                    text = if (isSending) "Sending" else "Ready",
                    color = if (isSending) AppColors.Amber else AppColors.Green
                )
            }

            RequestComposer(
                method = method,
                url = url,
                isSending = isSending,
                onMethodChange = onMethodChange,
                onUrlChange = onUrlChange,
                onSend = onSend
            )
        }
    }
}

@Composable
private fun RequestComposer(
    method: String,
    url: String,
    isSending: Boolean,
    onMethodChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onSend: () -> Unit
) {
    BoxWithConstraints {
        val compact = maxWidth < 620.dp
        if (compact) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MethodDropdown(
                        method = method,
                        onMethodChange = onMethodChange,
                        modifier = Modifier.width(126.dp)
                    )
                    SendButton(
                        isSending = isSending,
                        enabled = url.isNotBlank(),
                        onSend = onSend,
                        modifier = Modifier.weight(1f)
                    )
                }
                UrlField(
                    url = url,
                    onUrlChange = onUrlChange,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MethodDropdown(
                    method = method,
                    onMethodChange = onMethodChange,
                    modifier = Modifier.width(128.dp)
                )
                UrlField(
                    url = url,
                    onUrlChange = onUrlChange,
                    modifier = Modifier.weight(1f)
                )
                SendButton(
                    isSending = isSending,
                    enabled = url.isNotBlank(),
                    onSend = onSend,
                    modifier = Modifier.width(132.dp)
                )
            }
        }
    }
}

@Composable
private fun MethodDropdown(
    method: String,
    onMethodChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clickable { expanded = true },
            shape = RoundedCornerShape(8.dp),
            color = methodColor(method).copy(alpha = 0.12f),
            border = BorderStroke(1.dp, methodColor(method).copy(alpha = 0.28f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = method,
                    color = methodColor(method),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "v",
                    color = methodColor(method),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            HttpMethods.forEach { item ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = item,
                            color = methodColor(item),
                            fontWeight = FontWeight.Bold
                        )
                    },
                    onClick = {
                        onMethodChange(item)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun UrlField(
    url: String,
    onUrlChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = url,
        onValueChange = onUrlChange,
        modifier = modifier.height(56.dp),
        singleLine = true,
        shape = RoundedCornerShape(8.dp),
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        placeholder = {
            Text(
                text = "https://api.example.com/resource",
                color = AppColors.Muted
            )
        }
    )
}

@Composable
private fun SendButton(
    isSending: Boolean,
    enabled: Boolean,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onSend,
        enabled = enabled && !isSending,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AppColors.Primary,
            contentColor = AppColors.OnPrimary,
            disabledContainerColor = AppColors.Border,
            disabledContentColor = AppColors.Muted
        )
    ) {
        if (isSending) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = AppColors.OnPrimary
            )
            Spacer(Modifier.width(8.dp))
            Text("Sending")
        } else {
            Text("Send", fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun EnvironmentPanel(
    variables: List<RequestField>,
    onVariablesChange: (List<RequestField>) -> Unit
) {
    PanelSurface {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionTitle(title = "Environment", meta = "Persistent variables")
                CountPill("Active", variables.countEnabled())
            }

            KeyValueEditor(
                rows = variables,
                onRowsChange = onVariablesChange,
                firstPlaceholder = "token",
                secondPlaceholder = "value"
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClientPanel(
    onLoad: (String) -> Unit,
    onAddMciFlow: () -> Unit,
    onAddMciRefreshFlow: () -> Unit,
    onAddTciFlow: () -> Unit,
    onAddIpFlow: () -> Unit
) {
    val mciActions = listOf(
        ClientAction("Login", "MCI password auth", AppColors.Primary) { onLoad("MCI Login") },
        ClientAction("Refresh", "MCI token refresh", AppColors.Amber) { onLoad("MCI Refresh token") },
        ClientAction("Packages", "MCI package details", AppColors.Green) { onLoad("MCI Packages") },
        ClientAction("Login flow", "Auth then packages", AppColors.Coral, onAddMciFlow),
        ClientAction("Refresh flow", "Refresh then packages", AppColors.Muted, onAddMciRefreshFlow)
    )
    val tciActions = listOf(
        ClientAction("Open panel", "TCI session page", AppColors.Primary) { onLoad("TCI Open panel") },
        ClientAction("Captcha", "TCI captcha image", AppColors.Amber) { onLoad("TCI Captcha image") },
        ClientAction("Login", "TCI captcha login", AppColors.Coral) { onLoad("TCI Login") },
        ClientAction("Traffic", "TCI panel traffic", AppColors.Green) { onLoad("TCI Panel traffic") },
        ClientAction("Panel flow", "Open login traffic", AppColors.Muted, onAddTciFlow)
    )
    val ipActions = listOf(
        ClientAction("Find IP", "Public IP address", AppColors.Primary) { onLoad("Find public IP") },
        ClientAction("Details", "Location and ASN", AppColors.Green) { onLoad("IP information") },
        ClientAction("IP flow", "Find then inspect", AppColors.Amber, onAddIpFlow)
    )

    PanelSurface {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionTitle(title = "Clients", meta = "MCI and TCI")
                CountPill("Ready", mciActions.size + tciActions.size + ipActions.size)
            }

            ClientActionGroup(
                title = "MCI",
                badge = "Token API",
                color = AppColors.Primary,
                actions = mciActions
            )

            HorizontalDivider(color = AppColors.Border)

            ClientActionGroup(
                title = "TCI",
                badge = "Cookie web",
                color = AppColors.Coral,
                actions = tciActions
            )

            HorizontalDivider(color = AppColors.Border)

            ClientActionGroup(
                title = "IP",
                badge = "Public lookup",
                color = AppColors.Green,
                actions = ipActions
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClientActionGroup(
    title: String,
    badge: String,
    color: Color,
    actions: List<ClientAction>
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Text(
                    text = title,
                    color = AppColors.Ink,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Black
                )
            }
            StatusPill(text = badge, color = color)
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            actions.forEach { action ->
                ClientActionTile(action = action)
            }
        }
    }
}

@Composable
private fun ClientActionTile(action: ClientAction) {
    Surface(
        modifier = Modifier
            .widthIn(min = 134.dp, max = 190.dp)
            .height(78.dp)
            .clickable { action.onClick() },
        shape = RoundedCornerShape(8.dp),
        color = action.color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, action.color.copy(alpha = 0.24f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = action.title,
                color = action.color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Black
            )
            Text(
                text = action.subtitle,
                color = AppColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowPanel(
    steps: List<FlowStep>,
    runEntries: List<FlowRunEntry>,
    isRunning: Boolean,
    widgetConfig: WidgetConfig,
    onAddCurrent: () -> Unit,
    onRun: () -> Unit,
    onWidgetConfigChange: (WidgetConfig) -> Unit,
    onLoad: (FlowStep) -> Unit,
    onRemove: (FlowStep) -> Unit
) {
    PanelSurface {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionTitle(title = "Flow", meta = "${steps.size} steps")
                StatusPill(
                    text = if (isRunning) "Running" else "Ready",
                    color = if (isRunning) AppColors.Amber else AppColors.Green
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRun,
                    enabled = steps.isNotEmpty() && !isRunning,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.Primary,
                        contentColor = AppColors.OnPrimary,
                        disabledContainerColor = AppColors.Border,
                        disabledContentColor = AppColors.Muted
                    )
                ) {
                    if (isRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = AppColors.OnPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (isRunning) "Running" else "Run flow", fontWeight = FontWeight.Bold)
                }

                TextButton(
                    onClick = onAddCurrent,
                    enabled = !isRunning
                ) {
                    Text("Add current", fontWeight = FontWeight.Bold)
                }
            }

            if (steps.isEmpty()) {
                EmptyState(title = "No flow steps", value = "Flow list is empty")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    steps.forEachIndexed { index, step ->
                        val entry = runEntries.firstOrNull { it.stepId == step.id }
                        FlowStepRow(
                            index = index + 1,
                            step = step,
                            runEntry = entry,
                            isRunning = isRunning,
                            onLoad = { onLoad(step) },
                            onRemove = { onRemove(step) }
                        )
                    }
                }
            }

            WidgetTextEditor(
                config = widgetConfig,
                onConfigChange = onWidgetConfigChange
            )
        }
    }
}

@Composable
private fun WidgetTextEditor(
    config: WidgetConfig,
    onConfigChange: (WidgetConfig) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = AppColors.PanelSoft,
        border = BorderStroke(1.dp, AppColors.Border)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionTitle(title = "Widget text", meta = "Templates")
                TextButton(onClick = { onConfigChange(WidgetConfig()) }) {
                    Text("Reset", fontWeight = FontWeight.Bold)
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusPill("{{summary}}", AppColors.Primary)
                StatusPill("{{status_code}}", AppColors.Green)
                StatusPill("{{duration_ms}}", AppColors.Amber)
                StatusPill("{{public_ip}}", AppColors.Coral)
            }

            WidgetTemplateField(
                label = "Title",
                value = config.titleTemplate,
                placeholder = "PostHUB Flow",
                onValueChange = { onConfigChange(config.copy(titleTemplate = it)) }
            )
            WidgetTemplateField(
                label = "Subtitle",
                value = config.subtitleTemplate,
                placeholder = "{{flow_steps}} step flow",
                onValueChange = { onConfigChange(config.copy(subtitleTemplate = it)) }
            )
            WidgetTemplateField(
                label = "Status",
                value = config.statusTemplate,
                placeholder = "{{status_code}}",
                onValueChange = { onConfigChange(config.copy(statusTemplate = it)) }
            )
            WidgetTemplateField(
                label = "Meta",
                value = config.metaTemplate,
                placeholder = "{{duration_ms}} ms  {{bytes}}",
                onValueChange = { onConfigChange(config.copy(metaTemplate = it)) }
            )
            WidgetTemplateField(
                label = "Body",
                value = config.bodyTemplate,
                placeholder = "{{summary}}",
                minLines = 2,
                onValueChange = { onConfigChange(config.copy(bodyTemplate = it)) }
            )
        }
    }
}

@Composable
private fun WidgetTemplateField(
    label: String,
    value: String,
    placeholder: String,
    minLines: Int = 1,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        minLines = minLines,
        maxLines = if (minLines > 1) 3 else 1,
        singleLine = minLines == 1,
        shape = RoundedCornerShape(8.dp),
        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        label = { Text(label) },
        placeholder = {
            Text(
                text = placeholder,
                color = AppColors.Muted,
                maxLines = 1
            )
        }
    )
}

@Composable
private fun FlowStepRow(
    index: Int,
    step: FlowStep,
    runEntry: FlowRunEntry?,
    isRunning: Boolean,
    onLoad: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isRunning) { onLoad() },
        shape = RoundedCornerShape(8.dp),
        color = AppColors.PanelSoft,
        border = BorderStroke(1.dp, AppColors.Border)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(30.dp),
                    shape = CircleShape,
                    color = AppColors.Panel,
                    border = BorderStroke(1.dp, AppColors.Border)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = index.toString(),
                            color = AppColors.Ink,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                MethodBadge(method = step.request.method)

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = step.request.name,
                        color = AppColors.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = step.request.url,
                        color = AppColors.Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CountPill("Save", step.request.captures.countEnabledCaptures())
                    runEntry?.let {
                        StatusPill(
                            text = it.statusCode?.toString() ?: it.status,
                            color = flowStatusColor(it.status, it.statusCode)
                        )
                    }
                    runEntry?.durationMs?.let {
                        StatusPill(text = "${it} ms", color = AppColors.Muted)
                    }
                    if ((runEntry?.savedVariables ?: 0) > 0) {
                        StatusPill(text = "+${runEntry?.savedVariables}", color = AppColors.Primary)
                    }
                }

                TextButton(
                    onClick = onRemove,
                    enabled = !isRunning
                ) {
                    Text("Remove")
                }
            }
        }
    }
}

@Composable
private fun CollectionPanel(
    savedRequests: List<SavedRequest>,
    onLoad: (SavedRequest) -> Unit
) {
    PanelSurface {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(title = "Collection", meta = "${savedRequests.size} requests")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                savedRequests.forEach { request ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLoad(request) },
                        shape = RoundedCornerShape(8.dp),
                        color = AppColors.PanelSoft,
                        border = BorderStroke(1.dp, AppColors.Border)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MethodBadge(method = request.method)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = request.name,
                                    color = AppColors.Ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = request.url,
                                    color = AppColors.Muted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RequestWorkspace(
    activeTab: String,
    onTabChange: (String) -> Unit,
    params: List<RequestField>,
    onParamsChange: (List<RequestField>) -> Unit,
    headers: List<RequestField>,
    onHeadersChange: (List<RequestField>) -> Unit,
    auth: AuthState,
    onAuthChange: (AuthState) -> Unit,
    body: String,
    onBodyChange: (String) -> Unit,
    captures: List<CaptureField>,
    onCapturesChange: (List<CaptureField>) -> Unit,
    method: String
) {
    PanelSurface {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionTitle(title = "Request", meta = method)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CountPill("Params", params.countEnabled())
                    CountPill("Headers", headers.countEnabled() + authHeaderCount(auth))
                    CountPill("Body", body.toByteArray().size)
                    CountPill("Save", captures.countEnabledCaptures())
                }
            }

            SegmentedTabs(
                tabs = RequestTabs,
                activeTab = activeTab,
                onTabChange = onTabChange
            )

            when (activeTab) {
                "Params" -> KeyValueEditor(
                    rows = params,
                    onRowsChange = onParamsChange,
                    firstPlaceholder = "query",
                    secondPlaceholder = "value"
                )

                "Headers" -> KeyValueEditor(
                    rows = headers,
                    onRowsChange = onHeadersChange,
                    firstPlaceholder = "Header",
                    secondPlaceholder = "Value"
                )

                "Auth" -> AuthEditor(
                    auth = auth,
                    onAuthChange = onAuthChange
                )

                "Body" -> BodyEditor(
                    body = body,
                    onBodyChange = onBodyChange,
                    method = method
                )

                else -> CaptureEditor(
                    captures = captures,
                    onCapturesChange = onCapturesChange
                )
            }
        }
    }
}

@Composable
private fun KeyValueEditor(
    rows: List<RequestField>,
    onRowsChange: (List<RequestField>) -> Unit,
    firstPlaceholder: String,
    secondPlaceholder: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            KeyValueRowEditor(
                row = row,
                firstPlaceholder = firstPlaceholder,
                secondPlaceholder = secondPlaceholder,
                onRowChange = { updated ->
                    onRowsChange(rows.map { if (it.id == row.id) updated else it })
                },
                onRemove = {
                    onRowsChange(removeRow(rows, row.id))
                }
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(
                onClick = {
                    onRowsChange(rows + RequestField(nextRowId(rows), "", ""))
                }
            ) {
                Text("Add row", fontWeight = FontWeight.Bold)
            }

            TextButton(
                onClick = {
                    onRowsChange(listOf(RequestField(1, "", "")))
                }
            ) {
                Text("Clear")
            }
        }
    }
}

@Composable
private fun CaptureEditor(
    captures: List<CaptureField>,
    onCapturesChange: (List<CaptureField>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        captures.forEach { capture ->
            CaptureRowEditor(
                capture = capture,
                onCaptureChange = { updated ->
                    onCapturesChange(captures.map { if (it.id == capture.id) updated else it })
                },
                onRemove = {
                    onCapturesChange(removeCapture(captures, capture.id))
                }
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(
                onClick = {
                    onCapturesChange(captures + CaptureField(nextCaptureId(captures), "", ""))
                }
            ) {
                Text("Add rule", fontWeight = FontWeight.Bold)
            }

            TextButton(
                onClick = {
                    onCapturesChange(defaultCaptureRows())
                }
            ) {
                Text("Clear")
            }
        }
    }
}

@Composable
private fun CaptureRowEditor(
    capture: CaptureField,
    onCaptureChange: (CaptureField) -> Unit,
    onRemove: () -> Unit
) {
    BoxWithConstraints {
        val compact = maxWidth < 520.dp
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = AppColors.PanelSoft,
            border = BorderStroke(1.dp, AppColors.Border)
        ) {
            if (compact) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CompactSwitch(
                                checked = capture.enabled,
                                onCheckedChange = { onCaptureChange(capture.copy(enabled = it)) }
                            )
                            Text(
                                text = if (capture.enabled) "Enabled" else "Disabled",
                                color = AppColors.Muted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        TextButton(onClick = onRemove) {
                            Text("Remove")
                        }
                    }
                    CaptureFieldPair(
                        capture = capture,
                        onCaptureChange = onCaptureChange
                    )
                }
            } else {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompactSwitch(
                        checked = capture.enabled,
                        onCheckedChange = { onCaptureChange(capture.copy(enabled = it)) }
                    )
                    CaptureFieldPair(
                        capture = capture,
                        onCaptureChange = onCaptureChange,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onRemove) {
                        Text("Remove")
                    }
                }
            }
        }
    }
}

@Composable
private fun CaptureFieldPair(
    capture: CaptureField,
    onCaptureChange: (CaptureField) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CompactTextField(
            value = capture.variableName,
            onValueChange = { onCaptureChange(capture.copy(variableName = it)) },
            placeholder = "token",
            modifier = Modifier.weight(1f)
        )
        CompactTextField(
            value = capture.source,
            onValueChange = { onCaptureChange(capture.copy(source = it)) },
            placeholder = "\$.access_token",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun KeyValueRowEditor(
    row: RequestField,
    firstPlaceholder: String,
    secondPlaceholder: String,
    onRowChange: (RequestField) -> Unit,
    onRemove: () -> Unit
) {
    BoxWithConstraints {
        val compact = maxWidth < 520.dp
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = AppColors.PanelSoft,
            border = BorderStroke(1.dp, AppColors.Border)
        ) {
            if (compact) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CompactSwitch(
                                checked = row.enabled,
                                onCheckedChange = { onRowChange(row.copy(enabled = it)) }
                            )
                            Text(
                                text = if (row.enabled) "Enabled" else "Disabled",
                                color = AppColors.Muted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        TextButton(onClick = onRemove) {
                            Text("Remove")
                        }
                    }
                    FieldPair(
                        row = row,
                        firstPlaceholder = firstPlaceholder,
                        secondPlaceholder = secondPlaceholder,
                        onRowChange = onRowChange
                    )
                }
            } else {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompactSwitch(
                        checked = row.enabled,
                        onCheckedChange = { onRowChange(row.copy(enabled = it)) }
                    )
                    FieldPair(
                        row = row,
                        firstPlaceholder = firstPlaceholder,
                        secondPlaceholder = secondPlaceholder,
                        onRowChange = onRowChange,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onRemove) {
                        Text("Remove")
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldPair(
    row: RequestField,
    firstPlaceholder: String,
    secondPlaceholder: String,
    onRowChange: (RequestField) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CompactTextField(
            value = row.key,
            onValueChange = { onRowChange(row.copy(key = it)) },
            placeholder = firstPlaceholder,
            modifier = Modifier.weight(1f)
        )
        CompactTextField(
            value = row.value,
            onValueChange = { onRowChange(row.copy(value = it)) },
            placeholder = secondPlaceholder,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.height(52.dp),
        singleLine = true,
        shape = RoundedCornerShape(8.dp),
        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        placeholder = {
            Text(
                text = placeholder,
                color = AppColors.Muted,
                maxLines = 1
            )
        }
    )
}

@Composable
private fun CompactSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.size(width = 46.dp, height = 30.dp),
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = AppColors.Primary,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = AppColors.Muted.copy(alpha = 0.42f)
        )
    )
}

@Composable
private fun AuthEditor(
    auth: AuthState,
    onAuthChange: (AuthState) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SegmentedTabs(
            tabs = AuthTypes,
            activeTab = auth.type,
            onTabChange = { onAuthChange(auth.copy(type = it)) }
        )

        when (auth.type) {
            "Bearer" -> OutlinedTextField(
                value = auth.bearerToken,
                onValueChange = { onAuthChange(auth.copy(bearerToken = it)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                placeholder = { Text("Bearer token", color = AppColors.Muted) }
            )

            "Basic" -> BoxWithConstraints {
                val compact = maxWidth < 520.dp
                if (compact) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        AuthField(
                            value = auth.username,
                            onValueChange = { onAuthChange(auth.copy(username = it)) },
                            placeholder = "Username"
                        )
                        AuthField(
                            value = auth.password,
                            onValueChange = { onAuthChange(auth.copy(password = it)) },
                            placeholder = "Password",
                            password = true
                        )
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AuthField(
                            value = auth.username,
                            onValueChange = { onAuthChange(auth.copy(username = it)) },
                            placeholder = "Username",
                            modifier = Modifier.weight(1f)
                        )
                        AuthField(
                            value = auth.password,
                            onValueChange = { onAuthChange(auth.copy(password = it)) },
                            placeholder = "Password",
                            password = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            else -> EmptyState(
                title = "No authorization",
                value = "Authorization disabled"
            )
        }
    }
}

@Composable
private fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    password: Boolean = false,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(8.dp),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        placeholder = { Text(placeholder, color = AppColors.Muted) }
    )
}

@Composable
private fun BodyEditor(
    body: String,
    onBodyChange: (String) -> Unit,
    method: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Raw body",
                color = AppColors.Ink,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            StatusPill(
                text = "${body.toByteArray().size} B",
                color = if (method.allowsBody()) AppColors.Primary else AppColors.Muted
            )
        }

        OutlinedTextField(
            value = body,
            onValueChange = onBodyChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp),
            shape = RoundedCornerShape(8.dp),
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = AppColors.Ink
            ),
            placeholder = {
                Text(
                    text = "{\n  \"name\": \"PostHUB\"\n}",
                    color = AppColors.Muted,
                    fontFamily = FontFamily.Monospace
                )
            }
        )
    }
}

@Composable
private fun ResponseWorkspace(
    result: RequestResult?,
    isSending: Boolean,
    activeTab: String,
    onTabChange: (String) -> Unit
) {
    PanelSurface {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionTitle(
                    title = "Response",
                    meta = result?.finalUrl?.hostFromUrl().orEmpty()
                )

                when {
                    isSending -> StatusPill("Waiting", AppColors.Amber)
                    result?.error != null -> StatusPill("Error", AppColors.Red)
                    result?.statusCode != null -> StatusPill(
                        text = result.statusCode.toString(),
                        color = statusColor(result.statusCode)
                    )
                    else -> StatusPill("Idle", AppColors.Muted)
                }
            }

            if (isSending) {
                LoadingResponse()
            } else if (result == null) {
                EmptyState(
                    title = "No response",
                    value = "Awaiting response"
                )
            } else {
                ResponseMeta(result)

                if (result.error != null) {
                    ErrorBlock(result.error)
                } else {
                    SegmentedTabs(
                        tabs = ResponseTabs,
                        activeTab = activeTab,
                        onTabChange = onTabChange
                    )

                    if (activeTab == "Headers") {
                        HeaderList(result.headers)
                    } else if (result.isImageResponse()) {
                        ImageBlock(result.bodyBytes)
                    } else {
                        CodeBlock(text = result.body.ifBlank { "<empty response>" })
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingResponse() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        shape = RoundedCornerShape(8.dp),
        color = AppColors.PanelSoft,
        border = BorderStroke(1.dp, AppColors.Border)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(color = AppColors.Primary)
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Connecting",
                color = AppColors.Muted,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ResponseMeta(result: RequestResult) {
    BoxWithConstraints {
        val compact = maxWidth < 560.dp
        val items = listOf(
            "Status" to (result.statusCode?.let { "$it ${result.statusText}" } ?: result.statusText),
            "Time" to "${result.durationMs} ms",
            "Size" to formatBytes(result.byteCount),
            "URL" to result.finalUrl
        )
        if (compact) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEach { (label, value) ->
                    MetaCell(label = label, value = value, modifier = Modifier.fillMaxWidth())
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEach { (label, value) ->
                    MetaCell(label = label, value = value, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MetaCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.heightIn(min = 66.dp),
        shape = RoundedCornerShape(8.dp),
        color = AppColors.PanelSoft,
        border = BorderStroke(1.dp, AppColors.Border)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label.uppercase(Locale.US),
                color = AppColors.Muted,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = value,
                color = AppColors.Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = if (label == "URL") FontFamily.Monospace else FontFamily.Default,
                fontWeight = if (label == "Status") FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun HeaderList(headers: List<ResponseHeader>) {
    if (headers.isEmpty()) {
        EmptyState(title = "No headers", value = "Header list unavailable")
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        headers.forEach { header ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = AppColors.PanelSoft,
                border = BorderStroke(1.dp, AppColors.Border)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = header.name,
                        color = AppColors.Ink,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = header.value,
                        color = AppColors.Muted,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageBlock(bytes: ByteArray) {
    val bitmap = remember(bytes) {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    if (bitmap == null) {
        EmptyState(title = "Image unavailable", value = "Preview unavailable")
        return
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 180.dp, max = 420.dp),
        shape = RoundedCornerShape(8.dp),
        color = AppColors.PanelSoft,
        border = BorderStroke(1.dp, AppColors.Border)
    ) {
        Box(
            modifier = Modifier.padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Response image",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 150.dp, max = 360.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
private fun CodeBlock(text: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 220.dp, max = 420.dp),
        shape = RoundedCornerShape(8.dp),
        color = AppColors.Code
    ) {
        val vertical = rememberScrollState()
        val horizontal = rememberScrollState()
        Box(
            modifier = Modifier
                .verticalScroll(vertical)
                .horizontalScroll(horizontal)
                .padding(14.dp)
        ) {
            SelectionContainer {
                Text(
                    text = text,
                    color = AppColors.CodeText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
private fun ErrorBlock(error: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = AppColors.Red.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, AppColors.Red.copy(alpha = 0.28f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Request failed",
                color = AppColors.Red,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Black
            )
            SelectionContainer {
                Text(
                    text = error,
                    color = AppColors.Ink,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun HistoryPanel(
    history: List<HistoryItem>,
    onSelect: (HistoryItem) -> Unit
) {
    PanelSurface {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(title = "History", meta = "${history.size} recent")

            if (history.isEmpty()) {
                EmptyState(
                    title = "History is empty",
                    value = "No completed requests"
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    history.forEach { item ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(item) },
                            shape = RoundedCornerShape(8.dp),
                            color = AppColors.PanelSoft,
                            border = BorderStroke(1.dp, AppColors.Border)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MethodBadge(method = item.method)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.url,
                                        color = AppColors.Ink,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "${item.durationMs} ms  ${formatBytes(item.bodySize)}",
                                        color = AppColors.Muted,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                item.statusCode?.let { code ->
                                    StatusPill(
                                        text = code.toString(),
                                        color = statusColor(code),
                                        minWidth = 54.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelSurface(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = AppColors.Panel,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, AppColors.Border),
        content = {
            Box(modifier = Modifier.padding(14.dp)) {
                content()
            }
        }
    )
}

@Composable
private fun SectionTitle(
    title: String,
    meta: String = ""
) {
    Column {
        Text(
            text = title,
            color = AppColors.Ink,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black
        )
        if (meta.isNotBlank()) {
            Text(
                text = meta,
                color = AppColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun SegmentedTabs(
    tabs: List<String>,
    activeTab: String,
    onTabChange: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = AppColors.PanelSoft,
        border = BorderStroke(1.dp, AppColors.Border)
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabs.forEach { tab ->
                val selected = tab == activeTab
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onTabChange(tab) },
                    shape = RoundedCornerShape(6.dp),
                    color = if (selected) AppColors.Panel else Color.Transparent,
                    shadowElevation = if (selected) 1.dp else 0.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = tab,
                            color = if (selected) AppColors.Primary else AppColors.Muted,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Black else FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(
    text: String,
    color: Color,
    minWidth: Dp = 0.dp
) {
    Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.22f))
    ) {
        Text(
            text = text,
            modifier = Modifier
                .widthIn(min = minWidth)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
    }
}

@Composable
private fun CountPill(label: String, value: Int) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = AppColors.PanelSoft,
        border = BorderStroke(1.dp, AppColors.Border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = AppColors.Muted,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = value.toString(),
                color = AppColors.Ink,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun MethodBadge(method: String) {
    val color = methodColor(method)
    Surface(
        modifier = Modifier.widthIn(min = 62.dp),
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.22f))
    ) {
        Text(
            text = method,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
    }
}

@Composable
private fun EmptyState(
    title: String,
    value: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = AppColors.PanelSoft,
        border = BorderStroke(1.dp, AppColors.Border)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                color = AppColors.Ink,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = value,
                color = AppColors.Muted,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun RequestSnapshot.resolveVariables(variables: Map<String, String>): RequestSnapshot {
    if (variables.isEmpty()) return this

    return copy(
        url = url.applyVariables(variables),
        params = params.map { it.resolveVariables(variables) },
        headers = headers.map { it.resolveVariables(variables) },
        auth = auth.resolveVariables(variables),
        body = body.applyVariables(variables)
    )
}

private fun RequestResult.isImageResponse(): Boolean {
    return headers.any {
        it.name.equals("Content-Type", ignoreCase = true) &&
            it.value.lowercase(Locale.US).startsWith("image/")
    }
}

private fun RequestField.resolveVariables(variables: Map<String, String>): RequestField {
    return copy(
        key = key.applyVariables(variables),
        value = value.applyVariables(variables)
    )
}

private fun AuthState.resolveVariables(variables: Map<String, String>): AuthState {
    return copy(
        bearerToken = bearerToken.applyVariables(variables),
        username = username.applyVariables(variables),
        password = password.applyVariables(variables)
    )
}

private fun String.applyVariables(variables: Map<String, String>): String {
    var resolved = this
    repeat(3) {
        val next = VariablePattern.replace(resolved) { match ->
            variables[match.groupValues[1]] ?: match.value
        }
        if (next == resolved) return resolved
        resolved = next
    }
    return resolved
}

private fun List<RequestField>.toEnvironmentMap(): Map<String, String> {
    return filter { it.enabled && it.key.isNotBlank() }
        .associate { it.key.trim() to it.value }
}

private fun captureValues(
    result: RequestResult,
    captures: List<CaptureField>
): Map<String, String> {
    if (result.error != null) return emptyMap()

    return captures
        .filter { it.enabled && it.variableName.isNotBlank() }
        .mapNotNull { capture ->
            val source = capture.source.ifBlank { capture.variableName }.trim()
            extractCaptureValue(result, source)?.let { value ->
                capture.variableName.trim() to value
            }
        }
        .toMap()
}

private fun mergeCapturedVariables(
    variables: List<RequestField>,
    captured: Map<String, String>
): List<RequestField> {
    var next = variables

    captured.forEach { (name, value) ->
        val existingIndex = next.indexOfFirst { it.key == name }
        next = if (existingIndex >= 0) {
            next.mapIndexed { index, field ->
                if (index == existingIndex) {
                    field.copy(value = value, enabled = true)
                } else {
                    field
                }
            }
        } else {
            next + RequestField(nextRowId(next), name, value, enabled = true)
        }
    }

    return next.ifEmpty { defaultEnvironmentVariables() }
}

private fun mergeDefaultEnvironmentVariables(variables: List<RequestField>): List<RequestField> {
    var next = variables
    defaultEnvironmentVariables().forEach { defaultRow ->
        val exists = next.any { it.key == defaultRow.key }
        if (!exists) {
            next = next + defaultRow.copy(id = nextRowId(next))
        }
    }
    return next
}

private fun extractCaptureValue(
    result: RequestResult,
    source: String
): String? {
    val expression = source.trim()
    if (expression.isBlank()) return null

    if (expression.equals("cookie", ignoreCase = true) || expression.equals("cookies", ignoreCase = true)) {
        return responseCookies(result.headers)
    }

    if (expression.startsWith("cookie:", ignoreCase = true)) {
        return responseCookies(
            headers = result.headers,
            cookieName = expression.substringAfter(":").trim().takeIf { it.isNotBlank() }
        )
    }

    if (expression.startsWith("regex:", ignoreCase = true)) {
        return extractRegexValue(
            body = result.rawBody,
            pattern = expression.substringAfter(":")
        )
    }

    val headerName = when {
        expression.startsWith("header:", ignoreCase = true) -> expression.substringAfter(":")
        expression.startsWith("headers.", ignoreCase = true) -> expression.substringAfter(".")
        else -> null
    }?.trim()

    if (!headerName.isNullOrBlank()) {
        return result.headers.firstOrNull { it.name.equals(headerName, ignoreCase = true) }?.value
    }

    return extractJsonValue(result.rawBody, expression)
}

private fun responseCookies(
    headers: List<ResponseHeader>,
    cookieName: String? = null
): String? {
    val cookies = headers
        .filter { it.name.equals("Set-Cookie", ignoreCase = true) }
        .flatMap { it.value.split(Regex("""(?:\n|,\s*(?=[A-Za-z0-9_.-]+=))""")) }
        .mapNotNull { setCookie ->
            val cookiePair = setCookie.substringBefore(";").trim()
            if (!cookiePair.contains("=")) return@mapNotNull null
            if (cookieName == null || cookiePair.substringBefore("=").equals(cookieName, ignoreCase = true)) {
                cookiePair
            } else {
                null
            }
        }

    return cookies.joinToString("; ").takeIf { it.isNotBlank() }
}

private fun extractRegexValue(
    body: String,
    pattern: String
): String? {
    if (pattern.isBlank()) return null

    return runCatching {
        val match = Regex(pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(body)
        match?.groups?.get(1)?.value ?: match?.value
    }.getOrNull()?.trim()?.takeIf { it.isNotBlank() }
}

private fun extractJsonValue(
    body: String,
    path: String
): String? {
    val trimmedBody = body.trim()
    if (trimmedBody.isBlank()) return null

    val root: Any = try {
        when {
            trimmedBody.startsWith("{") -> JSONObject(trimmedBody)
            trimmedBody.startsWith("[") -> JSONArray(trimmedBody)
            else -> return null
        }
    } catch (_: JSONException) {
        return null
    }

    val tokens = jsonPathTokens(path)
    var current: Any? = root

    if (tokens.isEmpty()) return jsonValueToString(current)

    tokens.forEach { token ->
        current = walkJsonToken(current, token)
        if (current == null || current == JSONObject.NULL) return null
    }

    return jsonValueToString(current)
}

private fun jsonPathTokens(path: String): List<String> {
    val clean = path
        .trim()
        .removePrefix("$")
        .trimStart('.')
        .replace("[", ".[")
        .trim('.')

    if (clean.isBlank()) return emptyList()

    return clean.split(".")
        .map { it.trim() }
        .filter { it.isNotBlank() }
}

private fun walkJsonToken(
    current: Any?,
    token: String
): Any? {
    var value = current ?: return null
    val fieldName = token.substringBefore("[")

    if (fieldName.isNotBlank()) {
        value = when (value) {
            is JSONObject -> value.opt(fieldName)
            else -> return null
        }
    }

    Regex("""\[(\d+)]""").findAll(token).forEach { match ->
        val index = match.groupValues[1].toInt()
        value = when (value) {
            is JSONArray -> value.opt(index)
            else -> return null
        }
    }

    return value
}

private fun jsonValueToString(value: Any?): String? {
    return when (value) {
        null, JSONObject.NULL -> null
        is JSONObject -> value.toString()
        is JSONArray -> value.toString()
        else -> value.toString()
    }
}

private fun loadEnvironmentVariables(context: Context): List<RequestField> {
    val raw = context
        .getSharedPreferences(PrefName, Context.MODE_PRIVATE)
        .getString(EnvironmentKey, null)

    if (raw.isNullOrBlank()) return defaultEnvironmentVariables()

    val loaded = runCatching {
        requestFieldsFromJson(JSONArray(raw)).ifEmpty { defaultEnvironmentVariables() }
    }.getOrDefault(defaultEnvironmentVariables())

    return mergeDefaultEnvironmentVariables(loaded)
}

private fun saveEnvironmentVariables(
    context: Context,
    variables: List<RequestField>
) {
    context
        .getSharedPreferences(PrefName, Context.MODE_PRIVATE)
        .edit()
        .putString(EnvironmentKey, requestFieldsToJson(variables).toString())
        .apply()
}

private fun loadFlowSteps(context: Context): List<FlowStep> {
    val raw = context
        .getSharedPreferences(PrefName, Context.MODE_PRIVATE)
        .getString(FlowKey, null)

    if (raw.isNullOrBlank()) return emptyList()

    return runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val request = item.optJSONObject("request") ?: return@mapNotNull null
            FlowStep(
                id = item.optLong("id", System.currentTimeMillis() + index),
                request = savedRequestFromJson(request)
            )
        }
    }.getOrDefault(emptyList())
}

private fun saveFlowSteps(
    context: Context,
    steps: List<FlowStep>
) {
    val array = JSONArray()
    steps.forEach { step ->
        array.put(
            JSONObject()
                .put("id", step.id)
                .put("request", step.request.toJson())
        )
    }

    context
        .getSharedPreferences(PrefName, Context.MODE_PRIVATE)
        .edit()
        .putString(FlowKey, array.toString())
        .apply()
}

private fun loadWidgetConfig(context: Context): WidgetConfig {
    val raw = context
        .getSharedPreferences(PrefName, Context.MODE_PRIVATE)
        .getString(WidgetConfigKey, null)

    if (raw.isNullOrBlank()) return WidgetConfig()

    return runCatching {
        val json = JSONObject(raw)
        WidgetConfig(
            titleTemplate = json.optString("titleTemplate", WidgetConfig().titleTemplate),
            subtitleTemplate = json.optString("subtitleTemplate", WidgetConfig().subtitleTemplate),
            statusTemplate = json.optString("statusTemplate", WidgetConfig().statusTemplate),
            metaTemplate = json.optString("metaTemplate", WidgetConfig().metaTemplate),
            bodyTemplate = json.optString("bodyTemplate", WidgetConfig().bodyTemplate)
        )
    }.getOrDefault(WidgetConfig())
}

private fun saveWidgetConfig(
    context: Context,
    config: WidgetConfig
) {
    val json = JSONObject()
        .put("titleTemplate", config.titleTemplate)
        .put("subtitleTemplate", config.subtitleTemplate)
        .put("statusTemplate", config.statusTemplate)
        .put("metaTemplate", config.metaTemplate)
        .put("bodyTemplate", config.bodyTemplate)

    context
        .getSharedPreferences(PrefName, Context.MODE_PRIVATE)
        .edit()
        .putString(WidgetConfigKey, json.toString())
        .apply()
}

private fun requestFieldsToJson(rows: List<RequestField>): JSONArray {
    val array = JSONArray()
    rows.forEach { row ->
        array.put(
            JSONObject()
                .put("id", row.id)
                .put("key", row.key)
                .put("value", row.value)
                .put("enabled", row.enabled)
        )
    }
    return array
}

private fun requestFieldsFromJson(array: JSONArray): List<RequestField> {
    return (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        RequestField(
            id = item.optInt("id", index + 1),
            key = item.optString("key"),
            value = item.optString("value"),
            enabled = item.optBoolean("enabled", true)
        )
    }
}

private fun captureFieldsToJson(rows: List<CaptureField>): JSONArray {
    val array = JSONArray()
    rows.forEach { row ->
        array.put(
            JSONObject()
                .put("id", row.id)
                .put("variableName", row.variableName)
                .put("source", row.source)
                .put("enabled", row.enabled)
        )
    }
    return array
}

private fun captureFieldsFromJson(array: JSONArray): List<CaptureField> {
    return (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        CaptureField(
            id = item.optInt("id", index + 1),
            variableName = item.optString("variableName"),
            source = item.optString("source"),
            enabled = item.optBoolean("enabled", true)
        )
    }
}

private fun AuthState.toJson(): JSONObject {
    return JSONObject()
        .put("type", type)
        .put("bearerToken", bearerToken)
        .put("username", username)
        .put("password", password)
}

private fun authStateFromJson(json: JSONObject?): AuthState {
    if (json == null) return AuthState()

    return AuthState(
        type = json.optString("type", "None"),
        bearerToken = json.optString("bearerToken"),
        username = json.optString("username"),
        password = json.optString("password")
    )
}

private fun SavedRequest.toJson(): JSONObject {
    return JSONObject()
        .put("name", name)
        .put("method", method)
        .put("url", url)
        .put("params", requestFieldsToJson(params))
        .put("body", body)
        .put("headers", requestFieldsToJson(headers))
        .put("auth", auth.toJson())
        .put("captures", captureFieldsToJson(captures))
}

private fun savedRequestFromJson(json: JSONObject): SavedRequest {
    return SavedRequest(
        name = json.optString("name", "Request"),
        method = json.optString("method", "GET"),
        url = json.optString("url"),
        params = json.optJSONArray("params")?.let(::requestFieldsFromJson) ?: defaultParamRows(),
        body = json.optString("body"),
        headers = json.optJSONArray("headers")?.let(::requestFieldsFromJson) ?: defaultHeaderRows(),
        auth = authStateFromJson(json.optJSONObject("auth")),
        captures = json.optJSONArray("captures")?.let(::captureFieldsFromJson) ?: defaultCaptureRows()
    )
}

private fun SavedRequest.toSnapshot(): RequestSnapshot {
    return RequestSnapshot(
        method = method,
        url = url,
        params = params,
        headers = headers,
        auth = auth,
        body = body,
        captures = captures
    )
}

private fun List<FlowRunEntry>.updateFlowEntry(
    stepId: Long,
    update: FlowRunEntry.() -> FlowRunEntry
): List<FlowRunEntry> {
    return map { entry ->
        if (entry.stepId == stepId) entry.update() else entry
    }
}

private suspend fun executeRequest(snapshot: RequestSnapshot): RequestResult {
    return withContext(Dispatchers.IO) {
        val started = System.nanoTime()
        var connection: HttpURLConnection? = null
        var finalUrl = ""

        try {
            finalUrl = buildUrl(snapshot.url, snapshot.params)
            connection = (URL(finalUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                setRequestMethodCompat(snapshot.method)
                setRequestProperty("User-Agent", "PostHUB Android/1.0")
            }

            snapshot.headers
                .filter { it.enabled && it.key.isNotBlank() }
                .forEach { header ->
                    connection.setRequestProperty(header.key.trim(), header.value)
                }

            authHeader(snapshot.auth)?.let { authorization ->
                val hasAuthorization = snapshot.headers.any {
                    it.enabled && it.key.equals("Authorization", ignoreCase = true)
                }
                if (!hasAuthorization) {
                    connection.setRequestProperty("Authorization", authorization)
                }
            }

            if (snapshot.method.allowsBody() && snapshot.body.isNotBlank()) {
                val requestBytes = snapshot.body.toByteArray(Charsets.UTF_8)
                connection.doOutput = true
                if (connection.getRequestProperty("Content-Type").isNullOrBlank()) {
                    connection.setRequestProperty("Content-Type", "application/json")
                }
                connection.setRequestProperty("Content-Length", requestBytes.size.toString())
                connection.outputStream.use { output ->
                    output.write(requestBytes)
                }
            }

            val statusCode = connection.responseCode
            val statusText = connection.responseMessage.orEmpty()
            val responseBytes = readResponseBytes(connection, statusCode)
            val durationMs = elapsedMs(started)
            val rawBody = responseBytes.toString(Charsets.UTF_8)

            RequestResult(
                statusCode = statusCode,
                statusText = statusText,
                durationMs = durationMs,
                byteCount = responseBytes.size,
                finalUrl = finalUrl,
                body = prettyPrintBody(rawBody),
                rawBody = rawBody,
                bodyBytes = responseBytes,
                headers = connection.headerFields
                    .orEmpty()
                    .filterKeys { it != null }
                    .map {
                        val name = it.key.orEmpty()
                        val separator = if (name.equals("Set-Cookie", ignoreCase = true)) "\n" else "; "
                        ResponseHeader(name, it.value.orEmpty().joinToString(separator))
                    }
                    .sortedBy { it.name.lowercase(Locale.US) }
            )
        } catch (error: Exception) {
            RequestResult(
                statusCode = null,
                statusText = error.javaClass.simpleName,
                durationMs = elapsedMs(started),
                byteCount = 0,
                finalUrl = finalUrl,
                body = "",
                rawBody = "",
                bodyBytes = ByteArray(0),
                headers = emptyList(),
                error = error.cleanMessage()
            )
        } finally {
            connection?.disconnect()
        }
    }
}

private fun errorResult(error: Throwable, finalUrl: String): RequestResult {
    return RequestResult(
        statusCode = null,
        statusText = error.javaClass.simpleName,
        durationMs = 0,
        byteCount = 0,
        finalUrl = finalUrl,
        body = "",
        rawBody = "",
        bodyBytes = ByteArray(0),
        headers = emptyList(),
        error = error.cleanMessage()
    )
}

private fun buildUrl(rawUrl: String, params: List<RequestField>): String {
    val trimmed = rawUrl.trim()
    require(trimmed.isNotBlank()) { "URL is required" }

    val normalized = if (
        trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)
    ) {
        trimmed
    } else {
        "https://$trimmed"
    }

    val builder = Uri.parse(normalized).buildUpon()
    params
        .filter { it.enabled && it.key.isNotBlank() }
        .forEach { param ->
            builder.appendQueryParameter(param.key.trim(), param.value)
        }

    return builder.build().toString()
}

private fun readResponseBytes(connection: HttpURLConnection, statusCode: Int): ByteArray {
    val stream = try {
        if (statusCode >= HttpURLConnection.HTTP_BAD_REQUEST) {
            connection.errorStream ?: connection.inputStream
        } else {
            connection.inputStream
        }
    } catch (_: IOException) {
        connection.errorStream
    }

    return stream?.use { it.readBytes() } ?: ByteArray(0)
}

private fun HttpURLConnection.setRequestMethodCompat(method: String) {
    try {
        requestMethod = method
        return
    } catch (error: ProtocolException) {
        if (method != "PATCH") throw error

        var targetClass: Class<*>? = this.javaClass
        while (targetClass != null) {
            try {
                val field = targetClass.getDeclaredField("method")
                field.isAccessible = true
                field.set(this, method)
                return
            } catch (_: NoSuchFieldException) {
                targetClass = targetClass.superclass
            } catch (_: Exception) {
                throw ProtocolException("PATCH is not supported on this device")
            }
        }

        throw ProtocolException("PATCH is not supported on this device")
    }
}

private fun authHeader(auth: AuthState): String? {
    return when (auth.type) {
        "Bearer" -> auth.bearerToken
            .takeIf { it.isNotBlank() }
            ?.let { "Bearer ${it.trim()}" }

        "Basic" -> {
            if (auth.username.isBlank() && auth.password.isBlank()) return null
            val credentials = "${auth.username}:${auth.password}"
            Base64.encodeToString(credentials.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                .let { "Basic $it" }
        }

        else -> null
    }
}

private fun String.allowsBody(): Boolean {
    return this in listOf("POST", "PUT", "PATCH", "DELETE")
}

private fun prettyPrintBody(body: String): String {
    val trimmed = body.trim()
    if (trimmed.isBlank()) return body

    return try {
        when {
            trimmed.startsWith("{") -> JSONObject(trimmed).toString(2)
            trimmed.startsWith("[") -> JSONArray(trimmed).toString(2)
            else -> body
        }
    } catch (_: JSONException) {
        body
    }
}

private fun Throwable.cleanMessage(): String {
    return message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName
}

private fun elapsedMs(started: Long): Long {
    return (System.nanoTime() - started) / 1_000_000
}

private fun List<RequestField>.countEnabled(): Int {
    return count { it.enabled && it.key.isNotBlank() }
}

private fun List<CaptureField>.countEnabledCaptures(): Int {
    return count { it.enabled && it.variableName.isNotBlank() }
}

private fun authHeaderCount(auth: AuthState): Int {
    return if (authHeader(auth) == null) 0 else 1
}

private fun nextRowId(rows: List<RequestField>): Int {
    return (rows.maxOfOrNull { it.id } ?: 0) + 1
}

private fun removeRow(rows: List<RequestField>, id: Int): List<RequestField> {
    val updated = rows.filterNot { it.id == id }
    return updated.ifEmpty { listOf(RequestField(1, "", "")) }
}

private fun nextCaptureId(rows: List<CaptureField>): Int {
    return (rows.maxOfOrNull { it.id } ?: 0) + 1
}

private fun removeCapture(rows: List<CaptureField>, id: Int): List<CaptureField> {
    val updated = rows.filterNot { it.id == id }
    return updated.ifEmpty { defaultCaptureRows() }
}

private fun defaultParamRows(): List<RequestField> {
    return listOf(
        RequestField(1, "", "", true)
    )
}

private fun defaultEnvironmentVariables(): List<RequestField> {
    return listOf(
        RequestField(1, "base_url", "https://jsonplaceholder.typicode.com", true),
        RequestField(2, "token", "", true),
        RequestField(3, "mci_username", "", true),
        RequestField(4, "mci_password", "", true),
        RequestField(5, "mci_access_token", "", true),
        RequestField(6, "mci_refresh_token", "", true),
        RequestField(7, "mci_session_state", "", true),
        RequestField(8, "mci_total_unused", "", true),
        RequestField(9, "mci_unused_unit", "", true),
        RequestField(10, "tci_username", "", true),
        RequestField(11, "tci_password", "", true),
        RequestField(12, "tci_captcha", "", true),
        RequestField(13, "tci_cookie", "", true),
        RequestField(14, "tci_login_action", "/panel", true),
        RequestField(15, "tci_captcha_path", "", true),
        RequestField(16, "public_ip", "", true),
        RequestField(17, "ip_city", "", true),
        RequestField(18, "ip_region", "", true),
        RequestField(19, "ip_country", "", true),
        RequestField(20, "ip_timezone", "", true),
        RequestField(21, "ip_org", "", true),
        RequestField(22, "ip_asn", "", true)
    )
}

private fun defaultHeaderRows(): List<RequestField> {
    return listOf(
        RequestField(1, "Accept", "application/json", true),
        RequestField(2, "Content-Type", "application/json", false)
    )
}

private fun defaultCaptureRows(): List<CaptureField> {
    return listOf(
        CaptureField(1, "token", "\$.token", false)
    )
}

private fun defaultFlowSteps(requests: List<SavedRequest>): List<FlowStep> {
    return requests
        .filter { it.name in setOf("Capture token", "Use token") }
        .mapIndexed { index, request ->
            FlowStep(id = index + 1L, request = request)
        }
}

private fun requestName(method: String, url: String): String {
    val host = url.hostFromUrl().ifBlank { url.take(28) }
    return "$method $host"
}

private fun mciHeaders(): List<RequestField> {
    return listOf(
        RequestField(1, "User-Agent", "Mozilla/5.0", true),
        RequestField(2, "Accept", "application/json", true),
        RequestField(3, "Content-Type", "application/json", true),
        RequestField(4, "Origin", "https://my.mci.ir", true),
        RequestField(5, "Referer", "https://my.mci.ir/", true),
        RequestField(6, "platform", "WEB", true),
        RequestField(7, "version", "1.29.0", true)
    )
}

private fun mciTokenCaptures(): List<CaptureField> {
    return listOf(
        CaptureField(1, "mci_access_token", "\$.access_token", true),
        CaptureField(2, "mci_refresh_token", "\$.refresh_token", true),
        CaptureField(3, "mci_session_state", "\$.session_state", true),
        CaptureField(4, "mci_access_expires_in", "\$.expires_in", true),
        CaptureField(5, "mci_refresh_expires_in", "\$.refresh_expires_in", true)
    )
}

private fun tciHeaders(): List<RequestField> {
    return listOf(
        RequestField(1, "User-Agent", "Mozilla/5.0", true),
        RequestField(2, "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8", true),
        RequestField(3, "Origin", "https://internet.tci.ir", false),
        RequestField(4, "Referer", "https://internet.tci.ir/panel", false)
    )
}

private fun savedRequests(): List<SavedRequest> {
    return listOf(
        SavedRequest(
            name = "Find public IP",
            method = "GET",
            url = "https://api.ipify.org?format=json",
            headers = listOf(
                RequestField(1, "Accept", "application/json", true),
                RequestField(2, "User-Agent", "PostHUB Android/1.0", true)
            ),
            captures = listOf(
                CaptureField(1, "public_ip", "\$.ip", true)
            )
        ),
        SavedRequest(
            name = "IP information",
            method = "GET",
            url = "https://ipapi.co/{{public_ip}}/json/",
            headers = listOf(
                RequestField(1, "Accept", "application/json", true),
                RequestField(2, "User-Agent", "PostHUB Android/1.0", true)
            ),
            captures = listOf(
                CaptureField(1, "ip_city", "\$.city", true),
                CaptureField(2, "ip_region", "\$.region", true),
                CaptureField(3, "ip_country", "\$.country_name", true),
                CaptureField(4, "ip_timezone", "\$.timezone", true),
                CaptureField(5, "ip_org", "\$.org", true),
                CaptureField(6, "ip_asn", "\$.asn", true)
            )
        ),
        SavedRequest(
            name = "MCI Login",
            method = "POST",
            url = "https://my.mci.ir/api/idm/v1/auth",
            body = "{\n" +
                "  \"username\": \"{{mci_username}}\",\n" +
                "  \"credential\": \"{{mci_password}}\",\n" +
                "  \"credential_type\": \"PASSWORD\"\n" +
                "}",
            headers = mciHeaders(),
            captures = mciTokenCaptures()
        ),
        SavedRequest(
            name = "MCI Refresh token",
            method = "POST",
            url = "https://my.mci.ir/api/idm/v1/auth",
            body = "{\n" +
                "  \"username\": \"{{mci_username}}\",\n" +
                "  \"credential_type\": \"REFRESH_TOKEN\",\n" +
                "  \"credential\": \"{{mci_refresh_token}}\"\n" +
                "}",
            headers = mciHeaders() + RequestField(8, "Authorization", "Bearer {{mci_access_token}}", true),
            captures = mciTokenCaptures()
        ),
        SavedRequest(
            name = "MCI Packages",
            method = "GET",
            url = "https://my.mci.ir/api/unit/v1/packages/details",
            headers = mciHeaders() + RequestField(8, "Authorization", "Bearer {{mci_access_token}}", true),
            captures = listOf(
                CaptureField(1, "mci_total_unused", "\$.totalUnusedBytes", true),
                CaptureField(2, "mci_unused_unit", "\$.bytesUnusedUnit", true),
                CaptureField(3, "mci_first_unused_amount", "\$.packageItems[0].itemDetails[0].unusedAmount", false)
            )
        ),
        SavedRequest(
            name = "TCI Open panel",
            method = "GET",
            url = "https://internet.tci.ir/panel",
            headers = tciHeaders(),
            captures = listOf(
                CaptureField(1, "tci_cookie", "cookies", true),
                CaptureField(2, "tci_login_action", """regex:<form[^>]+action=["']([^"']+)""", true),
                CaptureField(3, "tci_captcha_path", """regex:id=["']loginCaptchaImage["'][^>]+src=["']([^"']+)""", true)
            )
        ),
        SavedRequest(
            name = "TCI Captcha image",
            method = "GET",
            url = "https://internet.tci.ir{{tci_captcha_path}}",
            headers = tciHeaders() + RequestField(8, "Cookie", "{{tci_cookie}}", true)
        ),
        SavedRequest(
            name = "TCI Login",
            method = "POST",
            url = "https://internet.tci.ir{{tci_login_action}}",
            body = "username={{tci_username}}&password={{tci_password}}&captcha={{tci_captcha}}&redirect=&LoginFromWeb=1",
            headers = listOf(
                RequestField(1, "User-Agent", "Mozilla/5.0", true),
                RequestField(2, "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8", true),
                RequestField(3, "Content-Type", "application/x-www-form-urlencoded", true),
                RequestField(4, "Origin", "https://internet.tci.ir", true),
                RequestField(5, "Referer", "https://internet.tci.ir/panel", true),
                RequestField(6, "Cookie", "{{tci_cookie}}", true)
            ),
            captures = listOf(
                CaptureField(1, "tci_cookie", "cookies", true)
            )
        ),
        SavedRequest(
            name = "TCI Panel traffic",
            method = "GET",
            url = "https://internet.tci.ir/panel",
            headers = tciHeaders() + RequestField(8, "Cookie", "{{tci_cookie}}", true),
            captures = listOf(
                CaptureField(1, "tci_cookie", "cookies", true),
                CaptureField(2, "tci_reserved_traffic", """regex:([0-9]+(?:[.,][0-9]+)?\s*(?:GB|MB|گیگابایت|مگابایت))""", false)
            )
        ),
        SavedRequest(
            name = "JSONPlaceholder post",
            method = "GET",
            url = "{{base_url}}/posts/1"
        ),
        SavedRequest(
            name = "Create post",
            method = "POST",
            url = "{{base_url}}/posts",
            body = "{\n" +
                "  \"title\": \"PostHUB\",\n" +
                "  \"body\": \"Created from Android\",\n" +
                "  \"userId\": 1\n" +
                "}",
            headers = listOf(
                RequestField(1, "Accept", "application/json", true),
                RequestField(2, "Content-Type", "application/json", true)
            )
        ),
        SavedRequest(
            name = "Capture token",
            method = "POST",
            url = "https://postman-echo.com/post",
            body = "{\n  \"token\": \"demo-posthub-token\"\n}",
            headers = listOf(
                RequestField(1, "Accept", "application/json", true),
                RequestField(2, "Content-Type", "application/json", true)
            ),
            captures = listOf(
                CaptureField(1, "token", "\$.json.token", true)
            )
        ),
        SavedRequest(
            name = "Use token",
            method = "GET",
            url = "https://postman-echo.com/get",
            headers = listOf(
                RequestField(1, "Accept", "application/json", true),
                RequestField(2, "Authorization", "Bearer {{token}}", true)
            )
        ),
        SavedRequest(
            name = "Echo request",
            method = "POST",
            url = "https://postman-echo.com/post",
            body = "{\n  \"client\": \"PostHUB\"\n}",
            headers = listOf(
                RequestField(1, "Accept", "application/json", true),
                RequestField(2, "Content-Type", "application/json", true)
            )
        )
    )
}

private fun methodColor(method: String): Color {
    return when (method) {
        "GET" -> AppColors.Green
        "POST" -> AppColors.Primary
        "PUT" -> AppColors.Amber
        "PATCH" -> AppColors.Coral
        "DELETE" -> AppColors.Red
        else -> AppColors.Muted
    }
}

private fun statusColor(statusCode: Int): Color {
    return when (statusCode) {
        in 200..299 -> AppColors.Green
        in 300..399 -> AppColors.Amber
        in 400..499 -> AppColors.Coral
        else -> AppColors.Red
    }
}

private fun flowStatusColor(status: String, statusCode: Int?): Color {
    return when {
        status == "Running" -> AppColors.Amber
        status == "Skipped" -> AppColors.Muted
        status == "Failed" -> AppColors.Red
        statusCode != null -> statusColor(statusCode)
        else -> AppColors.Muted
    }
}

private fun formatBytes(bytes: Int): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024f)
        else -> String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f))
    }
}

private fun String.hostFromUrl(): String {
    return runCatching { Uri.parse(this).host.orEmpty() }.getOrDefault("")
}

@Preview(showBackground = true, widthDp = 390, heightDp = 1100)
@Composable
private fun ApiClientPreview() {
    PostHUBTheme(dynamicColor = false) {
        ApiClientApp()
    }
}
