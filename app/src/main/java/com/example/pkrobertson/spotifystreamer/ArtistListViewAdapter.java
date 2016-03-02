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
 * ArtistListViewAdapter - extends ArrayAdaptor to store and render artist name and image from
 *     ArtistItem
 *
 *     this class is used by ArtistSearchFragment and ArtistSearchTask
 *
 */

public class ArtistListViewAdapter extends ArrayAdapter<ArtistItem> {
    private final String LOG_TAG = ArtistListViewAdapter.class.getSimpleName();

    private Context        myContext;
    private LayoutInflater myLayoutInflater;

    static class ViewHolder {
	    ImageView artistImageView;
        TextView  artistTextView;
    };         

    // constructor used to populate adapter with list of artist items
    public ArtistListViewAdapter (Context context, int resourceId, List<ArtistItem> items) {
        super (context, resourceId, items);

        // save reference to context
        this.myContext = context;

        // get layout inflater reference from context
        this.myLayoutInflater = (LayoutInflater) this.myContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    // get list of artist items from adapter, used to save state
    public ArrayList<ArtistItem> getArtistItems () {
        ArrayList<ArtistItem> result = new ArrayList<ArtistItem>();
        int i;

        for (i = 0; i < getCount(); i++) {
            result.add(getItem (i));
        }
        return result;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        // get reference to and optionally inflate each "artist_list_item"
        LinearLayout itemView;
        ViewHolder    itemHolder;

        if (convertView == null) {
            itemView = (LinearLayout) myLayoutInflater.inflate(R.layout.artist_list_item, parent, false);
	    
	 
            // get and save references to the artist_list_item view elements
            itemHolder = new ViewHolder ();

            itemHolder.artistImageView = (ImageView) itemView.findViewById(R.id.artist_icon);
            itemHolder.artistTextView  = (TextView)  itemView.findViewById(R.id.artist_name);

            itemView.setTag(itemHolder);	
            
        } else {
            itemView = (LinearLayout) convertView;
            itemHolder = (ViewHolder) itemView.getTag();
        }

        // get ArtistItem from the adaptor, holding the artist name and image URL
        ArtistItem artistItem = getItem (position);

        // populate artist name
        itemHolder.artistTextView.setText(artistItem.artistName);

        // load artist image using "Picasso" or with a default image if the URL is not valid
        if ((artistItem.artistImageURL != null) &&
             Patterns.WEB_URL.matcher(artistItem.artistImageURL).matches()) {
            try {
                Picasso.with(myContext).load(artistItem.artistImageURL).into(itemHolder.artistImageView);
            } catch (Exception e) {
                Log.v(LOG_TAG, "Picasso call failed" + e.toString());
                itemHolder.artistImageView.setImageResource(R.drawable.ic_artist_image);
            }
        } else {
            itemHolder.artistImageView.setImageResource(R.drawable.ic_artist_image);
        }

        return itemView;
    }
}
