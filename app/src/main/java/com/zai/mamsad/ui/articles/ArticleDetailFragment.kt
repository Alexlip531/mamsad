package com.zai.mamsad.ui.articles

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.zai.mamsad.R
import com.zai.mamsad.databinding.FragmentArticleDetailBinding

/**
 * Shows a single article (post) in a styled WebView.
 * Receives title, content (HTML), link as arguments.
 */
class ArticleDetailFragment : Fragment() {

    private var _binding: FragmentArticleDetailBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentArticleDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        val title = arguments?.getString("title") ?: ""
        val content = arguments?.getString("content") ?: ""
        val link = arguments?.getString("link") ?: ""

        binding.toolbar.title = title

        binding.webView.settings.apply {
            javaScriptEnabled = false
            loadWithOverviewMode = true
            useWideViewPort = true
        }
        binding.webView.webViewClient = WebViewClient()

        val html = buildHtml(title, content, link)
        binding.webView.loadDataWithBaseURL("https://mamsad.ru/", html, "text/html", "utf-8", null)
    }

    /**
     * Wrap article HTML in a styled page so it matches the app's palette.
     */
    private fun buildHtml(title: String, content: String, link: String): String {
        return """
            <!DOCTYPE html>
            <html lang="ru">
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
                <style>
                    body {
                        font-family: -apple-system, "Roboto", sans-serif;
                        color: #2D2A28;
                        background: #FFF7F2;
                        padding: 18px 16px 32px;
                        line-height: 1.5;
                        font-size: 15px;
                        margin: 0;
                    }
                    h1 {
                        color: #E85A4A;
                        font-size: 22px;
                        line-height: 1.25;
                        margin: 0 0 14px;
                    }
                    h2 { color: #2D2A28; font-size: 18px; margin-top: 20px; }
                    h3 { color: #2D2A28; font-size: 16px; margin-top: 16px; }
                    p { margin: 0 0 12px; }
                    img { max-width: 100%; height: auto; border-radius: 12px; margin: 12px 0; }
                    a { color: #E85A4A; text-decoration: none; }
                    blockquote {
                        border-left: 3px solid #7FB069;
                        background: #F4E7DC;
                        padding: 10px 14px;
                        margin: 12px 0;
                        border-radius: 8px;
                    }
                    ul, ol { padding-left: 20px; margin: 8px 0 12px; }
                    li { margin: 4px 0; }
                    .source {
                        margin-top: 28px;
                        padding: 12px 14px;
                        background: #FFFFFF;
                        border-radius: 12px;
                        font-size: 13px;
                        color: #5C5650;
                        text-align: center;
                    }
                    .source a { color: #E85A4A; font-weight: bold; }
                </style>
            </head>
            <body>
                <h1>${escape(title)}</h1>
                ${content}
                <div class="source">
                    Источник: <a href="${escape(link)}">mamsad.ru</a>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun escape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    override fun onDestroyView() {
        super.onDestroyView()
        binding.webView.destroy()
        _binding = null
    }
}
