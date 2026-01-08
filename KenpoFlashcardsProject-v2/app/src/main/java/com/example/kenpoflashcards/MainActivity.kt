package com.example.kenpoflashcards

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
    }
}

@Composable
private fun NavBar(nav: NavHostController, currentRoute: String) {
    NavigationBar(containerColor = DarkPanel) {
        NavigationBarItem(icon = { Icon(Icons.Default.School, "To Study") }, label = { Text("To Study", fontSize = 9.sp) },
            selected = currentRoute == Route.Active.path, onClick = { nav.navigate(Route.Active.path) { popUpTo(Route.Active.path) { inclusive = true } } })
        NavigationBarItem(icon = { Icon(Icons.Default.Help, "Unsure") }, label = { Text("Unsure", fontSize = 9.sp) },
            selected = currentRoute == Route.Unsure.path, onClick = { nav.navigate(Route.Unsure.path) })
        NavigationBarItem(icon = { Icon(Icons.Default.CheckCircle, "Learned") }, label = { Text("Learned", fontSize = 9.sp) },
            selected = currentRoute == Route.Learned.path, onClick = { nav.navigate(Route.Learned.path) })
        NavigationBarItem(icon = { Icon(Icons.Default.List, "All") }, label = { Text("All", fontSize = 9.sp) },
            selected = currentRoute == Route.AllCards.path, onClick = { nav.navigate(Route.AllCards.path) })
        NavigationBarItem(icon = { Icon(Icons.Default.Star, "Custom") }, label = { Text("Custom", fontSize = 9.sp) },
            selected = currentRoute == Route.CustomSet.path, onClick = { nav.navigate(Route.CustomSet.path) })
        NavigationBarItem(icon = { Icon(Icons.Default.Settings, "More") }, label = { Text("More", fontSize = 9.sp) },
            selected = currentRoute == Route.Settings.path || currentRoute == Route.Deleted.path, onClick = { nav.navigate(Route.Settings.path) })
    }
}

@Composable
private fun CountsRow(progress: ProgressState, allCards: List<FlashCard>) {
    val counts = remember(progress, allCards) {
        var a = 0; var u = 0; var l = 0
        allCards.forEach { card -> when (progress.getStatus(card.id)) { CardStatus.ACTIVE -> a++; CardStatus.UNSURE -> u++; CardStatus.LEARNED -> l++; CardStatus.DELETED -> {} } }
        Triple(a, u, l)
    }
    Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        Text("Unlearned: ${counts.first}", color = DarkMuted, fontSize = 12.sp)
        Text("Unsure: ${counts.second}", color = DarkMuted, fontSize = 12.sp)
        Text("Learned: ${counts.third}", color = DarkMuted, fontSize = 12.sp)
    }
}

@Composable
fun FlipCard(card: FlashCard, breakdown: TermBreakdown?, showFront: Boolean, settings: StudySettings, onFlip: () -> Unit, onSwipeNext: () -> Unit, onSwipePrev: () -> Unit) {
    val rotation by animateFloatAsState(if (showFront) 0f else 180f, tween(260), label = "flip")
    val showFrontSide = rotation.absoluteValue <= 90f
    var dragTotal by remember { mutableStateOf(0f) }
    Card(
        modifier = Modifier.fillMaxWidth().height(280.dp)
            .pointerInput(Unit) { detectHorizontalDragGestures(onDragStart = { dragTotal = 0f }, onHorizontalDrag = { _, d -> dragTotal += d },
                onDragEnd = { if (dragTotal <= -90f) onSwipeNext(); if (dragTotal >= 90f) onSwipePrev(); dragTotal = 0f }) }
            .graphicsLayer { rotationY = rotation; cameraDistance = 12f * density }.clickable { onFlip() },
        colors = CardDefaults.cardColors(containerColor = DarkPanel), elevation = CardDefaults.cardElevation(6.dp)
    ) {
        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            val frontIsMeaning = settings.reverseCards
            val frontText = if (frontIsMeaning) card.meaning else card.term
            val backText = if (frontIsMeaning) card.term else card.meaning
            val isDefSide = if (settings.reverseCards) showFrontSide else !showFrontSide
            if (showFrontSide) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
                    Text(frontText, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, color = Color.White)
                    if (!settings.reverseCards && !card.pron.isNullOrBlank()) { Spacer(Modifier.height(6.dp)); Text("(${card.pron})", color = DarkMuted) }
                    Spacer(Modifier.height(8.dp))
                    if (settings.showGroup) AssistChip(onClick = {}, label = { Text(card.group, fontSize = 11.sp) })
                    if (settings.showSubgroup && !card.subgroup.isNullOrBlank()) Text(card.subgroup, fontSize = 12.sp, color = DarkMuted)
                    if (isDefSide && settings.showBreakdownOnDefinition && breakdown?.hasContent() == true) { Spacer(Modifier.height(8.dp)); BreakdownInline(breakdown) }
                    Spacer(Modifier.weight(1f)); Text("Tap to flip", color = DarkMuted, fontSize = 11.sp)
                }
            } else {
                Box(Modifier.graphicsLayer { rotationY = 180f }.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
                        Text(backText, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, color = Color.White)
                        if (settings.reverseCards && !card.pron.isNullOrBlank()) { Spacer(Modifier.height(6.dp)); Text("(${card.pron})", color = DarkMuted) }
                        if (settings.reverseCards) { Spacer(Modifier.height(8.dp)); if (settings.showGroup) AssistChip(onClick = {}, label = { Text(card.group, fontSize = 11.sp) })
                            if (settings.showSubgroup && !card.subgroup.isNullOrBlank()) Text(card.subgroup, fontSize = 12.sp, color = DarkMuted) }
                        if (isDefSide && settings.showBreakdownOnDefinition && breakdown?.hasContent() == true) { Spacer(Modifier.height(8.dp)); BreakdownInline(breakdown) }
                        Spacer(Modifier.weight(1f)); Text("Tap to flip", color = DarkMuted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun BreakdownInline(breakdown: TermBreakdown) {
    Column(Modifier.fillMaxWidth().background(DarkPanel2, RoundedCornerShape(8.dp)).padding(10.dp)) {
        if (breakdown.parts.isNotEmpty()) { Text("Breakdown", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = DarkMuted); Text(breakdown.formatParts(), fontSize = 12.sp, color = Color.White) }
        if (breakdown.literal.isNotBlank()) { if (breakdown.parts.isNotEmpty()) Spacer(Modifier.height(4.dp)); Text("Literal", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = DarkMuted); Text(breakdown.literal, fontSize = 12.sp, color = Color.White) }
    }
}

@Composable
fun BreakdownDialog(card: FlashCard, breakdown: TermBreakdown?, onSave: (TermBreakdown) -> Unit, onDismiss: () -> Unit) {
    var parts by remember(breakdown) { mutableStateOf(breakdown?.parts?.toMutableList() ?: mutableListOf()) }
    var literal by remember(breakdown) { mutableStateOf(breakdown?.literal ?: "") }
    var notes by remember(breakdown) { mutableStateOf(breakdown?.notes ?: "") }
    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = DarkPanel)) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                Text("Breakdown: ${card.term}", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Spacer(Modifier.height(6.dp)); Text("Break compound terms into parts.", color = DarkMuted, fontSize = 12.sp)
                Spacer(Modifier.height(14.dp)); Text("Parts", fontWeight = FontWeight.Bold, color = Color.White); Spacer(Modifier.height(6.dp))
                parts.forEachIndexed { idx, part ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(part.part, { parts = parts.toMutableList().apply { this[idx] = part.copy(part = it) } }, Modifier.weight(1f), label = { Text("Part") }, singleLine = true)
                        OutlinedTextField(part.meaning, { parts = parts.toMutableList().apply { this[idx] = part.copy(meaning = it) } }, Modifier.weight(1f), label = { Text("Meaning") }, singleLine = true)
                        IconButton({ parts = parts.toMutableList().apply { removeAt(idx) } }) { Icon(Icons.Default.Delete, "Remove", tint = DarkMuted) }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                TextButton({ parts = (parts + BreakdownPart("", "")).toMutableList() }) { Icon(Icons.Default.Add, "Add"); Spacer(Modifier.width(4.dp)); Text("Add part") }
                Spacer(Modifier.height(12.dp)); OutlinedTextField(literal, { literal = it }, Modifier.fillMaxWidth(), label = { Text("Literal meaning") })
                Spacer(Modifier.height(10.dp)); OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("Notes") }, minLines = 2)
                Spacer(Modifier.height(14.dp))
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
    var search by remember { mutableStateOf("") }
    var showFront by remember { mutableStateOf(true) }
    var index by remember { mutableStateOf(0) }
    var showBreakdown by remember { mutableStateOf(false) }
    val isActive = statusFilter == CardStatus.ACTIVE
    val title = if (isActive) "To Study" else "Unsure"
    val route = if (isActive) Route.Active.path else Route.Unsure.path
    val shouldRandomize = if (isActive) settings.randomizeUnlearned else settings.randomizeUnsure
    val filteredCards = remember(allCards, progress, search, shouldRandomize, settings.sortMode) {
        val base = allCards.filter { progress.getStatus(it.id) == statusFilter }
        val searched = if (search.isBlank()) base else base.filter { it.term.contains(search, true) || it.meaning.contains(search, true) || (it.pron?.contains(search, true) ?: false) || it.group.contains(search, true) }
        sortCards(searched, settings.sortMode, shouldRandomize)
    }
    LaunchedEffect(filteredCards.size) { if (filteredCards.isEmpty()) index = 0 else index = index.coerceIn(0, filteredCards.size - 1); showFront = true }
    val current = filteredCards.getOrNull(index)
    val currentBreakdown = current?.let { breakdowns[it.id] }
    val inCustomSet = current?.let { customSet.contains(it.id) } ?: false
    val atEnd = index >= filteredCards.size - 1 && filteredCards.isNotEmpty()
    Scaffold(topBar = { TopAppBar(title = { Text(title) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkPanel)) }, bottomBar = { NavBar(nav, route) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CountsRow(progress, allCards)
            OutlinedTextField(search, { search = it; index = 0; showFront = true }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search") }, colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = DarkPanel2, focusedContainerColor = DarkPanel2))
            Spacer(Modifier.height(8.dp))
            if (current == null) { Text("No ${title.lowercase()} cards.", color = DarkMuted) }
            else {
                Text("Card ${index + 1} / ${filteredCards.size}", color = DarkMuted, fontSize = 12.sp); Spacer(Modifier.height(4.dp))
                FlipCard(current, currentBreakdown, showFront, settings, { showFront = !showFront }, { if (index < filteredCards.size - 1) { index++; showFront = true } }, { if (index > 0) { index--; showFront = true } })
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    OutlinedButton({ if (index > 0) { index--; showFront = true } }, enabled = index > 0, modifier = Modifier.weight(1f)) { Text("Prev") }
                    Spacer(Modifier.width(8.dp))
                    Button({ tts.setRate(settings.speechRate); settings.speechVoice?.let { tts.setVoice(it) }; tts.speak(current.term + if (!current.pron.isNullOrBlank()) ", ${current.pron}" else "") }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.VolumeUp, "Speak") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton({ if (index < filteredCards.size - 1) { index++; showFront = true } }, enabled = index < filteredCards.size - 1, modifier = Modifier.weight(1f)) { Text("Next") }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button({ scope.launch { repo.setStatus(current.id, CardStatus.LEARNED); if (index >= filteredCards.size - 1) index = (filteredCards.size - 2).coerceAtLeast(0); showFront = true } }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = AccentGood)) { Text("Got it ✓") }
                    IconButton({ scope.launch { if (inCustomSet) repo.removeFromCustomSet(current.id) else repo.addToCustomSet(current.id) } }, modifier = Modifier.size(48.dp).background(DarkPanel2, RoundedCornerShape(8.dp))) { Icon(if (inCustomSet) Icons.Default.Star else Icons.Default.StarBorder, "Custom", tint = if (inCustomSet) Color.Yellow else DarkMuted) }
                    IconButton({ showBreakdown = true }, modifier = Modifier.size(48.dp).background(DarkPanel2, RoundedCornerShape(8.dp))) { Icon(Icons.Default.Extension, "Breakdown", tint = if (currentBreakdown?.hasContent() == true) AccentBlue else DarkMuted) }
                    OutlinedButton({ scope.launch { val newStatus = if (isActive) CardStatus.UNSURE else CardStatus.ACTIVE; repo.setStatus(current.id, newStatus); if (index >= filteredCards.size - 1) index = (filteredCards.size - 2).coerceAtLeast(0); showFront = true } }, Modifier.weight(1f)) { Text(if (isActive) "Unsure" else "Relearn") }
                }
                if (atEnd) { Spacer(Modifier.height(8.dp)); OutlinedButton({ index = 0; showFront = true }, Modifier.fillMaxWidth()) { Icon(Icons.Default.KeyboardArrowUp, "Top"); Spacer(Modifier.width(4.dp)); Text("Return to Top") } }
            }
        }
    }
    if (showBreakdown && current != null) { BreakdownDialog(current, currentBreakdown, { scope.launch { repo.saveBreakdown(it) }; showBreakdown = false }, { showBreakdown = false }) }
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
    var viewMode by remember { mutableStateOf(LearnedViewMode.LIST) }
    var search by remember { mutableStateOf("") }
    var showFront by remember { mutableStateOf(true) }
    var index by remember { mutableStateOf(0) }
    var showBreakdown by remember { mutableStateOf(false) }
    var breakdownCard by remember { mutableStateOf<FlashCard?>(null) }
    val learnedCards = remember(allCards, progress, search, viewMode, settings) {
        val base = allCards.filter { progress.getStatus(it.id) == CardStatus.LEARNED }
        val searched = if (search.isBlank()) base else base.filter { it.term.contains(search, true) || it.meaning.contains(search, true) }
        if (viewMode == LearnedViewMode.STUDY && settings.randomizeLearnedStudy) searched.shuffled() else sortCards(searched, settings.sortMode, false)
    }
    LaunchedEffect(learnedCards.size) { if (learnedCards.isEmpty()) index = 0 else index = index.coerceIn(0, learnedCards.size - 1); showFront = true }
    val current = learnedCards.getOrNull(index)
    val currentBreakdown = current?.let { breakdowns[it.id] }
    val inCustomSet = current?.let { customSet.contains(it.id) } ?: false
    val atEnd = index >= learnedCards.size - 1 && learnedCards.isNotEmpty()
    Scaffold(topBar = { TopAppBar(title = { Text("Learned") }, colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkPanel), actions = {
        FilterChip(viewMode == LearnedViewMode.LIST, { viewMode = LearnedViewMode.LIST }, { Text("List", fontSize = 11.sp) }); Spacer(Modifier.width(4.dp))
        FilterChip(viewMode == LearnedViewMode.STUDY, { viewMode = LearnedViewMode.STUDY }, { Text("Study", fontSize = 11.sp) }); Spacer(Modifier.width(8.dp)) }) },
        bottomBar = { NavBar(nav, Route.Learned.path) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CountsRow(progress, allCards); Text("Learned: ${learnedCards.size}", color = DarkMuted, fontSize = 12.sp); Spacer(Modifier.height(4.dp))
            OutlinedTextField(search, { search = it; index = 0 }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search") }, colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = DarkPanel2, focusedContainerColor = DarkPanel2))
            Spacer(Modifier.height(8.dp))
            if (viewMode == LearnedViewMode.LIST) {
                if (learnedCards.isEmpty()) Text("Nothing learned yet.", color = DarkMuted)
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(learnedCards, key = { it.id }) { c ->
                        val bd = breakdowns[c.id]
                        Card(colors = CardDefaults.cardColors(containerColor = DarkPanel), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(c.term, fontWeight = FontWeight.Bold, color = Color.White)
                                    IconButton({ breakdownCard = c; showBreakdown = true }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Extension, "Breakdown", tint = if (bd?.hasContent() == true) AccentBlue else DarkMuted, modifier = Modifier.size(20.dp)) }
                                }
                                if (!c.pron.isNullOrBlank()) Text("(${c.pron})", fontSize = 11.sp, color = DarkMuted)
                                if (settings.showDefinitionsInLearnedList) Text(c.meaning, fontSize = 13.sp, color = Color.White)
                                if (settings.showLearnedListGroupLabel) Text("${c.group}${c.subgroup?.let { " • $it" } ?: ""}", fontSize = 11.sp, color = DarkMuted)
                                if (settings.showBreakdownOnDefinition && bd?.hasContent() == true) { Spacer(Modifier.height(6.dp)); BreakdownInline(bd) }
                                if (settings.showRelearnUnsureButtonsInLearnedList) { Spacer(Modifier.height(6.dp)); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton({ scope.launch { repo.setStatus(c.id, CardStatus.ACTIVE) } }) { Text("Relearn", fontSize = 11.sp) }
                                    OutlinedButton({ scope.launch { repo.setStatus(c.id, CardStatus.UNSURE) } }) { Text("Unsure", fontSize = 11.sp) } } }
                            }
                        }
                    }
                }
            } else {
                if (current == null) Text("No learned cards.", color = DarkMuted)
                else {
                    Text("Card ${index + 1} / ${learnedCards.size}", color = DarkMuted, fontSize = 12.sp); Spacer(Modifier.height(4.dp))
                    FlipCard(current, currentBreakdown, showFront, settings, { showFront = !showFront }, { if (index < learnedCards.size - 1) { index++; showFront = true } }, { if (index > 0) { index--; showFront = true } })
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        OutlinedButton({ if (index > 0) { index--; showFront = true } }, enabled = index > 0, modifier = Modifier.weight(1f)) { Text("Prev") }; Spacer(Modifier.width(8.dp))
                        Button({ tts.speak(current.term) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.VolumeUp, "Speak") }; Spacer(Modifier.width(8.dp))
                        OutlinedButton({ if (index < learnedCards.size - 1) { index++; showFront = true } }, enabled = index < learnedCards.size - 1, modifier = Modifier.weight(1f)) { Text("Next") }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton({ scope.launch { repo.setStatus(current.id, CardStatus.ACTIVE) } }, Modifier.weight(1f)) { Text("Relearn") }
                        IconButton({ scope.launch { if (inCustomSet) repo.removeFromCustomSet(current.id) else repo.addToCustomSet(current.id) } }, modifier = Modifier.size(48.dp).background(DarkPanel2, RoundedCornerShape(8.dp))) { Icon(if (inCustomSet) Icons.Default.Star else Icons.Default.StarBorder, "Custom", tint = if (inCustomSet) Color.Yellow else DarkMuted) }
                        IconButton({ showBreakdown = true; breakdownCard = current }, modifier = Modifier.size(48.dp).background(DarkPanel2, RoundedCornerShape(8.dp))) { Icon(Icons.Default.Extension, "Breakdown", tint = if (currentBreakdown?.hasContent() == true) AccentBlue else DarkMuted) }
                        OutlinedButton({ scope.launch { repo.setStatus(current.id, CardStatus.UNSURE) } }, Modifier.weight(1f)) { Text("Unsure") }
                    }
                    if (atEnd) { Spacer(Modifier.height(8.dp)); OutlinedButton({ index = 0; showFront = true }, Modifier.fillMaxWidth()) { Icon(Icons.Default.KeyboardArrowUp, "Top"); Spacer(Modifier.width(4.dp)); Text("Return to Top") } }
                }
            }
        }
    }
    if (showBreakdown && breakdownCard != null) { BreakdownDialog(breakdownCard!!, breakdowns[breakdownCard!!.id], { scope.launch { repo.saveBreakdown(it) }; showBreakdown = false }, { showBreakdown = false }) }
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
    var search by remember { mutableStateOf("") }
    var showBreakdown by remember { mutableStateOf(false) }
    var breakdownCard by remember { mutableStateOf<FlashCard?>(null) }
    var showGroupFilter by remember { mutableStateOf(false) }
    val groups = remember(allCards) { listOf("All Groups") + allCards.map { it.group }.distinct().sorted() }
    val displayedCards = remember(allCards, progress, search, settings.filterGroup, settings.sortMode) {
        val visible = allCards.filter { progress.getStatus(it.id) != CardStatus.DELETED }
        val grouped = if (settings.filterGroup != null) visible.filter { it.group == settings.filterGroup } else visible
        val searched = if (search.isBlank()) grouped else grouped.filter { it.term.contains(search, true) || it.meaning.contains(search, true) }
        sortCards(searched, settings.sortMode, false)
    }
    Scaffold(topBar = { TopAppBar(title = { Text("All Cards") }, colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkPanel)) }, bottomBar = { NavBar(nav, Route.AllCards.path) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp, vertical = 8.dp)) {
            CountsRow(progress, allCards)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Total: ${displayedCards.size}", color = DarkMuted, fontSize = 12.sp); Spacer(Modifier.weight(1f))
                OutlinedButton({ showGroupFilter = true }) { Text(settings.filterGroup ?: "All Groups", fontSize = 11.sp); Icon(Icons.Default.ArrowDropDown, "Filter") }
                DropdownMenu(showGroupFilter, { showGroupFilter = false }) { groups.forEach { g -> DropdownMenuItem(text = { Text(g, color = Color.White) }, onClick = { scope.launch { repo.saveSettingsAll(settings.copy(filterGroup = if (g == "All Groups") null else g)) }; showGroupFilter = false }) } }
            }
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search") }, colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = DarkPanel2, focusedContainerColor = DarkPanel2))
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(displayedCards, key = { it.id }) { c ->
                    val status = progress.getStatus(c.id); val bd = breakdowns[c.id]; val inCustomSet = customSet.contains(c.id)
                    Card(colors = CardDefaults.cardColors(containerColor = DarkPanel), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(c.term, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
                                IconButton({ scope.launch { if (inCustomSet) repo.removeFromCustomSet(c.id) else repo.addToCustomSet(c.id) } }, modifier = Modifier.size(32.dp)) { Icon(if (inCustomSet) Icons.Default.Star else Icons.Default.StarBorder, "Custom Set", tint = if (inCustomSet) Color.Yellow else DarkMuted, modifier = Modifier.size(20.dp)) }
                                IconButton({ breakdownCard = c; showBreakdown = true }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Extension, "Breakdown", tint = if (bd?.hasContent() == true) AccentBlue else DarkMuted, modifier = Modifier.size(20.dp)) }
                                AssistChip(onClick = {}, label = { Text(status.name, fontSize = 10.sp) })
                            }
                            if (settings.showDefinitionsInAllList) Text(c.meaning, fontSize = 13.sp, color = Color.White)
                            Text("${c.group}${c.subgroup?.let { " • $it" } ?: ""}", fontSize = 11.sp, color = DarkMuted)
                            if (settings.showBreakdownOnDefinition && bd?.hasContent() == true) { Spacer(Modifier.height(6.dp)); BreakdownInline(bd) }
                            if (settings.showUnlearnedUnsureButtonsInAllList) { Spacer(Modifier.height(6.dp)); Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (status != CardStatus.ACTIVE) OutlinedButton({ scope.launch { repo.setStatus(c.id, CardStatus.ACTIVE) } }) { Text("Active", fontSize = 10.sp) }
                                if (status != CardStatus.UNSURE) OutlinedButton({ scope.launch { repo.setStatus(c.id, CardStatus.UNSURE) } }) { Text("Unsure", fontSize = 10.sp) }
                                if (status != CardStatus.LEARNED) Button({ scope.launch { repo.setStatus(c.id, CardStatus.LEARNED) } }, colors = ButtonDefaults.buttonColors(containerColor = AccentGood)) { Text("Learned", fontSize = 10.sp) } } }
                        }
                    }
                }
            }
        }
    }
    if (showBreakdown && breakdownCard != null) { BreakdownDialog(breakdownCard!!, breakdowns[breakdownCard!!.id], { scope.launch { repo.saveBreakdown(it) }; showBreakdown = false }, { showBreakdown = false }) }
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
    var search by remember { mutableStateOf("") }
    var showFront by remember { mutableStateOf(true) }
    var index by remember { mutableStateOf(0) }
    var showBreakdown by remember { mutableStateOf(false) }
    val customCards = remember(allCards, customSet, search, settings) {
        val inSet = allCards.filter { customSet.contains(it.id) }
        val searched = if (search.isBlank()) inSet else inSet.filter { it.term.contains(search, true) || it.meaning.contains(search, true) }
        sortCards(searched, settings.sortMode, settings.randomizeUnlearned)
    }
    LaunchedEffect(customCards.size) { if (customCards.isEmpty()) index = 0 else index = index.coerceIn(0, customCards.size - 1); showFront = true }
    val current = customCards.getOrNull(index)
    val currentBreakdown = current?.let { breakdowns[it.id] }
    val atEnd = index >= customCards.size - 1 && customCards.isNotEmpty()
    Scaffold(topBar = { TopAppBar(title = { Text("Custom Set") }, colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkPanel), actions = {
        if (customSet.isNotEmpty()) { IconButton({ scope.launch { repo.clearCustomSet() } }) { Icon(Icons.Default.DeleteSweep, "Clear", tint = DarkMuted) } } }) },
        bottomBar = { NavBar(nav, Route.CustomSet.path) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CountsRow(progress, allCards); Text("Custom Set: ${customCards.size} cards", color = DarkMuted, fontSize = 12.sp); Spacer(Modifier.height(4.dp))
            OutlinedTextField(search, { search = it; index = 0; showFront = true }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search") }, colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = DarkPanel2, focusedContainerColor = DarkPanel2))
            Spacer(Modifier.height(8.dp))
            if (current == null) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("No cards in custom set.", color = DarkMuted); Spacer(Modifier.height(16.dp)); Text("Add cards from the All tab by tapping the ☆ icon.", color = DarkMuted, fontSize = 12.sp, textAlign = TextAlign.Center) } }
            else {
                Text("Card ${index + 1} / ${customCards.size}", color = DarkMuted, fontSize = 12.sp); Spacer(Modifier.height(4.dp))
                FlipCard(current, currentBreakdown, showFront, settings, { showFront = !showFront }, { if (index < customCards.size - 1) { index++; showFront = true } }, { if (index > 0) { index--; showFront = true } })
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    OutlinedButton({ if (index > 0) { index--; showFront = true } }, enabled = index > 0, modifier = Modifier.weight(1f)) { Text("Prev") }; Spacer(Modifier.width(8.dp))
                    Button({ tts.setRate(settings.speechRate); settings.speechVoice?.let { tts.setVoice(it) }; tts.speak(current.term + if (!current.pron.isNullOrBlank()) ", ${current.pron}" else "") }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.VolumeUp, "Speak") }; Spacer(Modifier.width(8.dp))
                    OutlinedButton({ if (index < customCards.size - 1) { index++; showFront = true } }, enabled = index < customCards.size - 1, modifier = Modifier.weight(1f)) { Text("Next") }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button({ scope.launch { repo.setStatus(current.id, CardStatus.LEARNED); showFront = true } }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = AccentGood)) { Text("Got it ✓") }
                    IconButton({ showBreakdown = true }, modifier = Modifier.size(48.dp).background(DarkPanel2, RoundedCornerShape(8.dp))) { Icon(Icons.Default.Extension, "Breakdown", tint = if (currentBreakdown?.hasContent() == true) AccentBlue else DarkMuted) }
                    OutlinedButton({ scope.launch { repo.removeFromCustomSet(current.id); if (index >= customCards.size - 1) index = (customCards.size - 2).coerceAtLeast(0) } }, Modifier.weight(1f)) { Icon(Icons.Default.RemoveCircleOutline, "Remove", modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Remove") }
                }
                if (atEnd) { Spacer(Modifier.height(8.dp)); OutlinedButton({ index = 0; showFront = true }, Modifier.fillMaxWidth()) { Icon(Icons.Default.KeyboardArrowUp, "Top"); Spacer(Modifier.width(4.dp)); Text("Return to Top") } }
            }
        }
    }
    if (showBreakdown && current != null) { BreakdownDialog(current, currentBreakdown, { scope.launch { repo.saveBreakdown(it) }; showBreakdown = false }, { showBreakdown = false }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeletedScreen(nav: NavHostController, repo: Repository) {
    val scope = rememberCoroutineScope()
    val allCards by repo.allCardsFlow().collectAsState(initial = emptyList())
    val progress by repo.progressFlow().collectAsState(initial = ProgressState.EMPTY)
    var search by remember { mutableStateOf("") }
    val deletedCards = remember(allCards, progress, search) { val deleted = allCards.filter { progress.getStatus(it.id) == CardStatus.DELETED }; if (search.isBlank()) deleted else deleted.filter { it.term.contains(search, true) } }
    Scaffold(topBar = { TopAppBar(title = { Text("Deleted") }, colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkPanel)) }, bottomBar = { NavBar(nav, Route.Deleted.path) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp)) {
            Text("Deleted: ${deletedCards.size}", color = DarkMuted, fontSize = 12.sp); Spacer(Modifier.height(6.dp))
            OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search") }, colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = DarkPanel2, focusedContainerColor = DarkPanel2))
            Spacer(Modifier.height(10.dp))
            if (deletedCards.isEmpty()) Text("No deleted cards.", color = DarkMuted)
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(deletedCards, key = { it.id }) { c -> Card(colors = CardDefaults.cardColors(containerColor = DarkPanel), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text(c.term, fontWeight = FontWeight.Bold, color = Color.White); Text(c.meaning, fontSize = 13.sp, color = Color.White); Spacer(Modifier.height(6.dp)); Button({ scope.launch { repo.setStatus(c.id, CardStatus.ACTIVE) } }) { Text("Restore") } } } } }
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
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("Display", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White); Spacer(Modifier.height(8.dp))
            SettingToggle("Show group label", settings.showGroup) { scope.launch { repo.saveSettingsAll(settings.copy(showGroup = it)) } }
            SettingToggle("Show subgroup label", settings.showSubgroup) { scope.launch { repo.saveSettingsAll(settings.copy(showSubgroup = it)) } }
            SettingToggle("Definition first (reverse)", settings.reverseCards) { scope.launch { repo.saveSettingsAll(settings.copy(reverseCards = it)) } }
            SettingToggle("Show breakdown on definition", settings.showBreakdownOnDefinition) { scope.launch { repo.saveSettingsAll(settings.copy(showBreakdownOnDefinition = it)) } }
            Spacer(Modifier.height(16.dp)); Text("Sorting (when not randomized)", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White); Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().clickable { showSortDropdown = true }.padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Sort order", fontSize = 14.sp, color = Color.White); Row { Text(settings.sortMode.label, fontSize = 14.sp, color = DarkMuted); Icon(Icons.Default.ArrowDropDown, "Select", tint = DarkMuted) }
                DropdownMenu(showSortDropdown, { showSortDropdown = false }) { SortMode.entries.forEach { mode -> DropdownMenuItem(text = { Text(mode.label, color = Color.White) }, onClick = { scope.launch { repo.saveSettingsAll(settings.copy(sortMode = mode)) }; showSortDropdown = false }) } }
            }
            Spacer(Modifier.height(16.dp)); Text("Randomization", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White); Spacer(Modifier.height(8.dp))
            SettingToggle("Randomize Unlearned", settings.randomizeUnlearned) { scope.launch { repo.saveSettingsAll(settings.copy(randomizeUnlearned = it)) } }
            SettingToggle("Randomize Unsure", settings.randomizeUnsure) { scope.launch { repo.saveSettingsAll(settings.copy(randomizeUnsure = it)) } }
            SettingToggle("Randomize Learned Study", settings.randomizeLearnedStudy) { scope.launch { repo.saveSettingsAll(settings.copy(randomizeLearnedStudy = it)) } }
            Spacer(Modifier.height(16.dp)); Text("List Views", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White); Spacer(Modifier.height(8.dp))
            SettingToggle("Show definitions in All list", settings.showDefinitionsInAllList) { scope.launch { repo.saveSettingsAll(settings.copy(showDefinitionsInAllList = it)) } }
            SettingToggle("Show definitions in Learned list", settings.showDefinitionsInLearnedList) { scope.launch { repo.saveSettingsAll(settings.copy(showDefinitionsInLearnedList = it)) } }
            SettingToggle("Show action buttons in All list", settings.showUnlearnedUnsureButtonsInAllList) { scope.launch { repo.saveSettingsAll(settings.copy(showUnlearnedUnsureButtonsInAllList = it)) } }
            SettingToggle("Show action buttons in Learned list", settings.showRelearnUnsureButtonsInLearnedList) { scope.launch { repo.saveSettingsAll(settings.copy(showRelearnUnsureButtonsInLearnedList = it)) } }
            Spacer(Modifier.height(16.dp)); Text("Voice", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White); Spacer(Modifier.height(8.dp))
            Text("Speech rate: ${String.format("%.1f", settings.speechRate)}x", fontSize = 13.sp, color = DarkMuted)
            Slider(settings.speechRate, { scope.launch { repo.saveSettingsAll(settings.copy(speechRate = it)) } }, valueRange = 0.5f..2.0f, steps = 5, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(6.dp))
            Button({ tts.setRate(settings.speechRate); tts.speakTest() }) { Text("Test Voice") }
            Spacer(Modifier.height(20.dp)); HorizontalDivider(color = DarkBorder); Spacer(Modifier.height(16.dp))
            OutlinedButton({ nav.navigate(Route.Deleted.path) }, Modifier.fillMaxWidth()) { Text("View Deleted Cards") }; Spacer(Modifier.height(8.dp))
            OutlinedButton({ csvLauncher.launch(arrayOf("text/*", "text/csv")) }, Modifier.fillMaxWidth()) { Text("Import CSV") }; Spacer(Modifier.height(8.dp))
            OutlinedButton({ scope.launch { repo.clearAllProgress() } }, Modifier.fillMaxWidth()) { Text("Reset All Progress") }
            Spacer(Modifier.height(24.dp)); HorizontalDivider(color = DarkBorder); Spacer(Modifier.height(16.dp))
            Text("Admin", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White); Spacer(Modifier.height(8.dp))
            Text("Admin features coming in future versions.", color = DarkMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SettingToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = Color.White); Switch(checked, onCheckedChange)
    }
}
