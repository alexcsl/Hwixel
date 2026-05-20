package edu.bluejack252.hwixel.ui.project.taskedit

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import edu.bluejack252.hwixel.R
import edu.bluejack252.hwixel.data.ServiceLocator
import edu.bluejack252.hwixel.data.model.Subtask
import edu.bluejack252.hwixel.data.model.Task
import edu.bluejack252.hwixel.databinding.FragmentCreateEditTaskBinding
import edu.bluejack252.hwixel.databinding.ItemSubtaskRowBinding
import edu.bluejack252.hwixel.util.constants.Constants
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar

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
    private var availableMembers: List<MemberOption> = emptyList()
    private val subtaskViews = mutableListOf<ItemSubtaskRowBinding>()

    private val viewModel: CreateEditTaskViewModel by viewModels {
        CreateEditTaskViewModelFactory(
            projectId = args.projectId,
            taskId = args.taskId,
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
        binding.deadlineButton.setOnLongClickListener {
            showTimeInputDialog()
            true
        }
        binding.assigneesButton.setOnClickListener { showAssigneeDialog() }
        binding.addSubtaskButton.setOnClickListener { addSubtaskRow() }
        binding.saveTaskButton.setOnClickListener { save() }

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is CreateEditTaskUiState.Loaded -> {
                    availableMembers = state.projectMembers
                    if (state.task != null && subtaskViews.isEmpty()) {
                        prefillTask(state.task)
                    }
                }
                is CreateEditTaskUiState.Success -> {
                    Snackbar.make(binding.root, R.string.task_saved, Snackbar.LENGTH_SHORT).show()
                    findNavController().navigateUp()
                }
                is CreateEditTaskUiState.Error -> {
                    Snackbar.make(binding.root, state.message, Snackbar.LENGTH_SHORT).show()
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
            val selectedNames = selectedAssigneeIds.map { selectedId ->
                availableMembers.firstOrNull { it.userId == selectedId }?.name
                    ?: getString(R.string.unknown_member)
            }
            binding.assigneesButton.text = getString(
                R.string.selected_members_names_format, selectedNames.joinToString(", ")
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
            actorId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        )
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
