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
    }
}

@Composable
fun isLandscape(): Boolean = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

@Composable
private fun NavBar(nav: NavHostController, currentRoute: String) {
    val landscape = isLandscape()
    if (landscape) {
        // Compact nav for landscape
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
    var isLoading by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth().padding(8.dp), colors = CardDefaults.cardColors(containerColor = DarkPanel)) {
            Column(Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
                Text("Breakdown: ${card.term}", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Spacer(Modifier.height(4.dp))
                // Auto-fill buttons
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val splitParts = ChatGptHelper.autoSplitTerm(card.term)
                        parts = splitParts.map { BreakdownPart(it, "") }.toMutableList()
                    }, modifier = Modifier.weight(1f)) { Text("Auto-Split", fontSize = 11.sp) }
                    if (adminSettings.chatGptEnabled && adminSettings.chatGptApiKey.isNotBlank()) {
                        Button(onClick = { onAutoFill(true) }, modifier = Modifier.weight(1f), enabled = !isLoading) { Text(if (isLoading) "..." else "AI Fill", fontSize = 11.sp) }
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
    Scaffold(topBar = { TopAppBar(title = { Text(title) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkPanel), actions = { GroupFilterDropdown(groups, settings.studyFilterGroup) { scope.launch { repo.saveSettingsAll(settings.copy(studyFilterGroup = it)) } }; Spacer(Modifier.width(8.dp)) }) }, bottomBar = { NavBar(nav, route) }) { pad ->
        if (landscape) {
            Row(Modifier.fillMaxSize().padding(pad).padding(8.dp)) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (current != null) { FlipCard(current, currentBreakdown, showFront, settings, { showFront = !showFront }, { if (index < filteredCards.size - 1) { index++; showFront = true } }, { if (index > 0) { index--; showFront = true } }) }
                    else { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No cards.", color = DarkMuted) } }
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(0.6f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                    CountsRow(progress, allCards)
                    OutlinedTextField(search, { search = it; index = 0; showFront = true }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search", fontSize = 11.sp) }, colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = DarkPanel2, focusedContainerColor = DarkPanel2), textStyle = LocalTextStyle.current.copy(fontSize = 12.sp))
                    if (current != null) {
                        Spacer(Modifier.height(4.dp)); Text("Card ${index + 1} / ${filteredCards.size}", color = DarkMuted, fontSize = 11.sp)
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            OutlinedButton({ if (index > 0) { index--; showFront = true } }, enabled = index > 0, modifier = Modifier.weight(1f).height(36.dp)) { Text("◀", fontSize = 14.sp) }
                            Spacer(Modifier.width(4.dp))
                            Button({ tts.setRate(settings.speechRate); val text = if (settings.speakPronunciationOnly && !current.pron.isNullOrBlank()) current.pron else current.term + if (!current.pron.isNullOrBlank()) ", ${current.pron}" else ""; tts.speak(text) }, modifier = Modifier.weight(1f).height(36.dp)) { Icon(Icons.Default.VolumeUp, "Speak", Modifier.size(18.dp)) }
                            Spacer(Modifier.width(4.dp))
                            OutlinedButton({ if (index < filteredCards.size - 1) { index++; showFront = true } }, enabled = index < filteredCards.size - 1, modifier = Modifier.weight(1f).height(36.dp)) { Text("▶", fontSize = 14.sp) }
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Button({ scope.launch { repo.setStatus(current.id, CardStatus.LEARNED); if (index >= filteredCards.size - 1) index = (filteredCards.size - 2).coerceAtLeast(0); showFront = true } }, Modifier.weight(1f).height(36.dp), colors = ButtonDefaults.buttonColors(containerColor = AccentGood)) { Text("✓", fontSize = 14.sp) }
                            IconButton({ scope.launch { if (inCustomSet) repo.removeFromCustomSet(current.id) else repo.addToCustomSet(current.id) } }, modifier = Modifier.size(36.dp).background(DarkPanel2, RoundedCornerShape(6.dp))) { Icon(if (inCustomSet) Icons.Default.Star else Icons.Default.StarBorder, "Custom", tint = if (inCustomSet) Color.Yellow else DarkMuted, modifier = Modifier.size(18.dp)) }
                            IconButton({ showBreakdown = true }, modifier = Modifier.size(36.dp).background(DarkPanel2, RoundedCornerShape(6.dp))) { Icon(Icons.Default.Extension, "Breakdown", tint = if (currentBreakdown?.hasContent() == true) AccentBlue else DarkMuted, modifier = Modifier.size(18.dp)) }
                            OutlinedButton({ scope.launch { val newStatus = if (isActive) CardStatus.UNSURE else CardStatus.ACTIVE; repo.setStatus(current.id, newStatus); if (index >= filteredCards.size - 1) index = (filteredCards.size - 2).coerceAtLeast(0); showFront = true } }, Modifier.weight(1f).height(36.dp)) { Text(if (isActive) "?" else "↺", fontSize = 14.sp) }
                        }
                        if (atEnd) { Spacer(Modifier.height(4.dp)); OutlinedButton({ index = 0; showFront = true }, Modifier.fillMaxWidth().height(32.dp)) { Icon(Icons.Default.KeyboardArrowUp, "Top", Modifier.size(16.dp)); Text("Top", fontSize = 11.sp) } }
                    }
                }
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
        if (viewMode == LearnedViewMode.STUDY) { GroupFilterDropdown(groups, settings.studyFilterGroup) { scope.launch { repo.saveSettingsAll(settings.copy(studyFilterGroup = it)) } }; Spacer(Modifier.width(4.dp)) }
    }) }, bottomBar = { NavBar(nav, Route.Learned.path) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 12.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CountsRow(progress, allCards); Text("Learned: ${learnedCards.size}", color = DarkMuted, fontSize = 11.sp)
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
    if (showBreakdown && breakdownCard != null) { BreakdownDialog(breakdownCard!!, breakdowns[breakdownCard!!.id], adminSettings, { scope.launch { repo.saveBreakdown(it) }; showBreakdown = false }, { useAI -> scope.launch { val bd = repo.autoFillBreakdown(breakdownCard!!.id, breakdownCard!!.term, useAI); repo.saveBreakdown(bd) }; showBreakdown = false }, { showBreakdown = false }) }
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
    Scaffold(
        topBar = { TopAppBar(title = { Text("Deleted") }, colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkPanel)) },
        bottomBar = { NavBar(nav, Route.Deleted.path) }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp)) {
            Text("Deleted: ${deletedCards.size}", color = DarkMuted, fontSize = 11.sp)
            OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search") }, colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = DarkPanel2, focusedContainerColor = DarkPanel2))
            Spacer(Modifier.height(8.dp))
            if (deletedCards.isEmpty()) {
                Text("No deleted cards.", color = DarkMuted)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: NavHostController, repo: Repository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by repo.settingsAllFlow().collectAsState(initial = StudySettings())
    val tts = remember { TtsHelper(context) }
    DisposableEffect(Unit) { onDispose { tts.shutdown() } }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> if (uri == null) return@rememberLauncherForActivityResult; scope.launch { val imported = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { CsvImport.parseCsv(it.bufferedReader()) } ?: emptyList() }; if (imported.isNotEmpty()) repo.replaceCustomCards(imported) } }
    var showSortDropdown by remember { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }, colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkPanel)) }, bottomBar = { NavBar(nav, Route.Settings.path) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp).verticalScroll(rememberScrollState())) {
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
            Spacer(Modifier.height(16.dp)); HorizontalDivider(color = DarkBorder)
            Spacer(Modifier.height(12.dp))
            OutlinedButton({ nav.navigate(Route.Deleted.path) }, Modifier.fillMaxWidth()) { Text("View Deleted Cards") }
            Spacer(Modifier.height(6.dp))
            OutlinedButton({ csvLauncher.launch(arrayOf("text/*", "text/csv")) }, Modifier.fillMaxWidth()) { Text("Import CSV") }
            Spacer(Modifier.height(6.dp))
            OutlinedButton({ scope.launch { repo.clearAllProgress() } }, Modifier.fillMaxWidth()) { Text("Reset All Progress") }
            Spacer(Modifier.height(16.dp)); HorizontalDivider(color = DarkBorder); Spacer(Modifier.height(12.dp))
            Button({ nav.navigate(Route.Admin.path) }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)) {
                Icon(Icons.Default.AdminPanelSettings, "Admin"); Spacer(Modifier.width(8.dp)); Text("Admin Settings")
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
    var serverUrl by remember(adminSettings) { mutableStateOf(adminSettings.webAppUrl.ifBlank { WebAppSync.DEFAULT_SERVER_URL }) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var chatGptKey by remember(adminSettings) { mutableStateOf(adminSettings.chatGptApiKey) }
    var statusMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    
    Scaffold(topBar = { TopAppBar(title = { Text("Admin") }, colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkPanel), navigationIcon = { IconButton({ nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Back") } }) }, bottomBar = { NavBar(nav, Route.Admin.path) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp).verticalScroll(rememberScrollState())) {
            // Login Section
            Text("Web App Sync", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text("Sync progress with: sidscri.tplinkdns.com:8009", color = DarkMuted, fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
            
            if (adminSettings.isLoggedIn) {
                Card(colors = CardDefaults.cardColors(containerColor = AccentGood), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("✓ Logged in as: ${adminSettings.username}", color = Color.White, fontWeight = FontWeight.Bold)
                        if (adminSettings.lastSyncTime > 0) {
                            Text("Last sync: ${java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(adminSettings.lastSyncTime))}", color = DarkMuted, fontSize = 11.sp)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ 
                        isLoading = true
                        scope.launch { 
                            val result = repo.syncPushProgress()
                            statusMessage = if (result.success) "Progress pushed!" else "Error: ${result.error}"
                            if (result.success) repo.saveAdminSettings(adminSettings.copy(lastSyncTime = System.currentTimeMillis()))
                            isLoading = false
                        }
                    }, Modifier.weight(1f), enabled = !isLoading) { Text(if (isLoading) "..." else "Push") }
                    Button({ 
                        isLoading = true
                        scope.launch { 
                            val result = repo.syncPullProgress()
                            statusMessage = if (result.success) "Progress pulled!" else "Error: ${result.error}"
                            if (result.success) repo.saveAdminSettings(adminSettings.copy(lastSyncTime = System.currentTimeMillis()))
                            isLoading = false
                        }
                    }, Modifier.weight(1f), enabled = !isLoading) { Text(if (isLoading) "..." else "Pull") }
                }
                Spacer(Modifier.height(4.dp))
                OutlinedButton({ 
                    scope.launch { 
                        repo.saveAdminSettings(adminSettings.copy(isLoggedIn = false, authToken = "", username = ""))
                        statusMessage = "Logged out"
                    }
                }, Modifier.fillMaxWidth()) { Text("Logout") }
            } else {
                OutlinedTextField(serverUrl, { serverUrl = it }, Modifier.fillMaxWidth(), label = { Text("Server URL") }, singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 12.sp))
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), label = { Text("Username") }, singleLine = true)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                Spacer(Modifier.height(8.dp))
                Button({
                    if (username.isBlank() || password.isBlank()) {
                        statusMessage = "Enter username and password"
                        return@Button
                    }
                    isLoading = true
                    scope.launch {
                        val result = repo.syncLogin(username, password)
                        if (result.success) {
                            repo.saveAdminSettings(adminSettings.copy(
                                webAppUrl = serverUrl,
                                authToken = result.token,
                                username = result.username,
                                isLoggedIn = true
                            ))
                            statusMessage = "Logged in!"
                            password = ""
                        } else {
                            statusMessage = "Login failed: ${result.error}"
                        }
                        isLoading = false
                    }
                }, Modifier.fillMaxWidth(), enabled = !isLoading) { Text(if (isLoading) "Logging in..." else "Login") }
            }
            
            if (statusMessage.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(statusMessage, color = if (statusMessage.startsWith("Error") || statusMessage.startsWith("Login failed")) Color.Red else AccentBlue, fontSize = 12.sp)
            }
            
            Spacer(Modifier.height(20.dp)); HorizontalDivider(color = DarkBorder); Spacer(Modifier.height(16.dp))
            
            // ChatGPT Section
            Text("ChatGPT API", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text("Enable AI-powered breakdown autofill", color = DarkMuted, fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
            
            OutlinedTextField(chatGptKey, { chatGptKey = it }, Modifier.fillMaxWidth(), label = { Text("OpenAI API Key") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), textStyle = LocalTextStyle.current.copy(fontSize = 12.sp))
            Spacer(Modifier.height(6.dp))
            SettingToggle("Enable AI Breakdown", adminSettings.chatGptEnabled) { 
                scope.launch { repo.saveAdminSettings(adminSettings.copy(chatGptEnabled = it)) } 
            }
            Spacer(Modifier.height(6.dp))
            Button({
                scope.launch { 
                    repo.saveAdminSettings(adminSettings.copy(chatGptApiKey = chatGptKey))
                    statusMessage = "API key saved"
                }
            }, Modifier.fillMaxWidth()) { Text("Save API Key") }
            
            Spacer(Modifier.height(20.dp)); HorizontalDivider(color = DarkBorder); Spacer(Modifier.height(16.dp))
            
            // Sync Breakdowns
            Text("Sync Breakdowns", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text("Download shared breakdowns from server", color = DarkMuted, fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
            Button({
                isLoading = true
                scope.launch {
                    val result = repo.syncBreakdowns()
                    statusMessage = if (result.success) "Breakdowns synced!" else "Error: ${result.error}"
                    isLoading = false
                }
            }, Modifier.fillMaxWidth(), enabled = !isLoading) { Text(if (isLoading) "Syncing..." else "Sync Breakdowns") }
            
            Spacer(Modifier.height(24.dp))
            Text("Server Setup Guide", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DarkMuted)
            Text("""
Your server needs these API endpoints:
• POST /api/login - authenticate users
• GET/POST /api/sync/pull|push - sync progress  
• GET/POST /api/breakdowns - shared breakdowns

Data stored in:
c:/personal-servers/kenpoflashcardswebserver/data/
• profiles.json - user accounts
• breakdown.json - shared breakdowns
• users/{id}/progress.json - per-user progress
            """.trimIndent(), color = DarkMuted, fontSize = 10.sp)
        }
    }
}
