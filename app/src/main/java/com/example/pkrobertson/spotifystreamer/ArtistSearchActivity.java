package com.example.pkrobertson.spotifystreamer;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import java.util.ArrayList;

/**
 * ArtistSearchActivity - main activity for the SpotifyStreamer app and is a container that holds
 *     the ArtistSearchFragment in small screen/phone mode
 *
 *     In tablet mode, this activity manages the ArtistSearchFragment, TopTracksFragment and also
 *     manages the PlayTracksFragment as a dialog when tracks are playing in the foreground
 *
 */

public class ArtistSearchActivity extends AppCompatActivity
        implements ArtistSearchFragment.SelectArtistInterface,
            TopTracksFragment.SelectTrackInterface, PlayTracksFragment.PlayTrackInterface {

    private final String LOG_TAG    = ArtistSearchActivity.class.getSimpleName();

    // used to identify the PlayTracksFragment when on the backstack
    private final String DIALOG_TAG = "SpotifyDialog";

    // we're running in tablet mode if true
    private boolean mTwoPaneView;

    // reference to now playing menu item to hide/show based on service state
    private MenuItem mNowPlaying;

    // user to connect to play tracks service
    private ServiceConnection mServiceConnection = null;
    private PlayTracksService mPlayTracksService = null;

    // manage state of "now playing" icon
    private void manageNowPlaying () {
        // nothing to do if menu has not been inflated yet...
        if (mNowPlaying == null) {
            return;
        }

        boolean result = PlayTracksService.isNowPlaying(mPlayTracksService);
        Log.d(LOG_TAG, "manageNowPlaying ==>" + String.valueOf(result));
        mNowPlaying.setVisible(result);
    }

    // closePlayTracksDialog -- used to remove the playtracks dialog from the backstack
    private boolean closePlayTracksDialog () {
        if (mTwoPaneView) {
            FragmentManager fm = getSupportFragmentManager();
            Fragment oldDialog = fm.findFragmentByTag(DIALOG_TAG);
            if (oldDialog != null) {
                Log.d(LOG_TAG, "handlePlayTracksDismiss: removing dialog fragment");
                fm.popBackStack();
                return true;
            }
        }
        return false;
    }

    // launchPlayTracksDialog -- used to launch the playtracks dialog in 2-panel tablet mode
    private void launchPlayTracksDialog () {
        // normally the dialog fragment has already been removed, but this takes care of
        // user dismissing dialog without pressing back...
        closePlayTracksDialog();

        // launch PlayTracksFragment as a dialog and add to backstack
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.addToBackStack(null);
        PlayTracksFragment playTracksDialog = PlayTracksFragment.newInstance(DIALOG_TAG);
        playTracksDialog.show(ft, DIALOG_TAG);
    }


    // bindPlayTracksService -- get a connection to the play tracks service
    private void bindPlayTracksService () {
        Log.d (LOG_TAG, "entering bindPlayTracksService");
        if (mServiceConnection == null) {
            mServiceConnection = new ServiceConnection () {
                @Override
                public void onServiceConnected (ComponentName name, IBinder binder) {
                    Log.d (LOG_TAG, "onServiceConnected");
                    mPlayTracksService = ((PlayTracksService.PlayTracksServiceBinder) binder).getService ();
                    manageNowPlaying();
                }

                @Override
                public void onServiceDisconnected (ComponentName name) {
                    Log.d (LOG_TAG, "onServiceDisconnected");
                    closePlayTracksDialog ();
                    mPlayTracksService = null;
                    manageNowPlaying();
                }
            };
            Log.d (LOG_TAG, "calling bindService");
            bindService(new Intent(this, PlayTracksService.class),
                    mServiceConnection, Context.BIND_AUTO_CREATE);
        }

        // make sure now playing is visible if we are already connected
        Log.d (LOG_TAG, "bind: service connection was null, showing now playing icon");
        manageNowPlaying();
    }

    // unbindPlayTracksService -- drop the connection when paused/destroyed
    private void unbindPlayTracksService () {
        Log.d (LOG_TAG, "entering unbindPlayTracksService");
        if (mServiceConnection != null) {
            Log.d (LOG_TAG, "unbind: service connection was not null, turning off now playing icon");
            unbindService(mServiceConnection);
            mServiceConnection = null;
            mPlayTracksService = null;
        }

        // turn off now playing icon
        manageNowPlaying();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(LOG_TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_artist_search);

        // see if top tracks container is present, if so we're running 2-pane mode
        mTwoPaneView    = (findViewById(R.id.fragment_top_tracks) != null);

        Log.d(LOG_TAG, "mTwoPaneView ==> " + (mTwoPaneView ? "TRUE" : "FALSE"));
    }

    @Override
    protected void onResume () {
        Log.d(LOG_TAG, "onResume");
        bindPlayTracksService();
        super.onResume();
    }

    @Override
    protected void onPause () {
        Log.d(LOG_TAG, "onPause");
        unbindPlayTracksService();
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_artist_search, menu);

        // get copy of now playing menu item so we can turn on/off visibility
        mNowPlaying = menu.findItem (R.id.action_now_playing);

        // set visibility based on state of service connection
        manageNowPlaying();

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        // Launch the settings activity when selected
        if (id == R.id.action_settings) {
            startActivity (new Intent(this, SettingsActivity.class));
            return true;
        } else if (id == R.id.action_now_playing) {
            // user clicked on now playing icon, launch play tracks activity or dialog
            if ( mPlayTracksService != null ) {
                if (mTwoPaneView) {
                    // launch PlayTracksFragment as a dialog
                    launchPlayTracksDialog();
                } else {
                    // launch PlayTracksActivity
                    Intent intent = new Intent(this, PlayTracksActivity.class);

                    // start the activity
                    startActivity(intent);
                }
            }
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed () {
        Log.d (LOG_TAG, "onBackPressed");

        // user has pressed back, let's see if the play tracks dialog is on the back stack...
        if (closePlayTracksDialog()) {
            // stop playback if back pressed and we're not actively playing a track
            if ( (mPlayTracksService != null) &&
                    (mPlayTracksService.getState() != PlayTracksService.PlayTracksState.PLAYTRACKS_PLAYING) ) {
                unbindPlayTracksService();
                stopService(new Intent(this, PlayTracksService.class));
            }
            return;
        }

		// no dialog/streaming music, handle back button by clearing top tracks selection if active
        TopTracksFragment topTracksFragment =
                (TopTracksFragment)getSupportFragmentManager().findFragmentById(R.id.fragment_top_tracks);
        ArtistSearchFragment artistSearchFragment =
                (ArtistSearchFragment)getSupportFragmentManager().findFragmentById(R.id.fragment_artist_search);
        if ( (topTracksFragment != null) && (topTracksFragment.clearArtistTopTracks()) ) {
            // top tracks cleared, remove artist selection from the subtitle
            ActionBar actionBar = ((AppCompatActivity) this).getSupportActionBar();
            actionBar.setSubtitle("");

            // update artist adapter to show no selected artist
            artistSearchFragment.clearArtistSelection ();
            return;
        }

        // no top tracks preset, clear artist list if active
        if (artistSearchFragment.clearArtistList()) {
            return;
        }

        // artist search text was already cleared, allow back to exit the activity
        super.onBackPressed();
    }

    /**
     * onArtistSelected - Implementation of ArtistSearchFragment.SelectArtistInterface to handle
     *     show top tracks in 2-pane mode or by launching the top tracks activity
     */
    @Override
     public void onArtistSelected (ArtistItem artist) {
        Log.d (LOG_TAG, "onArtistSelected ==>" + artist.toString());

        if (mTwoPaneView) {
            // put artist name under the action bar title
            ActionBar actionBar = ((AppCompatActivity) this).getSupportActionBar();
            actionBar.setSubtitle(artist.artistName);

            // show artist top tracks in the top tracks pane
            TopTracksFragment topTracksFragment =
                    (TopTracksFragment)getSupportFragmentManager().findFragmentById(R.id.fragment_top_tracks);
            if (topTracksFragment != null)
                topTracksFragment.showArtistTopTracks(artist);
        } else {
            // launch TopTracksActivity intent and pass in the artist name and artist Spotify ID
            Intent intent = new Intent(this, TopTracksActivity.class);
            intent.putExtra(Intent.EXTRA_TITLE, artist.artistName);
            intent.putExtra(Intent.EXTRA_TEXT, artist.artistSpotifyId);

            // start the activity
            startActivity(intent);
        }
    }


    /**
     * onTrackSelected - Implementation of TopTracksFragment.SelectTrackInterface to handle bringing
     *     up the play tracks dialog.
     *
     *     NOTE: The top tracks activity takes care of launching the play tracks activity when
     *     running on a handset.
     */
    @Override
    public void onTrackSelected (ArrayList<TopTrackItem> topTrackItems, int topTrackSelection) {
        Log.d (LOG_TAG, "onTrackSelected ==>" + topTrackItems.toString());

        // no need to check for "mTwoPaneView" as onTracksSelected is only called in two panes...
        // launch PlayTracksService intent and pass in the tracks and selected track
        Intent startIntent = new Intent(this, PlayTracksService.class);
        startIntent.setAction(PlayTracksService.ACTION_START);
        startIntent.putExtra(PlayTracksService.ARRAY_KEY, topTrackItems);
        startIntent.putExtra(PlayTracksService.POSITION_KEY, topTrackSelection);
        startService(startIntent);

        // also bind to service if not already bound...
        bindPlayTracksService();

        // open up the play tracks dialog
        launchPlayTracksDialog();

        // sometimes there is a race when starting the service, show now playing just to be sure
        mNowPlaying.setVisible(true);
    }

    // getServiceInstance -- used to pass along the service reference to PlayTracksFragment
    @Override
    public PlayTracksService getServiceInstance () {
        return (mPlayTracksService);
    }

    // handlePlayTracksDismiss -- used to close the play tracks dialog when user dismisses playback
    @Override
    public void handlePlayTracksDismiss () {
        // remove dialog from the backstack
        closePlayTracksDialog();

        // stop the service if music is not playing when dismissed
        if ((mPlayTracksService != null) &&
                (mPlayTracksService.getState() != PlayTracksService.PlayTracksState.PLAYTRACKS_PLAYING)) {
            unbindPlayTracksService();
            stopService(new Intent(this, PlayTracksService.class));
        }
        manageNowPlaying();
    }
}
