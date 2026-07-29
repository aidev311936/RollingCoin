package com.example.coinrain.core

/** Maps each Euro coin denomination to its spec ID and face value in cents. */
enum class Denomination(val specId: String, val cents: Int) {
    CENT_1("CENT_1", 1),
    CENT_2("CENT_2", 2),
    CENT_5("CENT_5", 5),
    CENT_10("CENT_10", 10),
    CENT_20("CENT_20", 20),
    CENT_50("CENT_50", 50),
    EURO_1("EURO_1", 100),
    EURO_2("EURO_2", 200);

    companion object {
        fun fromSpecId(specId: String): Denomination? = values().find { it.specId == specId }
    }
}
