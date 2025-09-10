package com.elad.halacha.rest

import com.elad.halachatime.core.presets.PresetRegistry
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ZmanimV2RouteTest {

    @Test
    fun `v2 returns preset-shaped items and kosherJavaMethod when explain=true`() = testApplication {
        application { halachaModule(PresetRegistry.fromDefaultLocations()) }

        val resp = client.get("/zmanim") {
            url {
                parameters.append("date", "2025-09-09")
                parameters.append("preset", "GRA")
                parameters.append("scheme", "v2")
                parameters.append("explain", "true")
            }
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val body = resp.bodyAsText()
        assertTrue(body.contains("\"schemeVersion\":\"v2\""))
        assertTrue(body.contains("\"profileKey\":\"GRA\""))
        assertTrue(body.contains("\"items\""))
    }
}