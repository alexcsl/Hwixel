package edu.bluejack25_2.hwixel.ui.project.taskedit

import android.os.Bundle
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import edu.bluejack25_2.hwixel.R
import edu.bluejack25_2.hwixel.data.ServiceLocator
import edu.bluejack25_2.hwixel.data.model.Attachment
import edu.bluejack25_2.hwixel.data.model.Subtask
import edu.bluejack25_2.hwixel.data.model.Task
import edu.bluejack25_2.hwixel.databinding.FragmentCreateEditTaskBinding
import edu.bluejack25_2.hwixel.databinding.ItemSubtaskRowBinding
import edu.bluejack25_2.hwixel.util.constants.Constants
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar
import java.util.UUID

class CreateEditTaskFragment : Fragment() {

    private var _binding: FragmentCreateEditTaskBinding? = null
    private val binding get() = _binding!!

    private val args: CreateEditTaskFragmentArgs by navArgs()
    private val dateFormat = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
    private val dateOnlyFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    private var selectedDeadline: Long = 0L
    private var selectedHour: Int = 23
    private var selectedMinute: Int = 59
    private var selectedAssigneeIds: MutableList<String> = mutableListOf()
    private val pendingAttachments = mutableListOf<Attachment>()
    private var availableMembers: List<MemberOption> = emptyList()
    private val subtaskViews = mutableListOf<ItemSubtaskRowBinding>()
    private val attachmentAdapter = EditableAttachmentAdapter { attachment ->
        pendingAttachments.removeAll { it.id == attachment.id && it.url == attachment.url }
        renderAttachments()
        // Best-effort cleanup if this was an uploaded blob; ignore failures.
        val uploadSource = ServiceLocator.getAttachmentUploadSource()
        if (uploadSource.isOwnedUrl(attachment.url)) {
            viewLifecycleOwner.lifecycleScope.launch {
                uploadSource.delete(attachment.url)
            }
        }
    }

    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) handlePickedFile(uri)
    }

    private val viewModel: CreateEditTaskViewModel by viewModels {
        CreateEditTaskViewModelFactory(
            projectId = args.projectId,
            taskId = args.taskId,
            currentUserId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty(),
            taskRepository = ServiceLocator.getTaskRepository(requireContext()),
            projectRepository = ServiceLocator.getProjectRepository(requireContext()),
            userRepository = ServiceLocator.getUserRepository(requireContext())
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreateEditTaskBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.backButton.setOnClickListener { findNavController().navigateUp() }

        binding.deadlineButton.setOnClickListener { showDatePicker() }
        binding.deadlineTimeButton.setOnClickListener { showTimeInputDialog() }
        binding.assigneesButton.setOnClickListener { showAssigneeDialog() }
        binding.addSubtaskButton.setOnClickListener { addSubtaskRow() }
        binding.addAttachmentButton.setOnClickListener { showAddAttachmentDialog() }
        binding.uploadAttachmentButton.setOnClickListener { pickFileLauncher.launch("*/*") }
        binding.saveTaskButton.isEnabled = false
        binding.saveTaskButton.setOnClickListener { save() }
        binding.editAttachmentsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = attachmentAdapter
        }
        renderAttachments()

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is CreateEditTaskUiState.Loaded -> {
                    availableMembers = state.projectMembers
                    binding.saveTaskButton.isEnabled = state.canSaveTask
                    if (state.task != null && subtaskViews.isEmpty()) {
                        prefillTask(state.task)
                    }
                }
                is CreateEditTaskUiState.Success -> {
                    Snackbar.make(binding.root, R.string.task_saved, Snackbar.LENGTH_SHORT).show()
                    findNavController().navigateUp()
                }
                is CreateEditTaskUiState.Error -> {
                    val msg = when (state.message) {
                        "no_permission" -> getString(R.string.error_no_permission)
                        "empty_title" -> getString(R.string.error_empty_task_title)
                        else -> state.message.ifBlank { getString(R.string.error_generic) }
                    }
                    Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
                }
                else -> Unit
            }
        }

        val isEdit = args.taskId.isNotBlank()
        binding.formTitleTextView.text = getString(
            if (isEdit) R.string.edit_task_title else R.string.create_task_title
        )
    }

    private fun prefillTask(task: Task) {
        binding.taskTitleInputLayout.editText?.setText(task.title)
        binding.taskDescriptionInputLayout.editText?.setText(task.description)

        if (task.deadline > 0L) {
            selectedDeadline = task.deadline
            val calendar = Calendar.getInstance().apply { timeInMillis = task.deadline }
            selectedHour = calendar.get(Calendar.HOUR_OF_DAY)
            selectedMinute = calendar.get(Calendar.MINUTE)
            binding.deadlineButton.text = dateFormat.format(Date(task.deadline))
        }

        selectedAssigneeIds = task.assignees.toMutableList()
        updateAssigneesLabel()
        pendingAttachments.clear()
        pendingAttachments.addAll(task.attachments)
        renderAttachments()

        val priorityBtnId = when (task.priority) {
            Constants.PRIORITY_LOW -> binding.lowPriorityButton.id
            Constants.PRIORITY_HIGH -> binding.highPriorityButton.id
            else -> binding.mediumPriorityButton.id
        }
        binding.priorityToggleGroup.check(priorityBtnId)

        task.subtasks.values.forEach { subtask ->
            val rowBinding = ItemSubtaskRowBinding.inflate(layoutInflater, binding.subtasksContainer, true)
            rowBinding.root.tag = subtask.id
            rowBinding.subtaskTitleEditText.setText(subtask.title)
            rowBinding.deleteSubtaskButton.setOnClickListener {
                binding.subtasksContainer.removeView(rowBinding.root)
                subtaskViews.remove(rowBinding)
            }
            subtaskViews.add(rowBinding)
        }
    }

    private fun showDatePicker() {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(R.string.task_deadline_label))
            .setSelection(if (selectedDeadline > 0L) selectedDeadline else MaterialDatePicker.todayInUtcMilliseconds())
            .build()
        picker.addOnPositiveButtonClickListener { selection ->
            selectedDeadline = buildDeadlineMillis(selection, selectedHour, selectedMinute)
            updateDeadlineButtons()
        }
        picker.show(parentFragmentManager, "deadline_picker")
    }

    private fun showTimeInputDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_deadline_time, null)
        val hourEditText = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.deadlineHourEditText)
        val minuteEditText = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.deadlineMinuteEditText)
        hourEditText.setText(selectedHour.toString())
        minuteEditText.setText(selectedMinute.toString())

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.task_time_dialog_title)
            .setView(dialogView)
            .setPositiveButton(R.string.btn_ok) { _, _ ->
                val hour = hourEditText.text?.toString()?.toIntOrNull()
                val minute = minuteEditText.text?.toString()?.toIntOrNull()
                if (hour == null || minute == null || hour !in 0..23 || minute !in 0..59) {
                    Snackbar.make(binding.root, R.string.error_invalid_deadline_time, Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                selectedHour = hour
                selectedMinute = minute
                selectedDeadline = if (selectedDeadline > 0L) {
                    buildDeadlineMillis(selectedDeadline, selectedHour, selectedMinute)
                } else {
                    selectedDeadline
                }
                updateDeadlineButtons()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun buildDeadlineMillis(dateMillis: Long, hour: Int, minute: Int): Long {
        return Calendar.getInstance().apply {
            timeInMillis = dateMillis
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun updateDeadlineButtons() {
        if (selectedDeadline > 0L) {
            binding.deadlineButton.text = dateFormat.format(Date(selectedDeadline))
        }
        binding.deadlineTimeButton.text = getString(
            R.string.task_deadline_time_format,
            selectedHour,
            selectedMinute
        )
    }

    private fun showAssigneeDialog() {
        if (availableMembers.isEmpty()) {
            Snackbar.make(binding.root, R.string.no_members_available, Snackbar.LENGTH_SHORT).show()
            return
        }
        val names = availableMembers.map { it.name }.toTypedArray()
        val checked = availableMembers.map { it.userId in selectedAssigneeIds }.toBooleanArray()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.select_members_title)
            .setMultiChoiceItems(names, checked) { _, which, isChecked ->
                val userId = availableMembers[which].userId
                if (isChecked) selectedAssigneeIds.add(userId)
                else selectedAssigneeIds.remove(userId)
            }
            .setPositiveButton(R.string.btn_ok) { _, _ -> updateAssigneesLabel() }
            .show()
    }

    private fun updateAssigneesLabel() {
        if (selectedAssigneeIds.isEmpty()) {
            binding.assigneesButton.text = getString(R.string.task_assign_members)
        } else {
            binding.assigneesButton.text = getString(
                R.string.selected_members_format, selectedAssigneeIds.size
            )
        }
    }

    private fun addSubtaskRow() {
        val rowBinding = ItemSubtaskRowBinding.inflate(layoutInflater, binding.subtasksContainer, true)
        rowBinding.root.tag = ""
        rowBinding.deleteSubtaskButton.setOnClickListener {
            binding.subtasksContainer.removeView(rowBinding.root)
            subtaskViews.remove(rowBinding)
        }
        subtaskViews.add(rowBinding)
    }

    private fun showAddAttachmentDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_attachment, null)
        val labelLayout = dialogView.findViewById<TextInputLayout>(R.id.attachmentLabelInputLayout)
        val urlLayout = dialogView.findViewById<TextInputLayout>(R.id.attachmentUrlInputLayout)
        val labelEditText = dialogView.findViewById<TextInputEditText>(R.id.attachmentLabelEditText)
        val urlEditText = dialogView.findViewById<TextInputEditText>(R.id.attachmentUrlEditText)
        val typeGroup = dialogView.findViewById<android.widget.RadioGroup>(R.id.attachmentTypeRadioGroup)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.add_attachment_title)
            .setView(dialogView)
            .setPositiveButton(R.string.btn_ok, null)
            .setNegativeButton(R.string.btn_cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val label = labelEditText.text?.toString().orEmpty().trim()
                val url = urlEditText.text?.toString().orEmpty().trim()
                labelLayout.error = null
                urlLayout.error = null
                if (label.isBlank()) {
                    labelLayout.error = getString(R.string.error_empty_attachment_label)
                    return@setOnClickListener
                }
                if (!isValidHttpUrl(url)) {
                    urlLayout.error = getString(R.string.error_invalid_attachment_url)
                    return@setOnClickListener
                }
                val type = when (typeGroup.checkedRadioButtonId) {
                    R.id.radioAttachmentPdf -> TYPE_PDF
                    R.id.radioAttachmentImage -> TYPE_IMAGE
                    else -> TYPE_LINK
                }
                pendingAttachments.add(
                    Attachment(
                        id = "a-${UUID.randomUUID()}",
                        label = label,
                        url = url,
                        type = type
                    )
                )
                renderAttachments()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun renderAttachments() {
        attachmentAdapter.submitList(pendingAttachments.toList())
        binding.emptyAttachmentsTextView.isVisible = pendingAttachments.isEmpty()
        binding.editAttachmentsRecyclerView.isVisible = pendingAttachments.isNotEmpty()
    }

    private fun handlePickedFile(uri: Uri) {
        val resolver = requireContext().contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val displayName = queryDisplayName(uri) ?: "file"

        val bytes = runCatching {
            resolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()
        if (bytes == null) {
            showSnack(getString(R.string.attachment_read_failed))
            return
        }
        if (bytes.size > MAX_UPLOAD_BYTES) {
            showSnack(getString(R.string.attachment_file_too_large_format, MAX_UPLOAD_BYTES / 1_000_000))
            return
        }

        val type = when {
            mime.startsWith("image/") -> TYPE_IMAGE
            mime == "application/pdf" -> TYPE_PDF
            else -> TYPE_LINK
        }
        binding.uploadAttachmentButton.isEnabled = false
        val progressBar = Snackbar.make(binding.root, getString(R.string.attachment_uploading), Snackbar.LENGTH_INDEFINITE)
        progressBar.show()

        viewLifecycleOwner.lifecycleScope.launch {
            val uploadSource = ServiceLocator.getAttachmentUploadSource()
            val result = uploadSource.upload(
                taskId = args.taskId.ifBlank { "new" },
                originalFileName = displayName,
                bytes = bytes,
                contentType = mime
            )
            progressBar.dismiss()
            binding.uploadAttachmentButton.isEnabled = true
            result
                .onSuccess { publicUrl ->
                    pendingAttachments.add(
                        Attachment(
                            id = "a-${UUID.randomUUID()}",
                            label = displayName,
                            url = publicUrl,
                            type = type
                        )
                    )
                    renderAttachments()
                    showSnack(getString(R.string.attachment_upload_success))
                }
                .onFailure { ex ->
                    val detail = ex.localizedMessage?.takeIf { it.isNotBlank() }
                    val msg = if (detail != null) {
                        getString(R.string.attachment_upload_failed_format, detail)
                    } else {
                        getString(R.string.attachment_upload_failed)
                    }
                    showSnack(msg)
                }
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        val resolver = requireContext().contentResolver
        val cursor = resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return it.getString(idx)
            }
        }
        return null
    }

    private fun showSnack(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun isValidHttpUrl(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull()
        return uri?.scheme in setOf("http", "https") && !uri?.host.isNullOrBlank()
    }

    private fun save() {
        val title = binding.taskTitleInputLayout.editText?.text?.toString().orEmpty().trim()
        if (title.isBlank()) {
            binding.taskTitleInputLayout.error = getString(R.string.error_empty_task_title)
            return
        }
        binding.taskTitleInputLayout.error = null

        val description = binding.taskDescriptionInputLayout.editText?.text?.toString().orEmpty().trim()
        val priority = when (binding.priorityToggleGroup.checkedButtonId) {
            binding.lowPriorityButton.id -> Constants.PRIORITY_LOW
            binding.highPriorityButton.id -> Constants.PRIORITY_HIGH
            else -> Constants.PRIORITY_MEDIUM
        }
        val subtasks = subtaskViews.mapNotNull {
            val title = it.subtaskTitleEditText.text?.toString()?.trim().orEmpty()
            if (title.isBlank()) return@mapNotNull null
            Subtask(
                id = it.root.tag as? String ?: "",
                title = title,
                isDone = false
            )
        }

        viewModel.saveTask(
            title = title,
            description = description,
            deadline = selectedDeadline,
            assigneeIds = selectedAssigneeIds.toList(),
            priority = priority,
            subtasksInput = subtasks,
            attachmentsInput = pendingAttachments.toList(),
            actorId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        )
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val TYPE_PDF = "pdf"
        const val TYPE_IMAGE = "image"
        const val TYPE_LINK = "link"
        const val MAX_UPLOAD_BYTES = 10 * 1_000_000 // 10 MB
    }
}
