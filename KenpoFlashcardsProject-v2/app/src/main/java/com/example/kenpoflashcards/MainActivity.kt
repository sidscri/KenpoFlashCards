package com.example.kenpoflashcards

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
    val cardHeight = if (landscape) 180.dp else 260.dp
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
    showGroupFilter: Boolean = true
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
            // Title row with search icon and group filter
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                Row {
                    IconButton({ searchExpanded = !searchExpanded }, Modifier.size(32.dp)) { Icon(Icons.Default.Search, "Search", tint = DarkMuted, modifier = Modifier.size(20.dp)) }
                    if (showGroupFilter) { GroupFilterDropdown(groups, selectedGroup, onGroupSelect) }
                }
            }
            if (searchExpanded) {
                OutlinedTextField(search, onSearchChange, Modifier.fillMaxWidth().height(48.dp), singleLine = true, placeholder = { Text("Search", fontSize = 11.sp) }, colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = DarkPanel2, focusedContainerColor = DarkPanel2), textStyle = LocalTextStyle.current.copy(fontSize = 12.sp))
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
                    IconButton(onCustomToggle, modifier = Modifier.size(36.dp).background(DarkPanel2, RoundedCornerShape(6.dp))) { Icon(if (inCustomSet) Icons.Default.Star else Icons.Default.StarBorder, "Custom", tint = if (inCustomSet) Color.Yellow else DarkMuted, modifier = Modifier.size(18.dp)) }
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
    val allCards by repo.allCardsFlow().collectAsState(initial = emptyList())
    val progress by repo.progressFlow().collectAsState(initial = ProgressState.EMPTY)
    val breakdowns by repo.breakdownsFlow().collectAsState(initial = emptyMap())
    val customSet by repo.customSetFlow().collectAsState(initial = emptySet())
    val settings by repo.settingsAllFlow().collectAsState(initial = StudySettings())
    val adminSettings by repo.adminSettingsFlow().collectAsState(initial = AdminSettings())
    val groups = remember(allCards) { allCards.map { it.group }.distinct().sorted() }
    var search by remember { mutableStateOf("") }
    var showFront by remember { mutableStateOf(true) }
    var index by remember { mutableStateOf(0) }
    var showBreakdown by remember { mutableStateOf(false) }
    val isActive = statusFilter == CardStatus.ACTIVE
    val title = if (isActive) "To Study" else "Unsure"
    val route = if (isActive) Route.Active.path else Route.Unsure.path
    val shouldRandomize = if (isActive) settings.randomizeUnlearned else settings.randomizeUnsure
    val landscape = isLandscape()
    val filteredCards = remember(allCards, progress, search, shouldRandomize, settings.sortMode, settings.studyFilterGroup) {
        val base = allCards.filter { progress.getStatus(it.id) == statusFilter }
        val grouped = if (settings.studyFilterGroup != null) base.filter { it.group == settings.studyFilterGroup } else base
        val searched = if (search.isBlank()) grouped else grouped.filter { it.term.contains(search, true) || it.meaning.contains(search, true) || (it.pron?.contains(search, true) ?: false) }
        sortCards(searched, settings.sortMode, shouldRandomize)
    }
    LaunchedEffect(filteredCards.size) { if (filteredCards.isEmpty()) index = 0 else index = index.coerceIn(0, filteredCards.size - 1); showFront = true }
    val current = filteredCards.getOrNull(index)
    val currentBreakdown = current?.let { breakdowns[it.id] }
    val inCustomSet = current?.let { customSet.contains(it.id) } ?: false
    val atEnd = index >= filteredCards.size - 1 && filteredCards.isNotEmpty()
    
    Scaffold(topBar = { TopAppBar(title = { Text(title) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkPanel), actions = { if (!landscape) { GroupFilterDropdown(groups, settings.studyFilterGroup) { scope.launch { repo.saveSettingsAll(settings.copy(studyFilterGroup = it)) } }; Spacer(Modifier.width(8.dp)) } }) }, bottomBar = { NavBar(nav, route) }) { pad ->
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
                    onReturnTop = { index = 0; showFront = true }
                )
            }
        } else {
            Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 12.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CountsRow(progress, allCards)
                OutlinedTextField(search, { search = it; index = 0; showFront = true }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search") }, colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = DarkPanel2, focusedContainerColor = DarkPanel2))
                Spacer(Modifier.height(6.dp))
                if (current == null) { Text("No ${title.lowercase()} cards.", color = DarkMuted) }
                else {
                    Text("Card ${index + 1} / ${filteredCards.size}", color = DarkMuted, fontSize = 12.sp); Spacer(Modifier.height(4.dp))
                    FlipCard(current, currentBreakdown, showFront, settings, { showFront = !showFront }, { if (index < filteredCards.size - 1) { index++; showFront = true } }, { if (index > 0) { index--; showFront = true } })
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        OutlinedButton({ if (index > 0) { index--; showFront = true } }, enabled = index > 0, modifier = Modifier.weight(1f)) { Text("Prev") }; Spacer(Modifier.width(6.dp))
                        Button({ tts.setRate(settings.speechRate); val text = if (settings.speakPronunciationOnly && !current.pron.isNullOrBlank()) current.pron else current.term + if (!current.pron.isNullOrBlank()) ", ${current.pron}" else ""; tts.speak(text) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.VolumeUp, "Speak") }; Spacer(Modifier.width(6.dp))
                        OutlinedButton({ if (index < filteredCards.size - 1) { index++; showFront = true } }, enabled = index < filteredCards.size - 1, modifier = Modifier.weight(1f)) { Text("Next") }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button({ scope.launch { repo.setStatus(current.id, CardStatus.LEARNED); if (index >= filteredCards.size - 1) index = (filteredCards.size - 2).coerceAtLeast(0); showFront = true } }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = AccentGood)) { Text("Got it ✓") }
                        IconButton({ scope.launch { if (inCustomSet) repo.removeFromCustomSet(current.id) else repo.addToCustomSet(current.id) } }, modifier = Modifier.size(44.dp).background(DarkPanel2, RoundedCornerShape(8.dp))) { Icon(if (inCustomSet) Icons.Default.Star else Icons.Default.StarBorder, "Custom", tint = if (inCustomSet) Color.Yellow else DarkMuted) }
                        IconButton({ showBreakdown = true }, modifier = Modifier.size(44.dp).background(DarkPanel2, RoundedCornerShape(8.dp))) { Icon(Icons.Default.Extension, "Breakdown", tint = if (currentBreakdown?.hasContent() == true) AccentBlue else DarkMuted) }
                        OutlinedButton({ scope.launch { val newStatus = if (isActive) CardStatus.UNSURE else CardStatus.ACTIVE; repo.setStatus(current.id, newStatus); if (index >= filteredCards.size - 1) index = (filteredCards.size - 2).coerceAtLeast(0); showFront = true } }, Modifier.weight(1f)) { Text(if (isActive) "Unsure" else "Relearn") }
                    }
                    if (atEnd) { Spacer(Modifier.height(6.dp)); OutlinedButton({ index = 0; showFront = true }, Modifier.fillMaxWidth()) { Icon(Icons.Default.KeyboardArrowUp, "Top"); Spacer(Modifier.width(4.dp)); Text("Return to Top") } }
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
    val allCards by repo.allCardsFlow().collectAsState(initial = emptyList())
    val progress by repo.progressFlow().collectAsState(initial = ProgressState.EMPTY)
    val breakdowns by repo.breakdownsFlow().collectAsState(initial = emptyMap())
    val customSet by repo.customSetFlow().collectAsState(initial = emptySet())
    val settings by repo.settingsAllFlow().collectAsState(initial = StudySettings())
    val adminSettings by repo.adminSettingsFlow().collectAsState(initial = AdminSettings())
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
    
    Scaffold(topBar = { TopAppBar(title = { Text("Learned") }, colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkPanel), actions = {
        FilterChip(viewMode == LearnedViewMode.LIST, { viewMode = LearnedViewMode.LIST }, { Text("List", fontSize = 10.sp) }); Spacer(Modifier.width(2.dp))
        FilterChip(viewMode == LearnedViewMode.STUDY, { viewMode = LearnedViewMode.STUDY }, { Text("Study", fontSize = 10.sp) }); Spacer(Modifier.width(4.dp))
        if (viewMode == LearnedViewMode.STUDY && !landscape) { GroupFilterDropdown(groups, settings.studyFilterGroup) { scope.launch { repo.saveSettingsAll(settings.copy(studyFilterGroup = it)) } }; Spacer(Modifier.width(4.dp)) }
    }) }, bottomBar = { NavBar(nav, Route.Learned.path) }) { pad ->
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
                if (!landscape) { CountsRow(progress, allCards); Text("Learned: ${learnedCards.size}", color = DarkMuted, fontSize = 11.sp) }
                OutlinedTextField(search, { search = it; index = 0 }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search") }, colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = DarkPanel2, focusedContainerColor = DarkPanel2))
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
                            IconButton({ scope.launch { if (inCustomSet) repo.removeFromCustomSet(current.id) else repo.addToCustomSet(current.id) } }, modifier = Modifier.size(44.dp).background(DarkPanel2, RoundedCornerShape(8.dp))) { Icon(if (inCustomSet) Icons.Default.Star else Icons.Default.StarBorder, "Custom", tint = if (inCustomSet) Color.Yellow else DarkMuted) }
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
    val allCards by repo.allCardsFlow().collectAsState(initial = emptyList())
    val progress by repo.progressFlow().collectAsState(initial = ProgressState.EMPTY)
    val breakdowns by repo.breakdownsFlow().collectAsState(initial = emptyMap())
    val customSet by repo.customSetFlow().collectAsState(initial = emptySet())
    val settings by repo.settingsAllFlow().collectAsState(initial = StudySettings())
    val adminSettings by repo.adminSettingsFlow().collectAsState(initial = AdminSettings())
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
    
    Scaffold(topBar = { TopAppBar(title = { Text("All Cards") }, colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkPanel), actions = {
        if (landscape) {
            IconButton({ searchExpanded = !searchExpanded }) { Icon(Icons.Default.Search, "Search", tint = DarkMuted) }
        }
        OutlinedButton({ showGroupFilter = true }, Modifier.height(32.dp)) { Text(settings.filterGroup ?: "All Groups", fontSize = 10.sp); Icon(Icons.Default.ArrowDropDown, "Filter", Modifier.size(16.dp)) }
        DropdownMenu(showGroupFilter, { showGroupFilter = false }) { groups.forEach { g -> DropdownMenuItem(text = { Text(g, color = Color.White, fontSize = 12.sp) }, onClick = { scope.launch { repo.saveSettingsAll(settings.copy(filterGroup = if (g == "All Groups") null else g)) }; showGroupFilter = false }) } }
        Spacer(Modifier.width(8.dp))
    }) }, bottomBar = { NavBar(nav, Route.AllCards.path) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 12.dp, vertical = 4.dp)) {
            if (!landscape) {
                CountsRow(progress, allCards)
                Text("Total: ${displayedCards.size}", color = DarkMuted, fontSize = 11.sp)
                OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search") }, colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = DarkPanel2, focusedContainerColor = DarkPanel2))
                Spacer(Modifier.height(6.dp))
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Total: ${displayedCards.size}", color = DarkMuted, fontSize = 11.sp)
                    if (searchExpanded) {
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(search, { search = it }, Modifier.weight(1f).height(40.dp), singleLine = true, placeholder = { Text("Search", fontSize = 11.sp) }, colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = DarkPanel2, focusedContainerColor = DarkPanel2), textStyle = LocalTextStyle.current.copy(fontSize = 12.sp))
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(displayedCards, key = { it.id }) { c ->
                    val status = progress.getStatus(c.id); val bd = breakdowns[c.id]; val inCustomSet = customSet.contains(c.id)
                    Card(colors = CardDefaults.cardColors(containerColor = DarkPanel), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(c.term, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                IconButton({ scope.launch { if (inCustomSet) repo.removeFromCustomSet(c.id) else repo.addToCustomSet(c.id) } }, modifier = Modifier.size(28.dp)) { Icon(if (inCustomSet) Icons.Default.Star else Icons.Default.StarBorder, "Custom", tint = if (inCustomSet) Color.Yellow else DarkMuted, modifier = Modifier.size(18.dp)) }
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
    val allCards by repo.allCardsFlow().collectAsState(initial = emptyList())
    val progress by repo.progressFlow().collectAsState(initial = ProgressState.EMPTY)
    val breakdowns by repo.breakdownsFlow().collectAsState(initial = emptyMap())
    val customSet by repo.customSetFlow().collectAsState(initial = emptySet())
    val settings by repo.settingsAllFlow().collectAsState(initial = StudySettings())
    val adminSettings by repo.adminSettingsFlow().collectAsState(initial = AdminSettings())
    var search by remember { mutableStateOf("") }
    var showFront by remember { mutableStateOf(true) }
    var index by remember { mutableStateOf(0) }
    var showBreakdown by remember { mutableStateOf(false) }
    val landscape = isLandscape()
    val customCards = remember(allCards, customSet, search, settings) {
        val inSet = allCards.filter { customSet.contains(it.id) }
        val searched = if (search.isBlank()) inSet else inSet.filter { it.term.contains(search, true) || it.meaning.contains(search, true) }
        sortCards(searched, settings.sortMode, settings.randomizeUnlearned)
    }
    LaunchedEffect(customCards.size) { if (customCards.isEmpty()) index = 0 else index = index.coerceIn(0, customCards.size - 1); showFront = true }
    val current = customCards.getOrNull(index)
    val currentBreakdown = current?.let { breakdowns[it.id] }
    val atEnd = index >= customCards.size - 1 && customCards.isNotEmpty()
    
    Scaffold(topBar = { TopAppBar(title = { Text("Custom Set") }, colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkPanel), actions = { if (customSet.isNotEmpty()) { IconButton({ scope.launch { repo.clearCustomSet() } }) { Icon(Icons.Default.DeleteSweep, "Clear", tint = DarkMuted) } } }) }, bottomBar = { NavBar(nav, Route.CustomSet.path) }) { pad ->
        if (landscape) {
            Box(Modifier.fillMaxSize().padding(pad)) {
                LandscapeStudyLayout(
                    title = "Custom", cardCount = "Card ${index + 1} / ${customCards.size}",
                    current = current, currentBreakdown = currentBreakdown, showFront = showFront, settings = settings,
                    groups = emptyList(), selectedGroup = null, inCustomSet = true, atEnd = atEnd,
                    showSearch = true, search = search, onSearchChange = { search = it; index = 0; showFront = true },
                    onFlip = { showFront = !showFront },
                    onNext = { if (index < customCards.size - 1) { index++; showFront = true } },
                    onPrev = { if (index > 0) { index--; showFront = true } },
                    onSpeak = { tts.setRate(settings.speechRate); val text = if (settings.speakPronunciationOnly && !current?.pron.isNullOrBlank()) current?.pron ?: "" else current?.term ?: ""; tts.speak(text) },
                    onGroupSelect = {},
                    onPrimaryAction = { scope.launch { current?.let { repo.setStatus(it.id, CardStatus.LEARNED); showFront = true } } },
                    primaryActionText = "✓",
                    onSecondaryAction = { scope.launch { current?.let { repo.removeFromCustomSet(it.id); if (index >= customCards.size - 1) index = (customCards.size - 2).coerceAtLeast(0) } } },
                    secondaryActionText = "Remove",
                    onCustomToggle = {},
                    onBreakdown = { showBreakdown = true },
                    onReturnTop = { index = 0; showFront = true },
                    showGroupFilter = false
                )
            }
        } else {
            Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 12.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CountsRow(progress, allCards); Text("Custom: ${customCards.size}", color = DarkMuted, fontSize = 11.sp)
                OutlinedTextField(search, { search = it; index = 0; showFront = true }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search") }, colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = DarkPanel2, focusedContainerColor = DarkPanel2))
                Spacer(Modifier.height(6.dp))
                if (current == null) { Text("No cards in custom set.", color = DarkMuted); Text("Add from All tab via ☆", color = DarkMuted, fontSize = 11.sp) }
                else {
                    Text("Card ${index + 1} / ${customCards.size}", color = DarkMuted, fontSize = 12.sp); Spacer(Modifier.height(4.dp))
                    FlipCard(current, currentBreakdown, showFront, settings, { showFront = !showFront }, { if (index < customCards.size - 1) { index++; showFront = true } }, { if (index > 0) { index--; showFront = true } })
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        OutlinedButton({ if (index > 0) { index--; showFront = true } }, enabled = index > 0, modifier = Modifier.weight(1f)) { Text("Prev") }; Spacer(Modifier.width(6.dp))
                        Button({ tts.setRate(settings.speechRate); val text = if (settings.speakPronunciationOnly && !current.pron.isNullOrBlank()) current.pron else current.term; tts.speak(text) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.VolumeUp, "Speak") }; Spacer(Modifier.width(6.dp))
                        OutlinedButton({ if (index < customCards.size - 1) { index++; showFront = true } }, enabled = index < customCards.size - 1, modifier = Modifier.weight(1f)) { Text("Next") }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button({ scope.launch { repo.setStatus(current.id, CardStatus.LEARNED); showFront = true } }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = AccentGood)) { Text("Got it ✓") }
                        IconButton({ showBreakdown = true }, modifier = Modifier.size(44.dp).background(DarkPanel2, RoundedCornerShape(8.dp))) { Icon(Icons.Default.Extension, "Breakdown", tint = if (currentBreakdown?.hasContent() == true) AccentBlue else DarkMuted) }
                        OutlinedButton({ scope.launch { repo.removeFromCustomSet(current.id); if (index >= customCards.size - 1) index = (customCards.size - 2).coerceAtLeast(0) } }, Modifier.weight(1f)) { Icon(Icons.Default.RemoveCircleOutline, "Remove", Modifier.size(14.dp)); Text("Remove", fontSize = 11.sp) }
                    }
                    if (atEnd) { Spacer(Modifier.height(6.dp)); OutlinedButton({ index = 0; showFront = true }, Modifier.fillMaxWidth()) { Icon(Icons.Default.KeyboardArrowUp, "Top"); Spacer(Modifier.width(4.dp)); Text("Return to Top") } }
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
    val allCards by repo.allCardsFlow().collectAsState(initial = emptyList())
    val progress by repo.progressFlow().collectAsState(initial = ProgressState.EMPTY)
    var search by remember { mutableStateOf("") }
    val deletedCards = remember(allCards, progress, search) {
        val d = allCards.filter { progress.getStatus(it.id) == CardStatus.DELETED }
        if (search.isBlank()) d else d.filter { it.term.contains(search, true) }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Deleted") }, colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkPanel)) }, bottomBar = { NavBar(nav, Route.Deleted.path) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp)) {
            Text("Deleted: ${deletedCards.size}", color = DarkMuted, fontSize = 11.sp)
            OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search") }, colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = DarkPanel2, focusedContainerColor = DarkPanel2))
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
            
            Spacer(Modifier.height(12.dp)); Text("List Views", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
            SettingToggle("Show definitions in All list", settings.showDefinitionsInAllList) { scope.launch { repo.saveSettingsAll(settings.copy(showDefinitionsInAllList = it)) } }
            SettingToggle("Show definitions in Learned list", settings.showDefinitionsInLearnedList) { scope.launch { repo.saveSettingsAll(settings.copy(showDefinitionsInLearnedList = it)) } }
            SettingToggle("Show action buttons in All list", settings.showUnlearnedUnsureButtonsInAllList) { scope.launch { repo.saveSettingsAll(settings.copy(showUnlearnedUnsureButtonsInAllList = it)) } }
            SettingToggle("Show action buttons in Learned list", settings.showRelearnUnsureButtonsInLearnedList) { scope.launch { repo.saveSettingsAll(settings.copy(showRelearnUnsureButtonsInLearnedList = it)) } }
            
            Spacer(Modifier.height(12.dp)); Text("Voice", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
            SettingToggle("Speak pronunciation only", settings.speakPronunciationOnly) { scope.launch { repo.saveSettingsAll(settings.copy(speakPronunciationOnly = it)) } }
            Text("Speech rate: ${String.format("%.1f", settings.speechRate)}x", fontSize = 12.sp, color = DarkMuted)
            Slider(settings.speechRate, { scope.launch { repo.saveSettingsAll(settings.copy(speechRate = it)) } }, valueRange = 0.5f..2.0f, steps = 5, modifier = Modifier.fillMaxWidth())
            Button({ tts.setRate(settings.speechRate); tts.speakTest() }, Modifier.height(36.dp)) { Text("Test Voice", fontSize = 12.sp) }
            
            Spacer(Modifier.height(16.dp)); HorizontalDivider(color = DarkBorder); Spacer(Modifier.height(12.dp))
            
            OutlinedButton({ nav.navigate(Route.Deleted.path) }, Modifier.fillMaxWidth()) { Text("View Deleted Cards") }
            Spacer(Modifier.height(6.dp))
            OutlinedButton({ csvLauncher.launch(arrayOf("text/*", "text/csv")) }, Modifier.fillMaxWidth()) { Text("Import CSV") }
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
    var chatGptKey by remember(adminSettings) { mutableStateOf(adminSettings.chatGptApiKey) }
    var chatGptModel by remember(adminSettings) { mutableStateOf(adminSettings.chatGptModel) }
    var geminiKey by remember(adminSettings) { mutableStateOf(adminSettings.geminiApiKey) }
    var geminiModel by remember(adminSettings) { mutableStateOf(adminSettings.geminiModel) }
    var statusMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var showChatGptModelDropdown by remember { mutableStateOf(false) }
    var showGeminiModelDropdown by remember { mutableStateOf(false) }
    
    // Check if user is admin
    if (!AdminUsers.isAdmin(adminSettings.username)) {
        // Redirect non-admins back to settings
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }
    
    Scaffold(topBar = { TopAppBar(title = { Text("AI Access Settings") }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF8B0000)), navigationIcon = { IconButton({ nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Back") } }) }, bottomBar = { NavBar(nav, Route.Admin.path) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp).verticalScroll(rememberScrollState())) {
            Text("⚠️ Admin Only", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Yellow)
            Spacer(Modifier.height(4.dp))
            Text("API keys are encrypted and shared between Android app and web server.", color = DarkMuted, fontSize = 11.sp)
            
            Spacer(Modifier.height(20.dp)); HorizontalDivider(color = DarkBorder); Spacer(Modifier.height(16.dp))
            
            // ChatGPT API Section
            Text("ChatGPT API", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text("Enable AI-powered breakdown autofill using OpenAI", color = DarkMuted, fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(chatGptKey, { chatGptKey = it }, Modifier.fillMaxWidth(), label = { Text("OpenAI API Key") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), textStyle = LocalTextStyle.current.copy(fontSize = 12.sp))
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
Logged in as: ${adminSettings.username}
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
    var serverUrl by remember(adminSettings) { mutableStateOf(adminSettings.webAppUrl.ifBlank { WebAppSync.DEFAULT_SERVER_URL }) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    
    Scaffold(topBar = { TopAppBar(title = { Text("Login") }, colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkPanel), navigationIcon = { IconButton({ nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Back") } }) }, bottomBar = { NavBar(nav, Route.Settings.path) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp).verticalScroll(rememberScrollState())) {
            Text("Web App Sync", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text("Sync progress with: sidscri.tplinkdns.com:8009", color = DarkMuted, fontSize = 11.sp)
            Spacer(Modifier.height(12.dp))
            
            if (adminSettings.isLoggedIn) {
                Card(colors = CardDefaults.cardColors(containerColor = AccentGood), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, "Logged In", tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Logged in as: ${adminSettings.username}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
                OutlinedTextField(serverUrl, { serverUrl = it }, Modifier.fillMaxWidth(), label = { Text("Server URL") }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 12.sp), leadingIcon = { Icon(Icons.Default.Cloud, "Server") })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), label = { Text("Username") }, singleLine = true, leadingIcon = { Icon(Icons.Default.Person, "User") })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), leadingIcon = { Icon(Icons.Default.Lock, "Password") })
                Spacer(Modifier.height(12.dp))
                Button({
                    if (username.isBlank() || password.isBlank()) { statusMessage = "Enter username and password"; return@Button }
                    isLoading = true
                    scope.launch {
                        val result = repo.syncLogin(username, password)
                        if (result.success) {
                            val newSettings = adminSettings.copy(
                                webAppUrl = serverUrl.ifBlank { WebAppSync.DEFAULT_SERVER_URL },
                                authToken = result.token,
                                username = result.username,
                                isLoggedIn = true,
                                lastSyncTime = 0
                            )
                            repo.saveAdminSettings(newSettings)
                            statusMessage = "Login successful!"
                            password = ""
                            if (adminSettings.autoPullOnLogin) {
                                statusMessage = "Login successful! Syncing..."
                                val pullResult = repo.syncPullProgressWithToken(result.token, serverUrl.ifBlank { WebAppSync.DEFAULT_SERVER_URL })
                                val breakdownResult = repo.syncBreakdowns()
                                statusMessage = if (pullResult.success && breakdownResult.success) "Login successful! Progress and breakdowns synced." else "Login successful! Some sync errors occurred."
                                repo.saveAdminSettings(newSettings.copy(lastSyncTime = System.currentTimeMillis()))
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
            Spacer(Modifier.height(8.dp))
            SettingToggle("Auto-pull progress on login", adminSettings.autoPullOnLogin) { scope.launch { repo.saveAdminSettings(adminSettings.copy(autoPullOnLogin = it)) } }
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
    var statusMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var showAiPicker by remember { mutableStateOf(false) }
    val availableAi = remember(adminSettings) { repo.getAvailableAiServices(adminSettings) }
    
    Scaffold(topBar = { TopAppBar(title = { Text("Sync Progress") }, colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkPanel), navigationIcon = { IconButton({ nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Back") } }) }, bottomBar = { NavBar(nav, Route.Settings.path) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp).verticalScroll(rememberScrollState())) {
            // Login Status Card
            Card(colors = CardDefaults.cardColors(containerColor = if (adminSettings.isLoggedIn) AccentGood else Color(0xFF3D1212)), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (adminSettings.isLoggedIn) Icons.Default.CheckCircle else Icons.Default.Error, "Status", tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Login Status: ${if (adminSettings.isLoggedIn) "Connected" else "Not Logged In"}", color = Color.White, fontWeight = FontWeight.Bold)
                        if (adminSettings.isLoggedIn) Text("User: ${adminSettings.username}", color = DarkMuted, fontSize = 11.sp)
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
                        Text("Changes pending sync", color = Color.Yellow, fontSize = 12.sp)
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
            Spacer(Modifier.height(12.dp))
            
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
            
            // Choose Breakdown AI button
            if (availableAi.isNotEmpty()) {
                OutlinedButton({ showAiPicker = true }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.SmartToy, "AI"); Spacer(Modifier.width(8.dp))
                    Text("Choose Breakdown AI: ${adminSettings.breakdownAiChoice.label}")
                }
                DropdownMenu(showAiPicker, { showAiPicker = false }) {
                    availableAi.forEach { choice ->
                        DropdownMenuItem(text = { Text(choice.label, color = Color.White) }, onClick = { scope.launch { repo.saveAdminSettings(adminSettings.copy(breakdownAiChoice = choice)) }; showAiPicker = false })
                    }
                }
                Spacer(Modifier.height(8.dp))
            } else {
                Text("No AI services configured. Admin can add API keys.", color = DarkMuted, fontSize = 11.sp)
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
