package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "symptom_triages")
data class SymptomTriage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val symptomDescription: String,
    val chatHistoryJson: String, // Chat turns encoded in JSON
    val hasRedFlags: Boolean,
    val redFlagsDetail: String,
    val likelyCauses: String, // Comma separated or simple list
    val confidenceBand: String, // "High", "Medium", "Low"
    val recommendedNextAction: String,
    val visualImagePath: String? = null,
    val referralStatus: String = "None" // "None", "Created", "Sent"
)

@Entity(tableName = "referrals")
data class ReferralRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val triageId: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val patientName: String,
    val hospitalName: String,
    val specialty: String,
    val clinicalSummary: String,
    val status: String = "Pending" // "Pending", "Confirmed", "Completed"
)

@Entity(tableName = "insurance_profiles")
data class InsuranceProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planName: String,
    val hmoPartner: String,
    val memberId: String,
    val coverageDetails: String,
    val expiryDate: String,
    val isActive: Boolean = true,
    val nin: String = "NIN-NotProvided",
    val vulnerableGroupStatus: String = "None",
    val dependentsCount: Int = 0,
    val preauthStatus: String = "None"
)

@Entity(tableName = "donations")
data class DonationRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientName: String,
    val hospitalName: String,
    val invoiceAmount: Double,
    val amountFunded: Double,
    val verificationStatus: String, // "Pending Verification", "Verified", "Funded"
    val ledgerTransactionId: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val diagnosisSummary: String = "Awaiting Diagnosis Audit",
    val socialWorkerVerifiedBy: String = "Pending Social Worker Audit",
    val fraudScore: Int = 100,
    val caseProgressJson: String = "[]",
    val invoiceAttachmentName: String? = null
)

@Entity(tableName = "patient_records")
data class PatientRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val age: Int,
    val gender: String,
    val conditionDescription: String,
    val medicationsText: String = "",
    val appointmentsText: String = "",
    val aiGeneratedCarePlan: String = "",
    val doctorNotes: String = "",
    val doctorId: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "personal_ehrs")
data class PersonalEHR(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fullName: String,
    val age: Int,
    val medicalHistory: String = "",
    val currentSymptoms: String = "",
    val knownAllergies: String = "",
    val preferredHospital: String = "",
    val recommendedSpecialists: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "immutable_audit_trail")
data class AuditLogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val actionType: String, // e.g. "PATIENT_TRIAGE", "REFERRAL_CREATED", "PAYMENT_PAYSTACK", "PAYMENT_STRIPE"
    val actorId: String,
    val payloadSummary: String,
    val previousHash: String,
    val currentHash: String
)

@Dao
interface CareDao {
    // Triage
    @Query("SELECT * FROM symptom_triages ORDER BY timestamp DESC")
    fun getAllTriages(): Flow<List<SymptomTriage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTriage(triage: SymptomTriage): Long

    @Query("UPDATE symptom_triages SET referralStatus = :status WHERE id = :id")
    suspend fun updateTriageReferralStatus(id: Long, status: String)

    // Referrals
    @Query("SELECT * FROM referrals ORDER BY timestamp DESC")
    fun getAllReferrals(): Flow<List<ReferralRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReferral(referral: ReferralRecord): Long

    // Insurance
    @Query("SELECT * FROM insurance_profiles WHERE isActive = 1 LIMIT 1")
    fun getActiveInsurance(): Flow<InsuranceProfile?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveInsurance(profile: InsuranceProfile)

    @Query("DELETE FROM insurance_profiles")
    suspend fun clearInsurance()

    // Donations
    @Query("SELECT * FROM donations ORDER BY timestamp DESC")
    fun getAllDonations(): Flow<List<DonationRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDonation(donation: DonationRecord): Long

    // Patient Records
    @Query("SELECT * FROM patient_records ORDER BY timestamp DESC")
    fun getAllPatientRecords(): Flow<List<PatientRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPatientRecord(record: PatientRecord): Long

    @Query("DELETE FROM patient_records WHERE id = :id")
    suspend fun deletePatientRecordById(id: Long)

    // Personal EHRs
    @Query("SELECT * FROM personal_ehrs ORDER BY timestamp DESC")
    fun getAllPersonalEHRs(): Flow<List<PersonalEHR>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPersonalEHR(ehr: PersonalEHR): Long

    // Immutable Audit Trail
    @Query("SELECT * FROM immutable_audit_trail ORDER BY id DESC")
    fun getAuditTrail(): Flow<List<AuditLogEntry>>

    @Query("SELECT * FROM immutable_audit_trail ORDER BY id DESC LIMIT 1")
    suspend fun getLastAuditEntry(): AuditLogEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditEntry(entry: AuditLogEntry): Long
}

@Database(
    entities = [
        SymptomTriage::class,
        ReferralRecord::class,
        InsuranceProfile::class,
        DonationRecord::class,
        PatientRecord::class,
        PersonalEHR::class,
        AuditLogEntry::class
    ],
    version = 4,
    exportSchema = false
)
abstract class CareDatabase : RoomDatabase() {
    abstract fun careDao(): CareDao
}
