package com.example.pkrobertson.spotifystreamer;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

/**
 * PlayTracksActivity - play tracks activity for the SpotifyStreamer app and is a container that holds
 *     the PlayTracksFragment
 *
 */

public class PlayTracksActivity
        extends AppCompatActivity
        implements PlayTracksFragment.PlayTrackInterface {

    private final String LOG_TAG = PlayTracksActivity.class.getSimpleName();

    // arguments passed to the play tracks fragment, in this case the fragment is embedded
    private final String DUMMY_ARGS = "activity";

    // user to connect to play tracks service
    private ServiceConnection mServiceConnection = null;
    private PlayTracksService mPlayTracksService = null;

    // bindPlayTracksService -- get a connection to the play tracks service
    private void bindPlayTracksService () {
        if (mServiceConnection == null) {
            mServiceConnection = new ServiceConnection () {
                @Override
                public void onServiceConnected (ComponentName name, IBinder binder) {
                    mPlayTracksService = ((PlayTracksService.PlayTracksServiceBinder) binder).getService ();
                }

                @Override
                public void onServiceDisconnected (ComponentName name) {
                    mPlayTracksService = null;
                }
            };
            bindService(new Intent(this, PlayTracksService.class),
                    mServiceConnection, Context.BIND_AUTO_CREATE);
        }
    }

    // unbindPlayTracksService -- drop the connection when paused/destroyed
    private void unbindPlayTracksService () {
        if (mServiceConnection != null) {
            unbindService(mServiceConnection);
            mServiceConnection = null;
            mPlayTracksService = null;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_play_tracks);

        // embed the play tracks dialog fragment within the container
        if (savedInstanceState == null) {
            PlayTracksFragment playTracksFragment = PlayTracksFragment.newInstance(DUMMY_ARGS);
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container_play_tracks, playTracksFragment)
                    .commit();
        }
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
        getMenuInflater().inflate(R.menu.menu_play_tracks, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        // Launch the settings activity when selected
        if (id == R.id.action_share_track) {
            // update share intent based on current track
            if ( mPlayTracksService != null) {
                TopTrackItem topTrackItem = mPlayTracksService.getTrackItem();
                if ( topTrackItem != null ) {
                    Intent shareActionIntent = new Intent(Intent.ACTION_SEND);
                    shareActionIntent.setType("text/plain");
                    shareActionIntent.putExtra(Intent.EXTRA_TEXT,
                            topTrackItem.topTrackAudioURL + getString(R.string.app_hash_tag));
                    startActivity(Intent.createChooser(shareActionIntent,
                            getText(R.string.title_share_tracks)));
                    return true;
                }
            }

        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed () {
        Log.d(LOG_TAG, "onBackPressed");

        // stop playback if back pressed and we're not actively playing a track
        if ((mPlayTracksService != null) &&
                (mPlayTracksService.getState() != PlayTracksService.PlayTracksState.PLAYTRACKS_PLAYING)) {
            unbindPlayTracksService();
            stopService(new Intent(this, PlayTracksService.class));
        }

        super.onBackPressed();
    }

    // getServiceInstance -- used to pass along the service reference to PlayTracksFragment
    @Override
    public PlayTracksService getServiceInstance () {
        return (mPlayTracksService);
    }

    // handlePlayTracksDismiss -- used to close the play tracks dialog, should not happen when
    //     PlayTracksFragment is managed by PlayTracksActivity
    @Override
    public void handlePlayTracksDismiss () {
        // should not ever get called...
        if ((mPlayTracksService != null) &&
                (mPlayTracksService.getState() != PlayTracksService.PlayTracksState.PLAYTRACKS_PLAYING)) {
            unbindPlayTracksService();
            stopService(new Intent(this, PlayTracksService.class));
        }
    }
}
