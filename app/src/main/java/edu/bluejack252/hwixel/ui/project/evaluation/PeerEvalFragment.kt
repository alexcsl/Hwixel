package edu.bluejack252.hwixel.ui.project.evaluation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.RatingBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import edu.bluejack252.hwixel.R
import edu.bluejack252.hwixel.data.ServiceLocator
import edu.bluejack252.hwixel.data.model.Project
import edu.bluejack252.hwixel.data.model.ProjectMember
import edu.bluejack252.hwixel.util.constants.Constants

class PeerEvalFragment : Fragment() {

    private val args: PeerEvalFragmentArgs by navArgs()

    private val viewModel: PeerEvalViewModel by viewModels {
        PeerEvalViewModelFactory(
            repository = ServiceLocator.getEvalRepository(),
            projectId = args.projectId,
            currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        )
    }

    private lateinit var receivedAdapter: ReceivedEvalAdapter
    private var memberDropdownItems: List<Pair<String, String>> = emptyList() // id to name
    private var memberDropdownLabels: Map<String, String> = emptyMap() // label to id
    private var periodOpenResolved = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_peer_eval, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        seedMembersFromArgs()
        setupDropdown(view)
        setupTabs(view)
        setupClickListeners(view)
        observeProjectMembers(view)
        observeViewModel(view)
    }

    private fun seedMembersFromArgs() {
        val memberIds = args.memberIds.toList()
        val memberNameList = args.memberNames.toList()
        val memberRoleList = args.memberRoles.toList()
        val members = memberIds.zip(memberNameList).zip(memberRoleList).map { (idName, role) ->
            Triple(idName.first, idName.second, role)
        }
        publishMembers(members)
    }

    private fun publishMembers(members: List<Triple<String, String, String>>) {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val projectMembers = members.map { (id, _, role) -> ProjectMember(userId = id, role = role) }
        val memberNames = members.associate { (id, name, _) -> id to name.ifBlank { id } }
        viewModel.projectMembers = projectMembers
        viewModel.memberNames = memberNames
        viewModel.currentUserRole = members.firstOrNull { it.first == currentUid }?.third.orEmpty()
        memberDropdownItems = members.filter { it.first != currentUid }.map { (id, name, _) ->
            id to name.ifBlank { id }
        }
        view?.let(::setupDropdown)
    }

    private fun setupDropdown(view: View) {
        val dropdown = view.findViewById<AutoCompleteTextView>(R.id.evaluateeDropdown)
        val labels = memberDropdownItems.associate { (id, name) ->
            val label = if (viewModel.hasAlreadySubmittedFor(id)) {
                getString(R.string.eval_already_submitted_format, name)
            } else {
                name
            }
            label to id
        }
        memberDropdownLabels = labels
        dropdown.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, labels.keys.toList())
        )
        val currentText = dropdown.text?.toString().orEmpty()
        if (currentText.isNotBlank() && currentText !in labels.keys) {
            dropdown.text?.clear()
        }
    }

    private fun observeProjectMembers(view: View) {
        ServiceLocator.getProjectRepository(requireContext())
            .observeProject(args.projectId)
            .observe(viewLifecycleOwner) { project ->
                publishMembersFromProject(project ?: return@observe)
            }
    }

    private fun publishMembersFromProject(project: Project) {
        val members = project.members.mapNotNull { (userId, member) ->
            if (member.status == Constants.MEMBER_STATUS_INACTIVE) return@mapNotNull null
            val id = member.userId.ifBlank { userId }
            val name = viewModel.memberNames[id] ?: id
            Triple(id, name, member.role)
        }.toMutableList()
        if (
            project.createdBy.isNotBlank() &&
            members.none { (id, _, _) -> id == project.createdBy }
        ) {
            val leadName = viewModel.memberNames[project.createdBy] ?: project.createdBy
            members.add(0, Triple(project.createdBy, leadName, Constants.ROLE_TEAM_LEAD))
        }
        publishMembers(members.distinctBy { it.first })
    }

    private fun setupTabs(view: View) {
        val tabLayout = view.findViewById<TabLayout>(R.id.evalTabLayout)
        val submitSection = view.findViewById<LinearLayout>(R.id.submitSection)
        val receivedSection = view.findViewById<LinearLayout>(R.id.receivedSection)

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                when (tab.position) {
                    0 -> { submitSection.isVisible = true; receivedSection.isVisible = false }
                    1 -> { submitSection.isVisible = false; receivedSection.isVisible = true }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        receivedAdapter = ReceivedEvalAdapter { viewModel.memberNames }
        view.findViewById<RecyclerView>(R.id.receivedEvalsRecyclerView).adapter = receivedAdapter
    }

    private fun setupClickListeners(view: View) {
        view.findViewById<View>(R.id.backButton).setOnClickListener {
            findNavController().navigateUp()
        }

        view.findViewById<MaterialButton>(R.id.submitEvalButton).setOnClickListener {
            val dropdown = view.findViewById<AutoCompleteTextView>(R.id.evaluateeDropdown)
            val selectedLabel = dropdown.text.toString()
            val evaluateeId = memberDropdownLabels[selectedLabel]

            if (evaluateeId == null) {
                showSnack(view, getString(R.string.eval_error_select_member))
                return@setOnClickListener
            }

            val communication = view.findViewById<RatingBar>(R.id.ratingCommunication).rating.toInt()
            val quality = view.findViewById<RatingBar>(R.id.ratingQuality).rating.toInt()
            val reliability = view.findViewById<RatingBar>(R.id.ratingReliability).rating.toInt()
            val effort = view.findViewById<RatingBar>(R.id.ratingEffort).rating.toInt()
            val feedback = view.findViewById<TextInputEditText>(R.id.feedbackEditText).text?.toString() ?: ""

            viewModel.submitEvaluation(evaluateeId, communication, quality, reliability, effort, feedback)
        }

        view.findViewById<MaterialButton>(R.id.togglePeriodButton).setOnClickListener {
            viewModel.togglePeriodOpen()
        }

        view.findViewById<MaterialButton>(R.id.createPeriodButton).setOnClickListener {
            viewModel.createPeriod()
        }
    }

    private fun observeViewModel(view: View) {
        val loading = view.findViewById<LinearProgressIndicator>(R.id.evalLoadingIndicator)
        val periodBanner = view.findViewById<LinearLayout>(R.id.periodStatusBanner)
        val periodLabel = view.findViewById<TextView>(R.id.periodStatusLabel)
        val toggleButton = view.findViewById<MaterialButton>(R.id.togglePeriodButton)
        val createButton = view.findViewById<MaterialButton>(R.id.createPeriodButton)
        val noPeriodCard = view.findViewById<LinearLayout>(R.id.noPeriodCard)
        val evalContent = view.findViewById<LinearLayout>(R.id.evalContent)
        val avgReceivedText = view.findViewById<TextView>(R.id.avgReceivedScoreText)
        val noReceivedText = view.findViewById<TextView>(R.id.noReceivedEvalsText)
        val receivedList = view.findViewById<RecyclerView>(R.id.receivedEvalsRecyclerView)
        val submitButton = view.findViewById<MaterialButton>(R.id.submitEvalButton)

        val isLead = viewModel.currentUserRole == Constants.ROLE_TEAM_LEAD
        toggleButton.isVisible = isLead
        createButton.isVisible = isLead
        submitButton.isEnabled = false

        viewModel.isPeriodOpen.observe(viewLifecycleOwner) { isOpen ->
            periodOpenResolved = true
            submitButton.isEnabled = isOpen
            periodBanner.setBackgroundResource(
                if (isOpen) R.drawable.bg_eval_period_open else R.drawable.bg_eval_period_closed
            )
            periodLabel.text = getString(
                if (isOpen) R.string.eval_period_open else R.string.eval_period_closed
            )
            toggleButton.text = getString(
                if (isOpen) R.string.eval_close_period else R.string.eval_open_period
            )
        }

        viewModel.periods.observe(viewLifecycleOwner) { ids ->
            val hasPeriod = ids.isNotEmpty()
            noPeriodCard.isVisible = !hasPeriod && isLead
            evalContent.isVisible = hasPeriod
            submitButton.isEnabled = hasPeriod && periodOpenResolved && viewModel.isPeriodOpen.value == true
        }

        viewModel.submittedByMe.observe(viewLifecycleOwner) {
            setupDropdown(view)
        }

        viewModel.receivedEvals.observe(viewLifecycleOwner) { evals ->
            receivedAdapter.submitList(evals)
            val hasEvals = evals.isNotEmpty()
            noReceivedText.isVisible = !hasEvals
            receivedList.isVisible = hasEvals
            if (hasEvals) {
                val avg = viewModel.computeMyAverageReceived()
                avgReceivedText.text = getString(R.string.eval_my_avg_format, avg)
                avgReceivedText.isVisible = true
            } else {
                avgReceivedText.isVisible = false
            }
        }

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is PeerEvalUiState.Loading -> loading.isVisible = true
                is PeerEvalUiState.SubmitSuccess -> {
                    loading.isVisible = false
                    setupDropdown(view)
                    showSnack(view, getString(R.string.eval_submit_success))
                    // Reset form
                    view.findViewById<RatingBar>(R.id.ratingCommunication).rating = 0f
                    view.findViewById<RatingBar>(R.id.ratingQuality).rating = 0f
                    view.findViewById<RatingBar>(R.id.ratingReliability).rating = 0f
                    view.findViewById<RatingBar>(R.id.ratingEffort).rating = 0f
                    view.findViewById<TextInputEditText>(R.id.feedbackEditText).text?.clear()
                    view.findViewById<AutoCompleteTextView>(R.id.evaluateeDropdown).text?.clear()
                }
                is PeerEvalUiState.PeriodToggled -> {
                    loading.isVisible = false
                    showSnack(view, getString(R.string.eval_period_updated))
                }
                is PeerEvalUiState.PeriodCreated -> {
                    loading.isVisible = false
                    showSnack(view, getString(R.string.eval_period_created))
                }
                is PeerEvalUiState.Error -> {
                    loading.isVisible = false
                    val msg = when (state.message) {
                        "lead_only" -> getString(R.string.error_no_permission)
                        "period_closed" -> getString(R.string.eval_period_closed_error)
                        "self_eval" -> getString(R.string.eval_error_self)
                        "empty_feedback" -> getString(R.string.eval_error_feedback)
                        else -> state.message.ifBlank { getString(R.string.error_generic) }
                    }
                    showSnack(view, msg)
                }
                else -> loading.isVisible = false
            }
        }
    }

    private fun showSnack(view: View, msg: String) =
        Snackbar.make(view, msg, Snackbar.LENGTH_SHORT).show()
}
