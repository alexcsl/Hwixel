package edu.bluejack252.hwixel.ui.project.hub

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import edu.bluejack252.hwixel.ui.project.analytics.AnalyticsFragment
import edu.bluejack252.hwixel.ui.project.members.MembersFragment
import edu.bluejack252.hwixel.ui.project.tasks.TaskBoardFragment

class ProjectPagerAdapter(
    fragmentManager: FragmentManager,
    lifecycle: Lifecycle,
    private val projectId: String
) : FragmentStateAdapter(fragmentManager, lifecycle) {

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        val args = Bundle().apply { putString(ARG_PROJECT_ID, projectId) }
        return when (position) {
            0 -> TaskBoardFragment().also { it.arguments = args }
            1 -> AnalyticsFragment().also { it.arguments = args }
            2 -> MembersFragment().also { it.arguments = args }
            else -> throw IllegalArgumentException("Invalid tab position: $position")
        }
    }

    companion object {
        const val ARG_PROJECT_ID = "projectId"
    }
}
