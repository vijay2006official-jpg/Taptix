package com.example.taptix.model

/**
 * Service categories supported across ride-hailing and delivery partner platforms
 * (Uber, Ola, Rapido, inDrive, Porter, Namma Yatri).
 */
enum class BookingServiceCategory(
    val id: String,
    val displayName: String,
    val detectionKeywords: List<String>
) {
    MOTO(
        id = "MOTO",
        displayName = "Bike / Moto",
        detectionKeywords = listOf(
            "RAPIDO BIKE", "UBER MOTO", "OLA BIKE", "TWO WHEELER", "2 WHEELER", "2W",
            "MOTO", "BIKE"
        )
    ),
    AUTO(
        id = "AUTO",
        displayName = "Auto Rickshaw",
        detectionKeywords = listOf(
            "UBER AUTO", "OLA AUTO", "RAPIDO AUTO", "AUTO RICKSHAW", "TUK TUK", "TUK-TUK",
            "AUTORICKSHAW", "AUTO"
        )
    ),
    CAB_ECONOMY(
        id = "CAB_ECONOMY",
        displayName = "Economy Cab",
        detectionKeywords = listOf(
            "UBER GO", "GO SEDAN", "OLA MINI", "MICRO", "ECONOMY", "COMPACT", "UBERX", "MINI"
        )
    ),
    CAB_PREMIUM(
        id = "CAB_PREMIUM",
        displayName = "Premium Cab",
        detectionKeywords = listOf(
            "UBER PREMIER", "OLA PRIME", "PRIME SEDAN", "PREMIER", "COMFORT", "SEDAN", "BLACK", "PRIME"
        )
    ),
    XL(
        id = "XL",
        displayName = "XL / SUV",
        detectionKeywords = listOf(
            "UBERXL", "PRIME SUV", "ERTIGA", "INNOVA", "6 SEATER", "6-SEATER", "SUV", "XL"
        )
    ),
    DELIVERY(
        id = "DELIVERY",
        displayName = "Parcel / Delivery",
        detectionKeywords = listOf(
            "UBER CONNECT", "RAPIDO PARCEL", "GOODS DELIVERY", "TATA ACE", "PICKUP 8FT",
            "CHOTA HATHI", "PARCEL", "PACKAGE", "CONNECT", "DELIVERY", "COURIER", "BOX", "PORTER"
        )
    ),
    RENTAL_OUTSTATION(
        id = "RENTAL_OUTSTATION",
        displayName = "Rental / Outstation",
        detectionKeywords = listOf(
            "OLA OUTSTATION", "UBER RENTAL", "OUTSTATION", "RENTAL", "INTERCITY", "HOURLY"
        )
    );

    companion object {
        val ALL_IDS: Set<String> = entries.map { it.id }.toSet()

        fun fromId(id: String): BookingServiceCategory? {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
        }

        /**
         * Identifies service category from screen text.
         * Prioritizes compound terms (e.g. "UBER AUTO", "RAPIDO BIKE") over individual keywords.
         */
        fun detectCategory(rawText: String): BookingServiceCategory? {
            if (rawText.isBlank()) return null
            val upper = rawText.uppercase()

            // 1. Compound keyword matching
            for (category in entries) {
                for (kw in category.detectionKeywords) {
                    if (kw.contains(" ") && upper.contains(kw)) {
                        return category
                    }
                }
            }

            // 2. Tokenized word matching to prevent substring false-positives
            val words = upper.split(Regex("[^A-Z0-9]+")).filter { it.isNotBlank() }.toSet()
            for (category in entries) {
                for (kw in category.detectionKeywords) {
                    if (!kw.contains(" ") && words.contains(kw)) {
                        return category
                    }
                }
            }

            return null
        }
    }
}
