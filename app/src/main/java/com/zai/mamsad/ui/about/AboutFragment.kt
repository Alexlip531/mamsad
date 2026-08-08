package com.zai.mamsad.ui.about

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.zai.mamsad.BuildConfig
import com.zai.mamsad.R
import com.zai.mamsad.admin.AdminPrefs
import com.zai.mamsad.api.NetworkClient
import com.zai.mamsad.databinding.FragmentAboutBinding
import com.zai.mamsad.ui.admin.AdminPasswordDialog

class AboutFragment : Fragment(), AdminPasswordDialog.Host {

    private var _binding: FragmentAboutBinding? = null
    private val binding get() = _binding!!

    private var versionTaps = 0
    private var firstTapTime = 0L

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvVersion.text = getString(R.string.about_version, BuildConfig.VERSION_NAME)

        // Secret 7-tap on version label → admin password dialog
        binding.tvVersion.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - firstTapTime > 2000) {
                // reset window if user paused too long
                versionTaps = 0
                firstTapTime = now
            }
            versionTaps++
            if (versionTaps >= 7) {
                versionTaps = 0
                tryEnterAdmin()
            }
        }

        binding.btnOpenSite.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(NetworkClient.SITE_URL)))
        }
        binding.btnFeedback.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf("support@mamsad.ru"))
                putExtra(Intent.EXTRA_SUBJECT, "Мамсад — обратная связь")
            }
            startActivity(Intent.createChooser(intent, getString(R.string.about_btn_feedback)))
        }
    }

    private fun tryEnterAdmin() {
        val ctx = requireContext()
        if (AdminPrefs.isUnlocked(ctx)) {
            // Already unlocked this session — go straight in
            findNavController().navigate(R.id.action_about_to_admin)
        } else {
            AdminPasswordDialog().show(parentFragmentManager, AdminPasswordDialog.TAG)
        }
    }

    override fun onAdminUnlocked() {
        findNavController().navigate(R.id.action_about_to_admin)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
