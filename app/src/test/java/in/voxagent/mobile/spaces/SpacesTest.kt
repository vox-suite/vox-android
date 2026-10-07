package `in`.voxagent.mobile.spaces

import `in`.voxagent.mobile.net.VoxJson
import org.junit.Assert.*
import org.junit.Test

class SpacesTest {
    private fun node(id: String, kind: String) = SpaceNode(id = id, kind = kind, title = id)

    @Test
    fun goalStaysCentralAndEveryNodeHasAUniquePosition() {
        val nodes =
            listOf(node("option", "option"), node("goal", "goal")) +
                (0..11).map { node("research$it", "research") } +
                listOf(node("step", "step"))
        val layout = radialLayout(nodes)
        assertTrue(layout.getValue("goal").central)
        assertEquals(nodes.size, layout.size)
        assertEquals(nodes.size, layout.values.map { it.x to it.y }.toSet().size)
        assertTrue(layout.getValue("research0").x < layout.getValue("goal").x)
        assertTrue(layout.getValue("step").y > layout.getValue("goal").y)
    }

    @Test
    fun emptyGraphAndGoalFallbackAreSupported() {
        assertTrue(radialLayout(emptyList()).isEmpty())
        assertTrue(radialLayout(listOf(node("first", "option"))).getValue("first").central)
    }

    @Test
    fun committedOrDroppedSpacesCannotBeEdited() {
        assertFalse(Space(id = "a", state = "committed").editable)
        assertFalse(Space(id = "a", state = "dropped").editable)
        assertTrue(Space(id = "a", state = "planned").editable)
    }

    @Test
    fun commitRequiresCompletedPlanOrStepCards() {
        val space = Space("a")
        assertFalse(SpaceGraph(space).canCommit)
        assertFalse(SpaceGraph(space, listOf(node("option", "option"))).canCommit)
        assertTrue(SpaceGraph(space, listOf(node("step", "step"))).canCommit)
        assertFalse(
            SpaceGraph(space, listOf(node("step", "step").copy(state = "rejected"))).canCommit
        )
        assertFalse(
            SpaceGraph(space.copy(state = "committed"), listOf(node("step", "step"))).canCommit
        )
    }

    @Test
    fun coreGraphDecodesNullableFieldsAndUnknownMetadata() {
        val graph =
            VoxJson.decodeFromString<SpaceGraph>(
                """{
            "space":{"id":"a","title":"Trip","intent":"Explore","state":"ideating",
              "agent_spec":{"mission":"Compare trips"},"run_error":null,"committed_collection_id":null},
            "nodes":[{"id":"n","kind":"goal","title":"Trip","body":"","state":"done",
              "data":{"budget":500},"position":null,"provenance":{},"derived_from":[],"version":1}],
            "edges":[]
        }"""
            )
        assertEquals("Compare trips", graph.space.mission)
        assertEquals("goal", graph.nodes.single().kind)
        assertNull(graph.space.run_error)
    }
}
