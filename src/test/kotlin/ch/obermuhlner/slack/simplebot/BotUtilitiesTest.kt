package ch.obermuhlner.slack.simplebot

import org.junit.Test
import org.junit.Assert.assertEquals

class BotUtilitiesTest {

    @Test
    fun test_limitedForLoop_2_3_empty() {
        assertEquals(
                listOf<String>(),
                limitedForLoopIntoList(2, 3, listOf<String>()))
    }

    @Test
    fun test_limitedForLoop_0_0_empty() {
        assertEquals(
                listOf<String>(),
                limitedForLoopIntoList(0, 0, listOf<String>()))
    }

    @Test
    fun test_limitedForLoop_2_3_5elements() {
        assertEquals(
                listOf("a", "b", "c", "d", "e"),
                limitedForLoopIntoList(2, 3, listOf("a", "b", "c", "d", "e")))
    }

    @Test
    fun test_limitedForLoop_2_3_6elements() {
        assertEquals(
                listOf("a", "b", "... (1)", "d", "e", "f"),
                limitedForLoopIntoList(2, 3, listOf("a", "b", "c", "d", "e", "f")))
    }

    @Test
    fun test_limitedForLoop_2_0_6elements() {
        assertEquals(
                listOf("a", "b", "... (4)"),
                limitedForLoopIntoList(2, 0, listOf("a", "b", "c", "d", "e", "f")))
    }

    @Test
    fun test_limitedForLoop_0_3_6elements() {
        assertEquals(
                listOf("... (3)", "d", "e", "f"),
                limitedForLoopIntoList(0, 3, listOf("a", "b", "c", "d", "e", "f")))
    }

    @Test
    fun test_limitedForLoop_0_0_6elements() {
        assertEquals(
                listOf("... (6)"),
                limitedForLoopIntoList(0, 0, listOf("a", "b", "c", "d", "e", "f")))
    }

    private fun limitedForLoopIntoList(leftSize: Int, rightSize: Int, elements: Collection<String>): List<String> {
        val result = mutableListOf<String>()

        limitedForLoop(leftSize, rightSize, elements, { element ->
            result.add(element)
        }, { skipped ->
            result.add("... ($skipped)")
        })

        return result
    }

}