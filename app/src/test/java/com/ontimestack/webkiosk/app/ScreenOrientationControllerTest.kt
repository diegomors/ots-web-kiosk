package com.ontimestack.webkiosk.app

import android.content.pm.ActivityInfo
import com.ontimestack.webkiosk.data.Rotation
import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenOrientationControllerTest {
    @Test
    fun `maps every kiosk rotation to the matching Android orientation`() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            Rotation.ROTATION_0.toRequestedOrientation()
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            Rotation.ROTATION_90.toRequestedOrientation()
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
            Rotation.ROTATION_180.toRequestedOrientation()
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
            Rotation.ROTATION_270.toRequestedOrientation()
        )
    }

    @Test
    fun `maps kiosk degrees to Android system rotation quarter turns`() {
        assertEquals(0, Rotation.ROTATION_0.toSystemRotation())
        assertEquals(1, Rotation.ROTATION_90.toSystemRotation())
        assertEquals(2, Rotation.ROTATION_180.toSystemRotation())
        assertEquals(3, Rotation.ROTATION_270.toSystemRotation())
    }
}
