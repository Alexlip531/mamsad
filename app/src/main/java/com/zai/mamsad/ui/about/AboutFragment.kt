package com.zai.mamsad.ui.about

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.zai.mamsad.BuildConfig
import com.zai.mamsad.R
import com.zai.mamsad.api.NetworkClient
import com.zai.mamsad.databinding.FragmentAboutBinding

class AboutFragment : Fragment() {

    private var _binding: FragmentAboutBinding? = null
    private val binding get() = _binding!!

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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
