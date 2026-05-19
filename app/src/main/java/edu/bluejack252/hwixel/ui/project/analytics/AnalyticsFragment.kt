package edu.bluejack252.hwixel.ui.project.analytics

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.google.android.material.datepicker.MaterialDatePicker
import edu.bluejack252.hwixel.R
import edu.bluejack252.hwixel.data.ServiceLocator
import edu.bluejack252.hwixel.databinding.FragmentAnalyticsBinding
import edu.bluejack252.hwixel.ui.project.hub.ProjectPagerAdapter
import edu.bluejack252.hwixel.ui.project.hub.fillViewPagerPage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AnalyticsFragment : Fragment() {

    private var _binding: FragmentAnalyticsBinding? = null
    private val binding get() = _binding!!
    private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    private var isUpdatingChartSelection = false

    private val memberAdapter = AnalyticsMemberAdapter { member ->
        viewModel.selectMember(member.userId)
    }

    private val viewModel: AnalyticsViewModel by viewModels {
        AnalyticsViewModelFactory(
            projectId = requireArguments().getString(ProjectPagerAdapter.ARG_PROJECT_ID).orEmpty(),
            projectRepository = ServiceLocator.getProjectRepository(requireContext()),
            taskRepository = ServiceLocator.getTaskRepository(requireContext()),
            userRepository = ServiceLocator.getUserRepository(requireContext()),
            teamHealthRepository = ServiceLocator.getTeamHealthRepository()
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAnalyticsBinding.inflate(inflater, container, false)
        return binding.root.fillViewPagerPage()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.memberContributionRecyclerView.adapter = memberAdapter
        binding.applyFilterButton.setOnClickListener { showDateRangePicker() }
        binding.analyzeButton.setOnClickListener { viewModel.refreshTeamHealth() }
        configurePieChart()
        viewModel.uiState.observe(viewLifecycleOwner, ::render)
    }

    private fun configurePieChart() {
        binding.pieChart.description.isEnabled = false
        binding.pieChart.setUsePercentValues(false)
        binding.pieChart.setDrawEntryLabels(false)
        binding.pieChart.legend.isWordWrapEnabled = true
        binding.pieChart.setNoDataText(getString(R.string.analytics_no_completed_tasks))
        binding.pieChart.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
            override fun onValueSelected(e: com.github.mikephil.charting.data.Entry?, h: Highlight?) {
                if (isUpdatingChartSelection) return
                val member = e?.data as? MemberAnalyticsUi ?: return
                viewModel.selectMember(member.userId)
            }

            override fun onNothingSelected() {
                if (isUpdatingChartSelection) return
                viewModel.selectMember(null)
            }
        })
    }

    private fun render(state: AnalyticsUiState) {
        memberAdapter.selectedMemberId = state.selectedMemberId
        memberAdapter.submitList(state.members)
        renderPieChart(state.members, state.selectedMemberId)
        renderDateRange(state)
        renderTeamHealth(state)
    }

    private fun renderPieChart(members: List<MemberAnalyticsUi>, selectedMemberId: String?) {
        val completedMembers = members.filter { it.completedCount > 0 }
        if (completedMembers.isEmpty()) {
            binding.pieChart.clear()
            return
        }
        val entries = completedMembers.map { member ->
            PieEntry(member.completedCount.toFloat(), member.name, member)
        }
        val dataSet = PieDataSet(entries, getString(R.string.analytics_completed_chart_label))
        dataSet.colors = listOf(
            ContextCompat.getColor(requireContext(), R.color.analytics_chart_a),
            ContextCompat.getColor(requireContext(), R.color.analytics_chart_b),
            ContextCompat.getColor(requireContext(), R.color.analytics_chart_c),
            ContextCompat.getColor(requireContext(), R.color.analytics_chart_d),
            ContextCompat.getColor(requireContext(), R.color.analytics_chart_e)
        )
        dataSet.valueTextColor = Color.WHITE
        dataSet.valueTextSize = 12f
        dataSet.selectionShift = 10f
        binding.pieChart.data = PieData(dataSet)
        val selectedIndex = completedMembers.indexOfFirst { it.userId == selectedMemberId }
        isUpdatingChartSelection = true
        try {
            if (selectedIndex >= 0) {
                binding.pieChart.highlightValue(selectedIndex.toFloat(), 0, false)
            } else {
                binding.pieChart.highlightValues(null)
            }
        } finally {
            isUpdatingChartSelection = false
        }
        binding.pieChart.invalidate()
    }

    private fun renderDateRange(state: AnalyticsUiState) {
        val rangeText = if (state.startDate != null || state.endDate != null) {
            val start = state.startDate?.let { dateFormat.format(Date(it)) }
                ?: getString(R.string.analytics_range_any_start)
            val end = state.endDate?.let { dateFormat.format(Date(it)) }
                ?: getString(R.string.analytics_range_any_end)
            getString(R.string.analytics_range_format, start, end)
        } else {
            getString(R.string.analytics_pick_date_range)
        }
        binding.startDateButton.text = rangeText
        binding.endDateButton.isVisible = false
    }

    private fun renderTeamHealth(state: AnalyticsUiState) {
        val health = state.teamHealth
        binding.teamHealthStatusText.text = health?.status ?: getString(R.string.analytics_health_pending)
        binding.teamHealthSummaryText.text = state.teamHealthError ?: health?.summary ?: getString(R.string.analytics_health_waiting)
        binding.recommendationsText.text = health?.recommendations
            ?.joinToString(separator = "\n") { recommendation -> "- $recommendation" }
            .orEmpty()
    }

    private fun healthColor(status: String?): Int {
        return when (status) {
            "Healthy" -> R.color.analytics_healthy
            "Mild Imbalance" -> R.color.analytics_mild
            "Severe Imbalance" -> R.color.analytics_severe
            else -> R.color.analytics_neutral
        }
    }

    private fun showDateRangePicker() {
        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText(getString(R.string.analytics_pick_date_range))
            .build()
        picker.addOnPositiveButtonClickListener { range ->
            viewModel.setDateRange(range.first, range.second)
        }
        picker.show(parentFragmentManager, "analytics_date_range")
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
