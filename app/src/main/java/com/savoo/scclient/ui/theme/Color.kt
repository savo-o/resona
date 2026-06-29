package com.savoo.scclient.ui.theme

import androidx.compose.ui.graphics.Color

object OrangeSeed {
    val Primary = Color(0xFFFF5500)
    val Secondary = Color(0xFFFF8A50)
    val Tertiary = Color(0xFFFFB74D)
}

object PurpleSeed {
    val Primary = Color(0xFF9C27B0)
    val Secondary = Color(0xFFBA68C8)
    val Tertiary = Color(0xFFFF80AB)
}

object MintSeed {
    val Primary = Color(0xFF00BFA5)
    val Secondary = Color(0xFF64DFC6)
    val Tertiary = Color(0xFFFFD166)
}

object OceanSeed {
    val Primary = Color(0xFF2979FF)
    val Secondary = Color(0xFF82B1FF)
    val Tertiary = Color(0xFFFF6E40)
}

enum class AppColorTheme(val displayName: String, val seedPrimary: Color, val seedSecondary: Color, val seedTertiary: Color) {
    ORANGE("SoundCloud Orange", OrangeSeed.Primary, OrangeSeed.Secondary, OrangeSeed.Tertiary),
    PURPLE("Ultraviolet", PurpleSeed.Primary, PurpleSeed.Secondary, PurpleSeed.Tertiary),
    MINT("Mint", MintSeed.Primary, MintSeed.Secondary, MintSeed.Tertiary),
    OCEAN("Ocean", OceanSeed.Primary, OceanSeed.Secondary, OceanSeed.Tertiary),
    DYNAMIC("System (Material You)", OrangeSeed.Primary, OrangeSeed.Secondary, OrangeSeed.Tertiary),
    DYNAMIC_TRACK("From Track Cover", OrangeSeed.Primary, OrangeSeed.Secondary, OrangeSeed.Tertiary),
}
