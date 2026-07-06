package com.example.codexmobile.ui

import android.content.Intent
import android.net.Uri
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
    viewModel: ChatViewModel = viewModel(),
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

    val context = LocalContext.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }
    var denyReason by remember { mutableStateOf("") }
    var showFullAccessConfirmation by remember { mutableStateOf(false) }

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
                        IconButton(onClick = onLogout) {
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
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                errorMessage?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    )
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
                        items(messages) { message ->
                            MessageBubble(message)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        if (isTyping) {
                            item {
                                Text(
                                    text = "CodexR is processing...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        enabled = isReady && !isTyping,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Type a prompt or command...") },
                        shape = RoundedCornerShape(24.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FloatingActionButton(
                        onClick = {
                            if (inputText.isNotBlank() && !isTyping) {
                                viewModel.sendMessage(inputText)
                                inputText = ""
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
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
    onDeny: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Approve Root Command") },
        text = {
            Column {
                Text("The AI wants to execute the following command as root:")
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Review the complete command. Approval grants unrestricted root access for this command only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
                    Text(command, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = denyReason,
                    onValueChange = onDenyReasonChange,
                    label = { Text("Denial reason (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { Button(onClick = onApprove) { Text("Approve") } },
        dismissButton = { OutlinedButton(onClick = onDeny) { Text("Deny") } },
    )
}

@Composable
fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Box(
            modifier = Modifier
                .background(color = color, shape = RoundedCornerShape(16.dp))
                .padding(12.dp),
        ) {
            Text(message.content, color = textColor, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun modelLabel(modelId: String, models: List<CodexModelOption>): String =
    models.firstOrNull { it.id == modelId }?.label ?: modelId
