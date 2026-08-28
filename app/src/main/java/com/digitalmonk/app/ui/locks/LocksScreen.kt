package com.digitalmonk.app.ui.locks

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.digitalmonk.app.data.local.prefs.PrefsManager
import com.digitalmonk.app.data.local.db.AppDatabase // Add this
import com.digitalmonk.app.data.local.db.entity.AppBlockRule // Add this
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.launch

// ── Color palette ─────────────────────────────────────────────────────────────
private val ScreenBg   = Color(0xFF080E1A)
private val CardBg     = Color(0xFF111827)
private val AccentCyan = Color(0xFF06B6D4)
private val TextPrimary = Color(0xFFF1F5F9)
private val TextSecond  = Color(0xFF64748B)
private val AccentRed   = Color(0xFFEF4444)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocksScreen(prefs: PrefsManager) {
    val context = LocalContext.current
    val viewModel: LocksViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val db = AppDatabase.getDatabase(context.applicationContext) // Get DB
                return LocksViewModel(context.applicationContext, prefs, db.appBlockDao()) as T
            }
        },
    )

    val activeRules by viewModel.activeRules.collectAsState()
    val wizardState by viewModel.wizardState.collectAsState()
    val websites by viewModel.blockedWebsites.collectAsState()

    var showWizard by remember { mutableStateOf(value = false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Apps", "Websites")


    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg),
    ) {
        LocksHeader()

        // ── Tabs ──────────────────────────────────────────────────────────────
        PrimaryTabRow(
            selectedTabIndex = selectedTab,
            containerColor = ScreenBg,
            contentColor = AccentCyan,
            divider = {},
            indicator = {
                TabRowDefaults.PrimaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(selectedTab),
                    color = AccentCyan,
                    width = 60.dp,
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, fontSize = 14.sp) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Box(modifier = Modifier.fillMaxSize()) {
            if (selectedTab == 0) {
                // ── Apps Tab (Active Plans) ──────────────────────────────────
                if (activeRules.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.Lock, contentDescription = null, tint = TextSecond, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("No active app plans", color = TextSecond)
                            Text("Click + to add your first plan", color = TextSecond.copy(alpha = 0.6f), fontSize = 12.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(activeRules, key = { it.packageName }) { rule ->
                            ActiveRuleItem(rule = rule) {
                                viewModel.removeRule(rule.packageName)
                            }
                        }
                    }
                }

                FloatingActionButton(
                    onClick = { showWizard = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(24.dp),
                    containerColor = AccentCyan,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add Plan")
                }

            } else {
                // ── Websites Tab ──────────────────────────────────────────────
                if (websites.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No blocked websites yet", color = TextSecond)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(websites.toList()) { domain ->
                            WebsiteLockItem(domain = domain) {
                                viewModel.removeWebsite(domain)
                            }
                        }
                    }
                }

                FloatingActionButton(
                    onClick = { /* Implement website dialog if needed */ },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(24.dp),
                    containerColor = AccentCyan,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add Website")
                }
            }
        }
    }

    if (showWizard) {
        ModalBottomSheet(
            onDismissRequest = { showWizard = false },
            sheetState = sheetState,
            containerColor = ScreenBg,
            dragHandle = { BottomSheetDefaults.DragHandle(color = TextSecond.copy(alpha = 0.5f)) }
        ) {
            AppBlockWizard(
                state = wizardState,
                viewModel = viewModel
            ) {
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    showWizard = false
                }
            }
        }
    }
}


@Composable
private fun ActiveRuleItem(rule: AppBlockRule, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(AccentCyan.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when(rule.planType) {
                        "STAY_FOCUSED" -> Icons.Rounded.Block
                        "TIME_LIMIT" -> Icons.Rounded.HourglassEmpty
                        "HABIT_TRAINING" -> Icons.Rounded.Repeat
                        else -> Icons.Rounded.Coffee
                    },
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(rule.appName, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                val detailText = when(rule.planType) {
                    "STAY_FOCUSED" -> "Always Blocked"
                    "TIME_LIMIT" -> "${rule.allowedMinutes}m limit / ${rule.intervalMinutes}m"
                    "HABIT_TRAINING" -> "${rule.maxLaunches} launches max"
                    else -> "Screen Breaks active"
                }
                Text(detailText, color = TextSecond, fontSize = 12.sp)
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, contentDescription = "Remove", tint = AccentRed.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
fun AppBlockWizard(
    state: WizardState,
    viewModel: LocksViewModel,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f) // Take up ~3/4 of the screen
            .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
    ) {
        // Header with Progress
        Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Discard Action
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { 
                            viewModel.resetWizard()
                            onDismiss()
                        }
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Discard",
                        tint = AccentRed,
                        modifier = Modifier.size(22.dp)
                    )
                    Text("Discard", color = AccentRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                // Center: Title & Progress
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val stepTitle = when(state.currentStep) {
                        1 -> "Choose Strategy"
                        2 -> "Target Apps"
                        3 -> "Trigger Mode"
                        4 -> "Configuration"
                        5 -> "Security"
                        6 -> "Final Review"
                        else -> "Set up Plan"
                    }
                    Text(
                        text = stepTitle,
                        color = TextPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "Step ${state.currentStep} of 6",
                        color = TextSecond,
                        fontSize = 11.sp
                    )
                }

                // Right: Clear Action
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { viewModel.clearCurrentStep() }
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Clear",
                        tint = AccentCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Text("Clear", color = AccentCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Progress Bar
            LinearProgressIndicator(
                progress = { state.currentStep / 6f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = AccentCyan,
                trackColor = CardBg,
            )
        }

        // Step Content
        Box(modifier = Modifier.weight(1f)) {
            AnimatedContent(
                targetState = state.currentStep,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it } + fadeOut())
                    } else {
                        (slideInHorizontally { -it } + fadeIn()) togetherWith (slideOutHorizontally { it } + fadeOut())
                    }.using(SizeTransform(clip = false))
                },
                label = "StepTransition"
            ) { step ->
                when (step) {
                    1 -> Step1PlanType(state, viewModel)
                    2 -> Step2Applications(viewModel)
                    3 -> Step3TimingMode(state, viewModel)
                    4 -> Step4TimingDetails(state, viewModel)
                    5 -> Step5Protection(state, viewModel)
                    6 -> Step6Review(state, viewModel)
                }
            }
        }

        // Bottom Navigation
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = CardBg,
            tonalElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { if (state.currentStep > 1) viewModel.previousStep() else onDismiss() },
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(
                        imageVector = if (state.currentStep > 1) Icons.AutoMirrored.Rounded.ArrowBack else Icons.Rounded.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = TextSecond
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.currentStep > 1) "Back" else "Cancel", color = TextSecond, fontWeight = FontWeight.Medium)
                }

                Button(
                    onClick = { if (state.currentStep < 6) viewModel.nextStep() else { viewModel.saveWizardPlan(); onDismiss() } },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.height(48.dp).padding(horizontal = 4.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Text(if (state.currentStep < 6) "Next" else "Finish", fontWeight = FontWeight.Bold)
                    if (state.currentStep < 6) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Step1PlanType(state: WizardState, viewModel: LocksViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Select how you want to manage these apps.", fontSize = 14.sp, color = TextSecond)

        Spacer(Modifier.height(24.dp))

        val plans = listOf(
            PlanItem("Stay Focused", "Total block for maximum productivity.", "STAY_FOCUSED", Icons.Rounded.Bolt),
            PlanItem("Time Limit", "Set a daily or hourly time budget.", "TIME_LIMIT", Icons.Rounded.Timer),
            PlanItem("Train Habits", "Limit how many times you launch.", "HABIT_TRAINING", Icons.Rounded.Psychology),
            PlanItem("Screen Breaks", "Enforce breaks away from the screen.", "SCREEN_BREAK", Icons.Rounded.Coffee)
        )

        plans.forEach { plan ->
            val isSelected = state.planType == plan.type
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clickable { viewModel.updateWizard { it.copy(planType = plan.type) } },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) AccentCyan.copy(alpha = 0.12f) else CardBg
                ),
                border = BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) AccentCyan else TextSecond.copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                if (isSelected) AccentCyan else TextSecond.copy(alpha = 0.1f),
                                RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = plan.icon,
                            contentDescription = null,
                            tint = if (isSelected) Color.White else TextSecond,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = plan.name,
                            color = if (isSelected) AccentCyan else TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = plan.desc,
                            color = TextSecond,
                            fontSize = 12.sp
                        )
                    }

                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = AccentCyan
                        )
                    }
                }
            }
        }
    }
}

private data class PlanItem(val name: String, val desc: String, val type: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
private fun Step2Applications(viewModel: LocksViewModel) {
    val apps by viewModel.selectableApps.collectAsState()
    val wizardState by viewModel.wizardState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isDataLoading by viewModel.isAppsLoading.collectAsState()

    // Local state to force at least 2 seconds of loading animation when entering this step
    var isAnimationRunning by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2000)
        isAnimationRunning = false
    }

    val isLoading = isDataLoading || isAnimationRunning

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Text("Select the apps you want to restrict.", fontSize = 14.sp, color = TextSecond)

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.onSearchQueryChange(it) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            placeholder = { Text("Search apps...", color = TextSecond) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = AccentCyan) },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentCyan,
                unfocusedBorderColor = TextSecond.copy(alpha = 0.2f),
                focusedContainerColor = CardBg,
                unfocusedContainerColor = CardBg
            )
        )

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = AccentCyan)
                    Spacer(Modifier.height(16.dp))
                    Text("Scanning apps...", color = TextSecond, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(apps) { app ->
                    val isSelected = wizardState.selectedPackages.contains(app.packageName)
                    Card(
                        onClick = { viewModel.toggleAppSelection(app.packageName) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) AccentCyan.copy(alpha = 0.08f) else Color.Transparent
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = if (isSelected) BorderStroke(1.dp, AccentCyan.copy(alpha = 0.3f)) else null
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Icon on Left
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = rememberAsyncImagePainter(app.icon),
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            
                            Spacer(Modifier.width(16.dp))

                            // Name and Package in Center
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.name,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = app.packageName,
                                    color = TextSecond,
                                    fontSize = 11.sp
                                )
                            }

                            // Checkbox on Right
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { viewModel.toggleAppSelection(app.packageName) },
                                colors = CheckboxDefaults.colors(checkedColor = AccentCyan)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Step3TimingMode(state: WizardState, viewModel: LocksViewModel) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).verticalScroll(rememberScrollState())) {
        Text("When should this restriction be active?", fontSize = 14.sp, color = TextSecond)

        Spacer(Modifier.height(24.dp))

        val modes = listOf(
            TimingModeItem("On-demand", "Start a session manually.", "ON_DEMAND", Icons.Rounded.TouchApp),
            TimingModeItem("Weekly Schedule", "Set recurring weekly time slots.", "WEEKLY", Icons.Rounded.Event),
            TimingModeItem("Pomodoro", "Structured focus sprints.", "POMODORO", Icons.Rounded.AvTimer),
            TimingModeItem("Multi-Day Plan", "Custom plan across multiple days.", "MULTI_DAY", Icons.Rounded.CalendarMonth)
        )

        modes.forEach { mode ->
            val isSelected = state.timingMode == mode.type
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clickable { viewModel.updateWizard { it.copy(timingMode = mode.type) } },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) AccentCyan.copy(alpha = 0.12f) else CardBg
                ),
                border = BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) AccentCyan else TextSecond.copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column {
                    Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    if (isSelected) AccentCyan else TextSecond.copy(alpha = 0.1f),
                                    RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = mode.icon,
                                contentDescription = null,
                                tint = if (isSelected) Color.White else TextSecond,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(mode.title, color = if (isSelected) AccentCyan else TextPrimary, fontWeight = FontWeight.Bold)
                            Text(mode.desc, color = TextSecond, fontSize = 12.sp)
                        }

                        if (isSelected) {
                            Icon(Icons.Rounded.CheckCircle, null, tint = AccentCyan)
                        }
                    }

                    // Expand if Weekly Schedule is selected
                    AnimatedVisibility(
                        visible = isSelected && mode.type == "WEEKLY",
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp)
                        ) {
                            HorizontalDivider(color = TextSecond.copy(alpha = 0.1f), modifier = Modifier.padding(bottom = 16.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val days = listOf("M", "T", "W", "T", "F", "S", "S")
                                days.forEachIndexed { index, day ->
                                    val dayNum = index + 1
                                    val isDaySelected = state.selectedDays.contains(dayNum)
                                    
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = day,
                                            color = if (isDaySelected) AccentCyan else TextSecond,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Checkbox(
                                            checked = isDaySelected,
                                            onCheckedChange = { viewModel.toggleDaySelection(dayNum) },
                                            colors = CheckboxDefaults.colors(checkedColor = AccentCyan)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class TimingModeItem(val title: String, val desc: String, val type: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
private fun Step4TimingDetails(state: WizardState, viewModel: LocksViewModel) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).verticalScroll(rememberScrollState())) {
        Text("Fine-tune how your strategy works.", fontSize = 14.sp, color = TextSecond)

        Spacer(Modifier.height(32.dp))

        when (state.planType) {
            "TIME_LIMIT" -> {
                Text("Daily Time Budget", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Schedule, null, tint = AccentCyan)
                        Spacer(Modifier.width(16.dp))
                        Text("Minutes per day", color = TextPrimary, modifier = Modifier.weight(1f))
                        
                        TextField(
                            value = state.allowedMinutes.toString(),
                            onValueChange = { newValue -> 
                                viewModel.updateWizard { it.copy(allowedMinutes = newValue.toIntOrNull() ?: 0) }
                            },
                            modifier = Modifier.width(70.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = AccentCyan
                            ),
                            singleLine = true
                        )
                    }
                }
            }
            "HABIT_TRAINING" -> {
                Text("Launch Restrictions", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.RestartAlt, null, tint = AccentCyan)
                        Spacer(Modifier.width(16.dp))
                        Text("Max launches", color = TextPrimary, modifier = Modifier.weight(1f))
                        
                        TextField(
                            value = state.maxLaunches.toString(),
                            onValueChange = { newValue -> 
                                viewModel.updateWizard { it.copy(maxLaunches = newValue.toIntOrNull() ?: 0) }
                            },
                            modifier = Modifier.width(70.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = AccentCyan
                            ),
                            singleLine = true
                        )
                    }
                }
            }
            else -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No extra configuration needed for\n${state.planType.lowercase().replace("_", " ")}.",
                        color = TextSecond,
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun Step5Protection(state: WizardState, viewModel: LocksViewModel) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).verticalScroll(rememberScrollState())) {
        Text("Prevent yourself from bypassing the lock.", fontSize = 14.sp, color = TextSecond)

        Spacer(Modifier.height(24.dp))

        // Anti-Uninstall Toggle
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, if (state.isAntiUninstall) AccentCyan.copy(alpha = 0.4f) else Color.Transparent)
        ) {
            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(40.dp).background(AccentCyan.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Shield, null, tint = AccentCyan, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Anti-Uninstall", color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text("Prevent app deletion.", color = TextSecond, fontSize = 12.sp)
                }
                Switch(
                    checked = state.isAntiUninstall,
                    onCheckedChange = { newValue -> viewModel.updateWizard { it.copy(isAntiUninstall = newValue) } },
                    colors = SwitchDefaults.colors(checkedTrackColor = AccentCyan)
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("Challenge to Exit", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(12.dp))

        val challenges = listOf(
            Triple("None", "Exit freely anytime.", "NONE"),
            Triple("Random Text", "Type random characters to exit.", "RANDOM_CHARS"),
            Triple("Fixed PIN", "Enter your security PIN.", "PIN")
        )

        challenges.forEach { (title, desc, challenge) ->
            val isSelected = state.stopChallenge == challenge
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clickable { viewModel.updateWizard { it.copy(stopChallenge = challenge) } },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) AccentCyan.copy(alpha = 0.08f) else CardBg
                ),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, if (isSelected) AccentCyan else TextSecond.copy(alpha = 0.05f))
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { viewModel.updateWizard { it.copy(stopChallenge = challenge) } },
                        colors = RadioButtonDefaults.colors(selectedColor = AccentCyan)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(desc, color = TextSecond, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun Step6Review(state: WizardState, viewModel: LocksViewModel) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).verticalScroll(rememberScrollState())) {
        Text("Give your plan a name and verify details.", fontSize = 14.sp, color = TextSecond)

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = state.planName,
            onValueChange = { name -> viewModel.updateWizard { it.copy(planName = name) } },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Plan Name", color = AccentCyan) },
            placeholder = { Text("e.g., Deep Work", color = TextSecond) },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentCyan,
                unfocusedBorderColor = TextSecond.copy(alpha = 0.2f),
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            )
        )

        Spacer(Modifier.height(32.dp))

        // Premium Summary Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = AccentCyan),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White)
                    Spacer(Modifier.width(12.dp))
                    Text("Plan Overview", fontWeight = FontWeight.Black, color = Color.White, fontSize = 18.sp)
                }
                
                Spacer(Modifier.height(20.dp))
                
                SummaryRow("Strategy", state.planType.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() })
                SummaryRow("Apps", "${state.selectedPackages.size} Selected")
                SummaryRow("Timing", state.timingMode.lowercase().replaceFirstChar { it.uppercase() })
                SummaryRow("Protection", state.stopChallenge.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() })
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun LocksHeader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp)
    ) {
        Column {
            Text("🔒 Locks", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text("Manage app and website restrictions.", fontSize = 13.sp, color = TextSecond)
        }
    }
}


@Composable
private fun WebsiteLockItem(domain: String, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.Public,
            contentDescription = null,
            tint = AccentCyan,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = domain,
            color = TextPrimary,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = "Remove",
                tint = AccentRed.copy(alpha = 0.7f)
            )
        }
    }
}

/*
@Composable
private fun AddWebsiteDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
...
}
*/
