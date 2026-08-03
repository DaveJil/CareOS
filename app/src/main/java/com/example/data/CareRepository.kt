package com.example.data

import kotlinx.coroutines.flow.Flow
import java.security.MessageDigest

class CareRepository(private val careDao: CareDao) {
    val allTriages: Flow<List<SymptomTriage>> = careDao.getAllTriages()
    val allReferrals: Flow<List<ReferralRecord>> = careDao.getAllReferrals()
    val activeInsurance: Flow<InsuranceProfile?> = careDao.getActiveInsurance()
    val allDonations: Flow<List<DonationRecord>> = careDao.getAllDonations()
    val allPatientRecords: Flow<List<PatientRecord>> = careDao.getAllPatientRecords()
    val allPersonalEHRs: Flow<List<PersonalEHR>> = careDao.getAllPersonalEHRs()
    val auditTrail: Flow<List<AuditLogEntry>> = careDao.getAuditTrail()

    suspend fun logAudit(actionType: String, actorId: String, payloadSummary: String) {
        val lastEntry = careDao.getLastAuditEntry()
        val prevHash = lastEntry?.currentHash ?: "0000000000000000000000000000000000000000000000000000000000000000"
        val timestamp = System.currentTimeMillis()
        val rawToHash = "$prevHash|$timestamp|$actionType|$actorId|$payloadSummary"
        
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(rawToHash.toByteArray(Charsets.UTF_8))
        val currentHash = digest.joinToString("") { "%02x".format(it) }

        val entry = AuditLogEntry(
            timestamp = timestamp,
            actionType = actionType,
            actorId = actorId,
            payloadSummary = payloadSummary,
            previousHash = prevHash,
            currentHash = currentHash
        )
        careDao.insertAuditEntry(entry)
    }

    suspend fun insertTriage(triage: SymptomTriage): Long {
        val id = careDao.insertTriage(triage)
        logAudit("PATIENT_TRIAGE", "TRIAGE_ENGINE", "Triage #$id created: ${triage.symptomDescription.take(30)}")
        return id
    }

    suspend fun updateTriageReferralStatus(id: Long, status: String) {
        careDao.updateTriageReferralStatus(id, status)
        logAudit("TRIAGE_REFERRAL_UPDATE", "CLINICIAN", "Triage #$id status set to $status")
    }

    suspend fun insertReferral(referral: ReferralRecord): Long {
        val id = careDao.insertReferral(referral)
        logAudit("REFERRAL_CREATED", "CLINICIAN", "Referral #$id created for ${referral.patientName} -> ${referral.hospitalName}")
        return id
    }

    suspend fun saveInsurance(profile: InsuranceProfile) {
        careDao.saveInsurance(profile)
        logAudit("INSURANCE_UPDATED", profile.memberId, "HMO ${profile.hmoPartner} plan ${profile.planName} saved")
    }

    suspend fun clearInsurance() {
        careDao.clearInsurance()
        logAudit("INSURANCE_CLEARED", "PATIENT", "Insurance profile cleared")
    }

    suspend fun insertDonation(donation: DonationRecord): Long {
        val id = careDao.insertDonation(donation)
        logAudit("DONATION_FUNDING", "PAYMENT_GATEWAY", "Donation #$id amount Funded \$${donation.amountFunded} for ${donation.patientName}")
        return id
    }

    suspend fun insertPatientRecord(record: PatientRecord): Long {
        val id = careDao.insertPatientRecord(record)
        logAudit("PATIENT_RECORD_SAVED", record.doctorId.ifBlank { "DOCTOR_SYSTEM" }, "Patient record #$id saved for ${record.name}")
        return id
    }

    suspend fun deletePatientRecordById(id: Long) {
        careDao.deletePatientRecordById(id)
        logAudit("PATIENT_RECORD_DELETED", "CLINICIAN", "Patient record #$id deleted")
    }

    suspend fun insertPersonalEHR(ehr: PersonalEHR): Long {
        val id = careDao.insertPersonalEHR(ehr)
        logAudit("EHR_UPDATED", "PATIENT", "EHR #$id saved for ${ehr.fullName}")
        return id
    }
}
