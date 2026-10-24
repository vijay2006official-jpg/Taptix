package com.example.taptix.model

/**
 * Result returned by the offer matching engine after evaluating an offer against configured rules.
 */
data class RuleEvaluationResult(
    val isAccepted: Boolean,
    val matchedRules: List<String> = emptyList(),
    val rejectedReason: String? = null,
    val evaluatedFare: Double? = null,
    val evaluatedDistanceKm: Double? = null,
    val recognizedDestination: String? = null,
    val recognizedPickup: String? = null
)
