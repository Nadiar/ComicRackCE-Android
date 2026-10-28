package com.comicrack.settings.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.comicrack.data.preferences.PreferencesViewModel
import com.comicrack.settings.screens.PreferencesScreen
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject
import kotlin.test.assertEquals

/**
 * Integration Tests for PreferencesScreen
 *
 * **Phase 3 Verification**: Validates Compose UI integration with ViewModel and Repository using Hilt DI.
 *
 * Tests cover:
 * - Screen rendering
 * - Theme preference UI elements
 * - Library preference UI elements
 * - Reader preference UI elements
 * - User interactions (radio button clicks, toggle switches)
 * - State updates flow through ViewModel to Repository
 * - Error state display (future: Snackbar testing)
 *
 * Run with:
 * ```bash
 * ./gradlew :app:connectedAndroidTest --tests "com.comicrack.settings.screens.PreferencesScreenTest"
 * ```
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
class PreferencesScreenTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val composeTestRule = createComposeRule()

    @Inject
    lateinit var viewModel: PreferencesViewModel

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun testScreenRendersWithLoadingState() =
        runTest {
            composeTestRule.setContent {
                PreferencesScreen(viewModel = viewModel)
            }

            // Initially should show loading
            composeTestRule.onNodeWithText("Loading preferences...").assertExists()
        }

    @Test
    fun testScreenRendersPreferencesSections() =
        runTest {
            // Wait for state to load
            val _ = viewModel.uiState.first { !it.isLoading }

            composeTestRule.setContent {
                PreferencesScreen(viewModel = viewModel)
            }

            // Verify section headers
            composeTestRule.onNodeWithText("Theme").assertExists()
            composeTestRule.onNodeWithText("Library").assertExists()
            composeTestRule.onNodeWithText("Reader").assertExists()
        }

    @Test
    fun testThemePreferencesAreDisplayed() =
        runTest {
            val _ = viewModel.uiState.first { !it.isLoading }

            composeTestRule.setContent {
                PreferencesScreen(viewModel = viewModel)
            }

            // Check theme options
            composeTestRule.onNodeWithText("System (Follow device settings)").assertExists()
            composeTestRule.onNodeWithText("Light").assertExists()
            composeTestRule.onNodeWithText("Dark").assertExists()
        }

    @Test
    fun testLibraryViewModePreferencesAreDisplayed() =
        runTest {
            val _ = viewModel.uiState.first { !it.isLoading }

            composeTestRule.setContent {
                PreferencesScreen(viewModel = viewModel)
            }

            // Check view mode options
            composeTestRule.onNodeWithText("Grid (Cover thumbnails)").assertExists()
            composeTestRule.onNodeWithText("List (Compact view)").assertExists()
        }

    @Test
    fun testLibrarySortOrderPreferencesAreDisplayed() =
        runTest {
            val _ = viewModel.uiState.first { !it.isLoading }

            composeTestRule.setContent {
                PreferencesScreen(viewModel = viewModel)
            }

            // Check sort order options
            composeTestRule.onNodeWithText("By Title (A-Z)").assertExists()
            composeTestRule.onNodeWithText("By Date Added (Newest first)").assertExists()
            composeTestRule.onNodeWithText("By Last Read (Recently read first)").assertExists()
        }

    @Test
    fun testReaderModePreferencesAreDisplayed() =
        runTest {
            val _ = viewModel.uiState.first { !it.isLoading }

            composeTestRule.setContent {
                PreferencesScreen(viewModel = viewModel)
            }

            // Check reader mode options
            composeTestRule.onNodeWithText("Page (One page at a time)").assertExists()
            composeTestRule.onNodeWithText("Scroll (Continuous scroll)").assertExists()
        }

    @Test
    fun testReaderTogglePreferencesAreDisplayed() =
        runTest {
            val _ = viewModel.uiState.first { !it.isLoading }

            composeTestRule.setContent {
                PreferencesScreen(viewModel = viewModel)
            }

            // Check reader toggle options
            composeTestRule.onNodeWithText("Flash Cover Page").assertExists()
            composeTestRule.onNodeWithText("Ask for Rating").assertExists()
            composeTestRule.onNodeWithText("Show Page Numbers").assertExists()
            composeTestRule.onNodeWithText("Keep Screen On").assertExists()
        }

    @Test
    fun testBrightnessSliderIsDisplayed() =
        runTest {
            val _ = viewModel.uiState.first { !it.isLoading }

            composeTestRule.setContent {
                PreferencesScreen(viewModel = viewModel)
            }

            // Check brightness slider labels
            composeTestRule.onNodeWithText("Reader Brightness").assertExists()
            composeTestRule.onNodeWithText("Dark").assertExists()
            composeTestRule.onNodeWithText("Bright").assertExists()
        }

    @Test
    fun testThemeSelectionUpdatesViewModel() =
        runTest {
            val _ = viewModel.uiState.first { !it.isLoading }

            composeTestRule.setContent {
                PreferencesScreen(viewModel = viewModel)
            }

            // Click Dark theme
            composeTestRule.onNodeWithText("Dark").performClick()

            // Wait for state update
            val updatedState =
                viewModel.uiState.first {
                    it.appTheme == AppPreferencesRepository.AppTheme.DARK
                }
            assertEquals(AppPreferencesRepository.AppTheme.DARK, updatedState.appTheme)
        }

    @Test
    fun testViewModeSelectionUpdatesViewModel() =
        runTest {
            val _ = viewModel.uiState.first { !it.isLoading }

            composeTestRule.setContent {
                PreferencesScreen(viewModel = viewModel)
            }

            // Click List view mode
            composeTestRule.onNodeWithText("List (Compact view)").performClick()

            // Wait for state update
            val updatedState =
                viewModel.uiState.first {
                    it.libraryViewMode == AppPreferencesRepository.LibraryViewMode.LIST
                }
            assertEquals(AppPreferencesRepository.LibraryViewMode.LIST, updatedState.libraryViewMode)
        }

    @Test
    fun testSortOrderSelectionUpdatesViewModel() =
        runTest {
            val _ = viewModel.uiState.first { !it.isLoading }

            composeTestRule.setContent {
                PreferencesScreen(viewModel = viewModel)
            }

            // Click Last Read sort order
            composeTestRule.onNodeWithText("By Last Read (Recently read first)").performClick()

            // Wait for state update
            val updatedState =
                viewModel.uiState.first {
                    it.librarySortOrder == AppPreferencesRepository.SortOrder.LAST_READ
                }
            assertEquals(AppPreferencesRepository.SortOrder.LAST_READ, updatedState.librarySortOrder)
        }

    @Test
    fun testReaderModeSelectionUpdatesViewModel() =
        runTest {
            val _ = viewModel.uiState.first { !it.isLoading }

            composeTestRule.setContent {
                PreferencesScreen(viewModel = viewModel)
            }

            // Click Scroll mode
            composeTestRule.onNodeWithText("Scroll (Continuous scroll)").performClick()

            // Wait for state update
            val updatedState =
                viewModel.uiState.first {
                    it.readerMode == AppPreferencesRepository.ReaderMode.SCROLL
                }
            assertEquals(AppPreferencesRepository.ReaderMode.SCROLL, updatedState.readerMode)
        }

    @Test
    fun testNavigationBackIsCallable() =
        runTest {
            val _ = viewModel.uiState.first { !it.isLoading }
            var backPressed = false

            composeTestRule.setContent {
                PreferencesScreen(
                    viewModel = viewModel,
                    onNavigateBack = { backPressed = true },
                )
            }

            // Click back button (look for ArrowBack icon)
            composeTestRule.onNodeWithText("Back").performClick()
            assertEquals(true, backPressed)
        }
}
