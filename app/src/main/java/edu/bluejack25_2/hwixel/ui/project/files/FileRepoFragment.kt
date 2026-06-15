package edu.bluejack25_2.hwixel.ui.project.files

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.ChipGroup
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import edu.bluejack25_2.hwixel.R
import edu.bluejack25_2.hwixel.data.ServiceLocator
import edu.bluejack25_2.hwixel.data.model.FileLink

class FileRepoFragment : Fragment() {

    private val args: FileRepoFragmentArgs by navArgs()

    private val viewModel: FileRepoViewModel by viewModels {
        FileRepoViewModelFactory(
            repository = ServiceLocator.getFileRepository(),
            projectId = args.projectId
        )
    }

    private lateinit var filesAdapter: FileCardAdapter
    private lateinit var historyAdapter: FileCardAdapter
    private var visibleFiles: List<FileLink> = emptyList()
    private var pendingDeleteFile: FileLink? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_file_repo, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAdapters(view)
        setupClickListeners(view)
        observeViewModel(view)
    }

    private fun setupAdapters(view: View) {
        filesAdapter = FileCardAdapter(
            onOpen = { openLink(it) },
            onDelete = { showDeleteUndo(view, it) },
            showDelete = true
        )
        view.findViewById<RecyclerView>(R.id.filesRecyclerView).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = filesAdapter
            isNestedScrollingEnabled = false
        }

        historyAdapter = FileCardAdapter(
            onOpen = { openLink(it) },
            onDelete = {},
            showDelete = false
        )
        view.findViewById<RecyclerView>(R.id.versionHistoryRecyclerView).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = historyAdapter
            isNestedScrollingEnabled = false
        }

        attachSwipeToDelete(view.findViewById(R.id.filesRecyclerView))
    }

    private fun attachSwipeToDelete(recyclerView: RecyclerView) {
        val deleteColor = ContextCompat.getColor(requireContext(), R.color.brand_rose)
        val paint = Paint().apply { color = deleteColor }

        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return
                val file = filesAdapter.currentList[position]
                showDeleteUndo(recyclerView, file)
            }

            override fun onChildDraw(
                c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder,
                dX: Float, dY: Float, actionState: Int, isActive: Boolean
            ) {
                val itemView = vh.itemView
                c.drawRect(
                    itemView.right + dX, itemView.top.toFloat(),
                    itemView.right.toFloat(), itemView.bottom.toFloat(), paint
                )
                super.onChildDraw(c, rv, vh, dX, dY, actionState, isActive)
            }
        })
        touchHelper.attachToRecyclerView(recyclerView)
    }

    private fun setupClickListeners(view: View) {
        view.findViewById<View>(R.id.backButton).setOnClickListener {
            findNavController().navigateUp()
        }
        view.findViewById<View>(R.id.addFileFab).setOnClickListener {
            val sheet = AddLinkBottomSheet()
            sheet.onAdd = { label, url, type, notes ->
                viewModel.addFile(label, url, type, notes)
            }
            sheet.show(childFragmentManager, "add_link")
        }
        view.findViewById<ChipGroup>(R.id.fileTypeFilterGroup).setOnCheckedStateChangeListener { _, checkedIds ->
            viewModel.setTypeFilter(
                when (checkedIds.firstOrNull()) {
                    R.id.filterDriveFilesChip -> "drive"
                    R.id.filterGithubFilesChip -> "github"
                    R.id.filterOtherFilesChip -> "other"
                    else -> null
                }
            )
        }
    }

    private fun observeViewModel(view: View) {
        val loading = view.findViewById<LinearProgressIndicator>(R.id.fileLoadingIndicator)
        val emptyFiles = view.findViewById<TextView>(R.id.emptyFilesTextView)
        val emptyHistory = view.findViewById<TextView>(R.id.emptyVersionHistoryTextView)
        val historySection = view.findViewById<LinearLayout>(R.id.versionHistorySection)

        viewModel.filteredFiles.observe(viewLifecycleOwner) { files ->
            visibleFiles = files
            submitVisibleFiles()
            emptyFiles.isVisible = visibleFiles.isEmpty() && pendingDeleteFile == null
        }

        viewModel.versionHistory.observe(viewLifecycleOwner) { history ->
            historyAdapter.submitList(history)
            historySection.isVisible = history.isNotEmpty()
            emptyHistory.isVisible = history.isEmpty()
        }

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is FileRepoUiState.Loading -> loading.isVisible = true
                is FileRepoUiState.AddSuccess -> {
                    loading.isVisible = false
                    Snackbar.make(view, getString(R.string.file_add_success), Snackbar.LENGTH_SHORT).show()
                    viewModel.resetState()
                }
                is FileRepoUiState.DeleteSuccess -> {
                    loading.isVisible = false
                    Snackbar.make(view, getString(R.string.file_delete_success), Snackbar.LENGTH_SHORT).show()
                    viewModel.resetState()
                }
                is FileRepoUiState.Error -> {
                    loading.isVisible = false
                    val msg = when (state.message) {
                        "empty_fields" -> getString(R.string.file_error_empty_fields)
                        "invalid_url" -> getString(R.string.file_error_invalid_url)
                        else -> state.message.ifBlank { getString(R.string.error_generic) }
                    }
                    Snackbar.make(view, msg, Snackbar.LENGTH_SHORT).show()
                    viewModel.resetState()
                }
                else -> loading.isVisible = false
            }
        }
    }

    private fun openLink(file: FileLink) {
        val uri = Uri.parse(file.url)
        try {
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
                .launchUrl(requireContext(), uri)
        } catch (e: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    }

    private fun showDeleteUndo(anchor: View, file: FileLink) {
        pendingDeleteFile = file
        submitVisibleFiles()
        Snackbar.make(anchor, getString(R.string.file_delete_pending), Snackbar.LENGTH_LONG)
            .setAction(R.string.file_delete_undo) {
                pendingDeleteFile = null
                submitVisibleFiles()
            }
            .addCallback(object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                    val pending = pendingDeleteFile ?: return
                    pendingDeleteFile = null
                    submitVisibleFiles()
                    if (event != DISMISS_EVENT_ACTION) {
                        viewModel.deleteFile(pending.id)
                    }
                }
            })
            .show()
    }

    private fun submitVisibleFiles() {
        val pendingId = pendingDeleteFile?.id
        filesAdapter.submitList(
            visibleFiles.filterNot { file -> file.id == pendingId }
        )
    }
}
