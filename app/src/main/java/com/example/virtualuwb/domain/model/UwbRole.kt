package com.example.virtualuwb.domain.model

/**
 * Defines the role of a UWB device in the indoor positioning system.
 *
 * - [ANCHOR]: A fixed reference point with a known position. Not movable.
 * - [TAG]:    A mobile device whose position is tracked relative to anchors. Movable.
 */
enum class UwbRole {

    /** Fixed reference point installed at a known location. */
    ANCHOR,

    /** Mobile device that moves within the tracking area. */
    TAG;

    /**
     * Whether this role represents a device that can move.
     * - ANCHOR → false (stationary)
     * - TAG    → true  (mobile)
     */
    val isMovable: Boolean
        get() = this == TAG
}
