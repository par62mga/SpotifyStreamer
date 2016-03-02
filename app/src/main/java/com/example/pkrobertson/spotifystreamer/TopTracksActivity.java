package com.example.pkrobertson.spotifystreamer;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import java.util.ArrayList;

/**
 * TopTracksActivity - top tracks activity for the SpotifyStreamer app and is a container that holds
 *     the TopTracksFragment
 *
 */

public class TopTracksActivity
        extends AppCompatActivity
        implements TopTracksFragment.SelectTrackInterface {

    private final String LOG_TAG = TopTracksActivity.class.getSimpleName();

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
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_top_tracks);

        // get reference to the intent that launched the TopTracksActivity
        Intent intent = this.getIntent();

        // Get artist name and Spotify ID from the intent
        if ((intent != null) && intent.hasExtra(Intent.EXTRA_TITLE) && intent.hasExtra(Intent.EXTRA_TEXT)) {
            ArtistItem artist = new ArtistItem(
                    intent.getStringExtra(Intent.EXTRA_TITLE),
                    intent.getStringExtra(Intent.EXTRA_TEXT),
                    "");

            Log.d(LOG_TAG, "got artist ==> " + artist.toString());

            // put artist name under the action bar title
            ActionBar actionBar = ((AppCompatActivity) this).getSupportActionBar();
            actionBar.setSubtitle(artist.artistName);

            TopTracksFragment topTracksFragment =
                    (TopTracksFragment)getSupportFragmentManager().findFragmentById(R.id.fragment_top_tracks);
            topTracksFragment.showArtistTopTracks(artist);
        }
    }

    @Override
    protected void onResume () {
        Log.d(LOG_TAG, "onResume");
        // stop PlayTracksService/music playback when PlayTracksActivity is no longer running
        // stopService(new Intent(this, PlayTracksService.class));
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
        getMenuInflater().inflate(R.menu.menu_top_tracks, menu);

        // get copy of now playing menu item so we can turn on/off visibility
        mNowPlaying = menu.findItem (R.id.action_now_playing);

        // set visibility based on state of service connection
        manageNowPlaying();

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // handle the now playing selection...
        int id = item.getItemId();

        // Launch the settings activity when selected
       if (id == R.id.action_now_playing) {
            if ( mPlayTracksService != null ) {
                // launch PlayTracksActivity
                Intent intent = new Intent(this, PlayTracksActivity.class);

                // start the activity
                startActivity(intent);
            }
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * onTrackSelected - Implementation of TopTracksFragment.SelectTrackInterface to handle
     *     launching the play tracks activity on the handset.
     *
     *     NOTE: The artist search tracks activity takes care of launching the play tracks
     *     dialog when running on a tablet.
     */
    @Override
    public void onTrackSelected (ArrayList<TopTrackItem> topTrackItems, int topTrackSelection) {
        Log.d(LOG_TAG, "onTrackSelected ==>" + topTrackItems.toString());

        // launch PlayTracksService intent and pass in the tracks and selected track
        Intent startIntent = new Intent(this, PlayTracksService.class);
        startIntent.setAction(PlayTracksService.ACTION_START);
        startIntent.putExtra(PlayTracksService.ARRAY_KEY, topTrackItems);
        startIntent.putExtra(PlayTracksService.POSITION_KEY, topTrackSelection);
        startService(startIntent);

        // launch PlayTracksActivity
        Intent intent = new Intent(this, PlayTracksActivity.class);

        // start the activity
        startActivity(intent);

        // show now playing even though we are about to get paused...
        mNowPlaying.setVisible (true);
    }
}
