package com.example.codexmobile.ui

import android.content.Intent
import android.net.Uri
import android.app.Activity
import android.Manifest
import android.os.Build
import android.content.ClipData
import android.content.ClipboardManager
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.codexmobile.CodexApplication
import com.example.codexmobile.api.MessageAttachment
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.codexmobile.R
import com.example.codexmobile.ShellAccessMode
import com.example.codexmobile.api.AuthMethod
import com.example.codexmobile.api.ChatMessage
import com.example.codexmobile.api.UsageSnapshot
import com.example.codexmobile.data.CodexModelOption
import com.example.codexmobile.data.ChatSession
import com.example.codexmobile.data.ReasoningLevel
import com.example.codexmobile.theme.ThemeMode
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onLogout: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeChanged: (ThemeMode) -> Unit,
    viewModel: ChatViewModel = (LocalContext.current.applicationContext as CodexApplication).chatController,
) {
    val sessions by viewModel.sessions.collectAsState()
    val activeSessionId by viewModel.activeSessionId.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val selectedModelId by viewModel.selectedModelId.collectAsState()
    val reasoningLevel by viewModel.reasoningLevel.collectAsState()
    val modelCatalog by viewModel.modelCatalog.collectAsState()
    val supportedReasoningLevels by viewModel.supportedReasoningLevels.collectAsState()
    val modelCatalogStatus by viewModel.modelCatalogStatus.collectAsState()
    val isReady by viewModel.isReady.collectAsState()
    val isTyping by viewModel.isTyping.collectAsState()
    val pendingCommand by viewModel.pendingCommand.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val accessMode by viewModel.accessMode.collectAsState()
    val usage by viewModel.usage.collectAsState()
    val isUsageLoading by viewModel.isUsageLoading.collectAsState()
    val usageError by viewModel.usageError.collectAsState()
    val streamingText by viewModel.streamingText.collectAsState()
    val phase by viewModel.phase.collectAsState()
    val queuedMessages by viewModel.queuedMessages.collectAsState()
    val sessionRootAllowed by viewModel.sessionRootAllowed.collectAsState()
    val canRetry by viewModel.canRetry.collectAsState()
    val draftAttachments by viewModel.draftAttachments.collectAsState()
    val attachmentStatus by viewModel.attachmentStatus.collectAsState()
    val attachmentError by viewModel.attachmentError.collectAsState()

    val context = LocalContext.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var inputText by rememberSaveable(activeSessionId) { mutableStateOf("") }
    var denyReason by remember { mutableStateOf("") }
    var showFullAccessConfirmation by remember { mutableStateOf(false) }
    var editingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var showCaptureConfirmation by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.addAttachments(uris)
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    DisposableEffect(isTyping, attachmentStatus) {
        val window = (context as? Activity)?.window
        if (isTyping || attachmentStatus != null) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    editingMessage?.let { message ->
        EditMessageDialog(message = message, enabled = !isTyping,
            onDismiss = { editingMessage = null },
            onSave = { text, attachments ->
                viewModel.editMessage(message.id, text, attachments)
                editingMessage = null
            })
    }
    if (showCaptureConfirmation) AlertDialog(
        onDismissRequest = { showCaptureConfirmation = false },
        title = { Text("Capture screen with root?") },
        text = { Text("Capture starts after a 3-second countdown. Switch to the screen you want to share. " +
            "The screenshot stays in your draft until you send it. Check for private information first. " +
            "Protected screens may appear blank.") },
        confirmButton = { Button(onClick = { showCaptureConfirmation = false; viewModel.captureScreen() }) { Text("Capture in 3 seconds") } },
        dismissButton = { TextButton(onClick = { showCaptureConfirmation = false }) { Text("Cancel") } },
    )

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    if (showFullAccessConfirmation) {
        FullAccessConfirmation(
            onConfirm = {
                viewModel.setFullAccessEnabled(true)
                showFullAccessConfirmation = false
            },
            onDismiss = { showFullAccessConfirmation = false },
        )
    }

    pendingCommand?.let { command ->
        CommandApprovalDialog(
            command = command,
            denyReason = denyReason,
            onDenyReasonChange = { denyReason = it },
            onApprove = {
                denyReason = ""
                viewModel.approveCommand()
            },
            onApproveSession = { denyReason = ""; viewModel.approveCommand(forSession = true) },
            onStop = viewModel::stopExecution,
            onDeny = {
                val reason = denyReason
                denyReason = ""
                viewModel.denyCommand(reason)
            },
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = pendingCommand == null,
        drawerContent = {
            SessionDrawer(
                sessions = sessions,
                activeSessionId = activeSessionId,
                modelCatalog = modelCatalog,
                accessMode = accessMode,
                authMethod = viewModel.authMethod,
                themeMode = themeMode,
                usage = usage,
                isUsageLoading = isUsageLoading,
                usageError = usageError,
                enabled = isReady && !isTyping && pendingCommand == null,
                onCreate = {
                    viewModel.createSession()
                    coroutineScope.launch { drawerState.close() }
                },
                onSelect = { sessionId ->
                    viewModel.selectSession(sessionId)
                    coroutineScope.launch { drawerState.close() }
                },
                onDelete = viewModel::deleteSession,
                onFullAccessChange = { enabled ->
                    if (enabled) showFullAccessConfirmation = true
                    else viewModel.setFullAccessEnabled(false)
                },
                onThemeModeChanged = onThemeModeChanged,
                onRefreshUsage = viewModel::refreshUsage,
                onOpenApiUsage = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://platform.openai.com/usage")),
                    )
                },
            )
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Open chat sessions")
                        }
                    },
                    title = {
                        Text(sessions.firstOrNull { it.id == activeSessionId }?.title ?: "CodexR")
                    },
                    actions = {
                        IconButton(onClick = onLogout, enabled = !isTyping && attachmentStatus == null) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            },
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize().imePadding()) {
                errorMessage?.let { error ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        SelectionContainer { Text(error, color = MaterialTheme.colorScheme.error, maxLines = 4) }
                        Row {
                            if (canRetry) TextButton(onClick = viewModel::retryResponse) { Text("Retry response") }
                            messages.lastOrNull { it.role == "user" && it.kind == "message" }?.let { last ->
                                TextButton(onClick = { editingMessage = last }, enabled = !isTyping) { Text("Edit last prompt") }
                            }
                        }
                    }
                }
                if (sessionRootAllowed && accessMode != ShellAccessMode.FULL_ACCESS) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Root allowed for this chat", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                        TextButton(onClick = viewModel::revokeSessionRoot) { Text("Revoke") }
                    }
                }
                if (accessMode == ShellAccessMode.FULL_ACCESS) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer) {
                        Text(
                            text = "FULL ACCESS - CodexR commands run immediately as root.",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }

                ModelControls(
                    selectedModelId = selectedModelId,
                    reasoningLevel = reasoningLevel,
                    models = modelCatalog,
                    supportedReasoningLevels = supportedReasoningLevels,
                    catalogStatus = modelCatalogStatus,
                    enabled = isReady && !isTyping,
                    onModelSelected = viewModel::setModel,
                    onReasoningSelected = viewModel::setReasoningLevel,
                    onRefresh = viewModel::refreshModels,
                )

                if (!isReady) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                    ) {
                        items(messages, key = { it.id }) { message ->
                            MessageBubble(message, onEdit = {
                                if (isTyping) viewModel.stopExecution()
                                editingMessage = message
                            })
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        if (isTyping) {
                            item {
                                if (streamingText.isNotBlank()) {
                                    MarkdownText(streamingText, MaterialTheme.colorScheme.onSurface, Modifier.fillMaxWidth().padding(12.dp))
                                }
                                Text(
                                    text = phase.ifBlank { "CodexR is processing…" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }
                }

                if (queuedMessages.isNotEmpty()) Column(Modifier.fillMaxWidth().heightIn(max = 144.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    Text(if (isTyping) "Queued • sent after the next tool result (or when this response finishes)" else "Queued • included when you retry or send", style = MaterialTheme.typography.labelSmall)
                    queuedMessages.forEach { queued ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(queued.content.ifBlank { "${queued.attachments.size} attachment(s)" }, maxLines = 2, modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                if (viewModel.restoreQueuedToDraft(queued)) {
                                    inputText = listOf(inputText, queued.content).filter { it.isNotBlank() }.joinToString("\n")
                                }
                            }) { Icon(Icons.Default.Edit, "Edit queued message") }
                            IconButton(onClick = { viewModel.removeQueued(queued.id) }) { Icon(Icons.Default.Close, "Remove queued message") }
                        }
                    }
                }
                AttachmentStrip(draftAttachments, onRemove = viewModel::removeDraftAttachment)
                attachmentStatus?.let { Text(it, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelSmall) }
                attachmentError?.let { Text(it, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall) }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    var attachmentMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { attachmentMenu = true }, enabled = isReady && attachmentStatus == null) {
                            Icon(Icons.Default.AttachFile, "Attach files or capture screen")
                        }
                        DropdownMenu(expanded = attachmentMenu, onDismissRequest = { attachmentMenu = false }) {
                            DropdownMenuItem(text = { Text("Images, PDFs, or text/code files") }, onClick = {
                                attachmentMenu = false; picker.launch(arrayOf("*/*"))
                            })
                            DropdownMenuItem(text = { Text("Capture screen (root)") }, onClick = {
                                attachmentMenu = false; showCaptureConfirmation = true
                            })
                        }
                    }
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        enabled = isReady,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(if (isTyping) "Add a follow-up…" else "Type a prompt…") },
                        maxLines = 5,
                        shape = RoundedCornerShape(24.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (isTyping || attachmentStatus != null) IconButton(onClick = { viewModel.stopExecution() }) {
                        Icon(Icons.Default.Stop, "Stop execution", tint = MaterialTheme.colorScheme.error)
                    }
                    IconButton(
                        onClick = {
                            if (viewModel.sendMessage(inputText)) {
                                inputText = ""
                            }
                        },
                        enabled = isReady && attachmentStatus == null && (inputText.isNotBlank() || draftAttachments.isNotEmpty()),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = if (isTyping) "Queue follow-up" else "Send")
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionDrawer(
    sessions: List<ChatSession>,
    activeSessionId: String?,
    modelCatalog: List<CodexModelOption>,
    accessMode: ShellAccessMode,
    authMethod: AuthMethod,
    themeMode: ThemeMode,
    usage: UsageSnapshot?,
    isUsageLoading: Boolean,
    usageError: String?,
    enabled: Boolean,
    onCreate: () -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onFullAccessChange: (Boolean) -> Unit,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onRefreshUsage: () -> Unit,
    onOpenApiUsage: () -> Unit,
) {
    val context = LocalContext.current
    var showBatteryHelp by remember { mutableStateOf(false) }
    if (showBatteryHelp) AlertDialog(
        onDismissRequest = { showBatteryHelp = false },
        title = { Text("Background execution") },
        text = { Text("CodexR keeps a work notification and holds the CPU awake during active turns. " +
            "For reliable screen-off networking, select CodexR in battery settings and choose Unrestricted or Don't optimize. " +
            "Some devices also need background activity/auto-start enabled. This uses more battery. " +
            "Android can still stop work after its background time limit or if you force-stop the app.") },
        confirmButton = { Button(onClick = {
            showBatteryHelp = false
            runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                .onFailure { context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }
        }) { Text("Open battery settings") } },
        dismissButton = { TextButton(onClick = { showBatteryHelp = false }) { Text("Close") } },
    )
    ModalDrawerSheet(modifier = Modifier.width(340.dp).fillMaxHeight()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.codexr_logo),
                contentDescription = null,
                modifier = Modifier.width(44.dp).height(44.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("CodexR", style = MaterialTheme.typography.titleLarge)
                Text("Root coding agent", style = MaterialTheme.typography.bodySmall)
            }
        }
        Button(
            onClick = onCreate,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("New chat")
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Full access", style = MaterialTheme.typography.titleSmall)
                Text("Run root commands without approval", style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = accessMode == ShellAccessMode.FULL_ACCESS,
                onCheckedChange = onFullAccessChange,
            )
        }
        HorizontalDivider()
        TextButton(onClick = { showBatteryHelp = true }, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Background execution / battery settings") }
        Text(
            "Appearance",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp),
        )
        Selector(
            label = "Theme",
            value = themeMode.label,
            options = ThemeMode.entries.map { it.label to it.name },
            enabled = enabled,
            onSelected = { onThemeModeChanged(ThemeMode.valueOf(it)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        Text(
            "Usage",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp),
        )
        if (authMethod == AuthMethod.API_KEY) {
            Text(
                "API usage and billing are managed by OpenAI Platform.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            OutlinedButton(
                onClick = onOpenApiUsage,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) { Text("Open API usage") }
        } else {
            OutlinedButton(
                onClick = onRefreshUsage,
                enabled = !isUsageLoading,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                if (isUsageLoading) {
                    CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp))
                } else {
                    Text("Check remaining usage")
                }
            }
            usage?.let { UsageSummary(it) }
            usageError?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
        Text(
            "Chats",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp),
        )
        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(8.dp)) {
            items(sessions, key = { it.id }) { session ->
                NavigationDrawerItem(
                    selected = session.id == activeSessionId,
                    onClick = { if (enabled) onSelect(session.id) },
                    label = {
                        Column {
                            Text(session.title, maxLines = 1)
                            Text(
                                "${modelLabel(session.modelId, modelCatalog)} / ${session.reasoningLevel.label}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    badge = {
                        IconButton(onClick = { if (enabled) onDelete(session.id) }, enabled = enabled) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete ${session.title}")
                        }
                    },
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun UsageSummary(usage: UsageSnapshot) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        if (usage.planType.isNotBlank()) {
            Text(
                usage.planType.replace('_', ' ').replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelMedium,
            )
        }
        usage.windows.forEach { window ->
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "${window.label}: ${window.remainingPercent}% remaining",
                style = MaterialTheme.typography.bodySmall,
            )
            LinearProgressIndicator(
                progress = { window.usedPercent / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
            window.resetsAtEpochSeconds?.let { reset ->
                Text(
                    "Resets ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(reset * 1000))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        when {
            usage.unlimitedCredits -> Text("Credits: unlimited", style = MaterialTheme.typography.bodySmall)
            usage.creditBalance != null -> Text(
                "Credits: ${usage.creditBalance}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ModelControls(
    selectedModelId: String,
    reasoningLevel: ReasoningLevel,
    models: List<CodexModelOption>,
    supportedReasoningLevels: List<ReasoningLevel>,
    catalogStatus: String,
    enabled: Boolean,
    onModelSelected: (String) -> Unit,
    onReasoningSelected: (ReasoningLevel) -> Unit,
    onRefresh: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Selector(
                label = "Model",
                value = modelLabel(selectedModelId, models),
                options = models.map { it.label to it.id },
                enabled = enabled,
                onSelected = onModelSelected,
                modifier = Modifier.weight(1f),
            )
            Selector(
                label = "Reasoning",
                value = reasoningLevel.label,
                options = supportedReasoningLevels.map { it.label to it.name },
                enabled = enabled,
                onSelected = { name -> onReasoningSelected(ReasoningLevel.valueOf(name)) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            Text(
                text = catalogStatus,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = onRefresh, enabled = enabled) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh model catalog")
            }
        }
    }
}

@Composable
private fun Selector(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    enabled: Boolean,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("$label: $value", maxLines = 1)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (optionLabel, optionValue) ->
                DropdownMenuItem(
                    text = { Text(optionLabel) },
                    onClick = {
                        onSelected(optionValue)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun FullAccessConfirmation(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enable full access?") },
        text = {
            Text(
                "CodexR commands will run immediately as root without individual approval. " +
                    "This setting remains enabled until you turn it off.",
            )
        },
        confirmButton = { Button(onClick = onConfirm) { Text("Enable full access") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CommandApprovalDialog(
    command: String,
    denyReason: String,
    onDenyReasonChange: (String) -> Unit,
    onApprove: () -> Unit,
    onApproveSession: () -> Unit,
    onStop: () -> Unit,
    onDeny: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Approve Root Command") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text("The AI wants to execute the following command as root:")
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Review the complete command. Approval grants unrestricted root access for this command only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
                    SelectionContainer { Text(command, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall) }
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = denyReason,
                    onValueChange = onDenyReasonChange,
                    label = { Text("Denial reason (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Allow for this chat grants root access to future commands here until revoked or the app restarts.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
                OutlinedButton(onClick = onApproveSession, modifier = Modifier.fillMaxWidth()) { Text("Allow for this chat") }
            }
        },
        confirmButton = { Button(onClick = onApprove) { Text("Approve") } },
        dismissButton = { Row {
            TextButton(onClick = onStop) { Text("Stop") }
            OutlinedButton(onClick = onDeny) { Text("Deny") }
        } },
    )
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun MessageBubble(message: ChatMessage, onEdit: (() -> Unit)? = null) {
    val isUser = message.role == "user" && message.kind == "message"
    val context = LocalContext.current
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Column(
            modifier = Modifier
                .background(color = color, shape = RoundedCornerShape(16.dp))
                .combinedClickable(onClick = {}, onLongClick = if (isUser) onEdit else null)
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (isUser) "You" else if (message.kind == "tool") "Command result" else if (message.kind == "capture_result") "Screen capture" else "CodexR",
                    color = textColor, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                if (isUser && onEdit != null) IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Edit, "Edit message", Modifier.size(16.dp)) }
                IconButton(onClick = {
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("CodexR", message.content))
                }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.ContentCopy, "Copy message", Modifier.size(16.dp)) }
            }
            if (message.interrupted) Text("Incomplete response", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            MarkdownText(message.content, textColor, Modifier.fillMaxWidth(), if (isUser) onEdit else null)
            AttachmentStrip(message.attachments)
        }
    }
}

@Composable
private fun EditMessageDialog(message: ChatMessage, enabled: Boolean, onDismiss: () -> Unit,
    onSave: (String, List<MessageAttachment>) -> Unit) {
    var text by remember(message.id) { mutableStateOf(message.content) }
    var attachments by remember(message.id) { mutableStateOf(message.attachments) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Edit and resend") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("This replaces the conversation from this prompt onward, including queued follow-ups. Previously executed commands are not undone.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 300.dp), maxLines = 10)
            AttachmentStrip(attachments, onRemove = { id -> attachments = attachments.filterNot { it.id == id } })
        } },
        confirmButton = { Button(onClick = { onSave(text, attachments) }, enabled = enabled && (text.isNotBlank() || attachments.isNotEmpty())) { Text(if (enabled) "Save & resend" else "Stopping…") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun modelLabel(modelId: String, models: List<CodexModelOption>): String =
    models.firstOrNull { it.id == modelId }?.label ?: modelId
