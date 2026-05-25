package com.miruplay.tv.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PagedListUiConventionsTest {
    @Test
    fun `page start maps every item to its visible page`() {
        assertEquals(0, pagedListPageStartForIndex(index = 0, itemCount = 17, pageSize = 8))
        assertEquals(0, pagedListPageStartForIndex(index = 7, itemCount = 17, pageSize = 8))
        assertEquals(8, pagedListPageStartForIndex(index = 8, itemCount = 17, pageSize = 8))
        assertEquals(16, pagedListPageStartForIndex(index = 16, itemCount = 17, pageSize = 8))
        assertEquals(16, pagedListPageStartForIndex(index = 30, itemCount = 17, pageSize = 8))
        assertEquals(0, pagedListPageStartForIndex(index = 0, itemCount = 0, pageSize = 8))
        assertEquals(0, pagedListPageStartForIndex(index = 0, itemCount = 17, pageSize = 0))
    }

    @Test
    fun `coerced page start snaps to valid page boundaries`() {
        assertEquals(8, pagedListCoercedPageStart(pageStart = 12, itemCount = 17, pageSize = 8))
        assertEquals(16, pagedListCoercedPageStart(pageStart = 40, itemCount = 17, pageSize = 8))
        assertEquals(0, pagedListCoercedPageStart(pageStart = -8, itemCount = 17, pageSize = 8))
        assertEquals(0, pagedListCoercedPageStart(pageStart = 8, itemCount = 0, pageSize = 8))
        assertEquals(0, pagedListCoercedPageStart(pageStart = 8, itemCount = 17, pageSize = 0))
    }

    @Test
    fun `page summary uses shared TV style range copy`() {
        assertEquals(
            "显示 9-16 / 17 个条目，按上/下继续翻页。",
            pagedListPageSummary(pageStart = 8, visibleCount = 8, itemCount = 17, pageSize = 8, unitLabel = "个条目"),
        )
        assertEquals(
            "候选：显示 5-8 / 13 个条目，按上/下继续翻页。",
            pagedListPageSummary(
                pageStart = 4,
                visibleCount = 4,
                itemCount = 13,
                pageSize = 4,
                unitLabel = "个条目",
                prefix = "候选",
            ),
        )
        assertEquals(
            null,
            pagedListPageSummary(pageStart = 0, visibleCount = 5, itemCount = 5, pageSize = 8, unitLabel = "个条目"),
        )
    }
}
