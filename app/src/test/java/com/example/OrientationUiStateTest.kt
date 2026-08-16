package com.example

import android.content.Context
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.example.sensor.DynoSensorManager
import com.example.ui.screens.OrientationCalibrationScreen
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OrientationUiStateTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var sensorManager: DynoSensorManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        sensorManager = DynoSensorManager(context)
    }

    @Test
    fun testOrientationScreen_initialPositionOk_displaysOrientacaoOkAndEnablesButton() {
        composeTestRule.setContent {
            OrientationCalibrationScreen(
                sensorManager = sensorManager,
                onCalibrationSuccess = {},
                onBack = {}
            )
        }

        // Must display and enable CALIBRAR AGORA button
        composeTestRule.onNodeWithTag("calibrar_agora_button").assertExists().assertIsEnabled().assertHasClickAction()
        composeTestRule.onAllNodesWithText("Posição correta — pronto para calibrar.").onFirst().assertExists()
    }
}
