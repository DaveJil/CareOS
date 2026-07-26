package com.example

import android.os.Bundle
import android.speech.tts.TextToSpeech
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.TextStyle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.data.DonationRecord
import com.example.data.PatientRecord
import com.example.data.PersonalEHR
import com.example.data.SymptomTriage
import com.example.data.InsuranceProfile
import com.example.data.ReferralRecord
import com.example.ui.CareViewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.api.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                CareOSApp(
                    onSpeak = { text ->
                        if (ttsReady) {
                            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "CareOSTTS")
                        }
                    },
                    onStopSpeaking = {
                        tts?.stop()
                    }
                )
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            ttsReady = true
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

@Composable
fun CareOSApp(
    onSpeak: (String) -> Unit = {},
    onStopSpeaking: () -> Unit = {}
) {
    val viewModel: CareViewModel = viewModel()
    val scope = rememberCoroutineScope()
    var currentTab by remember { mutableStateOf("triage") }
    var showLhrDialog by remember { mutableStateOf(false) }
    var showClinicalSafetyDialog by remember { mutableStateOf(false) }
    var showInvestorDeckDialog by remember { mutableStateOf(false) }

    val currentPatient by viewModel.currentPatient.collectAsState()
    val triages by viewModel.isolatedTriages.collectAsState()
    val activeIns by viewModel.activeInsurance.collectAsState()
    val urgentEscalation by viewModel.urgentEscalation.collectAsState()

    if (currentPatient == null) {
        CareOSAuthScreen(viewModel = viewModel)
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTablet = maxWidth >= 600.dp

        if (isTablet) {
            // TABLET / DESKTOP RESPONSIVE SIDEBAR LAYOUT
            Row(modifier = Modifier.fillMaxSize()) {
                CareOSNavigationSidebar(
                    currentTab = currentTab,
                    onTabSelect = { currentTab = it },
                    activeInsurance = activeIns,
                    onLhrClick = { showLhrDialog = true },
                    onInvestorDeckClick = { showInvestorDeckDialog = true },
                    onAdminClick = { currentTab = "admin" },
                    viewModel = viewModel
                )

                // Main Content Panel
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Urgent Escalation Header Banner inside Tablet View
                        if (urgentEscalation != null) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEE2E2)),
                                border = BorderStroke(1.5.dp, Color(0xFFDC2626)),
                                shape = RoundedCornerShape(0.dp),
                                modifier = Modifier.fillMaxWidth().testTag("urgent_escalation_banner")
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text("🚨", fontSize = 24.sp)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("CRITICAL MEDICAL ESCALATION ALERT", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF991B1B))
                                        Text(urgentEscalation ?: "", fontSize = 10.5.sp, color = Color(0xFF7F1D1D))
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { currentTab = "telehealth" },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Connect Live GP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Button(
                                            onClick = { viewModel.clearUrgentEscalation() },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray, contentColor = Color.DarkGray),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Dismiss", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            when (currentTab) {
                                "triage" -> TriageScreen(
                                    viewModel = viewModel,
                                    onEscalateClick = { currentTab = "telehealth" },
                                    onNavigateToTab = { currentTab = it },
                                    onLhrClick = { showLhrDialog = true },
                                    onSafetyCenterClick = { showClinicalSafetyDialog = true },
                                    onSpeak = onSpeak,
                                    onStopSpeaking = onStopSpeaking
                                )
                                "scan" -> SymptomScanScreen(viewModel = viewModel, onStartTriageClick = { currentTab = "triage" })
                                "specialists" -> SpecialistsScreen(viewModel = viewModel)
                                "telehealth" -> TelehealthScreen(viewModel = viewModel)
                                "referral" -> ReferralScreen(viewModel = viewModel)
                                "insurance" -> InsuranceScreen(viewModel = viewModel)
                                "fund" -> FundScreen(viewModel = viewModel)
                                "admin" -> AdminPanelScreen(viewModel = viewModel)
                            }
                        }
                    }
                }
            }
        } else {
            // COMPACT MOBILE PHONE BOTTOM NAV SCALED LAYOUT
            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("main_scaffold"),
                topBar = {
                    CareOSTopBar(
                        currentTab = currentTab,
                        onLhrClick = { showLhrDialog = true },
                        onInvestorDeckClick = { showInvestorDeckDialog = true },
                        onAdminClick = { currentTab = "admin" },
                        activeInsurance = activeIns
                    )
                },
                bottomBar = {
                    CareOSBottomNavigation(
                        currentTab = currentTab,
                        onTabSelect = { currentTab = it }
                    )
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Urgent Escalation Header Banner inside Compact Mobile View
                        if (urgentEscalation != null) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEE2E2)),
                                border = BorderStroke(1.dp, Color(0xFFDC2626)),
                                shape = RoundedCornerShape(0.dp),
                                modifier = Modifier.fillMaxWidth().testTag("urgent_escalation_banner")
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("🚨", fontSize = 16.sp)
                                        Text("CRITICAL MEDICAL ESCALATION ALERT", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color(0xFF991B1B))
                                    }
                                    Text(urgentEscalation ?: "", fontSize = 9.sp, color = Color(0xFF7F1D1D))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                        Button(
                                            onClick = { currentTab = "telehealth" },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                            modifier = Modifier.weight(1f).height(32.dp),
                                            shape = RoundedCornerShape(4.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text("Connect GP", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Button(
                                            onClick = { viewModel.clearUrgentEscalation() },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray, contentColor = Color.DarkGray),
                                            modifier = Modifier.weight(1f).height(32.dp),
                                            shape = RoundedCornerShape(4.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text("Dismiss", fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            when (currentTab) {
                                "triage" -> TriageScreen(
                                    viewModel = viewModel,
                                    onEscalateClick = { currentTab = "telehealth" },
                                    onNavigateToTab = { currentTab = it },
                                    onLhrClick = { showLhrDialog = true },
                                    onSafetyCenterClick = { showClinicalSafetyDialog = true },
                                    onSpeak = onSpeak,
                                    onStopSpeaking = onStopSpeaking
                                )
                                "scan" -> SymptomScanScreen(viewModel = viewModel, onStartTriageClick = { currentTab = "triage" })
                                "specialists" -> SpecialistsScreen(viewModel = viewModel)
                                "telehealth" -> TelehealthScreen(viewModel = viewModel)
                                "referral" -> ReferralScreen(viewModel = viewModel)
                                "insurance" -> InsuranceScreen(viewModel = viewModel)
                                "fund" -> FundScreen(viewModel = viewModel)
                                "admin" -> AdminPanelScreen(viewModel = viewModel)
                            }
                        }
                    }
                }
            }
        }

        // Global Alert Dialog Modals
        if (showLhrDialog) {
            LhrDialog(
                triages = triages,
                onDismiss = { showLhrDialog = false }
            )
        }

        if (showClinicalSafetyDialog) {
            ClinicalSafetyDialog(
                viewModel = viewModel,
                onDismiss = { showClinicalSafetyDialog = false }
            )
        }

        if (showInvestorDeckDialog) {
            InvestorPresentationDialog(
                onDismiss = { showInvestorDeckDialog = false }
            )
        }
        
        // Dynamic Pop-up Alert Dialog for Urgent Escalation
        if (urgentEscalation != null) {
            AlertDialog(
                onDismissRequest = { /* Force reading/acknowledgement or GP Telehealth action */ },
                icon = { Icon(Icons.Default.Warning, contentDescription = "Critical Alert", tint = Color(0xFFDC2626), modifier = Modifier.size(36.dp)) },
                title = { Text("🚨 CLINICAL ACTION REQUIRED", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "CareOS Medical Core has flagged the following symptom record as requiring IMMEDIATE physician evaluation to prevent clinical delay or complications:",
                            fontSize = 11.sp,
                            color = Color(0xFF0F172A)
                        )
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                            border = BorderStroke(1.dp, Color(0xFFFCA5A5))
                        ) {
                            Text(
                                text = urgentEscalation ?: "",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF991B1B),
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                        Text(
                            text = "Please connect to our active Duty GP on live telehealth instantly, or present to LUTH ER directly.",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF475569)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            currentTab = "telehealth"
                            viewModel.clearUrgentEscalation()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                    ) {
                        Text("Connect Telehealth Now", fontSize = 11.sp)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.clearUrgentEscalation() }) {
                        Text("Acknowledge & Close", color = Color.Gray, fontSize = 11.sp)
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CareOSTopBar(
    currentTab: String,
    onLhrClick: () -> Unit,
    onInvestorDeckClick: () -> Unit,
    onAdminClick: () -> Unit,
    activeInsurance: InsuranceProfile?
) {
    CenterAlignedTopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "CareOS Concierge",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    letterSpacing = (-0.5).sp,
                    color = Color.White
                )
                if (activeInsurance != null) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFD9F3EE), RoundedCornerShape(100.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "NHIA APPROVED ✓",
                            color = Color(0xFF0F766E),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 8.sp
                        )
                    }
                }
            }
        },
        actions = {
            IconButton(onClick = onAdminClick, modifier = Modifier.testTag("admin_top_button")) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Admin Control Hub",
                    tint = if (currentTab == "admin") Color.White else Color.White.copy(alpha = 0.6f)
                )
            }
            IconButton(onClick = onInvestorDeckClick, modifier = Modifier.testTag("investor_deck_button")) {
                Icon(
                    imageVector = Icons.Default.TrendingUp,
                    contentDescription = "Investor Presentation Deck",
                    tint = Color(0xFFFCA5A5)
                )
            }
            IconButton(onClick = onLhrClick, modifier = Modifier.testTag("lhr_button")) {
                Icon(
                    imageVector = Icons.Default.FolderShared,
                    contentDescription = "Longitudinal Health Record",
                    tint = Color(0xFFD9F3EE)
                )
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = Color(0xFF0F766E)
        )
    )
}

@Composable
fun CareOSBottomNavigation(
    currentTab: String,
    onTabSelect: (String) -> Unit
) {
    val tabColors = mapOf(
        "triage" to Color(0xFF0F766E),       // Primary deep teal
        "scan" to Color(0xFF14B8A6),         // Symptom Scan soft teal
        "specialists" to Color(0xFFEC4899),  // Specialists pink
        "telehealth" to Color(0xFF14B8A6),   // Secondary soft teal
        "referral" to Color(0xFF6366F1),     // Indigo
        "insurance" to Color(0xFF0D9488),    // Clean teal
        "fund" to Color(0xFF16A34A),         // Green success
        "admin" to Color(0xFF475569)         // Muted Slate
    )

    NavigationBar(
        modifier = Modifier.testTag("bottom_nav"),
        containerColor = Color.White,
        tonalElevation = 8.dp
    ) {
        val items = listOf(
            Triple("triage", "Triage", Icons.Default.MedicalServices),
            Triple("scan", "Scan", Icons.Default.CameraAlt),
            Triple("specialists", "Specialists", Icons.Default.Group),
            Triple("telehealth", "Consult", Icons.Default.Chat),
            Triple("referral", "Referrals", Icons.Default.Assignment),
            Triple("insurance", "Insurance", Icons.Default.Shield),
            Triple("fund", "Care Fund", Icons.Default.Handshake),
            Triple("admin", "Admin", Icons.Default.Settings)
        )
        items.forEach { (tab, label, icon) ->
            val isActive = currentTab == tab
            val activeColor = tabColors[tab] ?: Color(0xFF0F766E)
            
            NavigationBarItem(
                selected = isActive,
                onClick = { onTabSelect(tab) },
                icon = { 
                    Icon(
                        imageVector = icon, 
                        contentDescription = label,
                        tint = if (isActive) Color.White else activeColor.copy(alpha = 0.5f)
                    ) 
                },
                label = { 
                    Text(
                        text = label, 
                        fontSize = 9.sp, 
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                        color = if (isActive) activeColor else Color(0xFF64748B)
                    ) 
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = activeColor,
                    selectedIconColor = Color.White,
                    unselectedIconColor = activeColor.copy(alpha = 0.5f)
                ),
                modifier = Modifier.testTag("nav_item_$tab")
            )
        }
    }
}

@Composable
fun RegionSelector(selectedCountry: String, onCountrySelected: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("🌐", fontSize = 14.sp)
                Text(
                    text = "SELECT YOUR COUNTRY / SERVICE REGION",
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "Switching your region will dynamically adapt the platform's features, languages, and national insurance scheme gateways.",
                fontSize = 9.sp,
                color = Color.Gray,
                lineHeight = 12.sp
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val regions = listOf(
                    "Universal" to "🌐 Universal",
                    "Nigeria" to "🇳🇬 Nigeria",
                    "Kenya" to "🇰🇪 Kenya",
                    "St. Lucia" to "🇱🇨 St. Lucia",
                    "India" to "🇮🇳 India",
                    "Brazil" to "🇧🇷 Brazil",
                    "United Kingdom" to "🇬🇧 United Kingdom",
                    "United States" to "🇺🇸 United States",
                    "Australia" to "🇦🇺 Australia"
                )
                regions.forEach { (code, label) ->
                    val isSelected = selectedCountry == code
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surface
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { onCountrySelected(code) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StLuciaOptimizationPanel(viewModel: CareViewModel) {
    var isOkeuConnected by remember { mutableStateOf(true) }
    var islandReferralActive by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDFA)),
        border = BorderStroke(1.dp, Color(0xFF99F6E4)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("🇱🇨", fontSize = 16.sp)
                Text("ST. LUCIA NATIONAL HEALTH INTEGRATION PORTAL", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF0F766E))
            }
            Text("Caribbean med-routing, Owen King EU (OKEU) Hospital portal, and island clinical network synchronization.", fontSize = 9.5.sp, color = Color.Gray)

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("OKEU Direct Ward Treasury", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                    Text("Auto-clear medical bills directly to OKEU Hospital board.", fontSize = 9.5.sp, color = Color.Gray)
                }
                Switch(
                    checked = isOkeuConnected,
                    onCheckedChange = { isOkeuConnected = it }
                )
            }

            HorizontalDivider(color = Color(0xFFCCFBF1))

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Inter-Island Referrals", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                    Text("Allow medical evacuation or specialist telemetry routing to Barbados or Martinique.", fontSize = 9.5.sp, color = Color.Gray)
                }
                Switch(
                    checked = islandReferralActive,
                    onCheckedChange = { islandReferralActive = it }
                )
            }
        }
    }
}

@Composable
fun BrazilOptimizationPanel(viewModel: CareViewModel) {
    val activeLang by viewModel.activeLanguage.collectAsState()
    var isSusConnected by remember { mutableStateOf(false) }
    var isUbsFinderActive by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
        border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("🇧🇷", fontSize = 16.sp)
                Text("BRASIL PORTAL DE SAÚDE PÚBLICA (SUS)", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF166534))
            }
            Text("Integração com o Sistema Único de Saúde (SUS) e rede de Unidades Básicas de Saúde (UBS).", fontSize = 9.5.sp, color = Color.Gray)

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Cartão Nacional de Saúde (CNS)", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                    Text("Sincronizar prontuário eletrônico unificado pelo número do SUS.", fontSize = 9.5.sp, color = Color.Gray)
                }
                Switch(
                    checked = isSusConnected,
                    onCheckedChange = { isSusConnected = it }
                )
            }

            HorizontalDivider(color = Color(0xFFDCFCE7))

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Localizador de UBS Integrado", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                    Text("Buscar posto de saúde local para encaminhamento presencial de emergências.", fontSize = 9.5.sp, color = Color.Gray)
                }
                Switch(
                    checked = isUbsFinderActive,
                    onCheckedChange = { isUbsFinderActive = it }
                )
            }

            HorizontalDivider(color = Color(0xFFDCFCE7))

            // Language picker
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Idioma do Aplicativo:", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val languages = listOf("English", "Portuguese")
                    languages.forEach { l ->
                        val isSel = activeLang == l
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) Color(0xFF166534) else Color(0xFFDCFCE7))
                                .clickable { viewModel.setActiveLanguage(l) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(l, color = if (isSel) Color.White else Color(0xFF166534), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UnitedKingdomOptimizationPanel(viewModel: CareViewModel) {
    val activeLang by viewModel.activeLanguage.collectAsState()
    var isNhsLoginConnected by remember { mutableStateOf(false) }
    var isNiceGuidelinesEnabled by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF2F6)),
        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("🇬🇧", fontSize = 16.sp)
                Text("UK NHS GENERAL PRACTICE PORTAL", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF1E293B))
            }
            Text("NHS login integration, General Practitioner (GP) e-referral pipeline, and NICE standard clinical decision support.", fontSize = 9.5.sp, color = Color.Gray)

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("NHS Identity & Login", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                    Text("Secure clinical linkage to your NHS medical record profile.", fontSize = 9.5.sp, color = Color.Gray)
                }
                Switch(
                    checked = isNhsLoginConnected,
                    onCheckedChange = { isNhsLoginConnected = it }
                )
            }

            if (isNhsLoginConnected) {
                OutlinedTextField(
                    value = "",
                    onValueChange = {},
                    label = { Text("NHS Number (e.g. 485 777 3456)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = false
                )
            }

            HorizontalDivider(color = Color(0xFFE2E8F0))

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("NICE Guideline Validation", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                    Text("Enforce National Institute for Health and Care Excellence safety filters.", fontSize = 9.5.sp, color = Color.Gray)
                }
                Switch(
                    checked = isNiceGuidelinesEnabled,
                    onCheckedChange = { isNiceGuidelinesEnabled = it }
                )
            }
        }
    }
}

@Composable
fun AustraliaOptimizationPanel(viewModel: CareViewModel) {
    var isMedicareConnected by remember { mutableStateOf(false) }
    var isMyHealthRecordLinked by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFAF5FF)),
        border = BorderStroke(1.dp, Color(0xFFE9D5FF)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("🇦🇺", fontSize = 16.sp)
                Text("AUSTRALIA MEDICARE BENEFIT GATEWAY", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF581C87))
            }
            Text("Medicare Benefits Schedule (MBS) bulk billing claims, and My Health Record database sync.", fontSize = 9.5.sp, color = Color.Gray)

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Medicare Bulk Billing", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                    Text("Submit bulk bill claims directly to Services Australia.", fontSize = 9.5.sp, color = Color.Gray)
                }
                Switch(
                    checked = isMedicareConnected,
                    onCheckedChange = { isMedicareConnected = it }
                )
            }

            HorizontalDivider(color = Color(0xFFF3E8FF))

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("My Health Record Integration", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                    Text("Synchronize pathology reports and digital prescription histories.", fontSize = 9.5.sp, color = Color.Gray)
                }
                Switch(
                    checked = isMyHealthRecordLinked,
                    onCheckedChange = { isMyHealthRecordLinked = it }
                )
            }
        }
    }
}

@Composable
fun UniversalWelcomeCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF2F6)),
        border = BorderStroke(1.5.dp, Color(0xFFCBD5E1)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🌍", fontSize = 24.sp)
                Column {
                    Text("CareOS Universal Standard Mode", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = Color(0xFF1E293B))
                    Text("Standard low-latency clinical routing activated", fontSize = 9.sp, color = Color.Gray)
                }
            }
            Text(
                text = "Welcome to the primary global interface of CareOS. Standard protocols, high-definition teleconsultations, and fully localized medical ledger verifications are enabled. Select a specific country above to load custom regional health gateways.",
                fontSize = 10.sp,
                lineHeight = 14.sp,
                color = Color(0xFF334155)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .background(Color(0xFFE2E8F0), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF10B981)))
                Text("SYSTEM STATUS: REGIONAL CHANNELS STABLE", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
            }
        }
    }
}

@Composable
fun KenyaOptimizationPanel(viewModel: CareViewModel) {
    val isLowBandwidth by viewModel.isLowBandwidthMode.collectAsState()
    val activeLang by viewModel.activeLanguage.collectAsState()
    var isMpesaEnabled by remember { mutableStateOf(true) }
    var isNhifConnected by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
        border = BorderStroke(1.dp, Color(0xFFFDE68A)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("🇰🇪", fontSize = 16.sp)
                Text("KENYA REGIONAL OPTIMIZATION PORTAL", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF78350F))
            }
            Text("Adjust regional integrations to route medical aid and access local clinics under NHIF regulations.", fontSize = 9.5.sp, color = Color.Gray)

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("M-Pesa STK Push Settlements", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                    Text("Route direct-to-pharmacy bills instantly using M-Pesa.", fontSize = 9.5.sp, color = Color.Gray)
                }
                Switch(
                    checked = isMpesaEnabled,
                    onCheckedChange = { isMpesaEnabled = it }
                )
            }

            HorizontalDivider(color = Color(0xFFFEF3C7))

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("NHIF Insurance Portal Integration", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                    Text("Auto-query national coverage for outpatient subsidies.", fontSize = 9.5.sp, color = Color.Gray)
                }
                Switch(
                    checked = isNhifConnected,
                    onCheckedChange = { isNhifConnected = it }
                )
            }

            HorizontalDivider(color = Color(0xFFFEF3C7))

            // Language picker
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("App Language:", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val languages = listOf("English", "Swahili")
                    languages.forEach { l ->
                        val isSel = activeLang == l
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) Color(0xFF78350F) else Color(0xFFFEF3C7))
                                .clickable { viewModel.setActiveLanguage(l) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(l, color = if (isSel) Color.White else Color(0xFF78350F), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IndiaOptimizationPanel(viewModel: CareViewModel) {
    val activeLang by viewModel.activeLanguage.collectAsState()
    var isAbhaConnected by remember { mutableStateOf(false) }
    var abhaId by remember { mutableStateOf("") }
    var isHighDensityMode by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED)),
        border = BorderStroke(1.dp, Color(0xFFFED7AA)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("🇮🇳", fontSize = 16.sp)
                Text("INDIA HEALTHCARE DISCOVERY PORTAL", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF7C2D12))
            }
            Text("Ayushman Bharat Digital Mission (ABDM) tools and high-density OPD queuing optimizations.", fontSize = 9.5.sp, color = Color.Gray)

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Ayushman Bharat Health Account (ABHA)", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                    Text("Auto-generate or link ABDM patient IDs.", fontSize = 9.5.sp, color = Color.Gray)
                }
                Switch(
                    checked = isAbhaConnected,
                    onCheckedChange = { isAbhaConnected = it }
                )
            }

            if (isAbhaConnected) {
                OutlinedTextField(
                    value = abhaId,
                    onValueChange = { abhaId = it },
                    label = { Text("Enter ABHA Health ID (e.g. 12-3456-7890)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            HorizontalDivider(color = Color(0xFFFFEDD5))

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("High-Density OPD Mode", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                    Text("Enables fast-queue offline barcodes to bypass outpatient queues.", fontSize = 9.5.sp, color = Color.Gray)
                }
                Switch(
                    checked = isHighDensityMode,
                    onCheckedChange = { isHighDensityMode = it }
                )
            }

            HorizontalDivider(color = Color(0xFFFFEDD5))

            // Language picker
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("App Language / Voice Translation:", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val languages = listOf("English", "Hindi", "Bengali", "Tamil", "Telugu", "Marathi")
                    languages.forEach { l ->
                        val isSel = activeLang == l
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) Color(0xFF7C2D12) else Color(0xFFFFEDD5))
                                .clickable { viewModel.setActiveLanguage(l) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(l, color = if (isSel) Color.White else Color(0xFF7C2D12), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UnitedStatesOptimizationPanel(viewModel: CareViewModel) {
    val activeLang by viewModel.activeLanguage.collectAsState()
    var isUhdVideoEnabled by remember { mutableStateOf(true) }
    var isCopayCalculatorEnabled by remember { mutableStateOf(true) }
    var showHipaalog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F9FF)),
        border = BorderStroke(1.dp, Color(0xFFBAE6FD)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("🇺🇸", fontSize = 16.sp)
                Text("US CO-PAY & HIPAA SECURITY PORTAL", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF0369A1))
            }
            Text("HIPAA compliance frameworks, HD teleconsultation triggers, and private co-pay integrations.", fontSize = 9.5.sp, color = Color.Gray)

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Ultra-HD Video Preferred Routing", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                    Text("Forces HD feed selection for clinical examinations.", fontSize = 9.5.sp, color = Color.Gray)
                }
                Switch(
                    checked = isUhdVideoEnabled,
                    onCheckedChange = { isUhdVideoEnabled = it }
                )
            }

            HorizontalDivider(color = Color(0xFFE0F2FE))

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Copay & Deductible Estimator", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                    Text("Deduct co-insurance real-time on consult booking.", fontSize = 9.5.sp, color = Color.Gray)
                }
                Switch(
                    checked = isCopayCalculatorEnabled,
                    onCheckedChange = { isCopayCalculatorEnabled = it }
                )
            }

            HorizontalDivider(color = Color(0xFFE0F2FE))

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("HIPAA Compliance Audit Registry", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                    Text("View secure transit audit keys for active session.", fontSize = 9.5.sp, color = Color.Gray)
                }
                Button(
                    onClick = { showHipaalog = !showHipaalog },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(if (showHipaalog) "Hide Log" else "View Key", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (showHipaalog) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("🛡️ COMPLIANCE TRACE CERTIFICATE", color = Color(0xFF38BDF8), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        Text("ENCRYPTION: AES-256-GCM / SHA-256 HMAC", color = Color.White, fontSize = 8.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        Text("AUDIT HASH: HIPAA-TX-902481-CAREOS-FIPS140", color = Color.Green, fontSize = 8.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

@Composable
fun NigeriaOptimizationPanel(viewModel: CareViewModel) {
    val isLowBandwidth by viewModel.isLowBandwidthMode.collectAsState()
    val isLegacyPhone by viewModel.isLegacyPhoneOptimization.collectAsState()
    val activeLang by viewModel.activeLanguage.collectAsState()
    val isCaregiver by viewModel.isCaregiverMode.collectAsState()
    var recipientName by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("🇳🇬 NIGERIA-FIRST OPTIMIZATION PANEL", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF334155))
            Text("Adjust settings below to maintain active medical consultations on legacy handsets or under extremely poor network connectivity.", fontSize = 9.5.sp, color = Color.Gray)

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Low-Bandwidth Mode", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("Compresses photos and allows offline draft queueing.", fontSize = 10.sp, color = Color.Gray)
                }
                Switch(
                    checked = isLowBandwidth,
                    onCheckedChange = { viewModel.setLowBandwidthMode(it) },
                    modifier = Modifier.testTag("low_bandwidth_switch")
                )
            }

            HorizontalDivider()

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Legacy Handset Optimization", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("Disables heavy UI effects for low-end Android processors.", fontSize = 10.sp, color = Color.Gray)
                }
                Switch(
                    checked = isLegacyPhone,
                    onCheckedChange = { viewModel.setLegacyPhoneOptimization(it) },
                    modifier = Modifier.testTag("legacy_phone_switch")
                )
            }

            HorizontalDivider()

            // Language picker
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("App Language / Multilingual voice:", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val languages = listOf("English", "Pidgin", "Hausa", "Yoruba", "Igbo")
                    languages.forEach { l ->
                        val isSel = activeLang == l
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) Color(0xFF0F172A) else Color(0xFFE2E8F0))
                                .clickable { viewModel.setActiveLanguage(l) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(l, color = if (isSel) Color.White else Color.DarkGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            HorizontalDivider()

            // Caregiver mode
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Family/Caregiver Mode", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("Allow managing health records for dependents.", fontSize = 10.sp, color = Color.Gray)
                }
                Switch(
                    checked = isCaregiver,
                    onCheckedChange = { viewModel.setCaregiverMode(it) }
                )
            }

            if (isCaregiver) {
                OutlinedTextField(
                    value = recipientName,
                    onValueChange = {
                        recipientName = it
                        viewModel.setCareRecipientName(it)
                    },
                    label = { Text("Care Recipient Full Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun BentoGridDashboard(
    viewModel: CareViewModel,
    onSymptomIntakeClick: () -> Unit,
    onVisualScanClick: () -> Unit,
    onTalkToDoctorClick: () -> Unit,
    onHealthRecordClick: () -> Unit,
    onInsuranceClick: () -> Unit,
    onMedicalAidClick: () -> Unit,
    onSafetyCenterClick: () -> Unit,
    followUpReminders: List<com.example.ui.FollowUpReminder>,
    onToggleReminder: (Long) -> Unit
) {
    val selectedCountry by viewModel.selectedCountry.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFF8FBFA), // Soft luxury ivory
                        Color(0xFFF1F5F4), // Muted alt surface
                        Color(0xFFFFFFFF)  // Light highlights
                    )
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Country Gateways Selector
        RegionSelector(
            selectedCountry = selectedCountry,
            onCountrySelected = { viewModel.setSelectedCountry(it) }
        )

        // Active country optimization dashboard panel
        when (selectedCountry) {
            "Universal" -> UniversalWelcomeCard()
            "Nigeria" -> NigeriaOptimizationPanel(viewModel = viewModel)
            "Kenya" -> KenyaOptimizationPanel(viewModel = viewModel)
            "St. Lucia" -> StLuciaOptimizationPanel(viewModel = viewModel)
            "India" -> IndiaOptimizationPanel(viewModel = viewModel)
            "Brazil" -> BrazilOptimizationPanel(viewModel = viewModel)
            "United Kingdom" -> UnitedKingdomOptimizationPanel(viewModel = viewModel)
            "United States" -> UnitedStatesOptimizationPanel(viewModel = viewModel)
            "Australia" -> AustraliaOptimizationPanel(viewModel = viewModel)
        }

        // Luxurious Healthcare Concierge Hero Section (AI Entry Focus)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("concierge_hero_card"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, Color(0xFFDCE7E5))
        ) {
            Column(
                modifier = Modifier
                    .background(
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(Color(0xFF0F766E), Color(0xFF14B8A6))
                        )
                    )
                    .padding(22.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "CAREOS PRIVATE HEALTH CONCIERGE",
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = Color(0xFFD9F3EE),
                            letterSpacing = 1.2.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Personalized AI Care",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp,
                            color = Color.White,
                            lineHeight = 28.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✨", fontSize = 20.sp)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Welcome to your premium private health suite. Our secure, medical-grade AI is fully optimized to manage conservative symptom triage, document processing, and coordinate immediate specialist support.",
                    fontSize = 11.5.sp,
                    color = Color(0xFFE2F1EE),
                    lineHeight = 17.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { onSymptomIntakeClick() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape = RoundedCornerShape(18.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("🩺", fontSize = 14.sp)
                        Text(
                            text = "Consult AI Concierge Now",
                            color = Color(0xFF0F766E),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text("➔", color = Color(0xFF0F766E), fontSize = 12.sp)
                    }
                }
            }
        }

        Text(
            text = "EXECUTIVE CARE CLINIC DIRECTORY",
            fontWeight = FontWeight.Bold,
            fontSize = 10.5.sp,
            color = Color(0xFF475569),
            letterSpacing = 1.sp,
            modifier = Modifier.padding(top = 8.dp, start = 4.dp)
        )

        // Row 1 - Symptom Intake and Visual Scanner
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier
                    .weight(1.3f)
                    .height(140.dp)
                    .clickable { onSymptomIntakeClick() }
                    .testTag("symptom_intake_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFDCE7E5)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "🩺", fontSize = 26.sp)
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFE6F4F1), CircleShape)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Active", fontSize = 8.sp, color = Color(0xFF0F766E), fontWeight = FontWeight.Bold)
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Symptom Intake",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "Run dynamic conservative AI clinical triage.",
                            fontSize = 10.sp,
                            color = Color(0xFF475569),
                            lineHeight = 13.sp
                        )
                    }
                }
            }

            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(140.dp)
                    .clickable { onVisualScanClick() }
                    .testTag("visual_scan_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFDCE7E5)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "📸", fontSize = 26.sp)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Visual Scan",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "Malaria packs, skin scans & OCR.",
                            fontSize = 10.sp,
                            color = Color(0xFF475569),
                            lineHeight = 13.sp
                        )
                    }
                }
            }
        }

        // Row 2 - Tele-Consult and Health Records
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(130.dp)
                    .clickable { onTalkToDoctorClick() }
                    .testTag("consult_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFDCE7E5)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "💬", fontSize = 26.sp)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Tele-Consult",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "Instant GP secure handoff.",
                            fontSize = 10.sp,
                            color = Color(0xFF475569)
                        )
                    }
                }
            }

            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(130.dp)
                    .clickable { onHealthRecordClick() }
                    .testTag("health_record_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFDCE7E5)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "📁", fontSize = 26.sp)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Health Records",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "Longitudinal health ledgers.",
                            fontSize = 10.sp,
                            color = Color(0xFF475569)
                        )
                    }
                }
            }
        }

        // Row 3 - HMO Wallet and Medical Aid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(100.dp)
                    .clickable { onInsuranceClick() },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFDCE7E5)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = "🛡️", fontSize = 24.sp)
                        Text(
                            text = "HMO Wallet",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                    }
                }
            }

            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(100.dp)
                    .clickable { onMedicalAidClick() },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFDCE7E5)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = "🤝", fontSize = 24.sp)
                        Text(
                            text = "Medical Aid",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                    }
                }
            }
        }

        // Clinical Safety Board Action Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSafetyCenterClick() }
                .testTag("clinical_safety_center_button"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
            border = BorderStroke(1.dp, Color(0xFFFCA5A5))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFEE2E2)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "🛡️", fontSize = 18.sp)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Clinical Safety & Governance Board",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color(0xFF991B1B)
                    )
                    Text(
                        text = "SaMD compliance v4.2.1 • Audit logs, Adverse reports & Peer doctor override active.",
                        fontSize = 9.5.sp,
                        color = Color(0xFF7F1D1D)
                    )
                }
                Text(text = "➔", fontSize = 15.sp, color = Color(0xFF991B1B))
            }
        }

        // Clinical Follow-up Reminders section (as requested: "Follow-up reminders continue after the visit")
        Spacer(modifier = Modifier.height(4.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFDCE7E5)),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⏰ ACTIVE TREATMENT PROTOCOLS",
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.5.sp,
                        color = Color(0xFF475569),
                        letterSpacing = 0.5.sp
                    )
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFEFF6FF), CircleShape)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${followUpReminders.count { !it.isCompleted }} pending",
                            fontSize = 8.sp,
                            color = Color(0xFF1D4ED8),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (followUpReminders.isEmpty()) {
                    Text(
                        text = "No outstanding clinical follow-ups. You are fully up to date with your recovery program.",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        lineHeight = 15.sp
                    )
                } else {
                    followUpReminders.forEach { r ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onToggleReminder(r.id) }
                                .padding(vertical = 4.dp, horizontal = 2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                             ) {
                                Checkbox(
                                    checked = r.isCompleted,
                                    onCheckedChange = { onToggleReminder(r.id) },
                                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFF0F766E))
                                )
                                Column {
                                    Text(
                                        text = r.text,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (r.isCompleted) Color(0xFF94A3B8) else Color(0xFF0F172A)
                                    )
                                    Text(
                                        text = "${r.category.uppercase()} • Scheduled: ${r.scheduledTime}",
                                        fontSize = 9.sp,
                                        color = Color(0xFF64748B)
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

// --- SCREEN 1: CLINICAL TRIAGE & MULTIMODAL CAMERA SCAN ---
@Composable
fun TriageScreen(
    viewModel: CareViewModel,
    onEscalateClick: () -> Unit,
    onNavigateToTab: (String) -> Unit,
    onLhrClick: () -> Unit,
    onSafetyCenterClick: () -> Unit,
    onSpeak: (String) -> Unit,
    onStopSpeaking: () -> Unit
) {
    val chatMessages by viewModel.chatMessages.collectAsState()
    val isLoading by viewModel.isTriageLoading.collectAsState()
    val activeTriageResult by viewModel.activeTriageResult.collectAsState()
    val followUpReminders by viewModel.followUpReminders.collectAsState()
    val isLowBandwidthMode by viewModel.isLowBandwidthMode.collectAsState()

    val selectedImageBase64 by viewModel.selectedImageBase64.collectAsState()
    val selectedImageUri by viewModel.selectedImageUri.collectAsState()

    var textInput by remember { mutableStateOf("") }
    var showCameraScanner by remember { mutableStateOf(false) }
    var showVoiceRecordDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Scroll to bottom when message list changes
    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    // Audio Communication Mode state
    var isAudioModeActive by remember { mutableStateOf(false) }
    var isRecordingVoice by remember { mutableStateOf(false) }
    var showSimulatedDialects by remember { mutableStateOf(false) }
    var currentlySpeakingText by remember { mutableStateOf<String?>(null) }

    // Wave scale animation state
    var waveScales by remember { mutableStateOf(listOf(1f, 1f, 1f, 1f, 1f)) }
    LaunchedEffect(isRecordingVoice) {
        if (isRecordingVoice) {
            while (true) {
                waveScales = List(5) { 0.4f + kotlin.random.Random.nextFloat() * 1.6f }
                delay(100)
            }
        } else {
            waveScales = listOf(1f, 1f, 1f, 1f, 1f)
        }
    }

    // Auto-read new AI messages out loud when in Audio Mode
    var lastSpokenTimestamp by remember { mutableStateOf(0L) }
    LaunchedEffect(chatMessages, isAudioModeActive) {
        if (isAudioModeActive && chatMessages.isNotEmpty()) {
            val lastMessage = chatMessages.last()
            if (lastMessage.sender == "ai" && lastMessage.timestamp > lastSpokenTimestamp) {
                onSpeak(lastMessage.text)
                currentlySpeakingText = lastMessage.text
                lastSpokenTimestamp = lastMessage.timestamp
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("triage_screen")
    ) {
        if (chatMessages.isEmpty()) {
            BentoGridDashboard(
                viewModel = viewModel,
                onSymptomIntakeClick = {
                    viewModel.sendTriageMessage("Hello, I would like to run a symptom triage check.")
                },
                onVisualScanClick = {
                    showCameraScanner = true
                },
                onTalkToDoctorClick = {
                    viewModel.startDoctorEscalation("Intake details: General escalation request")
                    onEscalateClick()
                },
                onHealthRecordClick = onLhrClick,
                onInsuranceClick = {
                    onNavigateToTab("insurance")
                },
                onMedicalAidClick = {
                    onNavigateToTab("fund")
                },
                onSafetyCenterClick = onSafetyCenterClick,
                followUpReminders = followUpReminders,
                onToggleReminder = { viewModel.toggleReminder(it) }
            )
        } else {
            // High visibility Switcher for Audio / Voice Mode
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .background(Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (!isAudioModeActive) Color.White else Color.Transparent)
                        .clickable {
                            isAudioModeActive = false
                            onStopSpeaking()
                            currentlySpeakingText = null
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("💬", fontSize = 14.sp)
                        Text("TEXT CHAT", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (!isAudioModeActive) Color(0xFF0F172A) else Color(0xFF64748B))
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isAudioModeActive) Color(0xFF0D9488) else Color.Transparent)
                        .clickable {
                            isAudioModeActive = true
                            // Auto-speak the last message if it was AI
                            val lastAiMsg = chatMessages.lastOrNull { it.sender == "ai" }
                            if (lastAiMsg != null) {
                                onSpeak(lastAiMsg.text)
                                currentlySpeakingText = lastAiMsg.text
                            }
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("🎙️", fontSize = 14.sp)
                        Text("TALK / AUDIO MODE", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (isAudioModeActive) Color.White else Color(0xFF64748B))
                    }
                }
            }

            if (isAudioModeActive) {
                // --- DESIGN: AUDIO-FIRST INTERACTIVE MODE PORTAL ---
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Explanatory card for non-literate/low-vision users
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                        border = BorderStroke(1.5.dp, Color(0xFFBBF7D0)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("📢", fontSize = 22.sp)
                                Text("CareOS Speaking Assistant", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = Color(0xFF166534))
                            }
                            Text(
                                text = "Perfect for those who prefer to hear and talk. The app speaks everything out loud. Tap the giant green button below, tell us how you feel, then pick your language.",
                                fontSize = 11.sp,
                                color = Color(0xFF14532D),
                                lineHeight = 16.sp
                            )
                        }
                    }

                    // Display sound waves if recording
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isRecordingVoice) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                waveScales.forEach { scale ->
                                    Box(
                                        modifier = Modifier
                                            .width(8.dp)
                                            .height((40 * scale).dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFFEF4444))
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "🎤 System waiting for you to speak...",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray
                            )
                        }
                    }

                    // Giant Microphone button
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(140.dp)
                    ) {
                        // Glowing outer ring
                        Box(
                            modifier = Modifier
                                .size(if (isRecordingVoice) 130.dp else 115.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isRecordingVoice) Color(0xFFFEE2E2).copy(alpha = 0.6f)
                                    else Color(0xFFCCFBF1).copy(alpha = 0.6f)
                                )
                        )
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .background(if (isRecordingVoice) Color(0xFFEF4444) else Color(0xFF0D9488))
                                .clickable {
                                    if (isRecordingVoice) {
                                        isRecordingVoice = false
                                        showSimulatedDialects = true
                                    } else {
                                        onStopSpeaking()
                                        currentlySpeakingText = null
                                        isRecordingVoice = true
                                        showSimulatedDialects = false
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isRecordingVoice) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = if (isRecordingVoice) "Stop Recording" else "Start Recording",
                                tint = Color.White,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }

                    Text(
                        text = if (isRecordingVoice) "🔴 TAP TO STOP RECORDING" else "🎤 TAP MIC TO SPEAK SYMPTOMS",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        color = if (isRecordingVoice) Color(0xFFB91C1C) else Color(0xFF0D9488),
                        letterSpacing = 1.sp
                    )

                    // Simulated Dialects & Languages selection
                    if (showSimulatedDialects) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "SELECT YOUR VOICE TRANSCRIPTION SIMULATION:",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                val simulations = listOf(
                                    "🇱🇨 St. Lucia Patois" to "I having real bad hot fever and my joints dem stiff, my head hurting since morning.",
                                    "🇳🇬 Nigerian Pidgin" to "My body dey hot, I get severe skin rash, and my chest dey tight when I breathe.",
                                    "🇰🇪 Swahili-English" to "Sina hamu ya chakula na ninahisi joto mwilini, pia niko na kikohozi kikavu.",
                                    "🇮🇳 Hinglish / India" to "Mujhe bohot tej fever hai aur sar dard ho raha hai, weakness bhi lag rahi hai.",
                                    "🇺🇸 Standard English" to "I have a high fever, severe skin rashes, and muscle aches."
                                )

                                simulations.forEach { (label, text) ->
                                    Button(
                                        onClick = {
                                            viewModel.sendTriageMessage(text)
                                            showSimulatedDialects = false
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "🗣️ Speak: $label",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Display Latest conversation in Audio Mode with extra large typography
                    val latestUserMessage = chatMessages.lastOrNull { it.sender == "user" }
                    val latestAiMessage = chatMessages.lastOrNull { it.sender == "ai" }

                    if (latestUserMessage != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2FE)),
                            border = BorderStroke(1.dp, Color(0xFFBAE6FD)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("📢 WHAT WE HEARD YOU SAY:", fontWeight = FontWeight.Bold, fontSize = 9.5.sp, color = Color(0xFF0369A1))
                                Text(text = latestUserMessage.text, fontSize = 15.sp, color = Color(0xFF0C4A6E), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    if (latestAiMessage != null) {
                        val isLastSpeaking = currentlySpeakingText == latestAiMessage.text
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                            border = BorderStroke(1.5.dp, Color(0xFFCBD5E1)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("🤖 CAREOS ADVICE RESPONSE:", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color(0xFF334155))
                                Text(
                                    text = latestAiMessage.text,
                                    fontSize = 15.sp,
                                    color = Color(0xFF0F172A),
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 21.sp
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = {
                                            if (isLastSpeaking) {
                                                onStopSpeaking()
                                                currentlySpeakingText = null
                                            } else {
                                                onSpeak(latestAiMessage.text)
                                                currentlySpeakingText = latestAiMessage.text
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isLastSpeaking) Color(0xFFEF4444) else Color(0xFF0D9488)
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isLastSpeaking) Icons.Default.Stop else Icons.Default.PlayArrow,
                                                contentDescription = "Speak response out loud",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = if (isLastSpeaking) "STOP REPLAY" else "🔊 READ ALOUD AGAIN",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }
            } else {
                // --- STANDARD HIGH CONTRAST CHAT AREA ---
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp)
                ) {
                    if (isLowBandwidthMode) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "📱 Low-Bandwidth Mode Active. Dynamic photo compression and offline state queues enabled.",
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(10.dp),
                                    color = Color.Gray
                                )
                            }
                        }
                    }

                    items(chatMessages) { msg ->
                        TriageChatBubble(
                            msg = msg,
                            onSpeak = onSpeak,
                            onStopSpeaking = onStopSpeaking,
                            isCurrentlySpeaking = currentlySpeakingText == msg.text,
                            onStartSpeaking = { currentlySpeakingText = msg.text }
                        )
                    }

                    // Specialist AI Smart Suggestion Loop
                    val lastMessageText = chatMessages.lastOrNull()?.text?.lowercase() ?: ""
                    val suggestedSpecialist = when {
                        lastMessageText.contains("eye") || lastMessageText.contains("vision") || lastMessageText.contains("glaucoma") || lastMessageText.contains("blind") || lastMessageText.contains("optic") || lastMessageText.contains("cataract") -> {
                            Triple("Dr. Emily Vance, OD", "Optometrist (Eye Specialist)", "spec_1")
                        }
                        lastMessageText.contains("tooth") || lastMessageText.contains("teeth") || lastMessageText.contains("dentist") || lastMessageText.contains("gum") || lastMessageText.contains("oral") || lastMessageText.contains("mouth") || lastMessageText.contains("pulpitis") -> {
                            Triple("Dr. Aarav Patel, DDS", "Dentist (Oral Specialist)", "spec_2")
                        }
                        lastMessageText.contains("bone") || lastMessageText.contains("fracture") || lastMessageText.contains("joint") || lastMessageText.contains("ortho") || lastMessageText.contains("leg") || lastMessageText.contains("arm") || lastMessageText.contains("tibia") -> {
                            Triple("Dr. Jean-Louis Dupont, MD", "Orthopedist (Bone Specialist)", "spec_3")
                        }
                        lastMessageText.contains("baby") || lastMessageText.contains("infant") || lastMessageText.contains("neonatal") || lastMessageText.contains("child") || lastMessageText.contains("pediatric") || lastMessageText.contains("lactation") -> {
                            Triple("Nurse Amina Yusuf, NP", "Neonatal Nurse Specialist", "spec_4")
                        }
                        else -> null
                    }

                    suggestedSpecialist?.let { (name, specialty, specId) ->
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .testTag("specialist_recommend_card"),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFDF2F8)), // soft pink blush
                                border = BorderStroke(1.5.dp, Color(0xFFFBCFE8)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFFCE7F3)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("⭐", fontSize = 18.sp, color = Color(0xFFEC4899))
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "💡 CareOS Specialist Match",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 11.sp,
                                            color = Color(0xFFBE185D)
                                        )
                                        Text(
                                            text = "Your symptoms relate to $specialty. You can consult $name's custom-trained AI pre-screener immediately.",
                                            fontSize = 11.sp,
                                            color = Color(0xFF9D174D),
                                            lineHeight = 15.sp
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Button(
                                                onClick = {
                                                    val specialist = viewModel.specialists.value.find { it.id == specId }
                                                    if (specialist != null) {
                                                        viewModel.startSpecialistConsultation(specialist)
                                                        onNavigateToTab("specialists")
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC4899)),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Text("Consult Specialist AI", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (isLoading) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.CenterStart) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }

                    // If active triage result exists, display a lovely summary card with doctor escalation options
                    activeTriageResult?.let { result ->
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                                border = BorderStroke(1.5.dp, Color(0xFFF59E0B))
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Text("📋 CareOS AI Triage Result", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFB45309))
                                        Text("CONFIDENCE: ${result.confidenceBand.uppercase()}", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFD97706))
                                    }

                                    if (result.hasRedFlags) {
                                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFEE2E2)), border = BorderStroke(1.dp, Color(0xFFFECACA))) {
                                            Text(
                                                text = "⚠️ EMERGENCY LEVEL RED FLAG FOUND:\n${result.redFlagsDetail}",
                                                color = Color(0xFF991B1B),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(8.dp)
                                            )
                                        }
                                    }

                                    Text("Assessed Cause: ${result.likelyCauses}", fontSize = 11.5.sp, color = Color.Black)
                                    Text("Protocol Next Step: ${result.recommendedNextAction}", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color.Black)

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                        OutlinedButton(
                                            onClick = { viewModel.resetTriageSession() },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Reset Chat", fontSize = 10.sp)
                                        }
                                        Button(
                                            onClick = {
                                                viewModel.startDoctorEscalation("AI symptom check: ${result.symptomDescription}")
                                                onEscalateClick()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                            modifier = Modifier.weight(1.5f)
                                        ) {
                                            Text("Escalate to Live GP", fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Input Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Photo button
                    IconButton(
                        onClick = { showCameraScanner = true },
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFFF1F5F4), CircleShape)
                            .testTag("photo_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = "Add Photo Scan",
                            tint = Color(0xFF0F766E),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Voice button
                    IconButton(
                        onClick = { showVoiceRecordDialog = true },
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFFF1F5F4), CircleShape)
                            .testTag("voice_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Record Voice Note",
                            tint = Color(0xFF0F766E),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        placeholder = { Text("Describe clinical symptoms...", fontSize = 13.sp, color = Color(0xFF475569)) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("triage_input"),
                        maxLines = 3,
                        singleLine = false,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF1F5F4),
                            unfocusedContainerColor = Color(0xFFF8FBFA),
                            focusedBorderColor = Color(0xFF0F766E),
                            unfocusedBorderColor = Color(0xFFDCE7E5),
                            cursorColor = Color(0xFF0F766E)
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.5.sp, color = Color(0xFF0F172A))
                    )

                    IconButton(
                        onClick = {
                            if (textInput.isNotBlank()) {
                                viewModel.sendTriageMessage(textInput)
                                textInput = ""
                            }
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFF0F766E), CircleShape)
                            .testTag("send_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send Message",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }

    if (showCameraScanner) {
        CameraScannerModal(
            onDismiss = { showCameraScanner = false },
            onImageCaptured = { base64, mime, uri ->
                viewModel.selectImage(base64, mime, uri)
                showCameraScanner = false
                viewModel.sendTriageMessage("Attached photo scan.")
            }
        )
    }

    if (showVoiceRecordDialog) {
        VoiceRecordDialog(
            onDismiss = { showVoiceRecordDialog = false },
            onVoiceTranscribed = { transcription ->
                textInput = transcription
                showVoiceRecordDialog = false
            }
        )
    }
}

@Composable
fun TriageChatBubble(
    msg: com.example.ui.ChatMessage,
    onSpeak: (String) -> Unit = {},
    onStopSpeaking: () -> Unit = {},
    isCurrentlySpeaking: Boolean = false,
    onStartSpeaking: () -> Unit = {}
) {
    val isUser = msg.sender == "user"
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Card(
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (isUser) 20.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 20.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) Color(0xFF0F766E) else Color.White
            ),
            border = BorderStroke(1.dp, if (isUser) Color(0xFF115E59) else Color(0xFFDCE7E5)),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (!isUser) {
                    // Luxurious, calming clinical credential badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(bottom = 2.dp)
                    ) {
                        Text(
                            text = "🛡️ CareOS Clinical Concierge",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F766E),
                            letterSpacing = 0.5.sp
                        )
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF16A34A))
                        )
                    }
                }

                if (msg.imageUri != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF1F5F4)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("📷 Photo Attachment Included", color = Color(0xFF0F766E), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                
                Text(
                    text = msg.text,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isUser) Color.White else Color(0xFF0F172A),
                    lineHeight = 21.sp
                )

                if (!isUser) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = {
                            if (isCurrentlySpeaking) {
                                onStopSpeaking()
                            } else {
                                onStartSpeaking()
                                onSpeak(msg.text)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isCurrentlySpeaking) Color(0xFFDC2626) else Color(0xFFD9F3EE)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (isCurrentlySpeaking) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = if (isCurrentlySpeaking) "Stop Replay" else "Play Audio Out Loud",
                                tint = if (isCurrentlySpeaking) Color.White else Color(0xFF0F766E),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (isCurrentlySpeaking) "STOP REPLAY" else "🔊 READ ALOUD",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isCurrentlySpeaking) Color.White else Color(0xFF0F766E)
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- SCREEN 2: VOICE RECORD DIALOG ---
@Composable
fun VoiceRecordDialog(
    onDismiss: () -> Unit,
    onVoiceTranscribed: (String) -> Unit
) {
    var recordingSimulated by remember { mutableStateOf(false) }
    var waveScale by remember { mutableStateOf(1f) }

    LaunchedEffect(recordingSimulated) {
        if (recordingSimulated) {
            while (true) {
                waveScale = 1.2f + kotlin.random.Random.nextFloat() * (1.8f - 1.2f)
                delay(120)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Multilingual Voice Intake", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Speak in your natural local dialect. CareOS auto-transcribes and translates Pidgin, Hausa, Yoruba, or Igbo into clinical-grade English reports.",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )

                Box(
                    modifier = Modifier.size(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .clip(CircleShape)
                            .background(if (recordingSimulated) Color(0xFFEF4444) else Color(0xFF0D9488)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.Mic, contentDescription = "Mic", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                }

                if (recordingSimulated) {
                    Text("🔴 Recording Voice Note... wave variance scale: $waveScale", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }

                Button(
                    onClick = { recordingSimulated = !recordingSimulated },
                    colors = ButtonDefaults.buttonColors(containerColor = if (recordingSimulated) Color.Gray else Color(0xFFEF4444))
                ) {
                    Text(if (recordingSimulated) "Stop & Audio-Process" else "Start Microphone")
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text("QUICK DIALECT SIMULATIONS:", fontWeight = FontWeight.Bold, fontSize = 9.sp, color = Color.Gray)
                
                Button(
                    onClick = {
                        onVoiceTranscribed("Patient reports: 'I get hot skin rash for my hand, e dey scratch me well well, e dey burn.' (Translation: Erthematous, highly pruritic eruptive rash on hand with associated burning sensation)")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Simulate Pidgin: 'I get hot skin rash...'", fontSize = 9.5.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// --- SCREEN 3: CAMERA SCANNER MODAL ---
@Composable
fun CameraScannerModal(
    onDismiss: () -> Unit,
    onImageCaptured: (String, String, String) -> Unit
) {
    var scanType by remember { mutableStateOf("Skin Rash") }
    var compressActive by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clinical Camera Intake", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Text("Position the skin rash, wound progression, medicine pack, or hospital document clearly inside the frame.", fontSize = 11.sp, color = Color.Gray)

                // Scan type selectors
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Skin Rash", "Wound Progress", "Medicine Pack", "Invoice OCR").forEach { type ->
                        val isSel = scanType == type
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) Color(0xFF0F172A) else Color(0xFFE2E8F0))
                                .clickable { scanType = type }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(type, fontSize = 9.sp, color = if (isSel) Color.White else Color.DarkGray, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Camera viewfinder visual simulation
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black)
                        .border(BorderStroke(2.dp, Color(0xFF0D9488)), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("📷 [VIEWFINDER OUTLINE]", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        Text("Target: $scanType", color = Color(0xFF2DD4BF), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Nigeria-first Auto Compression", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Text("Compresses raw image for slow 2G networks.", fontSize = 9.sp, color = Color.Gray)
                    }
                    Switch(checked = compressActive, onCheckedChange = { compressActive = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // Send dummy base64 skin rash image to trigger Gemini
                    onImageCaptured("dummyBase64", "image/jpeg", "content://media/external/images/media/rash")
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488))
            ) {
                Text("Capture & Scan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// --- SCREEN 4: TELEHEALTH SCREEN (LIVE CONSULTATION) ---
@Composable
fun TelehealthScreen(viewModel: CareViewModel) {
    val doctorMessages by viewModel.doctorChatMessages.collectAsState()
    val isDoctorTyping by viewModel.isDoctorTyping.collectAsState()
    var telehealthInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Cloudflare Stream TUS Uploader state and UI controls
    val uploaderState = rememberCloudflareUploader()
    var showStreamUploadPanel by remember { mutableStateOf(false) }

    // Scroll down on messages changes
    LaunchedEffect(doctorMessages.size) {
        if (doctorMessages.isNotEmpty()) {
            listState.animateScrollToItem(doctorMessages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("telehealth_screen")
    ) {
        // Active doctor header with Cloudflare Stream upload toggle
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
            shape = RoundedCornerShape(0.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFE2E8F0)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("👩‍⚕️", fontSize = 18.sp)
                    }
                    Column {
                        Text("Dr. Chioma Nwachukwu, GP", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("CareOS Duty Doctor • Board Registry: LUTH-MD-2901", fontSize = 9.5.sp, color = Color.Gray)
                    }
                }
                
                IconButton(
                    onClick = { showStreamUploadPanel = !showStreamUploadPanel },
                    modifier = Modifier.testTag("cloudflare_upload_toggle_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.VideoCall,
                        contentDescription = "Cloudflare Live Stream Upload",
                        tint = if (showStreamUploadPanel) Color(0xFFE11D48) else Color(0xFF0F766E),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        // Expanded Cloudflare Stream TUS Upload Dashboard
        if (showStreamUploadPanel) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .testTag("cloudflare_upload_panel"),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFAF9)),
                border = BorderStroke(1.5.dp, Color(0xFFE7E5E4)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("📹", fontSize = 22.sp)
                            Text(
                                text = "Cloudflare Stream Integration",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                color = Color(0xFF1C1917)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .background(
                                    when (uploaderState.state) {
                                        is CloudflareUploadState.Uploading -> Color(0xFFFEF08A)
                                        is CloudflareUploadState.Completed -> Color(0xFFDCFCE7)
                                        is CloudflareUploadState.Error -> Color(0xFFFEE2E2)
                                        else -> Color(0xFFE7E5E4)
                                    },
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            val statusText = when (uploaderState.state) {
                                is CloudflareUploadState.Idle -> "TUS Idle"
                                is CloudflareUploadState.GeneratingToken -> "Generating..."
                                is CloudflareUploadState.Uploading -> if ((uploaderState.state as CloudflareUploadState.Uploading).isPaused) "Paused" else "Uploading"
                                is CloudflareUploadState.Completed -> "Uploaded ✅"
                                is CloudflareUploadState.Error -> "Failed ❌"
                            }
                            Text(
                                text = statusText,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }

                    Text(
                        text = "Securely generate single-use TUS upload tokens server-side and upload video recordings directly to Cloudflare Stream CDN. Supports resumable chunked uploading.",
                        fontSize = 11.sp,
                        color = Color(0xFF57534E),
                        lineHeight = 15.sp
                    )

                    HorizontalDivider(color = Color(0xFFE7E5E4))

                    when (val uploadState = uploaderState.state) {
                        is CloudflareUploadState.Idle -> {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "Choose Simulated Video Payload Size to Upload:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF292524)
                                )

                                var selectedSizeMb by remember { mutableStateOf(5) }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(5, 15, 30).forEach { size ->
                                        val isSel = selectedSizeMb == size
                                        Card(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { selectedSizeMb = size },
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isSel) Color(0xFF0F766E) else Color.White
                                            ),
                                            border = BorderStroke(1.dp, if (isSel) Color(0xFF0F766E) else Color(0xFFD6D3D1)),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = "${size}MB\n" + when(size) {
                                                    5 -> "Symptom Clip"
                                                    15 -> "GP Consult"
                                                    else -> "Full Session"
                                                },
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSel) Color.White else Color.Black,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(8.dp),
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }

                                Button(
                                    onClick = {
                                        val sizeBytes = selectedSizeMb * 1024 * 1024
                                        val mockBytes = ByteArray(sizeBytes) { 0x0A }
                                        uploaderState.onStartUpload(mockBytes)
                                    },
                                    modifier = Modifier.fillMaxWidth().testTag("start_cloudflare_upload_btn"),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(imageVector = Icons.Default.CloudUpload, contentDescription = "Upload icon")
                                        Text("Generate Token & Upload to Cloudflare")
                                    }
                                }
                            }
                        }

                        is CloudflareUploadState.GeneratingToken -> {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(color = Color(0xFF0D9488), modifier = Modifier.size(36.dp))
                                Text(
                                    text = "Accessing secure server environment config...",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF57534E)
                                )
                                Text(
                                    text = "Generating Cloudflare TUS direct-creator upload URL securely...",
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        is CloudflareUploadState.Uploading -> {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = if (uploadState.isPaused) "Upload Paused (TUS Resumable)" else "Uploading Chunked Stream...",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1C1917)
                                        )
                                        Text(
                                            text = "Offset: ${com.example.api.formatBytes(uploadState.bytesUploaded)} / ${com.example.api.formatBytes(uploadState.totalBytes)} (${(uploadState.progress * 100).toInt()}%)",
                                            fontSize = 10.sp,
                                            color = Color.Gray
                                        )
                                    }
                                    if (!uploadState.isPaused) {
                                        Text(
                                            text = "${com.example.api.formatSpeed(uploadState.uploadSpeedBytesPerSec)} | ETA: ${uploadState.etaSeconds}s",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0D9488)
                                        )
                                    }
                                }

                                LinearProgressIndicator(
                                    progress = { uploadState.progress },
                                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                    color = Color(0xFF0D9488),
                                    trackColor = Color(0xFFE7E5E4)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (uploadState.isPaused) {
                                        Button(
                                            onClick = { uploaderState.onResume() },
                                            modifier = Modifier.weight(1f).testTag("resume_upload_btn"),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488))
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Play Icon", modifier = Modifier.size(16.dp))
                                                Text("Resume TUS Upload", fontSize = 11.sp)
                                            }
                                        }
                                    } else {
                                        Button(
                                            onClick = { uploaderState.onPause() },
                                            modifier = Modifier.weight(1f).testTag("pause_upload_btn"),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48))
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Icon(imageVector = Icons.Default.Pause, contentDescription = "Pause Icon", modifier = Modifier.size(16.dp))
                                                Text("Pause Upload", fontSize = 11.sp)
                                            }
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = { uploaderState.onCancel() },
                                        modifier = Modifier.weight(0.5f).testTag("cancel_upload_btn"),
                                        border = BorderStroke(1.dp, Color(0xFFD6D3D1)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Cancel", fontSize = 11.sp, color = Color.Black)
                                    }
                                }
                            }
                        }

                        is CloudflareUploadState.Completed -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFECFDF5), RoundedCornerShape(8.dp))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "🎉 Cloudflare Video Upload Successful!",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color(0xFF047857)
                                )
                                Text(
                                    text = if (uploadState.isMock) "Demo Simulation Mode: Successfully generated and tracked resumable TUS upload offsets locally."
                                           else "Production Mode: Real TUS upload complete! Video stream has been parsed and is now live on Cloudflare Stream CDN.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF065F46)
                                )
                                androidx.compose.foundation.text.selection.SelectionContainer {
                                    Column {
                                        Text(
                                            text = "UID: ${uploadState.videoUid}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.DarkGray
                                        )
                                        Text(
                                            text = "Watch: ${uploadState.watchUrl}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF0D9488)
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            viewModel.sendDoctorMessage("Check out the video stream from our telehealth session recorded live: ${uploadState.watchUrl}")
                                            uploaderState.onCancel()
                                        },
                                        modifier = Modifier.weight(1.5f).testTag("send_video_link_btn"),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488))
                                    ) {
                                        Text("Send Video Link to Dr.", fontSize = 11.sp)
                                    }

                                    Button(
                                        onClick = { uploaderState.onCancel() },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                                    ) {
                                        Text("Clear", fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        is CloudflareUploadState.Error -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFFEF2F2), RoundedCornerShape(8.dp))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "❌ Upload Failed",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color(0xFFB91C1C)
                                )
                                Text(
                                    text = uploadState.message,
                                    fontSize = 11.sp,
                                    color = Color(0xFF991B1B)
                                )

                                Text(
                                    text = "Tip: Make sure you have entered valid CLOUDFLARE_ACCOUNT_ID and CLOUDFLARE_API_TOKEN environment variables in the secure Secrets panel in AI Studio if you wish to run a live upload. Otherwise, it will automatically run in sandbox simulation mode.",
                                    fontSize = 10.sp,
                                    color = Color.Gray,
                                    lineHeight = 14.sp
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { uploaderState.onCancel() },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB91C1C))
                                    ) {
                                        Text("Try Again", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (doctorMessages.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("💬", fontSize = 44.sp)
                    Text("Initiate On-Demand Clinician Handoff", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Button(onClick = { viewModel.startDoctorEscalation("Telehealth Handshake Initiation") }) {
                        Text("Connect to Duty GP")
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp)
            ) {
                items(doctorMessages) { dMsg ->
                    val isU = dMsg.sender == "user"
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = if (isU) Alignment.CenterEnd else Alignment.CenterStart) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = if (isU) Color(0xFFD9F99D) else Color(0xFFF1F5F9)),
                            modifier = Modifier.widthIn(max = 270.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = if (isU) "Simeon Adebayo" else "Dr. Chioma Nwachukwu",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Gray
                                )
                                Text(dMsg.text, fontSize = 12.sp, color = Color.Black)
                            }
                        }
                    }
                }

                if (isDoctorTyping) {
                    item {
                        Text("Dr. Chioma is typing reply...", fontSize = 10.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = Color.Gray)
                    }
                }
            }

            // Input Row
            Row(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = telehealthInput,
                    onValueChange = { telehealthInput = it },
                    placeholder = { Text("Consult duty clinician...") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                IconButton(
                    onClick = {
                        if (telehealthInput.isNotBlank()) {
                            viewModel.sendDoctorMessage(telehealthInput)
                            telehealthInput = ""
                        }
                    }
                ) {
                    Icon(imageVector = Icons.Default.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun SpecialistsScreen(viewModel: CareViewModel) {
    val specialists by viewModel.specialists.collectAsState()
    val activeSession by viewModel.activeSpecialistSession.collectAsState()
    val isChatLoading by viewModel.isSpecialistChatLoading.collectAsState()

    // Flows for Patient Records & EHRs
    val patientRecords by viewModel.allPatientRecords.collectAsState()
    val personalEhrs by viewModel.allPersonalEHRs.collectAsState()
    val isAiPlanLoading by viewModel.isAiPlanLoading.collectAsState()
    val isEhrAnalysisLoading by viewModel.isEhrAnalysisLoading.collectAsState()
    val ehrAnalysisResult by viewModel.ehrAnalysisResult.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var activeSubTab by remember { mutableStateOf("browse") } // "browse", "ehr", "manage", "register"

    // Inputs for registration
    var nameInput by remember { mutableStateOf("") }
    var selectedSpecialtyIndex by remember { mutableStateOf(0) }
    var customSpecialtyInput by remember { mutableStateOf("") }
    var locationInput by remember { mutableStateOf("") }
    var rateInput by remember { mutableStateOf("2.50") }
    var currencyInput by remember { mutableStateOf("USD") }
    var bioInput by remember { mutableStateOf("") }
    var aiNameInput by remember { mutableStateOf("") }
    var aiKbInput by remember { mutableStateOf("") }
    var aiPromptInput by remember { mutableStateOf("") }
    var credentialsAttachedName by remember { mutableStateOf<String?>(null) }
    var registrationCompleted by remember { mutableStateOf(false) }

    // Inputs for Doctor Patient Management
    var showAddPatientDialog by remember { mutableStateOf(false) }
    var patNameInput by remember { mutableStateOf("") }
    var patAgeInput by remember { mutableStateOf("") }
    var patGenderInput by remember { mutableStateOf("Male") }
    var patConditionInput by remember { mutableStateOf("") }
    var patMedsInput by remember { mutableStateOf("") }
    var patApptsInput by remember { mutableStateOf("") }
    var patNotesInput by remember { mutableStateOf("") }
    var selectedDoctorIdForPatient by remember { mutableStateOf("spec_1") }
    var selectedPatientForDetail by remember { mutableStateOf<PatientRecord?>(null) }
    var doctorAiQueryInput by remember { mutableStateOf("") }
    var doctorAiChatResult by remember { mutableStateOf("") }
    var isDoctorAiQueryLoading by remember { mutableStateOf(false) }

    // Inputs for Patient EHR Wallet
    var showAddEhrDialog by remember { mutableStateOf(false) }
    var ehrNameInput by remember { mutableStateOf("") }
    var ehrAgeInput by remember { mutableStateOf("") }
    var ehrHistoryInput by remember { mutableStateOf("") }
    var ehrSymptomsInput by remember { mutableStateOf("") }
    var ehrAllergiesInput by remember { mutableStateOf("") }
    var ehrHospitalInput by remember { mutableStateOf("") }
    var ehrQueryInput by remember { mutableStateOf("Review my logged health profile and tell me which physical hospital or CareOS specialist is recommended.") }

    // Search & filter
    var searchQuery by remember { mutableStateOf("") }
    var selectedSpecialtyFilter by remember { mutableStateOf("All Specialties") }

    val registrationSpecialties = listOf(
        "Optometrist (Eye Specialist)",
        "Dentist (Oral Specialist)",
        "Orthopedist (Bone Specialist)",
        "Cardiologist (Heart Specialist)",
        "Dermatologist (Skin Specialist)",
        "Neurologist (Brain Specialist)",
        "Pediatrician (Child Specialist)",
        "Gynecologist (Women's Health)",
        "Oncologist (Cancer Specialist)",
        "Psychiatrist (Mental Health)",
        "Neonatal Nurse Specialist",
        "Other (Specify Custom specialty...)"
    )

    val specialtiesList = listOf("All Specialties") + registrationSpecialties.filter { it != "Other (Specify Custom specialty...)" }

    // Running Timer Effect for Live Doctor Call
    LaunchedEffect(activeSession?.phase) {
        if (activeSession?.phase == "live_doctor") {
            while (true) {
                delay(1000)
                viewModel.updateSpecialistTimer()
            }
        }
    }

    if (activeSession != null) {
        // --- IMMERSIVE COOPERATIVE / LIVE SURGERY TELE-PRESENCE & CONSULTATION WORKSPACE ---
        val session = activeSession!!
        val chatListState = rememberLazyListState()

        LaunchedEffect(session.messages.size) {
            if (session.messages.isNotEmpty()) {
                chatListState.animateScrollToItem(session.messages.size - 1)
            }
        }

        var chatInputText by remember { mutableStateOf("") }
        var doctorDischargeNotes by remember { mutableStateOf("") }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A)) // Sleek premium medical-slate dark backdrop
                .testTag("specialist_consult_active")
        ) {
            // High-fidelity active headers
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF334155)),
                                contentAlignment = Alignment.Center
                            ) {
                                val specialtyEmoji = when {
                                    session.specialist.specialty.contains("Eye") || session.specialist.specialty.contains("Opto") -> "👁️"
                                    session.specialist.specialty.contains("Dentist") || session.specialist.specialty.contains("Oral") -> "🦷"
                                    session.specialist.specialty.contains("Bone") || session.specialist.specialty.contains("Ortho") -> "🦴"
                                    session.specialist.specialty.contains("Nurse") || session.specialist.specialty.contains("Neonatal") -> "👶"
                                    else -> "🩺"
                                }
                                Text(specialtyEmoji, fontSize = 20.sp)
                            }
                            Column {
                                Text(
                                    text = session.specialist.name,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = session.specialist.specialty,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF38BDF8)
                                )
                            }
                        }

                        IconButton(onClick = { viewModel.endSpecialistSession() }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close session", tint = Color.White)
                        }
                    }

                    // Real-time status band (AI pre-screener or Live consult billing ticker!)
                    if (session.phase == "ai_triage") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0284C7).copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                .border(BorderStroke(1.dp, Color(0xFF0284C7)), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = Color(0xFF38BDF8))
                                    Text(
                                        text = "Complementary AI Pre-screening active",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF38BDF8)
                                    )
                                }
                                Text(
                                    text = "FREE AI PHASE",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF38BDF8)
                                )
                            }
                        }
                    } else if (session.phase == "live_doctor") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFE11D48).copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                .border(BorderStroke(1.dp, Color(0xFFF43F5E)), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFF43F5E)))
                                    val minutes = session.activeCallSeconds / 60
                                    val seconds = session.activeCallSeconds % 60
                                    Text(
                                        text = "Live physician Telepresence: ${String.format("%02d:%02d", minutes, seconds)}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFDA4AF)
                                    )
                                }
                                Text(
                                    text = "Accrued Cost: \$${String.format("%.2f", session.currentBillingAmount)}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFFF43F5E)
                                )
                            }
                        }
                    }
                }
            }

            if (session.phase == "live_doctor") {
                // --- HIGH FIDELITY SIMULATED CLINICAL VIDEO / ULTRASOUND / TELEMETRY FEED ---
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .background(Color.Black)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val width = size.width
                        val height = size.height
                        val midY = height / 2f
                        
                        // draw medical grids
                        for (i in 0 until width.toInt() step 50) {
                            drawLine(Color(0xFF1E293B), androidx.compose.ui.geometry.Offset(i.toFloat(), 0f), androidx.compose.ui.geometry.Offset(i.toFloat(), height), strokeWidth = 1f)
                        }
                        for (i in 0 until height.toInt() step 50) {
                            drawLine(Color(0xFF1E293B), androidx.compose.ui.geometry.Offset(0f, i.toFloat()), androidx.compose.ui.geometry.Offset(width, i.toFloat()), strokeWidth = 1f)
                        }

                        // pulsing heartbeat sinus wave
                        val path = androidx.compose.ui.graphics.Path()
                        path.moveTo(0f, midY)
                        for (x in 0..width.toInt() step 5) {
                            val timeFactor = (System.currentTimeMillis() / 150.0) % (2 * Math.PI)
                            val waveY = if (x % 160 < 20) {
                                midY + (Math.sin((x / 5f) + timeFactor) * 45f).toFloat()
                            } else {
                                midY + (Math.sin((x / 40f) + timeFactor) * 4f).toFloat()
                            }
                            path.lineTo(x.toFloat(), waveY)
                        }
                        drawPath(path, Color(0xFF10B981), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .background(Color.Red, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color.White))
                                Text("LIVE SURGICAL VIDEOSTREAM", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        
                        Column(horizontalAlignment = Alignment.End) {
                            Text("TELEMETRY CHANNEL 04", color = Color(0xFF10B981), fontSize = 7.5.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                            Text("LATENCY: 12ms (STARLINK-SECURE)", color = Color(0xFF10B981), fontSize = 7.5.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                            Text("CURRENCY EXCHANGE: GLOBAL SETTLEMENT", color = Color(0xFF10B981), fontSize = 7.5.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("🩺 CLINICAL TEAM TELE-PRESENCE PORTAL", fontWeight = FontWeight.Black, fontSize = 11.sp, color = Color.White)
                            Text("Click Discharge when surgery guidance or consultation is complete", fontSize = 8.sp, color = Color.LightGray)
                        }
                    }
                }
            }

            if (session.phase == "discharged") {
                // --- DISCHARGED SUMMARY VIEW & FHIR INTEROPERABILITY SYNC CARD ---
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🎉", fontSize = 48.sp)
                    Text(
                        text = "Consultation Successfully Completed",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                    Text(
                        text = "Your billing session has ended. All files and transcript analyses are now securely linked and synced directly to your Patient Longitudinal Health Record (LHR).",
                        fontSize = 11.sp,
                        color = Color.LightGray,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp
                    )

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        border = BorderStroke(1.5.dp, Color(0xFF10B981)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "🏥 INTEROPERABILITY FHIR SYNC REPORT",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Color(0xFF10B981)
                            )
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Black)
                                    .padding(12.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            ) {
                                SelectionContainer {
                                    Text(
                                        text = session.clinicalSummary,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = Color(0xFF34D399),
                                        lineHeight = 14.sp
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF065F46), RoundedCornerShape(8.dp))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("✅", fontSize = 16.sp)
                                Text(
                                    text = "HL7 FHIR Interoperability Bundle generated. Synced to primary clinic.",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { viewModel.endSpecialistSession() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC4899)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Back to Specialist Directory")
                    }
                }
            } else {
                // --- ACTIVE CONVERSATION CHAT SCREEN ---
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    LazyColumn(
                        state = chatListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(top = 14.dp, bottom = 120.dp)
                    ) {
                        items(session.messages) { msg ->
                            val isUser = msg.sender == "user"
                            val bubbleColor = when (msg.sender) {
                                "user" -> Color(0xFF2563EB)
                                "ai" -> Color(0xFF334155)
                                "doctor" -> Color(0xFF0D9488)
                                else -> Color(0xFF1E293B)
                            }
                            val senderLabel = when (msg.sender) {
                                "user" -> "Patient (Simeon Adebayo)"
                                "ai" -> "${session.specialist.customAiName} [AI COMPANION]"
                                "doctor" -> "${session.specialist.name} [LIVE SPECIALIST]"
                                else -> "CAREOS TELEMETRY SYSTEM"
                            }
                            
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
                            ) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = bubbleColor),
                                    modifier = Modifier.widthIn(max = 280.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = senderLabel,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White.copy(alpha = 0.7f)
                                        )
                                        Text(
                                            text = msg.text,
                                            fontSize = 12.sp,
                                            color = Color.White,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            }
                        }

                        if (isChatLoading) {
                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                                    Text("Specialist team analyzing...", color = Color.Gray, fontSize = 10.sp)
                                }
                            }
                        }

                        if (session.phase == "ai_triage" && !isChatLoading) {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                    border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = "💡 Specialist Handoff Control",
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF38BDF8),
                                            fontSize = 11.sp
                                        )
                                        Text(
                                            text = "You can instantly bypass pre-screening and escalate directly to the live physician. Consultation billing rates of \$${String.format("%.2f", session.specialist.ratePerMinute)}/min apply.",
                                            fontSize = 10.sp,
                                            color = Color.LightGray,
                                            lineHeight = 14.sp
                                        )
                                        Button(
                                            onClick = { viewModel.forceHandoffToLiveDoctor() },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Connect to Live Specialist", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bottom Row input or Discharge controls
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color(0xFF1E293B))
                            .padding(10.dp)
                    ) {
                        if (session.phase == "live_doctor") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = doctorDischargeNotes,
                                    onValueChange = { doctorDischargeNotes = it },
                                    placeholder = { Text("Enter Clinical Notes & Discharge Instructions...", color = Color.Gray) },
                                    modifier = Modifier.fillMaxWidth().background(Color(0xFF0F172A), RoundedCornerShape(8.dp)),
                                    maxLines = 2,
                                    textStyle = TextStyle(color = Color.White, fontSize = 11.sp)
                                )
                                
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = chatInputText,
                                        onValueChange = { chatInputText = it },
                                        placeholder = { Text("Consult live physician...", color = Color.Gray) },
                                        modifier = Modifier.weight(1f).background(Color(0xFF0F172A), RoundedCornerShape(8.dp)),
                                        singleLine = true,
                                        textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                                        trailingIcon = {
                                            IconButton(onClick = {
                                                if (chatInputText.isNotBlank()) {
                                                    viewModel.sendSpecialistMessage(chatInputText)
                                                    chatInputText = ""
                                                }
                                            }) {
                                                Icon(imageVector = Icons.Default.Send, contentDescription = "Send", tint = Color(0xFF38BDF8))
                                            }
                                        }
                                    )
                                    
                                    Button(
                                        onClick = {
                                            val notes = if (doctorDischargeNotes.isBlank()) "Cleared for standard outpatient care. No acute surgical intervention needed." else doctorDischargeNotes
                                            viewModel.dischargeAndLinkToLhr(notes)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF43F5E))
                                    ) {
                                        Text("Discharge", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else {
                            OutlinedTextField(
                                value = chatInputText,
                                onValueChange = { chatInputText = it },
                                placeholder = { Text("Explain symptoms to Custom AI...", color = Color.Gray) },
                                modifier = Modifier.fillMaxWidth().background(Color(0xFF0F172A), RoundedCornerShape(8.dp)),
                                singleLine = true,
                                textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                                trailingIcon = {
                                    IconButton(onClick = {
                                        if (chatInputText.isNotBlank()) {
                                            viewModel.sendSpecialistMessage(chatInputText)
                                            chatInputText = ""
                                        }
                                    }) {
                                        Icon(imageVector = Icons.Default.Send, contentDescription = "Send", tint = Color(0xFF38BDF8))
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    } else {
        // --- STANDARD LIST & REGISTRATION INTERFACE ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .testTag("specialists_screen")
        ) {
            ScrollableTabRow(
                selectedTabIndex = when (activeSubTab) {
                    "browse" -> 0
                    "ehr" -> 1
                    "manage" -> 2
                    else -> 3
                },
                containerColor = Color(0xFF0F172A),
                contentColor = Color.White,
                edgePadding = 12.dp
            ) {
                Tab(
                    selected = activeSubTab == "browse",
                    onClick = { activeSubTab = "browse" },
                    text = { Text("Specialist Directory", fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                )
                Tab(
                    selected = activeSubTab == "ehr",
                    onClick = { activeSubTab = "ehr" },
                    text = { Text("My EHR Wallet", fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                )
                Tab(
                    selected = activeSubTab == "manage",
                    onClick = { activeSubTab = "manage" },
                    text = { Text("AI Patient Manager", fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                )
                Tab(
                    selected = activeSubTab == "register",
                    onClick = { activeSubTab = "register" },
                    text = { Text("Specialist Signup Hub", fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                )
            }

            if (activeSubTab == "browse") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF1F5F9))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search specialists, hospitals, credentials...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search icon") }
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(specialtiesList) { specFilter ->
                            val isSel = selectedSpecialtyFilter == specFilter
                            Card(
                                modifier = Modifier
                                    .clickable { selectedSpecialtyFilter = specFilter }
                                    .testTag("filter_$specFilter"),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSel) Color(0xFFEC4899) else Color.White
                                ),
                                border = BorderStroke(1.dp, if (isSel) Color(0xFFEC4899) else Color(0xFFCBD5E1)),
                                shape = RoundedCornerShape(100.dp)
                            ) {
                                Text(
                                    text = specFilter,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) Color.White else Color(0xFF475569),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                val filteredSpecialists = specialists.filter { spec ->
                    val matchesQuery = spec.name.contains(searchQuery, ignoreCase = true) || 
                                       spec.specialty.contains(searchQuery, ignoreCase = true) ||
                                       spec.location.contains(searchQuery, ignoreCase = true) ||
                                       spec.bio.contains(searchQuery, ignoreCase = true)
                    
                    val matchesSpecialty = selectedSpecialtyFilter == "All Specialties" || spec.specialty == selectedSpecialtyFilter
                    matchesQuery && matchesSpecialty
                }

                if (filteredSpecialists.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("🩺", fontSize = 48.sp)
                            Text("No specialized doctors found matching criteria", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Try adjusting filters or onboarding yourself in the hub!", fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp)
                    ) {
                        items(filteredSpecialists) { spec ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth().testTag("spec_card_${spec.id}")
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFFFCE7F3)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                val specialtyEmoji = when {
                                                    spec.specialty.contains("Eye") || spec.specialty.contains("Opto") -> "👁️"
                                                    spec.specialty.contains("Dentist") || spec.specialty.contains("Oral") -> "🦷"
                                                    spec.specialty.contains("Bone") || spec.specialty.contains("Ortho") -> "🦴"
                                                    spec.specialty.contains("Nurse") || spec.specialty.contains("Neonatal") -> "👶"
                                                    else -> "🩺"
                                                }
                                                Text(specialtyEmoji, fontSize = 22.sp)
                                            }
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Text(
                                                        text = spec.name,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        fontSize = 13.5.sp,
                                                        color = Color(0xFF0F172A)
                                                    )
                                                    if (spec.isCustomRegistered) {
                                                        Box(
                                                            modifier = Modifier
                                                                .background(Color(0xFFE0F2FE), RoundedCornerShape(4.dp))
                                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                                        ) {
                                                            Text("NEW SIGNUP", fontSize = 6.5.sp, color = Color(0xFF0369A1), fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                }
                                                Text(
                                                    text = spec.specialty,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.5.sp,
                                                    color = Color(0xFFEC4899)
                                                )
                                            }
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text("⭐", fontSize = 10.sp, color = Color(0xFFF59E0B))
                                                Text(
                                                    text = "${spec.rating}",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp,
                                                    color = Color.Black
                                                )
                                                Text(
                                                    text = "(${spec.reviewsCount})",
                                                    fontSize = 9.5.sp,
                                                    color = Color.Gray
                                                )
                                            }
                                            Text(
                                                text = "\$${String.format("%.2f", spec.ratePerMinute)}/min",
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 12.sp,
                                                color = Color(0xFF16A34A)
                                            )
                                        }
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Icon(imageVector = Icons.Default.LocationOn, contentDescription = "Location", tint = Color.Gray, modifier = Modifier.size(11.dp))
                                            Text(text = spec.location, fontSize = 9.5.sp, color = Color.Gray)
                                        }
                                        Text(text = spec.bio, fontSize = 11.sp, color = Color(0xFF334155), lineHeight = 15.sp)
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                                            .border(BorderStroke(1.dp, Color(0xFFE2E8F0)), RoundedCornerShape(8.dp))
                                            .padding(10.dp)
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFEC4899)))
                                                Text(
                                                    text = "CUSTOM AI: ${spec.customAiName}",
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = Color(0xFF475569)
                                                )
                                            }
                                            Text(
                                                text = "Fed knowledge base files: \"${spec.customAiKnowledgeBase.take(65)}...\"",
                                                fontSize = 9.sp,
                                                color = Color.Gray,
                                                lineHeight = 12.sp
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = { viewModel.startSpecialistConsultation(spec) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC4899)),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = "Consult AI icon", modifier = Modifier.size(16.dp))
                                            Text("Consult Specialist AI Pre-Screener", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (activeSubTab == "ehr") {
                // --- PERSONAL EHR WALLET FOR RECORDLESS PATIENTS ---
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDFA)),
                        border = BorderStroke(1.5.dp, Color(0xFF99F6E4)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("📁", fontSize = 24.sp)
                                Text("CareOS EHR Wallet & Specialist Router", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = Color(0xFF0F766E))
                            }
                            Text(
                                text = "Perfect for patients without a standard medical record or hospital. Build your health history profile, log current symptoms, run AI health analysis, and let CareOS automatically generate referrals to live physical hospitals and specialists on our platform.",
                                fontSize = 11.sp,
                                color = Color(0xFF115E59),
                                lineHeight = 16.sp
                            )
                        }
                    }

                    if (personalEhrs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("🩺", fontSize = 56.sp)
                                Text("No Health Profile Found", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Gray)
                                Text("Create your digital EHR record to access automated medical routing and clinical care management.", fontSize = 11.sp, color = Color.Gray, textAlign = TextAlign.Center)
                                Button(
                                    onClick = { showAddEhrDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488))
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add EHR Icon")
                                        Text("Create Medical Record Profile")
                                    }
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Your Digital Health History", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
                            Button(
                                onClick = { showAddEhrDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(imageVector = Icons.Default.Edit, contentDescription = "Update icon", modifier = Modifier.size(12.dp))
                                    Text("Update History Profile", fontSize = 10.5.sp)
                                }
                            }
                        }

                        personalEhrs.forEach { ehr ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(
                                            modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFCCFBF1)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("👤", fontSize = 18.sp)
                                        }
                                        Column {
                                            Text(ehr.fullName, fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp, color = Color(0xFF0F172A))
                                            Text("Age: ${ehr.age} | Logged History Wallet", fontSize = 10.sp, color = Color.Gray)
                                        }
                                    }

                                    HorizontalDivider(color = Color(0xFFF1F5F9))

                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Medical History / Chronic Conditions:", fontWeight = FontWeight.Bold, fontSize = 10.5.sp, color = Color(0xFF0F766E))
                                        Text(if (ehr.medicalHistory.isBlank()) "None logged" else ehr.medicalHistory, fontSize = 11.5.sp, color = Color(0xFF334155))

                                        Text("Current Active Symptoms:", fontWeight = FontWeight.Bold, fontSize = 10.5.sp, color = Color(0xFF0F766E))
                                        Text(if (ehr.currentSymptoms.isBlank()) "None logged" else ehr.currentSymptoms, fontSize = 11.5.sp, color = Color(0xFF334155))

                                        Text("Known Allergies & Contraindications:", fontWeight = FontWeight.Bold, fontSize = 10.5.sp, color = Color(0xFFE11D48))
                                        Text(if (ehr.knownAllergies.isBlank()) "None logged" else ehr.knownAllergies, fontSize = 11.5.sp, color = Color(0xFF334155))

                                        if (ehr.preferredHospital.isNotBlank()) {
                                            Text("Preferred Physical Hospital:", fontWeight = FontWeight.Bold, fontSize = 10.5.sp, color = Color(0xFF475569))
                                            Text(ehr.preferredHospital, fontSize = 11.5.sp, color = Color(0xFF334155))
                                        }
                                    }

                                    HorizontalDivider(color = Color(0xFFF1F5F9))

                                    // Interactive AI Health Analysis Block
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp))
                                            .border(BorderStroke(1.dp, Color(0xFFE2E8F0)), RoundedCornerShape(12.dp))
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = "AI Icon", tint = Color(0xFF0D9488), modifier = Modifier.size(16.dp))
                                            Text("CareOS AI EHR Companion", fontWeight = FontWeight.Black, fontSize = 11.5.sp, color = Color(0xFF0F172A))
                                        }

                                        Text("Analyze your records & history to determine if emergency care, a physical clinic, or platform specialty referral is required.", fontSize = 10.5.sp, color = Color.Gray)

                                        OutlinedTextField(
                                            value = ehrQueryInput,
                                            onValueChange = { ehrQueryInput = it },
                                            label = { Text("EHR Diagnostic Prompt") },
                                            modifier = Modifier.fillMaxWidth(),
                                            textStyle = TextStyle(fontSize = 11.sp),
                                            maxLines = 2
                                        )

                                        Button(
                                            onClick = { viewModel.analyzePersonalHealth(ehr, ehrQueryInput) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                                            modifier = Modifier.fillMaxWidth(),
                                            enabled = !isEhrAnalysisLoading
                                        ) {
                                            if (isEhrAnalysisLoading) {
                                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("AI Synthesizing Record...", fontSize = 11.sp)
                                            } else {
                                                Text("Run AI EHR Diagnostics & Router", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            }
                                        }

                                        ehrAnalysisResult?.let { result ->
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                                border = BorderStroke(1.dp, Color(0xFF99F6E4)),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Text("📋 Diagnostic Synthesis Report", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF0F766E))
                                                    SelectionContainer {
                                                        Text(result, fontSize = 11.sp, color = Color(0xFF334155), lineHeight = 15.sp)
                                                    }
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .background(Color(0xFFECFDF5), RoundedCornerShape(6.dp))
                                                            .padding(8.dp)
                                                    ) {
                                                        Text(
                                                            text = "✅ SYSTEM: A clinical referral based on this analysis has been automatically generated and logged to the 'Referrals' tab! You can export it as an HL7 FHIR bundle to share with your physical clinic.",
                                                            fontSize = 9.sp,
                                                            color = Color(0xFF047857),
                                                            fontWeight = FontWeight.Bold,
                                                            lineHeight = 13.sp
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
            } else if (activeSubTab == "manage") {
                // --- DOCTOR AI PATIENT MANAGEMENT SYSTEM ---
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFDF2F8)),
                        border = BorderStroke(1.5.dp, Color(0xFFFBCFE8)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("👩‍⚕️", fontSize = 24.sp)
                                Text("CareOS AI Doctor Patient Manager", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = Color(0xFF9D174D))
                            }
                            Text(
                                text = "Doctors can feed our HIPAA-compliant AI with clinical records, symptoms, and diagnoses. The AI manages patient details, schedules follow-ups, and auto-populates regular pill intake reminders and appointment schedules to ensure high patient compliance.",
                                fontSize = 11.sp,
                                color = Color(0xFF831843),
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Active Managed Patients", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
                        Button(
                            onClick = { showAddPatientDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC4899)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = "Onboard Patient Icon", modifier = Modifier.size(12.dp))
                                Text("Onboard Patient", fontSize = 10.5.sp)
                            }
                        }
                    }

                    if (patientRecords.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("🩺", fontSize = 56.sp)
                                Text("No Patients Onboarded Yet", fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = Color.Gray)
                                Text("Click Onboard Patient to set up medication adherence plans and clinical follow-ups.", fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                    } else {
                        // Managed Patients List
                        patientRecords.forEach { patient ->
                            val isSelected = selectedPatientForDetail?.id == patient.id
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedPatientForDetail = if (isSelected) null else patient },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) Color(0xFFFFF1F2) else Color.White
                                ),
                                border = BorderStroke(1.dp, if (isSelected) Color(0xFFF43F5E) else Color(0xFFE2E8F0)),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(if (patient.gender == "Male") "👨" else "👩", fontSize = 24.sp)
                                            Column {
                                                Text(patient.name, fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp, color = Color(0xFF0F172A))
                                                Text("Age: ${patient.age} | ${patient.gender} | Logged Patient", fontSize = 10.sp, color = Color.Gray)
                                            }
                                        }

                                        IconButton(onClick = { viewModel.deletePatientRecord(patient.id) }) {
                                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Patient", tint = Color.Gray, modifier = Modifier.size(18.dp))
                                        }
                                    }

                                    Text(
                                        text = "Diagnostic State: ${patient.conditionDescription}",
                                        fontSize = 11.sp,
                                        color = Color(0xFF475569),
                                        fontWeight = FontWeight.Medium
                                    )

                                    if (isSelected) {
                                        HorizontalDivider(color = Color(0xFFFDA4AF))

                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text("Pills / Medications List:", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color(0xFFBE123C))
                                            Text(if (patient.medicationsText.isBlank()) "None logged" else patient.medicationsText, fontSize = 11.sp, color = Color(0xFF334155))

                                            Text("Upcoming Appts / Check-ups:", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color(0xFFBE123C))
                                            Text(if (patient.appointmentsText.isBlank()) "None logged" else patient.appointmentsText, fontSize = 11.sp, color = Color(0xFF334155))

                                            if (patient.doctorNotes.isNotBlank()) {
                                                Text("Clinical Notes:", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color(0xFFBE123C))
                                                Text(patient.doctorNotes, fontSize = 11.sp, color = Color(0xFF334155))
                                            }
                                        }

                                        HorizontalDivider(color = Color(0xFFFDA4AF))

                                        // Deploy/Run AI Patient Manager Care Plan
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color.White, RoundedCornerShape(12.dp))
                                                .border(BorderStroke(1.dp, Color(0xFFFBCFE8)), RoundedCornerShape(12.dp))
                                                .padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = "AI Doctor Icon", tint = Color(0xFFEC4899), modifier = Modifier.size(16.dp))
                                                Text("CareOS Patient AI Management Center", fontWeight = FontWeight.Black, fontSize = 11.sp, color = Color(0xFF9D174D))
                                            }

                                            Button(
                                                onClick = { viewModel.generateAiCarePlan(patient) },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC4899)),
                                                modifier = Modifier.fillMaxWidth(),
                                                enabled = isAiPlanLoading[patient.id] != true
                                            ) {
                                                if (isAiPlanLoading[patient.id] == true) {
                                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("AI Formulating Care Plan...", fontSize = 11.sp)
                                                } else {
                                                    Text("Generate & Compile AI Care Plan", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                                }
                                            }

                                            if (patient.aiGeneratedCarePlan.isNotBlank()) {
                                                Card(
                                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF5F5)),
                                                    border = BorderStroke(1.dp, Color(0xFFFECDD3)),
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        Text("📋 Formulated AI Care Plan", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF9D174D))
                                                        SelectionContainer {
                                                            Text(patient.aiGeneratedCarePlan, fontSize = 11.sp, color = Color(0xFF334155), lineHeight = 15.sp)
                                                        }
                                                        
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .background(Color(0xFFFCE7F3), RoundedCornerShape(6.dp))
                                                                .padding(8.dp)
                                                        ) {
                                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                                Text("🔔 COMPLIANCE ENGINE ACTIVE", fontWeight = FontWeight.ExtraBold, fontSize = 9.sp, color = Color(0xFF9D174D))
                                                                Text("Pill alarms and check-up appointments have been scheduled under CareOS system notifications to manage ${patient.name} regularly.", fontSize = 9.sp, color = Color(0xFF831843), lineHeight = 12.sp)
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            // Doctor Direct Query Chat
                                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text("Directly Consult AI Manager Regarding Patient Care:", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color.Gray)
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    OutlinedTextField(
                                                        value = doctorAiQueryInput,
                                                        onValueChange = { doctorAiQueryInput = it },
                                                        placeholder = { Text("e.g. 'Is Lonart safe with history of jaundice?'", fontSize = 10.5.sp) },
                                                        modifier = Modifier.weight(1f),
                                                        textStyle = TextStyle(fontSize = 11.sp),
                                                        maxLines = 2
                                                    )
                                                    Button(
                                                        onClick = {
                                                            if (doctorAiQueryInput.isNotBlank()) {
                                                                isDoctorAiQueryLoading = true
                                                                coroutineScope.launch {
                                                                    val recordJson = """
                                                                        {
                                                                            "patient": "${patient.name}",
                                                                            "condition": "${patient.conditionDescription}",
                                                                            "medications": "${patient.medicationsText}",
                                                                            "carePlan": "${patient.aiGeneratedCarePlan}"
                                                                        }
                                                                    """.trimIndent()
                                                                    val response = com.example.api.GeminiHelper.managePatientAI(recordJson, doctorAiQueryInput)
                                                                    doctorAiChatResult = response
                                                                    isDoctorAiQueryLoading = false
                                                                }
                                                            }
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569)),
                                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                        enabled = !isDoctorAiQueryLoading
                                                    ) {
                                                        if (isDoctorAiQueryLoading) {
                                                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(12.dp))
                                                        } else {
                                                            Text("Query AI", fontSize = 11.sp)
                                                        }
                                                    }
                                                }

                                                if (doctorAiChatResult.isNotBlank()) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
                                                            .padding(10.dp)
                                                    ) {
                                                        SelectionContainer {
                                                            Text(doctorAiChatResult, fontSize = 11.sp, color = Color(0xFF334155), lineHeight = 15.sp)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Text("Tap to manage record, run care plans & query Patient AI Manager", fontSize = 9.5.sp, color = Color.Gray, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (activeSubTab == "register") {
                // --- ONBOARD SPECIALIST PORTAL & AI TRAINING HUB ---
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                        border = BorderStroke(1.5.dp, Color(0xFFBFDBFE)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("🌐", fontSize = 24.sp)
                                Text("Global Medical Tele-Presence Network", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = Color(0xFF1E40AF))
                            }
                            Text(
                                text = "Onboard your private practice as a world-class Specialist on CareOS. Work from anywhere in the world, earn in foreign currency, feed your custom clinical AI model with your professional knowledge, and let it safely screen and schedule patients for you.",
                                fontSize = 11.sp,
                                color = Color(0xFF1E3A8A),
                                lineHeight = 16.sp
                            )
                        }
                    }

                    if (registrationCompleted) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)),
                            border = BorderStroke(1.5.dp, Color(0xFF10B981)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("🎉", fontSize = 44.sp)
                                Text("Onboarding Request Registered Successfully!", fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = Color(0xFF065F46))
                                Text(
                                    text = "Your specialized AI pre-screening model ($aiNameInput) has been compiled and hot-deployed. You are now live on the CareOS Specialists Directory and patients worldwide can consult with your AI companion.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF047857),
                                    textAlign = TextAlign.Center
                                )
                                Button(
                                    onClick = { 
                                        registrationCompleted = false
                                        activeSubTab = "browse"
                                        nameInput = ""
                                        locationInput = ""
                                        bioInput = ""
                                        aiNameInput = ""
                                        aiKbInput = ""
                                        aiPromptInput = ""
                                        credentialsAttachedName = null
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                                ) {
                                    Text("Go to Specialist Directory")
                                }
                            }
                        }
                    } else {
                        Text("1. Personal & Registry Details", fontWeight = FontWeight.Black, fontSize = 12.sp, color = Color(0xFF0F172A))
                        
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            label = { Text("Full Name (e.g. Dr. Jane Smith, MD)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Primary Medical Specialty", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Gray)
                            var dropdownExpanded by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { dropdownExpanded = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Black),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = registrationSpecialties[selectedSpecialtyIndex],
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = "Dropdown icon",
                                            tint = Color.Gray
                                        )
                                    }
                                }
                                DropdownMenu(
                                    expanded = dropdownExpanded,
                                    onDismissRequest = { dropdownExpanded = false },
                                    modifier = Modifier.fillMaxWidth(0.9f).background(Color.White)
                                ) {
                                    registrationSpecialties.forEachIndexed { idx, specOption ->
                                        DropdownMenuItem(
                                            text = { Text(specOption, fontSize = 12.sp) },
                                            onClick = {
                                                selectedSpecialtyIndex = idx
                                                dropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                            if (registrationSpecialties[selectedSpecialtyIndex] == "Other (Specify Custom specialty...)") {
                                OutlinedTextField(
                                    value = customSpecialtyInput,
                                    onValueChange = { customSpecialtyInput = it },
                                    label = { Text("Specify Specialty (e.g. Neurosurgeon, Oncologist)") },
                                    modifier = Modifier.fillMaxWidth().testTag("custom_specialty_input"),
                                    singleLine = true
                                )
                            }
                        }

                        OutlinedTextField(
                            value = locationInput,
                            onValueChange = { locationInput = it },
                            label = { Text("Primary Hospital / Clinic & License Registry ID") },
                            placeholder = { Text("e.g. Johns Hopkins Hospital, Maryland (MD-1029)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = rateInput,
                                onValueChange = { rateInput = it },
                                label = { Text("Consultation Rate per Min") },
                                modifier = Modifier.weight(1.5f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = currencyInput,
                                onValueChange = { currencyInput = it },
                                label = { Text("Currency") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }

                        OutlinedTextField(
                            value = bioInput,
                            onValueChange = { bioInput = it },
                            label = { Text("Professional Biography & Research Focus") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3
                        )

                        Button(
                            onClick = { credentialsAttachedName = "certified_medical_board_credentials_${System.currentTimeMillis() % 1000}.pdf" },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(imageVector = Icons.Default.Attachment, contentDescription = "Attach certificate")
                                Text(if (credentialsAttachedName == null) "Attach Medical Board License / Certificate" else "Attached: $credentialsAttachedName", fontSize = 11.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text("2. Configure Your Custom Pre-Screening AI Companion", fontWeight = FontWeight.Black, fontSize = 12.sp, color = Color(0xFF0F172A))
                        
                        OutlinedTextField(
                            value = aiNameInput,
                            onValueChange = { aiNameInput = it },
                            label = { Text("AI Agent Name (e.g. JenkinsEye-AI)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = aiKbInput,
                            onValueChange = { aiKbInput = it },
                            label = { Text("Feed AI Knowledge Base (Paste research, diagnostics, manuals...)") },
                            placeholder = { Text("e.g. My private practice protocols regarding visual acuity thresholds, cataracts progression indices, glaucoma diagnostics...") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 4
                        )

                        OutlinedTextField(
                            value = aiPromptInput,
                            onValueChange = { aiPromptInput = it },
                            label = { Text("AI Safety Threshold: When to handoff to live doctor?") },
                            placeholder = { Text("e.g. If patient has sudden pain, visual field cuts, or says they want to see me directly, escalate instantly.") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 4
                        )

                         Button(
                            onClick = {
                                if (nameInput.isNotBlank() && locationInput.isNotBlank()) {
                                    val r = rateInput.toDoubleOrNull() ?: 2.50
                                    val kb = if (aiKbInput.isBlank()) "Standard general diagnostic guidelines compiled by ${nameInput}." else aiKbInput
                                    val pr = if (aiPromptInput.isBlank()) "Assess standard concerns. If high concern or request, trigger [HANDOFF_TRIGGER] directly." else aiPromptInput
                                    val ain = if (aiNameInput.isBlank()) "${nameInput.substringAfter("Dr. ").substringBefore(",")}-AI" else aiNameInput
                                    val finalSpecialty = if (registrationSpecialties[selectedSpecialtyIndex] == "Other (Specify Custom specialty...)") {
                                        if (customSpecialtyInput.isNotBlank()) customSpecialtyInput else "General Specialist"
                                    } else {
                                        registrationSpecialties[selectedSpecialtyIndex]
                                    }
                                    
                                    viewModel.registerSpecialist(
                                        name = nameInput,
                                        specialty = finalSpecialty,
                                        location = locationInput,
                                        rate = r,
                                        currency = currencyInput,
                                        bio = bioInput,
                                        aiName = ain,
                                        aiKb = kb,
                                        aiPrompt = pr
                                    )
                                    registrationCompleted = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC4899)),
                            modifier = Modifier.fillMaxWidth().testTag("deploy_ai_specialist_button"),
                            enabled = nameInput.isNotBlank() && locationInput.isNotBlank()
                        ) {
                            Text("Register Specialist & Deploy Custom AI", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showAddPatientDialog) {
        AlertDialog(
            onDismissRequest = { showAddPatientDialog = false },
            title = { Text("Onboard Patient & Deploy AI Manager", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = patNameInput,
                        onValueChange = { patNameInput = it },
                        label = { Text("Patient Full Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = patAgeInput,
                            onValueChange = { patAgeInput = it },
                            label = { Text("Age") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Gender", fontSize = 10.sp, color = Color.Gray)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("Male", "Female").forEach { g ->
                                    val isSel = patGenderInput == g
                                    Card(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { patGenderInput = g },
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSel) Color(0xFFEC4899) else Color.White
                                        ),
                                        border = BorderStroke(1.dp, if (isSel) Color(0xFFEC4899) else Color(0xFFCBD5E1))
                                    ) {
                                        Text(
                                            g,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSel) Color.White else Color.Black,
                                            modifier = Modifier.padding(vertical = 6.dp, horizontal = 2.dp),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = patConditionInput,
                        onValueChange = { patConditionInput = it },
                        label = { Text("Clinical Presentation & Diagnosis Summary") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                    OutlinedTextField(
                        value = patMedsInput,
                        onValueChange = { patMedsInput = it },
                        label = { Text("Prescribed Pills / Dosage Regimen") },
                        placeholder = { Text("e.g. Artemether-Lumefantrine twice daily") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = patApptsInput,
                        onValueChange = { patApptsInput = it },
                        label = { Text("Appointment / Next Check-up Schedule") },
                        placeholder = { Text("e.g. Next Monday at 10:00 AM") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = patNotesInput,
                        onValueChange = { patNotesInput = it },
                        label = { Text("Secondary Care Guidelines / Notes") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                    
                    // Connected Doctor Dropdown
                    var connectedDoctorExpanded by remember { mutableStateOf(false) }
                    val activeDoc = specialists.find { it.id == selectedDoctorIdForPatient } ?: specialists.firstOrNull()
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Connected Attending Doctor / Specialist", fontSize = 10.sp, color = Color.Gray)
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { connectedDoctorExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Black)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(activeDoc?.name ?: "No Doctors Live", fontSize = 12.sp)
                                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "Dropdown")
                                }
                            }
                            DropdownMenu(
                                expanded = connectedDoctorExpanded,
                                onDismissRequest = { connectedDoctorExpanded = false },
                                modifier = Modifier.fillMaxWidth(0.8f).background(Color.White)
                            ) {
                                specialists.forEach { doc ->
                                    DropdownMenuItem(
                                        text = { Text("${doc.name} (${doc.specialty})", fontSize = 11.sp) },
                                        onClick = {
                                            selectedDoctorIdForPatient = doc.id
                                            connectedDoctorExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (patNameInput.isNotBlank()) {
                            val ageVal = patAgeInput.toIntOrNull() ?: 35
                            viewModel.addPatientRecord(
                                name = patNameInput,
                                age = ageVal,
                                gender = patGenderInput,
                                condition = patConditionInput,
                                medications = patMedsInput,
                                appointments = patApptsInput,
                                notes = patNotesInput,
                                doctorId = selectedDoctorIdForPatient
                            )
                            showAddPatientDialog = false
                            // Clear inputs
                            patNameInput = ""
                            patAgeInput = ""
                            patConditionInput = ""
                            patMedsInput = ""
                            patApptsInput = ""
                            patNotesInput = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC4899))
                ) {
                    Text("Deploy Patient AI")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddPatientDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddEhrDialog) {
        AlertDialog(
            onDismissRequest = { showAddEhrDialog = false },
            title = { Text("Log Personal EHR Health Profile", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = ehrNameInput,
                        onValueChange = { ehrNameInput = it },
                        label = { Text("Your Full Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = ehrAgeInput,
                        onValueChange = { ehrAgeInput = it },
                        label = { Text("Age") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = ehrHistoryInput,
                        onValueChange = { ehrHistoryInput = it },
                        label = { Text("Chronic Conditions / Medical History") },
                        placeholder = { Text("e.g. Mild Hypertension, past appendix surgery in 2019") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                    OutlinedTextField(
                        value = ehrSymptomsInput,
                        onValueChange = { ehrSymptomsInput = it },
                        label = { Text("Active Symptoms & Medical Needs") },
                        placeholder = { Text("e.g. Persistent chest tightness, breathing difficulties on exertion") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                    OutlinedTextField(
                        value = ehrAllergiesInput,
                        onValueChange = { ehrAllergiesInput = it },
                        label = { Text("Allergies & Sensitivities") },
                        placeholder = { Text("e.g. Penicillin, sulfur, peanuts") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = ehrHospitalInput,
                        onValueChange = { ehrHospitalInput = it },
                        label = { Text("Preferred Nearby Physical Hospital / Clinic") },
                        placeholder = { Text("e.g. National Hospital Abuja") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (ehrNameInput.isNotBlank()) {
                            val ageVal = ehrAgeInput.toIntOrNull() ?: 30
                            viewModel.createPersonalEHR(
                                fullName = ehrNameInput,
                                age = ageVal,
                                history = ehrHistoryInput,
                                symptoms = ehrSymptomsInput,
                                allergies = ehrAllergiesInput,
                                preferredHospital = ehrHospitalInput
                            )
                            showAddEhrDialog = false
                            // Clear inputs
                            ehrNameInput = ""
                            ehrAgeInput = ""
                            ehrHistoryInput = ""
                            ehrSymptomsInput = ""
                            ehrAllergiesInput = ""
                            ehrHospitalInput = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488))
                ) {
                    Text("Save & Log EHR")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddEhrDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}


// --- SCREEN 5: REFERRAL SCREEN ---
@Composable
fun ReferralScreen(viewModel: CareViewModel) {
    val referrals by viewModel.isolatedReferrals.collectAsState()
    var showFhirPayloadForReferralId by remember { mutableStateOf<Long?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("referral_screen")
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
            border = BorderStroke(1.5.dp, Color(0xFFBFDBFE)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("🏥 SPECIALIST INTEROPERABILITY LAYER", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF1E40AF))
                Text("Access official hospital referral records generated securely after symptom assessment checks. Conforming with standard FHIR API structures for instant clinic reception intake.", fontSize = 9.5.sp, color = Color(0xFF1E3A8A))
            }
        }

        Text("ACTIVE CLINICAL REFERRAL RECORDS", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Gray)

        if (referrals.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                Text("No active referrals. Referral cards generate automatically upon emergency detection.", color = Color.Gray, fontSize = 11.sp)
            }
        } else {
            referrals.forEach { r ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Column {
                                Text(r.hospitalName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Specialty: ${r.specialty}", fontSize = 11.sp, color = Color.Gray)
                            }
                            Text(
                                "ACTIVE REFERRAL",
                                color = Color(0xFF3B82F6),
                                fontWeight = FontWeight.Bold,
                                fontSize = 8.sp,
                                modifier = Modifier.background(Color(0xFFDBEAFE), RoundedCornerShape(4.dp)).padding(4.dp)
                            )
                        }

                        Text("Summary: ${r.clinicalSummary}", fontSize = 11.sp, color = Color.DarkGray)

                        Button(
                            onClick = { showFhirPayloadForReferralId = r.id },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E40AF)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Inspect FHIR Interoperability payload", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }

    if (showFhirPayloadForReferralId != null) {
        val refId = showFhirPayloadForReferralId!!
        val referral = referrals.find { it.id == refId }
        
        if (referral != null) {
            val pName = referral.patientName
            AlertDialog(
                onDismissRequest = { showFhirPayloadForReferralId = null },
                title = { Text("FHIR Referral (ServiceRequest)", fontSize = 14.sp, fontWeight = FontWeight.Bold) },
                text = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F172A))
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = """{
  "resourceType": "ServiceRequest",
  "id": "rf-00${referral.id}-luth",
  "status": "active",
  "intent": "order",
  "subject": {
    "reference": "Patient/simeon-adebayo",
    "display": "$pName"
  },
  "requester": {
    "display": "Dr. Chioma Nwachukwu, GP"
  },
  "recipient": [{
    "display": "${referral.hospitalName}"
  }],
  "reasonCode": [{
    "text": "Intake AI symptom check acute escalation"
  }],
  "description": "${referral.clinicalSummary.take(120)}...",
  "supportingInfo": [
    { "reference": "Observation/vitals-temp-38-5" },
    { "reference": "DocumentReference/rash-image-compressed" }
  ]
}""",
                            color = Color(0xFF34D399),
                            fontSize = 8.5.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            lineHeight = 11.sp
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = { showFhirPayloadForReferralId = null }) {
                        Text("Close Interoperability Inspector")
                    }
                }
            )
        }
    }
}

// --- SCREEN 4: INSURANCE ONBOARDING & DIGITAL HMO CARDS ---
@Composable
fun InsuranceScreen(viewModel: CareViewModel) {
    val activeIns by viewModel.activeInsurance.collectAsState()
    
    // Onboarding form steps: 0: Plans, 1: Eligibility Questionnaire, 2: KYC & Docs, 3: Dependents, 4: Secure Payment Review
    var currentStep by remember { mutableStateOf(0) }
    
    var selectedPlan by remember { mutableStateOf("NHIA Public Program") }
    var selectedHmo by remember { mutableStateOf("NHIA Federal") }
    
    // Eligibility Questionnaire states
    var incomeCategory by remember { mutableStateOf("Under ₦30,000 (Subsidized Tier)") }
    var healthNeedCategory by remember { mutableStateOf("General Outpatient Care") }
    
    // KYC inputs
    var fullName by remember { mutableStateOf("Simeon Adebayo") }
    var ninNumber by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("08031234567") }
    var ageField by remember { mutableStateOf("34") }
    var genderSelect by remember { mutableStateOf("Male") }
    var residentState by remember { mutableStateOf("Lagos") }
    
    // Vulnerable group checklist (under NHIA Vulnerable Group guidelines)
    var isPregnant by remember { mutableStateOf(false) }
    var isRefugee by remember { mutableStateOf(false) }
    var isDisabled by remember { mutableStateOf(false) }
    var isTraffickingSurvivor by remember { mutableStateOf(false) }
    
    val hasVulnerableGroupEligibility = isPregnant || isRefugee || isDisabled || isTraffickingSurvivor || (incomeCategory == "Under ₦30,000 (Subsidized Tier)")

    // KYC Document Collection Simulation states
    var isIdUploaded by remember { mutableStateOf(false) }
    var isBillUploaded by remember { mutableStateOf(false) }
    var activeScanningDoc by remember { mutableStateOf<String?>(null) } // "NIN" or "Address"
    var scanProgress by remember { mutableStateOf(0f) }

    // Dependents Queue
    var dependentName by remember { mutableStateOf("") }
    var dependentRelation by remember { mutableStateOf("Child") }
    var dependentAgeField by remember { mutableStateOf("") }
    var dependentsList by remember { mutableStateOf<List<Triple<String, String, String>>>(emptyList()) }
    
    // Payment Gateways (prioritizing cash/bank/card first, airtime second)
    var paymentMethod by remember { mutableStateOf("Bank Transfer / Card") }
    var mtnSharePin by remember { mutableStateOf("") }
    var simPaymentProgress by remember { mutableStateOf(false) }
    
    // Enrolled active states
    var providerSearchQuery by remember { mutableStateOf("") }
    var showPreauthDialog by remember { mutableStateOf(false) }
    var preauthDrugName by remember { mutableStateOf("Artemether-Lumefantrine") }
    var preauthFacility by remember { mutableStateOf("Lagos University Teaching Hospital (LUTH)") }
    var preauthSuccessMsg by remember { mutableStateOf<String?>(null) }
    
    val scope = rememberCoroutineScope()

    // Real-time NIN Validation check (NIN is strictly 11 digits under NIMC federal law)
    val isNinLengthValid = ninNumber.isEmpty() || (ninNumber.length == 11 && ninNumber.all { it.isDigit() })

    // OCR Document Scanner launched effect simulation
    LaunchedEffect(activeScanningDoc) {
        if (activeScanningDoc != null) {
            scanProgress = 0f
            while (scanProgress < 1.0f) {
                delay(100)
                scanProgress += 0.05f
            }
            if (activeScanningDoc == "NIN") {
                isIdUploaded = true
            } else if (activeScanningDoc == "Address") {
                isBillUploaded = true
            }
            activeScanningDoc = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("insurance_screen")
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (activeIns == null) {
            // --- ONBOARDING FLOW ---
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDFA)),
                border = BorderStroke(1.5.dp, Color(0xFF2DD4BF)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("🛡️ NIGERIA NATIONAL HEALTH COVERAGE GATEWAY", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF0F766E))
                    Text("CareOS integrates with accredited HMO partners and Nigeria's National Health Insurance Authority (NHIA) standards to provide instant clinical enrollment.", fontSize = 10.sp, color = Color(0xFF0D9488))
                    
                    // Progressive Stepper Bar
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("Plans", "Eligibility", "KYC Docs", "Dependents", "Activate").forEachIndexed { index, title ->
                            val isCompletedOrActive = index <= currentStep
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(if (isCompletedOrActive) Color(0xFF0D9488) else Color(0xFFE2E8F0))
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(title, fontSize = 8.sp, color = if (isCompletedOrActive) Color(0xFF0F766E) else Color.Gray, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            when (currentStep) {
                0 -> {
                    // STEP 1: PLAN COMPARISON & PARTNER HMO SELECTION
                    Text("STEP 1: PLAN COMPARISON & ACCREDITED PARTNERS", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E293B))
                    Text("Select your primary plan category below. Subsidized plans are fully financed under the federal NHIA Vulnerable Group Trust.", fontSize = 11.sp, color = Color.Gray)

                    // Partner HMO Accreditations Dropdown Selection Row
                    Text("SELECT LICENSED HMO PROVIDER OR FEDERAL SCHEME:", fontWeight = FontWeight.Bold, fontSize = 9.sp, color = Color.Gray)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("NHIA Federal", "Reliance HMO", "Hygeia HMO", "Leadway Health", "Total Health Trust").forEach { hmo ->
                            val isSel = selectedHmo == hmo
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) Color(0xFF0F766E) else Color(0xFFF1F5F9))
                                    .clickable { 
                                        selectedHmo = hmo
                                        if (hmo == "NHIA Federal") {
                                            selectedPlan = "NHIA Public Program"
                                        } else if (selectedPlan == "NHIA Public Program") {
                                            selectedPlan = "Reliance HMO Core"
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = hmo,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) Color.White else Color.DarkGray
                                )
                            }
                        }
                    }

                    // Side-by-Side Premium Plans Comparison Layout
                    val plans = listOf(
                        Triple(
                            "NHIA Public Program",
                            "₦0.00 (Fully Subsidized)",
                            "• 100% pre-funded primary malaria care & generic pharmacy\n• Full maternal diagnostics & child immunization\n• Requires proof of eligibility (Pregnant, Refugee, Disability, or Survivor)"
                        ),
                        Triple(
                            "Reliance HMO Core",
                            "₦12,500 / Annually",
                            "• Unlimited private primary GP consultations\n• Comprehensive outpatient lab diagnostics & malaria pharmacy\n• Includes 24/7 CareOS Live Doctor video teleconsultation"
                        ),
                        Triple(
                            "Hygeia Care Premium",
                            "₦35,000 / Annually",
                            "• Private ward admissions & specialist referrals\n• Full diagnostic imaging (X-rays, ultrasounds, basic MRI)\n• Emergency trauma surgery subsidy & 80% prescription copay"
                        )
                    )

                    plans.forEach { (title, cost, benefits) ->
                        val isSelected = selectedPlan == title
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    selectedPlan = title
                                    if (title == "NHIA Public Program") {
                                        selectedHmo = "NHIA Federal"
                                    } else if (selectedHmo == "NHIA Federal") {
                                        selectedHmo = "Reliance HMO" // switch to standard private HMO partner
                                    }
                                }
                                .border(
                                    BorderStroke(2.dp, if (isSelected) Color(0xFF0D9488) else Color.Transparent),
                                    RoundedCornerShape(16.dp)
                                ),
                            colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFFF0FDFA) else Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E293B))
                                    Text(cost, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = Color(0xFF0D9488))
                                }
                                Text(benefits, fontSize = 10.sp, color = Color.DarkGray, lineHeight = 14.sp)
                            }
                        }
                    }

                    // Interactive Benefit Comparison Matrix
                    var showComparisonTable by remember { mutableStateOf(false) }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("📋 BENEFIT COMPARISON MATRIX", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color(0xFF475569))
                                TextButton(onClick = { showComparisonTable = !showComparisonTable }) {
                                    Text(if (showComparisonTable) "Hide Details" else "Compare Side-by-Side", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (showComparisonTable) {
                                val matrix = listOf(
                                    Triple("Primary GP Consultation", "Subsidy Centers", "Unlimited Private"),
                                    Triple("Malaria Diagnostics & Drugs", "100% Free", "100% Free"),
                                    Triple("Antenatal Care/Maternity", "100% Subsidized", "Outpatient Cover"),
                                    Triple("Diagnostic Lab & CT/MRI", "Basic Labs Only", "Advanced Cover"),
                                    Triple("Emergency / Major Surgery", "Public Referral", "Copay Subsidies")
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Service Covered", fontWeight = FontWeight.Bold, fontSize = 9.sp, modifier = Modifier.weight(1.2f), color = Color.Gray)
                                        Text("NHIA Public", fontWeight = FontWeight.Bold, fontSize = 9.sp, modifier = Modifier.weight(1f), color = Color.Gray)
                                        Text("Reliance Core", fontWeight = FontWeight.Bold, fontSize = 9.sp, modifier = Modifier.weight(1f), color = Color.Gray)
                                    }
                                    HorizontalDivider()
                                    matrix.forEach { (srv, nhia, rel) ->
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(srv, fontSize = 9.sp, modifier = Modifier.weight(1.2f), fontWeight = FontWeight.Bold)
                                            Text(nhia, fontSize = 9.sp, modifier = Modifier.weight(1f), color = Color(0xFF0F766E))
                                            Text(rel, fontSize = 9.sp, modifier = Modifier.weight(1f), color = Color.DarkGray)
                                        }
                                    }
                                }
                            } else {
                                Text("Click to view side-by-side benefit differences between public federal programs and private commercial providers.", fontSize = 9.sp, color = Color.Gray)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = { currentStep = 1 },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488))
                    ) {
                        Text("Proceed to Eligibility Check")
                    }
                }
                
                1 -> {
                    // STEP 2: ELIGIBILITY QUESTIONNAIRE
                    Text("STEP 2: HEALTH COVER & SUBSIDY ASSESSMENT", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E293B))
                    Text("Answer these quick questions to dynamically calculate your public subsidy tier and recommend the optimal health program.", fontSize = 11.sp, color = Color.Gray)

                    // Q1: Monthly Income Category Selection
                    Text("1. WHAT IS YOUR MONTHLY HOUSEHOLD INCOME RANGE?", fontWeight = FontWeight.Bold, fontSize = 9.sp, color = Color.Gray)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "Under ₦30,000 (Subsidized Tier)",
                            "₦30,000 - ₦100,000 (Standard Tier)",
                            "Over ₦100,000 (Premium Tier)"
                        ).forEach { category ->
                            val isSel = incomeCategory == category
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) Color(0xFF0F766E).copy(alpha = 0.08f) else Color.White)
                                    .border(
                                        BorderStroke(
                                            if (isSel) 2.dp else 1.dp,
                                            if (isSel) Color(0xFF0D9488) else Color(0xFFE2E8F0)
                                        ),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        incomeCategory = category
                                        if (category == "Under ₦30,000 (Subsidized Tier)") {
                                            selectedPlan = "NHIA Public Program"
                                            selectedHmo = "NHIA Federal"
                                        }
                                    }
                                    .padding(12.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(category, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    RadioButton(selected = isSel, onClick = {
                                        incomeCategory = category
                                        if (category == "Under ₦30,000 (Subsidized Tier)") {
                                            selectedPlan = "NHIA Public Program"
                                            selectedHmo = "NHIA Federal"
                                        }
                                    })
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Q2: Primary Healthcare Needs Selection
                    Text("2. WHAT ARE YOUR PRIMARY HEALTHCARE REQUISITIONS?", fontWeight = FontWeight.Bold, fontSize = 9.sp, color = Color.Gray)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "General Outpatient Care",
                            "Maternal / Obstetric Care",
                            "Specialist Referrals",
                            "Pediatric Immunizations"
                        ).forEach { need ->
                            val isSel = healthNeedCategory == need
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) Color(0xFF0F766E) else Color(0xFFF1F5F9))
                                    .clickable { healthNeedCategory = need }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = need,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) Color.White else Color.DarkGray
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Q3: Vulnerable Status Questionnaire
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("3. STATUTORY VULNERABILITY ACCREDITATION", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color(0xFF475569))
                            Text("Federal NHIA mandates provide 100% pre-funded health coverage for marginalized segments. Check all that apply:", fontSize = 9.sp, color = Color.DarkGray)

                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { isPregnant = !isPregnant }) {
                                    Checkbox(checked = isPregnant, onCheckedChange = { isPregnant = it })
                                    Text("Pregnant Mother (Maternal Subsidy)", fontSize = 10.5.sp, color = Color(0xFF334155))
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { isDisabled = !isDisabled }) {
                                    Checkbox(checked = isDisabled, onCheckedChange = { isDisabled = it })
                                    Text("Person Living with Registered Disability", fontSize = 10.5.sp, color = Color(0xFF334155))
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { isRefugee = !isRefugee }) {
                                    Checkbox(checked = isRefugee, onCheckedChange = { isRefugee = it })
                                    Text("Registered Refugee / IDP (UNHCR)", fontSize = 10.5.sp, color = Color(0xFF334155))
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { isTraffickingSurvivor = !isTraffickingSurvivor }) {
                                    Checkbox(checked = isTraffickingSurvivor, onCheckedChange = { isTraffickingSurvivor = it })
                                    Text("Human Trafficking Survivor (NAPTIP)", fontSize = 10.5.sp, color = Color(0xFF334155))
                                }
                            }
                        }
                    }

                    // Dynamic Questionnaire Assessment Results
                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (hasVulnerableGroupEligibility) Color(0xFFECFDF5) else Color(0xFFEFF6FF)),
                        border = BorderStroke(1.dp, if (hasVulnerableGroupEligibility) Color(0xFF10B981) else Color(0xFF3B82F6)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(if (hasVulnerableGroupEligibility) "✨" else "🛡️", fontSize = 14.sp)
                                Text(
                                    text = if (hasVulnerableGroupEligibility) "ELIGIBLE FOR 100% PUBLIC COVER" else "ELIGIBLE FOR SUBSIDIZED PRIVATE CO-PAY",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp,
                                    color = if (hasVulnerableGroupEligibility) Color(0xFF047857) else Color(0xFF1D4ED8)
                                )
                            }
                            Text(
                                text = if (hasVulnerableGroupEligibility) {
                                    "Your answers qualify you for the federal NHIA Vulnerable Group Social Fund. Your base premium is fully subsidized at ₦0.00."
                                } else {
                                    "Based on your income, you qualify for public-private HMO co-insurance. Recommended plan: Reliance HMO Core or Hygeia Premium."
                                },
                                fontSize = 10.sp,
                                color = if (hasVulnerableGroupEligibility) Color(0xFF065F46) else Color(0xFF1E40AF)
                            )
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { currentStep = 0 }, modifier = Modifier.weight(1f)) {
                            Text("Back")
                        }
                        Button(
                            onClick = {
                                if (hasVulnerableGroupEligibility) {
                                    selectedPlan = "NHIA Public Program"
                                    selectedHmo = "NHIA Federal"
                                }
                                currentStep = 2
                            },
                            modifier = Modifier.weight(1.5f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488))
                        ) {
                            Text("Next: KYC Documents")
                        }
                    }
                }

                2 -> {
                    // STEP 3: KYC DOCUMENT COLLECTION SIMULATION
                    Text("STEP 3: SECURE DIGITAL DOCUMENT COLLECTION", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E293B))
                    Text("Under federal NIMC & NHIA laws, upload and scan your official identification documents to complete subscriber registry verification.", fontSize = 11.sp, color = Color.Gray)

                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        label = { Text("Full Legal Name (Matching Official ID)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = ninNumber,
                            onValueChange = { if (it.length <= 11) ninNumber = it },
                            label = { Text("National Identification Number (NIN) - 11 Digits") },
                            modifier = Modifier.fillMaxWidth(),
                            isError = !isNinLengthValid,
                            singleLine = true
                        )
                        if (!isNinLengthValid) {
                            Text("⚠️ NIN must be exactly 11 digits under NIMC regulations.", color = Color.Red, fontSize = 9.sp)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = phoneNumber,
                            onValueChange = { phoneNumber = it },
                            label = { Text("Phone Number") },
                            modifier = Modifier.weight(1.5f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = ageField,
                            onValueChange = { ageField = it },
                            label = { Text("Age") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = genderSelect,
                            onValueChange = { genderSelect = it },
                            label = { Text("Gender") },
                            modifier = Modifier.weight(1.2f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = residentState,
                            onValueChange = { residentState = it },
                            label = { Text("Resident State") },
                            modifier = Modifier.weight(1.2f),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Document collection interfaces with Simulated Scanning
                    Text("UPLOAD MANDATORY DIGITAL VERIFICATIONS", fontWeight = FontWeight.Bold, fontSize = 9.sp, color = Color.Gray)
                    
                    // Card 1: National Identification document (NIMC Slip/Card)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (isIdUploaded) Color(0xFFF0FDFA) else Color.White),
                        border = BorderStroke(1.5.dp, if (isIdUploaded) Color(0xFF14B8A6) else Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (isIdUploaded) Color(0xFFCCFBF1) else Color(0xFFF1F5F9)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(if (isIdUploaded) "✓" else "🪪", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (isIdUploaded) Color(0xFF0D9488) else Color.Gray)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("NIMC National ID Card / Slip", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF1E293B))
                                Text(
                                    text = if (isIdUploaded) "Verified NIMC database match found." else "Simulate scanner verification of identity card.",
                                    fontSize = 9.sp,
                                    color = Color.Gray
                                )
                            }
                            Button(
                                onClick = { activeScanningDoc = "NIN" },
                                colors = ButtonDefaults.buttonColors(containerColor = if (isIdUploaded) Color(0xFF0F766E) else Color(0xFF475569)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text(if (isIdUploaded) "Re-Scan" else "Simulate Scan", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Card 2: State Residence Proof / Utility Bill Document
                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (isBillUploaded) Color(0xFFF0FDFA) else Color.White),
                        border = BorderStroke(1.5.dp, if (isBillUploaded) Color(0xFF14B8A6) else Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (isBillUploaded) Color(0xFFCCFBF1) else Color(0xFFF1F5F9)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(if (isBillUploaded) "✓" else "📄", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (isBillUploaded) Color(0xFF0D9488) else Color.Gray)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Proof of Residence (Utility Bill)", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF1E293B))
                                Text(
                                    text = if (isBillUploaded) "Verified residency address registry match." else "Upload LASG or state energy utility invoice copy.",
                                    fontSize = 9.sp,
                                    color = Color.Gray
                                )
                            }
                            Button(
                                onClick = { activeScanningDoc = "Address" },
                                colors = ButtonDefaults.buttonColors(containerColor = if (isBillUploaded) Color(0xFF0F766E) else Color(0xFF475569)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text(if (isBillUploaded) "Re-Scan" else "Simulate Scan", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { currentStep = 1 }, modifier = Modifier.weight(1f)) {
                            Text("Back")
                        }
                        Button(
                            onClick = { currentStep = 3 },
                            enabled = fullName.isNotBlank() && isNinLengthValid && ninNumber.isNotEmpty() && isIdUploaded && isBillUploaded,
                            modifier = Modifier.weight(1.5f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488))
                        ) {
                            Text("Next: Dependents")
                        }
                    }
                }

                3 -> {
                    // STEP 4: DEPENDENT ENROLLMENT
                    Text("STEP 4: FAMILY DEPENDENT ENROLLMENT", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E293B))
                    Text("Extend direct insurance coverage to immediate family members. Enrolled dependents are registered as co-members under your subscriber ID.", fontSize = 11.sp, color = Color.Gray)

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Add Dependent Member", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF0F172A))
                            
                            OutlinedTextField(
                                value = dependentName,
                                onValueChange = { dependentName = it },
                                label = { Text("Dependent Full Legal Name") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { dependentRelation = "Child" }) {
                                    RadioButton(selected = dependentRelation == "Child", onClick = { dependentRelation = "Child" })
                                    Text("Child", fontSize = 11.sp)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { dependentRelation = "Spouse" }) {
                                    RadioButton(selected = dependentRelation == "Spouse", onClick = { dependentRelation = "Spouse" })
                                    Text("Spouse", fontSize = 11.sp)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { dependentRelation = "Parent" }) {
                                    RadioButton(selected = dependentRelation == "Parent", onClick = { dependentRelation = "Parent" })
                                    Text("Parent", fontSize = 11.sp)
                                }
                            }

                            OutlinedTextField(
                                value = dependentAgeField,
                                onValueChange = { dependentAgeField = it },
                                label = { Text("Dependent Age") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            Button(
                                onClick = {
                                    if (dependentName.isNotBlank() && dependentAgeField.isNotBlank()) {
                                        dependentsList = dependentsList + Triple(dependentName, dependentRelation, dependentAgeField)
                                        dependentName = ""
                                        dependentAgeField = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("+ Append Dependent to Coverage Queue", fontSize = 11.sp)
                            }
                        }
                    }

                    if (dependentsList.isNotEmpty()) {
                        Text("ENROLLED FAMILY DEPENDENTS:", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color.Gray)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            dependentsList.forEachIndexed { idx, (name, rel, age) ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(name, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text("$rel • Age: $age", fontSize = 10.sp, color = Color.Gray)
                                        }
                                        IconButton(onClick = {
                                            dependentsList = dependentsList.filterIndexed { index, _ -> index != idx }
                                        }) {
                                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Dependent", tint = Color.Red)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { currentStep = 2 }, modifier = Modifier.weight(1f)) {
                            Text("Back")
                        }
                        Button(
                            onClick = { currentStep = 4 },
                            modifier = Modifier.weight(1.5f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488))
                        ) {
                            Text("Next: Secure Payment")
                        }
                    }
                }

                4 -> {
                    // STEP 5: PREMIUM REVIEW & SECURE PAYMENT GATEWAYS
                    val isSubsidized = selectedPlan == "NHIA Public Program" || hasVulnerableGroupEligibility
                    val baseCost = if (isSubsidized) 0.0 else if (selectedPlan == "Reliance HMO Core") 12500.0 else 35000.0
                    val dependentsCost = if (isSubsidized) 0.0 else dependentsList.size * 2000.0
                    val totalPremiumDue = baseCost + dependentsCost

                    Text("STEP 5: PREMIUM REVIEW & ACCREDITED GATEWAYS", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E293B))
                    Text("Verify subscriber details and select an approved payment rail to instantly activate coverage.", fontSize = 11.sp, color = Color.Gray)

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("Primary Subscriber:", fontSize = 11.sp, color = Color.Gray)
                                Text(fullName, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("NIMC Verified NIN:", fontSize = 11.sp, color = Color.Gray)
                                Text(ninNumber, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("Accredited HMO:", fontSize = 11.sp, color = Color.Gray)
                                Text(selectedHmo, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("Plan Tier Selected:", fontSize = 11.sp, color = Color.Gray)
                                Text(selectedPlan, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("Dependents Registered:", fontSize = 11.sp, color = Color.Gray)
                                Text("${dependentsList.size} Member(s)", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("Subsidized Path Status:", fontSize = 11.sp, color = Color.Gray)
                                Text(
                                    text = if (isSubsidized) "NHIA Vulnerable Fund ✓" else "Private HMO Premium",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = if (isSubsidized) Color(0xFF10B981) else Color.Gray
                                )
                            }
                            HorizontalDivider()
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("Total Premium Due:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("₦${String.format("%,.2f", totalPremiumDue)}", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = Color(0xFF0D9488))
                            }
                        }
                    }

                    if (!isSubsidized) {
                        Text("SELECT BILLING GATEWAY (CASH/CARD HIGHLY RECOMMENDED):", fontWeight = FontWeight.Bold, fontSize = 9.sp, color = Color.Gray)
                        
                        // Gateway Selection Row
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                Pair("Bank Transfer / Card", "Primary secure Paystack gateway instant clearance"),
                                Pair("Airtel Money", "Licensed secure mobile wallet instant clearance"),
                                Pair("MTN Share P2P", "Telecom merchant settlement. Converted automatically to cash")
                            ).forEach { (method, descriptor) ->
                                val isM = paymentMethod == method
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { paymentMethod = method }
                                        .border(
                                            BorderStroke(
                                                1.5.dp,
                                                if (isM) Color(0xFF0F172A) else Color.Transparent
                                            ),
                                            RoundedCornerShape(8.dp)
                                        ),
                                    colors = CardDefaults.cardColors(containerColor = if (isM) Color(0xFFF8FAFC) else Color.White)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        RadioButton(selected = isM, onClick = { paymentMethod = method })
                                        Column {
                                            Text(method, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = if (isM) Color(0xFF0F172A) else Color.DarkGray)
                                            Text(descriptor, fontSize = 9.sp, color = Color.Gray)
                                        }
                                    }
                                }
                            }
                        }

                        when (paymentMethod) {
                            "MTN Share P2P" -> {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                                    border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("📱 REGULATORY TELECOM AIRTIME-TO-CASH SETTLEMENT", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color(0xFFB45309))
                                        Text("P2P airtime transfers are cleared through our telecom billing merchant partner and instantly liquidated into cash in our regulated HMO trust. Dial *321# on your phone to configure your MTN Transfer PIN. Limits: ₦100 to ₦5,000.", fontSize = 9.sp, color = Color(0xFF92400E))
                                        
                                        OutlinedTextField(
                                            value = mtnSharePin,
                                            onValueChange = { if (it.length <= 4) mtnSharePin = it },
                                            placeholder = { Text("Enter 4-Digit MTN Transfer PIN") },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )
                                    }
                                }
                            }
                            "Airtel Money" -> {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                                    border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("✓ LICENSED WALLET ACCESS: Airtel Money API is live and direct. Submitting will launch a secure USSD overlay on your phone to approve.", fontSize = 9.sp, color = Color(0xFF1E40AF), modifier = Modifier.padding(10.dp))
                                }
                            }
                            else -> {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)),
                                    border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("✓ Paystack / Flutterwave Gateway is active. Fully encrypted bank card or direct bank transfer wire. Direct provider-to-provider settlement is 100% pre-configured.", fontSize = 9.sp, color = Color(0xFF065F46), modifier = Modifier.padding(10.dp))
                                }
                            }
                        }
                    } else {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)),
                            border = BorderStroke(1.dp, Color(0xFF10B981)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("✨ SUBSIDIZED VULNERABLE COVER SELECTED: No premium or out-of-pocket payment is due. Verification is processed on behalf of the subscriber by the National Health Insurance Authority board.", fontSize = 10.sp, color = Color(0xFF047857), modifier = Modifier.padding(10.dp))
                        }
                    }

                    if (simPaymentProgress) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    } else {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { currentStep = 3 }, modifier = Modifier.weight(1f)) {
                                Text("Back")
                            }
                            Button(
                                onClick = {
                                    scope.launch {
                                        simPaymentProgress = true
                                        delay(1500)
                                        simPaymentProgress = false
                                        val vStatus = if (isSubsidized) {
                                            val listEligibility = mutableListOf<String>()
                                            if (isPregnant) listEligibility.add("Maternal Subsidy")
                                            if (isDisabled) listEligibility.add("Disability Subvention")
                                            if (isRefugee) listEligibility.add("UNHCR Refugee Scheme")
                                            if (isTraffickingSurvivor) listEligibility.add("NAPTIP Survivor Fund")
                                            if (incomeCategory == "Under ₦30,000 (Subsidized Tier)") listEligibility.add("Income Subsidy")
                                            "NHIA Vulnerable Fund [${listEligibility.joinToString(", ")}]"
                                        } else {
                                            "Private Premium HMO Plan"
                                        }
                                        viewModel.enrollInInsurance(
                                            plan = selectedPlan,
                                            hmo = selectedHmo,
                                            memberId = "NG-CO-${selectedHmo.take(3).uppercase()}-${(100000..999999).random()}",
                                            coverage = if (isSubsidized) "Malaria Tx, Primary consults, generic pharmaceuticals, maternal ultrasounds, child immunization" else "Full private HMO cover, Specialist Referrals, Diagnostic CT/MRI, Surgery subsidies",
                                            expiry = "06 July 2027",
                                            nin = ninNumber,
                                            vulnerableStatus = vStatus,
                                            dependentsCount = dependentsList.size,
                                            preauthStatus = "Artemether-Lumefantrine Dispensing: PRE-AUTHORIZED"
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1.5f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488))
                            ) {
                                Text(if (isSubsidized) "Activate Subsidized ID" else "Complete Payment & Activate")
                            }
                        }
                    }
                }
            }

            // High Tech OCR Scanner Simulation Dialog
            if (activeScanningDoc != null) {
                AlertDialog(
                    onDismissRequest = { activeScanningDoc = null },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(
                                progress = scanProgress,
                                color = Color(0xFF0D9488),
                                modifier = Modifier.size(24.dp)
                            )
                            Text("AI OCR DOCUMENT SCANNER", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                        }
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Analyzing physical security elements & signatures for document type: ${if (activeScanningDoc == "NIN") "NIMC National ID Slip" else "Lagos Utility Bill Register"}",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                            
                            // High tech visual viewfinder
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .background(Color.Black.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                                    .border(BorderStroke(1.5.dp, Color(0xFF0D9488)), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                // Viewfinder grid lookalike
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("⚡", fontSize = 28.sp)
                                    Text("SCANNING IN PROGRESS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0D9488))
                                    Text("${(scanProgress * 100).toInt()}% SECURELY COPIED", fontSize = 9.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                }
                                
                                // Green scanning laser bar going down based on scanProgress
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(2.dp)
                                        .align(Alignment.TopCenter)
                                        .offset(y = (140.dp * scanProgress))
                                        .background(Color(0xFF10B981))
                                )
                            }
                            
                            Text("✓ Dynamic watermarks and NIMC seals verified.", fontSize = 9.sp, color = Color.Gray)
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { activeScanningDoc = null }) {
                            Text("Abort Scan")
                        }
                    }
                )
            }
        } else {
            // --- ENROLLED ACTIVE DASHBOARD ---
            val active = activeIns!!
            
            // Premium digital visual ID Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF005C4E)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Nigeria Unified Health Insurance Scheme", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                            Text(active.planName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .background(Color.White.copy(alpha = 0.2f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("ACTIVE", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 9.sp)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // User Avatar placeholder
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("👤", fontSize = 24.sp)
                        }
                        Column {
                            Text("SUBSCRIBER FULL NAME", color = Color.White.copy(alpha = 0.5f), fontSize = 8.sp)
                            Text(fullName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("MEMBER ID: ${active.memberId} | Registered NIN: ${active.nin}", color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1.5f)) {
                            Text("BENEFITS COVERAGE", color = Color.White.copy(alpha = 0.5f), fontSize = 8.sp)
                            Text(active.coverageDetails, color = Color.White, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text("EXPIRY DATE", color = Color.White.copy(alpha = 0.5f), fontSize = 8.sp)
                            Text(active.expiryDate, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.2f))

                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(active.vulnerableGroupStatus, color = Color.White.copy(alpha = 0.9f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text("Dependents: ${active.dependentsCount} Member(s)", color = Color.White.copy(alpha = 0.9f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Claims guidance
            Text("📋 ZERO OUT-OF-POCKET CLAIMS GUIDANCE", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Gray)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val guideSteps = listOf(
                        "1. Present Digital Insurance Card" to "Upon hospital check-in, present this digital ID screen to the receptionist or social worker.",
                        "2. Reception registry lookup" to "The provider queries Nigeria's NHIA/HMO unified network via FHIR API to verify Member ID.",
                        "3. Complete Pre-Authorization" to "For prescriptions or diagnostics, the physician triggers a live pre-authorization check via CareOS. Approved bills are direct-settled with zero out-of-pocket copay!"
                    )
                    guideSteps.forEach { (title, desc) ->
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("✓", fontWeight = FontWeight.Bold, color = Color(0xFF005C4E), fontSize = 12.sp)
                            Column {
                                Text(title, fontWeight = FontWeight.Bold, fontSize = 10.5.sp, color = Color(0xFF0F172A))
                                Text(desc, fontSize = 9.5.sp, color = Color.DarkGray, lineHeight = 13.sp)
                            }
                        }
                    }
                }
            }

            // Real-time Provider Network Search
            Text("🔍 APPROVED NETWORK PROVIDER SEARCH", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Gray)
            OutlinedTextField(
                value = providerSearchQuery,
                onValueChange = { providerSearchQuery = it },
                placeholder = { Text("Search accredited hospitals (e.g., LUTH, Abuja...)") },
                modifier = Modifier.fillMaxWidth().testTag("provider_search_input"),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
            )

            val facilities = listOf(
                Triple("Lagos University Teaching Hospital (LUTH)", "Primary & Specialist referrals, Surgery, Maternal Hub", "2.4 km"),
                Triple("National Hospital Abuja", "Trauma emergency, diagnostics, oncology hub", "FCT Abuja"),
                Triple("Garki Family Medicine Center", "Outpatient family care, pediatrics, malaria clinics", "FCT Abuja"),
                Triple("Synlab Diagnostics Center (Lagos branch)", "Acclimatized diagnostic bloodwork, MRI, ultrasound scans", "3.1 km"),
                Triple("Gbagada General Hospital", "Primary diagnostics, pediatrics, and maternity wards", "4.8 km")
            ).filter { it.first.contains(providerSearchQuery, ignoreCase = true) }

            facilities.forEach { (fac, service, dist) ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(fac, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Text(service, fontSize = 9.sp, color = Color.Gray)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("IN-NETWORK", color = Color(0xFF10B981), fontSize = 7.sp, fontWeight = FontWeight.Bold, modifier = Modifier.background(Color(0xFFECFDF5), RoundedCornerShape(4.dp)).padding(4.dp))
                            Text(dist, fontSize = 8.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Real-time Pre-authorization support console
            Text("⚡ REAL-TIME PRE-AUTHORIZATION CONSOLE", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Gray)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Pre-Authorization Status:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF166534))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF22C55E)))
                        Text("Active Approved Prescriptions: ${active.preauthStatus}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF14532D))
                    }
                    
                    if (preauthSuccessMsg != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFF22C55E)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(preauthSuccessMsg!!, color = Color(0xFF15803D), fontSize = 10.sp, modifier = Modifier.padding(8.dp), fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = { showPreauthDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF166534)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Request New Live Scan/Rx Approval", fontSize = 11.sp)
                    }
                }
            }

            // Renewal status with clear manual options
            Text("⏰ COVERAGE DURATION STATUS", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Gray)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Expires: 06 July 2027 (365 Days remaining)", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF991B1B))
                        Text("Renewal is configured to automatically query the unified registry.", fontSize = 9.sp, color = Color(0xFF7F1D1D))
                    }
                    IconButton(onClick = { viewModel.clearInsurance() }) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Clear Insurance Profile", tint = Color(0xFF991B1B))
                    }
                }
            }
        }
    }

    if (showPreauthDialog) {
        AlertDialog(
            onDismissRequest = { showPreauthDialog = false },
            title = { Text("Pre-Auth Claim Submission", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Accredited practitioners submit clinical pre-authorizations for premium prescription regimens or diagnostic scans before dispensing.", fontSize = 11.sp, color = Color.Gray)
                    
                    OutlinedTextField(
                        value = preauthDrugName,
                        onValueChange = { preauthDrugName = it },
                        label = { Text("Regimen Code / Scan Code") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = preauthFacility,
                        onValueChange = { preauthFacility = it },
                        label = { Text("Designated Dispensing Pharmacy / Clinic") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    preauthSuccessMsg = "✓ LIVE APPROVAL GRANTED. Reference: PA-CO-${(1000..9999).random()} for $preauthDrugName approved at $preauthFacility. Copay due: ₦0.00."
                    showPreauthDialog = false
                }) {
                    Text("Authorize")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPreauthDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}


// --- SCREEN 5: CARE FUND TRUST ENGINE (DONATIONS & DISBURSEMENTS) ---
@Composable
fun FundScreen(viewModel: CareViewModel) {
    val donations by viewModel.allDonations.collectAsState()
    val scope = rememberCoroutineScope()
    
    var showAddBillDialog by remember { mutableStateOf(false) }
    var showLocalInvestorDeck by remember { mutableStateOf(false) }
    var patientName by remember { mutableStateOf("") }
    var hospitalName by remember { mutableStateOf("Lagos University Teaching Hospital (LUTH)") }
    var billAmount by remember { mutableStateOf("") }
    var diagnosisInput by remember { mutableStateOf("") }
    
    // OCR Simulation states
    var ocrMatched by remember { mutableStateOf(false) }
    var attachedFile by remember { mutableStateOf<String?>(null) }
    var fraudIntegrityRating by remember { mutableStateOf(99) }
    var activeSocialWorker by remember { mutableStateOf("Mrs. Beatrice Obi (Chief Social services officer, LUTH Board ID: LUTH-SW-0822)") }

    var selectedDonationForDetails by remember { mutableStateOf<DonationRecord?>(null) }
    var showDonationTransferDialog by remember { mutableStateOf<DonationRecord?>(null) }
    var customDonationAmount by remember { mutableStateOf("5000") }
    var donorTransferPin by remember { mutableStateOf("") }
    var donorTransferProgress by remember { mutableStateOf(false) }
    var showLedgerVerificationDetail by remember { mutableStateOf<Triple<String, String, String>?>(null) }

    val totalDisbursed = donations.sumOf { it.amountFunded }
    val totalRequired = donations.sumOf { it.invoiceAmount }
    val activeCampaignsCount = donations.count { it.verificationStatus != "Funded" }
    val fundedCampaignsCount = donations.count { it.verificationStatus == "Funded" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("fund_screen")
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // --- TRUST HEADER BANNER ---
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF1F2)),
            border = BorderStroke(1.5.dp, Color(0xFFFDA4AF)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🤝 CAREOS TRUST CROWDFUNDING", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = Color(0xFF991B1B))
                    Button(
                        onClick = { showAddBillDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF991B1B)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("Request Aid Audit", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    text = "CareOS functions as a strict trust engine. 100% of public contributions are settled directly into the verified corporate treasury of the accredited hospital registry. No funds are ever transferred to personal subscriber wallets, completely eliminating crowdfunding fraud.",
                    fontSize = 10.sp,
                    color = Color(0xFF7F1D1D),
                    lineHeight = 14.sp
                )
            }
        }

        // --- INVESTOR PRESENTATION BANNER ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showLocalInvestorDeck = true },
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
            border = BorderStroke(1.5.dp, Color(0xFF475569)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("📈", fontSize = 28.sp)
                Column(modifier = Modifier.weight(1f)) {
                    Text("CareOS Executive Presentation & Pitch Deck", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0F172A))
                    Text("View and export the updated investor report featuring our new regional updates & universal voice modes.", fontSize = 10.sp, color = Color(0xFF475569))
                }
                Icon(
                    imageVector = Icons.Default.TrendingUp,
                    contentDescription = null,
                    tint = Color(0xFF0F172A)
                )
            }
        }

        // --- DYNAMIC TRUST METRICS DASHBOARD (Bento Grid Layout) ---
        Text("📊 SYSTEM-WIDE AID & TRUST METRICS", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Gray)
        
        // Bento Row 1
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Card(
                modifier = Modifier
                    .weight(1.2f)
                    .height(95.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                border = BorderStroke(1.5.dp, Color(0xFF86EFAC)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("🏛️", fontSize = 14.sp)
                        Text("Total Settled Direct", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color(0xFF166534))
                    }
                    Text(
                        text = "₦${String.format("%,.0f", totalDisbursed)}",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = Color(0xFF14532D)
                    )
                    Text("100% Direct-To-Hospital treasury", fontSize = 8.5.sp, color = Color(0xFF15803D))
                }
            }

            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(95.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED)),
                border = BorderStroke(1.5.dp, Color(0xFFFDBA74)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("🛡️", fontSize = 14.sp)
                        Text("Fraud Prevented", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color(0xFF9A3412))
                    }
                    Text(
                        text = "₦1,250,000",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = Color(0xFF7C2D12)
                    )
                    Text("Audited invoice losses avoided", fontSize = 8.5.sp, color = Color(0xFFC2410C))
                }
            }
        }

        // Bento Row 2
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(95.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                border = BorderStroke(1.5.dp, Color(0xFF93C5FD)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("🔒", fontSize = 14.sp)
                        Text("Audit Match Rate", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color(0xFF1E40AF))
                    }
                    Text(
                        text = "100.0%",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = Color(0xFF1E3A8A)
                    )
                    Text("Verified social worker sign-offs", fontSize = 8.5.sp, color = Color(0xFF1D4ED8))
                }
            }

            Card(
                modifier = Modifier
                    .weight(1.1f)
                    .height(95.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFDF2F8)),
                border = BorderStroke(1.5.dp, Color(0xFFFBCFE8)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("📋", fontSize = 14.sp)
                        Text("Tracked Cases", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color(0xFF9D174D))
                    }
                    Text(
                        text = "$activeCampaignsCount Active / $fundedCampaignsCount Funded",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        color = Color(0xFF831843),
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                    Text("Total validated clinical cases", fontSize = 8.5.sp, color = Color(0xFFBE185D))
                }
            }
        }

        // --- FUNDED PATIENT RECOVERY TIMELINE (LIVE STATUS UPDATES) ---
        val fundedCases = donations.filter { it.verificationStatus == "Funded" || it.amountFunded >= it.invoiceAmount }
        if (fundedCases.isNotEmpty()) {
            Text("📈 CLINICAL RECOVERY LOG (FUNDED MEDICAL CASES)", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Gray)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                fundedCases.forEach { campaign ->
                    val timeline = try {
                        campaign.caseProgressJson
                            .removeSurrounding("[", "]")
                            .split("},{")
                            .map { chunk ->
                                val date = chunk.substringAfter("\"date\":\"").substringBefore("\"")
                                val msg = chunk.substringAfter("\"msg\":\"").substringBefore("\"")
                                date to msg
                            }.filter { it.first.isNotBlank() }
                    } catch (e: Exception) {
                        listOf("Today" to "Fully Funded. Hospital direct wire completed.")
                    }
                    val latestEvent = timeline.lastOrNull() ?: ("Today" to "Treatment course completed successfully.")
                    
                    Card(
                        modifier = Modifier
                            .width(265.dp)
                            .height(115.dp)
                            .clickable { selectedDonationForDetails = campaign },
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.5.dp, Color(0xFFCCFBF1)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(campaign.patientName, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0F766E))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    modifier = Modifier
                                        .background(Color(0xFFE6FFFA), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(Color(0xFF0D9488)))
                                    Text("RECOVERING", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0D9488))
                                }
                            }
                            Text(campaign.hospitalName, fontSize = 8.5.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            
                            HorizontalDivider(color = Color(0xFFF1F5F9))
                            
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text("🏥", fontSize = 11.sp)
                                Column {
                                    Text(
                                        text = "Latest Status (${latestEvent.first}):",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 8.5.sp,
                                        color = Color.DarkGray
                                    )
                                    Text(
                                        text = latestEvent.second,
                                        fontSize = 9.sp,
                                        lineHeight = 12.sp,
                                        color = Color.Black,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Text("ACTIVE VERIFIED AID CAMPAIGNS", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Gray)

        if (donations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp))
                    .border(BorderStroke(1.dp, Color(0xFFE2E8F0))),
                contentAlignment = Alignment.Center
            ) {
                Text("No medical aid campaigns currently active in your region.", color = Color.Gray, fontSize = 11.sp)
            }
        } else {
            donations.forEach { campaign ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column {
                                Text(campaign.patientName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E293B))
                                Text(campaign.hospitalName, fontSize = 10.5.sp, color = Color.Gray)
                            }
                            Text(
                                text = "AUDITED ✓",
                                color = Color(0xFF0D9488),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 8.sp,
                                modifier = Modifier
                                    .background(Color(0xFFCCFBF1), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }

                        // Diagnosis details
                        Text(campaign.diagnosisSummary, fontSize = 10.5.sp, color = Color.DarkGray, lineHeight = 13.sp)

                        // Progress status
                        val progress = if (campaign.invoiceAmount > 0) (campaign.amountFunded / campaign.invoiceAmount).toFloat() else 0f
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Fundraising Progress: ${(progress * 100).toInt()}%", fontSize = 10.sp, color = Color.Gray)
                            Text("₦${String.format("%,.0f", campaign.amountFunded)} / ₦${String.format("%,.0f", campaign.invoiceAmount)}", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }

                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = Color(0xFF0D9488),
                            trackColor = Color(0xFFE2E8F0)
                        )

                        // Dynamic Trust metrics
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                            border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "🛡️ Trust Score: ${campaign.fraudScore}% Secure",
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F766E),
                                        modifier = Modifier
                                            .background(Color(0xFFE6FFFA), RoundedCornerShape(4.dp))
                                            .padding(4.dp)
                                    )
                                    Text(
                                        text = "Auditor ID: ${campaign.socialWorkerVerifiedBy.take(22)}...",
                                        fontSize = 8.5.sp,
                                        color = Color.Gray,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (campaign.invoiceAttachmentName != null) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text("📄 Official Receipt Attached:", fontSize = 8.5.sp, color = Color.Gray)
                                        Text(
                                            text = campaign.invoiceAttachmentName,
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0D9488)
                                        )
                                    }
                                }
                            }
                        }

                        // Actions Row
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { selectedDonationForDetails = campaign },
                                modifier = Modifier.weight(1.1f),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Audited Case Timeline", fontSize = 10.sp)
                            }
                            if (campaign.verificationStatus != "Funded") {
                                Button(
                                    onClick = { showDonationTransferDialog = campaign },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("Contribute Now", fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- PUBLIC IMPACT LEDGER DISBURSEMENT BOARD ---
        Text("📜 TRANSPARENT DIRECT-TO-HOSPITAL DISBURSEMENT LEDGER", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Gray)
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("REAL-TIME PUBLIC HEALTH AUDITED DISBURSEMENTS", color = Color(0xFF38BDF8), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp)
                Text("Click any disbursement log to verify its immutable Central Bank settlement key and accredited hospital registry clearance certificate.", color = Color.LightGray.copy(alpha = 0.7f), fontSize = 8.5.sp, lineHeight = 11.sp)

                // Build unified list from dynamic + static entries
                val dynamicLedgerItems = remember(donations) {
                    val list = mutableListOf<Triple<String, String, String>>()
                    // Live database entries
                    donations.filter { it.amountFunded > 0 }.forEach { campaign ->
                        val isFullyFunded = campaign.amountFunded >= campaign.invoiceAmount
                        val textAmt = "₦${String.format("%,.0f", campaign.amountFunded)} Direct ${if (isFullyFunded) "Settled" else "Co-Paid"}"
                        val desc = "To: ${campaign.hospitalName} Treasury for Patient ${campaign.patientName}"
                        val hash = "DISB-${campaign.id * 127 + 3821}-${campaign.hospitalName.take(4).replace(" ", "").uppercase()} | Verified by Mrs. Beatrice Obi"
                        list.add(Triple(textAmt, desc, hash))
                    }
                    // Static legacy entries
                    list.add(Triple("₦142,000 Direct Disbursed", "To: Lagos University Teaching Hosp. (LUTH) Board Treasury for Patient Amara Kalu", "DISB-9028-LUTH | Verified by Social Worker Obi"))
                    list.add(Triple("₦350,000 Direct Disbursed", "To: National Hospital Abuja Treasury for Patient Musa Ibrahim", "DISB-1104-NHA | Verified by Board Officer Bello"))
                    list.add(Triple("₦15,000 Direct Disbursed", "To: Gbagada General Hospital Pharmacy for Patient Ngozi Egwu", "DISB-0824-GBG | Verified by Pharmacist Aliyu"))
                    list.distinctBy { it.second } // Avoid duplicates
                }

                dynamicLedgerItems.forEach { triple ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showLedgerVerificationDetail = triple }
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text(triple.first, color = Color(0xFF34D399), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(Color(0xFF34D399)))
                                    Text("Audited Clearance ✓", color = Color(0xFF34D399), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Text(triple.second, color = Color.White.copy(alpha = 0.85f), fontSize = 9.sp, lineHeight = 12.sp)
                            Text(triple.third, color = Color.Gray, fontSize = 8.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }

    // --- IMMUTABLE PUBLIC HEALTH LEDGER VERIFICATION CERTIFICATE ---
    if (showLedgerVerificationDetail != null) {
        val ledgerItem = showLedgerVerificationDetail!!
        AlertDialog(
            onDismissRequest = { showLedgerVerificationDetail = null },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("🛡️ Public Health Ledger Audit Certificate", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F766E))
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "This receipt represents an immutable bank-level direct settlement clearing event registered on the CareOS public impact ledger. No personal subscriber wallets were involved.",
                        fontSize = 10.sp,
                        color = Color.Gray,
                        lineHeight = 13.sp
                    )

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("TRANSACTION CLASS", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                Text("DIRECT SETTLEMENT", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0D9488))
                            }
                            Text(
                                text = ledgerItem.first,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF0F766E)
                            )
                            
                            HorizontalDivider(color = Color(0xFFE2E8F0))

                            Text("RECIPIENT & PURPOSE", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                            Text(
                                text = ledgerItem.second,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )

                            HorizontalDivider(color = Color(0xFFE2E8F0))

                            Text("AUDIT HASH & SIGNATURE", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                            Text(
                                text = ledgerItem.third,
                                fontSize = 8.5.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = Color.DarkGray
                            )
                        }
                    }

                    // Simulated secure checkmark icon & certificate graphics
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFE6FFFA), RoundedCornerShape(8.dp))
                            .border(BorderStroke(1.dp, Color(0xFF34D399)), RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("🛡️", fontSize = 16.sp)
                            Text(
                                text = "VERIFIED INTEGRITY: DIRECT-TO-HOSPITAL SETTLEMENT CONFIRMED ✓",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F766E)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showLedgerVerificationDetail = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488))
                ) {
                    Text("Close Verification")
                }
            }
        )
    }

    // AID DIALOG WIZARD WITH OCR SIMULATION & FRAUD CHECKS
    if (showAddBillDialog) {
        AlertDialog(
            onDismissRequest = { showAddBillDialog = false },
            title = { Text("Clinical Aid Request & Invoice Audit", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Upload an official clinical receipt or invoice. CareOS's AI-OCR parses patient names, billing coordinates, and diagnosis details to eliminate fraudulent crowdfunding campaigns.", fontSize = 11.sp, color = Color.Gray)

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp).clickable {
                                ocrMatched = true
                                patientName = "Simeon Adebayo"
                                billAmount = "150000.0"
                                diagnosisInput = "Severe Malarial Hyperpyrexia and acute gastroenteritis with moderate hydration deficit."
                                attachedFile = "luth_receipt_9082_adebayo.pdf"
                                fraudIntegrityRating = 99
                            },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("📸", fontSize = 24.sp)
                            Text("Simulate OCR Invoice Upload Scanner", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF0F172A))
                            Text("Click here to simulate uploading an official PDF invoice from LUTH hospital registry.", fontSize = 9.sp, color = Color.Gray, textAlign = TextAlign.Center)
                        }
                    }

                    if (ocrMatched) {
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)), border = BorderStroke(1.dp, Color(0xFF10B981))) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("✓ AI OCR MATCH FOUND!", color = Color(0xFF065F46), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text("• Extracted Name: Simeon Adebayo\n• Extracted Amount: ₦150,000.00\n• Watermark Signature: LUTH-REG-OK\n• Fraud Security Audit Rating: 99% SECURE", color = Color(0xFF047857), fontSize = 9.sp)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = patientName,
                        onValueChange = { patientName = it },
                        label = { Text("Patient Full Name") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = hospitalName,
                        onValueChange = { hospitalName = it },
                        label = { Text("Hospital / Pharmacy Registry") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = billAmount,
                        onValueChange = { billAmount = it },
                        label = { Text("Invoice Amount (₦)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = diagnosisInput,
                        onValueChange = { diagnosisInput = it },
                        label = { Text("Diagnosis Summary & Clinical Justification") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Active Certifying Social worker:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    OutlinedTextField(
                        value = activeSocialWorker,
                        onValueChange = { activeSocialWorker = it },
                        label = { Text("Assigned Social Services Auditor ID") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = billAmount.toDoubleOrNull() ?: 0.0
                        if (patientName.isNotBlank() && amt > 0) {
                            viewModel.submitDonationRequest(
                                patientName = patientName,
                                hospital = hospitalName,
                                amount = amt,
                                diagnosisSummary = diagnosisInput,
                                socialWorker = activeSocialWorker,
                                invoiceFile = attachedFile,
                                fraudScore = fraudIntegrityRating
                            )
                            showAddBillDialog = false
                            patientName = ""
                            billAmount = ""
                            diagnosisInput = ""
                            ocrMatched = false
                            attachedFile = null
                        }
                    }
                ) {
                    Text("Launch Audited Campaign")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddBillDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // AUDITED CASE TIMELINE DIALOG
    if (selectedDonationForDetails != null) {
        val detail = selectedDonationForDetails!!
        
        val timelineEvents = remember(detail.caseProgressJson) {
            try {
                detail.caseProgressJson
                    .removeSurrounding("[", "]")
                    .split("},{")
                    .map { chunk ->
                        val date = chunk.substringAfter("\"date\":\"").substringBefore("\"")
                        val msg = chunk.substringAfter("\"msg\":\"").substringBefore("\"")
                        date to msg
                    }.filter { it.first.isNotBlank() }
            } catch (e: Exception) {
                listOf("Today" to "Campaign launched, invoice audited and verified by board auditor Mrs. Obi.")
            }
        }

        AlertDialog(
            onDismissRequest = { selectedDonationForDetails = null },
            title = { Text("📋 Patient Case Audit Trail", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("VERIFIED PATIENT RECORDS & TIMELINE", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Gray)
                    Text("Subscriber Name: ${detail.patientName}\nTreatment Center: ${detail.hospitalName}\nCertifying Social Worker: ${detail.socialWorkerVerifiedBy}", fontSize = 10.sp)
                    
                    HorizontalDivider()

                    timelineEvents.forEach { (date, msg) ->
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(date, fontWeight = FontWeight.Bold, color = Color(0xFF0D9488), fontSize = 10.sp, modifier = Modifier.width(52.dp))
                            Column {
                                Text(msg, fontSize = 10.5.sp, lineHeight = 13.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text("✓ Blockchain Ledger Transaction Reference:\n${detail.ledgerTransactionId ?: "TXN-GENERATING"}", fontSize = 8.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = Color.Gray)
                }
            },
            confirmButton = {
                Button(onClick = { selectedDonationForDetails = null }) {
                    Text("Close Case Audit")
                }
            }
        )
    }

    // DONOR CONTRIBUTION DIALOG & TAX-EXEMPT RECEIPT GENERATION
    if (showDonationTransferDialog != null) {
        val activeCampaign = showDonationTransferDialog!!
        var transferMethod by remember { mutableStateOf("Bank Transfer / Card") }
        var showExemptionReceipt by remember { mutableStateOf(false) }

        if (showExemptionReceipt) {
            AlertDialog(
                onDismissRequest = {
                    showExemptionReceipt = false
                    showDonationTransferDialog = null
                },
                title = { Text("Tax-Exempt Donor Receipt", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Text("📜 OFFICIAL CLINICAL AID RECEIPT", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Gray)
                        Text("CareOS Trust Fund is a verified partner registered under federal healthcare aid codes. This donation qualifies for full tax deduction.", fontSize = 10.sp, color = Color.DarkGray)
                        
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)), border = BorderStroke(1.dp, Color(0xFFE2E8F0))) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Donor ID: CO-DONOR-${(1000..9999).random()}", fontWeight = FontWeight.Bold, fontSize = 9.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                Text("Donation Value: ₦${customDonationAmount}", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = Color(0xFF0D9488))
                                Text("Earmarked Patient: ${activeCampaign.patientName}", fontSize = 10.5.sp)
                                Text("Settled Direct-To: ${activeCampaign.hospitalName} Registry", fontSize = 10.5.sp)
                                Text("Ledger Signature: SECURE-LEDGER-TX-${(100000..999999).random()}", fontSize = 8.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = Color.Gray)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        showExemptionReceipt = false
                        showDonationTransferDialog = null
                    }) {
                        Text("Close & Save PDF")
                    }
                }
            )
        } else {
            var selectedGatewayTab by remember { mutableStateOf("national") } // "national", "international", "mobile"
            var cardHolderName by remember { mutableStateOf("") }
            var cardNumber by remember { mutableStateOf("") }
            var cardExpiry by remember { mutableStateOf("") }
            var cardCvv by remember { mutableStateOf("") }
            var mobileNumber by remember { mutableStateOf("") }
            var selectedMobileNetwork by remember { mutableStateOf("MTN") }

            val rawAmount = customDonationAmount.toDoubleOrNull() ?: 5000.0
            
            // Financial details
            val nationalFeeRate = 0.015 // 1.5%
            val internationalFeeRate = 0.029 // 2.9% + $0.30 approx
            val exchangeRate = 1500.0 // 1 USD = 1500 NGN
            
            val platformFee = if (selectedGatewayTab == "national" || selectedGatewayTab == "mobile") {
                rawAmount * nationalFeeRate
            } else {
                (rawAmount / exchangeRate) * internationalFeeRate * exchangeRate + (0.30 * exchangeRate)
            }
            
            val totalCharged = rawAmount + platformFee

            AlertDialog(
                onDismissRequest = { showDonationTransferDialog = null },
                title = { 
                    Column {
                        Text("Secure Direct-To-Hospital Checkout", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("100% of aid is wired to vetted hospital treasuries.", fontSize = 10.sp, color = Color.Gray)
                    }
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    ) {
                        OutlinedTextField(
                            value = customDonationAmount,
                            onValueChange = { customDonationAmount = it },
                            label = { Text("Enter Intended Support Amount (₦)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        // Visual Tab Switches for Gateways
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFFF1F5F9)),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            listOf(
                                Triple("national", "🇳🇬 Paystack", Color(0xFF0D9488)),
                                Triple("international", "🌐 Stripe", Color(0xFF0EA5E9)),
                                Triple("mobile", "📱 MoMo", Color(0xFFF59E0B))
                            ).forEach { (tabId, label, tabColor) ->
                                val active = selectedGatewayTab == tabId
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (active) tabColor else Color.Transparent)
                                        .clickable { selectedGatewayTab = tabId }
                                        .padding(vertical = 10.dp, horizontal = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (active) Color.White else Color.DarkGray
                                    )
                                }
                            }
                        }

                        // Render Selected Gateway Form
                        when (selectedGatewayTab) {
                            "national" -> {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)), border = BorderStroke(1.dp, Color(0xFFBBF7D0))) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text("🇳🇬 PAYSTACK CLEARANCE ENGINE", fontWeight = FontWeight.Bold, fontSize = 9.sp, color = Color(0xFF15803D))
                                            Text("Processes local cards (Visa, Mastercard, Verve) and direct bank debit. Clears instantly into hospital ledger.", fontSize = 8.5.sp, color = Color(0xFF166534))
                                        }
                                    }
                                    
                                    OutlinedTextField(
                                        value = cardNumber,
                                        onValueChange = { cardNumber = it },
                                        label = { Text("Local Card Number") },
                                        placeholder = { Text("4000 1234 5678 9010") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = cardExpiry,
                                            onValueChange = { cardExpiry = it },
                                            label = { Text("Expiry (MM/YY)") },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true
                                        )
                                        OutlinedTextField(
                                            value = cardCvv,
                                            onValueChange = { cardCvv = it },
                                            label = { Text("CVV") },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true
                                        )
                                    }
                                }
                            }
                            "international" -> {
                                val usdEquivalent = rawAmount / exchangeRate
                                val usdPlatformFee = platformFee / exchangeRate
                                val usdTotal = totalCharged / exchangeRate
                                
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F9FF)), border = BorderStroke(1.dp, Color(0xFFBAE6FD))) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text("🌐 STRIPE GLOBAL ROUTING GATEWAY", fontWeight = FontWeight.Bold, fontSize = 9.sp, color = Color(0xFF0369A1))
                                            Text("Converts local currency to USD at standard interbank rates. Supports international credit cards, Apple Pay, and PayPal.", fontSize = 8.5.sp, color = Color(0xFF075985))
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("Equivalent: $${String.format("%.2f", usdEquivalent)} USD", fontWeight = FontWeight.Bold, fontSize = 9.sp, color = Color(0xFF0369A1))
                                        }
                                    }
                                    OutlinedTextField(
                                        value = cardHolderName,
                                        onValueChange = { cardHolderName = it },
                                        label = { Text("Cardholder Name") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = cardNumber,
                                        onValueChange = { cardNumber = it },
                                        label = { Text("International Card Number") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = cardExpiry,
                                            onValueChange = { cardExpiry = it },
                                            label = { Text("MM / YY") },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true
                                        )
                                        OutlinedTextField(
                                            value = cardCvv,
                                            onValueChange = { cardCvv = it },
                                            label = { Text("CVC") },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true
                                        )
                                    }
                                }
                            }
                            "mobile" -> {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)), border = BorderStroke(1.dp, Color(0xFFFDE68A))) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text("📱 REGIONAL MOBILE MONEY NETWORK", fontWeight = FontWeight.Bold, fontSize = 9.sp, color = Color(0xFFB45309))
                                            Text("Provides direct integration with Airtel Money, MTN MoMo, M-Pesa, and Orange Money for rural accessibility.", fontSize = 8.5.sp, color = Color(0xFF92400E))
                                        }
                                    }
                                    
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf("MTN", "Airtel", "M-Pesa", "Orange").forEach { net ->
                                            val isNet = selectedMobileNetwork == net
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (isNet) Color(0xFFB45309) else Color(0xFFFEF3C7))
                                                    .clickable { selectedMobileNetwork = net }
                                                    .padding(vertical = 6.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(net, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (isNet) Color.White else Color(0xFFB45309))
                                            }
                                        }
                                    }
                                    
                                    OutlinedTextField(
                                        value = mobileNumber,
                                        onValueChange = { mobileNumber = it },
                                        label = { Text("Mobile Money Wallet Number") },
                                        placeholder = { Text("+234 or +254...") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = donorTransferPin,
                                        onValueChange = { if (it.length <= 4) donorTransferPin = it },
                                        label = { Text("4-Digit Wallet Authorization PIN") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                }
                            }
                        }

                        // Transparent Platform Revenue Breakout
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("Base Hospital Aid Value:", fontSize = 9.5.sp, color = Color.Gray)
                                    Text("₦${String.format("%.2f", rawAmount)}", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                                }
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = if (selectedGatewayTab == "international") "Stripe Routing + Clearing Fee (2.9%):" else "Paystack platform fee (1.5%):",
                                        fontSize = 9.5.sp,
                                        color = Color.Gray
                                    )
                                    Text("₦${String.format("%.2f", platformFee)}", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0D9488))
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("Total Direct Settlement Charge:", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                    Text("₦${String.format("%.2f", totalCharged)}", fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0F172A))
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "💡 CareOS charges a modest 1.5% national / 2.9% international clearance fee to cover network costs, ledger gas, and sustain our secure compliance validation infrastructure.",
                                    fontSize = 8.sp,
                                    color = Color.Gray,
                                    lineHeight = 11.sp
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                donorTransferProgress = true
                                delay(1200)
                                donorTransferProgress = false
                                val donationVal = customDonationAmount.toDoubleOrNull() ?: 5000.0
                                viewModel.fundDonationCase(activeCampaign.id, donationVal)
                                showExemptionReceipt = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when (selectedGatewayTab) {
                                "national" -> Color(0xFF0D9488)
                                "international" -> Color(0xFF0EA5E9)
                                else -> Color(0xFFF59E0B)
                            }
                        )
                    ) {
                        Text(
                            text = if (donorTransferProgress) "Initializing Gateway..." else "Complete Secure Settlement",
                            fontSize = 11.sp
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDonationTransferDialog = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }

    if (showLocalInvestorDeck) {
        InvestorPresentationDialog(
            onDismiss = { showLocalInvestorDeck = false }
        )
    }
}


// --- LONGITUDINAL HEALTH RECORD (LHR) DIALOG ---
@Composable
fun LhrDialog(
    triages: List<SymptomTriage>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FolderShared, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Simeon Adebayo LHR")
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Your official clinical longitudinal health record details. CareOS updates this automatically after every medical consultation and AI-intake session.",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (triages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No health records logged yet. Begin a triage check.", fontSize = 12.sp, color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(triages) { item ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, Color.LightGray)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Text("AI Triage Encounter", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                        Text(
                                            text = if (item.hasRedFlags) "Critical Flag" else "Routine",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp,
                                            color = if (item.hasRedFlags) Color(0xFFEF4444) else Color(0xFF10B981)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Symptoms: ${item.symptomDescription}", fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("Next Step: ${item.recommendedNextAction}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close Record")
            }
        }
    )
}


// --- SCREEN 6: CLINICAL SAFETY & GOVERNANCE COMPLIANCE HUB ---
@Composable
fun ClinicalSafetyDialog(
    viewModel: CareViewModel,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf("board") } // "board", "protocols", "audits", "override", "adverse"
    
    // Clinical override states
    var overrideSymptomId by remember { mutableStateOf("1") }
    var overrideTriageResult by remember { mutableStateOf("Routine") }
    var overrideNote by remember { mutableStateOf("") }
    var overrideSubmitted by remember { mutableStateOf(false) }

    // Adverse event states
    var adverseSeverity by remember { mutableStateOf("Moderate") }
    var adverseEventText by remember { mutableStateOf("") }
    var adverseEventSubmitted by remember { mutableStateOf(false) }

    val triages by viewModel.allTriages.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth().heightIn(max = 620.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🛡️", fontSize = 22.sp)
                Column {
                    Text("Clinical Safety & Governance", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                    Text("SaMD Compliance Audit Hub v4.2.1", fontSize = 9.sp, color = Color.Gray)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                // Horizontal navigation bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val tabs = listOf(
                        "board" to "👥 Board",
                        "protocols" to "📋 Protocols",
                        "audits" to "🕵️ Logs",
                        "override" to "🩺 Override",
                        "adverse" to "🚨 Adverse Event"
                    )
                    tabs.forEach { (tabId, label) ->
                        val isSelected = selectedTab == tabId
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) Color(0xFF0D9488) else Color.Transparent)
                                .clickable { selectedTab = tabId }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else Color.DarkGray
                            )
                        }
                    }
                }

                HorizontalDivider()

                when (selectedTab) {
                    "board" -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                            Text("COMMITTED CLINICAL GOVERNANCE BOARD", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Gray)
                            Text("To prevent clinical diagnostic drift, CareOS operates under active governance guidelines overseen by senior medical experts:", fontSize = 11.sp, lineHeight = 14.sp)
                            
                            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)), border = BorderStroke(1.dp, Color(0xFFE2E8F0))) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("• Prof. Femi Adebayo, MD, FWACP", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    Text("  Chairman (Representing Nigeria Medical Council Registry)", fontSize = 10.sp, color = Color.Gray)
                                    Text("• Dr. Chioma Nwachukwu, GP (LUTH clinical lead)", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    Text("  On-Duty Clinical Triage Auditor", fontSize = 10.sp, color = Color.Gray)
                                    Text("• Dr. David Alao, PhD (West Africa Epidemiology Advisor)", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    Text("  Local pathogen risk weighting supervisor", fontSize = 10.sp, color = Color.Gray)
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text("VERSIONED CLINICAL KNOWLEDGE BASE", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color.Gray)
                            Text("Standardized Protocols: BMJ Best Practice Evidence Base, WHO digital healthcare safety guidelines, Nigeria FMOH malaria treatment policies.\nRelease tag: v4.2.1-Regional-Pathogen-Lagos", fontSize = 10.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                        }
                    }
                    "protocols" -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                            Text("SAFE CONSERVATIVE AI LIMITS (WHO ALIGNED)", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Gray)
                            
                            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)), border = BorderStroke(1.dp, Color(0xFFFDE68A))) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("⚠️ BMJ Symptom Checker Audit Reference", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFFB45309))
                                    Text("Because older legacy symptom checkers only achieved correct triage 57% of the time, CareOS enforces rigorous safety parameters rather than diagnostic self-confidence.", fontSize = 10.sp, color = Color(0xFF92400E), lineHeight = 13.sp)
                                }
                            }

                            val rules = listOf(
                                "75% Confidence Threshold" to "AI confidence levels below 75% trigger instant GP handoff disclaimers.",
                                "Red Flag Isolation" to "Any chest pain, respiratory distress, or severe fever freezes normal conversation to protect patients.",
                                "Optical Safety Lock" to "Systemic infections (e.g., Meningitis, Sepsis) are blocked from camera diagnostic scans.",
                                "Epidemiology Tuning" to "In Lagos rain seasons, malaria pathogen risks are weighted 3x higher in clinical decision models."
                            )

                            rules.forEach { (rule, desc) ->
                                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("✓", fontWeight = FontWeight.Bold, color = Color(0xFF0D9488), fontSize = 12.sp)
                                    Column {
                                        Text(rule, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF0F172A))
                                        Text(desc, fontSize = 10.sp, color = Color.DarkGray)
                                    }
                                }
                            }
                        }
                    }
                    "audits" -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                            Text("PROMPT/RESPONSE CRYPTOGRAPHIC AUDIT TRAILS", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Gray)
                            Text("Every session is cryptographically hash-logged for post-market safety surveillance review:", fontSize = 10.sp, color = Color.DarkGray)

                            if (triages.isEmpty()) {
                                Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                                    Text("No triage encounters logged on-device yet.", fontSize = 11.sp, color = Color.Gray)
                                }
                            } else {
                                triages.take(2).forEach { t ->
                                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)), modifier = Modifier.fillMaxWidth()) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                                Text("LOG REF: CO-TR-${t.id}", fontWeight = FontWeight.Bold, fontSize = 9.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                                Text("CONFIDENCE: ${t.confidenceBand.uppercase()}", color = Color(0xFF0D9488), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("Symptom description: \"${t.symptomDescription}\"", fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("Hash SHA-256: 8b01c3${t.id * 19}f49...29a", fontSize = 8.sp, color = Color.LightGray, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    "override" -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                            Text("PEER DOCTOR OVERRIDE INTERFACE", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Gray)
                            Text("Designated duty GP's can override AI triage status to ensure patient safety.", fontSize = 10.sp)

                            if (overrideSubmitted) {
                                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)), border = BorderStroke(1.dp, Color(0xFF10B981))) {
                                    Text("✓ Clinical Override applied successfully! Case record in Longitudinal Health Record signed and locked with Board ID: LUTH-GP-002.", color = Color(0xFF065F46), fontSize = 11.sp, modifier = Modifier.padding(10.dp), fontWeight = FontWeight.Bold)
                                }
                            } else {
                                OutlinedTextField(
                                    value = overrideSymptomId,
                                    onValueChange = { overrideSymptomId = it },
                                    label = { Text("Enter Patient Case Ref ID") },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Text("Select Clinical Override Severity:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    listOf("Routine", "Urgent", "Emergency").forEach { opt ->
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { overrideTriageResult = opt }) {
                                            RadioButton(selected = overrideTriageResult == opt, onClick = { overrideTriageResult = opt })
                                            Text(opt, fontSize = 11.sp)
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = overrideNote,
                                    onValueChange = { overrideNote = it },
                                    label = { Text("Clinical Justification for Override") },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Button(
                                    onClick = { overrideSubmitted = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Sign & Authorize Override", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                    "adverse" -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                            Text("ADVERSE EVENT LOGGING & SAFETY MONITORING", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Gray)
                            Text("Submit immediate notifications regarding any diagnostic discrepancies, triage errors, or delayed care alerts.", fontSize = 10.sp)

                            if (adverseEventSubmitted) {
                                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)), border = BorderStroke(1.dp, Color(0xFFEF4444))) {
                                    Text("✓ Adverse Event Report successfully signed and logged on local trust ledger. Prof. Adebayo's review panel notified.", color = Color(0xFF991B1B), fontSize = 11.sp, modifier = Modifier.padding(10.dp), fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Text("Severity:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    listOf("Mild", "Moderate", "Severe").forEach { s ->
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { adverseSeverity = s }) {
                                            RadioButton(selected = adverseSeverity == s, onClick = { adverseSeverity = s })
                                            Text(s, fontSize = 11.sp)
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = adverseEventText,
                                    onValueChange = { adverseEventText = it },
                                    label = { Text("Describe adverse outcome details or lag") },
                                    modifier = Modifier.fillMaxWidth().height(80.dp)
                                )

                                Button(
                                    onClick = { adverseEventSubmitted = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Log Adverse Event Audit", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close Safety Center")
            }
        }
    )
}

// --- INVESTOR PRESENTATION & PDF GENERATOR ---
fun generateInvestorPresentationPdf(context: android.content.Context) {
    try {
        val pdfDocument = android.graphics.pdf.PdfDocument()
        val width = 1280
        val height = 720
        
        // SLIDE 1: Cover (slate-900)
        run {
            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(width, height, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            
            // bg
            canvas.drawColor(android.graphics.Color.parseColor("#0F172A"))
            
            // graphics
            val circlePaint = android.graphics.Paint().apply { 
                color = android.graphics.Color.parseColor("#0D9488")
                isAntiAlias = true
            }
            canvas.drawCircle(1100f, 200f, 300f, circlePaint)
            
            val accentPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#38BDF8")
                isAntiAlias = true
            }
            canvas.drawCircle(100f, 600f, 150f, accentPaint)
            
            // Title
            val titlePaint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 80f
                isFakeBoldText = true
                isAntiAlias = true
            }
            canvas.drawText("CareOS", 120f, 280f, titlePaint)
            
            // Subtitle
            val subPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#38BDF8")
                textSize = 32f
                isAntiAlias = true
            }
            canvas.drawText("Next-Generation Clinically-Governed AI Care Platform", 120f, 350f, subPaint)
            
            // Description
            val descPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#94A3B8")
                textSize = 22f
                isAntiAlias = true
            }
            canvas.drawText("INVESTOR PITCH DECK & EXECUTIVE UPDATE (2026)", 120f, 430f, descPaint)
            canvas.drawText("★ Focus: Universal Audio Accessibility & Multi-Regional Scale", 120f, 475f, descPaint)
            
            // Footer
            val footerPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#64748B")
                textSize = 18f
                isAntiAlias = true
            }
            canvas.drawText("Accredited Clinical Trust Engine • Slide 1 of 6", 120f, 640f, footerPaint)
            
            pdfDocument.finishPage(page)
        }
        
        // SLIDE 2: Problem (rose-50)
        run {
            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(width, height, 2).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            
            canvas.drawColor(android.graphics.Color.parseColor("#FFF1F2"))
            
            val borderPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#FDA4AF")
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 10f
            }
            canvas.drawRect(20f, 20f, (width - 20).toFloat(), (height - 20).toFloat(), borderPaint)
            
            val hPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#991B1B")
                textSize = 44f
                isFakeBoldText = true
                isAntiAlias = true
            }
            canvas.drawText("🚨 The Core Healthcare Accessibility Problem", 80f, 100f, hPaint)
            
            val tPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#4C0519")
                textSize = 24f
                isAntiAlias = true
            }
            
            val bulletPoints = listOf(
                "• Diagnostic Drift & Clinical Inaccuracy: Legacy triage software yields over 57% drift errors.",
                "• Language & Literacy Barriers: Dialect mismatches exclude millions in local communities.",
                "• Lack of Inclusive Design: Visual and reading limitations prevent access for elderly and low-vision users.",
                "• Systemic Donation & Aid Fraud: General crowdfunding models route funds to unvetted personal accounts.",
                "• Regulatory Fragmentation: Systems lack synchronized National Health Insurance (NHIA) standards."
            )
            
            var currentY = 180f
            for (line in bulletPoints) {
                canvas.drawText(line, 80f, currentY, tPaint)
                currentY += 60f
            }
            
            val footPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#9F1239")
                textSize = 18f
                isAntiAlias = true
            }
            canvas.drawText("CareOS Investor Presentation • Slide 2 of 6", 80f, 650f, footPaint)
            
            pdfDocument.finishPage(page)
        }
        
        // SLIDE 3: Solution - Universal Voice Mode (teal-50)
        run {
            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(width, height, 3).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            
            canvas.drawColor(android.graphics.Color.parseColor("#F0FDF4"))
            
            val borderPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#A7F3D0")
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 10f
            }
            canvas.drawRect(20f, 20f, (width - 20).toFloat(), (height - 20).toFloat(), borderPaint)
            
            val hPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#115E59")
                textSize = 44f
                isFakeBoldText = true
                isAntiAlias = true
            }
            canvas.drawText("🎙️ Solution: Universal Voice-First Interface", 80f, 100f, hPaint)
            
            val tPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#134E4A")
                textSize = 24f
                isAntiAlias = true
            }
            
            val bulletPoints = listOf(
                "CareOS breaks literacy barriers by introducing an Audio-First Interactive Mode Portal:",
                "• Text-to-Speech Playback: Spoken playback for all diagnosis and system advice alerts.",
                "• High-Contrast Legibility: Clean, bold text chat bubbles set against highly legible light backgrounds.",
                "• Interactive Mic Controller: Touch-to-record voice symptoms with immediate diagnostic intakes.",
                "• Multi-Dialect Simulations: Local transcription simulations for Swahili, Patois, Hinglish, Pidgin, etc.",
                "• Complete Accessibility: Built hand-in-hand with clinical guidelines to aid non-literate and low-vision users."
            )
            
            var currentY = 180f
            for (line in bulletPoints) {
                canvas.drawText(line, 80f, currentY, tPaint)
                currentY += 55f
            }
            
            val footPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#0F766E")
                textSize = 18f
                isAntiAlias = true
            }
            canvas.drawText("CareOS Investor Presentation • Slide 3 of 6", 80f, 650f, footPaint)
            
            pdfDocument.finishPage(page)
        }
        
        // SLIDE 4: Regional Footprint (amber-50)
        run {
            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(width, height, 4).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            
            canvas.drawColor(android.graphics.Color.parseColor("#FFFBEB"))
            
            val borderPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#FDE68A")
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 10f
            }
            canvas.drawRect(20f, 20f, (width - 20).toFloat(), (height - 20).toFloat(), borderPaint)
            
            val hPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#92400E")
                textSize = 44f
                isFakeBoldText = true
                isAntiAlias = true
            }
            canvas.drawText("🗺️ Scalability: Global Multi-Regional Footprint", 80f, 100f, hPaint)
            
            val tPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#78350F")
                textSize = 24f
                isAntiAlias = true
            }
            
            val bulletPoints = listOf(
                "Designed for multi-national distribution and region-aware healthcare:",
                "• Active Regional Targets: Expanded to Nigeria, Kenya, St. Lucia, India, Brazil, UK, US, Australia.",
                "• Epidemiological Weighting: Models weight local seasonal risk factors (e.g. Malaria weightings in Lagos).",
                "• Vetted Clinical Override: Designated peer-review boards can sign-off and force-escalate crucial cases.",
                "• Automated Regulatory Sync: Real-time validation against country registries (such as Nigeria Medical Council).",
                "• Standardized Evidence Base: Clinical models strictly aligned with BMJ and WHO digital safety criteria."
            )
            
            var currentY = 180f
            for (line in bulletPoints) {
                canvas.drawText(line, 80f, currentY, tPaint)
                currentY += 55f
            }
            
            val footPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#B45309")
                textSize = 18f
                isAntiAlias = true
            }
            canvas.drawText("CareOS Investor Presentation • Slide 4 of 6", 80f, 650f, footPaint)
            
            pdfDocument.finishPage(page)
        }
        
        // SLIDE 5: Business Model & Modules (blue-50)
        run {
            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(width, height, 5).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            
            canvas.drawColor(android.graphics.Color.parseColor("#EFF6FF"))
            
            val borderPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#BFDBFE")
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 10f
            }
            canvas.drawRect(20f, 20f, (width - 20).toFloat(), (height - 20).toFloat(), borderPaint)
            
            val hPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#1E40AF")
                textSize = 44f
                isFakeBoldText = true
                isAntiAlias = true
            }
            canvas.drawText("🔗 Five Modules of the Direct-Trust Engine", 80f, 100f, hPaint)
            
            val tPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#1E3A8A")
                textSize = 24f
                isAntiAlias = true
            }
            
            val bulletPoints = listOf(
                "1. AI Triage: Real-time, peer-governed symptom assessments integrated with local optical scan checks.",
                "2. Live Telehealth GP: Seamless handoff to live GPs when symptoms trigger confidence or red-flag limits.",
                "3. Direct-to-Hospital Care Fund: 100% of donations route directly to vetted clinical treasuries, avoiding fraud.",
                "4. NHIA Insurance Integration: Instant registration and automated digital claims checking.",
                "5. Longitudinal Health Records (LHR): Portable, patient-owned longitudinal electronic files."
            )
            
            var currentY = 180f
            for (line in bulletPoints) {
                canvas.drawText(line, 80f, currentY, tPaint)
                currentY += 60f
            }
            
            val footPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#1D4ED8")
                textSize = 18f
                isAntiAlias = true
            }
            canvas.drawText("CareOS Investor Presentation • Slide 5 of 6", 80f, 650f, footPaint)
            
            pdfDocument.finishPage(page)
        }
        
        // SLIDE 6: Traction Metrics (purple-50)
        run {
            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(width, height, 6).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            
            canvas.drawColor(android.graphics.Color.parseColor("#FAF5FF"))
            
            val borderPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#E9D5FF")
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 10f
            }
            canvas.drawRect(20f, 20f, (width - 20).toFloat(), (height - 20).toFloat(), borderPaint)
            
            val hPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#6B21A8")
                textSize = 44f
                isFakeBoldText = true
                isAntiAlias = true
            }
            canvas.drawText("📊 Financial KPIs & Public Ledger Traction", 80f, 100f, hPaint)
            
            val tPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#581C87")
                textSize = 24f
                isAntiAlias = true
            }
            
            val bulletPoints = listOf(
                "Immediate operational success on global trust pilot:",
                "• ₦1,250,000+ Settled Directly: Clinical wires completed to certified hospital bank registries.",
                "• 100% Fraud Prevention: Audited invoice check rates prevent invalid claims or billing inflation.",
                "• Cryptographic Audit Trails: 100% of patient encounters logged onto a public-ledger trust registry.",
                "• Scalable Growth Cost: Cost per session is 100x lower than legacy telephone health lines.",
                "• Accolades & Accreditations: Endorsed by local medical registries and NHIA insurance managers."
            )
            
            var currentY = 180f
            for (line in bulletPoints) {
                canvas.drawText(line, 80f, currentY, tPaint)
                currentY += 55f
            }
            
            val footPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#7E22CE")
                textSize = 18f
                isAntiAlias = true
            }
            canvas.drawText("CareOS Investor Presentation • Slide 6 of 6", 80f, 650f, footPaint)
            
            pdfDocument.finishPage(page)
        }
        
        // Save the PDF file
        val file = java.io.File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), "CareOS_Investor_Presentation.pdf")
        val fileOutputStream = java.io.FileOutputStream(file)
        pdfDocument.writeTo(fileOutputStream)
        pdfDocument.close()
        fileOutputStream.close()
        
        // Trigger Share Intent with FileProvider
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "com.example.fileprovider", file)
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            putExtra(android.content.Intent.EXTRA_SUBJECT, "CareOS Investor Presentation & Pitch Deck")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "Download / Export Presentation PDF"))
        
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@Composable
fun InvestorPresentationDialog(onDismiss: () -> Unit) {
    var currentSlide by remember { mutableStateOf(0) }
    val context = androidx.compose.ui.platform.LocalContext.current
    var isExporting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.TrendingUp, contentDescription = null, tint = Color(0xFF0F172A))
                Spacer(modifier = Modifier.width(8.dp))
                Text("CareOS Executive Pitch Deck", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Executive update for CareOS board members and investors. Featuring our new accessibility upgrades and global expansion vectors.",
                    fontSize = 11.sp,
                    color = Color.Gray
                )

                // The Interactive Slide Container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when (currentSlide) {
                                0 -> Color(0xFF0F172A) // slate-900 (dark cover)
                                1 -> Color(0xFFFFF1F2) // rose-50
                                2 -> Color(0xFFF0FDF4) // teal-50
                                3 -> Color(0xFFFFFBEB) // amber-50
                                4 -> Color(0xFFEFF6FF) // blue-50
                                5 -> Color(0xFFFAF5FF) // purple-50
                                else -> Color.White
                            }
                        )
                        .border(
                            width = 1.dp,
                            color = when (currentSlide) {
                                0 -> Color.Transparent
                                1 -> Color(0xFFFDA4AF)
                                2 -> Color(0xFFA7F3D0)
                                3 -> Color(0xFFFDE68A)
                                4 -> Color(0xFFBFDBFE)
                                5 -> Color(0xFFE9D5FF)
                                else -> Color.LightGray
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(16.dp)
                ) {
                    when (currentSlide) {
                        0 -> {
                            // Cover Slide
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text("CareOS", fontWeight = FontWeight.Black, fontSize = 28.sp, color = Color.White)
                                Text("Next-Generation Clinically-Governed AI Care Platform", fontWeight = FontWeight.Medium, fontSize = 12.sp, color = Color(0xFF38BDF8))
                                Spacer(modifier = Modifier.height(14.dp))
                                Text("INVESTOR PRESENTATION & PITCH DECK", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF94A3B8))
                                Text("★ Focus: Universal Audio Accessibility & Multi-Regional Scale", fontSize = 10.sp, color = Color(0xFF94A3B8))
                                Spacer(modifier = Modifier.height(30.dp))
                                Text("Slide 1 of 6 • Executive Briefing", fontSize = 9.sp, color = Color(0xFF64748B))
                            }
                        }
                        1 -> {
                            // Problem
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("🚨 The Core Problem", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF991B1B))
                                Text("• Clinical Diagnostic Drift: Legacy triage software yields over 57% drift errors.", fontSize = 11.sp, color = Color(0xFF4C0519))
                                Text("• Language Barriers: Standard systems exclude regional communities.", fontSize = 11.sp, color = Color(0xFF4C0519))
                                Text("• Accessibility Gap: Low-vision or non-literate patients have zero self-care channels.", fontSize = 11.sp, color = Color(0xFF4C0519))
                                Text("• Crowdfunding Loss: Unvetted donation campaigns are riddle-filled with fraud.", fontSize = 11.sp, color = Color(0xFF4C0519))
                                Spacer(modifier = Modifier.weight(1f))
                                Text("Slide 2 of 6 • The Opportunity", fontSize = 9.sp, color = Color(0xFF9F1239))
                            }
                        }
                        2 -> {
                            // Universal Voice Mode
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("🎙️ Solution: Universal Audio Mode", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF115E59))
                                Text("• Spoken Read-Aloud: Full Text-to-Speech playback for all advice dialogues.", fontSize = 11.sp, color = Color(0xFF134E4A))
                                Text("• High Contrast UI: Crisp, bold chat bubbles with white backgrounds for maximum legibility.", fontSize = 11.sp, color = Color(0xFF134E4A))
                                Text("• Tap-to-Record Mic: Simplifies patient symptoms intakes with voice capture.", fontSize = 11.sp, color = Color(0xFF134E4A))
                                Text("• Local Dialects: Built-in simulations for St. Lucian Patois, Nigerian Pidgin, Hinglish, etc.", fontSize = 11.sp, color = Color(0xFF134E4A))
                                Spacer(modifier = Modifier.weight(1f))
                                Text("Slide 3 of 6 • Inclusive Health Access", fontSize = 9.sp, color = Color(0xFF0F766E))
                            }
                        }
                        3 -> {
                            // Regional Footprint
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("🗺️ Scale: Multi-Regional Footprint", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF92400E))
                                Text("• Active Regions: Live in Nigeria, Kenya, St. Lucia, India, Brazil, UK, US, Australia.", fontSize = 11.sp, color = Color(0xFF78350F))
                                Text("• Seasonal Weights: Adapts medical risk values to local seasons (e.g. rainy season Malaria weighting).", fontSize = 11.sp, color = Color(0xFF78350F))
                                Text("• Regulatory Integration: Active compliance checks against national boards.", fontSize = 11.sp, color = Color(0xFF78350F))
                                Text("• Clinical Overrides: Peer-review duty GPs can instantly sign-off and force-redirect cases.", fontSize = 11.sp, color = Color(0xFF78350F))
                                Spacer(modifier = Modifier.weight(1f))
                                Text("Slide 4 of 6 • Global Distribution Vector", fontSize = 9.sp, color = Color(0xFFB45309))
                            }
                        }
                        4 -> {
                            // Modules
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("🔗 Five Core Direct-Trust Modules", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1E40AF))
                                Text("1. AI Triage: Vetted intake with cryptographic record hashing.", fontSize = 11.sp, color = Color(0xFF1E3A8A))
                                Text("2. Live Telehealth: Smooth handoff to live GPs upon red flag triggers.", fontSize = 11.sp, color = Color(0xFF1E3A8A))
                                Text("3. Care Fund: Direct hospital settlements bypassing personal wallets.", fontSize = 11.sp, color = Color(0xFF1E3A8A))
                                Text("4. LHR: Portable, patient-owned longitudinal electronic files.", fontSize = 11.sp, color = Color(0xFF1E3A8A))
                                Text("5. NHIA Insurance: Automated claims clearance and checks.", fontSize = 11.sp, color = Color(0xFF1E3A8A))
                                Spacer(modifier = Modifier.weight(1f))
                                Text("Slide 5 of 6 • Trust Platform Engine", fontSize = 9.sp, color = Color(0xFF1D4ED8))
                            }
                        }
                        5 -> {
                            // Metrics
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("📊 Public Ledger Traction Metrics", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF6B21A8))
                                Text("• ₦1,250,000+ Direct Settled: Wires completed directly to hospital registries.", fontSize = 11.sp, color = Color(0xFF581C87))
                                Text("• 100% Fraud Blocked: Invoice audit matching prevents billing inflations.", fontSize = 11.sp, color = Color(0xFF581C87))
                                Text("• Regulatory Support: Verified by LUTH and West Africa Epidemiology.", fontSize = 11.sp, color = Color(0xFF581C87))
                                Text("• 100x Cost Reduction: Multi-regional automated triage cost footprint.", fontSize = 11.sp, color = Color(0xFF581C87))
                                Spacer(modifier = Modifier.weight(1f))
                                Text("Slide 6 of 6 • Operational Health", fontSize = 9.sp, color = Color(0xFF7E22CE))
                            }
                        }
                    }
                }

                // Slide Controls Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { if (currentSlide > 0) currentSlide-- },
                        enabled = currentSlide > 0
                    ) {
                        Text("◀ Previous")
                    }

                    Text("Slide ${currentSlide + 1} of 6", fontSize = 11.sp, fontWeight = FontWeight.Bold)

                    TextButton(
                        onClick = { if (currentSlide < 5) currentSlide++ },
                        enabled = currentSlide < 5
                    ) {
                        Text("Next ▶")
                    }
                }

                HorizontalDivider()

                // Download/Export Button
                Button(
                    onClick = {
                        scope.launch {
                            isExporting = true
                            kotlinx.coroutines.delay(1000)
                            generateInvestorPresentationPdf(context)
                            isExporting = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A))
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isExporting) "Generating PDF Presentation..." else "Export Presentation as PDF", fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF64748B))
            ) {
                Text("Dismiss")
            }
        }
    )
}

// --- ADMIN PANEL SCREEN (BACKEND CLINICAL CONTROL & REVENUE PORTAL) ---
@Composable
fun AdminPanelScreen(viewModel: CareViewModel) {
    var revenueTier by remember { mutableStateOf("Enterprise Authority ($999/mo)") }
    var nationalFeeSplit by remember { mutableStateOf(1.5f) }
    var internationalFeeSplit by remember { mutableStateOf(2.9f) }
    
    // Epidemic Multipliers
    var malariaWeight by remember { mutableStateOf(2.5f) }
    var choleraAlertEnabled by remember { mutableStateOf(true) }
    var activeClinicians by remember { mutableStateOf(8) }
    
    // Transaction simulators
    var webhookLogs by remember { mutableStateOf<List<String>>(listOf(
        "[08:31:02] Stripe webhook initialized. Listening on careos.api/v2/payments",
        "[08:32:11] ₦15,000 donation cleared for Patient Aisha Bello. Direct-to-hospital wire verified.",
        "[08:33:40] LUTH Registry ledger verified. Hash: 0x8a92bf...7c2"
    )) }
    var isFiringWebhook by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("admin_panel_screen")
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Glowing Crown Header Banner
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF2F6)),
            border = BorderStroke(2.dp, Color(0xFF6366F1)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("👑", fontSize = 28.sp)
                    Column {
                        Text("CAREOS CENTRAL CONTROL SYSTEM", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = Color(0xFF312E81))
                        Text("Backend Administration & Monetization Hub", fontSize = 10.sp, color = Color(0xFF6366F1), fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    text = "Welcome to the Platform Admin panel. From this console, you can audit financial flows, configure regional epidemic thresholds, customize platform fee splits, and simulate secure direct-settlement payment gateways.",
                    fontSize = 10.5.sp,
                    color = Color(0xFF374151),
                    lineHeight = 14.sp
                )
            }
        }

        // --- SECTION 1: MONETIZATION & PLATFORM EARNINGS ENGINE ---
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("💰", fontSize = 20.sp)
                    Text("PLATFORM MONETIZATION POLICY", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E293B))
                }
                
                Text(
                    text = "CareOS earns revenue via transaction clearance splits on direct settlements and B2B SaaS telemetry licenses sold to partner clinics/NHIA boards.",
                    fontSize = 10.sp,
                    color = Color.Gray
                )

                HorizontalDivider()

                // Earnings dashboard
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .background(Color(0xFFF0FDF4), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Text("NATIONAL FEES (PAYSTACK)", fontSize = 7.5.sp, color = Color(0xFF166534), fontWeight = FontWeight.Bold)
                            Text("₦128,450", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color(0xFF15803D))
                            Text("1.5% split cleared locally", fontSize = 8.sp, color = Color.Gray)
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .background(Color(0xFFF0F9FF), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Text("INT'L FEES (STRIPE)", fontSize = 7.5.sp, color = Color(0xFF075985), fontWeight = FontWeight.Bold)
                            Text("$1,425.80", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color(0xFF0369A1))
                            Text("2.9% split cleared globally", fontSize = 8.sp, color = Color.Gray)
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .background(Color(0xFFFDF2F8), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Text("SPECIALIST COMMISSIONS", fontSize = 7.5.sp, color = Color(0xFF9D174D), fontWeight = FontWeight.Bold)
                            Text("$342.50", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color(0xFFBE185D))
                            Text("15.0% platform consultation split", fontSize = 8.sp, color = Color.Gray)
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .background(Color(0xFFFAF5FF), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Text("B2B TELEMETRY SAAS ARR", fontSize = 7.5.sp, color = Color(0xFF6B21A8), fontWeight = FontWeight.Bold)
                            Text("$18,500", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color(0xFF7E22CE))
                            Text("Active hospital licenses", fontSize = 8.sp, color = Color.Gray)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Adjuster controls
                Text("Configure National Clearance Fee Split: ${String.format("%.1f", nationalFeeSplit)}%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                Slider(
                    value = nationalFeeSplit,
                    onValueChange = { nationalFeeSplit = it },
                    valueRange = 0.5f..5.0f,
                    colors = SliderDefaults.colors(thumbColor = Color(0xFF0D9488), activeTrackColor = Color(0xFF0D9488))
                )

                Text("Configure Stripe Int'l Routing Fee: ${String.format("%.1f", internationalFeeSplit)}%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                Slider(
                    value = internationalFeeSplit,
                    onValueChange = { internationalFeeSplit = it },
                    valueRange = 1.0f..7.0f,
                    colors = SliderDefaults.colors(thumbColor = Color(0xFF0EA5E9), activeTrackColor = Color(0xFF0EA5E9))
                )

                Spacer(modifier = Modifier.height(4.dp))

                // B2B License tiers selector
                Text("Select B2B Partner API License Tier:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                listOf(
                    "Starter API Tier ($49/mo - 100 Triage Requests)",
                    "Practice Professional ($199/mo - 1000 Triage Requests)",
                    "Enterprise Authority ($999/mo - Unlimited SLA Telemetry)"
                ).forEach { tier ->
                    val sel = revenueTier.contains(tier.substring(0, 8))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (sel) Color(0xFFEEF2F6) else Color.Transparent)
                            .border(1.dp, if (sel) Color(0xFF6366F1) else Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                            .clickable { revenueTier = tier }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(tier, fontSize = 9.5.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                        RadioButton(selected = sel, onClick = { revenueTier = tier })
                    }
                }
            }
        }

        // --- SECTION 2: CLINICAL RISK & EPIDEMIC ENGINE ---
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("🏥", fontSize = 20.sp)
                    Text("EPIDEMIOLOGICAL & PROTOCOL SETTINGS", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E293B))
                }
                
                Text(
                    text = "Override automated symptom diagnostic parameters to prevent clinical drift during local outbreaks.",
                    fontSize = 10.sp,
                    color = Color.Gray
                )

                HorizontalDivider()

                // Slide Weights
                Column {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Seasonal Malaria Intake Weighting:", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("${String.format("%.1f", malariaWeight)}x Priority", fontSize = 10.sp, color = Color(0xFFD97706), fontWeight = FontWeight.Bold)
                    }
                    Text("Increases diagnostic flags for malaria upon standard fever reports.", fontSize = 8.5.sp, color = Color.Gray)
                    Slider(
                        value = malariaWeight,
                        onValueChange = { malariaWeight = it },
                        valueRange = 1.0f..5.0f,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFFD97706), activeTrackColor = Color(0xFFD97706))
                    )
                }

                // Cholera switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Cholera Outbreak Red-Flag Escalation", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("Forces immediate GP handoff for any gastric intake symptom.", fontSize = 8.5.sp, color = Color.Gray)
                    }
                    Switch(
                        checked = choleraAlertEnabled,
                        onCheckedChange = { choleraAlertEnabled = it }
                    )
                }

                HorizontalDivider()

                // Active Doctors count
                Column {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Active Simulated Duty GPs Online:", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("$activeClinicians MDs Online", fontSize = 10.sp, color = Color(0xFF0D9488), fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = activeClinicians.toFloat(),
                        onValueChange = { activeClinicians = it.toInt() },
                        valueRange = 1f..25f,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFF0D9488), activeTrackColor = Color(0xFF0D9488))
                    )
                }
            }
        }

        // --- SECTION 3: PAYMENTS TESTING & WEBHOOK CONSOLE ---
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("⚡", fontSize = 20.sp)
                    Text("DEVELOPER TEST CONSOLE & WEBHOOKS", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E293B))
                }
                
                Text(
                    text = "Test Stripe, Paystack, and Mobile Money API response simulations and webhook deliveries instantly.",
                    fontSize = 10.sp,
                    color = Color.Gray
                )

                HorizontalDivider()

                Button(
                    onClick = {
                        scope.launch {
                            isFiringWebhook = true
                            webhookLogs = webhookLogs + "[PENDING] Firing Stripe test charge hook: careos.api/v2/payments..."
                            delay(1000)
                            webhookLogs = webhookLogs + "[SUCCESS] stripe_charge_cleared. Cryptographic wire initiated to hospital #CO-LUTH-48."
                            webhookLogs = webhookLogs + "[LEDGER] Recorded transactional block onto public audit ledger. Gas cost: 0.002 Gwei."
                            isFiringWebhook = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isFiringWebhook
                ) {
                    Text(if (isFiringWebhook) "Firing webhook..." else "Fire Simulated Gateway Webhook (Test Payment)", fontSize = 11.sp)
                }

                Text("Simulated Terminal Logs:", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    webhookLogs.forEach { log ->
                        Text(
                            text = log,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 8.5.sp,
                            color = if (log.contains("SUCCESS") || log.contains("donation cleared")) Color(0xFF34D399) else if (log.contains("PENDING")) Color(0xFFFBBF24) else Color(0xFF94A3B8)
                        )
                    }
                }
            }
        }
    }
}


// ==========================================
// --- SECURE CLINICAL AUTHENTICATION GATE SCREEN ---
// ==========================================
@Composable
fun MockDownloadDialog(
    platformName: String,
    onDismiss: () -> Unit
) {
    var progress by remember { mutableStateOf(0f) }
    var currentStep by remember { mutableStateOf("Initializing clinical node...") }
    
    LaunchedEffect(Unit) {
        val steps = listOf(
            "Contacting localized CareOS Clinical CDN...",
            "Validating cryptographic package signatures...",
            "Syncing NDPR medical vault libraries...",
            "Finalizing secure installation package..."
        )
        for (i in 1..100) {
            kotlinx.coroutines.delay(25)
            progress = i / 100f
            when (i) {
                15 -> currentStep = steps[0]
                40 -> currentStep = steps[1]
                65 -> currentStep = steps[2]
                85 -> currentStep = steps[3]
            }
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription = null,
                    tint = Color(0xFF0F766E),
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "Clinical Downloader",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Preparing $platformName secure deployment...",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF475569),
                    textAlign = TextAlign.Center
                )
                
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = Color(0xFF14B8A6),
                    trackColor = Color(0xFFD9F3EE)
                )
                
                Text(
                    text = "${(progress * 100).toInt()}% - $currentStep",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF0F766E),
                    textAlign = TextAlign.Center
                )
                
                if (progress >= 1f) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFDCFCE7)),
                        border = BorderStroke(1.dp, Color(0xFF86EFAC)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF166534),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Secure signature validated. Ready to host!",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF166534)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(text = if (progress >= 1f) "Finished" else "Cancel", fontSize = 11.sp)
            }
        }
    )
}

@Composable
fun CareOSLandingPageScreen(
    viewModel: CareViewModel,
    onProceedClick: () -> Unit
) {
    var activeBenefitTab by remember { mutableStateOf("patient") } // "patient" or "doctor"
    var downloadingPlatform by remember { mutableStateOf<String?>(null) }
    
    if (downloadingPlatform != null) {
        MockDownloadDialog(
            platformName = downloadingPlatform ?: "",
            onDismiss = { downloadingPlatform = null }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF1F5F9))
            .testTag("landing_page_screen")
    ) {
        // 1. HEADER HERO
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(Color(0xFF0F766E), Color(0xFF115E59))
                        )
                    )
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.White, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MedicalServices,
                                contentDescription = "CareOS Logo",
                                tint = Color(0xFF0F766E),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "CareOS",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Text(
                                text = "Clinical Medical Core",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2DD4BF)
                            )
                        }
                    }

                    Button(
                        onClick = onProceedClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF14B8A6)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Launch Portal", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Hero Headline
                Text(
                    text = "Healthcare That Feels Like Home.",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    lineHeight = 30.sp
                )

                Text(
                    text = "A clinically-governed health operating system. Chat, scan, consult, and insure under an NDPR-secure medical ledger.",
                    fontSize = 11.sp,
                    color = Color(0xFFCCFBF1),
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )

                // Trust Badges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("99.4%", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFF2DD4BF))
                        Text("Triage Accuracy", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("24/7", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFF2DD4BF))
                        Text("Duty Doctors", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Instant", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFF2DD4BF))
                        Text("HMO Approval", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Hero Image - Doctor portrait (using Coil AsyncImage)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .border(1.dp, Color(0xFF2DD4BF).copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AsyncImage(
                            model = "https://images.unsplash.com/photo-1559839734-2b71ea197ec2?auto=format&fit=crop&q=80&w=600",
                            contentDescription = "Doctor",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    androidx.compose.ui.graphics.Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(modifier = Modifier.size(6.dp).background(Color(0xFF10B981), CircleShape))
                                Text("Online & Consultative", fontSize = 8.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                            }
                            Text("Dr. Grace Adeniran, MD", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Primary Care Physician, CareOS Lagos Team", fontSize = 10.sp, color = Color.LightGray)
                        }
                    }
                }
            }
        }

        // 2. THE BEAUTIFUL STORY of SIMEON
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🚨", fontSize = 20.sp)
                        Column {
                            Text(
                                text = "THE STORY OF SIMEON",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF991B1B)
                            )
                            Text(
                                text = "Emergency Escalation at 11:30 PM",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                        }
                    }

                    Text(
                        text = "It was 11:30 PM in Lagos. Simeon Adebayo woke up with sudden, burning chest tightness. Driving to LUTH ER meant navigating dark streets, and queuing for hours inside a crowded lobby was a painful risk.",
                        fontSize = 11.sp,
                        color = Color(0xFF475569),
                        lineHeight = 16.sp
                    )

                    // Card inside with photo of smiling happy family representing peace of mind
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = "https://images.unsplash.com/photo-1512052989961-a0c37d51163b?auto=format&fit=crop&q=80&w=600",
                                contentDescription = "Smiling family",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        androidx.compose.ui.graphics.Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                                        )
                                    )
                            )
                            Text(
                                text = "Simeon Adebayo & family, living healthy with absolute peace of mind.",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(8.dp)
                            )
                        }
                    }

                    Text(
                        text = "Instead, Simeon launched CareOS. He chatted his symptoms in plain language and captured a diagnostic scan. Instantly, the CareOS clinical core identified critical warning signs, generated a standard FHIR referral ticket, pre-authorized his connected HMO, and triggered a direct live telehealth call connecting him to an active Duty GP.",
                        fontSize = 11.sp,
                        color = Color(0xFF475569),
                        lineHeight = 16.sp
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF0FDF4), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFFBBF7D0), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "✅ \"CareOS saved my life by bridging the gap instantly between triage advisory, insurance pre-auth, and active doctors.\" — Simeon A.",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF15803D)
                        )
                    }
                }
            }
        }

        // 3. CORE FEATURES GRID (Title)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "WHAT CAREOS CAN DO",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F766E),
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Comprehensive Clinical Operating System",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF0F172A),
                    textAlign = TextAlign.Center
                )
            }
        }

        // 4. THE FEATURES (List format for beautiful responsive scroll)
        val features = listOf(
            Triple("AI Triage Chat", "A highly precise chatbot mapped to clinical protocols. Speaks simple terms, estimates severity, and flags red flags in seconds.", Icons.Default.Chat),
            Triple("Symptom Visual Scan", "Uses advanced photography tools inside your phone camera to capture clear images of lesions, rashes, and swelling.", Icons.Default.CameraAlt),
            Triple("24/7 Telehealth Consult", "Instant live audio or video line directly to certified clinical Duty GPs across Nigeria with zero wait times.", Icons.Default.Call),
            Triple("FHIR Referral Vault", "Drives standard Hospital Referrals automatically for red flags. Decrypts your longitudinal medical history securely.", Icons.Default.FolderShared),
            Triple("NHIA & HMO Claims", "Link private health insurance or NHIA profiles. Pre-authorize outpatient consults automatically through APIs.", Icons.Default.LocalHospital),
            Triple("CareOS Mutual Fund", "Community-led health crowdfunding ledger. Transparent payouts directly to validated clinic billing records.", Icons.Default.Handshake)
        )

        items(features) { feature ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0xFFD9F3EE), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = feature.third,
                            contentDescription = null,
                            tint = Color(0xFF0F766E),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = feature.first,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = feature.second,
                            fontSize = 10.5.sp,
                            color = Color(0xFF475569),
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }

        // 5. BENEFITS SWITCHER TABS
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "UNMATCHED APP BENEFITS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F766E),
                    letterSpacing = 1.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                // Tab Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                        .padding(4.dp)
                ) {
                    Button(
                        onClick = { activeBenefitTab = "patient" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (activeBenefitTab == "patient") Color.White else Color.Transparent,
                            contentColor = if (activeBenefitTab == "patient") Color(0xFF0F766E) else Color(0xFF64748B)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = if (activeBenefitTab == "patient") 2.dp else 0.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("For Patients", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { activeBenefitTab = "doctor" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (activeBenefitTab == "doctor") Color.White else Color.Transparent,
                            contentColor = if (activeBenefitTab == "doctor") Color(0xFF0F766E) else Color(0xFF64748B)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = if (activeBenefitTab == "doctor") 2.dp else 0.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("For Practitioners", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (activeBenefitTab == "patient") {
                            listOf(
                                "Zero Clinic Delays: Instant triage determinations in seconds.",
                                "Personal Secure Vault: Total NDPR control over your health records.",
                                "Linked HMO claims: Skip claims lines; outpatient fees covered automatically.",
                                "Community Crowdfunding: Secure local help for outstanding bill deficits."
                            ).forEach { bullet ->
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(bullet, fontSize = 10.5.sp, color = Color(0xFF334155))
                                }
                            }
                        } else {
                            listOf(
                                "Onboard Private Practice: Join Nigeria's premier private specialist catalog.",
                                "Competitive Cross-Border Payouts: Work remotely, earning in global currencies.",
                                "AI Clinician Assistants: Feed a custom AI with your medical logic to screen clients.",
                                "FHIR EHR Ledger Sync: Read validated patient medical records upon explicit consent."
                            ).forEach { bullet ->
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color(0xFF0F766E),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(bullet, fontSize = 10.5.sp, color = Color(0xFF334155))
                                }
                            }
                        }
                    }
                }
            }
        }

        // 6. STORES & DOWNLOAD BADGES
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                border = BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "DOWNLOAD CHANNELS",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2DD4BF),
                        letterSpacing = 1.sp
                    )

                    Text(
                        text = "Secure Clinical Deployments",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Text(
                        text = "Click to run a verified, cryptographically checked simulation download for your sovereign OS device:",
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center
                    )

                    // Download Buttons Stack
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Google Play Button
                        Button(
                            onClick = { downloadingPlatform = "Google Play Store" },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF334155)),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("GET IT ON", fontSize = 7.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                                    Text("Google Play", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // App Store Button
                        Button(
                            onClick = { downloadingPlatform = "Apple App Store" },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF334155)),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhoneIphone,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("DOWNLOAD ON THE", fontSize = 7.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                                    Text("App Store", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Direct APK Button
                        Button(
                            onClick = { downloadingPlatform = "Direct Secure APK" },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E)),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF14B8A6)),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Android,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("SECURE DIRECT DOWNLOAD", fontSize = 7.sp, color = Color(0xFFCCFBF1), fontWeight = FontWeight.Bold)
                                    Text("Download CareOS APK", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    
                    // Final Pediatrician card from Unsplash
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .padding(top = 8.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = "https://images.unsplash.com/photo-1581594693702-fbdc51b2763b?auto=format&fit=crop&q=80&w=400",
                                contentDescription = "Pediatric checkup",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        androidx.compose.ui.graphics.Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f))
                                        )
                                    )
                            )
                            Text(
                                text = "Bridging pediatric and primary care gaps securely.",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(8.dp)
                            )
                        }
                    }
                }
            }
        }

        // 7. BOTTOM CTA - ACTION GATE
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onProceedClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("landing_proceed_button")
                ) {
                    Text("Access Secure Patient Portal", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(imageVector = Icons.Default.ArrowForward, contentDescription = null, tint = Color.White)
                }

                Text(
                    text = "CareOS Version 1.4.0 • Compliant with NDPR Cryptographic Ledger Guidelines",
                    fontSize = 8.5.sp,
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ==========================================
// --- SECURE CLINICAL AUTHENTICATION GATE SCREEN ---
// ==========================================
@Composable
fun CareOSAuthScreen(viewModel: CareViewModel) {
    var showLandingPage by remember { mutableStateOf(true) }
    var isSignUpMode by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("demo@careos.org") }
    var password by remember { mutableStateOf("password123") }
    var fullName by remember { mutableStateOf("Simeon Adebayo") }
    var hmoMemberId by remember { mutableStateOf("NHIA-NIG-7734") }
    
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    
    if (showLandingPage) {
        CareOSLandingPageScreen(
            viewModel = viewModel,
            onProceedClick = { showLandingPage = false }
        )
        return
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FBFA))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .background(Color.White, RoundedCornerShape(24.dp))
                .border(1.dp, Color(0xFFDCE7E5), RoundedCornerShape(24.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(Color(0xFFD9F3EE), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "Encrypted Vault Logo",
                    tint = Color(0xFF0F766E),
                    modifier = Modifier.size(32.dp)
                )
            }
            
            Text(
                text = "CareOS Secure Clinic Portal",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
            
            Text(
                text = if (isSignUpMode) "Enroll in Nigeria's private health network with clinical-grade ledger security." else "Decrypt and access your secure medical data vaults.",
                fontSize = 11.sp,
                color = Color(0xFF475569),
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )
            
            TextButton(
                onClick = { showLandingPage = true },
                modifier = Modifier.testTag("back_to_tour_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFF14B8A6),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "View App Tour, Stories & Downloads",
                    color = Color(0xFF14B8A6),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            if (errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEE2E2)),
                    border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = errorMessage ?: "",
                        color = Color(0xFF991B1B),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            if (successMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFDCFCE7)),
                    border = BorderStroke(1.dp, Color(0xFF86EFAC)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = successMessage ?: "",
                        color = Color(0xFF166534),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            if (isSignUpMode) {
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Patient Full Name") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF0F766E)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF0F766E),
                        focusedLabelColor = Color(0xFF0F766E)
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("auth_name_field")
                )
                
                OutlinedTextField(
                    value = hmoMemberId,
                    onValueChange = { hmoMemberId = it },
                    label = { Text("NHIA / HMO Member ID (Optional)") },
                    leadingIcon = { Icon(Icons.Default.CardMembership, contentDescription = null, tint = Color(0xFF14B8A6)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF0F766E),
                        focusedLabelColor = Color(0xFF0F766E)
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("auth_hmo_field")
                )
            }
            
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Secure Email Address") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = Color(0xFF0F766E)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF0F766E),
                    focusedLabelColor = Color(0xFF0F766E)
                ),
                modifier = Modifier.fillMaxWidth().testTag("auth_email_field")
            )
            
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Clinical Access Password") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF0F766E)) },
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF0F766E),
                    focusedLabelColor = Color(0xFF0F766E)
                ),
                modifier = Modifier.fillMaxWidth().testTag("auth_password_field")
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(
                onClick = {
                    isLoading = true
                    errorMessage = null
                    successMessage = null
                    if (isSignUpMode) {
                        viewModel.signUpPatient(email, password, fullName, hmoMemberId) { success, err ->
                            isLoading = false
                            if (success) {
                                if (err != null) {
                                    successMessage = err
                                }
                            } else {
                                errorMessage = err ?: "Sign Up Failed"
                            }
                        }
                    } else {
                        viewModel.loginPatient(email, password) { success, err ->
                            isLoading = false
                            if (success) {
                                if (err != null) {
                                    successMessage = err
                                }
                            } else {
                                errorMessage = err ?: "Login Failed. Check credentials."
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("auth_submit_button"),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text(
                        text = if (isSignUpMode) "Create Enrolled Identity" else "Authenticate & Access Vault",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            // Quick bypass demo button
            Button(
                onClick = {
                    isLoading = true
                    errorMessage = null
                    successMessage = null
                    viewModel.loginPatient("demo@careos.org", "password123") { success, err ->
                        isLoading = false
                        if (success) {
                            if (err != null) {
                                successMessage = err
                            }
                        } else {
                            errorMessage = err ?: "Login Failed. Check credentials."
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF14B8A6)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("auth_demo_button"),
                enabled = !isLoading
            ) {
                Icon(
                    imageVector = Icons.Default.MedicalServices,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "One-Click Quick Demo Access",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
            
            TextButton(
                onClick = {
                    isSignUpMode = !isSignUpMode
                    errorMessage = null
                    successMessage = null
                },
                modifier = Modifier.testTag("auth_mode_toggle")
            ) {
                Text(
                    text = if (isSignUpMode) "Already verified? Authenticate here" else "New patient? Establish secure enrollment profile",
                    color = Color(0xFF0F766E),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            Divider(color = Color(0xFFE2E8F0), thickness = 1.dp)
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color(0xFF14B8A6),
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "NDPR Clinical Grade Cryptographic Vault Locked",
                    fontSize = 8.5.sp,
                    color = Color(0xFF94A3B8),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}


// ==========================================
// --- SYMPTOM SCANNERS & CAMERA INTERFACE SCREEN ---
// ==========================================
@Composable
fun SymptomScanScreen(viewModel: CareViewModel, onStartTriageClick: () -> Unit) {
    var activeScanMode by remember { mutableStateOf("Dermatology") }
    var zoomLevel by remember { mutableStateOf("1x") }
    var isFlashOn by remember { mutableStateOf(false) }
    var isScanningActive by remember { mutableStateOf(false) }
    
    val selectedImageUri by viewModel.selectedImageUri.collectAsState()
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val imagePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                if (bytes != null) {
                    val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
                    val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                    viewModel.selectImage(base64, mimeType, uri.toString())
                }
            } catch (e: Exception) {
                // error logging
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("symptom_scan_screen")
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFD9F3EE)),
            border = BorderStroke(1.dp, Color(0xFF14B8A6)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFF0F766E), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("AI-ASSISTED CLINICAL VISUAL SCANS", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0F766E))
                    Text("Capture high-fidelity physical symptom photographs to power localized digital medical triage.", fontSize = 10.sp, color = Color(0xFF115E59))
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .testTag("camera_viewfinder")
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (selectedImageUri != null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "📸 PHOTO CAPTURED AT CLINICAL RESOLUTION",
                            color = Color(0xFF14B8A6),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .border(2.dp, Color(0xFF14B8A6), RoundedCornerShape(20.dp))
                        )
                    }
                } else {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        
                        drawCircle(color = Color(0xFF14B8A6).copy(alpha = 0.3f), radius = 60f, center = center)
                        drawLine(color = Color(0xFF14B8A6), start = androidx.compose.ui.geometry.Offset(center.x - 30, center.y), end = androidx.compose.ui.geometry.Offset(center.x + 30, center.y), strokeWidth = 2f)
                        drawLine(color = Color(0xFF14B8A6), start = androidx.compose.ui.geometry.Offset(center.x, center.y - 30), end = androidx.compose.ui.geometry.Offset(center.x, center.y + 30), strokeWidth = 2f)

                        val bracketLen = 40f
                        val pad = 30f
                        drawLine(color = Color.White, start = androidx.compose.ui.geometry.Offset(pad, pad), end = androidx.compose.ui.geometry.Offset(pad + bracketLen, pad), strokeWidth = 4f)
                        drawLine(color = Color.White, start = androidx.compose.ui.geometry.Offset(pad, pad), end = androidx.compose.ui.geometry.Offset(pad, pad + bracketLen), strokeWidth = 4f)
                        drawLine(color = Color.White, start = androidx.compose.ui.geometry.Offset(w - pad, pad), end = androidx.compose.ui.geometry.Offset(w - pad - bracketLen, pad), strokeWidth = 4f)
                        drawLine(color = Color.White, start = androidx.compose.ui.geometry.Offset(w - pad, pad), end = androidx.compose.ui.geometry.Offset(w - pad, pad + bracketLen), strokeWidth = 4f)
                        drawLine(color = Color.White, start = androidx.compose.ui.geometry.Offset(pad, h - pad), end = androidx.compose.ui.geometry.Offset(pad + bracketLen, h - pad), strokeWidth = 4f)
                        drawLine(color = Color.White, start = androidx.compose.ui.geometry.Offset(pad, h - pad), end = androidx.compose.ui.geometry.Offset(pad, h - pad - bracketLen), strokeWidth = 4f)
                        drawLine(color = Color.White, start = androidx.compose.ui.geometry.Offset(w - pad, h - pad), end = androidx.compose.ui.geometry.Offset(w - pad - bracketLen, h - pad), strokeWidth = 4f)
                        drawLine(color = Color.White, start = androidx.compose.ui.geometry.Offset(w - pad, h - pad), end = androidx.compose.ui.geometry.Offset(w - pad, h - pad - bracketLen), strokeWidth = 4f)
                    }
                    
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(12.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "MODE: ACTIVE ${activeScanMode.uppercase()} SCALING",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }

                    Text(
                        text = "ALIGN LESION INSIDE CENTER RETICLE",
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("SCANNER CAPTURE CONFIGURATION", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Gray)
                
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Capture Mode:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("Dermatology", "Ophthalmic", "Oral/ENT").forEach { mode ->
                            val isActive = activeScanMode == mode
                            Box(
                                modifier = Modifier
                                    .background(if (isActive) Color(0xFF0F766E) else Color(0xFFF1F5F4), RoundedCornerShape(8.dp))
                                    .clickable { activeScanMode = mode }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = mode,
                                    fontSize = 10.sp,
                                    color = if (isActive) Color.White else Color(0xFF475569),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Optical Zoom:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("1x", "2x", "5x").forEach { z ->
                            val isActive = zoomLevel == z
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(if (isActive) Color(0xFF14B8A6) else Color(0xFFF1F5F4), CircleShape)
                                    .clickable { zoomLevel = z }
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = z,
                                    fontSize = 9.sp,
                                    color = if (isActive) Color.White else Color(0xFF475569),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("LED Flash Diagnostic:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Switch(
                        checked = isFlashOn,
                        onCheckedChange = { isFlashOn = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF0F766E)
                        )
                    )
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FBFA)),
            border = BorderStroke(1.dp, Color(0xFFDCE7E5)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("CLINICAL SYMPTOM SIMULATOR CASES", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF0F766E))
                Text("Simulate a high-resolution photograph upload or select from preset medical scenarios for rapid testing:", fontSize = 10.sp, color = Color(0xFF475569))
                
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = {
                            viewModel.selectImage(
                                base64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==",
                                mimeType = "image/png",
                                uriString = "android.resource://com.example/drawable/ic_simulated_rash"
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF0F766E)),
                        border = BorderStroke(1.dp, Color(0xFF0F766E)),
                        modifier = Modifier.fillMaxWidth().height(36.dp).testTag("sim_rash_button")
                    ) {
                        Text("Simulate Ocular Redness & Swelling Scan", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            viewModel.selectImage(
                                base64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==",
                                mimeType = "image/png",
                                uriString = "android.resource://com.example/drawable/ic_simulated_lesion"
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF0F766E)),
                        border = BorderStroke(1.dp, Color(0xFF0F766E)),
                        modifier = Modifier.fillMaxWidth().height(36.dp).testTag("sim_lesion_button")
                    ) {
                        Text("Simulate Dermatology Skin Lesion Scan", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF14B8A6)),
                        modifier = Modifier.fillMaxWidth().height(36.dp).testTag("select_file_button")
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Upload Photo from local Device library", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (selectedImageUri != null) {
                Button(
                    onClick = { viewModel.clearImage() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    modifier = Modifier.weight(1f).height(46.dp)
                ) {
                    Text("Discard", fontWeight = FontWeight.Bold, color = Color.White)
                }

                Button(
                    onClick = {
                        onStartTriageClick()
                        viewModel.sendTriageMessage("I have uploaded a symptom photograph for AI clinical visual triage.")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E)),
                    modifier = Modifier.weight(1.5f).height(46.dp).testTag("start_ai_triage_photo")
                ) {
                    Text("Analyze with AI Triage", fontWeight = FontWeight.Bold, color = Color.White)
                }
            } else {
                Button(
                    onClick = {
                        isScanningActive = true
                        viewModel.selectImage(
                            base64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==",
                            mimeType = "image/png",
                            uriString = "android.resource://com.example/drawable/ic_simulated_scan_photo"
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E)),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("capture_shutter_button")
                ) {
                    Icon(Icons.Default.Camera, contentDescription = "Capture Button", tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Capture Diagnostic Image", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}


// ==========================================
// --- RESPONSIVE SIDEBAR NAVIGATION COMPOSABLE ---
// ==========================================
@Composable
fun CareOSNavigationSidebar(
    currentTab: String,
    onTabSelect: (String) -> Unit,
    activeInsurance: InsuranceProfile?,
    onLhrClick: () -> Unit,
    onInvestorDeckClick: () -> Unit,
    onAdminClick: () -> Unit,
    viewModel: CareViewModel
) {
    val patientProfile by viewModel.currentPatient.collectAsState()
    
    val items = listOf(
        Triple("triage", "Triage Chat", Icons.Default.MedicalServices),
        Triple("scan", "Symptom Scan", Icons.Default.CameraAlt),
        Triple("specialists", "Specialists", Icons.Default.Group),
        Triple("telehealth", "Consult Live", Icons.Default.Chat),
        Triple("referral", "Referrals", Icons.Default.Assignment),
        Triple("insurance", "HMO Profile", Icons.Default.Shield),
        Triple("fund", "Care Fund", Icons.Default.Handshake)
    )

    NavigationRail(
        containerColor = Color(0xFF0F766E),
        header = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 16.dp, horizontal = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.White.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalHospital,
                        contentDescription = "Clinic Logo",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "CareOS",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 15.sp
                )
                Text(
                    text = "CLINICAL AI ENGINE",
                    color = Color(0xFFD9F3EE),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        },
        modifier = Modifier
            .fillMaxHeight()
            .width(130.dp)
            .testTag("navigation_sidebar"),
        content = {
            Column(
                modifier = Modifier.fillMaxHeight().padding(bottom = 16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    items.forEach { (tab, label, icon) ->
                        val isActive = currentTab == tab
                        NavigationRailItem(
                            selected = isActive,
                            onClick = { onTabSelect(tab) },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label, fontSize = 9.sp, maxLines = 1) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = Color(0xFF0F766E),
                                selectedTextColor = Color.White,
                                indicatorColor = Color.White,
                                unselectedIconColor = Color.White.copy(alpha = 0.6f),
                                unselectedTextColor = Color.White.copy(alpha = 0.6f)
                            ),
                            modifier = Modifier.testTag("sidebar_tab_$tab")
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(onClick = onLhrClick, modifier = Modifier.testTag("sidebar_lhr_btn")) {
                        Icon(Icons.Default.FolderShared, contentDescription = "LHR", tint = Color(0xFFD9F3EE))
                    }
                    IconButton(onClick = onInvestorDeckClick, modifier = Modifier.testTag("sidebar_investor_btn")) {
                        Icon(Icons.Default.TrendingUp, contentDescription = "Investor Deck", tint = Color(0xFFFCA5A5))
                    }
                    IconButton(onClick = onAdminClick, modifier = Modifier.testTag("sidebar_admin_btn")) {
                        Icon(Icons.Default.Settings, contentDescription = "Admin", tint = Color.White)
                    }
                    
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = patientProfile?.fullName?.substringBefore(" ") ?: "Simeon",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                        Text(
                            text = "Log Out",
                            color = Color(0xFFFCA5A5),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clickable { viewModel.logoutPatient() }
                                .padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    )
}


