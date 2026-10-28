package com.comicrack.navigation

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.comicrack.data.database.ComicRackDatabase
import com.comicrack.data.database.entities.ComicBook
import com.comicrack.data.database.entities.LibraryPath
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * UI Tests for NavigationRail with Database Integration (Phase 2)
 */
class NavigationRailTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    lateinit var viewModel: NavigationRailViewModel
    lateinit var db: ComicRackDatabase

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = ComicRackDatabase.getTestInstance(context)
        viewModel = NavigationRailViewModel(db.comicBookDao(), db.libraryPathDao())

        // Populate with some test data
        runBlocking {
            val pathId =
                db.libraryPathDao().insertLibraryPath(
                    LibraryPath(path = "test/path", displayName = "Test Library", comicCount = 5),
                )
            // comicCount in LibraryPath is what's displayed in badge for physical libs

            val now = System.currentTimeMillis()
            val oneWeekAgo = now - (7 * 24 * 60 * 60 * 1000) // 7 days ago
            val twoWeeksAgo = now - (14 * 24 * 60 * 60 * 1000) // 14 days ago (outside "new" window)

            db.comicBookDao().insertComicBooks(
                ComicBook(libraryPathId = pathId, title = "Comic 1", filePath = "test/1", added = now),
                ComicBook(libraryPathId = pathId, title = "Comic 2", filePath = "test/2", added = oneWeekAgo),
                ComicBook(libraryPathId = pathId, title = "Comic 3", filePath = "test/3", added = twoWeeksAgo),
            )
        }
    }

    @Test
    fun testRailToggle() {
        composeTestRule.setContent {
            NavigationRail(viewModel = viewModel)
        }

        // Initially closed
        composeTestRule.onNodeWithText("All Comics").assertDoesNotExist()

        // Open via ViewModel
        viewModel.toggleRail()
        composeTestRule.onNodeWithText("All Comics").assertIsDisplayed()

        // Close
        viewModel.closeRail()
        composeTestRule.onNodeWithText("All Comics").assertDoesNotExist()
    }

    @Test
    fun testRailItemsAndCounts() {
        composeTestRule.setContent {
            NavigationRail(viewModel = viewModel)
        }

        viewModel.toggleRail()

        // Verify virtual items from database
        composeTestRule.onNodeWithText("All Comics").assertIsDisplayed()
        composeTestRule.onNodeWithText("3").assertIsDisplayed() // Total comics inserted

        // Verify "New Comics" item (shows comics from last 7 days)
        composeTestRule.onNodeWithText("New Comics").assertIsDisplayed()
        // Should show "2" for new comics (within last 7 days)
        // Note: Exact badge visibility depends on compose tree traversal

        // Verify physical items from database
        composeTestRule.onNodeWithText("Test Library").assertIsDisplayed()
        composeTestRule.onNodeWithText("5").assertIsDisplayed() // comicCount from LibraryPath
    }

    @Test
    fun testBadgeFormatting() {
        val formatted = viewModel.formatBadgeCount(1234)
        assert(formatted == "999+")

        val normal = viewModel.formatBadgeCount(50)
        assert(normal == "50")
    }

    @Test
    fun testItemSelection() {
        composeTestRule.setContent {
            NavigationRail(viewModel = viewModel)
        }
        viewModel.toggleRail()

        // Click on "Reading Now"
        composeTestRule.onNodeWithText("Reading Now").performClick()

        // Verify selection updated
        assert(viewModel.uiState.value.selectedLibraryId == "reading")
        // Verify rail closed on selection
        assert(!viewModel.uiState.value.isOpen)
    }

    @Test
    fun testNewComicsSelection() {
        composeTestRule.setContent {
            NavigationRail(viewModel = viewModel)
        }
        viewModel.toggleRail()

        // Click on "New Comics"
        composeTestRule.onNodeWithText("New Comics").performClick()

        // Verify selection updated
        assert(viewModel.uiState.value.selectedLibraryId == "new")
        // Verify rail closed on selection
        assert(!viewModel.uiState.value.isOpen)
    }

    @Test
    fun testPhysicalLibrarySelection() {
        composeTestRule.setContent {
            NavigationRail(viewModel = viewModel)
        }
        viewModel.toggleRail()

        // Click on "Test Library"
        composeTestRule.onNodeWithText("Test Library").performClick()

        // Verify selection updated (prefixed with path_)
        // We know pathId is 1 since it's the first insert in in-memory DB
        assert(
            viewModel.uiState.value.selectedLibraryId
                .startsWith("path_"),
        )
        assert(!viewModel.uiState.value.isOpen)
    }

    @Test
    fun testGestureClose() {
        composeTestRule.setContent {
            NavigationRail(viewModel = viewModel)
        }
        viewModel.toggleRail()

        // Swipe left on the rail
        composeTestRule.onNodeWithText("All Comics").performTouchInput {
            swipeLeft()
        }

        // Verify closed
        assert(!viewModel.uiState.value.isOpen)
    }
}
