package com.example.pkrobertson.spotifystreamer;

import android.os.AsyncTask;
import android.util.Log;
import android.widget.TextView;

import java.util.Hashtable;
import java.util.Map;

import kaaes.spotify.webapi.android.SpotifyApi;
import kaaes.spotify.webapi.android.SpotifyService;
import kaaes.spotify.webapi.android.models.Image;
import kaaes.spotify.webapi.android.models.Track;
import kaaes.spotify.webapi.android.models.Tracks;

/**
 * TopTracksSearchTask - an async task responsible for searching for top tracks using the
 *     Spotify web service fragment
 *
 *     this task takes care of populating the top tracks list view with data returned from Spotify
 *
 */
public class TopTracksSearchTask extends AsyncTask<String, Void, String> {

    private final String LOG_TAG = TopTracksSearchTask.class.getSimpleName();

    // hold references to the top tracks list view adapter and the search status text view
    private TopTracksListViewAdapter mTopTracksAdapter;
    private TextView                 mSearchStatus;
    private String                   mSearchCountryCode;

    // holds artist ID that we are searching for
    private String                   mTrackSearchString;

    // holds Spotify search result
    private Tracks                   mSearchResult;

    public TopTracksSearchTask(TopTracksListViewAdapter topTracksAdapter,
                               TextView searchStatus,
                               String   searchCountryCode) {
        super ();

        // save references for later use
        this.mTopTracksAdapter  = topTracksAdapter;
        this.mSearchStatus      = searchStatus;
        this.mSearchCountryCode = searchCountryCode;
    }

    @Override
    protected String doInBackground (String... params) {

        String result = null;

        // See if artist ID was passed as parameter, if no artist, no search is needed.
        if (params.length == 0) {
            return result;
        }

        // log parameters...
        mTrackSearchString = params[0];
        Log.d(LOG_TAG, "artist spotify ID = " + params[0]);

        // get reference to Spotify API handler and service
        SpotifyApi     api     = new SpotifyApi();
        SpotifyService spotify = api.getService();

        // for now hardcode "country" to US
        Map<String, Object> options = new Hashtable<String, Object>();
        options.put("country", mSearchCountryCode);


        try {
            // search for tracks based on artist ID and country option
            mSearchResult = spotify.getArtistTopTrack(params[0], options);

            // did we find any tracks?
            if (mSearchResult.tracks.isEmpty() == false) {
                result = "found at least one track";
            }
        } catch (Exception e) {
            Log.v (LOG_TAG, "Spotify call failed" + e.toString());
        }

        // Return null only if no artist results found
        return result;
    }

    @Override
    protected void onPostExecute(String result) {

        // did we find any tracks?
        if (result == null) {
            // tell user no tracks found
            // no need to clear adapter as we always create a new one for each search...
            mSearchStatus.setText (String.format(mSearchStatus.getResources().getString(R.string.track_not_found), mTrackSearchString, mSearchCountryCode));
        } else {
            mSearchStatus.setText ("");

            // fill in new data...
            ;
            for (Track track : mSearchResult.tracks) {
                // get track and album name directly from the search results
                String topTrackArtistName   = track.artists.get(0).name;
                String topTrackName         = track.name;
                String topTrackAlbumName    = track.album.name;
                String topTrackListImageURL = null;
                String topTrackPlayImageURL = null;
                String topTrackAudioURL     = track.preview_url;

                // find smallest artist image for track view and the next size up for play view
                int imageSize = 0;
                for (Image image : track.album.images) {
                    if ( (imageSize == 0) || (imageSize > image.height) ) {
                        imageSize = image.height;
                        topTrackPlayImageURL = topTrackListImageURL;
                        topTrackListImageURL = image.url;
                    }
                }

                // create new top tracks item based on search results
                TopTrackItem topTrackItem = new TopTrackItem (
                        topTrackArtistName, topTrackName, topTrackAlbumName,
                        topTrackListImageURL, topTrackPlayImageURL, topTrackAudioURL);
                Log.d(LOG_TAG, "track = " + topTrackItem.toString());

                // add the top tracks information to the top tracks adapter
                mTopTracksAdapter.add(topTrackItem);
            }

        // This does nothing as far as I can tell...remove?
        mTopTracksAdapter.notifyDataSetChanged();
        }
    }
}
