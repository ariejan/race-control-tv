package fr.groggy.racecontrol.tv.ui.player

import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.leanback.media.PlaybackTransportControlGlue
import androidx.leanback.media.PlayerAdapter
import androidx.leanback.widget.*
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.analytics.AnalyticsListener
import com.google.android.exoplayer2.analytics.AnalyticsListener.EventTime
import com.google.android.exoplayer2.ext.leanback.LeanbackPlayerAdapter
import com.google.android.exoplayer2.source.MediaSourceEventListener.MediaLoadData
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import fr.groggy.racecontrol.tv.R
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class ExoPlayerPlaybackTransportControlGlue(
    private val activity: FragmentActivity,
    player: SimpleExoPlayer,
    private val trackSelector: DefaultTrackSelector) :
    PlaybackTransportControlGlue<LeanbackPlayerAdapter>(activity, LeanbackPlayerAdapter(activity, player, 1_000)),
    AnalyticsListener {

    companion object {
        private val TAG = ExoPlayerPlaybackTransportControlGlue::class.simpleName

        private const val DEFAULT_SEEK_OFFSET = 15_000L
    }

    private val rewindAction = PlaybackControlsRow.RewindAction(activity)
    private val fastFormatAction = PlaybackControlsRow.FastForwardAction(activity)
    private val selectAudioAction = Action(
        Action.NO_ID,
        activity.getString(R.string.audio_selection_dialog_title),
        null,
        activity.getDrawable(R.drawable.lb_ic_search_mic_out)
    )

    private var currentVideoFormat: Format? = null
    private var currentAudioFormat: Format? = null

    init {
        player.addAnalyticsListener(this)
    }

    override fun onCreatePrimaryActions(adapter: ArrayObjectAdapter) {
        Log.d(TAG, "onCreatePrimaryActions")
        adapter.apply {
            super.onCreatePrimaryActions(this)
            add(rewindAction)
            add(fastFormatAction)
            add(selectAudioAction)
        }
    }

    override fun onActionClicked(action: Action) {
        Log.d(TAG, "onActionClicked")
        when(action) {
            rewindAction -> playerAdapter.seekOffset(-DEFAULT_SEEK_OFFSET)
            fastFormatAction -> playerAdapter.seekOffset(DEFAULT_SEEK_OFFSET)
            selectAudioAction -> openAudioSelectionDialog()
            else -> super.onActionClicked(action)
        }
    }

    private fun openAudioSelectionDialog() {
        trackSelector.currentMappedTrackInfo?.let {
            val audio = it.getTrackGroups(1)
            val dialog = AudioSelectionDialogFragment(audio)
            dialog.onAudioLanguageSelected { language ->
                val parameters = trackSelector.buildUponParameters()
                    .setPreferredAudioLanguage(language)
                trackSelector.setParameters(parameters)
            }
            activity.supportFragmentManager.beginTransaction()
                .add(dialog, null)
                .commit()
        }
    }

    override fun onTracksChanged(eventTime: EventTime, trackGroups: TrackGroupArray, trackSelections: TrackSelectionArray) {
        Log.d(TAG, "onTracksChanged")
        val audio = trackSelections[1]
        if (audio != null) {
            currentAudioFormat = audio.selectedFormat
            updateSubtitle()
        }
    }

    override fun onDownstreamFormatChanged(eventTime: EventTime, mediaLoadData: MediaLoadData) {
        Log.d(TAG, "onDownstreamFormatChanged")
        val trackFormat = mediaLoadData.trackFormat
        if (mediaLoadData.dataType != C.DATA_TYPE_MEDIA || trackFormat == null) {
            return
        }
        if (mediaLoadData.trackType == C.TRACK_TYPE_DEFAULT || mediaLoadData.trackType == C.TRACK_TYPE_VIDEO) {
            currentVideoFormat = trackFormat
            updateSubtitle()
        }
    }

    private fun updateSubtitle() {
        val videoQuality = currentVideoFormat?.let { context.getString(R.string.video_quality, it.height, it.frameRate.roundToInt()) }
        val audioLanguage = currentAudioFormat?.label
        subtitle = listOfNotNull(videoQuality, audioLanguage).joinToString(separator = " / ")
    }

}

private fun PlayerAdapter.seekOffset(offset: Long) {
    val position = max(min(currentPosition + offset, duration), 0)
    seekTo(position)
}