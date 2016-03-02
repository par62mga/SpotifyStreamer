package com.example.pkrobertson.spotifystreamer;

import android.os.AsyncTask;
import android.util.Log;
import android.widget.TextView;

import kaaes.spotify.webapi.android.SpotifyApi;
import kaaes.spotify.webapi.android.SpotifyService;
import kaaes.spotify.webapi.android.models.Artist;
import kaaes.spotify.webapi.android.models.ArtistsPager;
import kaaes.spotify.webapi.android.models.Image;

/**
 * ArtistSearchTask - an async task responsible for searching for artists using the
 *     Spotify web service fragment
 *
 *     this task takes care of populating the artist list view with data returned from Spotify
 *
 */
public class ArtistSearchTask extends AsyncTask<String, Void, String> {

    private final String LOG_TAG = ArtistSearchTask.class.getSimpleName();

    // hold references to the artist list view adapter and the search status text view
    private ArtistListViewAdapter artistAdapter;
    private TextView              searchStatus;

    // holds artist name that we are searching for
    private String                artistSearchString;

    // holds Spotify search result
    private ArtistsPager          searchResult;

    public ArtistSearchTask (ArtistListViewAdapter artistAdapter, TextView searchStatus) {
        super ();

        // save references for later use
        this.artistAdapter = artistAdapter;
        this.searchStatus  = searchStatus;
    }

    @Override
    protected String doInBackground (String... params) {

        String result = null;

        // See if artist name was entered, if no artist, no search is needed.
        if (params.length == 0) {
            return result;
        }

        // log parameters...
        artistSearchString = params[0];
        Log.d(LOG_TAG, "artist name = " + artistSearchString);

        // get reference to Spotify API handler and service
        SpotifyApi api = new SpotifyApi();
        SpotifyService spotify = api.getService();

        try {
            // search for artist based on the artist name
            searchResult = spotify.searchArtists(artistSearchString);

            // did we find any artists?
            if (searchResult.artists.items.isEmpty() == false) {
                result = "found at least one artist";
            }
        } catch (Exception e) {
            Log.v (LOG_TAG, "Spotify call failed" + e.toString());
        }

        // Return null only if no artist results found
        return result;
    }

    @Override
    protected void onPostExecute(String result) {

        // did we find any artists?
        if (result == null) {
            // tell user artist not found
            // no need to clear adapter as we always create a new one for each search...
            searchStatus.setText (String.format(searchStatus.getResources().getString(R.string.artist_not_found), artistSearchString));
        } else {
            searchStatus.setText ("");

            // fill in new data...
            for (Artist artist : searchResult.artists.items) {
                // get artist name and ID directly from the search results
                String     artistName = artist.name;
                String     artistSpotifyId = artist.id;
                String     artistImageURL = null;

                // find smallest artist image for now...
                // TODO: during Phase 2 we should look for "best fit"
                int imageSize = 0;
                for (Image image : artist.images) {
                    if ( (imageSize == 0) || (imageSize > image.height) ) {
                        imageSize = image.height;
                        artistImageURL = image.url;
                    }
                }

                // create new artist item based on search results
                ArtistItem artistItem = new ArtistItem (artistName, artistSpotifyId, artistImageURL);
                Log.d (LOG_TAG, "artist = " + artistItem.toString());

                // add the artist information to the artist adapter
                artistAdapter.add (artistItem);
            }

        // This does nothing as far as I can tell...remove?
        artistAdapter.notifyDataSetChanged();
        }
    }
}
