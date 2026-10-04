package com.example.samsonic.playback

/** How much music the player holds ahead of what it plays: from [minMs] it tops up, to [maxMs] or [bytes]. */
data class BufferPlan(val minMs: Int, val maxMs: Int, val bytes: Int)

private const val MB = 1024 * 1024

/**
 * A deeper buffer than Media3's default (which suits video), so a poor connection has
 * minutes of music in hand rather than seconds: up to three minutes ahead, topped up once
 * under one, and capped by size too, as hi-res files run large. A phone with little memory
 * ([memoryClassMb], the app's heap limit; or [lowRam]) holds less, so the buffer doesn't
 * crowd the app out of it. Flagships report 256 MB or more, and keep the full buffer.
 */
fun bufferPlanFor(memoryClassMb: Int, lowRam: Boolean): BufferPlan = when {
    lowRam || memoryClassMb <= 128 -> BufferPlan(minMs = 30_000, maxMs = 60_000, bytes = 16 * MB)
    memoryClassMb < 256 -> BufferPlan(minMs = 60_000, maxMs = 120_000, bytes = 32 * MB)
    else -> BufferPlan(minMs = 60_000, maxMs = 180_000, bytes = 48 * MB)
}
