package com.example.codexmobile.api

import com.example.codexmobile.data.ReasoningLevel
import java.io.StringReader
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AIClientTest {
    @Test
    fun `builds Codex Responses payload instead of Chat Completions payload`() {
        val body = AIClient.buildRequestBody(
            listOf(
                ChatMessage("system", "system instructions"),
                ChatMessage("user", "hello"),
                ChatMessage("assistant", "hi"),
            ),
        )

        assertEquals("gpt-5.5", body["model"]?.jsonPrimitive?.content)
        assertFalse(body["store"]!!.jsonPrimitive.boolean)
        assertTrue(body["stream"]!!.jsonPrimitive.boolean)
        assertEquals("system instructions", body["instructions"]?.jsonPrimitive?.content)
        assertEquals(
            "medium",
            body["reasoning"]?.jsonObject?.get("effort")?.jsonPrimitive?.content,
        )
        assertFalse(body.containsKey("messages"))

        val input = body["input"]!!.jsonArray
        assertEquals("user", input[0].jsonObject["role"]?.jsonPrimitive?.content)
        assertEquals(
            "input_text",
            input[0].jsonObject["content"]!!.jsonArray[0].jsonObject["type"]?.jsonPrimitive?.content,
        )
        assertEquals(
            "output_text",
            input[1].jsonObject["content"]!!.jsonArray[0].jsonObject["type"]?.jsonPrimitive?.content,
        )
    }

    @Test
    fun `parses current models and their advertised reasoning efforts`() {
        val models = AIClient.parseModelCatalog(
            """
            {
              "models": [
                {
                  "slug": "gpt-new",
                  "display_name": "GPT New",
                  "description": "Current model",
                  "default_reasoning_level": "high",
                  "supported_reasoning_levels": [
                    {"effort": "low", "description": "Fast"},
                    {"effort": "high", "description": "Deep"}
                  ],
                  "visibility": "list",
                  "supported_in_api": true,
                  "priority": 0
                },
                {
                  "slug": "hidden-model",
                  "display_name": "Hidden",
                  "supported_reasoning_levels": [],
                  "visibility": "hide",
                  "supported_in_api": true,
                  "priority": 1
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(listOf("gpt-new"), models.map { it.id })
        assertEquals(
            listOf(ReasoningLevel.LOW, ReasoningLevel.HIGH),
            models.single().supportedReasoningLevels,
        )
        assertEquals(ReasoningLevel.HIGH, models.single().defaultReasoningLevel)
    }

    @Test
    fun `parses API model aliases and removes dated snapshots`() {
        val models = AIClient.parseApiModelCatalog(
            """
            {"data":[
              {"id":"gpt-5.4"},
              {"id":"gpt-5.4-mini"},
              {"id":"gpt-5.4-2026-06-01"},
              {"id":"text-embedding-3-large"}
            ]}
            """.trimIndent(),
        )

        assertEquals(listOf("gpt-5.4", "gpt-5.4-mini"), models.map { it.id })
    }

    @Test
    fun `parses subscription usage into remaining windows`() {
        val usage = AIClient.parseUsage(
            """
            {
              "plan_type":"plus",
              "rate_limit":{
                "primary_window":{"used_percent":25,"reset_at":1800000000},
                "secondary_window":{"used_percent":70,"reset_at":1800100000}
              },
              "credits":{"has_credits":true,"unlimited":false,"balance":"12.50"}
            }
            """.trimIndent(),
        )

        assertEquals("plus", usage.planType)
        assertEquals(listOf(75, 30), usage.windows.map { it.remainingPercent })
        assertEquals("12.50", usage.creditBalance)
    }

    @Test
    fun `collects text deltas from Codex event stream`() {
        val stream = """
            data: {"type":"response.output_text.delta","delta":"hello "}

            data: {"type":"response.output_text.delta","delta":"world"}

            data: {"type":"response.completed","response":{"status":"completed"}}

            data: [DONE]

        """.trimIndent()

        val result = AIClient.parseEventStream(StringReader(stream).buffered())

        assertEquals("hello world", result)
    }

    @Test
    fun `uses completed response text when stream has no deltas`() {
        val stream = """
            data: {"type":"response.completed","response":{"output":[{"type":"message","content":[{"type":"output_text","text":"fallback"}]}]}}

        """.trimIndent()

        val result = AIClient.parseEventStream(StringReader(stream).buffered())

        assertEquals("fallback", result)
    }
}
