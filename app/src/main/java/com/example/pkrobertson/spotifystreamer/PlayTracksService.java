package com.example.pkrobertson.spotifystreamer;


import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v7.app.NotificationCompat;
import android.util.Log;
import android.util.Patterns;
import android.widget.ListView;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.util.ArrayList;

/**
 * PlayTracksService - service responsible for playing tracks and managing the state of the
 *     media player. This lifetime of this service is managed by TopTracksActivity or
 *     ArtistSearchActivity based on running on handset or tablet
 *
 */
public class PlayTracksService
        extends Service
        implements MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener, MediaPlayer.OnCompletionListener {

    private final String LOG_TAG = PlayTracksService.class.getSimpleName();

    public static final int    SERVICE_ID       = 101;

    // define the different intents handled by the play tracks service
    public static final String ACTION_START     = "com.example.pkrobertson.playtracksservice.start";
    public static final String ACTION_STOP      = "com.example.pkrobertson.playtracksservice.stop";
    public static final String ACTION_NEXTTRACK = "com.example.pkrobertson.playtracksservice.next";
    public static final String ACTION_PREVTRACK = "com.example.pkrobertson.playtracksservice.prev";
    public static final String ACTION_PAUSEPLAY = "com.example.pkrobertson.playtracksservice.pauseplay";

    // define how data is passed to the service in the start intent
    public static final String ARRAY_KEY    = "com.example.pkrobertson.playtracksservice.array";
    public static final String POSITION_KEY = "com.example.pkrobertson.playtracksservice.position";

    // playback states presented to the outside world
	public enum PlayTracksState {
	    PLAYTRACKS_INACTIVE,
		PLAYTRACKS_PLAYING,
		PLAYTRACKS_PAUSED,
		PLAYTRACKS_ERROR
		}

	// used to lock Wifi active when actively streaming music
	private static final String WIFI_LOCK = "com.example.pkrobertson.playtracksservice.wifilock";

    // private states used to manage the media player
    private enum InternalState {
		STATE_INITIALIZE,
		STATE_PREPARING,
		STATE_PLAYING, 
		STATE_PAUSED,
		STATE_STOPPED,
		STATE_ERROR
	}

    // media player and wifi lock instances
    private MediaPlayer          mMediaPlayer = null;
    private WifiManager.WifiLock mWifiLock    = null;

    // IBinder used to allow connections to the PlayTracksFragment
	private final IBinder        mBinder      = new PlayTracksServiceBinder();

    // local copy of top tracks being played, the service advances from track to track automatically
    // and also on demand by intents or bind actions
    private ArrayList<TopTrackItem> mTopTrackItems = null;
    private int mTopTrackNowPlaying                = ListView.INVALID_POSITION;
    private InternalState mState                   = InternalState.STATE_INITIALIZE;

    // intents used to handle ongoing notification
    private boolean       mNotificationActive = false;

    private Intent        mNotificationIntent;
    private PendingIntent mNotificationPendingIntent;
    private Intent        mPreviousIntent;
    private PendingIntent mPreviousPendingIntent;
    private Intent        mPausePlayIntent;
    private PendingIntent mPausePlayPendingIntent;
    private Intent        mNextIntent;
    private PendingIntent mNextPendingIntent;

    private NotificationCompat.Builder mBuilder;

    // target used to load picasso image into a bitmap, thanks GitHub!
    private Target target = new Target() {
        @Override
        public void onPrepareLoad (Drawable placeHolder) {
            // I don't think we have to do anything here
        }

        @Override
        public void onBitmapLoaded (Bitmap bitmap, Picasso.LoadedFrom from){
            Log.d (LOG_TAG, "onBitmapLoaded: got image from picasso");
            mBuilder.setLargeIcon (bitmap);
        }

        @Override
        public void onBitmapFailed (Drawable errorDrawable) {
            // don't care, we are already showing a product image
        }
    };

    // loadTopTrackImage -- used to get thumbnail from picasso and update notification
    private void loadTopTrackImage (String imageURL) {
        // make sure the image URL is valid, if not load a default image
        if ((imageURL != null) && Patterns.WEB_URL.matcher(imageURL).matches()) {
            try {
                Picasso.with(this).load(imageURL).into(target);
            } catch (Exception e) {
                Log.v(LOG_TAG, "Picasso call failed" + e.toString());
            }
        }
    }

    // notificationEnabled -- check preferences to see if notifications are enabled
    private boolean notificationEnabled () {
        // get country code from preferences
        Context           context = this;
        SharedPreferences prefs   = PreferenceManager.getDefaultSharedPreferences(this);

        return ( prefs.getBoolean(getString(R.string.pref_notification_key), false) );
    }

    // buildNotification -- builds notification based on current state of the player
    private Notification  buildNotification () {
        // should not happen, but just to be safe...
        if (mTopTrackItems == null) {
            return null;
        }

        TopTrackItem topTrackItem  = mTopTrackItems.get(mTopTrackNowPlaying);
        int          pausePlayIcon;
        String       pausePlayLabel;
        String       contentTitle;
        String       contentText;

        // get/show track name
        contentTitle = getString(R.string.app_name);
        contentText  = topTrackItem.topTrackName;
        // determine if pause or play action is active
        switch (mState) {
            case STATE_INITIALIZE:
            case STATE_PREPARING:
            case STATE_PLAYING:
                pausePlayIcon  = android.R.drawable.ic_media_pause;
                pausePlayLabel = getString(R.string.label_pause);
                break;

            case STATE_ERROR:
                // override contentText to show error happended
                contentText = getString (R.string.error_play_tracks);
                // no break
            default:
                pausePlayIcon  = android.R.drawable.ic_media_play;
                pausePlayLabel = getString(R.string.label_play);
                break;
        }

        // build the notification, save in a state where we can add the icon later
        (mBuilder = new NotificationCompat.Builder(this))
                .setVisibility(Notification.VISIBILITY_PUBLIC)// put notification on lock screen
                .setPriority(Notification.PRIORITY_HIGH) // this makes buttons visible
                .setSmallIcon(R.mipmap.ic_launcher) // small "product icon"
                .addAction(android.R.drawable.ic_media_previous, getString(R.string.label_previous_track), mPreviousPendingIntent)
                .addAction(pausePlayIcon, pausePlayLabel, mPausePlayPendingIntent)
                .addAction(android.R.drawable.ic_media_next, getString(R.string.label_next_track), mNextPendingIntent)
                .setContentTitle(contentTitle)
                .setContentText(contentText)
                .setContentIntent(mNotificationPendingIntent)
                .setOngoing(true);

        // load the image URL as the larger icon
        loadTopTrackImage (topTrackItem.topTrackListImageURL);

        // now package it all up together
        Notification notification = mBuilder.build ();
        return (notification);
    }

    // setupNotification -- set up intents and the first notification when service is started
    private void setupNotification () {
        // set up main intent to launch SpotifyStreamer
        mNotificationIntent = new Intent(this, ArtistSearchActivity.class);
        mNotificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        mNotificationPendingIntent = PendingIntent.getActivity(this, 0, mNotificationIntent, 0);

        // set up previous intent to select previous track
        mPreviousIntent = new Intent(this, PlayTracksService.class);
        mPreviousIntent.setAction(ACTION_PREVTRACK);
        mPreviousPendingIntent = PendingIntent.getService(this, 0, mPreviousIntent, 0);

        // set up pause/play intent to select pause or play
        mPausePlayIntent = new Intent(this, PlayTracksService.class);
        mPausePlayIntent.setAction(ACTION_PAUSEPLAY);
        mPausePlayPendingIntent = PendingIntent.getService(this, 0, mPausePlayIntent, 0);

        // set up previous intent to select previous track
        mNextIntent = new Intent(this, PlayTracksService.class);
        mNextIntent.setAction(ACTION_NEXTTRACK);
        mNextPendingIntent = PendingIntent.getService(this, 0, mNextIntent, 0);

        Notification notification = buildNotification();
        if (notification != null) {
            Log.d (LOG_TAG, "setupNotification: starting notification");
            startForeground(SERVICE_ID, notification);
            mNotificationActive = true;
        }
    }

    // changeInternalState -- used only to update state variable and send to logcat
    private void changeInternalState (InternalState newState) {
        Log.d(LOG_TAG, "Changing state to ==>" + String.valueOf(newState));
        mState = newState;

        if (! mNotificationActive )
            return;


        Notification notification = buildNotification();
        if ( notification != null ) {
            Log.d (LOG_TAG, "changeInternalState: updating notification");
            NotificationManager notificationManager = (NotificationManager)getSystemService(
                    Context.NOTIFICATION_SERVICE);
            notificationManager.notify(SERVICE_ID, notification);
        }
    }

    // playNewTrack -- used to clean up any playback in progress and start a new track
    private void playNewTrack () {
        // null here should not happen, but this handles any race conditions...
        if (mTopTrackItems == null) {
            return;
        }
        TopTrackItem topTrackItem  = mTopTrackItems.get (mTopTrackNowPlaying);
        boolean validAudioURL = true;

        // handle current state, create new or cleanup old...
        switch (mState) {
            case STATE_INITIALIZE:
                mMediaPlayer = new MediaPlayer ();
                mWifiLock = ((WifiManager)getSystemService(Context.WIFI_SERVICE))
                        .createWifiLock (WifiManager.WIFI_MODE_FULL, WIFI_LOCK);
                break;

            case STATE_PREPARING :
			case STATE_PLAYING   :
                if ( mWifiLock.isHeld() ) {
                    mWifiLock.release();
                }
				// no break;
            default              :
			    mMediaPlayer.reset();
                break;
        }

        // set up media player
        mMediaPlayer.setAudioStreamType (AudioManager.STREAM_MUSIC);
        mMediaPlayer.setOnPreparedListener(this);
        mMediaPlayer.setOnCompletionListener(this);
        mMediaPlayer.setOnErrorListener(this);
        mMediaPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);

        // double check that we have a valid URL, if not we'll just go to an error state
        if ( (topTrackItem.topTrackAudioURL != null) &&
                Patterns.WEB_URL.matcher(topTrackItem.topTrackAudioURL).matches() ) {
            try {
                mMediaPlayer.setDataSource(topTrackItem.topTrackAudioURL);
            } catch (Exception e) {
                Log.e(LOG_TAG, "Spotify Audio URL is not valid" + e.toString());
                validAudioURL = false;
            }
        } else {
            validAudioURL = false;
        }

        // start preparing audio or go to error state if the URL was not valid
        if ( validAudioURL ) {
            mWifiLock.acquire();
            mMediaPlayer.prepareAsync();
            changeInternalState(InternalState.STATE_PREPARING);
        } else {
            changeInternalState(InternalState.STATE_ERROR);
        }
	}

    // pauseTrack -- used to pause playback
    private void pauseTrack () {
        switch (mState) {
            case STATE_PLAYING:
                mMediaPlayer.pause();
                if ( mWifiLock.isHeld() ) {
                    mWifiLock.release();
                }
                changeInternalState (InternalState.STATE_PAUSED);
                break;

            default:
                Log.e(LOG_TAG, "pauseTrack(): Invalid State!");
                break;
        }
    }

    // resumeTrack -- resumes playback after a pause
	private void resumeTrack () {
        switch (mState) {
            case STATE_PAUSED:
                mWifiLock.acquire();
                mMediaPlayer.start();
                changeInternalState (InternalState.STATE_PLAYING);
                break;

            default:
                Log.e(LOG_TAG, "resumeTrack(): Invalid State!");
                break;
        }
    }

    // PlayTracksService -- default constructor
    public PlayTracksService () {
    }

    // PlayTracksServiceBinder -- used to handle connections to the play tracks fragment
    public class PlayTracksServiceBinder extends Binder {
        PlayTracksService getService () {
            return PlayTracksService.this;
        }
    }

    @Override
    public IBinder onBind (Intent arguments) {
        Log.d(LOG_TAG, "OnBind");
        return mBinder;
    }

    @Override
    public boolean onUnbind (Intent intent) {
        Log.d(LOG_TAG, "onUnbind");

        // automatic rebind not allowed
        return true;
    }

    // isNowPlaying -- static helper function to see if a track is playing
    public static boolean isNowPlaying (PlayTracksService serviceInstance) {
        if (serviceInstance == null) {
            return false;
        }
        switch (serviceInstance.mState) {
            case STATE_PREPARING:
            case STATE_PLAYING  :
            case STATE_PAUSED   :
                return true;

            default:
                return false;
        }
    }

    // getState -- return external state of the service
    public PlayTracksState getState () {
		switch (mState) {
			case STATE_PREPARING :
			case STATE_PLAYING   :
			    return PlayTracksState.PLAYTRACKS_PLAYING;
			
			case STATE_PAUSED    :
			    return PlayTracksState.PLAYTRACKS_PAUSED;
			
			case STATE_ERROR     :
			    return PlayTracksState.PLAYTRACKS_ERROR;
				
			default              :
			    return PlayTracksState.PLAYTRACKS_INACTIVE;
		}
	}

    // getTrackNowPlaying -- return current track that is playing or paused
	public int getTrackNowPlaying () {
		return mTopTrackNowPlaying;
	}

    // getTrackItem -- return track details to allow showing artist/track information
	public TopTrackItem getTrackItem () {
		if ( (mTopTrackItems != null) && (mTopTrackNowPlaying != ListView.INVALID_POSITION) ) {
			return mTopTrackItems.get(mTopTrackNowPlaying);
		}
		return null;
	}

    // getDuration -- get duration of the currently playing track
	public int getDuration () {
		switch (mState) {
			case STATE_PLAYING   :
			case STATE_PAUSED    :
			    return mMediaPlayer.getDuration ();
			    
			default              :
			    return 0;
		}
	}

    // getCurrentPosition -- gets current position of the currently playing track
	public int getCurrentPosition () {
		switch (mState) {
			case STATE_PLAYING   :
			case STATE_PAUSED    :
			    return mMediaPlayer.getCurrentPosition();
			    
			default              :
			    return 0;
		}
	}

    // handleSeekAction -- moves to a new position in a playing or paused track
    public void handleSeekAction (int newPosition) {
        switch (mState) {
            case STATE_PLAYING   :
            case STATE_PAUSED    :
                mMediaPlayer.seekTo (newPosition);
                break;

            default              :
                break;
        }
    }

    // handlePreviousAction -- handles previous track request (moves to last track if on first one)
	public void handlePreviousAction () {
		// make sure we have tracks to play
		if (getTrackItem () != null) {
			if (--mTopTrackNowPlaying < 0) {
                mTopTrackNowPlaying = mTopTrackItems.size() - 1;
		    }
		    playNewTrack ();
		}		
	}

    // handleNextAction -- handles next track request (moves to first track if on last one)
	public void handleNextAction () {
		// make sure we have tracks to play
		if (getTrackItem () != null) {
			if (++mTopTrackNowPlaying >= mTopTrackItems.size()) {
                mTopTrackNowPlaying = 0;
		    }
		    playNewTrack ();
		}		
	}

    // handlePausePlayAction -- handles pause or play request based on state of playback
	public void handlePausePlayAction () {
		switch (mState) {
			case STATE_PLAYING   :
			    pauseTrack ();
				break;
				
			case STATE_PAUSED    :
			    resumeTrack ();
				break;
			    
			default              :
			    break;
		}
	}
	
	
    // onStartCommand -- handle incoming intent, for now we're only receiving "ACTION_START"
    public int onStartCommand (Intent intent, int flags, int startid) {
        // TODO: android suggests we need to properly handle media focus change events
        if (intent.getAction().equals(ACTION_START)) {
            Log.i(LOG_TAG, "Received ACTION_START Intent");
            if (intent.hasExtra(ARRAY_KEY) && intent.hasExtra(POSITION_KEY)) {
                mTopTrackItems      = intent.getExtras().getParcelableArrayList(ARRAY_KEY);
                mTopTrackNowPlaying = intent.getExtras().getInt(POSITION_KEY);

                Log.d(LOG_TAG, "got top tracks ==> " + mTopTrackItems.toString());
                Log.d(LOG_TAG, "got track position ==> " + String.valueOf(mTopTrackNowPlaying));
            }

            // start playback
            playNewTrack();

            // setup as foreground service when notifications are enabled
            if ( notificationEnabled() ) {
                setupNotification();
            }
        } else if (intent.getAction().equals(ACTION_PREVTRACK)) {
            Log.i(LOG_TAG, "Clicked Previous");
            handlePreviousAction ();
        } else if (intent.getAction().equals(ACTION_PAUSEPLAY)) {
            Log.i(LOG_TAG, "Clicked Pause/Play");
            handlePausePlayAction ();
        } else if (intent.getAction().equals(ACTION_NEXTTRACK)) {
            Log.i(LOG_TAG, "Clicked Next");
            handleNextAction ();
        } else if (intent.getAction().equals(ACTION_STOP)) {
            Log.i(LOG_TAG, "Received Stop Foreground Intent");
            stopForeground(true);
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy () {
        Log.d(LOG_TAG, "onDestroy");
        if ( (mWifiLock != null) && (mWifiLock.isHeld()) ) {
            mWifiLock.release();
        }
        if (mMediaPlayer != null) {
            mMediaPlayer.release ();
            mMediaPlayer = null;
        }
        super.onDestroy ();
    }

    @Override
    public void onPrepared (MediaPlayer player) {
        // time to start playing...
        player.start();
		changeInternalState(InternalState.STATE_PLAYING);
    }
	
	@Override
    public void onCompletion (MediaPlayer player) {
        // keep cycling through tracks until user pauses and exits now playing screen
		if (mTopTrackNowPlaying >= (mTopTrackItems.size()) - 1) {
			mTopTrackNowPlaying = 0;
		} else {
		    ++mTopTrackNowPlaying;
		}
        playNewTrack();
    }

    @Override
    public boolean onError (MediaPlayer player, int what, int extra) {
        Log.d(LOG_TAG, "onError");
        // handle error and reset before trying again
		if ( (mWifiLock != null) && (mWifiLock.isHeld()) ) {
            mWifiLock.release();
        }
		player.reset ();
        changeInternalState(InternalState.STATE_ERROR);
        return true;
    }
}
