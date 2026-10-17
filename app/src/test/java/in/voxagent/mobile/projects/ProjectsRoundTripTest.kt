package `in`.voxagent.mobile.projects

import `in`.voxagent.mobile.net.VoxJson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ProjectsRoundTripTest {
    @Test
    fun contextEditPreservesDesktopDiagramsAndUnknownNestedFields() {
        val original = VoxJson.parseToJsonElement("""{
          "notes":"old", "future_setting":{"enabled":true},
          "nodes":[{"id":"n","kind":"goal","title":"Goal","body":"old","origin":"user","future_node":7}],
          "messages":[{"id":"m","role":"assistant","body":"Diagram","at":"now",
            "flow":{"title":"Architecture","diagram":{"diagram_type":"architecture","nodes":[]}},
            "diagram_id":"desktop-diagram", "future_message":{"version":2}}]
        }""").jsonObject
        val data = VoxJson.decodeFromJsonElement<ProjectData>(original)
        val edited = data.copy(notes = "new", nodes = data.nodes.map { it.copy(body = "updated") })
        val encoded = VoxJson.parseToJsonElement(VoxJson.encodeToString(edited)).jsonObject
        assertEquals("new", encoded["notes"]?.jsonPrimitive?.content)
        assertEquals(original["future_setting"], encoded["future_setting"])
        val message = encoded["messages"]!!.jsonArray.single().jsonObject
        val originalMessage = original["messages"]!!.jsonArray.single().jsonObject
        listOf("flow", "diagram_id", "future_message").forEach { key ->
            assertEquals(originalMessage[key], message[key])
        }
        assertEquals(7, encoded["nodes"]!!.jsonArray.single().jsonObject["future_node"]!!.jsonPrimitive.int)
        assertEquals("updated", encoded["nodes"]!!.jsonArray.single().jsonObject["body"]!!.jsonPrimitive.content)
    }

    @Test
    fun removingAndReorderingCardsDoesNotRestoreDeletedCardsOrCrossTheirUnknownFields() {
        val data = VoxJson.decodeFromString<ProjectData>("""{
          "nodes":[
            {"id":"a","kind":"goal","title":"A","body":"a","origin":"user","extension":"first"},
            {"id":"b","kind":"goal","title":"B","body":"b","origin":"user","extension":"second"}]
        }""")
        val encoded = VoxJson.encodeToJsonElement(data.copy(nodes = listOf(data.nodes[1].copy(body = "")))).jsonObject
        val node = encoded["nodes"]!!.jsonArray.single().jsonObject
        assertEquals("b", node["id"]!!.jsonPrimitive.content)
        assertEquals("second", node["extension"]?.jsonPrimitive?.content)
        assertEquals("", node["body"]!!.jsonPrimitive.content)
    }
    @Test
    fun historyTrimKeepsUnknownProvenanceWithItsOriginalVersion() {
        val history = JsonArray((0 until 20).map { index ->
            buildJsonObject {
                put("title", "Version $index")
                put("body", "Body $index")
                put("updated_at", "time-$index")
                put("provenance", "source-$index")
            }
        })
        val raw = buildJsonObject {
            put("nodes", JsonArray(listOf(buildJsonObject {
                put("id", "n")
                put("kind", "goal")
                put("title", "Current")
                put("body", "Current body")
                put("origin", "user")
                put("updated_at", "current-time")
                put("history", history)
            })))
        }
        val data = VoxJson.decodeFromJsonElement<ProjectData>(raw)
        val project = Project("p", "Fixture", data, 1, "", "")
        val edited = project.withNode(data.nodes.single().copy(body = "New body"))
        val encoded = VoxJson.encodeToJsonElement(edited.data).jsonObject
        val versions = encoded["nodes"]!!.jsonArray.single().jsonObject["history"]!!.jsonArray
        assertEquals(20, versions.size)
        for (index in 0 until 19) {
            assertEquals("Version ${index + 1}", versions[index].jsonObject["title"]!!.jsonPrimitive.content)
            assertEquals("source-${index + 1}", versions[index].jsonObject["provenance"]?.jsonPrimitive?.content)
        }
        assertEquals("Current", versions.last().jsonObject["title"]!!.jsonPrimitive.content)
        assertFalse("new history must not inherit the removed entry's metadata", versions.last().jsonObject.containsKey("provenance"))
    }

    @Test
    fun reorderedAndNewCitationsDoNotInheritOtherCitationMetadata() {
        val data = VoxJson.decodeFromString<ProjectData>("""{
          "nodes":[{"id":"n","kind":"goal","title":"Goal","body":"Body","origin":"user",
            "citations":[
              {"source_id":"s","path":"a.rs","start_line":1,"end_line":2,"evidence":"alpha"},
              {"source_id":"s","path":"b.rs","start_line":3,"end_line":4,"evidence":"beta"}]}]
        }""")
        val old = data.nodes.single()
        val updated = data.copy(nodes = listOf(old.copy(citations = listOf(
            old.citations[1], old.citations[0], Citation("s", "new.rs", 5, 6)
        ))))
        val encoded = VoxJson.encodeToJsonElement(updated).jsonObject
        val citations = encoded["nodes"]!!.jsonArray.single().jsonObject["citations"]!!.jsonArray
        assertEquals("beta", citations[0].jsonObject["evidence"]?.jsonPrimitive?.content)
        assertEquals("alpha", citations[1].jsonObject["evidence"]?.jsonPrimitive?.content)
        assertFalse(citations[2].jsonObject.containsKey("evidence"))
    }

}
