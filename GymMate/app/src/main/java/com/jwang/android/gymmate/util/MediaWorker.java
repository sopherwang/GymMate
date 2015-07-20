package com.jwang.android.gymmate.util;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.text.TextUtils;
import android.util.Log;

import com.jwang.android.gymmate.R;
import com.jwang.android.gymmate.activity.MainNavigationActivity;
import com.jwang.android.gymmate.model.ModelLocation;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestParams;
import com.loopj.android.http.SyncHttpClient;

import org.apache.http.Header;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * @author Jiajun Wang on 7/16/15
 *         Copyright (c) 2015 StumbleUpon, Inc. All rights reserved.
 */
public class MediaWorker
{
    private static final String TAG = MediaWorker.class.getSimpleName();
    private static final long DAY_IN_MILLIS = 1000 * 60 * 60 * 24;
    private static final int MEDIA_NOTIFICATION_ID = 3004;
    private static MediaWorker sMediaWorker;
    private Context mContext;
    private HashSet<String> mGymMediaPaginationUrls;

    private MediaWorker(Context context)
    {
        mContext = context;
        mGymMediaPaginationUrls = new HashSet<>();
    }

    public static MediaWorker getInstance(Context context)
    {
        if (sMediaWorker == null)
        {
            sMediaWorker = new MediaWorker(context);
        }
        return sMediaWorker;
    }

    public void startFetchGymMedia(String[] location)
    {
        fetchLocationsFromGoogle(location);
    }

    public HashSet<String> getPaginationUrls()
    {
        return mGymMediaPaginationUrls;
    }

    private void fetchLocationsFromGoogle(String[] location)
    {
        //make a google api call to get gym locations
        RequestParams googleRequestParams = new RequestParams();
        googleRequestParams.put("location", location[1] + "," + location[0]);
        googleRequestParams.put("radius", AppConfig.RADIUS_FROM_DESTINATION * 1000);
        googleRequestParams.put("key", AppConfig.GOOGLE_ACCESS_TOKEN);
        googleRequestParams.put("types", "gym");
        googleRequestParams.put("language", "en");
        SyncHttpClient googleSyncHttpClient = new SyncHttpClient();
        googleSyncHttpClient.get(AppConfig.GOOGLE_API_ENDPOINT, googleRequestParams, new AsyncHttpResponseHandler()
        {
            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] responseBody)
            {
                ArrayList<ModelLocation> locations = JsonParseUtil.parseGetGeoLocationByGoogleApiJson(new String(responseBody));
                Log.w(TAG, "***fetchInstagramMedias: Get " + locations.size() + " Locations from Google.");

                for (ModelLocation location : locations)
                {
                    fetchMedias(location.getLocationLat(), location.getLocationLong());
                }
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error)
            {
                Log.e(TAG, "fetchInstagramMedias ERROR: get locations from Google.");
            }
        });
    }

    private void fetchMedias(String lat, String lng)
    {
        RequestParams facebookRequestParams = new RequestParams();
        facebookRequestParams.put("type", "place");
        facebookRequestParams.put("center", lat + "," + lng);
        facebookRequestParams.put("distance", "100");
        facebookRequestParams.put("access_token", "425103717696529|1b77655dba1ccc2ed88fad1f9a932d7b");
        facebookRequestParams.put("expires_in", "5184000");
        SyncHttpClient asyncHttpClient = new SyncHttpClient();
        asyncHttpClient.get("https://graph.facebook.com/search", facebookRequestParams, new AsyncHttpResponseHandler()
        {
            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] responseBody)
            {
                String facebookLocationId = JsonParseUtil.parseGetFaceBookLocationByGeoResultJson(new String(responseBody)).getId();
                Log.w(TAG, "***Facebook Id is " + facebookLocationId);

                final String access_token = InstagramOauth.getsInstance(mContext).getSession().getAccessToken();
                SyncHttpClient getInstagramSyncHttpClient = new SyncHttpClient();
                RequestParams instagramRequestParams = new RequestParams();
                instagramRequestParams.put("facebook_places_id", facebookLocationId);
                instagramRequestParams.put("access_token", access_token);
                getInstagramSyncHttpClient.get(AppConfig.INSTAGRAM_LOCATION_ENDPOINT, instagramRequestParams, new AsyncHttpResponseHandler()
                {
                    @Override
                    public void onSuccess(int statusCode, Header[] headers, byte[] responseBody)
                    {
                        String instagramLocationId = JsonParseUtil.parseGetInstagramLocationByFaceBookIdJson(mContext, new String(responseBody)).getId();
                        Log.w(TAG, "***Instagram Id is " + instagramLocationId);
                        if (!TextUtils.isEmpty(instagramLocationId))
                        {
                            String popularJsonStr = HttpRequestUtil.startHttpRequest("https://api.instagram.com/v1/locations/" + instagramLocationId + "/media/recent?access_token=" + access_token, TAG);
                            boolean b = JsonParseUtil.parseMediaGetPagination(mContext, popularJsonStr, mGymMediaPaginationUrls);
                            if (b)
                            {
                                notifyMedia();
                            }
                        }
                    }

                    @Override
                    public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error)
                    {
                    }
                });
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error)
            {
            }
        });
    }

    private void notifyMedia()
    {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        String lastNotificationKey = mContext.getString(R.string.pref_last_notification);
        long lastSync = prefs.getLong(lastNotificationKey, 0);

        if (System.currentTimeMillis() - lastSync >= DAY_IN_MILLIS)
        {
            Resources resources = mContext.getResources();
            String title = mContext.getString(R.string.app_name);
            String contentText = mContext.getString(R.string.notification_content);

            NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(mContext).setColor(resources.getColor(R.color.primary_color)).setSmallIcon(R.drawable.ic_launcher).setContentTitle(title).setContentText(contentText);

            Intent resultIntent = new Intent(mContext, MainNavigationActivity.class);

            TaskStackBuilder stackBuilder = TaskStackBuilder.create(mContext);
            stackBuilder.addNextIntent(resultIntent);
            PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
            mBuilder.setContentIntent(resultPendingIntent);

            NotificationManager mNotificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
            // MEDIA_NOTIFICATION_ID allows you to update the notification later on.
            mNotificationManager.notify(MEDIA_NOTIFICATION_ID, mBuilder.build());

            //refreshing last sync
            SharedPreferences.Editor editor = prefs.edit();
            editor.putLong(lastNotificationKey, System.currentTimeMillis());
            editor.commit();
        }
    }
}
