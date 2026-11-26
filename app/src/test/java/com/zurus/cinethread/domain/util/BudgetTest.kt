package com.zurus.cinethread.domain.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Budget: учёт лимитов (docs/04_algorithms.md §1.1, §3.6).
 */
class BudgetTest {

    @Test
    fun `fresh budget is not exhausted`() {
        val budget = Budget()
        assertTrue(!budget.exhausted())
        assertEquals(0, budget.requestsUsed)
        assertEquals(0, budget.nodesVisited)
    }

    @Test
    fun `request limit exhausts budget`() {
        val budget = Budget(maxRequests = 2)
        budget.recordRequest()
        assertTrue(!budget.exhausted())
        budget.recordRequest()
        assertTrue(budget.exhausted(), "лимит запросов достигнут")
    }

    @Test
    fun `node limit exhausts budget`() {
        val budget = Budget(maxNodesVisited = 3)
        repeat(3) { budget.recordNodeVisited() }
        assertTrue(budget.exhausted())
    }

    @Test
    fun `wall clock limit exhausts budget`() {
        var now = 0L
        val budget = Budget(maxWallClockMs = 1000L, now = { now })
        now += 999L
        assertTrue(!budget.exhausted())
        now += 1L
        assertTrue(budget.exhausted(), "wall-clock лимит достигнут")
    }

    @Test
    fun `default limits match documentation`() {
        val budget = Budget()
        assertEquals(300, budget.maxRequests)
        assertEquals(1500, budget.maxNodesVisited)
        assertEquals(6000L, budget.maxWallClockMs)
    }

    @Test
    fun `generation profile is lazier than default (D1)`() {
        assertTrue(
            Budget.GENERATION_MAX_WALL_CLOCK_MS > Budget.DEFAULT_MAX_WALL_CLOCK_MS,
            "generation wall-clock длиннее дефолта (медленный канал ~1 запрос/с)",
        )
        assertTrue(
            Budget.GENERATION_MAX_WALL_CLOCK_MS >= 60_000L,
            "generation wall-clock покрывает минуту загрузки",
        )
        assertTrue(
            Budget.GENERATION_MAX_REQUESTS >= Budget.DEFAULT_MAX_REQUESTS,
            "generation лимит запросов не меньше дефолта",
        )
        val gen = Budget(
            maxRequests = Budget.GENERATION_MAX_REQUESTS,
            maxNodesVisited = Budget.GENERATION_MAX_NODES_VISITED,
            maxWallClockMs = Budget.GENERATION_MAX_WALL_CLOCK_MS,
        )
        assertTrue(!gen.exhausted(), "свежий generation-бюджет не исчерпан")
    }
}
