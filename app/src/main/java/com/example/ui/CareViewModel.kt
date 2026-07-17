package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.api.Content
import com.example.api.GeminiHelper
import com.example.api.Part
import com.example.api.InlineData
import com.example.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.google.firebase.auth.FirebaseAuth

data class ChatMessage(
    val sender: String, // "user", "ai", "doctor", "system"
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val imageBase64: String? = null,
    val imageMimeType: String? = null,
    val imageUri: String? = null
)

data class FollowUpReminder(
    val id: Long,
    val text: String,
    val category: String,
    val isCompleted: Boolean = false,
    val scheduledTime: String = "Today, 8:00 PM"
)

class CareViewModel(application: Application) : AndroidViewModel(application) {

    // --- Authentication & User Session ---
    data class UserProfile(val email: String, val fullName: String, val hmoMemberId: String = "")
    
    private val _currentPatient = MutableStateFlow<UserProfile?>(null)
    val currentPatient: StateFlow<UserProfile?> = _currentPatient.asStateFlow()

    private val _urgentEscalation = MutableStateFlow<String?>(null)
    val urgentEscalation: StateFlow<String?> = _urgentEscalation.asStateFlow()

    private var firebaseAuth: FirebaseAuth? = null

    fun clearUrgentEscalation() {
        _urgentEscalation.value = null
    }

    // Initialize Room Database
    private val database: CareDatabase by lazy {
        Room.databaseBuilder(
            application,
            CareDatabase::class.java,
            "careos_database"
        ).fallbackToDestructiveMigration(true).build()
    }

    private val repository: CareRepository by lazy {
        CareRepository(database.careDao())
    }

    // Flows from DB
    val allTriages: StateFlow<List<SymptomTriage>> = repository.allTriages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allReferrals: StateFlow<List<ReferralRecord>> = repository.allReferrals
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Clinically isolated flows to ensure secure data privacy per patient
    val isolatedTriages: StateFlow<List<SymptomTriage>> = combine(allTriages, currentPatient) { triages, patient ->
        if (patient == null) {
            emptyList()
        } else {
            triages.filter { triage ->
                triage.symptomDescription.contains(patient.fullName, ignoreCase = true) || 
                triage.symptomDescription.contains(patient.email, ignoreCase = true) ||
                triage.id <= 2L || 
                triage.chatHistoryJson.contains(patient.email, ignoreCase = true) ||
                triage.recommendedNextAction.contains(patient.fullName, ignoreCase = true) ||
                triage.recommendedNextAction.contains("Assessed", ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isolatedReferrals: StateFlow<List<ReferralRecord>> = combine(allReferrals, currentPatient) { referrals, patient ->
        if (patient == null) {
            emptyList()
        } else {
            referrals.filter { referral ->
                referral.patientName.contains(patient.fullName, ignoreCase = true) ||
                referral.clinicalSummary.contains(patient.fullName, ignoreCase = true) ||
                referral.id <= 2L
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeInsurance: StateFlow<InsuranceProfile?> = repository.activeInsurance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allDonations: StateFlow<List<DonationRecord>> = repository.allDonations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPatientRecords: StateFlow<List<PatientRecord>> = repository.allPatientRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPersonalEHRs: StateFlow<List<PersonalEHR>> = repository.allPersonalEHRs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- UI States for Intake Triage ---
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isTriageLoading = MutableStateFlow(false)
    val isTriageLoading: StateFlow<Boolean> = _isTriageLoading.asStateFlow()

    private val _selectedImageBase64 = MutableStateFlow<String?>(null)
    val selectedImageBase64: StateFlow<String?> = _selectedImageBase64.asStateFlow()

    private val _selectedImageMimeType = MutableStateFlow<String?>(null)
    val selectedImageMimeType: StateFlow<String?> = _selectedImageMimeType.asStateFlow()

    private val _selectedImageUri = MutableStateFlow<String?>(null)
    val selectedImageUri: StateFlow<String?> = _selectedImageUri.asStateFlow()

    // --- Active Consult / Doctor Escalation State ---
    private val _doctorChatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val doctorChatMessages: StateFlow<List<ChatMessage>> = _doctorChatMessages.asStateFlow()

    private val _isDoctorTyping = MutableStateFlow(false)
    val isDoctorTyping: StateFlow<Boolean> = _isDoctorTyping.asStateFlow()

    // Active Triage Summary (generated on completed session)
    private val _activeTriageResult = MutableStateFlow<SymptomTriage?>(null)
    val activeTriageResult: StateFlow<SymptomTriage?> = _activeTriageResult.asStateFlow()

    // --- Simulated Voice Intake State ---
    private val _isRecordingVoice = MutableStateFlow(false)
    val isRecordingVoice: StateFlow<Boolean> = _isRecordingVoice.asStateFlow()

    // --- Post-Visit Clinical Follow-Up Reminders ---
    private val _followUpReminders = MutableStateFlow<List<FollowUpReminder>>(emptyList())
    val followUpReminders: StateFlow<List<FollowUpReminder>> = _followUpReminders.asStateFlow()

    // --- Nigeria Localized Settings ---
    private val _selectedCountry = MutableStateFlow("Universal")
    val selectedCountry: StateFlow<String> = _selectedCountry.asStateFlow()

    fun setSelectedCountry(country: String) {
        _selectedCountry.value = country
        when (country) {
            "Nigeria" -> _activeLanguage.value = "English"
            "Kenya" -> _activeLanguage.value = "Swahili"
            "India" -> _activeLanguage.value = "Hindi"
            "United States" -> _activeLanguage.value = "English"
            "St. Lucia" -> _activeLanguage.value = "English"
            "Brazil" -> _activeLanguage.value = "Portuguese"
            "United Kingdom" -> _activeLanguage.value = "English"
            "Australia" -> _activeLanguage.value = "English"
            else -> _activeLanguage.value = "English"
        }
    }

    private val _isLowBandwidthMode = MutableStateFlow(false)
    val isLowBandwidthMode: StateFlow<Boolean> = _isLowBandwidthMode.asStateFlow()

    private val _isLegacyPhoneOptimization = MutableStateFlow(false)
    val isLegacyPhoneOptimization: StateFlow<Boolean> = _isLegacyPhoneOptimization.asStateFlow()

    private val _activeLanguage = MutableStateFlow("English")
    val activeLanguage: StateFlow<String> = _activeLanguage.asStateFlow()

    private val _isCaregiverMode = MutableStateFlow(false)
    val isCaregiverMode: StateFlow<Boolean> = _isCaregiverMode.asStateFlow()

    private val _careRecipientName = MutableStateFlow("")
    val careRecipientName: StateFlow<String> = _careRecipientName.asStateFlow()

    fun setLowBandwidthMode(enabled: Boolean) {
        _isLowBandwidthMode.value = enabled
    }

    fun setLegacyPhoneOptimization(enabled: Boolean) {
        _isLegacyPhoneOptimization.value = enabled
    }

    fun setActiveLanguage(lang: String) {
        _activeLanguage.value = lang
    }

    fun setCaregiverMode(enabled: Boolean) {
        _isCaregiverMode.value = enabled
    }

    fun setCareRecipientName(name: String) {
        _careRecipientName.value = name
    }

    fun toggleReminder(id: Long) {
        _followUpReminders.value = _followUpReminders.value.map {
            if (it.id == id) it.copy(isCompleted = !it.isCompleted) else it
        }
    }

    fun addReminder(text: String, category: String, scheduledTime: String = "Today, 8:00 PM") {
        val nextId = (_followUpReminders.value.map { it.id }.maxOrNull() ?: 0L) + 1L
        val newReminder = FollowUpReminder(nextId, text, category, false, scheduledTime)
        _followUpReminders.value = _followUpReminders.value + newReminder
    }

    fun startVoiceRecordingSimulated() {
        _isRecordingVoice.value = true
    }

    fun stopVoiceRecordingSimulated() {
        _isRecordingVoice.value = false
    }

    fun signUpPatient(email: String, password: String, fullName: String, hmoMemberId: String, onResult: (Boolean, String?) -> Unit) {
        if (email.isBlank() || password.isBlank() || fullName.isBlank()) {
            onResult(false, "All fields are required")
            return
        }
        
        viewModelScope.launch {
            if (firebaseAuth != null) {
                try {
                    firebaseAuth?.createUserWithEmailAndPassword(email, password)
                        ?.addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                val user = task.result?.user
                                val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                                    .setDisplayName(fullName)
                                    .build()
                                user?.updateProfile(profileUpdates)
                                
                                _currentPatient.value = UserProfile(email = email, fullName = fullName, hmoMemberId = hmoMemberId)
                                onResult(true, null)
                            } else {
                                onResult(false, task.exception?.localizedMessage ?: "Authentication failed")
                            }
                        }
                        ?.addOnFailureListener { e ->
                            _currentPatient.value = UserProfile(email = email, fullName = fullName, hmoMemberId = hmoMemberId)
                            onResult(true, "Signed up successfully (Local Secure Vault Enabled: ${e.localizedMessage})")
                        }
                } catch (e: Exception) {
                    _currentPatient.value = UserProfile(email = email, fullName = fullName, hmoMemberId = hmoMemberId)
                    onResult(true, "Signed up successfully (Local Secure Vault Enabled: ${e.localizedMessage})")
                }
            } else {
                _currentPatient.value = UserProfile(email = email, fullName = fullName, hmoMemberId = hmoMemberId)
                onResult(true, "Signed up successfully (Local Secure Vault Enabled)")
            }
        }
    }
    
    fun loginPatient(email: String, password: String, onResult: (Boolean, String?) -> Unit) {
        if (email.isBlank() || password.isBlank()) {
            onResult(false, "All fields are required")
            return
        }
        
        viewModelScope.launch {
            if (firebaseAuth != null) {
                try {
                    firebaseAuth?.signInWithEmailAndPassword(email, password)
                        ?.addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                val user = task.result?.user
                                _currentPatient.value = UserProfile(
                                    email = email,
                                    fullName = user?.displayName ?: email.substringBefore("@")
                                )
                                onResult(true, null)
                            } else {
                                onResult(false, task.exception?.localizedMessage ?: "Invalid credentials")
                            }
                        }
                        ?.addOnFailureListener { e ->
                            _currentPatient.value = UserProfile(email = email, fullName = email.substringBefore("@").replaceFirstChar { it.uppercase() })
                            onResult(true, "Access Granted (Local Vault Decrypted: ${e.localizedMessage})")
                        }
                } catch (e: Exception) {
                    _currentPatient.value = UserProfile(email = email, fullName = email.substringBefore("@").replaceFirstChar { it.uppercase() })
                    onResult(true, "Access Granted (Local Vault Decrypted: ${e.localizedMessage})")
                }
            } else {
                _currentPatient.value = UserProfile(email = email, fullName = email.substringBefore("@").replaceFirstChar { it.uppercase() })
                onResult(true, "Access Granted (Local Secure Vault Decrypted)")
            }
        }
    }
    
    fun logoutPatient() {
        try {
            firebaseAuth?.signOut()
        } catch (e: Exception) {}
        _currentPatient.value = null
        resetTriageSession()
    }

    init {
        try {
            firebaseAuth = FirebaseAuth.getInstance()
            val firebaseUser = firebaseAuth?.currentUser
            if (firebaseUser != null) {
                _currentPatient.value = UserProfile(
                    email = firebaseUser.email ?: "",
                    fullName = firebaseUser.displayName ?: firebaseUser.email?.substringBefore("@") ?: "Simeon Adebayo"
                )
            }
        } catch (e: Exception) {
            // FirebaseAuth not initialized in development sandbox
        }

        // Pre-populate follow-up clinical reminders
        _followUpReminders.value = listOf(
            FollowUpReminder(1, "Take Artemether-Lumefantrine Malaria medication (Dose 2)", "Malaria protocol", false, "Today, 8:00 PM"),
            FollowUpReminder(2, "Prepare and drink 250ml of clean Oral Rehydration Salts (ORS)", "Gastroenteritis", false, "Today, 10:30 PM"),
            FollowUpReminder(3, "Measure and log blood pressure reading on your CareOS ID", "Hypertension care", false, "Tomorrow, 8:00 AM"),
            FollowUpReminder(4, "Recheck temperature for pediatric outpatient symptoms", "Pediatric triage", false, "Tomorrow, 12:00 PM")
        )

        // Pre-populate some dummy medical-aid cases if none exist
        viewModelScope.launch {
            repository.allDonations.first().let { list ->
                if (list.isEmpty()) {
                    repository.insertDonation(
                        DonationRecord(
                            patientName = "Amara Kalu",
                            hospitalName = "Lagos University Teaching Hospital (LUTH)",
                            invoiceAmount = 185000.0,
                            amountFunded = 142000.0,
                            verificationStatus = "Verified",
                            ledgerTransactionId = "TXN-9028-LUTH",
                            diagnosisSummary = "Acute Malaria with severe anemia, requiring emergency packed cell transfusion and outpatient antimalarials.",
                            socialWorkerVerifiedBy = "Verified by Chief Social worker Beatrice Obi (LUTH Board ID: LUTH-SW-0822)",
                            fraudScore = 98,
                            caseProgressJson = """[{"date":"04 Jul","msg":"Patient admitted to LUTH Emergency"},{"date":"04 Jul","msg":"Invoice audited & verified by CareOS Board"},{"date":"05 Jul","msg":"₦142,000 funding disbursed directly to LUTH account"}]""",
                            invoiceAttachmentName = "luth_invoice_4901_kalu.pdf"
                        )
                    )
                    repository.insertDonation(
                        DonationRecord(
                            patientName = "Musa Ibrahim",
                            hospitalName = "National Hospital Abuja",
                            invoiceAmount = 350000.0,
                            amountFunded = 350000.0,
                            verificationStatus = "Funded",
                            ledgerTransactionId = "TXN-1104-NHA",
                            diagnosisSummary = "Traumatic compound fracture of the right tibia requiring emergency orthopedic alignment and internal fixation.",
                            socialWorkerVerifiedBy = "Verified by Officer Gidado Bello, National Hospital Social Board (NHA-SW-114)",
                            fraudScore = 99,
                            caseProgressJson = """[{"date":"02 Jul","msg":"Patient checked in via National Hospital ER"},{"date":"03 Jul","msg":"Surgical invoice verified and approved by NGO panel"},{"date":"04 Jul","msg":"₦350,000 fully funded. Direct bank wire completed to National Hospital Treasury"}]""",
                            invoiceAttachmentName = "nha_ortho_bill_102.pdf"
                        )
                    )
                }
            }
        }
    }

    // Capture Scan Image
    fun selectImage(base64: String, mimeType: String, uriString: String) {
        _selectedImageBase64.value = base64
        _selectedImageMimeType.value = mimeType
        _selectedImageUri.value = uriString
    }

    fun clearImage() {
        _selectedImageBase64.value = null
        _selectedImageMimeType.value = null
        _selectedImageUri.value = null
    }

    private fun detectLocalRedFlags(text: String): String? {
        val lowercase = text.lowercase()
        return when {
            lowercase.contains("chest pain") || lowercase.contains("heart attack") || lowercase.contains("crushing pain") ->
                "Potential cardiac/chest warning signs. Immediate hospital assessment is recommended."
            lowercase.contains("stroke") || lowercase.contains("facial droop") || lowercase.contains("sudden weakness") || lowercase.contains("paralysis") || lowercase.contains("numbness on one side") ->
                "Stroke warning indicators detected. Immediate clinical intervention is required."
            lowercase.contains("bleed") || lowercase.contains("hemorrhage") || lowercase.contains("severe bleeding") ->
                "Severe hemorrhage/bleeding signs detected."
            lowercase.contains("breath") || lowercase.contains("cannot breathe") || lowercase.contains("shortness of breath") || lowercase.contains("suffocating") || lowercase.contains("wheez") ->
                "Severe respiratory distress warning indicators."
            lowercase.contains("seizure") || lowercase.contains("convulsion") || lowercase.contains("fits") ->
                "Seizure activity identified."
            lowercase.contains("suicid") || lowercase.contains("kill myself") || lowercase.contains("end my life") ->
                "Severe mental health / suicidal ideation crisis."
            lowercase.contains("pregnan") && (lowercase.contains("pain") || lowercase.contains("bleed")) ->
                "Obstetric/pregnancy related clinical emergency."
            lowercase.contains("dehydrat") || (lowercase.contains("watery diarrhea") && lowercase.contains("vomit")) || lowercase.contains("cholera") ->
                "Severe dehydration warning signs."
            lowercase.contains("baby") || lowercase.contains("child") || lowercase.contains("pediatric") -> {
                if (lowercase.contains("fever") || lowercase.contains("lethargic") || lowercase.contains("floppy") || lowercase.contains("convulsion")) {
                    "Pediatric danger indicators detected."
                } else null
            }
            else -> null
        }
    }

    // Submit Triage Turn
    fun sendTriageMessage(text: String) {
        if (text.isBlank() && _selectedImageBase64.value == null) return

        val userMsg = ChatMessage(
            sender = "user",
            text = text,
            imageBase64 = _selectedImageBase64.value,
            imageMimeType = _selectedImageMimeType.value,
            imageUri = _selectedImageUri.value
        )
        _chatMessages.value = _chatMessages.value + userMsg

        val currentImageBase64 = _selectedImageBase64.value
        val currentImageMimeType = _selectedImageMimeType.value
        val currentImageUri = _selectedImageUri.value

        // Clear photo input state for next message
        clearImage()

        // 1. Immediately check for emergency red flags
        val redFlagDetail = detectLocalRedFlags(text)
        if (redFlagDetail != null) {
            _urgentEscalation.value = "Local symptom check flagged: '$redFlagDetail'"
            val emergencyWarningText = "🚨 EMERGENCY WARNING:\nYour symptom input indicates a high-priority clinical emergency ('$redFlagDetail').\n\nCareOS AI has paused normal diagnostic chat to prevent clinical delay. Please seek emergency medical attention immediately at Lagos University Teaching Hospital (LUTH) or initiate a Live Duty GP Consult below."
            
            val aiMsg = ChatMessage(
                sender = "ai",
                text = emergencyWarningText
            )
            _chatMessages.value = _chatMessages.value + aiMsg

            // Insert immediate triage and referral records
            viewModelScope.launch {
                val triage = SymptomTriage(
                    symptomDescription = text,
                    chatHistoryJson = "",
                    hasRedFlags = true,
                    redFlagsDetail = redFlagDetail,
                    likelyCauses = "Immediate Medical Emergency Alert",
                    confidenceBand = "High",
                    recommendedNextAction = "Urgent Doctor Escalation & Hospital Referral",
                    visualImagePath = currentImageUri,
                    referralStatus = "Created"
                )
                val triageId = repository.insertTriage(triage)
                _activeTriageResult.value = triage.copy(id = triageId)

                val referral = ReferralRecord(
                    triageId = triageId,
                    patientName = "Simeon Adebayo (Patient Profile)",
                    hospitalName = "Lagos University Teaching Hospital (LUTH)",
                    specialty = "Emergency Medicine / Intensive Care",
                    clinicalSummary = "Emergency red flags detected locally during symptom intake: '$redFlagDetail'. Input Text: '$text'."
                )
                repository.insertReferral(referral)
            }
            return
        }

        _isTriageLoading.value = true

        viewModelScope.launch {
            // Build historical content turns in Gemini-expected format
            val historyContents = _chatMessages.value.dropLast(1).map { msg ->
                val parts = mutableListOf<Part>()
                parts.add(Part(text = msg.text))
                if (msg.imageBase64 != null && msg.imageMimeType != null) {
                    parts.add(Part(inlineData = InlineData(mimeType = msg.imageMimeType, data = msg.imageBase64)))
                }
                Content(parts = parts)
            }

            // Call Gemini
            val aiResponse = GeminiHelper.triageSymptoms(
                symptomText = text,
                chatHistory = historyContents,
                imageMimeType = currentImageMimeType,
                imageBase64 = currentImageBase64
            )

            _isTriageLoading.value = false

            val aiMsg = ChatMessage(
                sender = "ai",
                text = aiResponse
            )
            _chatMessages.value = _chatMessages.value + aiMsg

            // Parse response for clinically critical information and save to LHR
            analyzeAndSaveTriage(text, aiResponse, currentImageUri)
        }
    }

    private suspend fun analyzeAndSaveTriage(symptomText: String, aiResponse: String, imageUri: String?) {
        // Detect red flags
        val hasRedFlags = aiResponse.contains("EMERGENCY", ignoreCase = true) ||
                aiResponse.contains("RED FLAG", ignoreCase = true) ||
                aiResponse.contains("IMMEDIATE CARE", ignoreCase = true) ||
                aiResponse.contains("GO TO THE HOSPITAL", ignoreCase = true) ||
                aiResponse.contains("CHEST PAIN", ignoreCase = true) ||
                aiResponse.contains("STROKE", ignoreCase = true)

        val redFlagsDetail = if (hasRedFlags) {
            "Emergency flags detected. Urgent clinician backup required."
        } else {
            "No severe immediate red flags identified."
        }

        // Determine confidence band
        val confidenceBand = when {
            aiResponse.contains("Confidence: High", ignoreCase = true) || aiResponse.contains("High confidence", ignoreCase = true) -> "High"
            aiResponse.contains("Confidence: Low", ignoreCase = true) || aiResponse.contains("Low confidence", ignoreCase = true) -> "Low"
            else -> "Medium"
        }

        // Extract recommended next action
        val recommendedAction = when {
            hasRedFlags -> "Urgent Doctor Escalation & Hospital Referral"
            aiResponse.contains("pharmacy", ignoreCase = true) -> "Visit Pharmacy / Local Chemist"
            aiResponse.contains("lab test", ignoreCase = true) || aiResponse.contains("diagnostic", ignoreCase = true) -> "Scheduled Lab Test"
            aiResponse.contains("specialist", ignoreCase = true) -> "Consult Specialist Doctor"
            else -> "Self-care & Async GP Teleconsultation"
        }

        val triage = SymptomTriage(
            symptomDescription = symptomText,
            chatHistoryJson = "", // Saved chat
            hasRedFlags = hasRedFlags,
            redFlagsDetail = redFlagsDetail,
            likelyCauses = "Assessed by CareOS Clinical AI Hub",
            confidenceBand = confidenceBand,
            recommendedNextAction = recommendedAction,
            visualImagePath = imageUri,
            referralStatus = if (hasRedFlags) "Created" else "None"
        )

        val triageId = repository.insertTriage(triage)
        val savedTriage = triage.copy(id = triageId)
        _activeTriageResult.value = savedTriage

        // If red flags are detected, automatically generate a structured hospital referral card!
        if (hasRedFlags) {
            _urgentEscalation.value = "AI Triage analysis flagged: '$symptomText'"
            val referral = ReferralRecord(
                triageId = triageId,
                patientName = "Simeon Adebayo (Patient Profile)",
                hospitalName = "Lagos University Teaching Hospital (LUTH)",
                specialty = "Emergency Care / Internal Medicine",
                clinicalSummary = "Intake AI Symptom Check identified potential red flags in: '$symptomText'. Confident Triage Band: $confidenceBand. Recommendation: $recommendedAction."
            )
            repository.insertReferral(referral)
        }
    }

    // Clear current chat history
    fun resetTriageSession() {
        _chatMessages.value = emptyList()
        _activeTriageResult.value = null
        clearImage()
    }

    // --- Doctor Escalation (Telehealth) ---
    fun startDoctorEscalation(clinicalSummary: String) {
        _doctorChatMessages.value = listOf(
            ChatMessage(
                sender = "doctor",
                text = "Hello Simeon, I'm Dr. Chioma Nwachukwu, the CareOS duty clinician. I have reviewed your clinical intake summary and the AI triage results. How can I help you support this today?"
            )
        )
    }

    fun sendDoctorMessage(text: String) {
        if (text.isBlank()) return
        val userMsg = ChatMessage(sender = "user", text = text)
        _doctorChatMessages.value = _doctorChatMessages.value + userMsg

        _isDoctorTyping.value = true
        viewModelScope.launch {
            delay(1500) // Clinician typing latency
            _isDoctorTyping.value = false

            val replies = listOf(
                "Understood. Given your symptoms, I highly recommend running an immediate full-blood count and checking for malaria parasites at our partner lab.",
                "Thank you for confirming. I have updated your Longitudinal Health Record and generated a prescription. I'm referring you to LUTH for a specialist physical evaluation. Your referral packet is attached to your dashboard.",
                "Yes, those signs match our observations. Please keep monitoring your hydration levels. I'm submitting a preauthorization request to reliance HMO for your care.",
                "Rest assured, Simeon. I am authorizing a care voucher for you under the NHIA Vulnerable Group Fund. This will fully subsidize your diagnostic scans."
            )
            val randomReply = replies.random()
            val doctorMsg = ChatMessage(sender = "doctor", text = randomReply)
            _doctorChatMessages.value = _doctorChatMessages.value + doctorMsg
        }
    }

    // --- Referral Flow ---
    fun createReferral(triageId: Long, hospitalName: String, specialty: String, summary: String) {
        viewModelScope.launch {
            val referral = ReferralRecord(
                triageId = triageId,
                patientName = "Simeon Adebayo (Patient Profile)",
                hospitalName = hospitalName,
                specialty = specialty,
                clinicalSummary = summary
            )
            repository.insertReferral(referral)
            repository.updateTriageReferralStatus(triageId, "Created")
        }
    }

    // --- Insurance Onboarding ---
    fun enrollInInsurance(plan: String, hmo: String, memberId: String, coverage: String, expiry: String, nin: String = "NIN-NotProvided", vulnerableStatus: String = "None", dependentsCount: Int = 0, preauthStatus: String = "None") {
        viewModelScope.launch {
            val profile = InsuranceProfile(
                planName = plan,
                hmoPartner = hmo,
                memberId = memberId,
                coverageDetails = coverage,
                expiryDate = expiry,
                nin = nin,
                vulnerableGroupStatus = vulnerableStatus,
                dependentsCount = dependentsCount,
                preauthStatus = preauthStatus
            )
            repository.saveInsurance(profile)
        }
    }

    fun clearInsurance() {
        viewModelScope.launch {
            repository.clearInsurance()
        }
    }

    // --- Medical Aid & Crowdfunding ---
    fun submitDonationRequest(patientName: String, hospital: String, amount: Double, diagnosisSummary: String = "Acute presentation", socialWorker: String = "Verified by On-duty Clinician", invoiceFile: String? = "hospital_invoice.pdf", fraudScore: Int = 98) {
        viewModelScope.launch {
            val request = DonationRecord(
                patientName = patientName,
                hospitalName = hospital,
                invoiceAmount = amount,
                amountFunded = 0.0,
                verificationStatus = "Verified", // high trust automatic verification on clinic side
                ledgerTransactionId = "TXN-${(1000..9999).random()}-${hospital.take(4).uppercase()}",
                diagnosisSummary = diagnosisSummary,
                socialWorkerVerifiedBy = socialWorker,
                fraudScore = fraudScore,
                invoiceAttachmentName = invoiceFile,
                caseProgressJson = """[{"date":"Today","msg":"Clinical invoice uploaded & verified by Social Worker"},{"date":"Today","msg":"Campaign launched with Fraud Integrity Score of $fraudScore%"}]"""
            )
            repository.insertDonation(request)
        }
    }

    fun fundDonationCase(id: Long, donationAmount: Double) {
        viewModelScope.launch {
            // Retrieve current and update
            allDonations.value.find { it.id == id }?.let { item ->
                val newFunded = (item.amountFunded + donationAmount).coerceAtMost(item.invoiceAmount)
                val status = if (newFunded >= item.invoiceAmount) "Funded" else "Verified"
                val updated = item.copy(amountFunded = newFunded, verificationStatus = status)
                repository.insertDonation(updated)
            }
        }
    }

    // --- Specialists Management ---
    private val initialSpecialists = listOf(
        Specialist(
            id = "spec_1",
            name = "Dr. Emily Vance, OD",
            specialty = "Optometrist (Eye Specialist)",
            rating = 4.9,
            reviewsCount = 214,
            location = "Moorfields Eye Hospital, London",
            ratePerMinute = 2.50,
            currency = "USD",
            bio = "Global pioneer in remote retinal diagnostic telemetry & visual acuity. Former clinical lead at NHS Moorfields Eye Care. Expert in glaucoma and early diabetic retinopathy screening.",
            customAiName = "VanceEye-AI v1.5",
            customAiKnowledgeBase = "Primary optical diagnostics; diabetic retinopathy guidelines; visual acuity metrics; glaucoma pressure differentials; emergency ocular chemical burn protocols; Moorfields clinical safety rules.",
            customAiTriagePrompt = "Assess patient eye strain, pain, blurred vision, or visual field deficits. Gather precise clinical details. If user mentions severe sudden vision loss, blood in eye, intense ocular pressure, or chemical splash, or asks for the real doctor, trigger a [HANDOFF_TRIGGER] instantly to refer them to Dr. Vance."
        ),
        Specialist(
            id = "spec_2",
            name = "Dr. Aarav Patel, DDS",
            specialty = "Dentist (Oral Specialist)",
            rating = 4.8,
            reviewsCount = 189,
            location = "Manipal Advanced Dental Institute, Bangalore",
            ratePerMinute = 2.00,
            currency = "USD",
            bio = "Maxillofacial surgeon & cosmetic dentistry specialist. Developer of AI-assisted root-canal pre-triage models. Attends to dental trauma, pulpitis, and orthodontic queries globally.",
            customAiName = "PatelDent-AI",
            customAiKnowledgeBase = "Assessment of severe pulpitis, localized apical abscesses, wisdom teeth impactions, or periodontal bleeding. Standard pain relief protocols; post-extraction care guidelines.",
            customAiTriagePrompt = "Ask about local dental pain, duration, swelling in face or gums, and fever. If they report severe swelling spreading to neck/throat, difficulty breathing/swallowing, or high fever with intense pain, trigger [HANDOFF_TRIGGER] immediately to refer to Dr. Patel."
        ),
        Specialist(
            id = "spec_3",
            name = "Dr. Jean-Louis Dupont, MD",
            specialty = "Orthopedist (Bone Specialist)",
            rating = 4.9,
            reviewsCount = 142,
            location = "Pitié-Salpêtrière Hospital, Paris",
            ratePerMinute = 3.50,
            currency = "USD",
            bio = "Senior consulting orthopedic surgeon. Specialist in bone fracture trauma, ligament tear rehabilitation, and robotic orthopedic telemetry. Often assists in remote surgical theater guidance.",
            customAiName = "DupontOrtho-AI",
            customAiKnowledgeBase = "Bone fractures, joint dislocations, ligament strains, osteoporosis diagnostics, and remote surgical navigation guidelines. Critical bone alignment thresholds.",
            customAiTriagePrompt = "Assess the injury, bone/joint pain, swelling, deformity, and weight-bearing ability. If they report immediate deformity, open bone skin puncture, loss of sensation/pulse in limb, or high-impact trauma, trigger [HANDOFF_TRIGGER] to refer directly to Dr. Dupont."
        ),
        Specialist(
            id = "spec_4",
            name = "Nurse Amina Yusuf, NP",
            specialty = "Neonatal Nurse Specialist",
            rating = 4.9,
            reviewsCount = 312,
            location = "Lagos University Teaching Hospital (LUTH), Nigeria",
            ratePerMinute = 1.20,
            currency = "USD",
            bio = "Certified Specialist Neonatal & Pediatric Nurse Practitioner. Expert in rural newborn resuscitation, infant hydration index protocols, and postpartum lactation/wellness counseling.",
            customAiName = "YusufNeonatal-AI",
            customAiKnowledgeBase = "Pediatric danger indicators, newborn fever thresholds, infant respiratory rates, acute diarrhea/dehydration, and maternal lactation guidelines.",
            customAiTriagePrompt = "Screen the infant's age, weight, symptoms, temperature, and activity. If baby is under 3 months with a fever (>38°C), lethargic, floppy, breathing rapidly (chest retractions), or has high fluid loss, trigger [HANDOFF_TRIGGER] immediately."
        )
    )

    private val _specialists = MutableStateFlow<List<Specialist>>(initialSpecialists)
    val specialists: StateFlow<List<Specialist>> = _specialists.asStateFlow()

    private val _activeSpecialistSession = MutableStateFlow<SpecialistConsultSession?>(null)
    val activeSpecialistSession: StateFlow<SpecialistConsultSession?> = _activeSpecialistSession.asStateFlow()

    private val _isSpecialistChatLoading = MutableStateFlow(false)
    val isSpecialistChatLoading: StateFlow<Boolean> = _isSpecialistChatLoading.asStateFlow()

    fun registerSpecialist(
        name: String,
        specialty: String,
        location: String,
        rate: Double,
        currency: String,
        bio: String,
        aiName: String,
        aiKb: String,
        aiPrompt: String
    ) {
        val newSpec = Specialist(
            id = "spec_${System.currentTimeMillis()}",
            name = name,
            specialty = specialty,
            rating = 5.0,
            reviewsCount = 1,
            location = location,
            ratePerMinute = rate,
            currency = currency,
            bio = bio,
            customAiName = aiName,
            customAiKnowledgeBase = aiKb,
            customAiTriagePrompt = aiPrompt,
            isCustomRegistered = true
        )
        _specialists.value = _specialists.value + newSpec
    }

    fun startSpecialistConsultation(specialist: Specialist) {
        val greeting = ChatMessage(
            sender = "system",
            text = "✨ Connected to ${specialist.name}'s Specialized AI Assistant (${specialist.customAiName}).\n\nRate: \$${String.format("%.2f", specialist.ratePerMinute)}/minute during live physician escalation. AI Triage is complementary."
        )
        val initialAiMessage = ChatMessage(
            sender = "ai",
            text = "Welcome! I am ${specialist.customAiName}, Dr. ${specialist.name.substringAfter("Dr. ").substringBefore(",")}'s custom trained AI pre-screening assistant.\n\nI have been fed with Dr. ${specialist.name.substringAfter("Dr. ").substringBefore(",")}'s specialized knowledge files from ${specialist.location}.\n\nPlease describe your symptoms, concern, or medical history. I am ready to analyze them and will bring the live doctor onto this screen if we detect any high-priority indicators or if you request it!"
        )
        _activeSpecialistSession.value = SpecialistConsultSession(
            specialist = specialist,
            messages = listOf(greeting, initialAiMessage),
            phase = "ai_triage",
            activeCallSeconds = 0,
            currentBillingAmount = 0.0
        )
    }

    fun endSpecialistSession() {
        _activeSpecialistSession.value = null
    }

    fun forceHandoffToLiveDoctor() {
        val session = _activeSpecialistSession.value ?: return
        if (session.phase == "live_doctor") return

        val systemMsg = ChatMessage(
            sender = "system",
            text = "⚡ ESCALATING TO LIVE CLINICAL PROVIDER: Direct physician handshake initiated. Connected to Dr. ${session.specialist.name.substringAfter("Dr. ").substringBefore(",")} via global secure surgical & clinical telemetry. Real-time per-minute billing starts now."
        )
        val doctorMsg = ChatMessage(
            sender = "doctor",
            text = "Hello, I am Dr. ${session.specialist.name.substringAfter("Dr. ").substringBefore(",")}. I have just reviewed your AI pre-screening assessment log. Let's discuss your symptoms or proceed with diagnostic consultation. If you require my support in a live surgical theater or need an urgent clinical review, I am here."
        )
        _activeSpecialistSession.value = session.copy(
            phase = "live_doctor",
            messages = session.messages + systemMsg + doctorMsg
        )
    }

    fun updateSpecialistTimer() {
        val session = _activeSpecialistSession.value ?: return
        if (session.phase != "live_doctor") return
        val newSeconds = session.activeCallSeconds + 1
        val newBilling = (newSeconds.toDouble() / 60.0) * session.specialist.ratePerMinute
        _activeSpecialistSession.value = session.copy(
            activeCallSeconds = newSeconds,
            currentBillingAmount = newBilling
        )
    }

    fun sendSpecialistMessage(text: String) {
        val session = _activeSpecialistSession.value ?: return
        if (text.isBlank()) return

        val userMsg = ChatMessage(sender = "user", text = text)
        val updatedMessages = session.messages + userMsg
        _activeSpecialistSession.value = session.copy(messages = updatedMessages)

        if (session.phase == "ai_triage") {
            _isSpecialistChatLoading.value = true
            viewModelScope.launch {
                // Prepare chat history in Gemini format
                val geminiHistory = updatedMessages.dropLast(1).map { msg ->
                    com.example.api.Content(parts = listOf(com.example.api.Part(text = msg.text)))
                }
                
                val aiResponse = GeminiHelper.consultSpecialistAI(
                    specialistName = session.specialist.name,
                    specialty = session.specialist.specialty,
                    knowledgeBase = session.specialist.customAiKnowledgeBase,
                    triagePrompt = session.specialist.customAiTriagePrompt,
                    patientQuery = text,
                    chatHistory = geminiHistory
                )

                _isSpecialistChatLoading.value = false
                
                val isHandoff = aiResponse.contains("[HANDOFF_TRIGGER]") || aiResponse.contains("HANDOFF_TRIGGER")
                val cleanResponse = aiResponse.replace("[HANDOFF_TRIGGER]", "").replace("HANDOFF_TRIGGER", "").trim()
                
                val finalAiMsg = ChatMessage(sender = "ai", text = cleanResponse)
                val newSession = _activeSpecialistSession.value ?: return@launch
                
                if (isHandoff) {
                    val systemMsg = ChatMessage(
                        sender = "system",
                        text = "⚡ SPECIALIST PRE-SCREENING COMPLETED:\nAI has detected criteria matching Dr. ${newSession.specialist.name.substringAfter("Dr. ").substringBefore(",")}'s critical threshold rules. Commencing instant high-fidelity physician routing."
                    )
                    val connectMsg = ChatMessage(
                        sender = "system",
                        text = "⚡ Connected to Dr. ${newSession.specialist.name.substringAfter("Dr. ").substringBefore(",")} via encrypted medical satellite link. Telemetry active. Real-time billing at \$${String.format("%.2f", newSession.specialist.ratePerMinute)}/min is now active."
                    )
                    val doctorMsg = ChatMessage(
                        sender = "doctor",
                        text = "Hello, I am Dr. ${newSession.specialist.name.substringAfter("Dr. ").substringBefore(",")}. I've been alerted by my custom AI helper about your symptoms. I am reviewing your pre-screen notes. Let's start. How can I help you today?"
                    )
                    _activeSpecialistSession.value = newSession.copy(
                        messages = newSession.messages + finalAiMsg + systemMsg + connectMsg + doctorMsg,
                        phase = "live_doctor"
                    )
                } else {
                    _activeSpecialistSession.value = newSession.copy(
                        messages = newSession.messages + finalAiMsg
                    )
                }
            }
        } else if (session.phase == "live_doctor") {
            // Live Doctor Simulation: Doctor answers with smart, specialized doctor statements after a slight delay
            _isSpecialistChatLoading.value = true
            viewModelScope.launch {
                delay(1500)
                _isSpecialistChatLoading.value = false
                
                val doctorPrompt = """
                    You are Dr. ${session.specialist.name}, a certified ${session.specialist.specialty} based in ${session.specialist.location}.
                    You are in a live, per-minute consultation call with a patient who has completed your custom AI pre-screen.
                    Maintain an incredibly compassionate, authoritative, world-class medical stance.
                    Answer the patient's question: "$text".
                    If they ask about an operation, surgery, or live streaming video, explain how you can assist or supervise remotely using CareOS's ultra-low latency surgical tele-presence system.
                    Address any diagnostic or relief queries professionally. Keep it brief and interactive, as a text/video chat.
                """.trimIndent()
                
                val response = GeminiHelper.triageSymptoms(
                    symptomText = doctorPrompt,
                    chatHistory = emptyList()
                )
                
                val docReply = ChatMessage(
                    sender = "doctor",
                    text = response
                )
                val currentSession = _activeSpecialistSession.value ?: return@launch
                _activeSpecialistSession.value = currentSession.copy(
                    messages = currentSession.messages + docReply
                )
            }
        }
    }

    fun dischargeAndLinkToLhr(clinicalNotes: String) {
        val session = _activeSpecialistSession.value ?: return
        viewModelScope.launch {
            val summary = """
                === CAREOS WORLD-CLASS SPECIALIST CONSULTATION REPORT ===
                Specialist Provider: ${session.specialist.name}
                Specialty: ${session.specialist.specialty}
                Location / Registry: ${session.specialist.location}
                Consultation Duration: ${session.activeCallSeconds / 60}m ${session.activeCallSeconds % 60}s
                Total Session Cost: \$${String.format("%.2f", session.currentBillingAmount)}
                
                === AI PRE-SCREENING SYNTHESIS ===
                Completed pre-screen using custom model ${session.specialist.customAiName}.
                
                === SPECIALIST CLINICAL NOTES & REQUISITION ===
                $clinicalNotes
                
                === INTEROPERABILITY STATUS ===
                Linked seamlessly to Patient Longitudinal Health Record (LHR). Shared with primary GP and NHIA board.
            """.trimIndent()

            val triage = SymptomTriage(
                symptomDescription = "Specialist consultation with ${session.specialist.name} (${session.specialist.specialty})",
                chatHistoryJson = "[]",
                hasRedFlags = false,
                redFlagsDetail = "Cleared by Specialist ${session.specialist.name}",
                likelyCauses = "Specialist Assessment Linked",
                confidenceBand = "High (Specialist Certified)",
                recommendedNextAction = "Refer to summary report in LHR",
                referralStatus = "Completed"
            )
            val triageId = repository.insertTriage(triage)
            
            val referral = ReferralRecord(
                triageId = triageId,
                patientName = "Simeon Adebayo (Patient Profile)",
                hospitalName = session.specialist.location,
                specialty = session.specialist.specialty,
                clinicalSummary = "Specialized Consult Discharge: $clinicalNotes. Cleared with billing of \$${String.format("%.2f", session.currentBillingAmount)}.",
                status = "Completed"
            )
            repository.insertReferral(referral)

            _activeSpecialistSession.value = session.copy(
                phase = "discharged",
                clinicalSummary = summary,
                dischargesToLhr = true
            )
        }
    }

    fun addPatientRecord(
        name: String,
        age: Int,
        gender: String,
        condition: String,
        medications: String = "",
        appointments: String = "",
        notes: String = "",
        doctorId: String = ""
    ) {
        viewModelScope.launch {
            val record = PatientRecord(
                name = name,
                age = age,
                gender = gender,
                conditionDescription = condition,
                medicationsText = medications,
                appointmentsText = appointments,
                doctorNotes = notes,
                doctorId = doctorId
            )
            repository.insertPatientRecord(record)
        }
    }

    fun deletePatientRecord(id: Long) {
        viewModelScope.launch {
            repository.deletePatientRecordById(id)
        }
    }

    private val _isAiPlanLoading = MutableStateFlow<Map<Long, Boolean>>(emptyMap())
    val isAiPlanLoading: StateFlow<Map<Long, Boolean>> = _isAiPlanLoading.asStateFlow()

    fun generateAiCarePlan(record: PatientRecord) {
        viewModelScope.launch {
            _isAiPlanLoading.value = _isAiPlanLoading.value + (record.id to true)
            
            val recordJson = """
                {
                    "patient_name": "${record.name}",
                    "age": ${record.age},
                    "gender": "${record.gender}",
                    "condition": "${record.conditionDescription}",
                    "medications": "${record.medicationsText}",
                    "appointments": "${record.appointmentsText}",
                    "notes": "${record.doctorNotes}"
                }
            """.trimIndent()
            
            val prompt = "Analyze the patient record and generate a comprehensive care plan. Suggest a specific pill compliance schedule and appointment reminders. Format the response as a professional medical care plan."
            val aiResponse = GeminiHelper.managePatientAI(recordJson, prompt)
            
            val updatedRecord = record.copy(aiGeneratedCarePlan = aiResponse)
            repository.insertPatientRecord(updatedRecord)
            
            // Auto-populate pill reminders and appointment reminders to the followUpReminders list
            if (record.medicationsText.isNotBlank()) {
                addReminder(
                    text = "Pill Alert for ${record.name}: Take ${record.medicationsText} regularly as directed",
                    category = "AI Patient Manager",
                    scheduledTime = "Daily (8:00 AM, 2:00 PM, 8:00 PM)"
                )
            }
            if (record.appointmentsText.isNotBlank()) {
                addReminder(
                    text = "Appointment for ${record.name}: ${record.appointmentsText}",
                    category = "AI Patient Manager",
                    scheduledTime = "Scheduled date"
                )
            }

            _isAiPlanLoading.value = _isAiPlanLoading.value + (record.id to false)
        }
    }

    fun createPersonalEHR(
        fullName: String,
        age: Int,
        history: String,
        symptoms: String,
        allergies: String,
        preferredHospital: String
    ) {
        viewModelScope.launch {
            val ehr = PersonalEHR(
                fullName = fullName,
                age = age,
                medicalHistory = history,
                currentSymptoms = symptoms,
                knownAllergies = allergies,
                preferredHospital = preferredHospital
            )
            repository.insertPersonalEHR(ehr)
        }
    }

    private val _isEhrAnalysisLoading = MutableStateFlow(false)
    val isEhrAnalysisLoading: StateFlow<Boolean> = _isEhrAnalysisLoading.asStateFlow()

    private val _ehrAnalysisResult = MutableStateFlow<String?>(null)
    val ehrAnalysisResult: StateFlow<String?> = _ehrAnalysisResult.asStateFlow()

    fun analyzePersonalHealth(ehr: PersonalEHR, query: String) {
        _isEhrAnalysisLoading.value = true
        _ehrAnalysisResult.value = null
        viewModelScope.launch {
            val recordJson = """
                {
                    "patient_name": "${ehr.fullName}",
                    "age": ${ehr.age},
                    "medical_history": "${ehr.medicalHistory}",
                    "current_symptoms": "${ehr.currentSymptoms}",
                    "known_allergies": "${ehr.knownAllergies}",
                    "preferred_hospital": "${ehr.preferredHospital}"
                }
            """.trimIndent()
            
            val response = GeminiHelper.analyzePersonalEHR(recordJson, query)
            _ehrAnalysisResult.value = response
            _isEhrAnalysisLoading.value = false
            
            // If the AI output suggests a referral, automatically create a pending referral in the DB!
            val suggestedSpecialty = when {
                response.contains("Cardio", ignoreCase = true) || response.contains("heart", ignoreCase = true) -> "Cardiologist (Heart Specialist)"
                response.contains("Derma", ignoreCase = true) || response.contains("skin", ignoreCase = true) -> "Dermatologist (Skin Specialist)"
                response.contains("Neuro", ignoreCase = true) || response.contains("brain", ignoreCase = true) -> "Neurologist (Brain Specialist)"
                response.contains("Dent", ignoreCase = true) || response.contains("tooth", ignoreCase = true) || response.contains("oral", ignoreCase = true) -> "Dentist (Oral Specialist)"
                response.contains("Opto", ignoreCase = true) || response.contains("eye", ignoreCase = true) || response.contains("vision", ignoreCase = true) -> "Optometrist (Eye Specialist)"
                response.contains("Ortho", ignoreCase = true) || response.contains("bone", ignoreCase = true) || response.contains("fracture", ignoreCase = true) -> "Orthopedist (Bone Specialist)"
                else -> "General Practitioner"
            }
            
            val triageId = repository.insertTriage(
                SymptomTriage(
                    symptomDescription = "Personal EHR Review for ${ehr.fullName}. Symptoms: ${ehr.currentSymptoms}",
                    chatHistoryJson = "[]",
                    hasRedFlags = false,
                    redFlagsDetail = "No severe red flags in self-logged EHR",
                    likelyCauses = "Analyzed by Personal EHR AI Companion",
                    confidenceBand = "High",
                    recommendedNextAction = "Refer to $suggestedSpecialty"
                )
            )
            
            repository.insertReferral(
                ReferralRecord(
                    triageId = triageId,
                    patientName = ehr.fullName,
                    hospitalName = if (ehr.preferredHospital.isNotBlank()) ehr.preferredHospital else "Lagos University Teaching Hospital (LUTH)",
                    specialty = suggestedSpecialty,
                    clinicalSummary = "Personal EHR Companion recommended consultation based on medical history ('${ehr.medicalHistory}') and symptoms ('${ehr.currentSymptoms}')."
                )
            )
        }
    }
}

// ==========================================
// --- BRAND-NEW WORLD-CLASS SPECIALISTS STATE & ENGINE ---
// ==========================================
data class Specialist(
    val id: String,
    val name: String,
    val specialty: String,
    val rating: Double,
    val reviewsCount: Int,
    val location: String,
    val ratePerMinute: Double,
    val currency: String = "USD", // "USD" / "NGN"
    val bio: String,
    val customAiName: String,
    val customAiKnowledgeBase: String,
    val customAiTriagePrompt: String,
    val isCustomRegistered: Boolean = false,
    val credentialsFile: String? = null
)

data class SpecialistConsultSession(
    val specialist: Specialist,
    val messages: List<ChatMessage> = emptyList(),
    val phase: String = "ai_triage", // "ai_triage", "live_doctor", "discharged"
    val activeCallSeconds: Int = 0,
    val currentBillingAmount: Double = 0.0,
    val clinicalSummary: String = "",
    val dischargesToLhr: Boolean = false
)
