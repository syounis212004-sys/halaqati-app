package com.mohammedsamir.halaqati.plans

/** Version 2 scoring; legacy assessments retain their stored scoring contract. */
object TilawahScoreV2 {
    const val VERSION = 2

    fun score(makharij: Int, tajweed: Int, waqf: Int, adherence: Int?, wardReviewed: Boolean, majorErrors: Int): Double {
        val ratings = listOfNotNull(makharij, tajweed, waqf, adherence)
        require(ratings.all { it in 0..5 })
        require(majorErrors >= 0)
        val quality = 90.0 * ratings.average() / 5.0
        val ward = if (wardReviewed) 10.0 else 0.0
        return (quality + ward - 8.0 * majorErrors).coerceIn(0.0, 100.0)
    }
}
