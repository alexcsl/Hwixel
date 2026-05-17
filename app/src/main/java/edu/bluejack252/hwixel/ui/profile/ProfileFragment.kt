package edu.bluejack252.hwixel.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import edu.bluejack252.hwixel.R
import edu.bluejack252.hwixel.data.ServiceLocator
import edu.bluejack252.hwixel.databinding.FragmentProfileBinding

class ProfileFragment : Fragment() {
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.logoutButton.setOnClickListener { confirmLogout() }
    }

    private fun confirmLogout() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.logout_confirm_title)
            .setMessage(R.string.logout_confirm_message)
            .setPositiveButton(R.string.logout_button) { _, _ -> logout() }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun logout() {
        ServiceLocator.getAuthRepository(requireContext()).logout()
        findNavController().setGraph(R.navigation.auth_nav_graph)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
