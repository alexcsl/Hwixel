package edu.bluejack252.hwixel.ui.project.tasks

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import edu.bluejack252.hwixel.databinding.BottomSheetFilterBinding
import edu.bluejack252.hwixel.util.constants.Constants

class FilterBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetFilterBinding? = null
    private val binding get() = _binding!!

    var onFilterApplied: ((TaskFilter) -> Unit)? = null
    var onFilterReset: (() -> Unit)? = null

    private var memberNames: Map<String, String> = emptyMap()
    private var currentFilter: TaskFilter = TaskFilter()

    fun setMembers(names: Map<String, String>) {
        memberNames = names
    }

    fun setCurrentFilter(filter: TaskFilter) {
        currentFilter = filter
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetFilterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        memberNames.forEach { (userId, name) ->
            val chip = Chip(requireContext()).apply {
                text = name
                tag = userId
                isCheckable = true
                isChecked = userId in currentFilter.assigneeIds
            }
            binding.assigneeChipGroup.addView(chip)
        }

        when (currentFilter.priority) {
            Constants.PRIORITY_LOW -> binding.priorityToggleGroup.check(binding.filterPriorityLowButton.id)
            Constants.PRIORITY_MEDIUM -> binding.priorityToggleGroup.check(binding.filterPriorityMediumButton.id)
            Constants.PRIORITY_HIGH -> binding.priorityToggleGroup.check(binding.filterPriorityHighButton.id)
            else -> binding.priorityToggleGroup.check(binding.filterPriorityAllButton.id)
        }

        binding.applyFilterButton.setOnClickListener {
            val selectedAssignees = mutableSetOf<String>()
            for (i in 0 until binding.assigneeChipGroup.childCount) {
                val chip = binding.assigneeChipGroup.getChildAt(i) as? Chip
                if (chip?.isChecked == true) {
                    selectedAssignees.add(chip.tag as String)
                }
            }
            val priority = when (binding.priorityToggleGroup.checkedButtonId) {
                binding.filterPriorityLowButton.id -> Constants.PRIORITY_LOW
                binding.filterPriorityMediumButton.id -> Constants.PRIORITY_MEDIUM
                binding.filterPriorityHighButton.id -> Constants.PRIORITY_HIGH
                else -> null
            }
            onFilterApplied?.invoke(TaskFilter(assigneeIds = selectedAssignees, priority = priority))
            dismiss()
        }

        binding.resetFilterButton.setOnClickListener {
            onFilterReset?.invoke()
            dismiss()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "FilterBottomSheet"
    }
}
