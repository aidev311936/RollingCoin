package com.example.coinrain

sealed class CoinRainError {
    data class PromoAmountInvalid(val message: String) : CoinRainError()
    data class ConfigInvalid(val message: String) : CoinRainError()
}
