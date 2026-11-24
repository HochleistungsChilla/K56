package app.k56.player.core.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsRedactionTest {

    @Test
    fun aCredentialLosesItsValueAndKeepsItsName() {
        val text = DiagnosticsRedaction.format(
            listOf("api_key" to "TOPSECRET", "IncludeItemTypes" to "Audio"),
        )
        assertEquals("api_key=***&IncludeItemTypes=Audio", text)
        assertFalse(text.contains("TOPSECRET"))
    }

    @Test
    fun theCheckIsCaseInsensitive() {
        val text = DiagnosticsRedaction.format(listOf("Api_Key" to "TOPSECRET", "Token" to "TOKEN-ABC"))
        assertEquals("Api_Key=***&Token=***", text)
        assertFalse(text.contains("TOPSECRET"))
        assertFalse(text.contains("TOKEN-ABC"))
    }

    @Test
    fun nonSecretValuesSurvive() {
        val text = DiagnosticsRedaction.format(
            listOf("SortBy" to "", "Limit" to "500", "Recursive" to "true"),
        )
        assertEquals("SortBy=&Limit=500&Recursive=true", text)
    }

    @Test
    fun aLongValueIsTruncated() {
        val text = DiagnosticsRedaction.format(listOf("SearchTerm" to "x".repeat(1_000)))
        assertTrue(text.length <= 400)
        assertTrue(text.startsWith("SearchTerm=xxx"))
    }

    @Test
    fun emptyInputStaysEmpty() {
        assertEquals("", DiagnosticsRedaction.format(emptyList()))
    }
}
