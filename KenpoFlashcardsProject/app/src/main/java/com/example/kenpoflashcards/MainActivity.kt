package com.example.kenpoflashcards

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { AppRoot() } }
    }
}

private sealed class Route(val path: String) {
    data object Active : Route("active")
    data object Learned : Route("learned")
    data object Deleted : Route("deleted")
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val store = remember { Store(context) }
    val repo = remember { Repository(context, store) }
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = Route.Active.path) {
        composable(Route.Active.path) { ActiveScreen(nav, repo) }
        composable(Route.Learned.path) { LearnedScreen(nav, repo) }
        composable(Route.Deleted.path) { DeletedScreen(nav, repo) }
    }
}

data class GroupFilters(
    val group: String = "All",
    val subgroup: String = "All",
    val search: String = ""
)

private fun subgroupsFor(cards: List<FlashCard>, group: String): List<String> =
    cards.filter { it.group == group }.mapNotNull { it.subgroup }.distinct().sorted()

private fun applyFilters(cards: List<FlashCard>, filters: GroupFilters): List<FlashCard> {
    val s = filters.search.trim()
    return cards
        .filter { filters.group == "All" || it.group == filters.group }
        .filter { filters.subgroup == "All" || it.subgroup == filters.subgroup }
        .filter {
            if (s.isBlank()) true
            else it.term.contains(s, true) ||
                it.meaning.contains(s, true) ||
                (it.pron?.contains(s, true) ?: false) ||
                (it.subgroup?.contains(s, true) ?: false) ||
                it.group.contains(s, true)
        }
}

@Composable
private fun GroupDropdown(label: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text(label) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt) },
                    onClick = { expanded = false; onSelect(opt) }
                )
            }
        }
    }
}

@Composable
private fun FilterBar(
    cards: List<FlashCard>,
    filters: GroupFilters,
    onChange: (GroupFilters) -> Unit
) {
    val groups = remember(cards) { cards.map { it.group }.distinct().sorted() }
    val showSubgroup = filters.group != "All" && subgroupsFor(cards, filters.group).isNotEmpty()
    val subgroups = remember(cards, filters.group) {
        if (filters.group == "All") emptyList() else subgroupsFor(cards, filters.group)
    }

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = filters.group == "All",
                onClick = { onChange(filters.copy(group = "All", subgroup = "All")) },
                label = { Text("All") }
            )
            val first = groups.take(3)
            first.forEach { g ->
                FilterChip(
                    selected = filters.group == g,
                    onClick = { onChange(filters.copy(group = g, subgroup = "All")) },
                    label = { Text(g) }
                )
            }
            if (groups.size > 3) {
                GroupDropdown(
                    label = if (filters.group in groups.drop(3)) filters.group else "More...",
                    options = groups.drop(3),
                    onSelect = { g -> onChange(filters.copy(group = g, subgroup = "All")) }
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        if (showSubgroup) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = filters.subgroup == "All",
                    onClick = { onChange(filters.copy(subgroup = "All")) },
                    label = { Text("All ${filters.group}") }
                )
                GroupDropdown(
                    label = if (filters.subgroup != "All") filters.subgroup else "Choose subgroup...",
                    options = subgroups,
                    onSelect = { sg -> onChange(filters.copy(subgroup = sg)) }
                )
            }
            Spacer(Modifier.height(10.dp))
        }

        OutlinedTextField(
            value = filters.search,
            onValueChange = { onChange(filters.copy(search = it)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search (term / meaning / pron)") }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveScreen(nav: NavHostController, repo: Repository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tts = remember { TtsHelper(context) }
    DisposableEffect(Unit) { onDispose { tts.shutdown() } }

    val allCards by repo.allCardsFlow().collectAsState(initial = emptyList())
    val progress by repo.progressFlow().collectAsState(initial = ProgressState(emptySet(), emptySet()))

    var filters by remember { mutableStateOf(GroupFilters()) }
    var showFront by remember { mutableStateOf(true) }
    var index by remember { mutableIntStateOf(0) }

    val activeBase = remember(allCards, progress) {
        allCards.filter { it.id !in progress.learnedIds && it.id !in progress.deletedIds }
    }
    val activeCards = remember(activeBase, filters) { applyFilters(activeBase, filters) }

    LaunchedEffect(activeCards.size) {
        if (activeCards.isEmpty()) index = 0
        else index = index.coerceIn(0, activeCards.size - 1)
    }

    val learnedCount = progress.learnedIds.size
    val deletedCount = progress.deletedIds.size
    val totalCount = allCards.size
    val activeCount = (totalCount - learnedCount - deletedCount).coerceAtLeast(0)

    val current = activeCards.getOrNull(index)

    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val imported = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    CsvImport.parseCsv(input.bufferedReader())
                } ?: emptyList()
            }
            if (imported.isNotEmpty()) {
                repo.replaceCustomCards(imported)
                index = 0
                showFront = true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kenpo Flashcards") },
                actions = {
                    TextButton(onClick = { nav.navigate(Route.Learned.path) }) { Text("Learned") }
                    TextButton(onClick = { nav.navigate(Route.Deleted.path) }) { Text("Deleted") }
                }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier.fillMaxSize().padding(pad).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Active: $activeCount")
                Text("Learned: $learnedCount")
                Text("Deleted: $deletedCount")
            }

            Spacer(Modifier.height(12.dp))
            FilterBar(
                cards = activeBase,
                filters = filters,
                onChange = {
                    filters = it
                    index = 0
                    showFront = true
                }
            )
            Spacer(Modifier.height(14.dp))

            if (current == null) {
                Text(
                    text = "No active cards in this stack.\nTry switching group/subgroup, clearing search, or check Learned.",
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { csvLauncher.launch(arrayOf("text/*", "text/csv")) }) { Text("Import CSV") }
                    OutlinedButton(onClick = { scope.launch { repo.clearAllProgress() } }) { Text("Reset Progress") }
                }
            } else {
                Text("Card ${index + 1} / ${activeCards.size}")
                Spacer(Modifier.height(12.dp))

                FlipCard(
                    card = current,
                    showFront = showFront,
                    onFlip = { showFront = !showFront },
                    onSwipeNext = { if (index < activeCards.size - 1) { index++; showFront = true } },
                    onSwipePrev = { if (index > 0) { index--; showFront = true } }
                )

                Spacer(Modifier.height(12.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    OutlinedButton(onClick = { if (index > 0) { index--; showFront = true } }, enabled = index > 0) {
                        Text("Prev")
                    }
                    Button(onClick = {
                        val speakText = buildString {
                            append(current.term)
                            if (!current.pron.isNullOrBlank()) append(", pronounced ${current.pron}")
                        }
                        tts.speak(speakText)
                    }) { Text("Speak") }
                    OutlinedButton(
                        onClick = { if (index < activeCards.size - 1) { index++; showFront = true } },
                        enabled = index < activeCards.size - 1
                    ) { Text("Next") }
                }

                Spacer(Modifier.height(10.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = { scope.launch { repo.setLearned(current.id, true) } }
                    ) { Text("Got it ✓") }

                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = { csvLauncher.launch(arrayOf("text/*", "text/csv")) }
                    ) { Text("Import CSV") }
                }

                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { scope.launch { repo.clearAllProgress() } }
                ) { Text("Reset all progress") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearnedScreen(nav: NavHostController, repo: Repository) {
    val scope = rememberCoroutineScope()
    val allCards by repo.allCardsFlow().collectAsState(initial = emptyList())
    val progress by repo.progressFlow().collectAsState(initial = ProgressState(emptySet(), emptySet()))
    var filters by remember { mutableStateOf(GroupFilters()) }

    val learnedBase = remember(allCards, progress) {
        allCards.filter { it.id in progress.learnedIds && it.id !in progress.deletedIds }
    }
    val learnedCards = remember(learnedBase, filters) { applyFilters(learnedBase, filters) }
        .sortedWith(compareBy({ it.group }, { it.subgroup ?: "" }, { it.term }))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Learned (Got it)") },
                navigationIcon = { TextButton(onClick = { nav.popBackStack() }) { Text("Back") } }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp)) {
            Text("Learned cards: ${learnedCards.size}")
            Spacer(Modifier.height(12.dp))
            FilterBar(cards = learnedBase, filters = filters, onChange = { filters = it })
            Spacer(Modifier.height(12.dp))

            if (learnedCards.isEmpty()) {
                Text("Nothing learned yet (with current filters).")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(learnedCards, key = { it.id }) { c ->
                        Card(elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Text(c.term, style = MaterialTheme.typography.titleMedium)
                                Text(c.meaning, style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(6.dp))
                                Text("Group: ${c.group}", style = MaterialTheme.typography.bodySmall)
                                if (!c.subgroup.isNullOrBlank()) {
                                    Text("Subgroup: ${c.subgroup}", style = MaterialTheme.typography.bodySmall)
                                }

                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(onClick = { scope.launch { repo.setLearned(c.id, false) } }) {
                                        Text("Restore to Active")
                                    }
                                    OutlinedButton(onClick = {
                                        scope.launch {
                                            repo.setLearned(c.id, false)
                                            repo.setDeleted(c.id, true)
                                        }
                                    }) { Text("Move to Deleted") }
                                }
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
fun DeletedScreen(nav: NavHostController, repo: Repository) {
    val scope = rememberCoroutineScope()
    val allCards by repo.allCardsFlow().collectAsState(initial = emptyList())
    val progress by repo.progressFlow().collectAsState(initial = ProgressState(emptySet(), emptySet()))
    var filters by remember { mutableStateOf(GroupFilters()) }

    val deletedBase = remember(allCards, progress) { allCards.filter { it.id in progress.deletedIds } }
    val deletedCards = remember(deletedBase, filters) { applyFilters(deletedBase, filters) }
        .sortedWith(compareBy({ it.group }, { it.subgroup ?: "" }, { it.term }))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Deleted") },
                navigationIcon = { TextButton(onClick = { nav.popBackStack() }) { Text("Back") } }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp)) {
            Text("Deleted cards: ${deletedCards.size}")
            Spacer(Modifier.height(12.dp))
            FilterBar(cards = deletedBase, filters = filters, onChange = { filters = it })
            Spacer(Modifier.height(12.dp))

            if (deletedCards.isEmpty()) {
                Text("No deleted cards (with current filters).")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(deletedCards, key = { it.id }) { c ->
                        Card(elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Text(c.term, style = MaterialTheme.typography.titleMedium)
                                Text(c.meaning, style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(6.dp))
                                Text("Group: ${c.group}", style = MaterialTheme.typography.bodySmall)
                                if (!c.subgroup.isNullOrBlank()) {
                                    Text("Subgroup: ${c.subgroup}", style = MaterialTheme.typography.bodySmall)
                                }
                                Spacer(Modifier.height(10.dp))
                                Button(onClick = { scope.launch { repo.setDeleted(c.id, false) } }) {
                                    Text("Restore")
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
fun FlipCard(
    card: FlashCard,
    showFront: Boolean,
    onFlip: () -> Unit,
    onSwipeNext: () -> Unit,
    onSwipePrev: () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (showFront) 0f else 180f,
        animationSpec = tween(260),
        label = "flip"
    )
    val showFrontSide = rotation.absoluteValue <= 90f

    // Swipe: accumulate drag distance and decide on end (prevents multi-trigger)
    var dragTotal by remember { mutableStateOf(0f) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { dragTotal = 0f },
                    onHorizontalDrag = { _, dragAmount -> dragTotal += dragAmount },
                    onDragEnd = {
                        val threshold = 90f
                        if (dragTotal <= -threshold) onSwipeNext()
                        if (dragTotal >= threshold) onSwipePrev()
                        dragTotal = 0f
                    }
                )
            }
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density
            }
            .clickable { onFlip() },
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(18.dp),
            contentAlignment = Alignment.Center
        ) {
            if (showFrontSide) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(card.term, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                    if (!card.pron.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text("(${card.pron})", style = MaterialTheme.typography.bodyLarge)
                    }
                    Spacer(Modifier.height(10.dp))
                    AssistChip(onClick = {}, label = { Text(card.group) })
                    if (!card.subgroup.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(card.subgroup!!, style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                Box(modifier = Modifier.graphicsLayer { rotationY = 180f }, contentAlignment = Alignment.Center) {
                    Text(card.meaning, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
