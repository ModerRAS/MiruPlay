package com.miruplay.tv.player

import org.junit.Assert.assertEquals
import org.junit.Test

class LibVlcObjectInstanceResolverTest {
    @Test
    fun `resolveNativeVlcObjectInstance prefers the direct instance when it is valid`() {
        val holder = FakeVlcObjectHolder(wrapperInstance = 77L, publicInstance = 88L)

        val resolved = resolveNativeVlcObjectInstance(
            directInstance = 55L,
            holder = holder,
        )

        assertEquals(55L, resolved)
    }

    @Test
    fun `resolveNativeVlcObjectInstance prefers public getInstance over wrapper field`() {
        val holder = FakeVlcObjectHolder(wrapperInstance = 77L, publicInstance = 88L)

        val resolved = resolveNativeVlcObjectInstance(
            directInstance = 0L,
            holder = holder,
        )

        assertEquals(88L, resolved)
    }

    @Test
    fun `resolveNativeVlcObjectInstance keeps signed non zero native pointer values`() {
        val holder = FakeVlcObjectHolder(wrapperInstance = 77L, publicInstance = 88L)

        val resolved = resolveNativeVlcObjectInstance(
            directInstance = Long.MIN_VALUE + 9L,
            holder = holder,
        )

        assertEquals(Long.MIN_VALUE + 9L, resolved)
    }

    @Test
    fun `resolveNativeVlcObjectInstance falls back to reflected mInstance when public accessor is absent`() {
        val holder = FakeVlcWrapperOnlyHolder(instance = 77L)

        val resolved = resolveNativeVlcObjectInstance(
            directInstance = 0L,
            holder = holder,
        )

        assertEquals(77L, resolved)
    }
}

private open class FakeVlcObjectBase(
    @Suppress("unused")
    private val mInstance: Long,
)

private class FakeVlcObjectHolder(
    wrapperInstance: Long,
    private val publicInstance: Long,
) : FakeVlcObjectBase(wrapperInstance) {
    fun getInstance(): Long = publicInstance
}

private class FakeVlcWrapperOnlyHolder(
    instance: Long,
) : FakeVlcObjectBase(instance)
