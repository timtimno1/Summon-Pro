package com.justjdupuis.summonpro.api

object LocationFreshness {
    fun ageMillis(timestamp: Long, nowMillis: Long = System.currentTimeMillis()): Long {
        val timestampMillis = if (timestamp < 10_000_000_000L) timestamp * 1_000 else timestamp
        return nowMillis - timestampMillis
    }

    fun isFresh(timestamp: Long, maxAgeMillis: Long, nowMillis: Long = System.currentTimeMillis()): Boolean =
        ageMillis(timestamp, nowMillis) in 0..maxAgeMillis
}
