package com.elad.halacha.rest

import com.elad.halachatime.core.presets.PresetRegistry
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

class ProfilesRouteTest {

    private val mapper = jacksonObjectMapper().findAndRegisterModules()

    @Test
    fun `profiles list and single profile`() = testApplication {
        application { halachaModule(PresetRegistry.fromDefaultLocations()) }

        val resp = client.get("/profiles")
        assertEquals(HttpStatusCode.OK, resp.status)
        val list: List<Map<String, Any?>> = mapper.readValue(resp.bodyAsText())
        assertTrue(list.isNotEmpty(), "profiles list is empty")
        val keys = list.map { it["key"] }.toSet()
        assertTrue(keys.contains("GRA"))

        val one = client.get("/profiles/GRA")
        assertEquals(HttpStatusCode.OK, one.status)
        val gra: Map<String, Any?> = mapper.readValue(one.bodyAsText())
        @Suppress("UNCHECKED_CAST")
        val items = gra["items"] as? List<*>
        assertNotNull(items)
        assertTrue(items!!.isNotEmpty(), "GRA items empty")
    }
}
