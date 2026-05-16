package edu.bluejack252.hwixel.ui.auth.login

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
import edu.bluejack252.hwixel.databinding.FragmentLoginBinding

class LoginFragment : Fragment() {
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LoginViewModel by viewModels {
        LoginViewModelFactory(ServiceLocator.getAuthRepository(requireContext()))
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.loginButton.setOnClickListener {
            clearErrors()
            viewModel.login(
                email = binding.emailEditText.text?.toString().orEmpty(),
                password = binding.passwordEditText.text?.toString().orEmpty()
            )
        }
        binding.registerLinkButton.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_registerFragment)
        }
        viewModel.uiState.observe(viewLifecycleOwner, ::render)
    }

    private fun render(state: LoginUiState) {
        binding.loginButton.isEnabled = state !is LoginUiState.Loading
        when (state) {
            LoginUiState.Idle,
            LoginUiState.Loading -> Unit
            LoginUiState.Success -> {
                Snackbar.make(binding.root, R.string.login_success, Snackbar.LENGTH_SHORT).show()
                findNavController().setGraph(R.navigation.main_nav_graph)
            }
            is LoginUiState.Error -> showError(state.messageResId)
        }
    }

    private fun showError(messageResId: Int) {
        when (messageResId) {
            R.string.error_invalid_email -> binding.emailInputLayout.error = getString(messageResId)
            R.string.error_empty_password -> binding.passwordInputLayout.error = getString(messageResId)
            else -> Snackbar.make(binding.root, messageResId, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun clearErrors() {
        binding.emailInputLayout.error = null
        binding.passwordInputLayout.error = null
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
