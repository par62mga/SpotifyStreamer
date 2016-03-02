package com.example.pkrobertson.spotifystreamer;

import android.content.Context;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;

import kaaes.spotify.webapi.android.models.Artist;

/**
 * ArtistSearchFragment - a fragment that holds the "fragment_artist_search view and
 *     handles searching for artists
 *
 *     the lifetime of this fragment is managed by ArtistSearchActivity
 *
 */

public class ArtistSearchFragment extends Fragment {
    private final String LOG_TAG = ArtistSearchActivity.class.getSimpleName();

    // used to store/retrieve ArtistItem context on saveInstanceState/onCreate
    private final String ARTIST_ARRAY_KEY    = "artist_items";
    private final String ARTIST_POSITION_KEY = "artist_selection";
    private  ArrayList<ArtistItem> artistArray;

    // hold reference for elements within the fragment/view
    private EditText artistSearchText;
    private ListView artistListView;
    private TextView artistListStatus;

    // artistAdapter is used to populate and manage artistListView
    private ArtistListViewAdapter artistAdapter;
    private int artistListViewPosition = ListView.INVALID_POSITION;

    public interface SelectArtistInterface {
        /*
         * Handle artist selected and show top tracks in pane or by launching activity
         */
        public void onArtistSelected (ArtistItem artist);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // assume we do not have a saved state
        artistArray            = null;
        artistListViewPosition = ListView.INVALID_POSITION;

        // restore array of ArtistList items from savedInstanceState
        // the array is then used to initialize the list view adapter during OnCreateView
        // the position is used to scroll to the selected artist
        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(ARTIST_ARRAY_KEY)) {
                artistArray = savedInstanceState.getParcelableArrayList(ARTIST_ARRAY_KEY);
            }
            if (savedInstanceState.containsKey(ARTIST_POSITION_KEY)) {
                artistListViewPosition = savedInstanceState.getInt(ARTIST_POSITION_KEY);
            }
        }
    }

    @Override
    public void onSaveInstanceState (Bundle outInstanceState) {
        // if we have an active artistAdapter, retrieve and store array of ArtistList items
        if (artistAdapter != null) {
            // create array from contents of the adapter
            artistArray = artistAdapter.getArtistItems();

            // save array to the bundle
            outInstanceState.putParcelableArrayList(ARTIST_ARRAY_KEY, artistArray);

            // save selected position to the bundle
            outInstanceState.putInt(ARTIST_POSITION_KEY, artistListViewPosition);
        }
        super.onSaveInstanceState(outInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_artist_search, container, false);

        // save reference to the EditText used to enter and search by artist name
        artistSearchText = (EditText) rootView.findViewById(R.id.artist_search_text);

        // make sure auto-correct is off
        // artistSearchText.setInputType(InputType.TYPE_TEXT_VARIATION_NORMAL);

        // set keypad action to "Search"
        artistSearchText.setImeOptions(EditorInfo.IME_ACTION_SEARCH);

        // set up listener that is executed when the search button is clicked
        artistSearchText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction (TextView textView, int editorAction, KeyEvent keyEvent) {
                boolean handled    = false;

                // get artist name as entered by the user
                String  searchText = textView.getText().toString();

                // nothing to do unless search was clicked and some text was entered
                if ( (editorAction == EditorInfo.IME_ACTION_SEARCH) && (searchText.isEmpty() == false)) {

                    // show searching status that appears until the list view is populated
                    artistListStatus.setText(String.format (getString(R.string.artist_searching), searchText));

                    // hide the darn keypad...not sure why Android makes this so difficult
                    artistSearchText.clearFocus();
                    InputMethodManager imm = (InputMethodManager)textView.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(artistSearchText.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);

                    // create new, empty adapter to handle artist search results
                    artistArray = new ArrayList<ArtistItem>();
                    artistAdapter = new ArtistListViewAdapter (getActivity(),
                            R.layout.artist_list_item,
                            artistArray);

                    // assign the adapter to the artist list view
                    artistListView.setAdapter(artistAdapter);

                    // kick off artist search task that populates the adaptor when finished
                    ArtistSearchTask searchTask = new ArtistSearchTask(artistAdapter, artistListStatus);
                    searchTask.execute(searchText);

                    // leave text in text box, clear if back pressed
                    handled = true;
                }

                return handled;
            }
        });

        // Get a reference to the artist list view
        artistListView = (ListView) rootView.findViewById(R.id.artist_list_view);

        // Get and save reference to text view used to show progress updates and results
        artistListStatus = (TextView) rootView.findViewById(R.id.artist_search_status);

        // Set up a click listener for the artist list
        artistListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
                // get reference to artist based on current position
                ArtistItem artist = artistAdapter.getItem(position);

                ((SelectArtistInterface)getActivity()).onArtistSelected(artist);
                artistListViewPosition = position;
            }
        });

        // this is where we populate the adapter from the saved instance state
        if (artistArray != null) {
            // populate adapter with saved ArtistItem information
            artistAdapter = new ArtistListViewAdapter(getActivity(),
                    R.layout.artist_list_item,
                    artistArray);

            // assign the adapter to the artist list view
            artistListView.setAdapter(artistAdapter);
            if (artistListViewPosition != ListView.INVALID_POSITION) {
                Log.d(LOG_TAG, "onCreatView list view position ==> " + artistListViewPosition);
                // artistListView.smoothScrollToPosition(artistListViewPosition);
                artistListView.setSelectionFromTop(artistListViewPosition, 0);
            }
        }

        return rootView;
    }

    /**
     * clearArtistSelection - used to clear selection when the parent activity is managing the
     *     backstack. This just deselects the artist after top tracks are cleared.
     */
    public void clearArtistSelection () {
        artistListView.clearChoices ();
        artistAdapter.notifyDataSetChanged();
    }

    /**
     * clearArtistList - used to clear artist when present and lets the parent activity know if
     *     clear actually happened. Again this is used to support management of the backstack.
     */
    public boolean clearArtistList () {
        // see if we have artists to clear
        if ( artistListView.getChildCount() > 0 ) {
            // clear adaptor...
            artistAdapter.clear();

            // clear artist search text
            artistSearchText.setText ("");

            return true;
        } else if ( artistListStatus.getText().length() > 0 ) {
            // artist not found message was present, clear it
            artistListStatus.setText("");

            return true;
        } else {

            // nothing was happening...
            return false;
        }

    }

    // TODO: when nothing else to do...need to complete/retain artist search when rotated
}
