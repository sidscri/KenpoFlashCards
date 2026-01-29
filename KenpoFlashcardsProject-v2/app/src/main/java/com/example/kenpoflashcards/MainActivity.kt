package com.example.kenpoflashcards

private const val GEN8_FULL_ADMIN_UI: Boolean = false

import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

private val DarkBg = Color(0xFF0F1217)
private val DarkPanel = Color(0xFF151A22)
private val DarkPanel2 = Color(0xFF11161F)
private val DarkBorder = Color(0xFF2B3140)
private val DarkText = Color(0xFFEAEEF7)
private val DarkMuted = Color(0xFFB7C0D4)
private val AccentGood = Color(0xFF12311F)
private val AccentBlue = Color(0xFF1F6FEB)

private val KenpoDarkColorScheme = darkColorScheme(
    primary = AccentBlue, onPrimary = Color.White, primaryContainer = AccentGood,
    secondary = DarkPanel, background = DarkBg, onBackground = DarkText,
    surface = DarkPanel, onSurface = DarkText, surfaceVariant = DarkPanel2,
    onSurfaceVariant = DarkText, outline = DarkBorder,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = KenpoDarkColorScheme) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { AppRoot() }
            }
        }
    }
}

private sealed class Route(val path: String) {
    data object Active : Route("active")
    data object Unsure : Route("unsure")
    data object Learned : Route("learned")
    data object AllCards : Route("all")
    data object CustomSet : Route("custom")
    data object Deleted : Route("deleted")
    data object Settings : Route("settings")
    data object Admin : Route("admin")
    data object About : Route("about")
    data object Login : Route("login")
    data object SyncProgress : Route("sync_progress")
    data object UserGuide : Route("user_guide")
    data object ManageDecks : Route("manage_decks")
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val store = remember { Store(context) }
    val repo = remember { Repository(context, store) }
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Route.Active.path) {
        composable(Route.Active.path) { StudyScreen(nav, repo, CardStatus.ACTIVE) }
        composable(Route.Unsure.path) { StudyScreen(nav, repo, CardStatus.UNSURE) }
        composable(Route.Learned.path) { LearnedScreen(nav, repo) }
        composable(Route.AllCards.path) { AllCardsScreen(nav, repo) }
        composable(Route.CustomSet.path) { CustomSetScreen(nav, repo) }
        composable(Route.Deleted.path) { DeletedScreen(nav, repo) }
        composable(Route.Settings.path) { SettingsScreen(nav, repo) }
        composable(Route.Admin.path) { AdminScreen(nav, repo) }
        composable(Route.About.path) { AboutScreen(nav) }
        composable(Route.Login.path) { LoginScreen(nav, repo) }
        composable(Route.SyncProgress.path) { SyncProgressScreen(nav, repo) }
        composable(Route.UserGuide.path) { UserGuideScreen(nav) }
        composable(Route.ManageDecks.path) { ManageDecksScreen(nav, repo) }
    }
}

@Composable
fun isLandscape(): Boolean = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

@Composable
private fun NavBar(nav: NavHostController, currentRoute: String) {
    val landscape = isLandscape()
    if (landscape) {
        NavigationBar(containerColor = DarkPanel, modifier = Modifier.height(56.dp)) {
            NavigationBarItem(icon = { Icon(Icons.Default.School, "To Study", Modifier.size(20.dp)) }, label = null, selected = currentRoute == Route.Active.path, onClick = { nav.navigate(Route.Active.path) { popUpTo(Route.Active.path) { inclusive = true } } })
            NavigationBarItem(icon = { Icon(Icons.Default.Help, "Unsure", Modifier.size(20.dp)) }, label = null, selected = currentRoute == Route.Unsure.path, onClick = { nav.navigate(Route.Unsure.path) })
            NavigationBarItem(icon = { Icon(Icons.Default.CheckCircle, "Learned", Modifier.size(20.dp)) }, label = null, selected = currentRoute == Route.Learned.path, onClick = { nav.navigate(Route.Learned.path) })
            NavigationBarItem(icon = { Icon(Icons.Default.List, "All", Modifier.size(20.dp)) }, label = null, selected = currentRoute == Route.AllCards.path, onClick = { nav.navigate(Route.AllCards.path) })
            NavigationBarItem(icon = { Icon(Icons.Default.Star, "Custom", Modifier.size(20.dp)) }, label = null, selected = currentRoute == Route.CustomSet.path, onClick = { nav.navigate(Route.CustomSet.path) })
            NavigationBarItem(icon = { Icon(Icons.Default.Settings, "More", Modifier.size(20.dp)) }, label = null, selected = currentRoute == Route.Settings.path || currentRoute == Route.Deleted.path || currentRoute == Route.Admin.path, onClick = { nav.navigate(Route.Settings.path) })
        }
    } else {
        NavigationBar(containerColor = DarkPanel) {
            NavigationBarItem(icon = { Icon(Icons.Default.School, "To Study") }, label = { Text("To Study", fontSize = 9.sp) }, selected = currentRoute == Route.Active.path, onClick = { nav.navigate(Route.Active.path) { popUpTo(Route.Active.path) { inclusive = true } } })
            NavigationBarItem(icon = { Icon(Icons.Default.Help, "Unsure") }, label = { Text("Unsure", fontSize = 9.sp) }, selected = currentRoute == Route.Unsure.path, onClick = { nav.navigate(Route.Unsure.path) })
            NavigationBarItem(icon = { Icon(Icons.Default.CheckCircle, "Learned") }, label = { Text("Learned", fontSize = 9.sp) }, selected = currentRoute == Route.Learned.path, onClick = { nav.navigate(Route.Learned.path) })
            NavigationBarItem(icon = { Icon(Icons.Default.List, "All") }, label = { Text("All", fontSize = 9.sp) }, selected = currentRoute == Route.AllCards.path, onClick = { nav.navigate(Route.AllCards.path) })
            NavigationBarItem(icon = { Icon(Icons.Default.Star, "Custom") }, label = { Text("Custom", fontSize = 9.sp) }, selected = currentRoute == Route.CustomSet.path, onClick = { nav.navigate(Route.CustomSet.path) })
            NavigationBarItem(icon = { Icon(Icons.Default.Settings, "More") }, label = { Text("More", fontSize = 9.sp) }, selected = currentRoute == Route.Settings.path || currentRoute == Route.Deleted.path || currentRoute == Route.Admin.path, onClick = { nav.navigate(Route.Settings.path) })
        }
    }
}

@Composable
private fun CountsRow(progress: ProgressState, allCards: List<FlashCard>) {
    val counts = remember(progress, allCards) {
        var a = 0; var u = 0; var l = 0
        allCards.forEach { card -> when (progress.getStatus(card.id)) { CardStatus.ACTIVE -> a++; CardStatus.UNSURE -> u++; CardStatus.LEARNED -> l++; CardStatus.DELETED -> {} } }
        Triple(a, u, l)
    }
    Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        Text("To Study: ${counts.first}", color = DarkMuted, fontSize = 11.sp)
        Text("Unsure: ${counts.second}", color = DarkMuted, fontSize = 11.sp)
        Text("Learned: ${counts.third}", color = DarkMuted, fontSize = 11.sp)
    }
}

// Status display name helper
private fun statusDisplayName(status: CardStatus): String = when(status) {
    CardStatus.ACTIVE -> "TO LEARN"
    CardStatus.UNSURE -> "UNSURE"
    CardStatus.LEARNED -> "LEARNED"
    CardStatus.DELETED -> "DELETED"
}

@Composable
fun FlipCard(card: FlashCard, breakdown: TermBreakdown?, showFront: Boolean, settings: StudySettings, onFlip: () -> Unit, onSwipeNext: () -> Unit, onSwipePrev: () -> Unit) {
    val rotation by animateFloatAsState(if (showFront) 0f else 180f, tween(260), label = "flip")
    val showFrontSide = rotation.absoluteValue <= 90f
    var dragTotal by remember { mutableStateOf(0f) }
    val landscape = isLandscape()
    val cardHeight = if (landscape) 220.dp else 260.dp  // Increased landscape height to fill space
    Card(
        modifier = Modifier.fillMaxWidth().height(cardHeight)
            .pointerInput(Unit) { detectHorizontalDragGestures(onDragStart = { dragTotal = 0f }, onHorizontalDrag = { _, d -> dragTotal += d }, onDragEnd = { if (dragTotal <= -90f) onSwipeNext(); if (dragTotal >= 90f) onSwipePrev(); dragTotal = 0f }) }
            .graphicsLayer { rotationY = rotation; cameraDistance = 12f * density }.clickable { onFlip() },
        colors = CardDefaults.cardColors(containerColor = DarkPanel), elevation = CardDefaults.cardElevation(6.dp)
    ) {
        Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
            val frontIsMeaning = settings.reverseCards
            val frontText = if (frontIsMeaning) card.meaning else card.term
            val backText = if (frontIsMeaning) card.term else card.meaning
            val isDefSide = if (settings.reverseCards) showFrontSide else !showFrontSide
            if (showFrontSide) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
                    Text(frontText, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, color = Color.White, fontSize = if (landscape) 18.sp else 22.sp)
                    if (!settings.reverseCards && !card.pron.isNullOrBlank()) { Spacer(Modifier.height(4.dp)); Text("(${card.pron})", color = DarkMuted, fontSize = 12.sp) }
                    Spacer(Modifier.height(4.dp))
                    if (settings.showGroup) AssistChip(onClick = {}, label = { Text(card.group, fontSize = 10.sp) })
                    if (settings.showSubgroup && !card.subgroup.isNullOrBlank()) Text(card.subgroup, fontSize = 11.sp, color = DarkMuted)
                    if (isDefSide && settings.showBreakdownOnDefinition && breakdown?.hasContent() == true) { Spacer(Modifier.height(6.dp)); BreakdownInline(breakdown, landscape) }
                    Spacer(Modifier.weight(1f)); Text("Tap to flip", color = DarkMuted, fontSize = 10.sp)
                }
            } else {
                Box(Modifier.graphicsLayer { rotationY = 180f }.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
                        Text(backText, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, color = Color.White, fontSize = if (landscape) 18.sp else 22.sp)
                        if (settings.reverseCards && !card.pron.isNullOrBlank()) { Spacer(Modifier.height(4.dp)); Text("(${card.pron})", color = DarkMuted, fontSize = 12.sp) }
                        if (settings.reverseCards) { Spacer(Modifier.height(4.dp)); if (settings.showGroup) AssistChip(onClick = {}, label = { Text(card.group, fontSize = 10.sp) }); if (settings.showSubgroup && !card.subgroup.isNullOrBlank()) Text(card.subgroup, fontSize = 11.sp, color = DarkMuted) }
                        if (isDefSide && settings.showBreakdownOnDefinition && breakdown?.hasContent() == true) { Spacer(Modifier.height(6.dp)); BreakdownInline(breakdown, landscape) }
                        Spacer(Modifier.weight(1f)); Text("Tap to flip", color = DarkMuted, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun BreakdownInline(breakdown: TermBreakdown, landscape: Boolean = false) {
    Column(Modifier.fillMaxWidth().background(DarkPanel2, RoundedCornerShape(6.dp)).padding(8.dp)) {
        if (breakdown.parts.isNotEmpty()) { Text("Breakdown", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = DarkMuted); Text(breakdown.formatParts(), fontSize = 11.sp, color = Color.White) }
        if (breakdown.literal.isNotBlank()) { if (breakdown.parts.isNotEmpty()) Spacer(Modifier.height(2.dp)); Text("Literal", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = DarkMuted); Text(breakdown.literal, fontSize = 11.sp, color = Color.White) }
    }
}

@Composable
fun BreakdownDialog(card: FlashCard, breakdown: TermBreakdown?, adminSettings: AdminSettings, onSave: (TermBreakdown) -> Unit, onAutoFill: (Boolean) -> Unit, onDismiss: () -> Unit) {
    var parts by remember(breakdown) { mutableStateOf(breakdown?.parts?.toMutableList() ?: mutableListOf()) }
    var literal by remember(breakdown) { mutableStateOf(breakdown?.literal ?: "") }
    var notes by remember(breakdown) { mutableStateOf(breakdown?.notes ?: "") }
    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth().padding(8.dp), colors = CardDefaults.cardColors(containerColor = DarkPanel)) {
            Column(Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
                Text("Breakdown: ${card.term}", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { parts = ChatGptHelper.autoSplitTerm(card.term).map { BreakdownPart(it, "") }.toMutableList() }, modifier = Modifier.weight(1f)) { Text("Auto-Split", fontSize = 11.sp) }
                    if (adminSettings.chatGptEnabled && adminSettings.chatGptApiKey.isNotBlank()) {
                        Button(onClick = { onAutoFill(true) }, modifier = Modifier.weight(1f)) { Text("AI Fill", fontSize = 11.sp) }
                    }
                }
                Spacer(Modifier.height(8.dp)); Text("Parts", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                parts.forEachIndexed { idx, part ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(part.part, { parts = parts.toMutableList().apply { this[idx] = part.copy(part = it) } }, Modifier.weight(1f), label = { Text("Part", fontSize = 10.sp) }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 12.sp))
                        OutlinedTextField(part.meaning, { parts = parts.toMutableList().apply { this[idx] = part.copy(meaning = it) } }, Modifier.weight(1f), label = { Text("Meaning", fontSize = 10.sp) }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 12.sp))
                        IconButton({ parts = parts.toMutableList().apply { removeAt(idx) } }, Modifier.size(32.dp)) { Icon(Icons.Default.Delete, "Remove", tint = DarkMuted, modifier = Modifier.size(18.dp)) }
                    }
                }
                TextButton({ parts = (parts + BreakdownPart("", "")).toMutableList() }) { Icon(Icons.Default.Add, "Add", Modifier.size(16.dp)); Text("Add part", fontSize = 11.sp) }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(literal, { literal = it }, Modifier.fillMaxWidth(), label = { Text("Literal meaning") }, textStyle = LocalTextStyle.current.copy(fontSize = 12.sp))
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("Notes") }, minLines = 2, textStyle = LocalTextStyle.current.copy(fontSize = 12.sp))
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onDismiss) { Text("Cancel") }; Spacer(Modifier.width(8.dp))
                    Button({ onSave(TermBreakdown(card.id, card.term, parts.filter { it.part.isNotBlank() || it.meaning.isNotBlank() }, literal, notes, System.currentTimeMillis() / 1000, null)) }) { Text("Save") }
                }
            }
        }
    }
}

private fun sortCards(cards: List<FlashCard>, sortMode: SortMode, randomize: Boolean): List<FlashCard> {
    if (randomize) return cards.shuffled()
    return when (sortMode) {
        SortMode.JSON_ORDER -> cards
        SortMode.ALPHABETICAL -> cards.sortedBy { it.term.lowercase() }
        SortMode.GROUP_ALPHA -> cards.sortedWith(compareBy({ it.group }, { it.subgroup ?: "" }, { it.term }))
        SortMode.GROUP_RANDOM -> { val groups = cards.map { it.group }.distinct().shuffled(); val order = groups.withIndex().associate { it.value to it.index }; cards.sortedBy { order[it.group] ?: 0 } }
        SortMode.RANDOM -> cards.shuffled()
    }
}

@Composable
private fun GroupFilterDropdown(groups: List<String>, selectedGroup: String?, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    OutlinedButton({ expanded = true }, modifier = Modifier.height(36.dp)) {
        Text(selectedGroup ?: "All Cards", fontSize = 10.sp, maxLines = 1)
        Icon(Icons.Default.ArrowDropDown, "Filter", Modifier.size(16.dp))
    }
    DropdownMenu(expanded, { expanded = false }) {
        DropdownMenuItem(text = { Text("All Cards", color = Color.White, fontSize = 12.sp) }, onClick = { onSelect(null); expanded = false })
        groups.forEach { g -> DropdownMenuItem(text = { Text(g, color = Color.White, fontSize = 12.sp) }, onClick = { onSelect(g); expanded = false }) }
    }
}

// Landscape study layout - shared by To Study, Unsure, Custom, Learned Study
@Composable
private fun LandscapeStudyLayout(
    title: String,
    cardCount: String,
    current: FlashCard?,
    currentBreakdown: TermBreakdown?,
    showFront: Boolean,
    settings: StudySettings,
    groups: List<String>,
    selectedGroup: String?,
    inCustomSet: Boolean,
    atEnd: Boolean,
    showSearch: Boolean,
    search: String,
    onSearchChange: (String) -> Unit,
    onFlip: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSpeak: () -> Unit,
    onGroupSelect: (String?) -> Unit,
    onPrimaryAction: () -> Unit,
    primaryActionText: String,
    onSecondaryAction: () -> Unit,
    secondaryActionText: String,
    onCustomToggle: () -> Unit,
    onBreakdown: () -> Unit,
    onReturnTop: () -> Unit,
    showGroupFilter: Boolean = true,
    onShuffle: (() -> Unit)? = null
) {
    var searchExpanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxSize().padding(8.dp)) {
        // Left: Card
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            if (current != null) {
                FlipCard(current, currentBreakdown, showFront, settings, onFlip, onNext, onPrev)
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No cards.", color = DarkMuted) }
            }
        }
        Spacer(Modifier.width(8.dp))
        // Right: Controls
        Column(Modifier.weight(0.6f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            // Title row with shuffle, search icon and group filter
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                Row {
                    if (onShuffle != null) {
                        IconButton(onShuffle, Modifier.size(32.dp)) { Icon(Icons.Default.Shuffle, "Shuffle", tint = AccentBlue, modifier = Modifier.size(20.dp)) }
                    }
                    IconButton({ searchExpanded = !searchExpanded }, Modifier.size(32.dp)) { Icon(Icons.Default.Search, "Search", tint = DarkMuted, modifier = Modifier.size(20.dp)) }
                    if (showGroupFilter) { GroupFilterDropdown(groups, selectedGroup, onGroupSelect) }
                }
            }
            if (searchExpanded) {
                OutlinedTextField(search, onSearchChange, Modifier.background(DarkPanel2, RoundedCornerShape(12.dp)).fillMaxWidth().height(48.dp), singleLine = true, placeholder = { Text("Search", fontSize = 10.sp) }, colors = OutlinedTextFieldDefaults.colors(), textStyle = LocalTextStyle.current.copy(fontSize = 11.sp),
                    trailingIcon = { if (search.isNotEmpty()) IconButton({ onSearchChange("") }, Modifier.size(20.dp)) { Icon(Icons.Default.Close, "Clear", tint = DarkMuted, modifier = Modifier.size(16.dp)) } }
                )
            }
            if (current != null) {
                Spacer(Modifier.height(4.dp)); Text(cardCount, color = DarkMuted, fontSize = 11.sp)
                Spacer(Modifier.height(4.dp))
                // Nav row
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    OutlinedButton(onPrev, modifier = Modifier.weight(1f).height(36.dp)) { Text("◀", fontSize = 14.sp) }
                    Spacer(Modifier.width(4.dp))
                    Button(onSpeak, modifier = Modifier.weight(1f).height(36.dp)) { Icon(Icons.Default.VolumeUp, "Speak", Modifier.size(18.dp)) }
                    Spacer(Modifier.width(4.dp))
                    OutlinedButton(onNext, modifier = Modifier.weight(1f).height(36.dp)) { Text("▶", fontSize = 14.sp) }
                }
                Spacer(Modifier.height(4.dp))
                // Action row
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(onPrimaryAction, Modifier.weight(1f).height(36.dp), colors = ButtonDefaults.buttonColors(containerColor = AccentGood)) { Text(primaryActionText, fontSize = 11.sp) }
                    if (settings.showCustomSetButton) {
                    IconButton(onCustomToggle, modifier = Modifier.size(36.dp).background(DarkPanel2, RoundedCornerShape(6.dp))) { Icon(if (inCustomSet) Icons.Default.Star else Icons.Default.StarBorder, "Custom", tint = if (inCustomSet) Color.Yellow else DarkMuted, modifier = Modifier.size(18.dp)) }
                    }
                    IconButton(onBreakdown, modifier = Modifier.size(36.dp).background(DarkPanel2, RoundedCornerShape(6.dp))) { Icon(Icons.Default.Extension, "Breakdown", tint = if (currentBreakdown?.hasContent() == true) AccentBlue else DarkMuted, modifier = Modifier.size(18.dp)) }
                    OutlinedButton(onSecondaryAction, Modifier.weight(1f).height(36.dp)) { Text(secondaryActionText, fontSize = 11.sp) }
                }
                if (atEnd) { Spacer(Modifier.height(4.dp)); OutlinedButton(onReturnTop, Modifier.fillMaxWidth().height(32.dp)) { Icon(Icons.Default.KeyboardArrowUp, "Top", Modifier.size(16.dp)); Text("Top", fontSize = 11.sp) } }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScreen(nav: NavHostController, repo: Repository, statusFilter: CardStatus) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tts = remember { TtsHelper(context) }
    DisposableEffect(Unit) { onDispose { tts.shutdown() } }
    val allCards by repo.activeCardsFlow().collectAsState(initial = emptyList())
    val progress by repo.progressFlow().collectAsState(initial = ProgressState.EMPTY)
    val breakdowns by repo.breakdownsFlow().collectAsState(initial = emptyMap())
    val customSet by repo.customSetFlow().collectAsState(initial = emptySet())
    val settings by repo.settingsAllFlow().collectAsState(initial = StudySettings())
    val adminSettings by repo.adminSettingsFlow().collectAsState(initial = AdminSettings())
    LaunchedEffect(Unit) { repo.refreshAdminStatus() }
    val groups = remember(allCards) { allCards.map { it.group }.distinct().sorted() }
    var search by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    var showFront by remember { mutableStateOf(true) }
    var index by remember { mutableStateOf(0) }
    var showBreakdown by remember { mutableStateOf(false) }
    val isActive = statusFilter == CardStatus.ACTIVE
    val title = if (isActive) "To Study" else "Unsure"
    val route = if (isActive) Route.Active.path else Route.Unsure.path
    val shouldRandomize = if (isActive) settings.randomizeUnlearned else settings.randomizeUnsure
    val landscape = isLandscape()
    var shuffleKey by remember { mutableStateOf(0) }  // Increment to force reshuffle
    val filteredCards = remember(allCards, progress, search, shouldRandomize, settings.sortMode, settings.studyFilterGroup, shuffleKey) {
        val base = allCards.filter { progress.getStatus(it.id) == statusFilter }
        val grouped = if (settings.studyFilterGroup != null) base.filter { it.group == settings.studyFilterGroup } else base
        val searched = if (search.isBlank()) grouped else grouped.filter { it.term.contains(search, true) || it.meaning.contains(search, true) || (it.pron?.contains(search, true) ?: false) }
        sortCards(searched, settings.sortMode, shouldRandomize || shuffleKey > 0)
    }
    LaunchedEffect(filteredCards.size) { if (filteredCards.isEmpty()) index = 0 else index = index.coerceIn(0, filteredCards.size - 1); showFront = true }
    val current = filteredCards.getOrNull(index)
    val currentBreakdown = current?.let { breakdowns[it.id] }
    val inCustomSet = current?.let { customSet.contains(it.id) } ?: false
    val atEnd = index >= filteredCards.size - 1 && filteredCards.isNotEmpty()
    
    // Auto-speak on card change
    LaunchedEffect(index, current?.id) {
        if (settings.autoSpeakOnCardChange && current != null) {
            tts.setRate(settings.speechRate)
            val text = if (settings.speakPronunciationOnly && !current.pron.isNullOrBlank()) current.pron else current.term
            tts.speak(text)
        }
    }
    
    // Speak definition when flipped to back
    LaunchedEffect(showFront) {
        if (!showFront && settings.speakDefinitionOnFlip && current != null) {
            tts.setRate(settings.speechRate)
            tts.speak(current.meaning)
        }
    }
    
    Scaffold(bottomBar = { NavBar(nav, route) }) { pad ->
        if (landscape) {
            Box(Modifier.fillMaxSize().padding(pad)) {
                LandscapeStudyLayout(
                    title = title,
                    cardCount = "Card ${index + 1} / ${filteredCards.size}",
                    current = current, currentBreakdown = currentBreakdown, showFront = showFront, settings = settings,
                    groups = groups, selectedGroup = settings.studyFilterGroup, inCustomSet = inCustomSet, atEnd = atEnd,
                    showSearch = true, search = search, onSearchChange = { search = it; index = 0; showFront = true },
                    onFlip = { showFront = !showFront },
                    onNext = { if (index < filteredCards.size - 1) { index++; showFront = true } },
                    onPrev = { if (index > 0) { index--; showFront = true } },
                    onSpeak = { tts.setRate(settings.speechRate); val text = if (settings.speakPronunciationOnly && !current?.pron.isNullOrBlank()) current?.pron ?: "" else current?.term ?: ""; tts.speak(text) },
                    onGroupSelect = { scope.launch { repo.saveSettingsAll(settings.copy(studyFilterGroup = it)) } },
                    onPrimaryAction = { scope.launch { current?.let { repo.setStatus(it.id, CardStatus.LEARNED); if (index >= filteredCards.size - 1) index = (filteredCards.size - 2).coerceAtLeast(0); showFront = true } } },
                    primaryActionText = "✓",
                    onSecondaryAction = { scope.launch { current?.let { val newStatus = if (isActive) CardStatus.UNSURE else CardStatus.ACTIVE; repo.setStatus(it.id, newStatus); if (index >= filteredCards.size - 1) index = (filteredCards.size - 2).coerceAtLeast(0); showFront = true } } },
                    secondaryActionText = if (isActive) "?" else "↺",
                    onCustomToggle = { scope.launch { current?.let { if (inCustomSet) repo.removeFromCustomSet(it.id) else repo.addToCustomSet(it.id) } } },
                    onBreakdown = { showBreakdown = true },
                    onReturnTop = { index = 0; showFront = true },
                    onShuffle = { shuffleKey++; index = 0; showFront = true }
                )
            }
        } else {
            Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 12.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                // Header row (title + shuffle + search + group filter)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton({ shuffleKey++; index = 0; showFront = true }) { Icon(Icons.Default.Shuffle, "Shuffle", tint = AccentBlue) }
                        IconButton({ searchExpanded = !searchExpanded }) { Icon(Icons.Default.Search, "Search", tint = DarkMuted) }
                        GroupFilterDropdown(groups, settings.studyFilterGroup) { scope.launch { repo.saveSettingsAll(settings.copy(studyFilterGroup = it)) } }
                    }
                }
                if (searchExpanded) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it; index = 0; showFront = true },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search...", color = DarkMuted, fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(),
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                        trailingIcon = { if (search.isNotEmpty()) IconButton({ search = ""; index = 0; showFront = true }) { Icon(Icons.Default.Close, "Clear", tint = DarkMuted) } }
                    )
                    Spacer(Modifier.height(6.dp))
                } else {
                    Spacer(Modifier.height(6.dp))
                }
                CountsRow(progress, allCards)
                Box(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (current == null) {
                            Text("No ${title.lowercase()} cards.", color = DarkMuted)
                        } else {
                            Text("Card ${index + 1} / ${filteredCards.size}", color = DarkMuted, fontSize = 12.sp); Spacer(Modifier.height(4.dp))
                            FlipCard(current, currentBreakdown, showFront, settings, { showFront = !showFront }, { if (index < filteredCards.size - 1) { index++; showFront = true } }, { if (index > 0) { index--; showFront = true } })
                            Spacer(Modifier.height(6.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                OutlinedButton({ if (index > 0) { index--; showFront = true } }, modifier = Modifier.weight(1f)) { Text("Prev") }; Spacer(Modifier.width(6.dp))
                                Button({ tts.setRate(settings.speechRate); val textToSpeak = if (settings.speakPronunciationOnly) current?.pron ?: "" else current?.term ?: ""; tts.speak(textToSpeak) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.VolumeUp, "Speak") }; Spacer(Modifier.width(6.dp))
                                OutlinedButton({ if (index < filteredCards.size - 1) { index++; showFront = true } }, modifier = Modifier.weight(1f)) { Text("Next") }
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Button({ scope.launch { repo.setStatus(current.id, CardStatus.LEARNED); if (index >= filteredCards.size - 1) index = (filteredCards.size - 2).coerceAtLeast(0); showFront = true } }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = AccentGood)) { Text("Got it ✓") }
                                if (settings.showCustomSetButton) {
                                    IconButton({ scope.launch { if (inCustomSet) repo.removeFromCustomSet(current.id) else repo.addToCustomSet(current.id) } }) { Icon(if (inCustomSet) Icons.Default.Star else Icons.Default.StarBorder, "Custom", tint = if (inCustomSet) Color.Yellow else DarkMuted) }
                                }
                                IconButton({ showBreakdown = true }) { Icon(Icons.Default.Extension, "Breakdown", tint = if (currentBreakdown?.hasContent() == true) AccentBlue else DarkMuted) }
                                OutlinedButton({ scope.launch { val nextStatus = if (isActive) CardStatus.UNSURE else CardStatus.ACTIVE; repo.setStatus(current.id, nextStatus); if (index >= filteredCards.size - 1) index = (filteredCards.size - 2).coerceAtLeast(0); showFront = true } }, modifier = Modifier.weight(1f)) { Text(if (isActive) "Unsure" else "Relearn") }
                            }
                            if (atEnd) { Spacer(Modifier.height(6.dp)); OutlinedButton({ index = 0; showFront = true }, Modifier.fillMaxWidth()) { Icon(Icons.Default.KeyboardArrowUp, "Top"); Spacer(Modifier.width(4.dp)); Text("Return to Top") } }
                        }
                    }
                    // Tap outside search to collapse, but keep results filtered
                    if (searchExpanded) {
                        Box(Modifier.matchParentSize().pointerInput(Unit) { detectTapGestures(onTap = { searchExpanded = false }) })
                    }
                }
            }
        }
    }
    if (showBreakdown && current != null) {
        BreakdownDialog(current, currentBreakdown, adminSettings, { scope.launch { repo.saveBreakdown(it) }; showBreakdown = false }, { useAI -> scope.launch { val bd = repo.autoFillBreakdown(current.id, current.term, useAI); repo.saveBreakdown(bd) }; showBreakdown = false }, { showBreakdown = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearnedScreen(nav: NavHostController, repo: Repository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tts = remember { TtsHelper(context) }
    DisposableEffect(Unit) { onDispose { tts.shutdown() } }
    val allCards by repo.activeCardsFlow().collectAsState(initial = emptyList())
    val progress by repo.progressFlow().collectAsState(initial = ProgressState.EMPTY)
    val breakdowns by repo.breakdownsFlow().collectAsState(initial = emptyMap())
    val customSet by repo.customSetFlow().collectAsState(initial = emptySet())
    val settings by repo.settingsAllFlow().collectAsState(initial = StudySettings())
    val adminSettings by repo.adminSettingsFlow().collectAsState(initial = AdminSettings())
    LaunchedEffect(Unit) { repo.refreshAdminStatus() }
    val groups = remember(allCards) { allCards.map { it.group }.distinct().sorted() }
    var viewMode by remember { mutableStateOf(LearnedViewMode.LIST) }
    var search by remember { mutableStateOf("") }
    var showFront by remember { mutableStateOf(true) }
    var index by remember { mutableStateOf(0) }
    var showBreakdown by remember { mutableStateOf(false) }
    var breakdownCard by remember { mutableStateOf<FlashCard?>(null) }
    val landscape = isLandscape()
    val learnedCards = remember(allCards, progress, search, viewMode, settings) {
        val base = allCards.filter { progress.getStatus(it.id) == CardStatus.LEARNED }
        val grouped = if (viewMode == LearnedViewMode.STUDY && settings.studyFilterGroup != null) base.filter { it.group == settings.studyFilterGroup } else base
        val searched = if (search.isBlank()) grouped else grouped.filter { it.term.contains(search, true) || it.meaning.contains(search, true) }
        if (viewMode == LearnedViewMode.STUDY && settings.randomizeLearnedStudy) searched.shuffled() else sortCards(searched, settings.sortMode, false)
    }
    LaunchedEffect(learnedCards.size) { if (learnedCards.isEmpty()) index = 0 else index = index.coerceIn(0, learnedCards.size - 1); showFront = true }
    val current = learnedCards.getOrNull(index)
    val currentBreakdown = current?.let { breakdowns[it.id] }
    val inCustomSet = current?.let { customSet.contains(it.id) } ?: false
    val atEnd = index >= learnedCards.size - 1 && learnedCards.isNotEmpty()
    
    // Auto-speak on card change (Learned Study mode)
    LaunchedEffect(index, current?.id, viewMode) {
        if (viewMode == LearnedViewMode.STUDY && settings.autoSpeakOnCardChange && current != null) {
            tts.setRate(settings.speechRate)
            val text = if (settings.speakPronunciationOnly && !current.pron.isNullOrBlank()) current.pron else current.term
            tts.speak(text)
        }
    }
    
    // Speak definition when flipped to back (Learned Study mode)
    LaunchedEffect(showFront, viewMode) {
        if (viewMode == LearnedViewMode.STUDY && !showFront && settings.speakDefinitionOnFlip && current != null) {
            tts.setRate(settings.speechRate)
            tts.speak(current.meaning)
        }
    }
    
    Scaffold(bottomBar = { NavBar(nav, Route.Learned.path) }) { pad ->
        var searchExpanded by remember { mutableStateOf(false) }
        if (landscape && viewMode == LearnedViewMode.STUDY) {
            Box(Modifier.fillMaxSize().padding(pad)) {
                LandscapeStudyLayout(
                    title = "Learned", cardCount = "Card ${index + 1} / ${learnedCards.size}",
                    current = current, currentBreakdown = currentBreakdown, showFront = showFront, settings = settings,
                    groups = groups, selectedGroup = settings.studyFilterGroup, inCustomSet = inCustomSet, atEnd = atEnd,
                    showSearch = true, search = search, onSearchChange = { search = it; index = 0; showFront = true },
                    onFlip = { showFront = !showFront },
                    onNext = { if (index < learnedCards.size - 1) { index++; showFront = true } },
                    onPrev = { if (index > 0) { index--; showFront = true } },
                    onSpeak = { tts.setRate(settings.speechRate); val text = if (settings.speakPronunciationOnly && !current?.pron.isNullOrBlank()) current?.pron ?: "" else current?.term ?: ""; tts.speak(text) },
                    onGroupSelect = { scope.launch { repo.saveSettingsAll(settings.copy(studyFilterGroup = it)) } },
                    onPrimaryAction = { scope.launch { current?.let { repo.setStatus(it.id, CardStatus.ACTIVE) } } },
                    primaryActionText = "Relearn",
                    onSecondaryAction = { scope.launch { current?.let { repo.setStatus(it.id, CardStatus.UNSURE) } } },
                    secondaryActionText = "Unsure",
                    onCustomToggle = { scope.launch { current?.let { if (inCustomSet) repo.removeFromCustomSet(it.id) else repo.addToCustomSet(it.id) } } },
                    onBreakdown = { showBreakdown = true; breakdownCard = current },
                    onReturnTop = { index = 0; showFront = true }
                )
            }
        } else {
            Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 12.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                // Header row matching Unsure pattern: title + List/Study chips + search icon + group filter
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (viewMode == LearnedViewMode.LIST) "Learned List" else "Learned Study", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                        Spacer(Modifier.width(8.dp))
                        FilterChip(viewMode == LearnedViewMode.LIST, { viewMode = LearnedViewMode.LIST }, { Text("List", fontSize = 9.sp) }, modifier = Modifier.height(28.dp))
                        Spacer(Modifier.width(4.dp))
                        FilterChip(viewMode == LearnedViewMode.STUDY, { viewMode = LearnedViewMode.STUDY }, { Text("Study", fontSize = 9.sp) }, modifier = Modifier.height(28.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton({ searchExpanded = !searchExpanded }) { Icon(Icons.Default.Search, "Search", tint = DarkMuted) }
                        if (viewMode == LearnedViewMode.STUDY) { GroupFilterDropdown(groups, settings.studyFilterGroup) { scope.launch { repo.saveSettingsAll(settings.copy(studyFilterGroup = it)) } } }
                    }
                }
                if (searchExpanded) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it; index = 0 },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search...", color = DarkMuted, fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(),
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                        trailingIcon = { if (search.isNotEmpty()) IconButton({ search = "" }) { Icon(Icons.Default.Close, "Clear", tint = DarkMuted) } }
                    )
                    Spacer(Modifier.height(6.dp))
                }
                // Show counts row
                CountsRow(progress, allCards)
                Spacer(Modifier.height(6.dp))
                if (viewMode == LearnedViewMode.LIST) {
                    if (learnedCards.isEmpty()) Text("Nothing learned yet.", color = DarkMuted)
                    else LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(learnedCards, key = { it.id }) { c ->
                            val bd = breakdowns[c.id]
                            Card(colors = CardDefaults.cardColors(containerColor = DarkPanel), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(10.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(c.term, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                                        IconButton({ breakdownCard = c; showBreakdown = true }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Extension, "Breakdown", tint = if (bd?.hasContent() == true) AccentBlue else DarkMuted, modifier = Modifier.size(18.dp)) }
                                    }
                                    if (!c.pron.isNullOrBlank()) Text("(${c.pron})", fontSize = 10.sp, color = DarkMuted)
                                    if (settings.showDefinitionsInLearnedList) Text(c.meaning, fontSize = 12.sp, color = Color.White)
                                    if (settings.showLearnedListGroupLabel) Text("${c.group}${c.subgroup?.let { " • $it" } ?: ""}", fontSize = 10.sp, color = DarkMuted)
                                    if (settings.showBreakdownOnDefinition && bd?.hasContent() == true) { Spacer(Modifier.height(4.dp)); BreakdownInline(bd) }
                                    if (settings.showRelearnUnsureButtonsInLearnedList) { Spacer(Modifier.height(4.dp)); Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        OutlinedButton({ scope.launch { repo.setStatus(c.id, CardStatus.ACTIVE) } }, Modifier.height(32.dp)) { Text("Relearn", fontSize = 10.sp) }
                                        OutlinedButton({ scope.launch { repo.setStatus(c.id, CardStatus.UNSURE) } }, Modifier.height(32.dp)) { Text("Unsure", fontSize = 10.sp) } } }
                                }
                            }
                        }
                    }
                } else {
                    if (current == null) Text("No learned cards.", color = DarkMuted)
                    else {
                        Text("Card ${index + 1} / ${learnedCards.size}", color = DarkMuted, fontSize = 12.sp); Spacer(Modifier.height(4.dp))
                        FlipCard(current, currentBreakdown, showFront, settings, { showFront = !showFront }, { if (index < learnedCards.size - 1) { index++; showFront = true } }, { if (index > 0) { index--; showFront = true } })
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            OutlinedButton({ if (index > 0) { index--; showFront = true } }, enabled = index > 0, modifier = Modifier.weight(1f)) { Text("Prev") }; Spacer(Modifier.width(6.dp))
                            Button({ tts.setRate(settings.speechRate); val text = if (settings.speakPronunciationOnly && !current.pron.isNullOrBlank()) current.pron else current.term; tts.speak(text) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.VolumeUp, "Speak") }; Spacer(Modifier.width(6.dp))
                            OutlinedButton({ if (index < learnedCards.size - 1) { index++; showFront = true } }, enabled = index < learnedCards.size - 1, modifier = Modifier.weight(1f)) { Text("Next") }
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton({ scope.launch { repo.setStatus(current.id, CardStatus.ACTIVE) } }, Modifier.weight(1f)) { Text("Relearn") }
                                if (settings.showCustomSetButton) {
                            IconButton({ scope.launch { if (inCustomSet) repo.removeFromCustomSet(current.id) else repo.addToCustomSet(current.id) } }, modifier = Modifier.size(44.dp).background(DarkPanel2, RoundedCornerShape(8.dp))) { Icon(if (inCustomSet) Icons.Default.Star else Icons.Default.StarBorder, "Custom", tint = if (inCustomSet) Color.Yellow else DarkMuted) }
                                }
                            IconButton({ showBreakdown = true; breakdownCard = current }, modifier = Modifier.size(44.dp).background(DarkPanel2, RoundedCornerShape(8.dp))) { Icon(Icons.Default.Extension, "Breakdown", tint = if (currentBreakdown?.hasContent() == true) AccentBlue else DarkMuted) }
                            OutlinedButton({ scope.launch { repo.setStatus(current.id, CardStatus.UNSURE) } }, Modifier.weight(1f)) { Text("Unsure") }
                        }
                        if (atEnd) { Spacer(Modifier.height(6.dp)); OutlinedButton({ index = 0; showFront = true }, Modifier.fillMaxWidth()) { Icon(Icons.Default.KeyboardArrowUp, "Top"); Spacer(Modifier.width(4.dp)); Text("Return to Top") } }
                    }
                }
            }
        }
    }
    if (showBreakdown && breakdownCard != null) { BreakdownDialog(breakdownCard!!, breakdowns[breakdownCard!!.id], adminSettings, { scope.launch { repo.saveBreakdown(it) }; showBreakdown = false }, { useAI -> scope.launch { val bd = repo.autoFillBreakdown(breakdownCard!!.id, breakdownCard!!.term, useAI); repo.saveBreakdown(bd) }; showBreakdown = false }, { showBreakdown = false }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllCardsScreen(nav: NavHostController, repo: Repository) {
    val scope = rememberCoroutineScope()
    val allCards by repo.activeCardsFlow().collectAsState(initial = emptyList())
    val progress by repo.progressFlow().collectAsState(initial = ProgressState.EMPTY)
    val breakdowns by repo.breakdownsFlow().collectAsState(initial = emptyMap())
    val customSet by repo.customSetFlow().collectAsState(initial = emptySet())
    val settings by repo.settingsAllFlow().collectAsState(initial = StudySettings())
    val adminSettings by repo.adminSettingsFlow().collectAsState(initial = AdminSettings())
    LaunchedEffect(Unit) { repo.refreshAdminStatus() }
    var search by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    var showBreakdown by remember { mutableStateOf(false) }
    var breakdownCard by remember { mutableStateOf<FlashCard?>(null) }
    var showGroupFilter by remember { mutableStateOf(false) }
    val groups = remember(allCards) { listOf("All Groups") + allCards.map { it.group }.distinct().sorted() }
    val landscape = isLandscape()
    val displayedCards = remember(allCards, progress, search, settings.filterGroup, settings.sortMode) {
        val visible = allCards.filter { progress.getStatus(it.id) != CardStatus.DELETED }
        val grouped = if (settings.filterGroup != null) visible.filter { it.group == settings.filterGroup } else visible
        val searched = if (search.isBlank()) grouped else grouped.filter { it.term.contains(search, true) || it.meaning.contains(search, true) }
        sortCards(searched, settings.sortMode, false)
    }
    
    Scaffold(bottomBar = { NavBar(nav, Route.AllCards.path) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 12.dp, vertical = 4.dp)) {
            // Header row matching Unsure pattern: title + search icon + group filter
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("All Cards", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ searchExpanded = !searchExpanded }) { Icon(Icons.Default.Search, "Search", tint = DarkMuted) }
                    OutlinedButton({ showGroupFilter = true }, Modifier.height(32.dp)) { Text(settings.filterGroup ?: "All Groups", fontSize = 10.sp); Icon(Icons.Default.ArrowDropDown, "Filter", Modifier.size(16.dp)) }
                    DropdownMenu(showGroupFilter, { showGroupFilter = false }) { groups.forEach { g -> DropdownMenuItem(text = { Text(g, color = Color.White, fontSize = 12.sp) }, onClick = { scope.launch { repo.saveSettingsAll(settings.copy(filterGroup = if (g == "All Groups") null else g)) }; showGroupFilter = false }) } }
                }
            }
            // Search field (shown when expanded)
            if (searchExpanded) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search...", color = DarkMuted, fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(),
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                    trailingIcon = { if (search.isNotEmpty()) IconButton({ search = "" }) { Icon(Icons.Default.Close, "Clear", tint = DarkMuted) } }
                )
                Spacer(Modifier.height(6.dp))
            }
            // Counts row and total
            CountsRow(progress, allCards)
            Text("Total: ${displayedCards.size}", color = DarkMuted, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(displayedCards, key = { it.id }) { c ->
                    val status = progress.getStatus(c.id); val bd = breakdowns[c.id]; val inCustomSet = customSet.contains(c.id)
                    Card(colors = CardDefaults.cardColors(containerColor = DarkPanel), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(c.term, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                if (settings.showCustomSetButton) {
                                IconButton({ scope.launch { if (inCustomSet) repo.removeFromCustomSet(c.id) else repo.addToCustomSet(c.id) } }, modifier = Modifier.size(28.dp)) { Icon(if (inCustomSet) Icons.Default.Star else Icons.Default.StarBorder, "Custom", tint = if (inCustomSet) Color.Yellow else DarkMuted, modifier = Modifier.size(18.dp)) }
                                }
                                IconButton({ breakdownCard = c; showBreakdown = true }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Extension, "Breakdown", tint = if (bd?.hasContent() == true) AccentBlue else DarkMuted, modifier = Modifier.size(18.dp)) }
                                AssistChip(onClick = {}, label = { Text(statusDisplayName(status), fontSize = 8.sp) })
                            }
                            if (settings.showDefinitionsInAllList) Text(c.meaning, fontSize = 12.sp, color = Color.White)
                            Text("${c.group}${c.subgroup?.let { " • $it" } ?: ""}", fontSize = 10.sp, color = DarkMuted)
                            if (settings.showBreakdownOnDefinition && bd?.hasContent() == true) { Spacer(Modifier.height(4.dp)); BreakdownInline(bd) }
                            if (settings.showUnlearnedUnsureButtonsInAllList) { 
                                Spacer(Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                    if (status != CardStatus.ACTIVE) OutlinedButton({ scope.launch { repo.setStatus(c.id, CardStatus.ACTIVE) } }, Modifier.height(28.dp), contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Relearn", fontSize = 9.sp) }
                                    if (status != CardStatus.UNSURE) OutlinedButton({ scope.launch { repo.setStatus(c.id, CardStatus.UNSURE) } }, Modifier.height(28.dp), contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Unsure", fontSize = 9.sp) }
                                    if (status != CardStatus.LEARNED) Button({ scope.launch { repo.setStatus(c.id, CardStatus.LEARNED) } }, Modifier.height(28.dp), colors = ButtonDefaults.buttonColors(containerColor = AccentGood), contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Learned", fontSize = 9.sp) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showBreakdown && breakdownCard != null) { BreakdownDialog(breakdownCard!!, breakdowns[breakdownCard!!.id], adminSettings, { scope.launch { repo.saveBreakdown(it) }; showBreakdown = false }, { useAI -> scope.launch { val bd = repo.autoFillBreakdown(breakdownCard!!.id, breakdownCard!!.term, useAI); repo.saveBreakdown(bd) }; showBreakdown = false }, { showBreakdown = false }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomSetScreen(nav: NavHostController, repo: Repository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tts = remember { TtsHelper(context) }
    DisposableEffect(Unit) { onDispose { tts.shutdown() } }
    val allCards by repo.activeCardsFlow().collectAsState(initial = emptyList())
    val progress by repo.progressFlow().collectAsState(initial = ProgressState.EMPTY)
    val breakdowns by repo.breakdownsFlow().collectAsState(initial = emptyMap())
    val customSet by repo.customSetFlow().collectAsState(initial = emptySet())
    val customSetStatus by repo.customSetStatusFlow().collectAsState(initial = emptyMap())
    val settings by repo.settingsAllFlow().collectAsState(initial = StudySettings())
    val adminSettings by repo.adminSettingsFlow().collectAsState(initial = AdminSettings())
    LaunchedEffect(Unit) { repo.refreshAdminStatus() }
    var search by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    var showFront by remember { mutableStateOf(true) }
    var index by remember { mutableStateOf(0) }
    var showBreakdown by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showRemoveConfirm by remember { mutableStateOf(false) }
    val landscape = isLandscape()
    
    // Custom status view mode: ALL, UNSURE, LEARNED within custom set
    var customViewMode by remember { mutableStateOf("ALL") }
    
    // Filter cards based on custom set membership AND custom status view mode
    val customCards = remember(allCards, customSet, customSetStatus, search, settings, customViewMode) {
        val inSet = allCards.filter { customSet.contains(it.id) }
        val filtered = when (customViewMode) {
            "UNSURE" -> inSet.filter { customSetStatus[it.id] == CustomCardStatus.UNSURE }
            "LEARNED" -> inSet.filter { customSetStatus[it.id] == CustomCardStatus.LEARNED }
            else -> inSet // "ALL" shows all cards in custom set
        }
        val searched = if (search.isBlank()) filtered else filtered.filter { it.term.contains(search, true) || it.meaning.contains(search, true) }
        val cs = settings.customSetSettings
        sortCards(searched, cs.sortMode, cs.randomOrder)
    }
    
    // Calculate custom set status counts (isolated from main deck)
    val customCounts = remember(customSet, customSetStatus) {
        var active = 0; var unsure = 0; var learned = 0
        customSet.forEach { id ->
            when (customSetStatus[id]) {
                CustomCardStatus.UNSURE -> unsure++
                CustomCardStatus.LEARNED -> learned++
                else -> active++
            }
        }
        Triple(active, unsure, learned)
    }
    
    LaunchedEffect(customCards.size) { if (customCards.isEmpty()) index = 0 else index = index.coerceIn(0, customCards.size - 1); showFront = true }
    val current = customCards.getOrNull(index)
    val currentBreakdown = current?.let { breakdowns[it.id] }
    val atEnd = index >= customCards.size - 1 && customCards.isNotEmpty()
    val cs = settings.customSetSettings
    
    // Auto-speak on card change (Custom Set)
    LaunchedEffect(index, current?.id) {
        if (settings.autoSpeakOnCardChange && current != null) {
            tts.setRate(settings.speechRate)
            val text = if (settings.speakPronunciationOnly && !current.pron.isNullOrBlank()) current.pron else current.term
            tts.speak(text)
        }
    }
    
    // Speak definition when flipped to back (Custom Set)
    LaunchedEffect(showFront) {
        if (!showFront && settings.speakDefinitionOnFlip && current != null) {
            tts.setRate(settings.speechRate)
            tts.speak(current.meaning)
        }
    }
    
    // Function to handle status change with optional reflection to main decks
    fun handleStatusChange(cardId: String, newStatus: CustomCardStatus) {
        scope.launch {
            repo.setCustomSetStatus(cardId, newStatus)
            // If reflect setting is on, also update main deck status
            if (cs.reflectInMainDecks) {
                val mainStatus = when (newStatus) {
                    CustomCardStatus.LEARNED -> CardStatus.LEARNED
                    CustomCardStatus.UNSURE -> CardStatus.UNSURE
                    CustomCardStatus.ACTIVE -> CardStatus.ACTIVE
                }
                repo.setStatus(cardId, mainStatus)
            }
        }
    }
    
    Scaffold(bottomBar = { NavBar(nav, Route.CustomSet.path) }) { pad ->
        if (landscape) {
            // Landscape layout with Unsure button instead of Remove
            Box(Modifier.fillMaxSize().padding(pad)) {
                Row(Modifier.fillMaxSize().padding(8.dp)) {
                    // Left: Card
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (current != null) {
                            FlipCard(current, currentBreakdown, showFront, settings, { showFront = !showFront }, 
                                { if (index < customCards.size - 1) { index++; showFront = true } }, 
                                { if (index > 0) { index--; showFront = true } })
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { 
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("No cards in custom set.", color = DarkMuted)
                                    Text("Add from All tab via ☆", color = DarkMuted, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    // Right: Controls
                    Column(Modifier.weight(0.6f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                        // Title row with search icon and settings
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Custom", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                            Row {
                                IconButton({ searchExpanded = !searchExpanded }, Modifier.size(32.dp)) { Icon(Icons.Default.Search, "Search", tint = DarkMuted, modifier = Modifier.size(20.dp)) }
                                IconButton({ showSettings = true }, Modifier.size(32.dp)) { Icon(Icons.Default.Settings, "Settings", tint = DarkMuted, modifier = Modifier.size(20.dp)) }
                                if (current != null) { IconButton({ showRemoveConfirm = true }, Modifier.size(32.dp)) { Icon(Icons.Default.Delete, "Remove", tint = DarkMuted, modifier = Modifier.size(20.dp)) } }
                            }
                        }
                        if (searchExpanded) {
                            OutlinedTextField(search, { search = it; index = 0; showFront = true }, Modifier.background(DarkPanel2, RoundedCornerShape(12.dp)).fillMaxWidth().height(48.dp), singleLine = true, placeholder = { Text("Search", fontSize = 10.sp) }, colors = OutlinedTextFieldDefaults.colors(), textStyle = LocalTextStyle.current.copy(fontSize = 11.sp),
                                trailingIcon = { if (search.isNotEmpty()) IconButton({ search = "" }, Modifier.size(20.dp)) { Icon(Icons.Default.Close, "Clear", tint = DarkMuted, modifier = Modifier.size(16.dp)) } }
                            )
                        }
                        // Custom status counts row (clickable to filter)
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            TextButton({ customViewMode = "ALL" }) { Text("Custom: ${customCounts.first}", color = if (customViewMode == "ALL") AccentBlue else DarkMuted, fontSize = 10.sp) }
                            TextButton({ customViewMode = "UNSURE" }) { Text("Unsure: ${customCounts.second}", color = if (customViewMode == "UNSURE") AccentBlue else DarkMuted, fontSize = 10.sp) }
                            TextButton({ customViewMode = "LEARNED" }) { Text("Learned: ${customCounts.third}", color = if (customViewMode == "LEARNED") AccentBlue else DarkMuted, fontSize = 10.sp) }
                        }
                        if (current != null) {
                            Spacer(Modifier.height(4.dp)); Text("Card ${index + 1} / ${customCards.size}", color = DarkMuted, fontSize = 11.sp)
                            Spacer(Modifier.height(4.dp))
                            // Nav row
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                OutlinedButton({ if (index > 0) { index--; showFront = true } }, modifier = Modifier.weight(1f).height(36.dp)) { Text("◀", fontSize = 14.sp) }
                                Spacer(Modifier.width(4.dp))
                                Button({ tts.setRate(settings.speechRate); val text = if (settings.speakPronunciationOnly && !current.pron.isNullOrBlank()) current.pron else current.term; tts.speak(text) }, modifier = Modifier.weight(1f).height(36.dp)) { Icon(Icons.Default.VolumeUp, "Speak", Modifier.size(18.dp)) }
                                Spacer(Modifier.width(4.dp))
                                OutlinedButton({ if (index < customCards.size - 1) { index++; showFront = true } }, modifier = Modifier.weight(1f).height(36.dp)) { Text("▶", fontSize = 14.sp) }
                            }
                            Spacer(Modifier.height(4.dp))
                            // Action row - Got it + Unsure (changed from Remove)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Button({ handleStatusChange(current.id, CustomCardStatus.LEARNED); if (index >= customCards.size - 1) index = (customCards.size - 2).coerceAtLeast(0); showFront = true }, Modifier.weight(1f).height(36.dp), colors = ButtonDefaults.buttonColors(containerColor = AccentGood)) { Text("✓", fontSize = 14.sp) }
                                IconButton({ showBreakdown = true }, modifier = Modifier.size(36.dp).background(DarkPanel2, RoundedCornerShape(6.dp))) { Icon(Icons.Default.Extension, "Breakdown", tint = if (currentBreakdown?.hasContent() == true) AccentBlue else DarkMuted, modifier = Modifier.size(18.dp)) }
                                OutlinedButton({ handleStatusChange(current.id, CustomCardStatus.UNSURE); if (index >= customCards.size - 1) index = (customCards.size - 2).coerceAtLeast(0); showFront = true }, Modifier.weight(1f).height(36.dp)) { Text("Unsure", fontSize = 11.sp) }
                            }
                            if (atEnd) { Spacer(Modifier.height(4.dp)); OutlinedButton({ index = 0; showFront = true }, Modifier.fillMaxWidth().height(32.dp)) { Icon(Icons.Default.KeyboardArrowUp, "Top", Modifier.size(16.dp)); Text("Top", fontSize = 11.sp) } }
                        }
                    }
                }
            }
        } else {
            // Portrait layout
            Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 12.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                // Header row matching Unsure pattern: title + Card count + icons
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Custom", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Card ${if (current != null) "${index + 1}/${customCards.size}" else "0/0"}", color = DarkMuted, fontSize = 11.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton({ searchExpanded = !searchExpanded }) { Icon(Icons.Default.Search, "Search", tint = DarkMuted) }
                        IconButton({ showSettings = true }) { Icon(Icons.Default.Settings, "Settings", tint = DarkMuted) }
                        if (current != null) { IconButton({ showRemoveConfirm = true }) { Icon(Icons.Default.Delete, "Remove", tint = DarkMuted) } }
                    }
                }
                // Custom status counts row (clickable to filter) - increased height
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    TextButton({ customViewMode = "ALL" }, Modifier.height(32.dp)) { Text("Custom: ${customCounts.first}", color = if (customViewMode == "ALL") AccentBlue else DarkMuted, fontSize = 11.sp) }
                    TextButton({ customViewMode = "UNSURE" }, Modifier.height(32.dp)) { Text("Unsure: ${customCounts.second}", color = if (customViewMode == "UNSURE") AccentBlue else DarkMuted, fontSize = 11.sp) }
                    TextButton({ customViewMode = "LEARNED" }, Modifier.height(32.dp)) { Text("Learned: ${customCounts.third}", color = if (customViewMode == "LEARNED") AccentBlue else DarkMuted, fontSize = 11.sp) }
                }
                Spacer(Modifier.height(4.dp))
                if (searchExpanded) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it; index = 0; showFront = true },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search...", color = DarkMuted, fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(),
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                        trailingIcon = { if (search.isNotEmpty()) IconButton({ search = "" }) { Icon(Icons.Default.Close, "Clear", tint = DarkMuted) } }
                    )
                    Spacer(Modifier.height(6.dp))
                }
                Box(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (current == null) { 
                            Text("No cards in custom set.", color = DarkMuted)
                            Text("Add from All tab via ☆", color = DarkMuted, fontSize = 11.sp)
                        } else {
                            FlipCard(current, currentBreakdown, showFront, settings, { showFront = !showFront }, { if (index < customCards.size - 1) { index++; showFront = true } }, { if (index > 0) { index--; showFront = true } })
                            Spacer(Modifier.height(6.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                OutlinedButton({ if (index > 0) { index--; showFront = true } }, enabled = index > 0, modifier = Modifier.weight(1f)) { Text("Prev") }; Spacer(Modifier.width(6.dp))
                                Button({ tts.setRate(settings.speechRate); val text = if (settings.speakPronunciationOnly && !current.pron.isNullOrBlank()) current.pron else current.term; tts.speak(text) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.VolumeUp, "Speak") }; Spacer(Modifier.width(6.dp))
                                OutlinedButton({ if (index < customCards.size - 1) { index++; showFront = true } }, enabled = index < customCards.size - 1, modifier = Modifier.weight(1f)) { Text("Next") }
                            }
                            Spacer(Modifier.height(4.dp))
                            // Action row - changes based on view mode
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (customViewMode == "LEARNED") {
                                    // In Learned view: Relearn (back to active) + Unsure
                                    OutlinedButton({ handleStatusChange(current.id, CustomCardStatus.ACTIVE); if (index >= customCards.size - 1) index = (customCards.size - 2).coerceAtLeast(0); showFront = true }, Modifier.weight(1f)) { Text("Relearn") }
                                } else {
                                    // In ALL or UNSURE view: Got it (mark learned)
                                    Button({ handleStatusChange(current.id, CustomCardStatus.LEARNED); if (index >= customCards.size - 1) index = (customCards.size - 2).coerceAtLeast(0); showFront = true }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = AccentGood)) { Text("Got it ✓") }
                                }
                                IconButton({ showBreakdown = true }, modifier = Modifier.size(44.dp).background(DarkPanel2, RoundedCornerShape(8.dp))) { Icon(Icons.Default.Extension, "Breakdown", tint = if (currentBreakdown?.hasContent() == true) AccentBlue else DarkMuted) }
                                OutlinedButton({ handleStatusChange(current.id, CustomCardStatus.UNSURE); if (index >= customCards.size - 1) index = (customCards.size - 2).coerceAtLeast(0); showFront = true }, Modifier.weight(1f)) { Text("Unsure") }
                            }
                            if (atEnd) { Spacer(Modifier.height(6.dp)); OutlinedButton({ index = 0; showFront = true }, Modifier.fillMaxWidth()) { Icon(Icons.Default.KeyboardArrowUp, "Top"); Spacer(Modifier.width(4.dp)); Text("Return to Top") } }
                        }
                    }
                    // Tap outside search to collapse, but keep results filtered
                    if (searchExpanded) {
                        Box(Modifier.matchParentSize().pointerInput(Unit) { detectTapGestures(onTap = { searchExpanded = false }) })
                    }
                }
            }
        }
    }
    
    // Remove confirmation dialog
    if (showRemoveConfirm && current != null) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title = { Text("Remove Card") },
            text = { Text("Remove \"${current.term}\" from Custom Set?") },
            confirmButton = { 
                TextButton({ 
                    scope.launch { 
                        repo.removeFromCustomSet(current.id)
                        if (index >= customCards.size - 1) index = (customCards.size - 2).coerceAtLeast(0)
                    }
                    showRemoveConfirm = false 
                }) { Text("Remove", color = Color.Red) } 
            },
            dismissButton = { TextButton({ showRemoveConfirm = false }) { Text("Cancel") } }
        )
    }
    
    // Custom Set Settings dialog
    if (showSettings) {
        Dialog(onDismissRequest = { showSettings = false }) {
            Card(Modifier.fillMaxWidth().padding(8.dp), colors = CardDefaults.cardColors(containerColor = DarkPanel)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Custom Set Settings", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                    Spacer(Modifier.height(12.dp))
                    SettingToggle("Random order", cs.randomOrder) { scope.launch { repo.saveSettingsAll(settings.copy(customSetSettings = cs.copy(randomOrder = it))) } }
                    SettingToggle("Reverse cards (Definition first)", cs.reverseCards) { scope.launch { repo.saveSettingsAll(settings.copy(customSetSettings = cs.copy(reverseCards = it))) } }
                    SettingToggle("Show group label", cs.showGroupLabel) { scope.launch { repo.saveSettingsAll(settings.copy(customSetSettings = cs.copy(showGroupLabel = it))) } }
                    SettingToggle("Show breakdown on definition", cs.showBreakdown) { scope.launch { repo.saveSettingsAll(settings.copy(customSetSettings = cs.copy(showBreakdown = it))) } }
                    Spacer(Modifier.height(8.dp))
                    Divider(color = DarkBorder)
                    Spacer(Modifier.height(8.dp))
                    // Custom toggle with wrapping text for "Reflect status changes in Main Decks"
                    Row(Modifier.fillMaxWidth().clickable { scope.launch { repo.saveSettingsAll(settings.copy(customSetSettings = cs.copy(reflectInMainDecks = !cs.reflectInMainDecks))) } }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Reflect status in Main Decks", fontSize = 13.sp, color = Color.White, modifier = Modifier.weight(1f))
                        Switch(cs.reflectInMainDecks, { scope.launch { repo.saveSettingsAll(settings.copy(customSetSettings = cs.copy(reflectInMainDecks = it))) } })
                    }
                    Text("When ON, marking cards as Learned/Unsure in Custom will also update their status in main decks.", color = DarkMuted, fontSize = 10.sp)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton({ showSettings = false }) { Text("Close") }
                    }
                }
            }
        }
    }
    
    if (showBreakdown && current != null) { BreakdownDialog(current, currentBreakdown, adminSettings, { scope.launch { repo.saveBreakdown(it) }; showBreakdown = false }, { useAI -> scope.launch { val bd = repo.autoFillBreakdown(current.id, current.term, useAI); repo.saveBreakdown(bd) }; showBreakdown = false }, { showBreakdown = false }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeletedScreen(nav: NavHostController, repo: Repository) {
    val scope = rememberCoroutineScope()
    val allCards by repo.activeCardsFlow().collectAsState(initial = emptyList())
    val progress by repo.progressFlow().collectAsState(initial = ProgressState.EMPTY)
    var search by remember { mutableStateOf("") }
    val deletedCards = remember(allCards, progress, search) {
        val d = allCards.filter { progress.getStatus(it.id) == CardStatus.DELETED }
        if (search.isBlank()) d else d.filter { it.term.contains(search, true) }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Deleted") }, colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkPanel)) }, bottomBar = { NavBar(nav, Route.Deleted.path) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp)) {
            Text("Deleted: ${deletedCards.size}", color = DarkMuted, fontSize = 11.sp)
            OutlinedTextField(search, { search = it }, Modifier.background(DarkPanel2, RoundedCornerShape(12.dp)).fillMaxWidth(), singleLine = true, label = { Text("Search") }, colors = OutlinedTextFieldDefaults.colors())
            Spacer(Modifier.height(8.dp))
            if (deletedCards.isEmpty()) Text("No deleted cards.", color = DarkMuted)
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(deletedCards, key = { it.id }) { c ->
                    Card(colors = CardDefaults.cardColors(containerColor = DarkPanel), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp)) {
                            Text(c.term, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(c.meaning, fontSize = 12.sp, color = Color.White)
                            Spacer(Modifier.height(4.dp))
                            Button({ scope.launch { repo.setStatus(c.id, CardStatus.ACTIVE) } }) { Text("Restore") }
                        }
                    }
                }
            }
        }
    }
}

// Default settings based on screenshot
private fun getDefaultSettings() = StudySettings(
    showGroup = false,
    showSubgroup = false,
    reverseCards = false,
    showBreakdownOnDefinition = true,
    sortMode = SortMode.JSON_ORDER,
    randomizeUnlearned = false,
    randomizeUnsure = false,
    randomizeLearnedStudy = false,
    showDefinitionsInAllList = true,
    showDefinitionsInLearnedList = true,
    showUnlearnedUnsureButtonsInAllList = true,
    showRelearnUnsureButtonsInLearnedList = true,
    speakPronunciationOnly = false,
    speechRate = 1.0f
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: NavHostController, repo: Repository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by repo.settingsAllFlow().collectAsState(initial = StudySettings())
    val adminSettings by repo.adminSettingsFlow().collectAsState(initial = AdminSettings())
    LaunchedEffect(Unit) { repo.refreshAdminStatus() }
    val tts = remember { TtsHelper(context) }
    DisposableEffect(Unit) { onDispose { tts.shutdown() } }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> if (uri == null) return@rememberLauncherForActivityResult; scope.launch { val imported = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { CsvImport.parseCsv(it.bufferedReader()) } ?: emptyList() }; if (imported.isNotEmpty()) repo.replaceCustomCards(imported) } }
    var showSortDropdown by remember { mutableStateOf(false) }
    
    // Get version info
    val versionName = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    } catch (_: Exception) { "4.2.0" }
    val versionCode = try {
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
    } catch (_: Exception) { 18L }
    
    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }, colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkPanel)) }, bottomBar = { NavBar(nav, Route.Settings.path) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp).verticalScroll(rememberScrollState())) {
            // Version info at top
            Text("Version $versionName (build $versionCode)", color = DarkMuted, fontSize = 11.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
            Spacer(Modifier.height(8.dp))
            
            Text("Display", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
            SettingToggle("Show group label", settings.showGroup) { scope.launch { repo.saveSettingsAll(settings.copy(showGroup = it)) } }
            SettingToggle("Show subgroup label", settings.showSubgroup) { scope.launch { repo.saveSettingsAll(settings.copy(showSubgroup = it)) } }
            SettingToggle("Definition first (reverse)", settings.reverseCards) { scope.launch { repo.saveSettingsAll(settings.copy(reverseCards = it)) } }
            SettingToggle("Show breakdown on definition", settings.showBreakdownOnDefinition) { scope.launch { repo.saveSettingsAll(settings.copy(showBreakdownOnDefinition = it)) } }
            
            Spacer(Modifier.height(12.dp)); Text("Sorting", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
            Row(Modifier.fillMaxWidth().clickable { showSortDropdown = true }.padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Sort order", fontSize = 13.sp, color = Color.White); Row { Text(settings.sortMode.label, fontSize = 13.sp, color = DarkMuted); Icon(Icons.Default.ArrowDropDown, "Select", tint = DarkMuted) }
                DropdownMenu(showSortDropdown, { showSortDropdown = false }) { SortMode.entries.forEach { mode -> DropdownMenuItem(text = { Text(mode.label, color = Color.White) }, onClick = { scope.launch { repo.saveSettingsAll(settings.copy(sortMode = mode)) }; showSortDropdown = false }) } }
            }
            
            Spacer(Modifier.height(12.dp)); Text("Randomization", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
            SettingToggle("Randomize To Study", settings.randomizeUnlearned) { scope.launch { repo.saveSettingsAll(settings.copy(randomizeUnlearned = it)) } }
            SettingToggle("Randomize Unsure", settings.randomizeUnsure) { scope.launch { repo.saveSettingsAll(settings.copy(randomizeUnsure = it)) } }
            SettingToggle("Randomize Learned Study", settings.randomizeLearnedStudy) { scope.launch { repo.saveSettingsAll(settings.copy(randomizeLearnedStudy = it)) } }
            SettingToggle("Randomize Custom Set", settings.customSetSettings.randomOrder) { 
                scope.launch { 
                    repo.saveSettingsAll(settings.copy(customSetSettings = settings.customSetSettings.copy(randomOrder = it))) 
                } 
            }

            Spacer(Modifier.height(12.dp)); Text("List Views", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
            SettingToggle("Show definitions in All list", settings.showDefinitionsInAllList) { scope.launch { repo.saveSettingsAll(settings.copy(showDefinitionsInAllList = it)) } }
            SettingToggle("Show definitions in Learned list", settings.showDefinitionsInLearnedList) { scope.launch { repo.saveSettingsAll(settings.copy(showDefinitionsInLearnedList = it)) } }
            SettingToggle("Show action buttons in All list", settings.showUnlearnedUnsureButtonsInAllList) { scope.launch { repo.saveSettingsAll(settings.copy(showUnlearnedUnsureButtonsInAllList = it)) } }
            SettingToggle("Show action buttons in Learned list", settings.showRelearnUnsureButtonsInLearnedList) { scope.launch { repo.saveSettingsAll(settings.copy(showRelearnUnsureButtonsInLearnedList = it)) } }
            
            Spacer(Modifier.height(12.dp)); Text("Voice", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
            SettingToggle("Speak pronunciation only", settings.speakPronunciationOnly) { scope.launch { repo.saveSettingsAll(settings.copy(speakPronunciationOnly = it)) } }
            SettingToggle("Auto-speak term on card change", settings.autoSpeakOnCardChange) { scope.launch { repo.saveSettingsAll(settings.copy(autoSpeakOnCardChange = it)) } }
            SettingToggle("Speak definition when flipped", settings.speakDefinitionOnFlip) { scope.launch { repo.saveSettingsAll(settings.copy(speakDefinitionOnFlip = it)) } }
            Text("Speech rate: ${String.format("%.1f", settings.speechRate)}x", fontSize = 12.sp, color = DarkMuted)
            Slider(settings.speechRate, { scope.launch { repo.saveSettingsAll(settings.copy(speechRate = it)) } }, valueRange = 0.5f..2.0f, steps = 5, modifier = Modifier.fillMaxWidth())
            Button({ tts.setRate(settings.speechRate); tts.speakTest() }, Modifier.height(36.dp)) { Text("Test Voice", fontSize = 12.sp) }
            
            // AI Features section - shows when API keys are available
            if (adminSettings.chatGptApiKey.isNotBlank() || adminSettings.geminiApiKey.isNotBlank()) {
                Spacer(Modifier.height(12.dp)); Text("AI Features", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                Text("AI assists with breakdowns & card creation", color = DarkMuted, fontSize = 10.sp)
                if (adminSettings.chatGptApiKey.isNotBlank()) {
                    SettingToggle("Use ChatGPT", adminSettings.chatGptEnabled) { 
                        scope.launch { repo.saveAdminSettings(adminSettings.copy(chatGptEnabled = it)) } 
                    }
                }
                if (adminSettings.geminiApiKey.isNotBlank()) {
                    SettingToggle("Use Gemini", adminSettings.geminiEnabled) { 
                        scope.launch { repo.saveAdminSettings(adminSettings.copy(geminiEnabled = it)) } 
                    }
                }
            }
            
            Spacer(Modifier.height(16.dp)); HorizontalDivider(color = DarkBorder); Spacer(Modifier.height(12.dp))
            
            // Manage Decks - Edit study decks, add cards, create new decks
            Button({ nav.navigate(Route.ManageDecks.path) }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)) { 
                Icon(Icons.Default.Dashboard, "Decks"); Spacer(Modifier.width(8.dp))
                Text("Edit Decks") 
            }
            Spacer(Modifier.height(6.dp))
            OutlinedButton({ scope.launch { repo.saveSettingsAll(getDefaultSettings()) } }, Modifier.fillMaxWidth()) { Text("Reset to Default Settings") }
            
            Spacer(Modifier.height(16.dp)); HorizontalDivider(color = DarkBorder); Spacer(Modifier.height(12.dp))
            
            // Login button (shows login status)
            Button({ nav.navigate(Route.Login.path) }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = if (adminSettings.isLoggedIn) AccentGood else AccentBlue)) {
                Icon(Icons.Default.Person, "Login"); Spacer(Modifier.width(8.dp))
                Text(if (adminSettings.isLoggedIn) "Logged in: ${adminSettings.username}" else "Login")
            }
            
            Spacer(Modifier.height(6.dp))
            
            // Sync Progress button (with pending indicator)
            Button({ nav.navigate(Route.SyncProgress.path) }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)) {
                Icon(Icons.Default.Sync, "Sync"); Spacer(Modifier.width(8.dp))
                Text("Sync Progress")
                if (adminSettings.pendingSync) {
                    Spacer(Modifier.width(8.dp))
                    Text("(Pending)", color = Color.Yellow, fontSize = 10.sp)
                }
            }
            
            Spacer(Modifier.height(16.dp)); HorizontalDivider(color = DarkBorder); Spacer(Modifier.height(12.dp))
            
            // Admin Settings button - only show for admin users
            if (AdminUsers.isAdmin(adminSettings.username)) {
                Button({ nav.navigate(Route.Admin.path) }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B0000))) {
                    Icon(Icons.Default.AdminPanelSettings, "Admin"); Spacer(Modifier.width(8.dp)); Text("Admin Settings")
                }
                Spacer(Modifier.height(6.dp))
            }
            
            // About button
            OutlinedButton({ nav.navigate(Route.About.path) }, Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Info, "About"); Spacer(Modifier.width(8.dp)); Text("About")
            }
        }
    }
}

@Composable
private fun SettingToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = Color.White); Switch(checked, onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(nav: NavHostController, repo: Repository) {
    val scope = rememberCoroutineScope()
    val adminSettings by repo.adminSettingsFlow().collectAsState(initial = AdminSettings())
    LaunchedEffect(Unit) { repo.refreshAdminStatus() }
    
        // Admin status is server-sourced (token) and stored in AdminSettings.isAdmin
    val isAdmin = adminSettings.isAdmin
        adminSettings.username.trim().lowercase() in setOf("sidscri")
    
    var chatGptKey by remember(adminSettings) { mutableStateOf(adminSettings.chatGptApiKey) }
    var chatGptModel by remember(adminSettings) { mutableStateOf(adminSettings.chatGptModel) }
    var geminiKey by remember(adminSettings) { mutableStateOf(adminSettings.geminiApiKey) }
    var geminiModel by remember(adminSettings) { mutableStateOf(adminSettings.geminiModel) }
    var managedServerUrl by remember(adminSettings) { mutableStateOf(adminSettings.webAppUrl.ifBlank { WebAppSync.DEFAULT_SERVER_URL }) }

    var statusMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var redeemCode by remember { mutableStateOf("") }
    var showChatGptModelDropdown by remember { mutableStateOf(false) }
    var showGeminiModelDropdown by remember { mutableStateOf(false) }
    
    // Key validity - simple prefix check
    val chatGptKeyValid = adminSettings.chatGptApiKey.startsWith("sk-")
    val geminiKeyValid = adminSettings.geminiApiKey.startsWith("AI")
    
    Scaffold(
        topBar = { 
            TopAppBar(
                title = { Text("AI Access Settings") }, 
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF8B0000)), 
                navigationIcon = { IconButton({ nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Back") } }
            ) 
        }, 
        bottomBar = { NavBar(nav, Route.Admin.path) }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp).verticalScroll(rememberScrollState())) {
            
            // Show access denied if not admin
            if (!isAdmin) {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF3D1212)), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Lock, "Locked", tint = Color.Red, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Admin Access Required", color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("Current user: ${adminSettings.username.ifBlank { "(not logged in)" }}", color = DarkMuted, fontSize = 12.sp)
                        Spacer(Modifier.height(12.dp))
                        Button({ nav.popBackStack() }) { Text("Go Back") }
                    }
                }
                return@Scaffold
            }
            
            Text("⚠️ Admin Only", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Yellow)
            Spacer(Modifier.height(4.dp))
            Text("API keys are encrypted and shared between Android app and web server.", color = DarkMuted, fontSize = 11.sp)
            
            Spacer(Modifier.height(20.dp)); HorizontalDivider(color = DarkBorder); 
            // =========================
            // GEN8: Deck Admin (Server)
            // =========================
            var adminTab by remember { mutableStateOf(0) }
            var deckCfgLoaded by remember { mutableStateOf(false) }
            var newUsersGetBuiltIn by remember { mutableStateOf(true) }
            var allowNonAdminEdits by remember { mutableStateOf(true) }
            var builtInDecks by remember { mutableStateOf(setOf<String>()) }
            var inviteDeckId by remember { mutableStateOf("kenpo") }
            var inviteCodeResult by remember { mutableStateOf("") }

            if (isAdmin) {
                Spacer(Modifier.height(16.dp))
                Text("Deck Admin (Server)", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                Spacer(Modifier.height(8.dp))

                Button(onClick = {
                    try {
                        val url = adminSettings.webAppUrl.trimEnd('/') + "/admin"
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                }, modifier = Modifier.fillMaxWidth()) { Text("Open Web Admin") }

                Spacer(Modifier.height(12.dp))

                TabRow(selectedTabIndex = adminTab, containerColor = DarkPanel) {
                    Tab(selected = adminTab == 0, onClick = { adminTab = 0 }) { Text("Decks", modifier = Modifier.padding(12.dp), fontSize = 12.sp) }
                }

                Spacer(Modifier.height(12.dp))

                when (adminTab) {
                    0 -> {
                        if (!deckCfgLoaded) {
                            LaunchedEffect("loadDeckCfg") {
                                try {
                                    val cfg = repo.adminGetDeckConfig()
                                    newUsersGetBuiltIn = cfg.optBoolean("newUsersGetBuiltInDecks", true)
                                    allowNonAdminEdits = cfg.optBoolean("allowNonAdminDeckEdits", true)
                                    val arr = cfg.optJSONArray("builtInDecks") ?: org.json.JSONArray()
                                    val sset = mutableSetOf<String>()
                                    for (i in 0 until arr.length()) sset.add(arr.optString(i))
                                    builtInDecks = sset
                                } catch (_: Exception) {
                                } finally {
                                    deckCfgLoaded = true
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Checkbox(checked = newUsersGetBuiltIn, onCheckedChange = { newUsersGetBuiltIn = it })
                            Text("New users get built-in decks", color = Color.White, fontSize = 12.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Checkbox(checked = allowNonAdminEdits, onCheckedChange = { allowNonAdminEdits = it })
                            Text("Allow non-admin deck edits", color = Color.White, fontSize = 12.sp)
                        }

                        Spacer(Modifier.height(8.dp))
                        Text("Built-in Decks", color = DarkMuted, fontSize = 12.sp)
                        decks.forEach { d ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                val checked = builtInDecks.contains(d.id)
                                Checkbox(checked = checked, onCheckedChange = {
                                    builtInDecks = if (it) builtInDecks + d.id else builtInDecks - d.id
                                })
                                Text(d.name + " (" + d.id + ")", color = Color.White, fontSize = 12.sp)
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            scope.launch {
                                try {
                                    val cfg = org.json.JSONObject()
                                        .put("newUsersGetBuiltInDecks", newUsersGetBuiltIn)
                                        .put("allowNonAdminDeckEdits", allowNonAdminEdits)
                                        .put("builtInDecks", org.json.JSONArray(builtInDecks.toList()))
                                    val resp = repo.adminSetDeckConfig(cfg)
                                    statusMessage = if (resp.optBoolean("success", false)) "Saved deck config." else resp.optString("error", "Save failed")
                                } catch (e: Exception) {
                                    statusMessage = "Save error: ${e.message}"
                                }
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("Save Deck Config") }

                        Spacer(Modifier.height(16.dp))
                        Text("Invite Code Generator", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))

                        var expanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                            OutlinedTextField(
                                value = inviteDeckId,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Deck ID") },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                decks.forEach { d ->
                                    DropdownMenuItem(text = { Text("${d.name} (${d.id})") }, onClick = {
                                        inviteDeckId = d.id
                                        expanded = false
                                    })
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            scope.launch {
                                try {
                                    val resp = repo.adminCreateInviteCode(inviteDeckId)
                                    inviteCodeResult = if (resp.optBoolean("success", false)) resp.optString("code", "") else resp.optString("error", "Failed")
                                } catch (e: Exception) {
                                    inviteCodeResult = "Error: ${e.message}"
                                }
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("Generate Invite Code") }

                        if (inviteCodeResult.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Card(colors = CardDefaults.cardColors(containerColor = DarkPanel), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("Code: " + inviteCodeResult, color = Color.White)
                                    Spacer(Modifier.height(6.dp))
                                    Button(onClick = {
                                        try {
                                            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                            cm.setPrimaryClip(android.content.ClipData.newPlainText("InviteCode", inviteCodeResult))
                                        } catch (_: Exception) {}
                                    }) { Text("Copy") }
                                }
                            }
                        }
                    }
                }
            }

Spacer(Modifier.height(16.dp))
            
            // ChatGPT API Section
            Text("ChatGPT API", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text("Enable AI-powered breakdown autofill using OpenAI", color = DarkMuted, fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(chatGptKey, { chatGptKey = it }, Modifier.fillMaxWidth(), label = { Text("OpenAI API Key") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), textStyle = LocalTextStyle.current.copy(fontSize = 12.sp))
            // Key status indicator
            if (adminSettings.chatGptApiKey.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (chatGptKeyValid) Icons.Default.CheckCircle else Icons.Default.Error, "Status", 
                        tint = if (chatGptKeyValid) Color(0xFF4CAF50) else Color.Red, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (chatGptKeyValid) "Key Accepted" else "Key Invalid", 
                        color = if (chatGptKeyValid) Color(0xFF4CAF50) else Color.Red, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(6.dp))
            
            // Model selection dropdown
            Row(Modifier.fillMaxWidth().clickable { showChatGptModelDropdown = true }.padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Model", fontSize = 13.sp, color = Color.White)
                Row {
                    Text(ChatGptModels.models.find { it.first == chatGptModel }?.second ?: chatGptModel, fontSize = 12.sp, color = DarkMuted)
                    Icon(Icons.Default.ArrowDropDown, "Select", tint = DarkMuted)
                }
                DropdownMenu(showChatGptModelDropdown, { showChatGptModelDropdown = false }) {
                    ChatGptModels.models.forEach { (modelId, modelName) ->
                        DropdownMenuItem(text = { Text(modelName, color = Color.White) }, onClick = { chatGptModel = modelId; showChatGptModelDropdown = false })
                    }
                }
            }
            
            SettingToggle("Enable ChatGPT Breakdown", adminSettings.chatGptEnabled) { scope.launch { repo.saveAdminSettings(adminSettings.copy(chatGptEnabled = it)) } }
            Spacer(Modifier.height(6.dp))
            Button({ scope.launch { repo.saveAdminSettings(adminSettings.copy(chatGptApiKey = chatGptKey, chatGptModel = chatGptModel)); statusMessage = "ChatGPT settings saved" } }, Modifier.fillMaxWidth()) { Text("Save ChatGPT Settings") }
            
            Spacer(Modifier.height(20.dp)); HorizontalDivider(color = DarkBorder); Spacer(Modifier.height(16.dp))
            
            // Gemini API Section
            Text("Gemini API", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text("Enable AI-powered breakdown autofill using Google Gemini", color = DarkMuted, fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(geminiKey, { geminiKey = it }, Modifier.fillMaxWidth(), label = { Text("Gemini API Key") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), textStyle = LocalTextStyle.current.copy(fontSize = 12.sp))
            // Key status indicator
            if (adminSettings.geminiApiKey.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (geminiKeyValid) Icons.Default.CheckCircle else Icons.Default.Error, "Status", 
                        tint = if (geminiKeyValid) Color(0xFF4CAF50) else Color.Red, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (geminiKeyValid) "Key Accepted" else "Key Invalid", 
                        color = if (geminiKeyValid) Color(0xFF4CAF50) else Color.Red, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(6.dp))
            
            // Model selection dropdown
            Row(Modifier.fillMaxWidth().clickable { showGeminiModelDropdown = true }.padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Model", fontSize = 13.sp, color = Color.White)
                Row {
                    Text(GeminiModels.models.find { it.first == geminiModel }?.second ?: geminiModel, fontSize = 12.sp, color = DarkMuted)
                    Icon(Icons.Default.ArrowDropDown, "Select", tint = DarkMuted)
                }
                DropdownMenu(showGeminiModelDropdown, { showGeminiModelDropdown = false }) {
                    GeminiModels.models.forEach { (modelId, modelName) ->
                        DropdownMenuItem(text = { Text(modelName, color = Color.White) }, onClick = { geminiModel = modelId; showGeminiModelDropdown = false })
                    }
                }
            }
            
            SettingToggle("Enable Gemini Breakdown", adminSettings.geminiEnabled) { scope.launch { repo.saveAdminSettings(adminSettings.copy(geminiEnabled = it)) } }
            Spacer(Modifier.height(6.dp))
            Button({ scope.launch { repo.saveAdminSettings(adminSettings.copy(geminiApiKey = geminiKey, geminiModel = geminiModel)); statusMessage = "Gemini settings saved" } }, Modifier.fillMaxWidth()) { Text("Save Gemini Settings") }
            
            Spacer(Modifier.height(20.dp)); HorizontalDivider(color = DarkBorder); Spacer(Modifier.height(16.dp))
            


            // ==================== SERVER URL (ADMIN) ====================
            Text("Server URL", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text("Admins can update the sync server URL and push it to all users via the web server config.", color = DarkMuted, fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                managedServerUrl,
                { managedServerUrl = it },
                Modifier.fillMaxWidth(),
                label = { Text("Managed Server URL (Admin)") },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                leadingIcon = { Icon(Icons.Default.Cloud, "Server") }
            )
            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({
                    scope.launch {
                        repo.saveAdminSettings(adminSettings.copy(webAppUrl = managedServerUrl))
                        statusMessage = "Server URL saved locally"
                    }
                }, Modifier.weight(1f)) { Text("Save Locally") }

                Button({
                    if (adminSettings.authToken.isBlank()) { statusMessage = "Error: Login required"; return@Button }
                    isLoading = true
                    scope.launch {
                        val res = repo.syncPushManagedServerUrl(adminSettings.authToken, adminSettings.webAppUrl, managedServerUrl)
                        statusMessage = if (res.success) "Server URL pushed to server!" else "Error: ${res.error}"
                        isLoading = false
                    }
                }, Modifier.weight(1f), enabled = !isLoading && adminSettings.isLoggedIn) { Text(if (isLoading) "..." else "Push URL") }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton({
                if (adminSettings.authToken.isBlank()) { statusMessage = "Error: Login required"; return@OutlinedButton }
                isLoading = true
                scope.launch {
                    val cfg = repo.syncPullServerConfig(adminSettings.authToken, adminSettings.webAppUrl)
                    if (cfg.success && cfg.managedServerUrl.isNotBlank()) {
                        managedServerUrl = cfg.managedServerUrl
                        repo.saveAdminSettings(adminSettings.copy(webAppUrl = cfg.managedServerUrl))
                        statusMessage = "Server URL pulled from server"
                    } else {
                        statusMessage = "Error: ${cfg.error.ifBlank { "Config endpoint not available" }}"
                    }
                    isLoading = false
                }
            }, Modifier.fillMaxWidth(), enabled = !isLoading && adminSettings.isLoggedIn) { Text(if (isLoading) "..." else "Pull URL from Server") }

            Spacer(Modifier.height(20.dp)); HorizontalDivider(color = DarkBorder); Spacer(Modifier.height(16.dp))
            // Sync API Keys with Server
            Text("Sync with Server", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text("Push/pull encrypted API keys and models to/from server", color = DarkMuted, fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({
                    if (adminSettings.authToken.isBlank()) { statusMessage = "Error: Login required"; return@Button }
                    isLoading = true
                    scope.launch {
                        // Save locally first
                        repo.saveAdminSettings(adminSettings.copy(
                            chatGptApiKey = chatGptKey, chatGptModel = chatGptModel,
                            geminiApiKey = geminiKey, geminiModel = geminiModel
                        ))
                        // Then push to server
                        val result = repo.syncPushApiKeys(adminSettings.authToken, adminSettings.webAppUrl, chatGptKey, chatGptModel, geminiKey, geminiModel)
                        statusMessage = if (result.success) "API keys pushed to server!" else "Error: ${result.error}"
                        isLoading = false
                    }
                }, Modifier.weight(1f), enabled = !isLoading && adminSettings.isLoggedIn) { Text(if (isLoading) "..." else "Push to Server") }
                
                Button({
                    if (adminSettings.authToken.isBlank()) { statusMessage = "Error: Login required"; return@Button }
                    isLoading = true
                    scope.launch {
                        val result = repo.syncPullApiKeys(adminSettings.authToken, adminSettings.webAppUrl)
                        if (result.success) {
                            chatGptKey = result.chatGptKey
                            chatGptModel = result.chatGptModel
                            geminiKey = result.geminiKey
                            geminiModel = result.geminiModel
                            repo.saveAdminSettings(adminSettings.copy(
                                chatGptApiKey = result.chatGptKey,
                                chatGptModel = result.chatGptModel,
                                geminiApiKey = result.geminiKey,
                                geminiModel = result.geminiModel,
                                chatGptEnabled = result.chatGptKey.isNotBlank(),
                                geminiEnabled = result.geminiKey.isNotBlank()
                            ))
                            statusMessage = "API keys pulled from server!"
                        } else {
                            statusMessage = "Error: ${result.error}"
                        }
                        isLoading = false
                    }
                }, Modifier.weight(1f), enabled = !isLoading && adminSettings.isLoggedIn) { Text(if (isLoading) "..." else "Pull from Server") }
            }
            
            if (statusMessage.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(statusMessage, color = if (statusMessage.startsWith("Error")) Color.Red else AccentBlue, fontSize = 12.sp) }
            
            Spacer(Modifier.height(24.dp))
            Text("Server Info", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DarkMuted)
            Text("""
API keys are encrypted and stored in data/api_keys.enc on server.
Both Android app and web server share the same encrypted keys.

Server: ${adminSettings.webAppUrl.ifBlank { WebAppSync.DEFAULT_SERVER_URL }}
Logged in as: ${adminSettings.username} (Admin)
            """.trimIndent(), color = DarkMuted, fontSize = 10.sp)
        }
    }
}

// ==================== ABOUT SCREEN ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(nav: NavHostController) {
    val context = LocalContext.current
    val versionName = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    } catch (_: Exception) { "4.2.0" }
    val versionCode = try {
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
    } catch (_: Exception) { 18L }
    
    Scaffold(topBar = { TopAppBar(title = { Text("About") }, colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkPanel), navigationIcon = { IconButton({ nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Back") } }) }, bottomBar = { NavBar(nav, Route.Settings.path) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState())) {
            // App Icon and Title
            Card(colors = CardDefaults.cardColors(containerColor = DarkPanel2), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.School, "App Icon", modifier = Modifier.size(64.dp), tint = AccentBlue)
                    Spacer(Modifier.height(12.dp))
                    Text("Kenpo Vocabulary Flashcards", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White, textAlign = TextAlign.Center)
                    Text("Version $versionName (build $versionCode)", color = DarkMuted, fontSize = 12.sp)
                }
            }
            
            Spacer(Modifier.height(20.dp))
            
            // Creator Info
            Text("Created By", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = DarkPanel), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Sidney Shelton", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Email, "Email", tint = AccentBlue, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Sidscri@yahoo.com", color = AccentBlue, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("For questions, feature requests, or bug reports, please email the address above.", color = DarkMuted, fontSize = 12.sp)
                }
            }
            
            Spacer(Modifier.height(20.dp))
            
            // App Description
            Text("About This App", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = DarkPanel), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Kenpo Vocabulary Flashcards is a study tool designed to help martial arts students learn and memorize Korean terminology used in Kenpo and other martial arts.\n\nThe app provides an organized, efficient way to study vocabulary with progress tracking, customizable study sessions, and AI-powered term breakdowns.", color = DarkText, fontSize = 13.sp, lineHeight = 20.sp)
                }
            }
            
            Spacer(Modifier.height(20.dp))
            
            // Major Features
            Text("Major Features", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = DarkPanel), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    FeatureItem("📚", "Flashcard Study", "Study cards with term/definition flip, organized by groups")
                    FeatureItem("📊", "Progress Tracking", "Track cards as Active, Unsure, or Learned")
                    FeatureItem("⭐", "Custom Sets", "Create personalized study sets with starred cards")
                    FeatureItem("🔤", "Term Breakdowns", "Break down compound terms into component parts with meanings")
                    FeatureItem("🤖", "AI Autofill", "Use ChatGPT or Gemini to auto-generate term breakdowns")
                    FeatureItem("🔊", "Text-to-Speech", "Hear pronunciations with customizable voice settings")
                    FeatureItem("☁️", "Cloud Sync", "Sync progress across devices with web server")
                    FeatureItem("🌙", "Dark Theme", "Easy-on-the-eyes dark mode interface")
                }
            }
            
            Spacer(Modifier.height(20.dp))
            
            // User Guide Button
            Button({ nav.navigate(Route.UserGuide.path) }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)) {
                Icon(Icons.Default.MenuBook, "Guide"); Spacer(Modifier.width(8.dp)); Text("View User Guide")
            }
            
            Spacer(Modifier.height(24.dp))
            Text("© 2026 Sidney Shelton. All rights reserved.", color = DarkMuted, fontSize = 10.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun FeatureItem(emoji: String, title: String, description: String) {
    Row(Modifier.padding(vertical = 6.dp)) {
        Text(emoji, fontSize = 16.sp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
            Text(description, color = DarkMuted, fontSize = 11.sp)
        }
    }
}

// ==================== LOGIN SCREEN ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(nav: NavHostController, repo: Repository) {
    val scope = rememberCoroutineScope()
    val adminSettings by repo.adminSettingsFlow().collectAsState(initial = AdminSettings())
    LaunchedEffect(Unit) { repo.refreshAdminStatus() }
    var serverUrl by remember(adminSettings) { mutableStateOf(adminSettings.webAppUrl.ifBlank { WebAppSync.DEFAULT_SERVER_URL }) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var redeemCode by remember { mutableStateOf("") }
    var isFirstLogin by remember { mutableStateOf(false) }
    
    val isAdmin = AdminUsers.isAdmin(adminSettings.username)
    val adminLabel = if (isAdmin) " (Admin)" else ""
    
    Scaffold(topBar = { TopAppBar(title = { Text("Login") }, colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkPanel), navigationIcon = { IconButton({ nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Back") } }) }, bottomBar = { NavBar(nav, Route.Settings.path) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp).verticalScroll(rememberScrollState())) {
            Text("Web App Sync", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text("Sync server is managed by an admin. No manual server entry is required for regular users.", color = DarkMuted, fontSize = 11.sp)
            Spacer(Modifier.height(12.dp))
            
            if (adminSettings.isLoggedIn) {
                Card(colors = CardDefaults.cardColors(containerColor = AccentGood), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, "Logged In", tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Logged in: ${adminSettings.username}$adminLabel", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Token: ${if (adminSettings.authToken.isNotBlank()) adminSettings.authToken.take(8) + "..." else "MISSING!"}", color = if (adminSettings.authToken.isNotBlank()) DarkMuted else Color.Red, fontSize = 10.sp)
                        if (adminSettings.lastSyncTime > 0) {
                            Text("Last sync: ${java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(adminSettings.lastSyncTime))}", color = DarkMuted, fontSize = 11.sp)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                OutlinedButton({ scope.launch { repo.saveAdminSettings(adminSettings.copy(isLoggedIn = false, authToken = "", username = "")); statusMessage = "Logged out successfully" } }, Modifier.fillMaxWidth()) { 
                    Icon(Icons.Default.Logout, "Logout"); Spacer(Modifier.width(8.dp)); Text("Logout") 
                }
            } else {
                val isAdminCandidate = AdminUsers.isAdmin(username)
                if (isAdminCandidate) {
                    OutlinedTextField(serverUrl, { serverUrl = it }, Modifier.fillMaxWidth(), label = { Text("Server URL (Admin)") }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 12.sp), leadingIcon = { Icon(Icons.Default.Cloud, "Server") })
                    Spacer(Modifier.height(8.dp))
                    Button({
                        scope.launch {
                            repo.saveAdminSettings(adminSettings.copy(webAppUrl = serverUrl))
                            statusMessage = "Server URL saved. You can login now."
                        }
                    }, Modifier.fillMaxWidth()) { Text("Save / Apply Server URL") }
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), label = { Text("Username") }, singleLine = true, leadingIcon = { Icon(Icons.Default.Person, "User") })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), leadingIcon = { Icon(Icons.Default.Lock, "Password") })
                Spacer(Modifier.height(12.dp))
                Button({
                    if (username.isBlank() || password.isBlank()) { statusMessage = "Enter username and password"; return@Button }
                    isLoading = true
                    scope.launch {
                        val result = repo.syncLogin(username, password, serverUrl)
                        if (result.success) {
                            var effectiveUrl = serverUrl.ifBlank { WebAppSync.DEFAULT_SERVER_URL }
                            // Pull admin-managed config (if server supports it)
                            val cfg = repo.syncPullServerConfig(result.token, effectiveUrl)
                            if (cfg.success && cfg.managedServerUrl.isNotBlank() && cfg.managedServerUrl != effectiveUrl) {
                                effectiveUrl = cfg.managedServerUrl
                            }
                            
                            // Check if this is first login on this device (lastSyncTime == 0)
                            isFirstLogin = adminSettings.lastSyncTime == 0L
                            
                            val newSettings = adminSettings.copy(
                                webAppUrl = effectiveUrl,
                                authToken = result.token,
                                username = result.username,
                                isLoggedIn = true,
                                lastSyncTime = 0
                            )
                            repo.saveAdminSettings(newSettings)
                            
                            // Fetch admin users list from server (Source of Truth)
                            val adminList = WebAppSync.fetchAdminUsers(effectiveUrl)
                            if (adminList.isNotEmpty()) {
                                AdminUsers.updateAdminList(adminList)
                            }
                            
                            // Pull API keys for ALL users (so they can use AI features)
                            val keysResult = repo.syncPullApiKeysForUser(result.token, effectiveUrl)
                            if (keysResult.success) {
                                repo.saveAdminSettings(newSettings.copy(
                                    chatGptApiKey = keysResult.chatGptKey,
                                    chatGptModel = keysResult.chatGptModel,
                                    geminiApiKey = keysResult.geminiKey,
                                    geminiModel = keysResult.geminiModel,
                                    chatGptEnabled = keysResult.chatGptKey.isNotBlank(),
                                    geminiEnabled = keysResult.geminiKey.isNotBlank()
                                ))
                            }
                            
                            password = ""
                            
                            // First login OR auto-pull enabled: always sync
                            if (isFirstLogin || adminSettings.autoPullOnLogin) {
                                statusMessage = "Login successful! Syncing..."
                                val pullResult = repo.syncPullProgressWithToken(result.token, effectiveUrl)
                                val breakdownResult = repo.syncBreakdowns()
                                // If local had newer offline changes, try pushing them back up (best-effort)
                                val pushPendingResult = repo.syncPushPendingProgressWithToken(result.token, effectiveUrl)
                                statusMessage = if (pullResult.success && breakdownResult.success && pushPendingResult.success) {
                                    "Login successful! Progress and breakdowns synced."
                                } else {
                                    "Login successful! Some sync errors occurred."
                                }
                                repo.saveAdminSettings(newSettings.copy(lastSyncTime = System.currentTimeMillis()))
                            } else {
                                statusMessage = "Login successful!"
                            }
                        } else { statusMessage = "Login failed: ${result.error}" }
                        isLoading = false
                    }
                }, Modifier.fillMaxWidth(), enabled = !isLoading) {
                    Icon(Icons.Default.Login, "Login"); Spacer(Modifier.width(8.dp)); Text(if (isLoading) "Logging in..." else "Login")
                }
            }
            
            if (statusMessage.isNotBlank()) { 
                Spacer(Modifier.height(12.dp))
                Card(colors = CardDefaults.cardColors(containerColor = if (statusMessage.contains("failed") || statusMessage.contains("Error")) Color(0xFF3D1212) else DarkPanel2)) {
                    Text(statusMessage, color = if (statusMessage.contains("failed") || statusMessage.contains("Error")) Color.Red else AccentBlue, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
                }
            }
            
            Spacer(Modifier.height(20.dp)); HorizontalDivider(color = DarkBorder); Spacer(Modifier.height(16.dp))
            Text("Sync Settings", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text("First login on a device always syncs automatically.", color = DarkMuted, fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
            SettingToggle("Auto-pull progress on future logins", adminSettings.autoPullOnLogin) { scope.launch { repo.saveAdminSettings(adminSettings.copy(autoPullOnLogin = it)) } }
            SettingToggle("Auto-push changes when made", adminSettings.autoPushOnChange) { scope.launch { repo.saveAdminSettings(adminSettings.copy(autoPushOnChange = it)) } }
            Spacer(Modifier.height(12.dp))
            Text("When auto-push is enabled, your progress will sync to the server whenever you change a card's status. If offline, changes will be queued.", color = DarkMuted, fontSize = 11.sp)
        }
    }
}

// ==================== SYNC PROGRESS SCREEN ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncProgressScreen(nav: NavHostController, repo: Repository) {
    val scope = rememberCoroutineScope()
    val adminSettings by repo.adminSettingsFlow().collectAsState(initial = AdminSettings())
    LaunchedEffect(Unit) { repo.refreshAdminStatus() }
    var statusMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var redeemCode by remember { mutableStateOf("") }
    var showAiPicker by remember { mutableStateOf(false) }
    val availableAi = remember(adminSettings) { repo.getAvailableAiServices(adminSettings) }
    
    val isAdmin = AdminUsers.isAdmin(adminSettings.username)
    val adminLabel = if (isAdmin) " (Admin)" else ""
    
    Scaffold(topBar = { TopAppBar(title = { Text("Sync Progress") }, colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkPanel), navigationIcon = { IconButton({ nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Back") } }) }, bottomBar = { NavBar(nav, Route.Settings.path) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp).verticalScroll(rememberScrollState())) {
            // Login Status Card
            Card(colors = CardDefaults.cardColors(containerColor = if (adminSettings.isLoggedIn) AccentGood else Color(0xFF3D1212)), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (adminSettings.isLoggedIn) Icons.Default.CheckCircle else Icons.Default.Error, "Status", tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Login Status: ${if (adminSettings.isLoggedIn) "Logged In$adminLabel" else "Not Logged In"}", color = Color.White, fontWeight = FontWeight.Bold)
                        if (adminSettings.isLoggedIn) Text("User: ${adminSettings.username}$adminLabel", color = DarkMuted, fontSize = 11.sp)
                        else Text("Please login to sync progress", color = DarkMuted, fontSize = 11.sp)
                    }
                }
            }
            
            if (!adminSettings.isLoggedIn) {
                Spacer(Modifier.height(12.dp))
                Button({ nav.navigate(Route.Login.path) }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Login, "Login"); Spacer(Modifier.width(8.dp)); Text("Go to Login") }
            }
            
            if (adminSettings.pendingSync) {
                Spacer(Modifier.height(12.dp))
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF3D3D12)), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, "Pending", tint = Color.Yellow)
                        Spacer(Modifier.width(8.dp))
                        Text("Changes pending sync - Push to sync", color = Color.Yellow, fontSize = 12.sp)
                    }
                }
            }
            
            Spacer(Modifier.height(20.dp))
            Text("Web App Sync", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text("Sync your study progress with the web server", color = DarkMuted, fontSize = 11.sp)
            if (adminSettings.lastSyncTime > 0) {
                Text("Last sync: ${java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(adminSettings.lastSyncTime))}", color = DarkMuted, fontSize = 11.sp)
            }
            Spacer(Modifier.height(8.dp))
            
            // Auto-sync explanation card
            Card(colors = CardDefaults.cardColors(containerColor = DarkPanel2), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("⚡ Auto-Sync", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = AccentBlue)
                    Spacer(Modifier.height(4.dp))
                    Text("• First login always syncs automatically\n• Auto-pull on login: pulls latest progress when you log in\n• Auto-push on change: pushes updates whenever you mark a card\n• Offline changes are queued and synced when online", color = DarkMuted, fontSize = 10.sp, lineHeight = 14.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            
            Text("Manual Sync", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text("Push sends your progress to server. Pull downloads server progress to device.", color = DarkMuted, fontSize = 10.sp)
            Spacer(Modifier.height(8.dp))
            
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ 
                    if (!adminSettings.isLoggedIn) { statusMessage = "Please login first"; return@Button }
                    if (adminSettings.authToken.isBlank()) { statusMessage = "Error: No auth token"; return@Button }
                    isLoading = true
                    scope.launch { 
                        val result = repo.syncPushProgressWithToken(adminSettings.authToken, adminSettings.webAppUrl)
                        statusMessage = if (result.success) "Progress pushed!" else "Error: ${result.error}"
                        if (result.success) { repo.saveAdminSettings(adminSettings.copy(lastSyncTime = System.currentTimeMillis(), pendingSync = false)) }
                        isLoading = false 
                    } 
                }, Modifier.weight(1f), enabled = !isLoading && adminSettings.isLoggedIn) { Text(if (isLoading) "..." else "Push") }
                Button({ 
                    if (!adminSettings.isLoggedIn) { statusMessage = "Please login first"; return@Button }
                    if (adminSettings.authToken.isBlank()) { statusMessage = "Error: No auth token"; return@Button }
                    isLoading = true
                    scope.launch { 
                        val result = repo.syncPullProgressWithToken(adminSettings.authToken, adminSettings.webAppUrl)
                        statusMessage = if (result.success) "Progress pulled!" else "Error: ${result.error}"
                        if (result.success) repo.saveAdminSettings(adminSettings.copy(lastSyncTime = System.currentTimeMillis()))
                        isLoading = false 
                    } 
                }, Modifier.weight(1f), enabled = !isLoading && adminSettings.isLoggedIn) { Text(if (isLoading) "..." else "Pull") }
            }
            
            Spacer(Modifier.height(20.dp)); HorizontalDivider(color = DarkBorder); Spacer(Modifier.height(16.dp))
            
            // Sync Breakdowns Section
            Text("Sync Breakdowns", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text("Download shared breakdowns from server", color = DarkMuted, fontSize = 11.sp)
            Spacer(Modifier.height(12.dp))
            
            // Choose Breakdown AI dropdown
            Text("Choose Breakdown AI", fontSize = 13.sp, color = Color.White)
            Spacer(Modifier.height(6.dp))
            if (availableAi.isNotEmpty()) {
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton({ showAiPicker = true }, Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.SmartToy, "AI"); Spacer(Modifier.width(8.dp))
                        Text(adminSettings.breakdownAiChoice.label)
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown, "Select")
                    }
                    DropdownMenu(showAiPicker, { showAiPicker = false }, Modifier.fillMaxWidth(0.9f)) {
                        availableAi.forEach { choice ->
                            DropdownMenuItem(
                                text = { 
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (adminSettings.breakdownAiChoice == choice) {
                                            Icon(Icons.Default.Check, "Selected", tint = AccentBlue, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                        }
                                        Text(choice.label, color = Color.White) 
                                    }
                                }, 
                                onClick = { 
                                    scope.launch { repo.saveAdminSettings(adminSettings.copy(breakdownAiChoice = choice)) }
                                    showAiPicker = false 
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            } else {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2D12)), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, "Info", tint = Color.Yellow, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("No AI services configured. Admin can add API keys in AI Access Settings.", color = DarkMuted, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            
            Button({ isLoading = true; scope.launch { val result = repo.syncBreakdowns(); statusMessage = if (result.success) "Breakdowns synced!" else "Error: ${result.error}"; isLoading = false } }, Modifier.fillMaxWidth(), enabled = !isLoading) { Text(if (isLoading) "Syncing..." else "Sync Breakdowns") }
            
            if (statusMessage.isNotBlank()) { Spacer(Modifier.height(12.dp)); Text(statusMessage, color = if (statusMessage.startsWith("Error")) Color.Red else AccentBlue, fontSize = 12.sp) }
        }
    }
}

// ==================== USER GUIDE SCREEN ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserGuideScreen(nav: NavHostController) {
    Scaffold(topBar = { TopAppBar(title = { Text("User Guide") }, colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkPanel), navigationIcon = { IconButton({ nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Back") } }) }, bottomBar = { NavBar(nav, Route.Settings.path) }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(16.dp)) {
            item { 
                Text("Kenpo Vocabulary Flashcards", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color.White)
                Text("Complete User Guide", color = DarkMuted, fontSize = 14.sp)
                Spacer(Modifier.height(20.dp))
            }
            
            item { GuideSection("Getting Started", """
Welcome to Kenpo Vocabulary Flashcards! This app helps you learn Korean martial arts terminology through interactive flashcard study.

When you first open the app, you'll see the "To Study" screen with all active flashcards. Each card shows a term on the front and its definition on the back.
            """) }
            
            item { GuideSection("Navigation", """
The bottom navigation bar has 6 tabs:
• To Study - Cards you're actively learning
• Unsure - Cards you're not confident about yet
• Learned - Cards you've mastered
• All - View all cards in a list format
• Custom - Your starred/favorite cards
• More - Settings, sync, and about
            """) }
            
            item { GuideSection("Studying Flashcards", """
On study screens (To Study, Unsure, Learned Study):
1. Tap the card to flip between term and definition
2. Swipe left or right to navigate between cards
3. Use the buttons at the bottom to mark your progress:
   - "Unsure" moves the card to the Unsure pile
   - "Learned" moves it to your Learned pile
   - "Relearn" moves it back to To Study

The speaker icon reads the term aloud using text-to-speech.
            """) }
            
            item { GuideSection("Term Breakdowns", """
Many Korean terms are compound words. Tap the "Breakdown" button to see:
• Individual parts of the word
• Meaning of each part
• Literal translation
• Optional notes

Admins can use AI (ChatGPT or Gemini) to auto-generate breakdowns.
            """) }
            
            item { GuideSection("Custom Study Sets", """
Star your favorite or difficult cards to create a custom study set:
1. Tap the star icon on any card
2. Go to the Custom tab to study only starred cards
3. Custom sets have their own progress tracking
            """) }
            
            item { GuideSection("Cloud Sync", """
Sync your progress across devices:
1. Go to More > Login
2. Enter your server URL, username, and password
3. Use More > Sync Progress to push/pull your data

Settings:
• Auto-pull on login - Downloads progress when you sign in
• Auto-push changes - Uploads progress automatically
            """) }
            
            item { GuideSection("Settings", """
Customize your experience in More > Settings:

Display:
• Show group/subgroup labels
• Definition first (reverse card order)
• Show breakdown on definition side

Sorting & Randomization:
• Sort by original order, alphabetical, or by group
• Randomize cards in each study pile

Voice:
• Adjust speech rate
• Speak pronunciation only
            """) }
            
            item { GuideSection("Contact & Support", """
Created by Sidney Shelton

For questions, feature requests, or bug reports:
Email: Sidscri@yahoo.com

Thank you for using Kenpo Vocabulary Flashcards!
            """) }
            
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun GuideSection(title: String, content: String) {
    Card(colors = CardDefaults.cardColors(containerColor = DarkPanel), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AccentBlue)
            Spacer(Modifier.height(8.dp))
            Text(content.trimIndent(), color = DarkText, fontSize = 13.sp, lineHeight = 20.sp)
        }
    }
}

// ==================== MANAGE DECKS SCREEN ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageDecksScreen(nav: NavHostController, repo: Repository) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val adminSettings by repo.adminSettingsFlow().collectAsState(initial = AdminSettings())
    LaunchedEffect(Unit) { repo.refreshAdminStatus() }
    val decks by repo.decksFlow().collectAsState(initial = listOf(StudyDeck.KENPO_DEFAULT))
    val deckSettings by repo.deckSettingsFlow().collectAsState(initial = DeckSettings())
    val userCards by repo.userCardsFlow().collectAsState(initial = emptyList())
    
    var selectedTab by remember { mutableStateOf(0) }  // 0=Switch, 1=Add Cards, 2=Create Deck, 3=Redeem
    var statusMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var redeemCode by remember { mutableStateOf("") }
    
    // Add Card form state
    var newTerm by remember { mutableStateOf("") }
    var newDefinition by remember { mutableStateOf("") }
    var newPronunciation by remember { mutableStateOf("") }
    var newGroup by remember { mutableStateOf("") }
    var selectedDeckForAdd by remember { mutableStateOf("kenpo") }
    var maxGroups by remember { mutableStateOf("10") }
    
    // AI Generation state
    var aiDefinitionOptions by remember { mutableStateOf<List<String>>(emptyList()) }
    var aiPronunciationOption by remember { mutableStateOf("") }
    var aiGroupOptions by remember { mutableStateOf<List<String>>(emptyList()) }
    var showDefinitionDropdown by remember { mutableStateOf(false) }
    var showGroupDropdown by remember { mutableStateOf(false) }
    var isGeneratingAi by remember { mutableStateOf(false) }
    
    // User cards viewing/editing state
    var showUserCards by remember { mutableStateOf(false) }
    var editingCard by remember { mutableStateOf<FlashCard?>(null) }
    var editTerm by remember { mutableStateOf("") }
    var editDefinition by remember { mutableStateOf("") }
    var editPronunciation by remember { mutableStateOf("") }
    var editGroup by remember { mutableStateOf("") }
    
    // Create Deck form state
    var newDeckName by remember { mutableStateOf("") }
    var newDeckDescription by remember { mutableStateOf("") }
    var createMethod by remember { mutableStateOf("ai_search") }  // ai_search, upload_image, upload_document
    var aiSearchKeywords by remember { mutableStateOf("") }
    var aiMaxCards by remember { mutableStateOf("20") }
    var showAiResults by remember { mutableStateOf(false) }
    var aiGeneratedTerms by remember { mutableStateOf<List<AiGeneratedTerm>>(emptyList()) }
    var selectedAiTerms by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var uploadedFileName by remember { mutableStateOf("") }
    var uploadedCreateMethod by remember { mutableStateOf("") }  // Track which method the file was selected for
    
    // File picker launchers
    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val fileName = uri.lastPathSegment ?: "image"
            uploadedFileName = fileName
            uploadedCreateMethod = "upload_image"
            statusMessage = "Image selected: $fileName"
        }
    }
    
    val documentPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val fileName = uri.lastPathSegment ?: "document"
            uploadedFileName = fileName
            uploadedCreateMethod = "upload_document"
            statusMessage = "Document selected: $fileName"
        }
    }
    
    // AI access check
    val hasAiAccess = adminSettings.chatGptEnabled || adminSettings.geminiEnabled
    val apiKey = if (adminSettings.chatGptEnabled) adminSettings.chatGptApiKey else adminSettings.geminiApiKey
    val aiModel = if (adminSettings.chatGptEnabled) adminSettings.chatGptModel else adminSettings.geminiModel
    
    // Get existing groups from all cards for AI group suggestions
    val allCards by repo.allCardsFlow().collectAsState(initial = emptyList())
    val existingGroups = remember(allCards) { allCards.map { it.group }.distinct().sorted() }
    
    Scaffold(
        topBar = { 
            TopAppBar(
                title = { Text("Edit Decks") }, 
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkPanel),
                navigationIcon = { IconButton({ nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Back") } }
            ) 
        },
        bottomBar = { NavBar(nav, Route.ManageDecks.path) }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp)) {
            // Tab Row
            TabRow(selectedTabIndex = selectedTab, containerColor = DarkPanel) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SwapHoriz, "Switch", Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Switch", fontSize = 12.sp)
                    }
                }
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Add, "Add", Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Cards", fontSize = 12.sp)
                    }
                }
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CreateNewFolder, "Create", Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Create", fontSize = 12.sp)
                    }
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            // Status message
            if (statusMessage.isNotBlank()) {
                Card(colors = CardDefaults.cardColors(containerColor = if (statusMessage.contains("Error")) Color(0xFF3D1212) else DarkPanel2), modifier = Modifier.fillMaxWidth()) {
                    Text(statusMessage, color = if (statusMessage.contains("Error")) Color.Red else AccentBlue, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
                }
                Spacer(Modifier.height(8.dp))
            }
            
            // Tab Content
            when (selectedTab) {
                // ========== TAB 0: SWITCH DECKS ==========
                0 -> {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        Text("Switch Study Subject", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                        Spacer(Modifier.height(4.dp))
                        Text("Select which flashcard deck to study", color = DarkMuted, fontSize = 11.sp)
                        Spacer(Modifier.height(12.dp))
                        
                        // Current deck indicator
                        Card(colors = CardDefaults.cardColors(containerColor = AccentGood), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, "Active", tint = Color.White)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text("Current: ${decks.find { it.id == deckSettings.activeDeckId }?.name ?: "Kenpo Vocabulary"}", color = Color.White, fontWeight = FontWeight.Bold)
                                    Text("${decks.find { it.id == deckSettings.activeDeckId }?.cardCount ?: 88} cards", color = DarkMuted, fontSize = 11.sp)
                                }
                            }
                        }
                        
                        Spacer(Modifier.height(16.dp))
                        Text("Available Decks", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                        Spacer(Modifier.height(8.dp))
                        
                        // List all decks
                        decks.forEach { deck ->
                            val isActive = deck.id == deckSettings.activeDeckId
                            Card(
                                colors = CardDefaults.cardColors(containerColor = if (isActive) DarkPanel2 else DarkPanel),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                                    if (!isActive) {
                                        scope.launch {
                                            repo.saveDeckSettings(deckSettings.copy(activeDeckId = deck.id))
                                            statusMessage = "Switched to ${deck.name}"
                                        }
                                    }
                                }
                            ) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (isActive) {
                                        Icon(Icons.Default.RadioButtonChecked, "Selected", tint = AccentBlue)
                                    } else {
                                        Icon(Icons.Default.RadioButtonUnchecked, "Not Selected", tint = DarkMuted)
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(deck.name, fontWeight = FontWeight.Bold, color = Color.White)
                                            if (deck.isBuiltIn) {
                                                Spacer(Modifier.width(8.dp))
                                                AssistChip(onClick = {}, label = { Text("Built-in", fontSize = 9.sp) }, modifier = Modifier.height(20.dp))
                                            }
                                            if (deck.isDefault) {
                                                Spacer(Modifier.width(4.dp))
                                                Icon(Icons.Default.Star, "Default", tint = Color.Yellow, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                        Text(deck.description, color = DarkMuted, fontSize = 11.sp)
                                        Text("${deck.cardCount} cards", color = DarkMuted, fontSize = 10.sp)
                                    }
                                    if (!deck.isBuiltIn) {
                                        IconButton({ 
                                            scope.launch { 
                                                repo.deleteDeck(deck.id)
                                                statusMessage = "Deleted ${deck.name}"
                                            }
                                        }) {
                                            Icon(Icons.Default.Delete, "Delete", tint = Color.Red)
                                        }
                                    }
                                }
                            }
                        }
                        
                        if (decks.size == 1) {
                            Spacer(Modifier.height(12.dp))
                            Text("Create new decks in the 'Create' tab to study different subjects!", color = DarkMuted, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        }
                        
                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(color = DarkBorder)
                        Spacer(Modifier.height(12.dp))
                        
                        // View Deleted Cards button
                        OutlinedButton({ nav.navigate(Route.Deleted.path) }, Modifier.fillMaxWidth()) { 
                            Icon(Icons.Default.Delete, "Deleted"); Spacer(Modifier.width(8.dp))
                            Text("View Deleted Cards") 
                        }
                    }
                }
                
                // ========== TAB 1: ADD CARDS ==========
                1 -> {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        Text("Add Cards to Deck", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                        Spacer(Modifier.height(4.dp))
                        Text("Manually add terms with optional AI assistance", color = DarkMuted, fontSize = 11.sp)
                        Spacer(Modifier.height(12.dp))
                        
                        // Select deck dropdown
                        Text("Add to Deck", fontSize = 12.sp, color = Color.White)
                        Spacer(Modifier.height(4.dp))
                        var showDeckDropdown by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton({ showDeckDropdown = true }, Modifier.fillMaxWidth()) {
                                Text(decks.find { it.id == selectedDeckForAdd }?.name ?: "Select Deck")
                                Spacer(Modifier.weight(1f))
                                Icon(Icons.Default.ArrowDropDown, "Select")
                            }
                            DropdownMenu(showDeckDropdown, { showDeckDropdown = false }) {
                                decks.forEach { deck ->
                                    DropdownMenuItem(
                                        text = { Text(deck.name, color = Color.White) },
                                        onClick = { selectedDeckForAdd = deck.id; showDeckDropdown = false }
                                    )
                                }
                            }
                        }
                        
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = DarkBorder)
                        Spacer(Modifier.height(12.dp))
                        
                        // Term input
                        OutlinedTextField(
                            value = newTerm,
                            onValueChange = { newTerm = it; aiDefinitionOptions = emptyList(); aiGroupOptions = emptyList(); aiPronunciationOption = "" },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Term *") },
                            singleLine = true,
                            placeholder = { Text("e.g., Taekwondo", color = DarkMuted) }
                        )
                        
                        Spacer(Modifier.height(8.dp))
                        
                        // Definition input with AI Generate button
                        OutlinedTextField(
                            value = newDefinition,
                            onValueChange = { newDefinition = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Definition *") },
                            placeholder = { Text("e.g., The Way of the Foot and Fist", color = DarkMuted) },
                            minLines = 2
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            Button(
                                onClick = {
                                    if (newTerm.isBlank()) {
                                        statusMessage = "Error: Enter a term first"
                                        return@Button
                                    }
                                    if (!hasAiAccess) {
                                        statusMessage = "Error: Configure AI in Admin Settings"
                                        return@Button
                                    }
                                    isGeneratingAi = true
                                    scope.launch {
                                        try {
                                            val result = AiGenerationHelper.generateDefinitions(apiKey, newTerm, aiModel, adminSettings.chatGptEnabled)
                                            aiDefinitionOptions = result
                                            if (result.isNotEmpty()) {
                                                newDefinition = result[0]
                                                showDefinitionDropdown = result.size > 1
                                                statusMessage = "Generated ${result.size} definition options"
                                            } else {
                                                statusMessage = "AI could not generate definitions"
                                            }
                                        } catch (e: Exception) {
                                            statusMessage = "Error: ${e.message}"
                                        }
                                        isGeneratingAi = false
                                    }
                                },
                                enabled = hasAiAccess && newTerm.isNotBlank() && !isGeneratingAi,
                                modifier = Modifier.height(32.dp)
                            ) {
                                if (isGeneratingAi) {
                                    CircularProgressIndicator(Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.AutoAwesome, "AI", Modifier.size(14.dp))
                                }
                                Spacer(Modifier.width(4.dp))
                                Text("Generate", fontSize = 10.sp)
                            }
                            if (aiDefinitionOptions.size > 1) {
                                Spacer(Modifier.width(8.dp))
                                Box {
                                    OutlinedButton({ showDefinitionDropdown = true }, Modifier.height(32.dp)) {
                                        Text("${aiDefinitionOptions.size} options", fontSize = 10.sp)
                                        Icon(Icons.Default.ArrowDropDown, "Select", Modifier.size(14.dp))
                                    }
                                    DropdownMenu(showDefinitionDropdown, { showDefinitionDropdown = false }) {
                                        aiDefinitionOptions.forEachIndexed { idx, def ->
                                            DropdownMenuItem(
                                                text = { Text(def.take(50) + if (def.length > 50) "..." else "", color = Color.White, fontSize = 11.sp) },
                                                onClick = { newDefinition = def; showDefinitionDropdown = false }
                                            )
                                        }
                                    }
                                }
                            }
                            if (!hasAiAccess) {
                                Spacer(Modifier.width(4.dp))
                                Text("(Configure AI in Admin)", fontSize = 9.sp, color = DarkMuted)
                            }
                        }
                        
                        Spacer(Modifier.height(8.dp))
                        
                        // Pronunciation input with AI Generate button
                        OutlinedTextField(
                            value = newPronunciation,
                            onValueChange = { newPronunciation = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Pronunciation (optional)") },
                            singleLine = true,
                            placeholder = { Text("e.g., tay-kwon-doh", color = DarkMuted) }
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            Button(
                                onClick = {
                                    if (newTerm.isBlank()) {
                                        statusMessage = "Error: Enter a term first"
                                        return@Button
                                    }
                                    isGeneratingAi = true
                                    scope.launch {
                                        try {
                                            val result = AiGenerationHelper.generatePronunciation(apiKey, newTerm, aiModel, adminSettings.chatGptEnabled)
                                            if (result.isNotBlank()) {
                                                newPronunciation = result
                                                aiPronunciationOption = result
                                                statusMessage = "Generated pronunciation"
                                            }
                                        } catch (e: Exception) {
                                            statusMessage = "Error: ${e.message}"
                                        }
                                        isGeneratingAi = false
                                    }
                                },
                                enabled = hasAiAccess && newTerm.isNotBlank() && !isGeneratingAi,
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.AutoAwesome, "AI", Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Generate", fontSize = 10.sp)
                            }
                        }
                        
                        Spacer(Modifier.height(8.dp))
                        
                        // Group input with AI Generate button
                        OutlinedTextField(
                            value = newGroup,
                            onValueChange = { newGroup = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Group (optional)") },
                            singleLine = true,
                            placeholder = { Text("e.g., Martial Arts Styles", color = DarkMuted) }
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            Button(
                                onClick = {
                                    if (newTerm.isBlank()) {
                                        statusMessage = "Error: Enter a term first"
                                        return@Button
                                    }
                                    isGeneratingAi = true
                                    scope.launch {
                                        try {
                                            val result = AiGenerationHelper.generateGroups(apiKey, newTerm, existingGroups, maxGroups.toIntOrNull() ?: 10, aiModel, adminSettings.chatGptEnabled)
                                            aiGroupOptions = result
                                            if (result.isNotEmpty()) {
                                                newGroup = result[0]
                                                showGroupDropdown = result.size > 1
                                                statusMessage = "Generated ${result.size} group options"
                                            }
                                        } catch (e: Exception) {
                                            statusMessage = "Error: ${e.message}"
                                        }
                                        isGeneratingAi = false
                                    }
                                },
                                enabled = hasAiAccess && newTerm.isNotBlank() && !isGeneratingAi,
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.AutoAwesome, "AI", Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Generate", fontSize = 10.sp)
                            }
                            if (aiGroupOptions.size > 1) {
                                Spacer(Modifier.width(8.dp))
                                Box {
                                    OutlinedButton({ showGroupDropdown = true }, Modifier.height(32.dp)) {
                                        Text("${aiGroupOptions.size} options", fontSize = 10.sp)
                                        Icon(Icons.Default.ArrowDropDown, "Select", Modifier.size(14.dp))
                                    }
                                    DropdownMenu(showGroupDropdown, { showGroupDropdown = false }) {
                                        aiGroupOptions.forEach { grp ->
                                            DropdownMenuItem(
                                                text = { Text(grp, color = Color.White, fontSize = 11.sp) },
                                                onClick = { newGroup = grp; showGroupDropdown = false }
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("Max:", fontSize = 10.sp, color = DarkMuted)
                            Spacer(Modifier.width(4.dp))
                            OutlinedTextField(
                                value = maxGroups,
                                onValueChange = { maxGroups = it.filter { c -> c.isDigit() }.take(2) },
                                modifier = Modifier.width(45.dp).height(32.dp),
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(fontSize = 10.sp)
                            )
                        }
                        
                        Spacer(Modifier.height(16.dp))
                        
                        // Add Card Button
                        Button(
                            onClick = {
                                if (newTerm.isBlank()) {
                                    statusMessage = "Error: Term is required"
                                    return@Button
                                }
                                if (newDefinition.isBlank()) {
                                    statusMessage = "Error: Definition is required"
                                    return@Button
                                }
                                isLoading = true
                                scope.launch {
                                    val cardId = "${selectedDeckForAdd}_${System.currentTimeMillis()}"
                                    val newCard = FlashCard(
                                        id = cardId,
                                        group = newGroup.ifBlank { "General" },
                                        subgroup = null,
                                        term = newTerm,
                                        pron = newPronunciation.takeIf { it.isNotBlank() },
                                        meaning = newDefinition,
                                        deckId = selectedDeckForAdd
                                    )
                                    repo.addUserCard(newCard)
                                    
                                    statusMessage = "Added: $newTerm"
                                    newTerm = ""
                                    newDefinition = ""
                                    newPronunciation = ""
                                    newGroup = ""
                                    aiDefinitionOptions = emptyList()
                                    aiGroupOptions = emptyList()
                                    aiPronunciationOption = ""
                                    isLoading = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Add, "Add")
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(if (isLoading) "Adding..." else "Add Card")
                        }
                        
                        // User-Added Cards Section
                        if (userCards.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider(color = DarkBorder)
                            Spacer(Modifier.height(12.dp))
                            
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("User-Added Cards (${userCards.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                                OutlinedButton({ showUserCards = !showUserCards }, Modifier.height(32.dp)) {
                                    Text(if (showUserCards) "Hide" else "Show", fontSize = 10.sp)
                                    Icon(if (showUserCards) Icons.Default.ExpandLess else Icons.Default.ExpandMore, "Toggle", Modifier.size(16.dp))
                                }
                            }
                            
                            if (showUserCards) {
                                Spacer(Modifier.height(8.dp))
                                userCards.forEach { card ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = DarkPanel),
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                    ) {
                                        Column(Modifier.padding(12.dp)) {
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                Column(Modifier.weight(1f)) {
                                                    Text(card.term, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                                                    if (!card.pron.isNullOrBlank()) {
                                                        Text("(${card.pron})", color = DarkMuted, fontSize = 10.sp)
                                                    }
                                                    Text(card.meaning, color = DarkText, fontSize = 11.sp, maxLines = 2)
                                                    Text("Group: ${card.group}", color = DarkMuted, fontSize = 9.sp)
                                                }
                                                Row {
                                                    IconButton({
                                                        editingCard = card
                                                        editTerm = card.term
                                                        editDefinition = card.meaning
                                                        editPronunciation = card.pron ?: ""
                                                        editGroup = card.group
                                                    }, Modifier.size(32.dp)) {
                                                        Icon(Icons.Default.Edit, "Edit", tint = AccentBlue, modifier = Modifier.size(18.dp))
                                                    }
                                                    IconButton({
                                                        scope.launch {
                                                            repo.deleteUserCard(card.id)
                                                            statusMessage = "Deleted: ${card.term}"
                                                        }
                                                    }, Modifier.size(32.dp)) {
                                                        Icon(Icons.Default.Delete, "Delete", tint = Color.Red, modifier = Modifier.size(18.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    // Edit Card Dialog
                    if (editingCard != null) {
                        Dialog(onDismissRequest = { editingCard = null }) {
                            Card(Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = DarkPanel)) {
                                Column(Modifier.padding(16.dp)) {
                                    Text("Edit Card", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                                    Spacer(Modifier.height(12.dp))
                                    OutlinedTextField(editTerm, { editTerm = it }, Modifier.fillMaxWidth(), label = { Text("Term") }, singleLine = true)
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedTextField(editDefinition, { editDefinition = it }, Modifier.fillMaxWidth(), label = { Text("Definition") }, minLines = 2)
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedTextField(editPronunciation, { editPronunciation = it }, Modifier.fillMaxWidth(), label = { Text("Pronunciation") }, singleLine = true)
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedTextField(editGroup, { editGroup = it }, Modifier.fillMaxWidth(), label = { Text("Group") }, singleLine = true)
                                    Spacer(Modifier.height(16.dp))
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        TextButton({ editingCard = null }) { Text("Cancel") }
                                        Spacer(Modifier.width(8.dp))
                                        Button({
                                            scope.launch {
                                                val updated = editingCard!!.copy(
                                                    term = editTerm,
                                                    meaning = editDefinition,
                                                    pron = editPronunciation.takeIf { it.isNotBlank() },
                                                    group = editGroup.ifBlank { "General" }
                                                )
                                                repo.updateUserCard(updated)
                                                statusMessage = "Updated: ${editTerm}"
                                                editingCard = null
                                            }
                                        }) { Text("Save") }
                                    }
                                }
                            }
                        }
                    }
                }
                
                // ========== TAB 2: CREATE DECK ==========
                2 -> {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        Text("Create New Deck", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                        Spacer(Modifier.height(4.dp))
                        Text("Create a new study subject from various sources", color = DarkMuted, fontSize = 11.sp)
                        Spacer(Modifier.height(12.dp))
                        
                        // Deck name and description
                        OutlinedTextField(
                            value = newDeckName,
                            onValueChange = { newDeckName = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Deck Name *") },
                            singleLine = true,
                            placeholder = { Text("e.g., Spanish Vocabulary", color = DarkMuted) }
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newDeckDescription,
                            onValueChange = { newDeckDescription = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Description") },
                            singleLine = true,
                            placeholder = { Text("e.g., Common Spanish words and phrases", color = DarkMuted) }
                        )
                        
                        Spacer(Modifier.height(16.dp))
                        Text("Create Method", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                        Spacer(Modifier.height(8.dp))
                        
                        // Method selection cards
                        // AI Search
                        Card(
                            colors = CardDefaults.cardColors(containerColor = if (createMethod == "ai_search") DarkPanel2 else DarkPanel),
                            modifier = Modifier.fillMaxWidth().clickable { createMethod = "ai_search" }
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = createMethod == "ai_search", onClick = { createMethod = "ai_search" })
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.Default.Search, "AI Search", tint = AccentBlue)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text("AI Search", fontWeight = FontWeight.Bold, color = Color.White)
                                    Text("Search for terms using keywords", color = DarkMuted, fontSize = 11.sp)
                                }
                            }
                        }
                        
                        Spacer(Modifier.height(8.dp))
                        
                        // Upload Image
                        Card(
                            colors = CardDefaults.cardColors(containerColor = if (uploadedCreateMethod == "upload_image") DarkPanel2 else DarkPanel),
                            modifier = Modifier.fillMaxWidth().clickable { createMethod = "upload_image" }
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = uploadedCreateMethod == "upload_image", onClick = { createMethod = "upload_image" })
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.Default.Image, "Image", tint = AccentBlue)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text("Upload Image", fontWeight = FontWeight.Bold, color = Color.White)
                                    Text("Scan photos of study materials", color = DarkMuted, fontSize = 11.sp)
                                }
                            }
                        }
                        
                        Spacer(Modifier.height(8.dp))
                        
                        // Upload Document
                        Card(
                            colors = CardDefaults.cardColors(containerColor = if (uploadedCreateMethod == "upload_document") DarkPanel2 else DarkPanel),
                            modifier = Modifier.fillMaxWidth().clickable { createMethod = "upload_document" }
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = uploadedCreateMethod == "upload_document", onClick = { createMethod = "upload_document" })
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.Default.Description, "Document", tint = AccentBlue)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text("Upload Document", fontWeight = FontWeight.Bold, color = Color.White)
                                    Text("PDF, Word, Text, CSV, Excel files", color = DarkMuted, fontSize = 11.sp)
                                }
                            }
                        }
                        
                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(color = DarkBorder)
                        Spacer(Modifier.height(12.dp))
                        
                        // Method-specific options
                        when (createMethod) {
                            "ai_search" -> {
                                Text("AI Search Settings", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = aiSearchKeywords,
                                    onValueChange = { aiSearchKeywords = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Search Keywords *") },
                                    placeholder = { Text("e.g., medical terminology, anatomy", color = DarkMuted) },
                                    minLines = 2
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Max cards to generate:", fontSize = 12.sp, color = Color.White)
                                    Spacer(Modifier.width(8.dp))
                                    OutlinedTextField(
                                        value = aiMaxCards,
                                        onValueChange = { aiMaxCards = it.filter { c -> c.isDigit() }.take(3) },
                                        modifier = Modifier.width(70.dp),
                                        singleLine = true
                                    )
                                }
                                Spacer(Modifier.height(12.dp))
                                
                                Button(
                                    onClick = {
                                        if (aiSearchKeywords.isBlank()) {
                                            statusMessage = "Error: Enter search keywords"
                                            return@Button
                                        }
                                        if (!hasAiAccess) {
                                            statusMessage = "Error: Configure AI in Admin Settings first"
                                            return@Button
                                        }
                                        isLoading = true
                                        scope.launch {
                                            try {
                                                val maxCards = aiMaxCards.toIntOrNull() ?: 20
                                                val results = AiGenerationHelper.searchAndGenerateTerms(
                                                    apiKey, aiSearchKeywords, maxCards, aiModel, adminSettings.chatGptEnabled
                                                )
                                                if (results.isNotEmpty()) {
                                                    aiGeneratedTerms = results
                                                    selectedAiTerms = emptySet()
                                                    showAiResults = true
                                                    statusMessage = "Generated ${results.size} terms. Select which to add."
                                                } else {
                                                    statusMessage = "AI could not generate terms. Try different keywords."
                                                }
                                            } catch (e: Exception) {
                                                statusMessage = "Error: ${e.message}"
                                            }
                                            isLoading = false
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !isLoading && hasAiAccess
                                ) {
                                    if (isLoading) {
                                        CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.Search, "Search")
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (isLoading) "Searching..." else "Search with AI")
                                }
                                
                                if (!hasAiAccess) {
                                    Spacer(Modifier.height(8.dp))
                                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF3D2D12)), modifier = Modifier.fillMaxWidth()) {
                                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Warning, "Warning", tint = Color.Yellow)
                                            Spacer(Modifier.width(8.dp))
                                            Text("AI not configured. Go to Admin Settings to add API keys.", color = Color.Yellow, fontSize = 11.sp)
                                        }
                                    }
                                }
                                
                                // Show AI results for selection
                                if (showAiResults && aiGeneratedTerms.isNotEmpty()) {
                                    Spacer(Modifier.height(16.dp))
                                    Text("Generated Terms (${selectedAiTerms.size}/${aiGeneratedTerms.size} selected)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                                    Spacer(Modifier.height(8.dp))
                                    
                                    aiGeneratedTerms.forEachIndexed { idx, term ->
                                        val isSelected = idx in selectedAiTerms
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = if (isSelected) DarkPanel2 else DarkPanel),
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable {
                                                selectedAiTerms = if (isSelected) selectedAiTerms - idx else selectedAiTerms + idx
                                            }
                                        ) {
                                            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(isSelected, { 
                                                    selectedAiTerms = if (isSelected) selectedAiTerms - idx else selectedAiTerms + idx 
                                                })
                                                Column(Modifier.weight(1f)) {
                                                    Text(term.term, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                                                    Text(term.definition, fontSize = 10.sp, color = DarkMuted, maxLines = 1)
                                                }
                                                AssistChip(onClick = {}, label = { Text(term.group, fontSize = 9.sp) }, modifier = Modifier.height(20.dp))
                                            }
                                        }
                                    }
                                    
                                    Spacer(Modifier.height(8.dp))
                                    Row {
                                        OutlinedButton({ selectedAiTerms = aiGeneratedTerms.indices.toSet() }, Modifier.weight(1f)) {
                                            Text("Select All", fontSize = 11.sp)
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        OutlinedButton({ selectedAiTerms = emptySet() }, Modifier.weight(1f)) {
                                            Text("Clear", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                            
                            "upload_image" -> {
                                Text("Upload Image", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                                Spacer(Modifier.height(8.dp))
                                Text("AI will scan the image and extract terms, definitions, and pronunciations.", color = DarkMuted, fontSize = 11.sp)
                                Spacer(Modifier.height(12.dp))
                                
                                // File selection button
                                OutlinedButton(
                                    onClick = { imagePickerLauncher.launch("image/*") },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Image, "Image")
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (uploadedFileName.isNotBlank() && uploadedCreateMethod == "upload_image") "Change Image" else "Select Image")
                                }
                                
                                // Show selected file
                                if (uploadedFileName.isNotBlank() && uploadedCreateMethod == "upload_image") {
                                    Spacer(Modifier.height(8.dp))
                                    Card(colors = CardDefaults.cardColors(containerColor = DarkPanel2), modifier = Modifier.fillMaxWidth()) {
                                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.CheckCircle, "Selected", tint = AccentGood)
                                            Spacer(Modifier.width(8.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text("Selected:", color = DarkMuted, fontSize = 10.sp)
                                                Text(uploadedFileName, color = Color.White, fontSize = 12.sp, maxLines = 1)
                                            }
                                            IconButton({ uploadedFileName = "" }, Modifier.size(28.dp)) {
                                                Icon(Icons.Default.Close, "Remove", tint = DarkMuted, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                    
                                    Spacer(Modifier.height(12.dp))
                                    Button(
                                        onClick = {
                                            if (!hasAiAccess) {
                                                statusMessage = "Error: Configure AI in Admin Settings first"
                                                return@Button
                                            }
                                            isLoading = true
                                            statusMessage = "Scanning image with AI..."
                                            scope.launch {
                                                // TODO: Implement actual image OCR + AI extraction
                                                // For now, show placeholder message
                                                statusMessage = "Image scanning coming soon. Use AI Search for now."
                                                isLoading = false
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = hasAiAccess && !isLoading
                                    ) {
                                        if (isLoading) {
                                            CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                        } else {
                                            Icon(Icons.Default.DocumentScanner, "Scan")
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Text(if (isLoading) "Scanning..." else "Scan Image with AI")
                                    }
                                }
                                
                                if (!hasAiAccess) {
                                    Spacer(Modifier.height(8.dp))
                                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF3D2D12)), modifier = Modifier.fillMaxWidth()) {
                                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Warning, "Warning", tint = Color.Yellow)
                                            Spacer(Modifier.width(8.dp))
                                            Text("AI not configured. Go to Admin Settings to add API keys.", color = Color.Yellow, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                            
                            "upload_document" -> {
                                Text("Upload Document", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                                Spacer(Modifier.height(8.dp))
                                Text("Supported: PDF, Word (.docx), Text (.txt), CSV, Excel (.xlsx)", color = DarkMuted, fontSize = 11.sp)
                                Spacer(Modifier.height(12.dp))
                                
                                // File selection button
                                OutlinedButton(
                                    onClick = { documentPickerLauncher.launch("*/*") },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Description, "Document")
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (uploadedFileName.isNotBlank() && uploadedCreateMethod == "upload_document") "Change Document" else "Select Document")
                                }
                                
                                // Show selected file
                                if (uploadedFileName.isNotBlank() && uploadedCreateMethod == "upload_document") {
                                    Spacer(Modifier.height(8.dp))
                                    Card(colors = CardDefaults.cardColors(containerColor = DarkPanel2), modifier = Modifier.fillMaxWidth()) {
                                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.CheckCircle, "Selected", tint = AccentGood)
                                            Spacer(Modifier.width(8.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text("Selected:", color = DarkMuted, fontSize = 10.sp)
                                                Text(uploadedFileName, color = Color.White, fontSize = 12.sp, maxLines = 1)
                                            }
                                            IconButton({ uploadedFileName = "" }, Modifier.size(28.dp)) {
                                                Icon(Icons.Default.Close, "Remove", tint = DarkMuted, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                    
                                    Spacer(Modifier.height(12.dp))
                                    Button(
                                        onClick = {
                                            if (!hasAiAccess) {
                                                statusMessage = "Error: Configure AI in Admin Settings first"
                                                return@Button
                                            }
                                            isLoading = true
                                            statusMessage = "Processing document with AI..."
                                            scope.launch {
                                                // TODO: Implement actual document parsing + AI extraction
                                                // For now, show placeholder message
                                                statusMessage = "Document processing coming soon. Use AI Search for now."
                                                isLoading = false
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = hasAiAccess && !isLoading
                                    ) {
                                        if (isLoading) {
                                            CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                        } else {
                                            Icon(Icons.Default.DocumentScanner, "Process")
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Text(if (isLoading) "Processing..." else "Process Document with AI")
                                    }
                                }
                                
                                if (!hasAiAccess) {
                                    Spacer(Modifier.height(8.dp))
                                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF3D2D12)), modifier = Modifier.fillMaxWidth()) {
                                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Warning, "Warning", tint = Color.Yellow)
                                            Spacer(Modifier.width(8.dp))
                                            Text("AI not configured. Go to Admin Settings to add API keys.", color = Color.Yellow, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Create Deck Button
                        if (showAiResults && selectedAiTerms.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    if (newDeckName.isBlank()) {
                                        statusMessage = "Error: Deck name is required"
                                        return@Button
                                    }
                                    isLoading = true
                                    scope.launch {
                                        val deckId = "deck_${System.currentTimeMillis()}"
                                        val selectedTerms = selectedAiTerms.map { aiGeneratedTerms[it] }
                                        
                                        // Create deck
                                        val newDeck = StudyDeck(
                                            id = deckId,
                                            name = newDeckName,
                                            description = newDeckDescription.ifBlank { "Created from AI search" },
                                            isDefault = false,
                                            isBuiltIn = false,
                                            sourceFile = null,
                                            cardCount = selectedTerms.size,
                                            createdAt = System.currentTimeMillis(),
                                            updatedAt = System.currentTimeMillis()
                                        )
                                        repo.addDeck(newDeck)
                                        
                                        // Add cards
                                        val cards = selectedTerms.mapIndexed { idx, term ->
                                            FlashCard(
                                                id = "${deckId}_$idx",
                                                group = term.group,
                                                subgroup = null,
                                                term = term.term,
                                                pron = term.pronunciation.takeIf { it.isNotBlank() },
                                                meaning = term.definition,
                                                deckId = deckId
                                            )
                                        }
                                        repo.addUserCards(cards)
                                        
                                        statusMessage = "Created deck '${newDeckName}' with ${selectedTerms.size} cards!"
                                        newDeckName = ""
                                        newDeckDescription = ""
                                        aiSearchKeywords = ""
                                        showAiResults = false
                                        aiGeneratedTerms = emptyList()
                                        selectedAiTerms = emptySet()
                                        isLoading = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentGood),
                                enabled = !isLoading
                            ) {
                                Icon(Icons.Default.Check, "Create")
                                Spacer(Modifier.width(8.dp))
                                Text("Create Deck with ${selectedAiTerms.size} Cards")
                            }
                        }
                    }
                }
            }
        }
    }
}

// Helper data class for AI-generated terms
data class AiGeneratedTerm(
    val term: String,
    val definition: String,
    val pronunciation: String,
    val group: String
)