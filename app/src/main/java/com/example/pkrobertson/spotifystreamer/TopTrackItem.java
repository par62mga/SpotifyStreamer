package com.example.pkrobertson.spotifystreamer;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * TopTrackItem - class that stores the track name, album name and track image URL and also
 *     implements "Parcelable" to support saving/restoring an array of TopTrackItem
 *
 *     this structure is used by the TopTracksListViewAdaptor to show and render artist tracks
 *
 *     NOTE: the code implementing "Parcelable" was derived from an example on StackOverflow:
 *     http://stackoverflow.com/questions/12503836/how-to-save-custom-arraylist-on-android-screen-rotate
 *
 */
public class TopTrackItem implements Parcelable {
    public String topTrackArtistName;
    public String topTrackName;
    public String topTrackAlbumName;
    public String topTrackListImageURL;
    public String topTrackPlayImageURL;
    public String topTrackAudioURL;

    public TopTrackItem (String topTrackArtistName,
                         String topTrackName,
                         String topTrackAlbumName,
                         String topTrackListImageURL,
                         String topTrackPlayImageURL,
                         String topTrackAudioURL) {
        this.topTrackArtistName   = topTrackArtistName;
        this.topTrackName         = topTrackName;
        this.topTrackAlbumName    = topTrackAlbumName;
        this.topTrackListImageURL = topTrackListImageURL;
        this.topTrackPlayImageURL = topTrackPlayImageURL;
        this.topTrackAudioURL     = topTrackAudioURL;
    }

    private TopTrackItem(Parcel in) {
        // get TopTrackItem instance from parcel
        topTrackArtistName   = in.readString ();
        topTrackName         = in.readString ();
        topTrackAlbumName    = in.readString ();
        topTrackListImageURL = in.readString ();
        topTrackPlayImageURL = in.readString ();
        topTrackAudioURL     = in.readString ();
    }

    @Override
    public String toString() {
        return (topTrackArtistName + "|" + topTrackName + "|" + topTrackAlbumName + "|" +
                topTrackListImageURL + "|" + topTrackPlayImageURL + "|" + topTrackAudioURL);
    }

    public int describeContents () {
        return 0;
    }

    public void writeToParcel (Parcel out, int flags) {
        // put TopTrackItem instance to parcel
        out.writeString (topTrackArtistName);
        out.writeString (topTrackName);
        out.writeString (topTrackAlbumName);
        out.writeString (topTrackListImageURL);
        out.writeString (topTrackPlayImageURL);
        out.writeString (topTrackAudioURL);
    }

    // implements Parcelable creator, supporting an array of TopTrackItem
    public static final Parcelable.Creator<TopTrackItem> CREATOR = new Parcelable.Creator<TopTrackItem>() {
        public TopTrackItem createFromParcel(Parcel in) {
            return new TopTrackItem(in);
        }

        public TopTrackItem[] newArray(int size) {
            return new TopTrackItem[size];
        }
    };


}
