package edu.bluejack252.hwixel.ui.project.taskedit

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import edu.bluejack252.hwixel.R
import edu.bluejack252.hwixel.data.ServiceLocator
import edu.bluejack252.hwixel.data.model.Task
import edu.bluejack252.hwixel.databinding.FragmentCreateEditTaskBinding
import edu.bluejack252.hwixel.databinding.ItemSubtaskRowBinding
import edu.bluejack252.hwixel.util.constants.Constants
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CreateEditTaskFragment : Fragment() {

    private var _binding: FragmentCreateEditTaskBinding? = null
    private val binding get() = _binding!!

    private val args: CreateEditTaskFragmentArgs by navArgs()
    private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    private var selectedDeadline: Long = 0L
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
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        binding.deadlineButton.setOnClickListener { showDatePicker() }
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
        binding.toolbar.title = getString(
            if (isEdit) R.string.edit_task_title else R.string.create_task_title
        )
    }

    private fun prefillTask(task: Task) {
        binding.titleEditText.setText(task.title)
        binding.descriptionEditText.setText(task.description)

        if (task.deadline > 0L) {
            selectedDeadline = task.deadline
            binding.deadlineButton.text = dateFormat.format(Date(task.deadline))
        }

        selectedAssigneeIds = task.assignees.toMutableList()
        updateAssigneesLabel()

        val priorityBtnId = when (task.priority) {
            Constants.PRIORITY_LOW -> binding.btnPriorityLow.id
            Constants.PRIORITY_HIGH -> binding.btnPriorityHigh.id
            else -> binding.btnPriorityMedium.id
        }
        binding.priorityToggleGroup.check(priorityBtnId)

        task.subtasks.values.forEach { subtask ->
            val rowBinding = ItemSubtaskRowBinding.inflate(layoutInflater, binding.subtasksContainer, true)
            rowBinding.subtaskEditText.setText(subtask.title)
            rowBinding.removeSubtaskButton.setOnClickListener {
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
            selectedDeadline = selection
            binding.deadlineButton.text = dateFormat.format(Date(selection))
        }
        picker.show(parentFragmentManager, "deadline_picker")
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
            binding.assigneesTextView.visibility = View.GONE
        } else {
            binding.assigneesTextView.visibility = View.VISIBLE
            binding.assigneesTextView.text = getString(
                R.string.selected_members_format, selectedAssigneeIds.size
            )
        }
    }

    private fun addSubtaskRow() {
        val rowBinding = ItemSubtaskRowBinding.inflate(layoutInflater, binding.subtasksContainer, true)
        rowBinding.removeSubtaskButton.setOnClickListener {
            binding.subtasksContainer.removeView(rowBinding.root)
            subtaskViews.remove(rowBinding)
        }
        subtaskViews.add(rowBinding)
    }

    private fun save() {
        val title = binding.titleEditText.text?.toString().orEmpty().trim()
        if (title.isBlank()) {
            binding.titleInputLayout.error = getString(R.string.error_empty_task_title)
            return
        }
        binding.titleInputLayout.error = null

        val description = binding.descriptionEditText.text?.toString().orEmpty().trim()
        val priority = when (binding.priorityToggleGroup.checkedButtonId) {
            binding.btnPriorityLow.id -> Constants.PRIORITY_LOW
            binding.btnPriorityHigh.id -> Constants.PRIORITY_HIGH
            else -> Constants.PRIORITY_MEDIUM
        }
        val subtaskTitles = subtaskViews.mapNotNull {
            it.subtaskEditText.text?.toString()?.trim()?.takeIf { t -> t.isNotBlank() }
        }

        viewModel.saveTask(
            title = title,
            description = description,
            deadline = selectedDeadline,
            assigneeIds = selectedAssigneeIds.toList(),
            priority = priority,
            subtaskTitles = subtaskTitles
        )
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
