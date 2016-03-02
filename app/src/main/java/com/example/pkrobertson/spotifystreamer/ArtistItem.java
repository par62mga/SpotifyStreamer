package com.example.pkrobertson.spotifystreamer;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * ArtistItem - class that stores artist name, artist Spotify ID and artist image URL and also]
 *     implements "Parcelable" to support saving/restoring an array of ArtistItem
 *
 *     this structure is used by the ArtistListViewAdaptor to show and render artists
 *
 *     NOTE: the code implementing "Parcelable" was derived from an example on StackOverflow:
 *     http://stackoverflow.com/questions/12503836/how-to-save-custom-arraylist-on-android-screen-rotate
 *
 */

public class ArtistItem implements Parcelable {
    public String artistName;
    public String artistSpotifyId;
    public String artistImageURL;

    public ArtistItem (String artistName, String artistSpotifyId, String artistImageURL) {
        this.artistName      = artistName;
        this.artistSpotifyId = artistSpotifyId;
        this.artistImageURL  = artistImageURL;
    }

    private ArtistItem(Parcel in) {
        // get ArtistItem instance from parcel
        artistName = in.readString ();
        artistSpotifyId = in.readString ();
        artistImageURL = in.readString ();
    }

    @Override
    public String toString() {
        return (artistName + "|" + artistSpotifyId + "|" + artistImageURL);
    }

    public int describeContents () {
        return 0;
    }

    public void writeToParcel (Parcel out, int flags) {
        // put ArtistItem instance to parcel
        out.writeString (artistName);
        out.writeString (artistSpotifyId);
        out.writeString (artistImageURL);
    }

    // implements Parcelable creator, supporting an array of ArtistItem
    public static final Parcelable.Creator<ArtistItem> CREATOR = new Parcelable.Creator<ArtistItem>() {
        public ArtistItem createFromParcel(Parcel in) {
            return new ArtistItem(in);
        }

        public ArtistItem[] newArray(int size) {
            return new ArtistItem[size];
        }
    };
}
