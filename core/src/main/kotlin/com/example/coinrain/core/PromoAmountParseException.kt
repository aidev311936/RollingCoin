package com.example.coinrain.core

/** Thrown when a monetary amount string cannot be parsed into a valid cent value. */
class PromoAmountParseException(message: String) : Exception(message)
