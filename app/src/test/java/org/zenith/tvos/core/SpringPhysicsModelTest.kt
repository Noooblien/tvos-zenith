package org.zenith.tvos.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.zenith.tvos.core.focus.SpringPhysicsModel

class SpringPhysicsModelTest {

    @Test
    fun testSpringBoundaryConditions() {
        val spring = SpringPhysicsModel()

        // t = 0.0 must evaluate to 0.0
        assertEquals(0f, spring.getInterpolation(0.0f), 0.0001f)

        // t = 1.0 must evaluate to 1.0
        assertEquals(1f, spring.getInterpolation(1.0f), 0.0001f)
    }

    @Test
    fun testSpringOvershootCharacteristics() {
        val spring = SpringPhysicsModel(stiffness = 280f, dampingRatio = 0.72f)

        // Find max displacement across steps
        var maxDisplacement = 0f
        for (i in 0..100) {
            val t = i / 100f
            val value = spring.getInterpolation(t)
            if (value > maxDisplacement) {
                maxDisplacement = value
            }
        }

        // Underdamped spring must have subtle overshoot between 1.01x and 1.15x
        assertTrue("Max displacement was $maxDisplacement, expected subtle overshoot", maxDisplacement > 1.0f)
        assertTrue("Max displacement $maxDisplacement exceeded 1.15 bound", maxDisplacement <= 1.15f)
    }

    @Test
    fun testCriticalDampingDisplacement() {
        val criticalSpring = SpringPhysicsModel(stiffness = 300f, dampingRatio = 0.99f)
        val midValue = criticalSpring.getInterpolation(0.5f)
        assertTrue(midValue in 0.5f..1.1f)
    }
}
