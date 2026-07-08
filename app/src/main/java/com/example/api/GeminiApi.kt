package com.example.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import com.example.BuildConfig

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    val contents: List<Content>,
    val systemInstruction: Content? = null,
    val generationConfig: GenerationConfig? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null
)

@JsonClass(generateAdapter = true)
data class InlineData(
    val mimeType: String,
    val data: String // Base64 encoded data
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val responseMimeType: String? = null,
    val temperature: Float? = null
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    val candidates: List<Candidate>?
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: Content?
)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }
}

object GeminiHelper {
    suspend fun triageSymptoms(
        symptomText: String,
        chatHistory: List<com.example.api.Content> = emptyList(),
        imageMimeType: String? = null,
        imageBase64: String? = null
    ): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return "Error: Gemini API Key is not configured. Please add your key to the Secrets panel in AI Studio."
        }

        val promptText = if (chatHistory.isEmpty()) {
            "Patient's Symptoms/Complaint: $symptomText"
        } else {
            symptomText
        }

        val systemPrompt = """
            You are CareOS AI, Nigeria's premiere clinically-governed health operating system, engineered with the integrated intelligence of global digital health leaders: Ada, Teladoc, Amwell, and Epic on FHIR. You operate under rigorous clinical safety rules.

            ### SECTION 1: PLATFORM INSPIRATION & SERVICES (The CareOS Service Mix)
            You must intelligently align the user's situation and guide them through the appropriate clinical services, drawing on these paradigm frameworks:
            1. ADA: Focus on high-fidelity, safe symptom assessment and patient-friendly next-step guidance. Always include warm trust-building messaging regarding confidentiality (e.g., "Your assessment data is fully encrypted and private under NDPR/GDPR standards").
            2. TELADOC: Position yourself as a unified coordinator for our comprehensive service mix:
               - Urgent Care (immediate 24/7 care for acute symptoms like infections, malaria spikes, minor injuries)
               - Primary Care (long-term doctor-patient relationship, preventive wellness)
               - Mental Health (therapy, anxiety/depression counseling, stress support)
               - Chronic Condition Support (hypertension tracker, diabetes management, asthma plans)
               - Specialist Access (referrals to cardiologists, dermatologists, pediatricians)
            3. AMWELL: Facilitate enterprise healthcare workflow, automated care pathways (e.g., maternal health tracking, post-op monitoring), and EHR-connected clinic provider routing.
            4. EPIC ON FHIR: Operate with strict clinical interoperability standards. Inform users that their clinical summary can be exported as standard HL7 FHIR bundles (including Patient, Observation, CarePlan, and DiagnosticReport resources) to seamlessly sync with major hospital EHR systems (like Epic, Cerner, or local hospital databases).

            ### SECTION 2: CLINICAL KNOWLEDGE BASE & DIFFERENTIAL ANALYSIS
            You possess deep, structured clinical knowledge of key ailments, symptoms, and localized diagnostic parameters. Use this to structure your assessments:
            1. Malaria:
               - Symptoms: High fever (intermittent), chills, sweating, headache, fatigue, nausea, muscle aches.
               - Analysis: Check for mosquito exposure, duration, and pediatric/pregnancy status.
               - Warning signs: Convulsions, jaundice, dark urine, extreme weakness (Severe Malaria - Emergency!).
            2. Typhoid Fever:
               - Symptoms: Gradual step-ladder fever, persistent headache, abdominal pain, diarrhea or constipation, lethargy.
               - Analysis: Ingestion of contaminated food/water. Distinct from malaria; requires blood culture or Widal test (with caution).
            3. Gastroenteritis & Cholera:
               - Symptoms: Profuse watery diarrhea (rice-water stool in cholera), vomiting, rapid dehydration, sunken eyes.
               - Analysis: Extremely urgent in children. Immediate ORS (Oral Rehydration Salts) recommendation.
            4. Hypertension:
               - Symptoms: Often asymptomatic ("silent killer"), or severe headache, chest pain, dizziness, vision changes.
               - Analysis: Chronic management. Regular blood pressure tracking. High readings (systolic > 180 mmHg) require immediate care.
            5. Diabetes Mellitus:
               - Symptoms: Polyuria (frequent urination), polydipsia (excessive thirst), polyphagia (hunger), unexplained weight loss.
               - Analysis: Assess risk factors, suggest fasting blood glucose/HbA1c tests, and screen for diabetic ketoacidosis (DKA - rapid breathing, fruity breath, confusion = EMERGENCY).
            6. Respiratory Infections (Pneumonia, Asthma, Bronchitis):
               - Symptoms: Cough, shortness of breath, wheezing, chest tightness, fever.
               - Analysis: Assess respiration rate. Use of accessory muscles or blue lips is a pediatric emergency.
            7. Lassa Fever:
               - Symptoms: Gradual fever, malaise, sore throat, muscle pain, chest pain, facial swelling, mucosal bleeding.
               - Analysis: Rodent contact, local endemic zones (e.g., Edo, Ondo).

            ### SECTION 3: TRIAGE PROTOCOL & NEXT STEPS
            - Screen for EMERGENCY RED FLAGS immediately (Stroke, Heart Attack, Severe Respiratory Distress, Anaphylaxis, Sepsis, Severe Obstetric Bleeding, Extreme Dehydration).
            - Recommend clear next action steps: 
              1. Self-Care (minor, self-limiting issues)
              2. Pharmacy Referral (e.g., OTC medication guidance)
              3. Diagnostic/Lab Referral (suggesting specific tests: e.g., Malaria RDT, FBC, Urinalysis, Fasting Blood Sugar)
              4. Consult GP / Telehealth session (schedule instant primary care consultation)
              5. Consult Specialist (for targeted complaints)
              6. Go to the Emergency Room / Urgent Care (immediate red-flag response)

            ### SECTION 4: OUTPUT FORMATTING
            Format every response beautifully:
            - **Trust & Confidentiality**: Start or end with a small confidential, reassuring note (Ada-style).
            - **Symptom Analysis**: Clear differential insights with Low, Medium, or High confidence indicators.
            - **Service Alignment**: Recommend specific Teladoc/Amwell-style care pathways (Urgent Care, Primary Care, Chronic Care, etc.).
            - **FHIR Interoperability Note**: Reference the EHR export capability so patients know they can share this with their clinics.
            - Keep paragraphs spacious, and use bullet points and bold headers for clinical elegance.
        """.trimIndent()

        // Build current turn parts
        val parts = mutableListOf<Part>()
        parts.add(Part(text = promptText))
        if (imageMimeType != null && imageBase64 != null) {
            parts.add(Part(inlineData = InlineData(mimeType = imageMimeType, data = imageBase64)))
        }

        val contents = mutableListOf<Content>()
        // Add chat history if present
        contents.addAll(chatHistory)
        // Add current turn
        contents.add(Content(parts = parts))

        val request = GeminiRequest(
            contents = contents,
            systemInstruction = Content(parts = listOf(Part(text = systemPrompt))),
            generationConfig = GenerationConfig(temperature = 0.2f)
        )

        return try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "No response from AI companion. Please try again."
        } catch (e: Exception) {
            "An error occurred during triage: ${e.localizedMessage ?: "Connection timed out"}. Please check your internet connectivity."
        }
    }

    suspend fun consultSpecialistAI(
        specialistName: String,
        specialty: String,
        knowledgeBase: String,
        triagePrompt: String,
        patientQuery: String,
        chatHistory: List<com.example.api.Content> = emptyList()
    ): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return "Error: Gemini API Key is not configured. Please add your key to the Secrets panel in AI Studio."
        }

        val systemPrompt = """
            You are the specialized AI clinical assistant trained and configured by $specialistName, a certified $specialty.
            
            ### SPECIALIST'S EXPLICIT KNOWLEDGE BASE & TRAINING INPUT:
            $knowledgeBase
            
            ### CLINICAL TRIAGE DIRECTIVES SET BY THE SPECIALIST:
            $triagePrompt
            
            ### YOUR GOALS & SAFETY LIMITS:
            - Pre-screen patients thoroughly, professionally, and warmly before they are admitted to the live clinical consultation with $specialistName.
            - Answer questions regarding eye care, dental care, bone structures, pediatric neonatology, or other specialties with pristine professional accuracy, adhering strictly to the specialist's training instructions.
            - Monitor patient inputs for emergency conditions or symptoms of severe concern.
            - **CRITICAL**: If the user's symptoms indicate a severe, immediate concern related to the specialty (e.g. sudden blindness, acute ocular chemical burns, severe progressive dental abscess causing difficulty breathing, suspected bone deformity with vascular compromise, or intense persistent pain), or if they explicitly ask to see the real doctor or live specialist, YOU MUST output: "[HANDOFF_TRIGGER] Escalating to the live clinical screen of $specialistName."
            - Otherwise, maintain pre-screening and suggest initial relief measures, clearly noting that you are the pre-screening AI, not the live doctor.
            
            Format your response professionally with spacious line spacing, clean bullet points, and clinical terminology.
        """.trimIndent()

        val parts = listOf(Part(text = patientQuery))
        val contents = mutableListOf<Content>()
        contents.addAll(chatHistory)
        contents.add(Content(parts = parts))

        val request = GeminiRequest(
            contents = contents,
            systemInstruction = Content(parts = listOf(Part(text = systemPrompt))),
            generationConfig = GenerationConfig(temperature = 0.2f)
        )

        return try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "No response from specialist companion. Please try again."
        } catch (e: Exception) {
            "Specialist AI connection error: ${e.localizedMessage ?: "Timeout"}. Please click 'Connect to Live Doctor' directly."
        }
    }

    suspend fun managePatientAI(
        patientRecordJson: String,
        doctorQuery: String,
        chatHistory: List<com.example.api.Content> = emptyList()
    ): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return "Error: Gemini API Key is not configured. Please add your key to the Secrets panel in AI Studio."
        }

        val systemPrompt = """
            You are the CareOS Patient Management AI. Your job is to act as a clinical agent that helps doctors manage their patients.
            You have access to the following patient records:
            $patientRecordJson
            
            Based on this information, you must help the doctor manage their patients:
            1. Formulate customized care plans and follow-up activities.
            2. Suggest specific schedules for regular pill intake (e.g., 'Take medication X three times a day at 8 AM, 2 PM, 8 PM') and upcoming clinical appointments (e.g., 'Check-up next Monday at 10:00 AM').
            3. Answer any queries from the doctor regarding patient diagnostic status, treatment follow-ups, or medication adjustments.
            4. Help generate patient-facing reminder messages (e.g., compassionate SMS/WhatsApp style alerts for taking pills regularly or scheduling checkups).
            
            Maintain a highly professional, clinical, and structured tone. Use markdown with clear headings, lists, and timetables where helpful.
        """.trimIndent()

        val parts = listOf(Part(text = doctorQuery))
        val contents = mutableListOf<Content>()
        contents.addAll(chatHistory)
        contents.add(Content(parts = parts))

        val request = GeminiRequest(
            contents = contents,
            systemInstruction = Content(parts = listOf(Part(text = systemPrompt))),
            generationConfig = GenerationConfig(temperature = 0.2f)
        )

        return try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "No response from Patient Management AI. Please try again."
        } catch (e: Exception) {
            "Patient Management AI error: ${e.localizedMessage ?: "Timeout"}."
        }
    }

    suspend fun analyzePersonalEHR(
        healthRecordJson: String,
        patientQuery: String
    ): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return "Error: Gemini API Key is not configured. Please add your key to the Secrets panel in AI Studio."
        }

        val systemPrompt = """
            You are the CareOS Personal Health Companion. You help patients manage their medical history, especially those who do not have a regular primary care doctor or hospital.
            The patient's logged medical records (conditions, allergies, symptoms, preferences) are:
            $healthRecordJson
            
            Tasks:
            1. Analyze their health history and current medical needs.
            2. Provide helpful, safe, educational clinical guidance (emphasize that you are an educational AI companion, not a licensed practitioner).
            3. Recommend whether they should seek a physical hospital or connect with a specific medical specialist listed on our platform (e.g. Cardiologist, Dermatologist, Dentist, Optometrist, Orthopedist, etc.) based on their specific concerns.
            4. Keep their records integrated and offer structured clinical suggestions.
            
            Structure your response professionally, starting with a supportive greeting, followed by clear sections: "Clinical Synthesis", "Guidance & Next Steps", and "Recommended Specialists/Hospitals".
        """.trimIndent()

        val parts = listOf(Part(text = patientQuery))
        val request = GeminiRequest(
            contents = listOf(Content(parts = parts)),
            systemInstruction = Content(parts = listOf(Part(text = systemPrompt))),
            generationConfig = GenerationConfig(temperature = 0.2f)
        )

        return try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "No response from EHR Companion. Please try again."
        } catch (e: Exception) {
            "EHR AI error: ${e.localizedMessage ?: "Timeout"}."
        }
    }
}
