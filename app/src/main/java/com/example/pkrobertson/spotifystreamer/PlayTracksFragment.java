package com.example.pkrobertson.spotifystreamer;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.DialogFragment;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.ShareActionProvider;
import android.util.Log;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.util.Formatter;
import java.util.Locale;

/**
 * PlayTracksFragment - a fragment that holds the "fragment_play_tracks" view and
 *     handles controls/view for playing the top tracks for a given artist
 *
 *     the lifetime of this fragment is managed by PlayTracksActivity and ArtistSearchActivity
 *
 */
public class PlayTracksFragment extends DialogFragment {

    private final String LOG_TAG = PlayTracksFragment.class.getSimpleName();

    // key used to retrieve fragment args
	private static final String DIALOG_ARGS = "playtracksdialog";
	
    // message id and handler used to update seekbar and current position during playback
    private static final int MONITOR_SERVICE = 42;
    private static final int MONITOR_SLOW    = 250; // 250ms
    private static final int MONITOR_FAST    = 100; // 100ms, not all that fast ;)

       // local copy of play tracks instance (when connected), state and currently selected track
    // private PlayTracksService                 mTopTrackService;
	private PlayTracksService.PlayTracksState mTopTrackState;
	private int                               mTopTrackNowPlaying;
    private boolean                           mUpdateSeekBar;

    // store references to view elements
    private TextView    mTextViewArtistName;
    private ImageButton mImageButtonShareTrack; // only present in portrait/dialog mode
    private TextView    mTextViewTrackName;
    private TextView    mTextViewAlbumName;
    private ImageView   mImageViewTrackImage;
    private SeekBar     mSeekBarTrackProgress;
    private TextView    mTextViewDuration;
    private TextView    mTextViewCurrentTime;
    private ImageButton mImageButtonPreviousTrack;
    private ImageButton mImageButtonPausePlay;
    private ImageButton mImageButtonNextTrack;

    // store reference to share action intent so it is ready for the share button click
    private Intent mShareActionIntent;

	// user to format duration and current position in track
    private StringBuilder mFormatBuilder;
    private Formatter     mFormatter;

    // used to flag when user dismissed dialog vs screen rotation (handle backstack)
    private boolean mBackButtonPressed;

    // message delay, when playing fast poll, otherwise run a slow poll
    private int mHandlerDelay = MONITOR_SLOW;

    // ideally a Handler should be declared static to avoid leaks. In this case we're taking care
    // to instantiate one time only when PlayTracksFragment is instantiated
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message message) {
            if (message.what == MONITOR_SERVICE) {
                checkServiceState ();

                // polling used to update view every 100ms when playing
                // when idle, poll every 250ms
                message = obtainMessage(MONITOR_SERVICE);
                sendMessageDelayed (message, mHandlerDelay);
            }
        }
    };


	// code to format time was lifted from Android MediaController Widget
    private String stringForTime(int timeMs) {
        int totalSeconds = timeMs / 1000;
        int seconds = totalSeconds % 60;
        int minutes = (totalSeconds / 60) % 60;
        int hours   = totalSeconds / 3600;
        mFormatBuilder.setLength(0);
        if (hours > 0) {
            return mFormatter.format("%d:%02d:%02d", hours, minutes, seconds).toString();
        } else {
            return mFormatter.format("%02d:%02d", minutes, seconds).toString();
        }
    }

    // playTracksService -- get service instance from parent activity
    private PlayTracksService playTracksService () {
        PlayTrackInterface myActivity = (PlayTrackInterface)getActivity();
        if (myActivity != null) {
            return myActivity.getServiceInstance();
        }

        // should not happen, but we're not bound to the service for some reason...
        return null;
    }

    // showNewState - manages the state of playback controls based on Play Tracks Service state
    private void showNewState () {
        // set default handler delay, updated to faster poll only when playing
        mHandlerDelay = MONITOR_SLOW;

        switch (mTopTrackState) {
            case PLAYTRACKS_PLAYING:
                mImageButtonPreviousTrack.setClickable(true);
                mImageButtonPausePlay.setClickable(true);
                mImageButtonNextTrack.setClickable (true);
                mImageButtonPausePlay.setImageResource(R.drawable.ic_pause_black);
                mHandlerDelay = MONITOR_FAST;
                break;

            case PLAYTRACKS_PAUSED:
                mImageButtonPreviousTrack.setClickable(true);
                mImageButtonPausePlay.setClickable(true);
                mImageButtonNextTrack.setClickable(true);
                mImageButtonPausePlay.setImageResource(R.drawable.ic_play_arrow_black);
                break;

			case PLAYTRACKS_ERROR:
                mImageButtonPreviousTrack.setClickable(true);
                mImageButtonPausePlay.setClickable(false);
                mImageButtonNextTrack.setClickable(true);
				mTextViewTrackName.setText (R.string.error_play_tracks);
                mImageButtonPausePlay.setImageResource(R.drawable.ic_play_arrow_black);
                break;
				
            default:
                mImageButtonPreviousTrack.setClickable(false);
                mImageButtonPausePlay.setClickable(false);
                mImageButtonNextTrack.setClickable(false);
                mImageButtonPausePlay.setImageResource(R.drawable.ic_play_arrow_black);
                break;
        }
    }

    // showProgressUpdate - manages the SeekBar and the current position and duration
    // TODO: could make this slightly more efficient by only updating the duration on change
	private void showProgressUpdate (PlayTracksService service) {
        int position = service.getCurrentPosition();
        int duration = service.getDuration();

		if (duration > 0) {
            mSeekBarTrackProgress.setMax (duration);
            mSeekBarTrackProgress.setProgress(position);
        }
        
		mTextViewDuration.setText(stringForTime(duration));
        mTextViewCurrentTime.setText(stringForTime(position));		
	}

    // showNewTrackItem - updates view of currently playing item when the now playing track changes
	private void showNewTrackItem (TopTrackItem topTrackItem) {
        // update artist, track and album
        mTextViewArtistName.setText (topTrackItem.topTrackArtistName);
        mTextViewTrackName.setText (topTrackItem.topTrackName);
        mTextViewAlbumName.setText (topTrackItem.topTrackAlbumName);

        // make sure the image URL is valid, if not load a default image
        if ((topTrackItem.topTrackPlayImageURL != null) &&
                Patterns.WEB_URL.matcher(topTrackItem.topTrackPlayImageURL).matches()) {
            try {
                Picasso.with(getActivity()).load(topTrackItem.topTrackPlayImageURL).into(mImageViewTrackImage);
            } catch (Exception e) {
                Log.v(LOG_TAG, "Picasso call failed" + e.toString());
                mImageViewTrackImage.setImageResource(R.drawable.ic_artist_image);
            }
        } else {
            mImageViewTrackImage.setImageResource(R.drawable.ic_artist_image);
        }

        // update share intent based on current track
        mShareActionIntent = new Intent(Intent.ACTION_SEND);
        mShareActionIntent.setType("text/plain");
        mShareActionIntent.putExtra(Intent.EXTRA_TEXT,
                topTrackItem.topTrackAudioURL + getString(R.string.app_hash_tag));
    }

    // checkServiceState - handles monitoring of playback state and updating the view
    private void checkServiceState () {
        PlayTracksService service = playTracksService();
        if ( service == null ) {
            return;
        }
		// see if this is a new track...
		int trackNowPlaying = service.getTrackNowPlaying();
		if ( trackNowPlaying != mTopTrackNowPlaying ) {
			mTopTrackNowPlaying = trackNowPlaying;
            mUpdateSeekBar      = true;
			showNewTrackItem (service.getTrackItem());
		}
		
		// see if this is a different state...
        PlayTracksService.PlayTracksState topTrackState = service.getState();
		if (topTrackState != mTopTrackState) {
			mTopTrackState = topTrackState;
			showNewState();
		}
		
		// update duration if we're not paused
		if ( mUpdateSeekBar &&
                (mTopTrackState == PlayTracksService.PlayTracksState.PLAYTRACKS_PLAYING) ) {
			showProgressUpdate(service);
        }
	}

    // startServiceMonitor -- send a message to kick things off
    private void startServiceMonitor () {
        mHandler.sendEmptyMessage(MONITOR_SERVICE);
    }

    // stopServiceMonitor -- remove all messages in the queue
    private void stopServiceMonitor () {
        mHandler.removeMessages(MONITOR_SERVICE);
    }

    // PlayTrackInterface -- define interface used to get service instance from parent activity
    public interface PlayTrackInterface {
        // getServiceInstance -- returns currently active service instance or null
        public PlayTracksService getServiceInstance ();

        // handlePlayTracksDismiss -- tell parent activity that user has dismissed the dialog
        public void handlePlayTracksDismiss ();
    }

    // PlayTracksFragment -- empty constructor is required for DialogFragment
    public PlayTracksFragment () {
	}

    // newInstance -- takes care of creating a new DialogFragment instance
	public static PlayTracksFragment newInstance (String fragmentArgs) {
	    PlayTracksFragment fragment = new PlayTracksFragment ();
        Bundle             bundleArgs = new Bundle ();
        bundleArgs.putString (DIALOG_ARGS, fragmentArgs);
        fragment.setArguments(bundleArgs);
		return fragment;
	}
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public Dialog onCreateDialog (Bundle savedInstanceState) {
        /**
         * onBackPressed is over-ridden to let us know when user selects back
         *     this extra work is needed as a spurious "OnDismiss" is received well after a
         *     screen rotation occurs and we have been resumed.
         */
        Dialog dialog = new Dialog (getActivity(), getTheme()) {
            @Override
            public void onBackPressed () {
                mBackButtonPressed = true;

                super.onBackPressed ();
            }
        };

        // get rid of the empty title bar
        dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);

        // flag user has not pressed back
        mBackButtonPressed = false;
        return dialog;
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_play_tracks, container, false);

		mFormatBuilder = new StringBuilder();
        mFormatter     = new Formatter(mFormatBuilder, Locale.getDefault());
		
		// If we need to know if this is a dialog, we can use something like the code below
		// mIsDialog = getArguments().getString(DIALOG_ARGS).length() > 0;

        // Get a reference to all view items
        mTextViewArtistName       = (TextView)    rootView.findViewById (R.id.play_tracks_artist_name);
        mImageButtonShareTrack    = (ImageButton) rootView.findViewById (R.id.play_tracks_share_track);
        mTextViewTrackName        = (TextView)    rootView.findViewById (R.id.play_tracks_track_name);
        mTextViewAlbumName        = (TextView)    rootView.findViewById (R.id.play_tracks_track_album);
        mImageViewTrackImage      = (ImageView)   rootView.findViewById (R.id.play_tracks_track_image);
        mSeekBarTrackProgress     = (SeekBar)     rootView.findViewById (R.id.play_tracks_track_progress);
        mTextViewDuration         = (TextView)    rootView.findViewById (R.id.play_tracks_duration);
        mTextViewCurrentTime      = (TextView)    rootView.findViewById (R.id.play_tracks_current_time);
        mImageButtonPreviousTrack = (ImageButton) rootView.findViewById (R.id.play_tracks_previous_track);
        mImageButtonPausePlay     = (ImageButton) rootView.findViewById (R.id.play_tracks_pause_play);
        mImageButtonNextTrack     = (ImageButton) rootView.findViewById (R.id.play_tracks_next_track);

        // handles "share" click
        if (mImageButtonShareTrack != null) {
            mImageButtonShareTrack.setOnClickListener(new ImageButton.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (mShareActionIntent != null) {
                        startActivity (Intent.createChooser(mShareActionIntent,
                                getText(R.string.title_share_tracks)));
                    }
                }
            });
        }

        // handle scrub bar change and stop seekbar updates when user is moving the scrub bar
        mSeekBarTrackProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // unfortunately this callback happens on every change, not just user changes...
                if (fromUser) {
                    // handle user change of position, seek to new position in track
                    PlayTracksService service = playTracksService();
                    if (service != null) {
                        service.handleSeekAction(progress);
                    }
                    mUpdateSeekBar = true;
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                mUpdateSeekBar = false;

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                mUpdateSeekBar = true;

            }
        });

        // handles "previous track" click
        mImageButtonPreviousTrack.setOnClickListener(new ImageButton.OnClickListener() {
            @Override
            public void onClick(View view) {
                PlayTracksService service = playTracksService ();
                if (service != null) {
					service.handlePreviousAction();
				}
                
            }
        });

        // handles "pause" or "play" click based on state of the play tracks service
        mImageButtonPausePlay.setOnClickListener(new ImageButton.OnClickListener() {
            @Override
            public void onClick(View view) {
                PlayTracksService service = playTracksService ();
                if (service != null) {
					service.handlePausePlayAction();
				}
      
            }
        });

        // handles "next track" click
        mImageButtonNextTrack.setOnClickListener(new ImageButton.OnClickListener() {
            @Override
            public void onClick(View view) {
                PlayTracksService service = playTracksService ();
                if (service != null) {
                    service.handleNextAction();
                }
            }
        });

		return rootView;
    }

    @Override
	public void onResume () {
        Log.d (LOG_TAG, "onResume");
		// initialize play tracks view connection
        mTopTrackState      = PlayTracksService.PlayTracksState.PLAYTRACKS_INACTIVE;
	    mTopTrackNowPlaying = ListView.INVALID_POSITION;

        // bind to the play tracks service and start monitoring playback
		startServiceMonitor ();
        super.onResume();
	}
	
	@Override
	public void onPause () {
        Log.d(LOG_TAG, "onPause");

        // stop monitoring playback
        stopServiceMonitor();
        super.onPause();
    }

    @Override
    public void onDestroy () {
        Log.d(LOG_TAG, "onDestroy");

        // just in case we didn't see a pause...
        stopServiceMonitor();
        super.onStop();
    }

    @Override
    public void onDismiss (DialogInterface dialog) {
        Log.d(LOG_TAG, "onDismiss");

        // sometimes we get "spurious dismiss" when screen is rotated, so no stop/unbind here

        // tell parent activity that user pressed back, yes this is a hack...
        if ( mBackButtonPressed ) {
            PlayTrackInterface myActivity = (PlayTrackInterface)getActivity();
            if (myActivity != null) {
                myActivity.handlePlayTracksDismiss ();
            }

            mBackButtonPressed = false;
        }
    }

}
