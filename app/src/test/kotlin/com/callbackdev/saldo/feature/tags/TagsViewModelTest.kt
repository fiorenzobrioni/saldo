package com.callbackdev.saldo.feature.tags

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.callbackdev.saldo.core.domain.model.Tag
import com.callbackdev.saldo.core.domain.repository.TagRepository
import com.callbackdev.saldo.testing.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherExtension::class)
class TagsViewModelTest {

    private val tagRepository = mockk<TagRepository>(relaxUnitFun = true)

    private fun tag(id: Long, name: String) = Tag(name = name, id = id)

    private fun viewModel(
        tags: List<Tag>,
        usage: Map<Long, Int> = emptyMap(),
    ): TagsViewModel {
        every { tagRepository.observeTags() } returns flowOf(tags)
        every { tagRepository.observeTagUsage() } returns flowOf(usage)
        coEvery { tagRepository.upsert(any()) } returns 0L
        return TagsViewModel(tagRepository)
    }

    private suspend fun ReceiveTurbine<TagsUiState>.awaitLoaded(): TagsUiState {
        var state = awaitItem()
        while (state.isLoading) state = awaitItem()
        return state
    }

    private suspend fun ReceiveTurbine<TagsUiState>.awaitDialog(): TagsDialog {
        var dialog = awaitItem().dialog
        while (dialog == null) dialog = awaitItem().dialog
        return dialog
    }

    @Test
    fun `rows join the usage counts and sort by use, names breaking ties`() = runTest {
        val viewModel = viewModel(
            tags = listOf(tag(1L, "viaggi"), tag(2L, "casa"), tag(3L, "Spesa"), tag(4L, "auto")),
            usage = mapOf(1L to 2, 2L to 5, 4L to 2),
        )

        viewModel.uiState.test {
            val state = awaitLoaded()
            // casa(5) first, then the tie at 2 alphabetically, then the unused one.
            assertEquals(listOf(2L, 4L, 1L, 3L), state.tags.map { it.tag.id })
            assertEquals(0, state.tags.last().movementCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the alphabetical sort ignores the casing`() = runTest {
        val viewModel = viewModel(tags = listOf(tag(1L, "Zaino"), tag(2L, "auto"), tag(3L, "Casa")))

        viewModel.uiState.test {
            awaitLoaded()
            viewModel.onSortSelected(TagSort.NAME)
            var state = awaitItem()
            while (state.sort != TagSort.NAME) state = awaitItem()
            assertEquals(listOf("auto", "Casa", "Zaino"), state.tags.map { it.tag.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the search matches case-insensitively and can empty the list`() = runTest {
        val viewModel = viewModel(tags = listOf(tag(1L, "Spesa"), tag(2L, "Viaggi")))

        viewModel.uiState.test {
            awaitLoaded()
            viewModel.onSearchQueryChange("spe")
            var state = awaitItem()
            while (state.tags.size != 1) state = awaitItem()
            assertEquals("Spesa", state.tags.single().tag.name)

            viewModel.onSearchQueryChange("xyz")
            state = awaitItem()
            while (state.tags.isNotEmpty()) state = awaitItem()
            assertTrue(state.hasNoResults)
            assertEquals(2, state.totalCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the search field appears only once the list is long enough`() = runTest {
        val few = (1L..7L).map { tag(it, "tag-$it") }
        viewModel(tags = few).uiState.test {
            assertFalse(awaitLoaded().isSearchAvailable)
            cancelAndIgnoreRemainingEvents()
        }

        val many = (1L..8L).map { tag(it, "tag-$it") }
        viewModel(tags = many).uiState.test {
            assertTrue(awaitLoaded().isSearchAvailable)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `renaming to a free name saves the normalized spelling`() = runTest {
        val viewModel = viewModel(tags = listOf(tag(5L, "work"), tag(6L, "trips")))

        viewModel.uiState.test {
            val state = awaitLoaded()
            viewModel.onTagClick(state.tags.first { it.tag.id == 5L })
            viewModel.requestRename()
            awaitDialog()
            viewModel.onRenameInputChange("  Job   hunt ")
            viewModel.confirmRename()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify { tagRepository.upsert(Tag(name = "Job hunt", id = 5L)) }
        coVerify(exactly = 0) { tagRepository.merge(any(), any()) }
    }

    @Test
    fun `renaming onto an existing name proposes the merge and merges on confirm`() = runTest {
        val viewModel = viewModel(tags = listOf(tag(5L, "work"), tag(6L, "trips")))

        viewModel.uiState.test {
            val state = awaitLoaded()
            viewModel.onTagClick(state.tags.first { it.tag.id == 5L })
            viewModel.requestRename()
            awaitDialog()
            viewModel.onRenameInputChange(" TRIPS ")
            var renamed = awaitItem()
            while ((renamed.dialog as? TagsDialog.Rename)?.collision == null) renamed = awaitItem()
            assertEquals(6L, (renamed.dialog as TagsDialog.Rename).collision?.id)
            viewModel.confirmRename()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify { tagRepository.merge(targetId = 6L, sourceIds = setOf(5L)) }
        coVerify(exactly = 0) { tagRepository.upsert(any()) }
        viewModel.events.test {
            assertEquals(TagsEvent.Merged("trips"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a pre-existing case-variant duplicate proposes the merge on open`() = runTest {
        // "Spesa" and "spesa" can coexist in the database (the unique index
        // compares bytes): opening the rename dialog must surface the merge
        // right away, confirmable without retyping the name.
        val viewModel = viewModel(tags = listOf(tag(1L, "Spesa"), tag(2L, "spesa")))

        viewModel.uiState.test {
            val state = awaitLoaded()
            viewModel.onTagClick(state.tags.first { it.tag.id == 1L })
            viewModel.requestRename()
            val dialog = awaitDialog() as TagsDialog.Rename
            assertEquals(2L, dialog.collision?.id)
            assertTrue(dialog.canConfirm)
            viewModel.confirmRename()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify { tagRepository.merge(targetId = 2L, sourceIds = setOf(1L)) }
        coVerify(exactly = 0) { tagRepository.upsert(any()) }
    }

    @Test
    fun `renaming a tag to its own casing variant is not a collision`() = runTest {
        val viewModel = viewModel(tags = listOf(tag(1L, "spesa"), tag(2L, "casa")))

        viewModel.uiState.test {
            val state = awaitLoaded()
            viewModel.onTagClick(state.tags.first { it.tag.id == 1L })
            viewModel.requestRename()
            awaitDialog()
            viewModel.onRenameInputChange("Spesa")
            var renamed = awaitItem()
            while (renamed.dialog !is TagsDialog.Rename) renamed = awaitItem()
            val dialog = renamed.dialog as TagsDialog.Rename
            assertNull(dialog.collision)
            assertTrue(dialog.canConfirm)
            viewModel.confirmRename()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify { tagRepository.upsert(Tag(name = "Spesa", id = 1L)) }
    }

    @Test
    fun `the merge picker lists every other tag by use and merges the selection`() = runTest {
        val viewModel = viewModel(
            tags = listOf(tag(1L, "spesa"), tag(2L, "Spesa"), tag(3L, "groceries")),
            usage = mapOf(1L to 10, 2L to 1, 3L to 4),
        )

        viewModel.uiState.test {
            val state = awaitLoaded()
            viewModel.onTagClick(state.tags.first { it.tag.id == 1L })
            viewModel.requestMerge()
            val dialog = awaitDialog() as TagsDialog.Merge
            assertEquals(1L, dialog.target.tag.id)
            assertEquals(listOf(3L, 2L), dialog.candidates.map { it.tag.id })
            viewModel.onMergeSourceToggled(2L)
            viewModel.onMergeSourceToggled(3L)
            viewModel.confirmMerge()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify { tagRepository.merge(targetId = 1L, sourceIds = setOf(2L, 3L)) }
        viewModel.events.test {
            assertEquals(TagsEvent.Merged("spesa"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleting asks with the row's movement count and deletes on confirm`() = runTest {
        val viewModel = viewModel(tags = listOf(tag(5L, "work")), usage = mapOf(5L to 3))

        viewModel.uiState.test {
            val state = awaitLoaded()
            viewModel.onTagClick(state.tags.single())
            viewModel.requestDelete()
            val dialog = awaitDialog() as TagsDialog.ConfirmDelete
            assertEquals(3, dialog.movementCount)
            viewModel.confirmDelete()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify { tagRepository.delete(tag(5L, "work")) }
        viewModel.events.test {
            assertEquals(TagsEvent.Deleted("work"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failed write surfaces the event`() = runTest {
        val viewModel = viewModel(tags = listOf(tag(5L, "work")))
        coEvery { tagRepository.delete(any()) } throws IllegalStateException("disk full")

        viewModel.uiState.test {
            val state = awaitLoaded()
            viewModel.onTagClick(state.tags.single())
            viewModel.requestDelete()
            awaitDialog()
            viewModel.confirmDelete()
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.events.test {
            assertEquals(TagsEvent.WriteFailed, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
