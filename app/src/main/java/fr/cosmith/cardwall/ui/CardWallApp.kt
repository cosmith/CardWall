package fr.cosmith.cardwall.ui

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import fr.cosmith.cardwall.R
import fr.cosmith.cardwall.anki.AnkiRepository
import fr.cosmith.cardwall.anki.AnkiUnavailableException
import fr.cosmith.cardwall.apps.AppRepository
import fr.cosmith.cardwall.data.CardWallPreferences
import fr.cosmith.cardwall.model.CardWallSettings
import fr.cosmith.cardwall.model.DeckInfo
import fr.cosmith.cardwall.model.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val LightColors =
    lightColorScheme(
        primary = Color(0xFF315C49),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFC1E9D0),
        onPrimaryContainer = Color(0xFF0D3524),
        background = Color(0xFFF7F8F4),
        surface = Color(0xFFFFFFFF),
    )

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFF9DD5B8),
        onPrimary = Color(0xFF123828),
        primaryContainer = Color(0xFF214D3A),
        background = Color(0xFF101512),
        surface = Color(0xFF171D19),
    )

@Composable
fun CardWallApp(
    preferences: CardWallPreferences,
    ankiRepository: AnkiRepository,
    appRepository: AppRepository,
    isAccessibilityEnabled: () -> Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onRequestAnkiPermission: ((() -> Unit) -> Unit),
) {
    val isDark = (LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    MaterialTheme(colorScheme = if (isDark) DarkColors else LightColors) {
        Surface(modifier = Modifier.fillMaxSize()) {
            SettingsScreen(
                preferences = preferences,
                ankiRepository = ankiRepository,
                appRepository = appRepository,
                isAccessibilityEnabled = isAccessibilityEnabled,
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                onRequestAnkiPermission = onRequestAnkiPermission,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    preferences: CardWallPreferences,
    ankiRepository: AnkiRepository,
    appRepository: AppRepository,
    isAccessibilityEnabled: () -> Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onRequestAnkiPermission: ((() -> Unit) -> Unit),
) {
    var refreshGeneration by remember { mutableIntStateOf(0) }
    var settings by remember { mutableStateOf(preferences.load()) }
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var decks by remember { mutableStateOf<List<DeckInfo>>(emptyList()) }
    var accessibilityEnabled by remember { mutableStateOf(false) }
    var ankiInstalled by remember { mutableStateOf(false) }
    var ankiPermission by remember { mutableStateOf(false) }
    var deckLoadError by remember { mutableStateOf<String?>(null) }
    var showApps by remember { mutableStateOf(false) }
    var showDecks by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshGeneration++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(refreshGeneration) {
        settings = preferences.load()
        accessibilityEnabled = isAccessibilityEnabled()
        ankiInstalled = ankiRepository.isInstalled()
        ankiPermission = ankiRepository.hasPermission()
        apps = withContext(Dispatchers.IO) { appRepository.loadLaunchableApps() }
        if (ankiInstalled && ankiPermission) {
            try {
                decks = withContext(Dispatchers.IO) { ankiRepository.loadDecks() }
                deckLoadError = null
            } catch (error: AnkiUnavailableException) {
                decks = emptyList()
                deckLoadError = error.message
            }
        } else {
            decks = emptyList()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("CardWall", fontWeight = FontWeight.Bold)
                        Text("A small pause before the scroll", style = MaterialTheme.typography.labelMedium)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionTitle("Setup")
                SetupCard(
                    accessibilityEnabled = accessibilityEnabled,
                    ankiInstalled = ankiInstalled,
                    ankiPermission = ankiPermission,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    onRequestAnkiPermission = {
                        onRequestAnkiPermission { refreshGeneration++ }
                    },
                )
            }

            item {
                SectionTitle("What to gate")
                SelectionCard(
                    icon = Icons.Default.Apps,
                    title = "Distracting apps",
                    summary =
                        when (settings.targetPackages.size) {
                            0 -> "Choose YouTube, X, or any other app"
                            1 -> "1 app selected"
                            else -> "${settings.targetPackages.size} apps selected"
                        },
                    enabled = true,
                    onClick = { showApps = true },
                )
            }

            item {
                SectionTitle("What to review")
                SelectionCard(
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    title = "Anki decks",
                    summary =
                        when {
                            !ankiInstalled -> "Install and set up AnkiDroid first"
                            !ankiPermission -> "Grant AnkiDroid collection access first"
                            settings.selectedDeckIds.isEmpty() -> "Choose one or more deck branches"
                            else -> "${settings.selectedDeckIds.size} deck branch${if (settings.selectedDeckIds.size == 1) "" else "es"} selected"
                        },
                    enabled = ankiInstalled && ankiPermission,
                    onClick = { showDecks = true },
                )
                deckLoadError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }

            item {
                SectionTitle("Gate rules")
                RuleCard(
                    title = "Reviews to unlock",
                    value = settings.requiredReviews,
                    valueLabel = { "$it card${if (it == 1) "" else "s"}" },
                    range = 1..20,
                    onChange = { value ->
                        preferences.saveRequiredReviews(value)
                        settings = preferences.load()
                    },
                )
                Spacer(Modifier.height(8.dp))
                RuleCard(
                    title = "Access window",
                    value = settings.accessMinutes,
                    valueLabel = { "$it minute${if (it == 1) "" else "s"}" },
                    range = 1..120,
                    onChange = { value ->
                        preferences.saveAccessMinutes(value)
                        settings = preferences.load()
                    },
                )
                Spacer(Modifier.height(8.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().toggleable(
                            value = settings.skipEnabled,
                            role = Role.Switch,
                            onValueChange = { enabled ->
                                preferences.saveSkipEnabled(enabled)
                                settings = preferences.load()
                            },
                        ).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(stringResource(R.string.show_skip_button), fontWeight = FontWeight.Medium)
                            Text(stringResource(R.string.skip_setting_description), style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = settings.skipEnabled, onCheckedChange = null)
                    }
                }
            }

            item {
                InfoCard(
                    title = "Local and private",
                    body = "CardWall has no account, analytics, ads, or internet permission. It reads cards through AnkiDroid and writes only real review grades back.",
                )
            }

            item {
                InfoCard(
                    title = "Media in v0.1",
                    body = "HTML, CSS, and card JavaScript are preserved. Images, video, and audio are intentionally replaced by a small placeholder in this first version.",
                    warning = true,
                )
            }
        }
    }

    if (showApps) {
        AppPickerDialog(
            apps = apps,
            selected = settings.targetPackages,
            onDismiss = { showApps = false },
            onSave = { selected ->
                preferences.saveTargetPackages(selected)
                settings = preferences.load()
                showApps = false
            },
        )
    }

    if (showDecks) {
        DeckPickerDialog(
            decks = decks,
            selected = settings.selectedDeckIds,
            onDismiss = { showDecks = false },
            onSave = { selected ->
                preferences.saveDeckIds(selected)
                settings = preferences.load()
                showDecks = false
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 8.dp, top = 4.dp),
    )
}

@Composable
private fun SetupCard(
    accessibilityEnabled: Boolean,
    ankiInstalled: Boolean,
    ankiPermission: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onRequestAnkiPermission: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SetupRow(
                title = "Foreground detection",
                ready = accessibilityEnabled,
                pendingText = "Enable CardWall accessibility",
                action = onOpenAccessibilitySettings,
            )
            HorizontalDivider()
            SetupRow(
                title = "AnkiDroid",
                ready = ankiInstalled,
                pendingText = "AnkiDroid is not installed",
                action = null,
            )
            HorizontalDivider()
            SetupRow(
                title = "Collection permission",
                ready = ankiPermission,
                pendingText = if (ankiInstalled) "Grant permission" else "Waiting for AnkiDroid",
                action = if (ankiInstalled) onRequestAnkiPermission else null,
            )
        }
    }
}

@Composable
private fun SetupRow(title: String, ready: Boolean, pendingText: String, action: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(
            imageVector = if (ready) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(if (ready) "Ready" else pendingText, style = MaterialTheme.typography.bodySmall)
        }
        if (!ready && action != null) OutlinedButton(onClick = action) { Text("Set up") }
    }
}

@Composable
private fun SelectionCard(
    icon: ImageVector,
    title: String,
    summary: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Choose", color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun RuleCard(
    title: String,
    value: Int,
    valueLabel: (Int) -> String,
    range: IntRange,
    onChange: (Int) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium)
                Text(valueLabel(value), style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = { onChange(value - 1) }, enabled = value > range.first) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease")
            }
            Text(value.toString(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 8.dp))
            IconButton(onClick = { onChange(value + 1) }, enabled = value < range.last) {
                Icon(Icons.Default.Add, contentDescription = "Increase")
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String, warning: Boolean = false) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = if (warning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
            ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AppPickerDialog(
    apps: List<InstalledApp>,
    selected: Set<String>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit,
) {
    var working by remember(selected) { mutableStateOf(selected.toSet()) }
    var query by remember { mutableStateOf("") }
    val filtered = apps.filter { it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Distracting apps") },
        text = {
            Column(Modifier.height(520.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    placeholder = { Text("Search installed apps") },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn {
                    items(filtered, key = { it.packageName }) { app ->
                        Row(
                            modifier =
                                Modifier.fillMaxWidth().clickable {
                                    working = if (app.packageName in working) working - app.packageName else working + app.packageName
                                }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Image(app.icon, contentDescription = null, modifier = Modifier.size(40.dp))
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(app.packageName, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                            Checkbox(checked = app.packageName in working, onCheckedChange = null)
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(working) }) { Text("Save") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DeckPickerDialog(
    decks: List<DeckInfo>,
    selected: Set<Long>,
    onDismiss: () -> Unit,
    onSave: (Set<Long>) -> Unit,
) {
    var working by remember(selected) { mutableStateOf(selected.toSet()) }
    var query by remember { mutableStateOf("") }
    val deckById = decks.associateBy { it.id }
    val filtered = decks.filter { it.name.contains(query, ignoreCase = true) }

    fun parentSelected(deck: DeckInfo): Boolean =
        working.any { selectedId ->
            val selectedName = deckById[selectedId]?.name ?: return@any false
            deck.name.startsWith("$selectedName::")
        }

    fun toggle(deck: DeckInfo) {
        if (parentSelected(deck)) return
        working =
            if (deck.id in working) {
                working - deck.id
            } else {
                val descendants = decks.filter { it.name.startsWith("${deck.name}::") }.map { it.id }.toSet()
                (working - descendants) + deck.id
            }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Anki deck branches") },
        text = {
            Column(Modifier.height(520.dp)) {
                Text("Selecting a parent automatically includes its current and future subdecks.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    placeholder = { Text("Search decks") },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn {
                    items(filtered, key = { it.id }) { deck ->
                        val inherited = parentSelected(deck)
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable(enabled = !inherited) { toggle(deck) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(deck.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    if (inherited) "Included by selected parent" else "${deck.dueCount} due now",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            Checkbox(checked = inherited || deck.id in working, enabled = !inherited, onCheckedChange = { toggle(deck) })
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(working) }) { Text("Save") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
