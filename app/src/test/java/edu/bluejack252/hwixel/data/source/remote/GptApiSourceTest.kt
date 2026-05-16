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

    @Test(expected = Exception::class)
    fun parseResponseThrowsForMalformedJson() {
        source.parseResponse("""{"output": []}""")
    }
}
