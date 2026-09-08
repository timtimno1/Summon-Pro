package com.justjdupuis.summonpro.api

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test

class TeslaApiModelTest {
    @Test
    fun parsesLocationDataResponse() {
        val json = """
            {
              "response": {
                "drive_state": {
                  "latitude": 25.033,
                  "longitude": 121.5654,
                  "heading": 90.0,
                  "timestamp": 1700000000000
                }
              }
            }
        """.trimIndent()

        val response = Gson().fromJson(json, TeslaApi.VehicleDataResponse::class.java)

        assertEquals(25.033, response.response.driveState?.latitude ?: 0.0, 0.0001)
        assertEquals(121.5654, response.response.driveState?.longitude ?: 0.0, 0.0001)
        assertEquals(90.0, response.response.driveState?.heading ?: 0.0, 0.0001)
        assertEquals(1700000000000, response.response.driveState?.timestamp)
    }
}
