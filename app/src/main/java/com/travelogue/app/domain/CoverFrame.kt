package com.travelogue.app.domain

import com.travelogue.app.data.db.entity.TripEntity
import kotlin.math.max

/**
 * Which part of a trip's cover shows when it is cropped into a frame of any shape.
 *
 * The photo is scaled to fill the frame (crop), placed so that the point ([x], [y]) of the photo
 * (fractions 0..1 across and down) sits at the same fraction of the frame, then zoomed by [zoom]
 * around that point. That point is therefore always in view, whether the frame is a wide header, a
 * card or a tall thumbnail, and the photo always covers the whole frame.
 */
data class CoverFrame(val x: Float = 0.5f, val y: Float = 0.5f, val zoom: Float = 1f) {

    val isCentered: Boolean get() = this == Center

    /**
     * The frame after the photo is dragged by ([dx], [dy]) pixels inside a [frameW] × [frameH] frame
     * showing an [imageW] × [imageH] photo. Dragging right reveals more of the left of the photo.
     * Along an axis where the photo exactly fits there is nothing to reveal, so it doesn't move.
     */
    fun dragged(dx: Float, dy: Float, frameW: Float, frameH: Float, imageW: Float, imageH: Float): CoverFrame {
        if (frameW <= 0f || frameH <= 0f || imageW <= 0f || imageH <= 0f) return this
        val s = cropScale(frameW, frameH, imageW, imageH)
        // How much wider/taller than the frame the zoomed photo is. Moving x from 0 to 1 slides the
        // photo by exactly this much, so a drag of d pixels is a change of d / overflow.
        val overflowX = zoom * imageW * s - frameW
        val overflowY = zoom * imageH * s - frameH
        val nx = if (overflowX > MIN_OVERFLOW) x - dx / overflowX else x
        val ny = if (overflowY > MIN_OVERFLOW) y - dy / overflowY else y
        return copy(x = nx.coerceIn(0f, 1f), y = ny.coerceIn(0f, 1f))
    }

    fun zoomedBy(factor: Float): CoverFrame = withZoom(zoom * factor)

    fun withZoom(z: Float): CoverFrame = copy(zoom = z.coerceIn(1f, MAX_ZOOM))

    /**
     * The part of the photo visible in a [frameW] × [frameH] frame, as fractions of the photo:
     * left, top, right, bottom.
     */
    fun visibleArea(frameW: Float, frameH: Float, imageW: Float, imageH: Float): List<Float> {
        val s = cropScale(frameW, frameH, imageW, imageH)
        val w = frameW / (zoom * imageW * s)
        val h = frameH / (zoom * imageH * s)
        return listOf(x - x * w, y - y * h, x + (1 - x) * w, y + (1 - y) * h)
    }

    companion object {
        val Center = CoverFrame()
        const val MAX_ZOOM = 4f
        private const val MIN_OVERFLOW = 0.5f

        /** The scale at which the photo just covers the frame. */
        fun cropScale(frameW: Float, frameH: Float, imageW: Float, imageH: Float): Float = max(frameW / imageW, frameH / imageH)

        /** Values read from storage or an archive may be anything; keep them in range. */
        fun of(x: Float, y: Float, zoom: Float): CoverFrame = CoverFrame(
            x = x.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0.5f,
            y = y.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0.5f,
            zoom = zoom.takeIf { it.isFinite() }?.coerceIn(1f, MAX_ZOOM) ?: 1f,
        )
    }
}

val TripEntity.coverFrame: CoverFrame get() = CoverFrame.of(coverFocusX, coverFocusY, coverZoom)

fun TripEntity.withCoverFrame(f: CoverFrame): TripEntity = copy(coverFocusX = f.x, coverFocusY = f.y, coverZoom = f.zoom)
