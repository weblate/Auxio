package org.oxycblt.auxio.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.oxycblt.auxio.BuildConfig
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.DialogAboutBinding
import org.oxycblt.auxio.logD
import org.oxycblt.auxio.logE
import org.oxycblt.auxio.music.MusicStore
import org.oxycblt.auxio.ui.createToast

/**
 * A [BottomSheetDialogFragment] that shows Auxio's about screen.
 * @author OxygenCobalt
 */
class AboutDialog : BottomSheetDialogFragment() {
    override fun getTheme() = R.style.Theme_BottomSheetFix

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = DialogAboutBinding.inflate(layoutInflater)
        val musicStore = MusicStore.getInstance()

        binding.aboutVersion.text = BuildConfig.VERSION_NAME

        binding.aboutCode.setOnClickListener { openLinkInBrowser(LINK_CODEBASE) }
        binding.aboutFaq.setOnClickListener { openLinkInBrowser(LINK_FAQ) }
        binding.aboutLicenses.setOnClickListener { openLinkInBrowser(LINK_LICENSES) }
        binding.aboutSongCount.text = getString(
            R.string.format_songs_loaded, musicStore.songs.size
        )

        logD("Dialog created.")

        return binding.root
    }

    /**
     * Go through the process of opening a [link] in a browser. Only supports the links
     * in [AboutDialog.Companion.LINKS].
     */
    private fun openLinkInBrowser(link: String) {
        check(link in LINKS) { "Invalid link." }

        try {
            val uri = link.toUri()

            val browserIntent = Intent(Intent.ACTION_VIEW, uri)
            browserIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

            val fallbackCandidates = requireContext().packageManager.queryIntentActivities(
                browserIntent, 0
            )

            // If there are candidates here, then launch those.
            if (fallbackCandidates.size > 0) {
                requireActivity().startActivity(browserIntent)
            } else {
                // Otherwise they don't have a browser on their phone, meaning they should
                // just see an error.
                getString(R.string.error_no_browser).createToast(requireContext())
            }
        } catch (e: Exception) {
            logE("Browser intent launching failed [Probably android's fault]")
            logE(e.stackTraceToString())

            // Sometimes people have """Browsers""" on their phone according to android,
            // but they actually don't so here's a fallback for that.
            getString(R.string.error_no_browser).createToast(requireContext())
        }
    }

    companion object {
        private const val LINK_CODEBASE = "https://github.com/oxygencobalt/Auxio"
        private const val LINK_FAQ = "$LINK_CODEBASE/blob/master/info/FAQ.md"
        private const val LINK_LICENSES = "$LINK_CODEBASE/blob/master/info/LICENSES.md"

        val LINKS = arrayOf(LINK_CODEBASE, LINK_FAQ, LINK_LICENSES)
    }
}