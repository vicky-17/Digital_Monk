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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import com.digitalmonk.app.data.local.prefs.PrefsManager
import com.digitalmonk.app.data.local.db.AppDatabase // Add this
import com.digitalmonk.app.data.local.db.entity.AppBlockRule // Add this
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

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
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                val db = AppDatabase.getDatabase(context.applicationContext) // Get DB
                return LocksViewModel(context.applicationContext, prefs, db.appBlockDao()) as T
            }
        }
    )

    val activeRules by viewModel.activeRules.collectAsState()
    val wizardState by viewModel.wizardState.collectAsState()
    val websites by viewModel.blockedWebsites.collectAsState()

    var showWizard by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Apps", "Websites")


    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
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
                    width = 60.dp
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
                            ActiveRuleItem(
                                rule = rule,
                                onDelete = { viewModel.removeRule(rule.packageName) }
                            )
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
                            WebsiteLockItem(
                                domain = domain,
                                onDelete = { viewModel.removeWebsite(domain) }
                            )
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
        AppBlockWizard(
            state = wizardState,
            viewModel = viewModel,
            onDismiss = { showWizard = false }
        )
    }
}


@Composable
private fun ActiveRuleItem(rule: AppBlockRule, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(16.dp)
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
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = ScreenBg
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { if (state.currentStep > 1) viewModel.previousStep() else onDismiss() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                    Text(
                        text = "Step ${state.currentStep}/6",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = TextPrimary)
                    }
                }

                // Step Content
                Box(modifier = Modifier.weight(1f)) {
                    AnimatedContent(
                        targetState = state.currentStep,
                        transitionSpec = {
                            if (targetState > initialState) {
                                slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                            } else {
                                slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                            }.using(SizeTransform(clip = false))
                        }, label = "StepTransition"
                    ) { step ->
                        when (step) {
                            1 -> Step1PlanType(state, viewModel)
                            2 -> Step2Applications(viewModel)
                            3 -> Step3TimingMode(state, viewModel)
                            4 -> Step4TimingDetails(state, viewModel)
                            5 -> Step5Protection(state, viewModel)
                            6 -> Step6Review(state, viewModel, onDismiss)
                        }
                    }
                }

                // Bottom Navigation
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TextSecond)
                    }

                    if (state.currentStep < 6) {
                        Button(
                            onClick = { viewModel.nextStep() },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Next")
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
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
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Choose a Plan Type", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text("This plan will manage your chosen apps whenever it is active.", fontSize = 13.sp, color = TextSecond)

        Spacer(Modifier.height(24.dp))

        // Grid of Plan Types
        val plans = listOf(
            Triple("Stay Focused", "Block chosen apps completely.", "STAY_FOCUSED"),
            Triple("Time Limit", "Set a daily or hourly time budget.", "TIME_LIMIT"),
            Triple("Train Habits", "Limit how many times you launch apps.", "HABIT_TRAINING"),
            Triple("Screen Breaks", "Enforce regular breaks away from apps.", "SCREEN_BREAK")
        )

        plans.forEach { (title, desc, type) ->
            val isSelected = state.planType == type
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clickable { viewModel.updateWizard { it.copy(planType = type) } },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) AccentCyan.copy(alpha = 0.15f) else CardBg
                ),
                border = if (isSelected) BorderStroke(2.dp, AccentCyan) else null,
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = isSelected, onClick = { viewModel.updateWizard { it.copy(planType = type) } }, colors = RadioButtonDefaults.colors(selectedColor = AccentCyan))
                    Column {
                        Text(title, color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text(desc, color = TextSecond, fontSize = 12.sp)
                    }
                }
            }
        }

        // Dynamic Settings based on selected plan
        if (state.planType == "TIME_LIMIT") {
            Spacer(Modifier.height(24.dp))
            Text("Limit Settings", color = AccentCyan, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Minutes Allowed:", color = TextPrimary, modifier = Modifier.weight(1f))
                TextField(
                    value = state.allowedMinutes.toString(),
                    onValueChange = { newValue -> viewModel.updateWizard { it.copy(allowedMinutes = newValue.toIntOrNull() ?: 0) } },
                    modifier = Modifier.width(80.dp),
                    colors = TextFieldDefaults.colors(focusedContainerColor = CardBg, unfocusedContainerColor = CardBg)
                )
            }
        }
    }
}

@Composable
private fun Step2Applications(viewModel: LocksViewModel) {
    val apps by viewModel.selectableApps.collectAsState()
    val wizardState by viewModel.wizardState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Select Applications", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.onSearchQueryChange(it) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            placeholder = { Text("Search apps...", color = TextSecond) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = TextSecond) },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentCyan)
        )

        // Block Method Selection
        var showMethodDropdown by remember { mutableStateOf(false) }
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedCard(
                onClick = { showMethodDropdown = true },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Settings, contentDescription = null, tint = AccentCyan)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Block Method", color = TextSecond, fontSize = 12.sp)
                        Text(wizardState.blockMethod, color = TextPrimary, fontWeight = FontWeight.Bold)
                    }
                    Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = TextSecond)
                }
            }

            DropdownMenu(
                expanded = showMethodDropdown,
                onDismissRequest = { showMethodDropdown = false },
                modifier = Modifier.background(CardBg)
            ) {
                listOf("SUSPEND", "HIDE", "KILL", "GO_HOME", "INTERSTITIAL").forEach { method ->
                    DropdownMenuItem(
                        text = { Text(method, color = TextPrimary) },
                        onClick = {
                            viewModel.updateWizard { it.copy(blockMethod = method) }
                            showMethodDropdown = false
                        }
                    )
                }
            }
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(apps) { app ->
                val isSelected = wizardState.selectedPackages.contains(app.packageName)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.toggleAppSelection(app.packageName) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = isSelected, onCheckedChange = { viewModel.toggleAppSelection(app.packageName) }, colors = CheckboxDefaults.colors(checkedColor = AccentCyan))
                    Spacer(Modifier.width(12.dp))
                    Text(app.name, color = TextPrimary, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Step3TimingMode(state: WizardState, viewModel: LocksViewModel) {
    Column(modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())) {
        Text("Set Timing Mode", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(24.dp))

        val modes = listOf(
            Triple("On-demand", "Start a focus session manually.", "ON_DEMAND"),
            Triple("Weekly Schedule", "Set a recurring weekly schedule.", "WEEKLY"),
            Triple("Pomodoro", "Work in structured sprints with breaks.", "POMODORO"),
            Triple("Multi-Day Plan", "Design a plan for several days.", "MULTI_DAY")
        )

        modes.forEach { (title, desc, mode) ->
            val isSelected = state.timingMode == mode
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clickable { viewModel.updateWizard { it.copy(timingMode = mode) } },
                colors = CardDefaults.cardColors(containerColor = if (isSelected) AccentCyan.copy(alpha = 0.15f) else CardBg),
                border = if (isSelected) BorderStroke(2.dp, AccentCyan) else null,
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = isSelected, onClick = { viewModel.updateWizard { it.copy(timingMode = mode) } }, colors = RadioButtonDefaults.colors(selectedColor = AccentCyan))
                    Column {
                        Text(title, color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text(desc, color = TextSecond, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun Step4TimingDetails(state: WizardState, viewModel: LocksViewModel) {
    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Timing Details", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(24.dp))

        Text("Configure how this plan triggers.", color = TextSecond)
        // Here we can add calendar pickers or time range sliders later.
        // For now, let's keep it simple.
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Settings for ${state.timingMode} will go here.", color = TextSecond, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun Step5Protection(state: WizardState, viewModel: LocksViewModel) {
    Column(modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())) {
        Text("Protection", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(24.dp))

        // Anti-Uninstall Toggle
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Anti-Uninstall", color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text("Prevent uninstallation of blocked apps.", color = TextSecond, fontSize = 12.sp)
                }
                Switch(
                    checked = state.isAntiUninstall,
                    onCheckedChange = { val value = it; viewModel.updateWizard { it.copy(isAntiUninstall = value) } },
                    colors = SwitchDefaults.colors(checkedTrackColor = AccentCyan)
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Challenge to Stop Plan", color = TextPrimary, fontWeight = FontWeight.SemiBold)

        val challenges = listOf("NONE", "RANDOM_CHARS", "PIN")
        challenges.forEach { challenge ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.updateWizard { it.copy(stopChallenge = challenge) } }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = state.stopChallenge == challenge, onClick = { viewModel.updateWizard { it.copy(stopChallenge = challenge) } }, colors = RadioButtonDefaults.colors(selectedColor = AccentCyan))
                Text(challenge.replace("_", " ").lowercase().capitalize(), color = TextPrimary)
            }
        }
    }
}

@Composable
private fun Step6Review(state: WizardState, viewModel: LocksViewModel, onFinish: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())) {
        Text("Plan Information", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = state.planName,
            onValueChange = { val name = it; viewModel.updateWizard { it.copy(planName = name) } },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Plan Name") },
            placeholder = { Text("e.g., Deep Work") },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentCyan, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
        )

        Spacer(Modifier.height(32.dp))

        // Summary Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = AccentCyan.copy(alpha = 0.1f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Summary", fontWeight = FontWeight.Bold, color = AccentCyan)
                Text("Type: ${state.planType}", color = TextPrimary)
                Text("Apps: ${state.selectedPackages.size} selected", color = TextPrimary)
                Text("Timing: ${state.timingMode}", color = TextPrimary)
                Text("Challenge: ${state.stopChallenge}", color = TextPrimary)
            }
        }

        Spacer(Modifier.height(40.dp))

        Button(
            onClick = { viewModel.saveWizardPlan(); onFinish() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Create Plan", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
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

@Composable
private fun AddWebsiteDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var domain by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Block Website") },
        text = {
            Column {
                Text("Enter the domain name to block (e.g., example.com)", fontSize = 12.sp, color = TextSecond)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = domain,
                    onValueChange = { domain = it },
                    placeholder = { Text("example.com") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = Color(0xFF1E293B)
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (domain.isNotBlank()) onConfirm(domain) }) {
                Text("Block", color = AccentCyan)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecond)
            }
        },
        containerColor = CardBg,
        titleContentColor = TextPrimary,
        textContentColor = TextPrimary
    )
}
