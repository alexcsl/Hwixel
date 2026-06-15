package edu.bluejack25_2.hwixel.ui.dashboard

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import edu.bluejack25_2.hwixel.R
import edu.bluejack25_2.hwixel.databinding.FragmentCreateProjectSheetBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CreateProjectBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentCreateProjectSheetBinding? = null
    private val binding get() = _binding!!

    private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    private var selectedDueDate: Long = 0L

    var onProjectCreate: ((name: String, description: String, goals: String, dueDate: Long) -> Unit)? = null

    override fun getTheme(): Int = R.style.Theme_HWiXeL_BottomSheet

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreateProjectSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.projectDeadlineButton.setOnClickListener { showDatePicker() }
        binding.btnCancel.setOnClickListener { dismiss() }
        binding.btnCreate.setOnClickListener { attemptCreate() }
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                cal.set(year, month, day, 23, 59, 59)
                selectedDueDate = cal.timeInMillis
                binding.projectDeadlineButton.text = dateFormat.format(cal.time)
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun attemptCreate() {
        val name = binding.projectNameInput.editText?.text?.toString().orEmpty().trim()
        if (name.isBlank()) {
            binding.projectNameInput.error = getString(R.string.error_empty_project_name)
            return
        }
        binding.projectNameInput.error = null
        val description = binding.projectDescriptionInput.editText?.text?.toString().orEmpty().trim()
        val goals = binding.projectGoalsInput.editText?.text?.toString().orEmpty().trim()
        onProjectCreate?.invoke(name, description, goals, selectedDueDate)
        dismiss()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "CreateProjectBottomSheet"
        fun newInstance() = CreateProjectBottomSheetFragment()
    }
}
