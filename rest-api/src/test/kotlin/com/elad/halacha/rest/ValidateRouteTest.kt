package com.elad.halacha.rest
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ValidateRouteTest {
    @Test
    fun validPresetReturnsTrue() = testApplication {
        application { halachaModule() } // same module you use in other tests
        val json = """
          { "key":"UNIT_VALID","displayName":"Unit Valid",
            "items":[
              {"id":"ALOT_HASHACHAR",
               "resolver":{"type":"DEGREES","method":"ALOS","value":16.1}}
            ]
          }
        """.trimIndent()

        val res = client.post("/validate/preset") {
            contentType(ContentType.Application.Json)
            setBody(json)
        }
        val txt = res.bodyAsText()
        assertEquals(200, res.status.value)
        assertTrue(txt.contains("\"valid\":true"))
        assertTrue(txt.contains("\"detectedKey\":\"UNIT_VALID\""))
    }

    @Test
    fun invalidPresetReturnsErrors() = testApplication {
        application { halachaModule() }
        val bad = """{
          "key":"BROKEN",
          "items":[{"id":"X","resolver":{"type":"MINUTES","mode":"SEA_LEVEL_SUNSET"}}]
        }"""

        val res = client.post("/validate/preset") {
            contentType(ContentType.Application.Json)
            setBody(bad)
        }
        val txt = res.bodyAsText()
        assertEquals(200, res.status.value)
        assertTrue(txt.contains("\"valid\":false"))
        assertTrue(txt.contains("\"errors\""))
    }
}