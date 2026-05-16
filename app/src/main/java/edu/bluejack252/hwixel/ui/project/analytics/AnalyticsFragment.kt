package edu.bluejack252.hwixel.ui.project.analytics

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import edu.bluejack252.hwixel.R
import edu.bluejack252.hwixel.databinding.FragmentPlaceholderBinding

class AnalyticsFragment : Fragment() {
    private var _binding: FragmentPlaceholderBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlaceholderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.placeholderTextView.setText(R.string.analytics_placeholder)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
