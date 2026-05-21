package edu.bluejack252.hwixel.ui.project.taskdetail

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.bumptech.glide.Glide
import edu.bluejack252.hwixel.databinding.DialogImageAttachmentViewerBinding

class ImageAttachmentDialogFragment : DialogFragment() {
    private var _binding: DialogImageAttachmentViewerBinding? = null
    private val binding get() = _binding!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = DialogImageAttachmentViewerBinding.inflate(inflater, container, false)
        .also { _binding = it }
        .root

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        val url = requireArguments().getString(ARG_URL).orEmpty()
        Glide.with(this)
            .load(url)
            .fitCenter()
            .into(binding.attachmentImageView)
        binding.closeImageButton.setOnClickListener { dismiss() }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_URL = "url"

        fun newInstance(url: String): ImageAttachmentDialogFragment {
            return ImageAttachmentDialogFragment().apply {
                arguments = Bundle().apply { putString(ARG_URL, url) }
            }
        }
    }
}
