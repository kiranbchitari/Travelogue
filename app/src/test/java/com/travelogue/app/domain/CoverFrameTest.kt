package com.travelogue.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverFrameTest {
    // A landscape photo (4:3) in a square-ish card frame: it overflows sideways only.
    private val imageW = 4000f
    private val imageH = 3000f
    private val frameW = 1000f
    private val frameH = 900f

    private fun CoverFrame.area() = visibleArea(frameW, frameH, imageW, imageH)

    @Test fun centeredShowsTheMiddle() {
        val (l, t, r, b) = CoverFrame.Center.area()
        assertEquals(0.5f, (l + r) / 2, 1e-4f)
        assertEquals(0f, t, 1e-4f)
        assertEquals(1f, b, 1e-4f)
    }

    @Test fun focusAtAnEdgeShowsThatEdge() {
        val left = CoverFrame(x = 0f).area()
        assertEquals(0f, left[0], 1e-4f)
        val right = CoverFrame(x = 1f).area()
        assertEquals(1f, right[2], 1e-4f)
    }

    @Test fun neverShowsOutsideThePhoto() {
        for (x in listOf(0f, 0.2f, 0.5f, 0.9f, 1f)) for (y in listOf(0f, 0.5f, 1f)) for (z in listOf(1f, 2f, 4f)) {
            val (l, t, r, b) = CoverFrame(x, y, z).area()
            assertTrue("$x $y $z", l >= -1e-4f && t >= -1e-4f && r <= 1 + 1e-4f && b <= 1 + 1e-4f)
        }
    }

    @Test fun focusPointStaysInView() {
        for (x in listOf(0f, 0.3f, 0.7f, 1f)) for (z in listOf(1f, 3f)) {
            val (l, _, r, _) = CoverFrame(x, 0.5f, z).area()
            assertTrue(x in l - 1e-4f..r + 1e-4f)
        }
    }

    @Test fun draggingRightRevealsTheLeftOfThePhoto() {
        val moved = CoverFrame.Center.dragged(100f, 0f, frameW, frameH, imageW, imageH)
        assertTrue(moved.x < 0.5f)
        assertTrue(moved.area()[0] < CoverFrame.Center.area()[0])
    }

    @Test fun dragMovesThePhotoByThePixelsDragged() {
        // Photo scaled to 900 tall is 1200 wide in a 1000 frame: 200 px of overflow.
        val moved = CoverFrame.Center.dragged(50f, 0f, frameW, frameH, imageW, imageH)
        assertEquals(0.25f, moved.x, 1e-4f)
    }

    @Test fun noMovementAlongAnAxisThatFits() {
        val moved = CoverFrame.Center.dragged(0f, 300f, frameW, frameH, imageW, imageH)
        assertEquals(0.5f, moved.y, 1e-6f)
    }

    @Test fun zoomingInAllowsVerticalMovement() {
        val moved = CoverFrame.Center.withZoom(2f).dragged(0f, 300f, frameW, frameH, imageW, imageH)
        assertTrue(moved.y < 0.5f)
    }

    @Test fun dragsAndZoomStayInRange() {
        val far = CoverFrame.Center.dragged(-1e6f, 1e6f, frameW, frameH, imageW, imageH)
        assertEquals(1f, far.x, 0f)
        assertEquals(CoverFrame.MAX_ZOOM, CoverFrame.Center.zoomedBy(100f).zoom, 0f)
        assertEquals(1f, CoverFrame.Center.zoomedBy(0.01f).zoom, 0f)
    }

    @Test fun badStoredValuesFallBackToCentered() {
        assertEquals(CoverFrame.Center, CoverFrame.of(Float.NaN, Float.POSITIVE_INFINITY, Float.NaN))
        assertEquals(CoverFrame(1f, 0f, 4f), CoverFrame.of(7f, -3f, 99f))
    }
}
