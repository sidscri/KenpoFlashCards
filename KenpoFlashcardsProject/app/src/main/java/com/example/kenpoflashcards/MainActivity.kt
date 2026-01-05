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
import kotlin.random.Random

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

private enum class StudyMode(val label: String) {
    SINGLE_GROUP("Study one main group"),
    ALL_GROUPS("Study all groups")
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

private fun subgroupsFor(cards: List<FlashCard>, group: String): List<String> =
    cards.filter { it.group == group }.mapNotNull { it.subgroup }.distinct().sorted()

private fun applySingleGroupFilter(cards: List<FlashCard>, group: String?, subgroup: String?): List<FlashCard> {
    var out = cards
    if (!group.isNullOrBlank()) out = out.filter { it.group == group }
    if (!subgroup.isNullOrBlank()) out = out.filter { it.subgroup == subgroup }
    return out
}

private fun applySearch(cards: List<FlashCard>, search: String): List<FlashCard> {
    val s = search.trim()
    if (s.isBlank()) return cards
    return cards.filter {
        it.term.contains(s, true) ||
            it.meaning.contains(s, true) ||
            (it.pron?.contains(s, true) ?: false) ||
            (it.subgroup?.contains(s, true) ?: false) ||
            it.group.contains(s, true)
    }
}

private fun applySort(cards: List<FlashCard>, sortMode: SortMode): List<FlashCard> {
    return when (sortMode) {
        SortMode.RANDOM -> cards
        SortMode.GROUP_RANDOM -> {
            val groups = cards.groupBy { it.group }.toList().shuffled()
            buildList {
                for ((_, gcards) in groups) addAll(gcards.sortedBy { it.term.lowercase() })
            }
        }
        SortMode.GROUP_SUBGROUP -> cards.sortedWith(
            compareBy<FlashCard>({ it.group }, { it.subgroup ?: "" }, { it.term.lowercase() })
        )
    }
}

@Composable
private fun StudyModeDropdown(mode: StudyMode, onMode: (StudyMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text(mode.label) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            StudyMode.entries.forEach { m ->
                DropdownMenuItem(text = { Text(m.label) }, onClick = { expanded = false; onMode(m) })
            }
        }
    }
}

@Composable
private fun SortModeDropdown(mode: SortMode, onMode: (SortMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text("Sort: ${mode.label}") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortMode.entries.forEach { m ->
                DropdownMenuItem(text = { Text(m.label) }, onClick = { expanded = false; onMode(m) })
            }
        }
    }
}

@Composable
private fun GroupDropdown(groups: List<String>, selected: String?, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = selected ?: "Choose main group…"
    Box {
        Button(onClick = { expanded = true }, enabled = groups.isNotEmpty()) { Text(label) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            groups.forEach { g ->
                DropdownMenuItem(text = { Text(g) }, onClick = { expanded = false; onSelect(g) })
            }
        }
    }
}

@Composable
private fun SubgroupDropdown(subgroups: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = selected ?: "All subgroups"
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = subgroups.isNotEmpty()) { Text(label) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("All subgroups") }, onClick = { expanded = false; onSelect(null) })
            subgroups.forEach { sg ->
                DropdownMenuItem(text = { Text(sg) }, onClick = { expanded = false; onSelect(sg) })
            }
        }
    }
}

@Composable
private fun SettingsPanel(settings: StudySettings, onChange: (StudySettings) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { expanded = !expanded }) {
        Text(if (expanded) "Hide settings" else "Show settings")
    }

    if (!expanded) return

    Spacer(Modifier.height(10.dp))

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SortModeDropdown(settings.sortMode) { onChange(settings.copy(sortMode = it)) }
            FilterChip(
                selected = settings.randomize,
                onClick = { onChange(settings.copy(randomize = !settings.randomize)) },
                label = { Text("Randomize") }
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(
                selected = settings.reverseCards,
                onClick = { onChange(settings.copy(reverseCards = !settings.reverseCards)) },
                label = { Text("Reverse cards") }
            )
            FilterChip(
                selected = settings.showGroup,
                onClick = { onChange(settings.copy(showGroup = !settings.showGroup)) },
                label = { Text("Show main group") }
            )
            FilterChip(
                selected = settings.showSubgroup,
                onClick = { onChange(settings.copy(showSubgroup = !settings.showSubgroup)) },
                label = { Text("Show subgroup") }
            )
        }

        val hideAll = !settings.showGroup && !settings.showSubgroup
        AssistChip(
            onClick = {
                if (hideAll) onChange(settings.copy(showGroup = true, showSubgroup = true))
                else onChange(settings.copy(showGroup = false, showSubgroup = false))
            },
            label = { Text(if (hideAll) "Show grouping identifiers" else "Hide all grouping identifiers") }
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

    var studyMode by remember { mutableStateOf(StudyMode.ALL_GROUPS) }

    val singleSettings by repo.settingsSingleFlow().collectAsState(initial = StudySettings())
    val allSettings by repo.settingsAllFlow().collectAsState(initial = StudySettings())

    fun settingsNow(): StudySettings = if (studyMode == StudyMode.SINGLE_GROUP) singleSettings else allSettings

    suspend fun saveSettingsNow(s: StudySettings) {
        if (studyMode == StudyMode.SINGLE_GROUP) repo.saveSettingsSingle(s) else repo.saveSettingsAll(s)
    }

    var search by remember { mutableStateOf("") }
    var showFront by remember { mutableStateOf(true) }
    var index by remember { mutableIntStateOf(0) }

    val activeBase = remember(allCards, progress) {
        allCards.filter { it.id !in progress.learnedIds && it.id !in progress.deletedIds }
    }

    val groups = remember(activeBase) { activeBase.map { it.group }.distinct().sorted() }

    LaunchedEffect(studyMode, groups) {
        if (studyMode == StudyMode.SINGLE_GROUP) {
            val s = singleSettings
            if (s.selectedGroup.isNullOrBlank() && groups.isNotEmpty()) {
                repo.saveSettingsSingle(s.copy(selectedGroup = groups.first(), selectedSubgroup = null))
            }
        }
    }

    val sNow = settingsNow()

    val filtered = remember(activeBase, sNow, search, studyMode) {
        val base = when (studyMode) {
            StudyMode.ALL_GROUPS -> activeBase
            StudyMode.SINGLE_GROUP -> applySingleGroupFilter(activeBase, sNow.selectedGroup, sNow.selectedSubgroup)
        }
        applySearch(base, search)
    }

    val sorted = remember(filtered, sNow.sortMode) { applySort(filtered, sNow.sortMode) }

    val activeCards = remember(sorted, sNow.randomize) {
        if (!sNow.randomize) sorted else sorted.shuffled(Random(0xC0FFEE))
    }

    LaunchedEffect(activeCards.size) {
        if (activeCards.isEmpty()) index = 0
        else index = index.coerceIn(0, activeCards.size - 1)
        showFront = true
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
                    TextButton(onClick = { nav.navigate(Route.Active.path) }) { Text("Active") }
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

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StudyModeDropdown(studyMode) {
                        studyMode = it
                        index = 0
                        showFront = true
                    }
                    AssistChip(onClick = { studyMode = StudyMode.ALL_GROUPS }, label = { Text("All") })
                }

                if (studyMode == StudyMode.SINGLE_GROUP) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GroupDropdown(groups = groups, selected = sNow.selectedGroup) { sel ->
                            scope.launch {
                                saveSettingsNow(sNow.copy(selectedGroup = sel, selectedSubgroup = null))
                                index = 0
                                showFront = true
                            }
                        }
                        val sgs = if (!sNow.selectedGroup.isNullOrBlank())
                            subgroupsFor(activeBase, sNow.selectedGroup!!)
                        else emptyList()
                        SubgroupDropdown(subgroups = sgs, selected = sNow.selectedSubgroup) { sg ->
                            scope.launch {
                                saveSettingsNow(sNow.copy(selectedSubgroup = sg))
                                index = 0
                                showFront = true
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it; index = 0; showFront = true },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search (term / meaning / pron)") }
                )

                SettingsPanel(settings = sNow) { newSettings ->
                    scope.launch { saveSettingsNow(newSettings) }
                }
            }

            Spacer(Modifier.height(14.dp))

            if (current == null) {
                Text(
                    text = "No active cards in this study set.\nTry changing study mode/group, clearing search, or check Learned.",
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
                    showGroup = sNow.showGroup,
                    showSubgroup = sNow.showSubgroup,
                    reverseCards = sNow.reverseCards,
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
                        onClick = {
                            scope.launch {
                                repo.setLearned(current.id, true)
                                if (index >= activeCards.size - 1) index = (activeCards.size - 2).coerceAtLeast(0)
                                showFront = true
                            }
                        }
                    ) { Text("Got it ✓ (mark learned)") }

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
    var search by remember { mutableStateOf("") }

    val learnedCards = remember(allCards, progress, search) {
        applySearch(
            allCards.filter { it.id in progress.learnedIds && it.id !in progress.deletedIds },
            search
        ).sortedWith(compareBy({ it.group }, { it.subgroup ?: "" }, { it.term }))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Learned (Got it)") },
                actions = {
                    TextButton(onClick = { nav.navigate(Route.Active.path) }) { Text("Active") }
                    TextButton(onClick = { nav.navigate(Route.Deleted.path) }) { Text("Deleted") }
                }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp)) {
            Text("Learned cards: ${learnedCards.size}")
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search learned") }
            )
            Spacer(Modifier.height(12.dp))

            if (learnedCards.isEmpty()) {
                Text("Nothing learned yet (with current search).")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(learnedCards, key = { it.id }) { c ->
                        Card(elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Text(c.term, style = MaterialTheme.typography.titleMedium)
                                if (!c.pron.isNullOrBlank()) Text("(${c.pron})", style = MaterialTheme.typography.bodySmall)
                                Text(c.meaning, style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(6.dp))
                                Text("Group: ${c.group}", style = MaterialTheme.typography.bodySmall)
                                if (!c.subgroup.isNullOrBlank()) {
                                    Text("Subgroup: ${c.subgroup}", style = MaterialTheme.typography.bodySmall)
                                }

                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(onClick = { scope.launch { repo.setLearned(c.id, false) } }) {
                                        Text("Unmark learned")
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
    var search by remember { mutableStateOf("") }

    val deletedCards = remember(allCards, progress, search) {
        applySearch(allCards.filter { it.id in progress.deletedIds }, search)
            .sortedWith(compareBy({ it.group }, { it.subgroup ?: "" }, { it.term }))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Deleted") },
                actions = {
                    TextButton(onClick = { nav.navigate(Route.Active.path) }) { Text("Active") }
                    TextButton(onClick = { nav.navigate(Route.Learned.path) }) { Text("Learned") }
                }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp)) {
            Text("Deleted cards: ${deletedCards.size}")
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search deleted") }
            )
            Spacer(Modifier.height(12.dp))

            if (deletedCards.isEmpty()) {
                Text("No deleted cards (with current search).")
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
    showGroup: Boolean,
    showSubgroup: Boolean,
    reverseCards: Boolean,
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

    var dragTotal by remember { mutableStateOf(0f) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
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
            val frontIsMeaning = reverseCards
            val frontTitle = if (frontIsMeaning) card.meaning else card.term
            val backTitle = if (frontIsMeaning) card.term else card.meaning

            if (showFrontSide) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(frontTitle, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)

                    if (!reverseCards && !card.pron.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text("(${card.pron})", style = MaterialTheme.typography.bodyLarge)
                    }

                    Spacer(Modifier.height(12.dp))

                    if (showGroup) {
                        AssistChip(onClick = {}, label = { Text(card.group) })
                    }
                    if (showSubgroup && !card.subgroup.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(card.subgroup!!, style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                Box(modifier = Modifier.graphicsLayer { rotationY = 180f }, contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(backTitle, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                        if (reverseCards && !card.pron.isNullOrBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text("(${card.pron})", style = MaterialTheme.typography.bodyLarge)
                        }
                        if (reverseCards) {
                            Spacer(Modifier.height(12.dp))
                            if (showGroup) AssistChip(onClick = {}, label = { Text(card.group) })
                            if (showSubgroup && !card.subgroup.isNullOrBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(card.subgroup!!, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
