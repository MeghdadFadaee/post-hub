package net.rodakot.posthub

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.widget.RemoteViews
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.ProtocolException
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

class FlowWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { widgetId ->
            appWidgetManager.updateAppWidget(
                widgetId,
                widgetViews(context, widgetId, loadLastWidgetState(context))
            )
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REFRESH) {
            super.onReceive(context, intent)
            return
        }

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        val ids = widgetIds(appContext, manager, intent)

        ids.forEach { widgetId ->
            manager.updateAppWidget(
                widgetId,
                widgetViews(
                    context = appContext,
                    widgetId = widgetId,
                    state = WidgetState(
                        subtitle = "Running saved flow",
                        status = "Running",
                        meta = "Starting",
                        body = "Connecting to the first request...",
                        statusColor = COLOR_AMBER
                    )
                )
            )
        }

        Executor.execute {
            try {
                val state = runSavedFlow(appContext) { runningState ->
                    ids.forEach { widgetId ->
                        manager.updateAppWidget(
                            widgetId,
                            widgetViews(appContext, widgetId, runningState)
                        )
                    }
                }
                saveLastWidgetState(appContext, state)
                ids.forEach { widgetId ->
                    manager.updateAppWidget(widgetId, widgetViews(appContext, widgetId, state))
                }
            } catch (error: Exception) {
                val state = WidgetState(
                    subtitle = "Flow failed",
                    status = "Error",
                    meta = error.javaClass.simpleName,
                    body = error.cleanMessage(),
                    statusColor = COLOR_RED
                )
                saveLastWidgetState(appContext, state)
                ids.forEach { widgetId ->
                    manager.updateAppWidget(widgetId, widgetViews(appContext, widgetId, state))
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val ACTION_REFRESH = "net.rodakot.posthub.action.RUN_WIDGET_FLOW"
        private const val PREF_NAME = "posthub_state"
        private const val ENVIRONMENT_KEY = "environment_variables"
        private const val FLOW_KEY = "request_flow"
        private const val WIDGET_STATE_KEY = "widget_state"
        private const val COLOR_PRIMARY = 0xFF006B5B.toInt()
        private const val COLOR_AMBER = 0xFFC47A12.toInt()
        private const val COLOR_GREEN = 0xFF147A56.toInt()
        private const val COLOR_RED = 0xFFC7383D.toInt()
        private const val COLOR_MUTED = 0xFF66736D.toInt()
        private val VariablePattern = Regex("""\{\{\s*([A-Za-z0-9_.-]+)\s*\}\}""")
        private val Executor = Executors.newSingleThreadExecutor()
    }
}

private data class WidgetState(
    val title: String = "PostHUB Flow",
    val subtitle: String = "Home runner",
    val status: String = "Ready",
    val meta: String = "Tap refresh",
    val body: String = "Run your saved flow from the home screen.",
    val statusColor: Int = 0xFF006B5B.toInt()
)

private data class WidgetRequestField(
    val id: Int,
    val key: String,
    val value: String,
    val enabled: Boolean = true
)

private data class WidgetAuthState(
    val type: String = "None",
    val bearerToken: String = "",
    val username: String = "",
    val password: String = ""
)

private data class WidgetCaptureField(
    val id: Int,
    val variableName: String,
    val source: String,
    val enabled: Boolean = true
)

private data class WidgetSavedRequest(
    val name: String,
    val method: String,
    val url: String,
    val params: List<WidgetRequestField>,
    val body: String,
    val headers: List<WidgetRequestField>,
    val auth: WidgetAuthState,
    val captures: List<WidgetCaptureField>
)

private data class WidgetFlowStep(
    val id: Long,
    val request: WidgetSavedRequest
)

private data class WidgetRequestSnapshot(
    val method: String,
    val url: String,
    val params: List<WidgetRequestField>,
    val headers: List<WidgetRequestField>,
    val auth: WidgetAuthState,
    val body: String
)

private data class WidgetResponseHeader(
    val name: String,
    val value: String
)

private data class WidgetRequestResult(
    val statusCode: Int?,
    val statusText: String,
    val durationMs: Long,
    val byteCount: Int,
    val finalUrl: String,
    val body: String,
    val rawBody: String,
    val headers: List<WidgetResponseHeader>,
    val error: String? = null
)

private fun widgetViews(
    context: Context,
    widgetId: Int,
    state: WidgetState
): RemoteViews {
    return RemoteViews(context.packageName, R.layout.widget_flow).apply {
        setTextViewText(R.id.widget_title, state.title)
        setTextViewText(R.id.widget_subtitle, state.subtitle)
        setTextViewText(R.id.widget_status, state.status)
        setTextViewText(R.id.widget_meta, state.meta)
        setTextViewText(R.id.widget_body, state.body)
        setTextColor(R.id.widget_status, state.statusColor)
        setOnClickPendingIntent(R.id.widget_refresh, refreshIntent(context, widgetId))
        setOnClickPendingIntent(R.id.widget_root, launchIntent(context))
    }
}

private fun refreshIntent(context: Context, widgetId: Int): PendingIntent {
    val intent = Intent(context, FlowWidgetProvider::class.java).apply {
        action = "net.rodakot.posthub.action.RUN_WIDGET_FLOW"
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
    }
    return PendingIntent.getBroadcast(
        context,
        widgetId,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

private fun launchIntent(context: Context): PendingIntent {
    val intent = Intent(context, MainActivity::class.java)
    return PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

private fun widgetIds(
    context: Context,
    manager: AppWidgetManager,
    intent: Intent
): IntArray {
    val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
    if (id != AppWidgetManager.INVALID_APPWIDGET_ID) return intArrayOf(id)
    return manager.getAppWidgetIds(ComponentName(context, FlowWidgetProvider::class.java))
}

private fun runSavedFlow(
    context: Context,
    onProgress: (WidgetState) -> Unit
): WidgetState {
    val steps = loadFlowSteps(context).ifEmpty { defaultWidgetFlow() }
    var environmentRows = loadEnvironmentVariables(context)
    var lastResult: WidgetRequestResult? = null

    steps.forEachIndexed { index, step ->
        onProgress(
            WidgetState(
                subtitle = "Step ${index + 1} of ${steps.size}",
                status = "Running",
                meta = step.request.name,
                body = step.request.url,
                statusColor = 0xFFC47A12.toInt()
            )
        )

        val snapshot = step.request
            .toSnapshot()
            .resolveVariables(environmentRows.toEnvironmentMap())
        val result = executeWidgetRequest(snapshot)
        lastResult = result

        if (result.error != null) {
            return WidgetState(
                subtitle = "Failed at ${step.request.name}",
                status = "Error",
                meta = result.statusText,
                body = result.error,
                statusColor = 0xFFC7383D.toInt()
            )
        }

        val captured = captureWidgetValues(result, step.request.captures)
        if (captured.isNotEmpty()) {
            environmentRows = mergeCapturedVariables(environmentRows, captured)
            saveEnvironmentVariables(context, environmentRows)
        }
    }

    val result = lastResult ?: return WidgetState(
        subtitle = "No flow steps",
        status = "Empty",
        meta = "Open PostHUB",
        body = "Create or add requests to a flow, then refresh this widget.",
        statusColor = 0xFF66736D.toInt()
    )

    return WidgetState(
        subtitle = "${steps.size} step flow",
        status = result.statusCode?.toString() ?: "Done",
        meta = "${result.durationMs} ms  ${formatBytes(result.byteCount)}",
        body = summarizeWidgetResponse(result),
        statusColor = statusColor(result.statusCode)
    )
}

private fun executeWidgetRequest(snapshot: WidgetRequestSnapshot): WidgetRequestResult {
    val started = System.nanoTime()
    var connection: HttpURLConnection? = null
    var finalUrl = ""

    return try {
        finalUrl = buildWidgetUrl(snapshot.url, snapshot.params)
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
        val bytes = readResponseBytes(connection, statusCode)
        val rawBody = bytes.toString(Charsets.UTF_8)

        WidgetRequestResult(
            statusCode = statusCode,
            statusText = statusText,
            durationMs = elapsedMs(started),
            byteCount = bytes.size,
            finalUrl = finalUrl,
            body = prettyPrintBody(rawBody),
            rawBody = rawBody,
            headers = connection.headerFields
                .orEmpty()
                .filterKeys { it != null }
                .map {
                    val name = it.key.orEmpty()
                    val separator = if (name.equals("Set-Cookie", ignoreCase = true)) "\n" else "; "
                    WidgetResponseHeader(name, it.value.orEmpty().joinToString(separator))
                }
                .sortedBy { it.name.lowercase(Locale.US) }
        )
    } catch (error: Exception) {
        WidgetRequestResult(
            statusCode = null,
            statusText = error.javaClass.simpleName,
            durationMs = elapsedMs(started),
            byteCount = 0,
            finalUrl = finalUrl.ifBlank { snapshot.url },
            body = "",
            rawBody = "",
            headers = emptyList(),
            error = error.cleanMessage()
        )
    } finally {
        connection?.disconnect()
    }
}

private fun loadFlowSteps(context: Context): List<WidgetFlowStep> {
    val raw = context
        .getSharedPreferences("posthub_state", Context.MODE_PRIVATE)
        .getString("request_flow", null)

    if (raw.isNullOrBlank()) return emptyList()

    return runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val request = item.optJSONObject("request") ?: return@mapNotNull null
            WidgetFlowStep(
                id = item.optLong("id", System.currentTimeMillis() + index),
                request = savedRequestFromJson(request)
            )
        }
    }.getOrDefault(emptyList())
}

private fun loadEnvironmentVariables(context: Context): List<WidgetRequestField> {
    val raw = context
        .getSharedPreferences("posthub_state", Context.MODE_PRIVATE)
        .getString("environment_variables", null)

    if (raw.isNullOrBlank()) return defaultEnvironmentVariables()

    val loaded = runCatching {
        requestFieldsFromJson(JSONArray(raw)).ifEmpty { defaultEnvironmentVariables() }
    }.getOrDefault(defaultEnvironmentVariables())

    return mergeDefaultEnvironmentVariables(loaded)
}

private fun saveEnvironmentVariables(
    context: Context,
    variables: List<WidgetRequestField>
) {
    context
        .getSharedPreferences("posthub_state", Context.MODE_PRIVATE)
        .edit()
        .putString("environment_variables", requestFieldsToJson(variables).toString())
        .apply()
}

private fun loadLastWidgetState(context: Context): WidgetState {
    val raw = context
        .getSharedPreferences("posthub_state", Context.MODE_PRIVATE)
        .getString("widget_state", null)

    if (raw.isNullOrBlank()) return WidgetState()

    return runCatching {
        val json = JSONObject(raw)
        WidgetState(
            title = json.optString("title", "PostHUB Flow"),
            subtitle = json.optString("subtitle", "Home runner"),
            status = json.optString("status", "Ready"),
            meta = json.optString("meta", "Tap refresh"),
            body = json.optString("body", "Run your saved flow from the home screen."),
            statusColor = json.optInt("statusColor", 0xFF006B5B.toInt())
        )
    }.getOrDefault(WidgetState())
}

private fun saveLastWidgetState(
    context: Context,
    state: WidgetState
) {
    val json = JSONObject()
        .put("title", state.title)
        .put("subtitle", state.subtitle)
        .put("status", state.status)
        .put("meta", state.meta)
        .put("body", state.body)
        .put("statusColor", state.statusColor)

    context
        .getSharedPreferences("posthub_state", Context.MODE_PRIVATE)
        .edit()
        .putString("widget_state", json.toString())
        .apply()
}

private fun requestFieldsToJson(rows: List<WidgetRequestField>): JSONArray {
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

private fun requestFieldsFromJson(array: JSONArray): List<WidgetRequestField> {
    return (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        WidgetRequestField(
            id = item.optInt("id", index + 1),
            key = item.optString("key"),
            value = item.optString("value"),
            enabled = item.optBoolean("enabled", true)
        )
    }
}

private fun captureFieldsFromJson(array: JSONArray): List<WidgetCaptureField> {
    return (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        WidgetCaptureField(
            id = item.optInt("id", index + 1),
            variableName = item.optString("variableName"),
            source = item.optString("source"),
            enabled = item.optBoolean("enabled", true)
        )
    }
}

private fun authStateFromJson(json: JSONObject?): WidgetAuthState {
    if (json == null) return WidgetAuthState()

    return WidgetAuthState(
        type = json.optString("type", "None"),
        bearerToken = json.optString("bearerToken"),
        username = json.optString("username"),
        password = json.optString("password")
    )
}

private fun savedRequestFromJson(json: JSONObject): WidgetSavedRequest {
    return WidgetSavedRequest(
        name = json.optString("name", "Request"),
        method = json.optString("method", "GET"),
        url = json.optString("url"),
        params = json.optJSONArray("params")?.let(::requestFieldsFromJson) ?: defaultParamRows(),
        body = json.optString("body"),
        headers = json.optJSONArray("headers")?.let(::requestFieldsFromJson) ?: defaultHeaderRows(),
        auth = authStateFromJson(json.optJSONObject("auth")),
        captures = json.optJSONArray("captures")?.let(::captureFieldsFromJson) ?: emptyList()
    )
}

private fun WidgetSavedRequest.toSnapshot(): WidgetRequestSnapshot {
    return WidgetRequestSnapshot(
        method = method,
        url = url,
        params = params,
        headers = headers,
        auth = auth,
        body = body
    )
}

private fun WidgetRequestSnapshot.resolveVariables(variables: Map<String, String>): WidgetRequestSnapshot {
    if (variables.isEmpty()) return this

    return copy(
        url = url.applyVariables(variables),
        params = params.map { it.resolveVariables(variables) },
        headers = headers.map { it.resolveVariables(variables) },
        auth = auth.resolveVariables(variables),
        body = body.applyVariables(variables)
    )
}

private fun WidgetRequestField.resolveVariables(variables: Map<String, String>): WidgetRequestField {
    return copy(
        key = key.applyVariables(variables),
        value = value.applyVariables(variables)
    )
}

private fun WidgetAuthState.resolveVariables(variables: Map<String, String>): WidgetAuthState {
    return copy(
        bearerToken = bearerToken.applyVariables(variables),
        username = username.applyVariables(variables),
        password = password.applyVariables(variables)
    )
}

private fun String.applyVariables(variables: Map<String, String>): String {
    var resolved = this
    repeat(3) {
        val next = Regex("""\{\{\s*([A-Za-z0-9_.-]+)\s*\}\}""").replace(resolved) { match ->
            variables[match.groupValues[1]] ?: match.value
        }
        if (next == resolved) return resolved
        resolved = next
    }
    return resolved
}

private fun List<WidgetRequestField>.toEnvironmentMap(): Map<String, String> {
    return filter { it.enabled && it.key.isNotBlank() }
        .associate { it.key.trim() to it.value }
}

private fun captureWidgetValues(
    result: WidgetRequestResult,
    captures: List<WidgetCaptureField>
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

private fun extractCaptureValue(
    result: WidgetRequestResult,
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
        return extractRegexValue(result.rawBody, expression.substringAfter(":"))
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
    headers: List<WidgetResponseHeader>,
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

private fun mergeCapturedVariables(
    variables: List<WidgetRequestField>,
    captured: Map<String, String>
): List<WidgetRequestField> {
    var next = variables

    captured.forEach { (name, value) ->
        val existingIndex = next.indexOfFirst { it.key == name }
        next = if (existingIndex >= 0) {
            next.mapIndexed { index, field ->
                if (index == existingIndex) field.copy(value = value, enabled = true) else field
            }
        } else {
            next + WidgetRequestField(nextRowId(next), name, value, enabled = true)
        }
    }

    return next.ifEmpty { defaultEnvironmentVariables() }
}

private fun mergeDefaultEnvironmentVariables(variables: List<WidgetRequestField>): List<WidgetRequestField> {
    var next = variables
    defaultEnvironmentVariables().forEach { defaultRow ->
        val exists = next.any { it.key == defaultRow.key }
        if (!exists) {
            next = next + defaultRow.copy(id = nextRowId(next))
        }
    }
    return next
}

private fun buildWidgetUrl(rawUrl: String, params: List<WidgetRequestField>): String {
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

private fun authHeader(auth: WidgetAuthState): String? {
    return when (auth.type) {
        "Bearer" -> auth.bearerToken
            .takeIf { it.isNotBlank() }
            ?.let { "Bearer ${it.trim()}" }

        "Basic" -> {
            if (auth.username.isBlank() && auth.password.isBlank()) return null
            val credentials = "${auth.username}:${auth.password}"
            android.util.Base64.encodeToString(credentials.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
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

private fun summarizeWidgetResponse(result: WidgetRequestResult): String {
    val json = runCatching { JSONObject(result.rawBody) }.getOrNull()
    if (json != null) {
        val parts = listOfNotNull(
            json.optString("ip").takeIf { it.isNotBlank() }?.let { "IP $it" },
            json.optString("city").takeIf { it.isNotBlank() },
            json.optString("country_name").takeIf { it.isNotBlank() },
            json.optString("org").takeIf { it.isNotBlank() },
            json.optString("totalUnusedBytes").takeIf { it.isNotBlank() }?.let {
                "Unused $it ${json.optString("bytesUnusedUnit")}"
            }
        )
        if (parts.isNotEmpty()) return parts.joinToString("\n")
    }

    return result.body
        .ifBlank { result.statusText }
        .replace(Regex("""\s+"""), " ")
        .take(260)
}

private fun defaultWidgetFlow(): List<WidgetFlowStep> {
    val findIp = WidgetSavedRequest(
        name = "Find public IP",
        method = "GET",
        url = "https://api.ipify.org?format=json",
        params = defaultParamRows(),
        body = "",
        headers = listOf(
            WidgetRequestField(1, "Accept", "application/json", true),
            WidgetRequestField(2, "User-Agent", "PostHUB Android/1.0", true)
        ),
        auth = WidgetAuthState(),
        captures = listOf(WidgetCaptureField(1, "public_ip", "\$.ip", true))
    )
    val ipInfo = WidgetSavedRequest(
        name = "IP information",
        method = "GET",
        url = "https://ipapi.co/{{public_ip}}/json/",
        params = defaultParamRows(),
        body = "",
        headers = listOf(
            WidgetRequestField(1, "Accept", "application/json", true),
            WidgetRequestField(2, "User-Agent", "PostHUB Android/1.0", true)
        ),
        auth = WidgetAuthState(),
        captures = listOf(
            WidgetCaptureField(1, "ip_city", "\$.city", true),
            WidgetCaptureField(2, "ip_region", "\$.region", true),
            WidgetCaptureField(3, "ip_country", "\$.country_name", true),
            WidgetCaptureField(4, "ip_timezone", "\$.timezone", true),
            WidgetCaptureField(5, "ip_org", "\$.org", true),
            WidgetCaptureField(6, "ip_asn", "\$.asn", true)
        )
    )
    return listOf(
        WidgetFlowStep(1L, findIp),
        WidgetFlowStep(2L, ipInfo)
    )
}

private fun defaultEnvironmentVariables(): List<WidgetRequestField> {
    return listOf(
        WidgetRequestField(1, "base_url", "https://jsonplaceholder.typicode.com", true),
        WidgetRequestField(2, "token", "", true),
        WidgetRequestField(3, "mci_username", "", true),
        WidgetRequestField(4, "mci_password", "", true),
        WidgetRequestField(5, "mci_access_token", "", true),
        WidgetRequestField(6, "mci_refresh_token", "", true),
        WidgetRequestField(7, "mci_session_state", "", true),
        WidgetRequestField(8, "mci_total_unused", "", true),
        WidgetRequestField(9, "mci_unused_unit", "", true),
        WidgetRequestField(10, "tci_username", "", true),
        WidgetRequestField(11, "tci_password", "", true),
        WidgetRequestField(12, "tci_captcha", "", true),
        WidgetRequestField(13, "tci_cookie", "", true),
        WidgetRequestField(14, "tci_login_action", "/panel", true),
        WidgetRequestField(15, "tci_captcha_path", "", true),
        WidgetRequestField(16, "public_ip", "", true),
        WidgetRequestField(17, "ip_city", "", true),
        WidgetRequestField(18, "ip_region", "", true),
        WidgetRequestField(19, "ip_country", "", true),
        WidgetRequestField(20, "ip_timezone", "", true),
        WidgetRequestField(21, "ip_org", "", true),
        WidgetRequestField(22, "ip_asn", "", true)
    )
}

private fun defaultParamRows(): List<WidgetRequestField> {
    return listOf(WidgetRequestField(1, "", "", true))
}

private fun defaultHeaderRows(): List<WidgetRequestField> {
    return listOf(
        WidgetRequestField(1, "Accept", "application/json", true),
        WidgetRequestField(2, "Content-Type", "application/json", false)
    )
}

private fun nextRowId(rows: List<WidgetRequestField>): Int {
    return (rows.maxOfOrNull { it.id } ?: 0) + 1
}

private fun elapsedMs(started: Long): Long {
    return (System.nanoTime() - started) / 1_000_000
}

private fun formatBytes(bytes: Int): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024f)
        else -> String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f))
    }
}

private fun statusColor(statusCode: Int?): Int {
    return when (statusCode) {
        in 200..299 -> 0xFF147A56.toInt()
        in 300..399 -> 0xFFC47A12.toInt()
        in 400..499 -> 0xFFD84B35.toInt()
        else -> 0xFFC7383D.toInt()
    }
}

private fun Throwable.cleanMessage(): String {
    return message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName
}
