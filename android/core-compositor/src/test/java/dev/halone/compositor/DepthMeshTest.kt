package dev.halone.compositor

import dev.halone.compositor.gl.TexturedQuad3D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DepthMeshTest {

    @Test
    fun `the mesh covers the same unit quad, with the same texture corners`() {
        val data = TexturedQuad3D.gridVertexData()
        val columns = TexturedQuad3D.GRID_COLUMNS
        val rows = TexturedQuad3D.GRID_ROWS
        assertEquals((columns + 1) * (rows + 1) * 5, data.size)
        fun vertex(column: Int, row: Int) = ((row * (columns + 1)) + column) * 5
        val first = vertex(0, 0)
        assertEquals(-0.5f, data[first], 0f); assertEquals(-0.5f, data[first + 1], 0f)
        assertEquals(0f, data[first + 3], 0f); assertEquals(0f, data[first + 4], 0f)
        val last = vertex(columns, rows)
        assertEquals(0.5f, data[last], 1e-6f); assertEquals(0.5f, data[last + 1], 1e-6f)
        assertEquals(1f, data[last + 3], 1e-6f); assertEquals(1f, data[last + 4], 1e-6f)
    }

    @Test
    fun `every index names a vertex, and fits in 16 bits`() {
        val vertices = (TexturedQuad3D.GRID_COLUMNS + 1) * (TexturedQuad3D.GRID_ROWS + 1)
        assertTrue(vertices <= 65_536)
        val indices = TexturedQuad3D.gridIndexData()
        assertEquals(TexturedQuad3D.GRID_INDEX_COUNT, indices.size)
        assertTrue(indices.all { (it.toInt() and 0xFFFF) < vertices })
    }
}
