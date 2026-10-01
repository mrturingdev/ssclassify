package com.agy.imagecategorizer.aicore.model

/**
 * Indicates which engine performed the semantic extraction.
 */
enum class InferenceSource {
    /** On-device Gemini Nano running through Google Play Services Android AICore. */
    AICORE_GEMINI_NANO,

    /** High-speed local heuristics and regex parsing (used when AICore is downloading, unsupported, or busy). */
    RULE_BASED_FALLBACK,

    /** Hybrid engine combination (e.g. AICore generated summary + rule-based entity validation). */
    HYBRID,

    /** Testing / mock implementation. */
    MOCK,
}
