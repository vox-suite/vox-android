package `in`.voxagent.mobile.pulse

import `in`.voxagent.mobile.net.VoxJson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class PulseTest {
    @Test
    fun missingPointsStayGapsAndZeroIsValidData() {
        val result = PulseResult(points = listOf(PulsePoint("Mon", null), PulsePoint("Tue", 0.0)))
        assertTrue(result.canSave)
        assertNull(result.points.first().value)
        assertEquals(0.0, result.points.last().value!!, 0.0)
        assertFalse(PulseResult(points = listOf(PulsePoint("Mon", null))).canSave)
        assertFalse(result.copy(error = "Access unavailable").canSave)
    }

    @Test
    fun rangeChangesResetOffsetAndAdjustDenseBuckets() {
        val definition = PulseDefinition(measurement_id = "m", bucket = "day", offset_days = 30)
        assertEquals("week", definition.withRange(90).bucket)
        assertEquals("month", definition.withRange(3650).bucket)
        assertEquals(0, definition.withRange(7).offset_days)
        assertEquals(0, definition.copy(offset_days = 2).later().offset_days)
    }

    @Test
    fun coreCanvasDecodesNullResultsAndBoardMetadata() {
        val canvas =
            VoxJson.decodeFromString<PulseCanvas>(
                """{"charts":[{"id":"c","title":"Music","definition":{"version":2,"measurement_id":"m","chart_type":"bar","bucket":"week","dimension":null,"period_days":30,"timezone":"Asia/Kolkata"},"result":null}],"legacy_boards":[{"id":"b","name":"Old board","chart_count":2}],"next_cursor":null}"""
            )
        assertNull(canvas.charts.single().result)
        assertEquals(2, canvas.legacy_boards.single().chart_count)
    }

    @Test
    fun outboundDefinitionsIncludeRequiredCoreFieldsEvenAtDefaults() {
        val json =
            PulseJson.parseToJsonElement(
                    PulseJson.encodeToString(PulseDefinition(measurement_id = "m"))
                )
                .jsonObject
        assertEquals(2, json.getValue("version").jsonPrimitive.int)
        assertEquals(30, json.getValue("period_days").jsonPrimitive.int)
        assertEquals("bar", json.getValue("chart_type").jsonPrimitive.content)
        assertTrue(json.getValue("timezone").jsonPrimitive.content.isNotBlank())
        assertEquals(JsonNull, json.getValue("bucket"))
        val draft =
            PulseJson.parseToJsonElement(
                    PulseJson.encodeToString(
                        GoalDraft(
                            title = "Bike",
                            kind = "saving",
                            direction = "at_least",
                            target = 80000.0,
                            unit = "INR",
                        )
                    )
                )
                .jsonObject
        assertTrue(draft.getValue("timezone").jsonPrimitive.content.isNotBlank())
    }

    @Test
    fun signedSavingsEntriesMustBeFiniteAndNonzero() {
        assertNull(validEntry("NaN"))
        assertNull(validEntry("Infinity"))
        assertNull(validEntry("0"))
        assertEquals(-100.0, validEntry("-100")!!, 0.0)
    }
}
