package com.example.rollingcoin

// Official Euro coin specifications (source: European Central Bank)
enum class CoinType(
    val label: String,
    val diameterMm: Float,
    val weightG: Float,
    val drawableName: String
) {
    CENT_1 (label = "1 ct",  diameterMm = 16.25f, weightG = 2.30f, drawableName = "coin_1ct"),
    CENT_2 (label = "2 ct",  diameterMm = 18.75f, weightG = 3.06f, drawableName = "coin_2ct"),
    CENT_5 (label = "5 ct",  diameterMm = 21.25f, weightG = 3.92f, drawableName = "coin_5ct"),
    CENT_10(label = "10 ct", diameterMm = 19.75f, weightG = 4.10f, drawableName = "coin_10ct"),
    CENT_20(label = "20 ct", diameterMm = 22.25f, weightG = 5.74f, drawableName = "coin_20ct"),
    CENT_50(label = "50 ct", diameterMm = 24.25f, weightG = 7.80f, drawableName = "coin_50ct"),
    EURO_1 (label = "1 €",   diameterMm = 23.25f, weightG = 7.50f, drawableName = "coin_1eu"),
    EURO_2 (label = "2 €",   diameterMm = 25.75f, weightG = 8.50f, drawableName = "coin_2eu");

    // Diameter of the largest coin (2€) used as the reference for scaling
    companion object {
        val MAX_DIAMETER_MM = EURO_2.diameterMm
    }
}
