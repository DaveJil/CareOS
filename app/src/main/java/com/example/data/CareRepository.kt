package com.example.data

import kotlinx.coroutines.flow.Flow

class CareRepository(private val careDao: CareDao) {
    val allTriages: Flow<List<SymptomTriage>> = careDao.getAllTriages()
    val allReferrals: Flow<List<ReferralRecord>> = careDao.getAllReferrals()
    val activeInsurance: Flow<InsuranceProfile?> = careDao.getActiveInsurance()
    val allDonations: Flow<List<DonationRecord>> = careDao.getAllDonations()
    val allPatientRecords: Flow<List<PatientRecord>> = careDao.getAllPatientRecords()
    val allPersonalEHRs: Flow<List<PersonalEHR>> = careDao.getAllPersonalEHRs()

    suspend fun insertTriage(triage: SymptomTriage): Long {
        return careDao.insertTriage(triage)
    }

    suspend fun updateTriageReferralStatus(id: Long, status: String) {
        careDao.updateTriageReferralStatus(id, status)
    }

    suspend fun insertReferral(referral: ReferralRecord): Long {
        return careDao.insertReferral(referral)
    }

    suspend fun saveInsurance(profile: InsuranceProfile) {
        careDao.saveInsurance(profile)
    }

    suspend fun clearInsurance() {
        careDao.clearInsurance()
    }

    suspend fun insertDonation(donation: DonationRecord): Long {
        return careDao.insertDonation(donation)
    }

    suspend fun insertPatientRecord(record: PatientRecord): Long {
        return careDao.insertPatientRecord(record)
    }

    suspend fun deletePatientRecordById(id: Long) {
        careDao.deletePatientRecordById(id)
    }

    suspend fun insertPersonalEHR(ehr: PersonalEHR): Long {
        return careDao.insertPersonalEHR(ehr)
    }
}
