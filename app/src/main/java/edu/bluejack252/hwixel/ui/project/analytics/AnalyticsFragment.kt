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
        binding.membersRecyclerView.adapter = memberAdapter
        binding.dateRangeButton.setOnClickListener { showDateRangePicker() }
        binding.clearDateRangeButton.setOnClickListener { viewModel.setDateRange(null, null) }
        binding.refreshHealthButton.setOnClickListener { viewModel.refreshTeamHealth() }
        configurePieChart()
        viewModel.uiState.observe(viewLifecycleOwner, ::render)
    }

    private fun configurePieChart() {
        binding.completedPieChart.description.isEnabled = false
        binding.completedPieChart.setUsePercentValues(false)
        binding.completedPieChart.setDrawEntryLabels(false)
        binding.completedPieChart.legend.isWordWrapEnabled = true
        binding.completedPieChart.setNoDataText(getString(R.string.analytics_no_completed_tasks))
        binding.completedPieChart.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
            override fun onValueSelected(e: com.github.mikephil.charting.data.Entry?, h: Highlight?) {
                val member = e?.data as? MemberAnalyticsUi ?: return
                viewModel.selectMember(member.userId)
            }

            override fun onNothingSelected() {
                viewModel.selectMember(null)
            }
        })
    }

    private fun render(state: AnalyticsUiState) {
        binding.analyticsLoadingIndicator.isVisible = state.isLoading
        binding.analyticsContentGroup.isVisible = !state.isLoading && state.members.isNotEmpty()
        binding.emptyAnalyticsTextView.isVisible = !state.isLoading && state.members.isEmpty()

        memberAdapter.selectedMemberId = state.selectedMemberId
        memberAdapter.submitList(state.members)
        renderPieChart(state.members, state.selectedMemberId)
        renderDateRange(state)
        renderTeamHealth(state)
    }

    private fun renderPieChart(members: List<MemberAnalyticsUi>, selectedMemberId: String?) {
        val completedMembers = members.filter { it.completedCount > 0 }
        if (completedMembers.isEmpty()) {
            binding.completedPieChart.clear()
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
        binding.completedPieChart.data = PieData(dataSet)
        val selectedIndex = completedMembers.indexOfFirst { it.userId == selectedMemberId }
        if (selectedIndex >= 0) {
            binding.completedPieChart.highlightValue(selectedIndex.toFloat(), 0)
        } else {
            binding.completedPieChart.highlightValues(null)
        }
        binding.completedPieChart.invalidate()
    }

    private fun renderDateRange(state: AnalyticsUiState) {
        binding.dateRangeButton.text = if (state.startDate != null || state.endDate != null) {
            val start = state.startDate?.let { dateFormat.format(Date(it)) }
                ?: getString(R.string.analytics_range_any_start)
            val end = state.endDate?.let { dateFormat.format(Date(it)) }
                ?: getString(R.string.analytics_range_any_end)
            getString(R.string.analytics_range_format, start, end)
        } else {
            getString(R.string.analytics_pick_date_range)
        }
        binding.clearDateRangeButton.isVisible = state.startDate != null || state.endDate != null
    }

    private fun renderTeamHealth(state: AnalyticsUiState) {
        binding.teamHealthProgressIndicator.isVisible = state.teamHealthLoading
        binding.teamHealthErrorTextView.isVisible = state.teamHealthError != null
        binding.teamHealthErrorTextView.text = state.teamHealthError.orEmpty()

        val health = state.teamHealth
        binding.teamHealthStatusChip.text = health?.status ?: getString(R.string.analytics_health_pending)
        binding.teamHealthSummaryTextView.text = health?.summary ?: getString(R.string.analytics_health_waiting)
        binding.teamHealthRecommendationsTextView.text = health?.recommendations
            ?.joinToString(separator = "\n") { recommendation -> "- $recommendation" }
            .orEmpty()
        binding.teamHealthStatusChip.setChipBackgroundColorResource(healthColor(health?.status))
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
