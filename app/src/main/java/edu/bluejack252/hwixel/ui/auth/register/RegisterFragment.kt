package edu.bluejack252.hwixel.ui.auth.register

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import edu.bluejack252.hwixel.R
import edu.bluejack252.hwixel.data.ServiceLocator
import edu.bluejack252.hwixel.databinding.FragmentRegisterBinding

class RegisterFragment : Fragment() {
    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RegisterViewModel by viewModels {
        RegisterViewModelFactory(ServiceLocator.getAuthRepository(requireContext()))
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.registerButton.setOnClickListener {
            clearErrors()
            viewModel.register(
                name = binding.nameEditText.text?.toString().orEmpty(),
                studentId = binding.studentIdEditText.text?.toString().orEmpty(),
                email = binding.emailEditText.text?.toString().orEmpty(),
                password = binding.passwordEditText.text?.toString().orEmpty(),
                confirmPassword = binding.confirmPasswordEditText.text?.toString().orEmpty()
            )
        }
        binding.loginLinkButton.setOnClickListener {
            findNavController().popBackStack()
        }
        viewModel.uiState.observe(viewLifecycleOwner, ::render)
    }

    private fun render(state: RegisterUiState) {
        binding.registerButton.isEnabled = state !is RegisterUiState.Loading
        when (state) {
            RegisterUiState.Idle,
            RegisterUiState.Loading -> Unit
            RegisterUiState.Success -> {
                Snackbar.make(binding.root, R.string.register_success, Snackbar.LENGTH_SHORT).show()
                findNavController().setGraph(R.navigation.main_nav_graph)
            }
            is RegisterUiState.Error -> showError(state.messageResId)
        }
    }

    private fun showError(messageResId: Int) {
        when (messageResId) {
            R.string.error_empty_name -> binding.nameInputLayout.error = getString(messageResId)
            R.string.error_empty_student_id -> binding.studentIdInputLayout.error = getString(messageResId)
            R.string.error_invalid_email -> binding.emailInputLayout.error = getString(messageResId)
            R.string.error_weak_password -> binding.passwordInputLayout.error = getString(messageResId)
            R.string.error_password_mismatch -> binding.confirmPasswordInputLayout.error = getString(messageResId)
            else -> Snackbar.make(binding.root, messageResId, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun clearErrors() {
        binding.nameInputLayout.error = null
        binding.studentIdInputLayout.error = null
        binding.emailInputLayout.error = null
        binding.passwordInputLayout.error = null
        binding.confirmPasswordInputLayout.error = null
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
