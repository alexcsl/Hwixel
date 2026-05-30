package edu.bluejack252.hwixel.ui.profile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import edu.bluejack252.hwixel.MainActivity
import edu.bluejack252.hwixel.R
import edu.bluejack252.hwixel.data.ServiceLocator
import edu.bluejack252.hwixel.data.model.User
import edu.bluejack252.hwixel.data.repository.SharedPrefsProfileSettingsRepository
import edu.bluejack252.hwixel.databinding.DialogEditProfileBinding
import edu.bluejack252.hwixel.databinding.FragmentProfileBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class ProfileFragment : Fragment() {
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private var _editBinding: DialogEditProfileBinding? = null
    private var selectedAvatarUrl: String = ""
    private var selectedAvatarUri: Uri? = null
    private var isRendering = false

    private val viewModel: ProfileViewModel by viewModels {
        ProfileViewModelFactory(
            userRepository = ServiceLocator.getUserRepository(requireContext()),
            settingsRepository = ServiceLocator.getProfileSettingsRepository(requireContext()),
            currentUserId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        )
    }

    private val avatarPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        selectedAvatarUri = uri
        _editBinding?.avatarPreviewImageView?.setImageURI(uri)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.editProfileButton.setOnClickListener {
            viewModel.uiState.value?.user?.let(::showEditProfileDialog)
        }
        binding.logoutButton.setOnClickListener { confirmLogout() }
        bindSettingListeners()
        viewModel.uiState.observe(viewLifecycleOwner, ::render)
        viewModel.saveResult.observe(viewLifecycleOwner) { result ->
            result ?: return@observe
            val message = if (result.isSuccess) R.string.profile_saved else R.string.profile_save_failed
            Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
            viewModel.consumeSaveResult()
        }
    }

    private fun bindSettingListeners() {
        binding.darkModeSwitch.setOnCheckedChangeListener { _, checked ->
            if (!isRendering) viewModel.setDarkMode(checked)
        }
        binding.languageToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || isRendering) return@addOnButtonCheckedListener
            val tag = if (checkedId == R.id.languageBahasaButton) "id" else "en"
            viewModel.setLanguage(tag)
        }
        binding.taskAssignedSwitch.setOnCheckedChangeListener { _, checked ->
            updateNotification(SharedPrefsProfileSettingsRepository.NOTIF_TASK_ASSIGNED, checked)
        }
        binding.mentionsSwitch.setOnCheckedChangeListener { _, checked ->
            updateNotification(SharedPrefsProfileSettingsRepository.NOTIF_MENTION, checked)
        }
        binding.deadlineSwitch.setOnCheckedChangeListener { _, checked ->
            updateNotification(SharedPrefsProfileSettingsRepository.NOTIF_DEADLINE, checked)
        }
        binding.evaluationSwitch.setOnCheckedChangeListener { _, checked ->
            updateNotification(SharedPrefsProfileSettingsRepository.NOTIF_EVALUATION, checked)
        }
        binding.inviteSwitch.setOnCheckedChangeListener { _, checked ->
            updateNotification(SharedPrefsProfileSettingsRepository.NOTIF_INVITE, checked)
        }
    }

    private fun updateNotification(type: String, enabled: Boolean) {
        if (!isRendering) viewModel.setNotificationEnabled(type, enabled)
    }

    private fun render(state: ProfileUiState) {
        isRendering = true
        binding.loadingIndicator.isVisible = state.isLoading
        renderUser(state.user)
        binding.darkModeSwitch.isChecked = state.isDarkMode
        binding.languageToggleGroup.check(
            if (state.languageTag == "id") R.id.languageBahasaButton else R.id.languageEnglishButton
        )
        renderNotificationSettings(state.notificationSettings)
        isRendering = false

        state.errorMessage?.takeIf { it.isNotBlank() }?.let {
            Snackbar.make(binding.root, R.string.error_generic, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun renderUser(user: User?) {
        val displayName = user?.name?.takeIf { it.isNotBlank() } ?: getString(R.string.profile_unknown_name)
        binding.profileNameTextView.text = displayName
        binding.profileStudentIdTextView.text = user?.studentId?.takeIf { it.isNotBlank() }
            ?: getString(R.string.profile_missing_student_id)
        binding.profileEmailTextView.text = user?.email.orEmpty()
        val phone = user?.phone.orEmpty()
        binding.profilePhoneTextView.isVisible = phone.isNotBlank()
        binding.profilePhoneTextView.text = phone
        binding.projectsCompletedValueTextView.text = (user?.totalProjectsCompleted ?: 0).toString()
        binding.peerRatingValueTextView.text = String.format(Locale.getDefault(), "%.1f", user?.averagePeerRating ?: 0f)
        binding.avatarInitialsTextView.text = displayName.trim().firstOrNull()?.uppercaseChar()?.toString().orEmpty()
        val avatarUrl = user?.avatarUrl.orEmpty()
        binding.avatarImageView.isVisible = avatarUrl.isNotBlank()
        binding.avatarInitialsTextView.isVisible = avatarUrl.isBlank()
        if (avatarUrl.isNotBlank()) {
            Glide.with(this)
                .load(avatarUrl)
                .placeholder(R.drawable.circle_background)
                .into(binding.avatarImageView)
        }
        renderBadges(user?.badges.orEmpty())
    }

    private fun renderBadges(badges: List<String>) {
        binding.badgeChipGroup.removeAllViews()
        binding.emptyBadgesTextView.isVisible = badges.isEmpty()
        badges.forEach { badge ->
            val chip = Chip(requireContext()).apply {
                text = badgeLabel(badge)
                setTextColor(resources.getColor(R.color.text_primary, null))
                setChipBackgroundColorResource(R.color.glass_primary)
                setChipStrokeColorResource(R.color.glass_primary_border)
                chipStrokeWidth = resources.getDimension(R.dimen.chip_stroke_width)
                isCheckable = false
            }
            binding.badgeChipGroup.addView(chip)
        }
    }

    private fun badgeLabel(badge: String): String = when (badge) {
        "top_contributor" -> getString(R.string.badge_top_contributor)
        "deadline_crusher" -> getString(R.string.badge_deadline_crusher)
        else -> badge.replace("_", " ").replaceFirstChar { it.titlecase(Locale.getDefault()) }
    }

    private fun renderNotificationSettings(settings: Map<String, Boolean>) {
        binding.taskAssignedSwitch.isChecked = settings[SharedPrefsProfileSettingsRepository.NOTIF_TASK_ASSIGNED] ?: true
        binding.mentionsSwitch.isChecked = settings[SharedPrefsProfileSettingsRepository.NOTIF_MENTION] ?: true
        binding.deadlineSwitch.isChecked = settings[SharedPrefsProfileSettingsRepository.NOTIF_DEADLINE] ?: true
        binding.evaluationSwitch.isChecked = settings[SharedPrefsProfileSettingsRepository.NOTIF_EVALUATION] ?: true
        binding.inviteSwitch.isChecked = settings[SharedPrefsProfileSettingsRepository.NOTIF_INVITE] ?: true
    }

    private fun showEditProfileDialog(user: User) {
        val dialogBinding = DialogEditProfileBinding.inflate(layoutInflater)
        _editBinding = dialogBinding
        selectedAvatarUrl = user.avatarUrl
        selectedAvatarUri = null
        dialogBinding.nameInput.editText?.setText(user.name)
        dialogBinding.studentIdInput.editText?.setText(user.studentId)
        dialogBinding.phoneInput.editText?.setText(user.phone)
        if (user.avatarUrl.isNotBlank()) {
            Glide.with(this).load(user.avatarUrl).placeholder(R.drawable.circle_background)
                .into(dialogBinding.avatarPreviewImageView)
        }
        dialogBinding.changeAvatarButton.setOnClickListener {
            avatarPicker.launch("image/*")
        }
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.profile_edit_title)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.btn_cancel, null)
            .setPositiveButton(R.string.btn_save, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = dialogBinding.nameInput.editText?.text?.toString().orEmpty()
                val studentId = dialogBinding.studentIdInput.editText?.text?.toString().orEmpty()
                val phone = dialogBinding.phoneInput.editText?.text?.toString().orEmpty()
                if (name.isBlank() || studentId.isBlank()) {
                    Snackbar.make(binding.root, R.string.profile_required_fields, Snackbar.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                saveProfileFromDialog(user, dialogBinding, dialog, name, studentId, phone)
            }
        }
        dialog.setOnDismissListener {
            _editBinding = null
            selectedAvatarUri = null
        }
        dialog.show()
    }

    private fun saveProfileFromDialog(
        user: User,
        dialogBinding: DialogEditProfileBinding,
        dialog: AlertDialog,
        name: String,
        studentId: String,
        phone: String
    ) {
        val pickedUri = selectedAvatarUri
        if (pickedUri == null) {
            viewModel.saveProfile(name, studentId, phone, selectedAvatarUrl)
            dialog.dismiss()
            return
        }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        lifecycleScope.launch {
            val uploadResult = uploadAvatar(user.id, pickedUri)
            uploadResult
                .onSuccess { publicUrl ->
                    selectedAvatarUrl = publicUrl
                    viewModel.saveProfile(name, studentId, phone, publicUrl)
                    dialog.dismiss()
                }
                .onFailure {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                    Snackbar.make(dialogBinding.root, R.string.profile_save_failed, Snackbar.LENGTH_SHORT).show()
                }
        }
    }

    private suspend fun uploadAvatar(userId: String, uri: Uri): Result<String> = runCatching {
        val contentType = requireContext().contentResolver.getType(uri) ?: "image/jpeg"
        val extension = when (contentType) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            else -> "jpg"
        }
        val fileName = "$userId-${System.currentTimeMillis()}.$extension"
        val bytes = withContext(Dispatchers.IO) {
            requireNotNull(requireContext().contentResolver.openInputStream(uri)) { "Cannot open avatar." }
                .use { stream -> stream.readBytes() }
        }
        ServiceLocator.getAttachmentUploadSource()
            .upload("avatars", fileName, bytes, contentType)
            .getOrThrow()
    }

    private fun confirmLogout() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.logout_confirm_title)
            .setMessage(R.string.logout_confirm_message)
            .setPositiveButton(R.string.logout_button) { _, _ -> logout() }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun logout() {
        ServiceLocator.getAuthRepository(requireContext()).logout()
        ServiceLocator.reset()
        val intent = Intent(requireContext(), MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        requireActivity().finish()
    }

    override fun onDestroyView() {
        _editBinding = null
        _binding = null
        super.onDestroyView()
    }
}
