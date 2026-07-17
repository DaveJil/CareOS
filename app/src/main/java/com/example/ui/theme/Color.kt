package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// --- Master Design System: Light Theme ("Calm Medical Luxury") ---
val ClinicPrimary = Color(0xFF0F766E)       // Deep healing teal
val ClinicPrimaryDark = Color(0xFF115E59)   // Healing teal dark
val ClinicSecondarySoft = Color(0xFFD9F3EE) // Soft clinic secondary
val ClinicAccent = Color(0xFF14B8A6)        // Vibrancy & interactive accent
val ClinicBackground = Color(0xFFF8FBFA)    // Soft ivory background
val ClinicSurface = Color(0xFFFFFFFF)       // Crisp white surface cards
val ClinicAltSurface = Color(0xFFF1F5F4)    // Elevated grey-teal surface
val ClinicTextPrimary = Color(0xFF0F172A)   // Deep slate
val ClinicTextSecondary = Color(0xFF475569) // Muted slate
val ClinicBorder = Color(0xFFDCE7E5)        // Soft teal border
val ClinicSuccess = Color(0xFF16A34A)       // Safe green
val ClinicWarning = Color(0xFFD97706)       // Safe warning orange/amber
val ClinicError = Color(0xFFDC2626)         // Clinical red
val ClinicInfo = Color(0xFF2563EB)          // Clean blue

// --- Master Design System: Dark Theme ("Calm Charcoal-Green") ---
val ClinicDarkBackground = Color(0xFF0C1917)    // Cozy dark deep charcoal-green
val ClinicDarkSurface = Color(0xFF142925)       // Elevated warm deep green surface
val ClinicDarkAltSurface = Color(0xFF1B3833)    // Selectable alt surface
val ClinicDarkTextPrimary = Color(0xFFF8FAFC)   // Crisp near white
val ClinicDarkTextSecondary = Color(0xFF94A3B8) // Relaxing slate gray
val ClinicDarkBorder = Color(0xFF22423D)        // Subtle dark borders
val ClinicDarkAccent = Color(0xFF2DD4BF)        // Muted cyan-teal active accent

// --- Retaining backward compatible variables to avoid any unresolved compilation references ---
val TealPrimary = ClinicPrimary
val TealSecondary = ClinicPrimaryDark
val TealTertiary = ClinicSecondarySoft

val SlateDark = ClinicDarkBackground
val SlateBackground = ClinicBackground
val CardDark = ClinicDarkSurface
val TextLight = ClinicTextPrimary
val TextMuted = ClinicTextSecondary

val MintGreen = ClinicSuccess
val CoralRed = ClinicError
val AmberWarning = ClinicWarning

val BentoHeroBg = ClinicSecondarySoft
val BentoHeroText = ClinicPrimaryDark
val BentoVisualBg = Color(0xFFFDE2D1)
val BentoDoctorBg = Color(0xFFE0F2FE)
val BentoInsuranceBg = Color(0xFFF3E8FF)
val BentoAidBg = Color(0xFFFCE7F3)
val BentoActiveNavBg = ClinicSecondarySoft
val BentoBorder = ClinicBorder


