package com.example.pkrobertson.spotifystreamer;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * TopTracksFragment - a fragment that holds the "fragment_top_tracks" view and
 *     handles retrieving the top tracks for a given artist
 *
 *     the lifetime of this fragment is managed by TopTracksActivity
 *
 */

public class TopTracksFragment extends Fragment {

    private final String LOG_TAG = TopTracksFragment.class.getSimpleName();

    // used to store/retrieve TopTrackItem context on saveInstanceState/onCreate
    private final String TRACK_ARRAY_KEY    = "track_items";
    private final String TRACK_POSITION_KEY = "track_selection";
    private  ArrayList<TopTrackItem> topTrackArray;

    // store references to the top tracks list and status text views
    private ListView topTracksListView;
    private TextView topTracksListStatus;


    // topTracksAdapter is used to populate and manage topTracksListView
    private TopTracksListViewAdapter topTracksAdapter;
    private int topTracksListViewPosition = ListView.INVALID_POSITION;

    public interface SelectTrackInterface {
        /*
         * Handle track selected by launching service and optionally a new activity
         */
        public void onTrackSelected (ArrayList<TopTrackItem> topTrackItems, int topTrackSelection);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // assume we do not have a saved state
        topTrackArray             = null;
        topTracksListViewPosition = ListView.INVALID_POSITION;

        // restore array of TopTrackList items from savedInstanceState
        // the array is then used to initialize the list view adapter during OnCreateView
        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(TRACK_ARRAY_KEY)) {
                topTrackArray = savedInstanceState.getParcelableArrayList(TRACK_ARRAY_KEY);
            }
            if (savedInstanceState.containsKey(TRACK_POSITION_KEY)) {
                topTracksListViewPosition = savedInstanceState.getInt(TRACK_POSITION_KEY);
            }
        }
    }

    @Override
    public void onSaveInstanceState (Bundle outInstanceState) {
        // if we have an active artistAdapter, retrieve and store array of ArtistList items
        if (topTracksAdapter != null) {
            // create array from contents of the adapter
            topTrackArray = topTracksAdapter.getTopTrackItems();

            // save array to the bundle
            outInstanceState.putParcelableArrayList(TRACK_ARRAY_KEY, topTrackArray);

            // save selected position to the bundle
            outInstanceState.putInt(TRACK_POSITION_KEY, topTracksListViewPosition);
        }
        super.onSaveInstanceState(outInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_top_tracks, container, false);

        // Get a reference to the ListView, and attach this adapter to it.
        topTracksListView = (ListView) rootView.findViewById(R.id.top_tracks_list_view);

        // Get and save reference to TextView and use to show progress updates and results
        topTracksListStatus = (TextView) rootView.findViewById(R.id.top_tracks_status);

        if (topTrackArray == null) {
            topTrackArray = new ArrayList<TopTrackItem>();
        }

        // create adapter and populate with empty or saved TopTrackItem information
        topTracksAdapter = new TopTracksListViewAdapter(getActivity(),
                R.layout.top_tracks_list_item,
                topTrackArray);

        // assign the adapter to the list view
        topTracksListView.setAdapter(topTracksAdapter);
        if (topTracksListViewPosition != ListView.INVALID_POSITION) {
            Log.d(LOG_TAG, "onCreatView list view position ==> " + topTracksListViewPosition);
            // topTracksListView.smoothScrollToPosition(artistListViewPosition);
            topTracksListView.setSelectionFromTop(topTracksListViewPosition, 0);
        }

        // Set up a click listener for the artist list
        topTracksListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
                // get copy of top tracks and pass along to the track selected handler
                // the track array is needed to allow moving to pervious and next track
                topTrackArray = topTracksAdapter.getTopTrackItems();

                ((SelectTrackInterface)getActivity()).onTrackSelected(topTrackArray, position);
                topTracksListViewPosition = position;
            }
        });
        return rootView;
    }

    /*
     * showArtistTopTracks -- used by ArtistSearchActivity when running in 2-pane mode to bring
     *     up tracks based on a new artist selection
     */
    public void showArtistTopTracks (ArtistItem artist) {
        Log.d (LOG_TAG, "showArtistTopTracks ==>" + artist.toString());

        // get country code from preferences
        Context           context = getActivity();
        SharedPreferences prefs   = PreferenceManager.getDefaultSharedPreferences(context);
        String countryCode        = prefs.getString(
                getString(R.string.pref_country_key), getString(R.string.pref_country_default));

        // clear adaptor...
        topTracksAdapter.clear();

        // show loading status that appears until the list view is populated
        topTracksListStatus.setText(String.format(getString(R.string.track_searching), artist.artistName));

        // kick off top tracks search task that populates the adaptor when finished
        TopTracksSearchTask searchTask = new TopTracksSearchTask(
                topTracksAdapter, topTracksListStatus, countryCode);
        searchTask.execute(artist.artistSpotifyId);
    }

    /**
     * clearArtistTopTracks - used to clear top tracks when present and lets the parent activity
     *     know if clear actually happened. This is used to support management of the backstack.
     */
    public boolean clearArtistTopTracks () {
        if ( topTracksListView.getChildCount() > 0 ) {
            // clear adaptor...
            topTracksAdapter.clear();

            // show loading status that appears until the list view is populated
            topTracksListStatus.setText("");

            return true;
        } else {
            return false;
        }
    }
}
