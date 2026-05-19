package edu.bluejack252.hwixel.data.source.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class GptApiSourceTest {

    private val source = GptApiSource(apiKey = "test-key")

    @Test
    fun parseResponseExtractsTeamHealthJson() {
        val body = """
            {
              "output": [
                {
                  "content": [
                    {
                      "text": "{\"status\":\"Mild Imbalance\",\"summary\":\"Work is uneven.\",\"recommendations\":[\"Pair Bob with Alice\",\"Split overdue tasks\"]}"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val result = source.parseResponse(body)

        assertEquals("Mild Imbalance", result.status)
        assertEquals("Work is uneven.", result.summary)
        assertEquals(listOf("Pair Bob with Alice", "Split overdue tasks"), result.recommendations)
    }

    @Test
    fun parseResponseExtractsOutputTextShape() {
        val body = """
            {
              "output_text": "{\"status\":\"Healthy\",\"summary\":\"The team is balanced.\",\"recommendations\":[\"Keep current split\"]}"
            }
        """.trimIndent()

        val result = source.parseResponse(body)

        assertEquals("Healthy", result.status)
        assertEquals("The team is balanced.", result.summary)
        assertEquals(listOf("Keep current split"), result.recommendations)
    }

    @Test
    fun parseResponseExtractsJsonFromMarkdownWrappedText() {
        val body = """
            {
              "output": [
                {
                  "content": [
                    {
                      "text": "```json\n{\"status\":\"Severe Imbalance\",\"summary\":\"Too much work is on one member.\",\"recommendations\":[\"Reassign overdue tasks\"]}\n```"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val result = source.parseResponse(body)

        assertEquals("Severe Imbalance", result.status)
        assertEquals("Too much work is on one member.", result.summary)
        assertEquals(listOf("Reassign overdue tasks"), result.recommendations)
    }

    @Test(expected = Exception::class)
    fun parseResponseThrowsForMalformedJson() {
        source.parseResponse("""{"output": []}""")
    }
}
