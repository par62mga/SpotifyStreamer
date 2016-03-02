package com.example.pkrobertson.spotifystreamer;

import android.content.Context;
import android.util.Log;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.List;

/**
 * TopTracksListViewAdapter - extends ArrayAdaptor to store and render the track image, track name
 *     and album name from TopTrackItem
 *
 *     this class is used by TopTracksFragment and TopTracksSearchTask
 *
 */
public class TopTracksListViewAdapter extends ArrayAdapter<TopTrackItem> {
    private final String LOG_TAG = TopTracksListViewAdapter.class.getSimpleName();

    private Context myContext;
    private LayoutInflater myLayoutInflater;

    static class ViewHolder {
	ImageView trackImageView;
        TextView  trackTextView;
    };

    // constructor used to populate adapter with list of top track items
    public TopTracksListViewAdapter (Context context, int resourceId, List<TopTrackItem> items) {
        super (context, resourceId, items);

        // save reference to context
        this.myContext = context;

        // get layout inflater reference from context
        this.myLayoutInflater = (LayoutInflater) this.myContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    // get list of top track items from adapter, used to save state
    public ArrayList<TopTrackItem> getTopTrackItems () {
        ArrayList<TopTrackItem> result = new ArrayList<TopTrackItem>();
        int i;

        for (i = 0; i < getCount(); i++) {
            result.add(getItem (i));
        }
        return result;
    }


    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        // get reference to and optionally inflate each "top_tracks_list_item"
        LinearLayout itemView;
        ViewHolder   itemHolder;

        if (convertView == null) {
            itemView = (LinearLayout) myLayoutInflater.inflate(R.layout.top_tracks_list_item, parent, false);

            // get and save references to the artist_list_item view elements
            itemHolder = new ViewHolder ();

            itemHolder.trackImageView = (ImageView) itemView.findViewById(R.id.top_track_icon);
            itemHolder.trackTextView  = (TextView)  itemView.findViewById(R.id.top_track_description);

            itemView.setTag(itemHolder);	

        } else {
            itemView = (LinearLayout) convertView;
            itemHolder = (ViewHolder) itemView.getTag();
        }

        // get TopTrackItem from the adaptor, holding the track name, album name and image URL
        TopTrackItem topTrackItem = getItem (position);

        // set top track name and album name in the text view
        itemHolder.trackTextView.setText(topTrackItem.topTrackName + "\n" + topTrackItem.topTrackAlbumName);

        // load top track image using "Picasso" or with a default image if the URL is not valid
        if ( (topTrackItem.topTrackListImageURL != null) &&
              Patterns.WEB_URL.matcher(topTrackItem.topTrackListImageURL).matches()) {
            try {
                Picasso.with(myContext).load(topTrackItem.topTrackListImageURL).into(itemHolder.trackImageView);
            } catch (Exception e) {
                Log.v(LOG_TAG, "Picasso call failed" + e.toString());
                itemHolder.trackImageView.setImageResource(R.drawable.ic_artist_image);
            }
        } else {
            itemHolder.trackImageView.setImageResource(R.drawable.ic_artist_image);
        }

        return itemView;
    }
}
