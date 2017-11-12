package fr.gouv.etalab.mastodon.fragments;
/* Copyright 2017 Thomas Schneider
 *
 * This file is a part of Mastalab
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Mastalab is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Mastalab; if not,
 * see <http://www.gnu.org/licenses>. */
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

import fr.gouv.etalab.mastodon.R;
import fr.gouv.etalab.mastodon.activities.MainActivity;
import fr.gouv.etalab.mastodon.asynctasks.RetrieveMissingFeedsAsyncTask;
import fr.gouv.etalab.mastodon.client.APIResponse;
import fr.gouv.etalab.mastodon.drawers.StatusListAdapter;
import fr.gouv.etalab.mastodon.helper.Helper;
import fr.gouv.etalab.mastodon.interfaces.OnRetrieveMissingFeedsInterface;
import fr.gouv.etalab.mastodon.services.StreamingFederatedTimelineService;
import fr.gouv.etalab.mastodon.services.StreamingLocalTimelineService;
import fr.gouv.etalab.mastodon.asynctasks.RetrieveFeedsAsyncTask;
import fr.gouv.etalab.mastodon.client.Entities.Status;
import fr.gouv.etalab.mastodon.interfaces.OnRetrieveFeedsInterface;


/**
 * Created by Thomas on 24/04/2017.
 * Fragment to display content related to status
 */
public class DisplayStatusFragment extends Fragment implements OnRetrieveFeedsInterface, OnRetrieveMissingFeedsInterface {


    private boolean flag_loading;
    private Context context;
    private AsyncTask<Void, Void, Void> asyncTask;
    private StatusListAdapter statusListAdapter;
    private String max_id, since_id;
    private List<Status> statuses;
    private ArrayList<String> knownId;
    private RetrieveFeedsAsyncTask.Type type;
    private RelativeLayout mainLoader, nextElementLoader, textviewNoAction;
    private boolean firstLoad;
    private SwipeRefreshLayout swipeRefreshLayout;
    private String targetedId;
    private String tag;
    private boolean swiped;
    private RecyclerView lv_status;
    private boolean isOnWifi;
    private int behaviorWithAttachments;
    private boolean showMediaOnly, showPinned;
    private int positionSpinnerTrans;
    private String lastReadStatus;
    private Intent streamingFederatedIntent, streamingLocalIntent;
    private boolean reversed;
    LinearLayoutManager mLayoutManager;

    public DisplayStatusFragment(){
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_status, container, false);
        statuses = new ArrayList<>();
        knownId = new ArrayList<>();
        context = getContext();
        Bundle bundle = this.getArguments();
        boolean comesFromSearch = false;
        boolean hideHeader = false;
        showMediaOnly = false;
        showPinned = false;
        if (bundle != null) {
            type = (RetrieveFeedsAsyncTask.Type) bundle.get("type");
            targetedId = bundle.getString("targetedId", null);
            tag = bundle.getString("tag", null);
            hideHeader = bundle.getBoolean("hideHeader", false);
            showMediaOnly = bundle.getBoolean("showMediaOnly",false);
            showPinned = bundle.getBoolean("showPinned",false);
            if( bundle.containsKey("statuses")){
                ArrayList<Parcelable> statusesReceived = bundle.getParcelableArrayList("statuses");
                assert statusesReceived != null;
                for(Parcelable status: statusesReceived){
                    statuses.add((Status) status);
                    knownId.add(((Status) status).getId());
                }
                comesFromSearch = true;
            }
        }
        assert context != null;
        final SharedPreferences sharedpreferences = context.getSharedPreferences(Helper.APP_PREFS, Context.MODE_PRIVATE);
        String userId = sharedpreferences.getString(Helper.PREF_KEY_ID, null);
        reversed = sharedpreferences.getBoolean(Helper.SET_REVERSE_READING, false);
        max_id = null;
        if( type == RetrieveFeedsAsyncTask.Type.HOME && reversed)
            since_id = "98987285241600274"; //TODO: remove
            //since_id = sharedpreferences.getString(Helper.LAST_HOMETIMELINE_MAX_ID + userId, null);
        flag_loading = true;
        firstLoad = true;
        swiped = false;



        isOnWifi = Helper.isOnWIFI(context);

        positionSpinnerTrans = sharedpreferences.getInt(Helper.SET_TRANSLATOR, Helper.TRANS_YANDEX);
        swipeRefreshLayout = rootView.findViewById(R.id.swipeContainer);
        behaviorWithAttachments = sharedpreferences.getInt(Helper.SET_ATTACHMENT_ACTION, Helper.ATTACHMENT_ALWAYS);
        if( type == RetrieveFeedsAsyncTask.Type.HOME)
            lastReadStatus = sharedpreferences.getString(Helper.LAST_HOMETIMELINE_MAX_ID + userId, null);
        lv_status = rootView.findViewById(R.id.lv_status);
        mainLoader =  rootView.findViewById(R.id.loader);
        nextElementLoader = rootView.findViewById(R.id.loading_next_status);
        textviewNoAction =  rootView.findViewById(R.id.no_action);
        mainLoader.setVisibility(View.VISIBLE);
        nextElementLoader.setVisibility(View.GONE);
        statusListAdapter = new StatusListAdapter(context, type, targetedId, isOnWifi, behaviorWithAttachments, positionSpinnerTrans, this.statuses);
        lv_status.setAdapter(statusListAdapter);
        mLayoutManager = new LinearLayoutManager(context);
        lv_status.setLayoutManager(mLayoutManager);
        if( !comesFromSearch){

            //Hide account header when scrolling for ShowAccountActivity
            if (hideHeader)
                ViewCompat.setNestedScrollingEnabled(lv_status, true);


            lv_status.addOnScrollListener(new RecyclerView.OnScrollListener() {
                public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                    if (dy > 0) {
                        int visibleItemCount = mLayoutManager.getChildCount();
                        int totalItemCount = mLayoutManager.getItemCount();
                        int firstVisibleItem = mLayoutManager.findFirstVisibleItemPosition();
                        if (firstVisibleItem + visibleItemCount == totalItemCount && context != null) {
                            if (!flag_loading) {
                                flag_loading = true;
                                if (type == RetrieveFeedsAsyncTask.Type.USER)
                                    asyncTask = new RetrieveFeedsAsyncTask(context, type, targetedId, max_id, showMediaOnly, showPinned, DisplayStatusFragment.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                                else if (type == RetrieveFeedsAsyncTask.Type.TAG)
                                    asyncTask = new RetrieveFeedsAsyncTask(context, type, tag, targetedId, max_id, DisplayStatusFragment.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                                else
                                    asyncTask = new RetrieveFeedsAsyncTask(context, type, max_id, DisplayStatusFragment.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

                                nextElementLoader.setVisibility(View.VISIBLE);
                            }
                        } else {
                            nextElementLoader.setVisibility(View.GONE);
                        }
                    }
                }
            });


            //Disables pull to refresh when reading HOME timeline in reverse mod
            if( type != RetrieveFeedsAsyncTask.Type.HOME || !reversed) {
                swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
                    @Override
                    public void onRefresh() {
                        max_id = null;
                        statuses = new ArrayList<>();
                        knownId = new ArrayList<>();
                        firstLoad = true;
                        flag_loading = true;
                        swiped = true;
                        MainActivity.countNewStatus = 0;
                        if (type == RetrieveFeedsAsyncTask.Type.USER)
                            asyncTask = new RetrieveFeedsAsyncTask(context, type, targetedId, max_id, showMediaOnly, showPinned, DisplayStatusFragment.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                        else if (type == RetrieveFeedsAsyncTask.Type.TAG)
                            asyncTask = new RetrieveFeedsAsyncTask(context, type, tag, targetedId, max_id, DisplayStatusFragment.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                        else
                            asyncTask = new RetrieveFeedsAsyncTask(context, type, max_id, DisplayStatusFragment.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

                    }
                });
            }
            swipeRefreshLayout.setColorSchemeResources(R.color.mastodonC4,
                    R.color.mastodonC2,
                    R.color.mastodonC3);
            if( context != null) {
                if (type == RetrieveFeedsAsyncTask.Type.USER)
                    asyncTask = new RetrieveFeedsAsyncTask(context, type, targetedId, max_id, showMediaOnly, showPinned, DisplayStatusFragment.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                else if (type == RetrieveFeedsAsyncTask.Type.TAG)
                    asyncTask = new RetrieveFeedsAsyncTask(context, type, tag, targetedId, max_id, DisplayStatusFragment.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                else {
                    asyncTask = new RetrieveFeedsAsyncTask(context, type, since_id, max_id, DisplayStatusFragment.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                }
            }else {
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if( context != null){
                            if (type == RetrieveFeedsAsyncTask.Type.USER)
                                asyncTask = new RetrieveFeedsAsyncTask(context, type, targetedId, max_id, showMediaOnly, showPinned, DisplayStatusFragment.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                            else if (type == RetrieveFeedsAsyncTask.Type.TAG)
                                asyncTask = new RetrieveFeedsAsyncTask(context, type, tag, targetedId, max_id, DisplayStatusFragment.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                            else {
                                asyncTask = new RetrieveFeedsAsyncTask(context, type, since_id, max_id, DisplayStatusFragment.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                            }
                        }
                    }
                }, 500);
            }
        }else {
            statusListAdapter.notifyDataSetChanged();
            mainLoader.setVisibility(View.GONE);
            nextElementLoader.setVisibility(View.GONE);
            if( statuses == null || statuses.size() == 0 )
                textviewNoAction.setVisibility(View.VISIBLE);
        }

        return rootView;
    }


    @Override
    public void onCreate(Bundle saveInstance)
    {
        super.onCreate(saveInstance);
    }



    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        this.context = context;
    }

    @Override
    public void onDestroy (){
        super.onDestroy();
        if(asyncTask != null && asyncTask.getStatus() == AsyncTask.Status.RUNNING)
            asyncTask.cancel(true);
    }



    @Override
    public void onRetrieveFeeds(APIResponse apiResponse) {
        mainLoader.setVisibility(View.GONE);
        nextElementLoader.setVisibility(View.GONE);
        //Discards 404 - error which can often happen due to toots which have been deleted
        final SharedPreferences sharedpreferences = context.getSharedPreferences(Helper.APP_PREFS, Context.MODE_PRIVATE);
        if( apiResponse.getError() != null && (apiResponse.getStatuses() == null || apiResponse.getStatuses().size() == 0) ){
            boolean show_error_messages = sharedpreferences.getBoolean(Helper.SET_SHOW_ERROR_MESSAGES, true);
            if( show_error_messages && !apiResponse.getError().getError().startsWith("404 -"))
                Toast.makeText(context, apiResponse.getError().getError(),Toast.LENGTH_LONG).show();
            swipeRefreshLayout.setRefreshing(false);
            swiped = false;
            flag_loading = false;
            return;
        }
        List<Status> statuses = apiResponse.getStatuses();
        max_id = apiResponse.getMax_id();
        flag_loading = (max_id == null );
        if( !swiped && firstLoad && (statuses == null || statuses.size() == 0))
            textviewNoAction.setVisibility(View.VISIBLE);
        else
            textviewNoAction.setVisibility(View.GONE);
        if( swiped ){
            statusListAdapter = new StatusListAdapter(context, type, targetedId, isOnWifi, behaviorWithAttachments, positionSpinnerTrans, this.statuses);
            lv_status.setAdapter(statusListAdapter);
            swiped = false;
        }

        if( statuses != null && statuses.size() > 0) {
            for(Status tmpStatus: statuses){
                if( !knownId.contains(tmpStatus.getId())) {
                    if( type == RetrieveFeedsAsyncTask.Type.HOME && firstLoad && lastReadStatus != null && Long.parseLong(tmpStatus.getId()) > Long.parseLong(lastReadStatus)){
                        tmpStatus.setNew(true);
                        MainActivity.countNewStatus++;
                    }else {
                        tmpStatus.setNew(false);
                    }
                    this.statuses.add(tmpStatus);
                    knownId.add(tmpStatus.getId());
                }
            }

            if( firstLoad && type == RetrieveFeedsAsyncTask.Type.HOME) {
                //Update the id of the last toot retrieved
                MainActivity.lastHomeId = statuses.get(0).getId();
                String userId = sharedpreferences.getString(Helper.PREF_KEY_ID, null);
                updateStatusLastId(sharedpreferences, userId, statuses.get(0).getId());
                lastReadStatus = statuses.get(0).getId();
            }
            statusListAdapter.notifyDataSetChanged();
            if( apiResponse.getFocusedElement() > 0)
                lv_status.scrollToPosition(apiResponse.getFocusedElement());
            if( firstLoad && type == RetrieveFeedsAsyncTask.Type.HOME)
            //Display new value in counter
            try {
                ((MainActivity) context).updateHomeCounter();
            }catch (Exception ignored){}
        }
        swipeRefreshLayout.setRefreshing(false);
        firstLoad = false;


    }

    /**
     * Deals with new status coming from the streaming api
     * @param status Status
     */
    public void refresh(Status status){
        //New data are available
        if( type == RetrieveFeedsAsyncTask.Type.HOME) {
            if (context == null)
                return;
            if (status != null && !knownId.contains(status.getId())) {
                //Update the id of the last toot retrieved
                MainActivity.lastHomeId = status.getId();
                status.setReplies(new ArrayList<Status>());
                statuses.add(0,status);
                knownId.add(0,status.getId());
                SharedPreferences sharedpreferences = context.getSharedPreferences(Helper.APP_PREFS, Context.MODE_PRIVATE);
                String userId = sharedpreferences.getString(Helper.PREF_KEY_ID, null);
                if( !status.getAccount().getId().equals(userId))
                    MainActivity.countNewStatus++;
                int firstVisibleItem = mLayoutManager.findFirstVisibleItemPosition();
                if( firstVisibleItem > 0)
                    statusListAdapter.notifyItemInserted(0);
                else
                    statusListAdapter.notifyDataSetChanged();
                if (textviewNoAction.getVisibility() == View.VISIBLE)
                    textviewNoAction.setVisibility(View.GONE);
            }
        }else if(type == RetrieveFeedsAsyncTask.Type.PUBLIC || type == RetrieveFeedsAsyncTask.Type.LOCAL){
            if (context == null)
                return;
            if (status != null && !knownId.contains(status.getId())) {
                status.setReplies(new ArrayList<Status>());
                status.setNew(false);
                statuses.add(0, status);
                int firstVisibleItem = mLayoutManager.findFirstVisibleItemPosition();
                if( firstVisibleItem > 0)
                    statusListAdapter.notifyItemInserted(0);
                else
                    statusListAdapter.notifyDataSetChanged();
                knownId.add(0, status.getId());
                if (textviewNoAction.getVisibility() == View.VISIBLE)
                    textviewNoAction.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Refresh status in list
     */
    public void refreshFilter(){
        statusListAdapter.notifyDataSetChanged();
    }

    @Override
    public void onResume(){
        super.onResume();
        if( type == RetrieveFeedsAsyncTask.Type.PUBLIC){

            if( getUserVisibleHint() ){
                SharedPreferences sharedpreferences = context.getSharedPreferences(Helper.APP_PREFS, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedpreferences.edit();
                String userId = sharedpreferences.getString(Helper.PREF_KEY_ID, null);
                editor.putBoolean(Helper.SHOULD_CONTINUE_STREAMING_FEDERATED+userId, true);
                editor.apply();
                streamingFederatedIntent = new Intent(context, StreamingFederatedTimelineService.class);
                context.startService(streamingFederatedIntent);
                if( statuses != null && statuses.size() > 0)
                    retrieveMissingToots(statuses.get(0).getId());
            }
        }else if (type == RetrieveFeedsAsyncTask.Type.LOCAL){

            if( getUserVisibleHint() ){
                SharedPreferences sharedpreferences = context.getSharedPreferences(Helper.APP_PREFS, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedpreferences.edit();
                String userId = sharedpreferences.getString(Helper.PREF_KEY_ID, null);
                editor.putBoolean(Helper.SHOULD_CONTINUE_STREAMING_LOCAL+userId, true);
                editor.apply();
                streamingLocalIntent = new Intent(context, StreamingLocalTimelineService.class);
                context.startService(streamingLocalIntent);
                if( statuses != null && statuses.size() > 0)
                    retrieveMissingToots(statuses.get(0).getId());
            }
        }
    }

    /**
     * Called from main activity in onResume to retrieve missing toots (home timeline)
     * @param sinceId String
     */
    public void retrieveMissingToots(String sinceId){
        asyncTask = new RetrieveMissingFeedsAsyncTask(context, sinceId, type, DisplayStatusFragment.this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * When tab comes visible, first displayed toot is defined as read
     * @param visible boolean
     */
    @Override
    public void setMenuVisibility(final boolean visible) {
        super.setMenuVisibility(visible);
        if( context == null)
            return;
        //Store last toot id for home timeline to avoid to notify for those that have been already seen
        if (type == RetrieveFeedsAsyncTask.Type.HOME && visible && statuses != null && statuses.size() > 0) {
            SharedPreferences sharedpreferences = context.getSharedPreferences(Helper.APP_PREFS, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedpreferences.edit();
            String userId = sharedpreferences.getString(Helper.PREF_KEY_ID, null);
            editor.putString(Helper.LAST_HOMETIMELINE_MAX_ID + userId, statuses.get(0).getId());
            lastReadStatus = statuses.get(0).getId();
            editor.apply();
        } else if( type == RetrieveFeedsAsyncTask.Type.PUBLIC ){
            if (visible) {
                SharedPreferences sharedpreferences = context.getSharedPreferences(Helper.APP_PREFS, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedpreferences.edit();
                String userId = sharedpreferences.getString(Helper.PREF_KEY_ID, null);
                editor.putBoolean(Helper.SHOULD_CONTINUE_STREAMING_FEDERATED+userId, true);
                editor.apply();
                streamingFederatedIntent = new Intent(context, StreamingFederatedTimelineService.class);
                context.startService(streamingFederatedIntent);
                if( statuses != null && statuses.size() > 0)
                    retrieveMissingToots(statuses.get(0).getId());
            }else {
                if( streamingFederatedIntent != null ){
                    SharedPreferences sharedpreferences = context.getSharedPreferences(Helper.APP_PREFS, Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = sharedpreferences.edit();
                    String userId = sharedpreferences.getString(Helper.PREF_KEY_ID, null);
                    editor.putBoolean(Helper.SHOULD_CONTINUE_STREAMING_FEDERATED+userId, false);
                    editor.apply();
                    context.stopService(streamingFederatedIntent);
                }
            }
        }else if (type == RetrieveFeedsAsyncTask.Type.LOCAL){
            if (visible) {
                SharedPreferences sharedpreferences = context.getSharedPreferences(Helper.APP_PREFS, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedpreferences.edit();
                String userId = sharedpreferences.getString(Helper.PREF_KEY_ID, null);
                editor.putBoolean(Helper.SHOULD_CONTINUE_STREAMING_LOCAL+userId, true);
                editor.apply();
                streamingLocalIntent = new Intent(context, StreamingLocalTimelineService.class);
                context.startService(streamingLocalIntent);
                if( statuses != null && statuses.size() > 0)
                    retrieveMissingToots(statuses.get(0).getId());
            }else {
                if( streamingLocalIntent != null ){
                    SharedPreferences sharedpreferences = context.getSharedPreferences(Helper.APP_PREFS, Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = sharedpreferences.edit();
                    String userId = sharedpreferences.getString(Helper.PREF_KEY_ID, null);
                    editor.putBoolean(Helper.SHOULD_CONTINUE_STREAMING_LOCAL+userId, false);
                    editor.apply();
                    context.stopService(streamingLocalIntent);
                }
            }
        }
    }

    @Override
    public void onStop(){
        super.onStop();
        if( type == RetrieveFeedsAsyncTask.Type.PUBLIC && streamingFederatedIntent != null){
            SharedPreferences sharedpreferences = context.getSharedPreferences(Helper.APP_PREFS, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedpreferences.edit();
            String userId = sharedpreferences.getString(Helper.PREF_KEY_ID, null);
            editor.putBoolean(Helper.SHOULD_CONTINUE_STREAMING_FEDERATED+userId, false);
            editor.apply();
            context.stopService(streamingFederatedIntent);
        }else if(type == RetrieveFeedsAsyncTask.Type.LOCAL && streamingLocalIntent != null){
            SharedPreferences sharedpreferences = context.getSharedPreferences(Helper.APP_PREFS, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedpreferences.edit();
            String userId = sharedpreferences.getString(Helper.PREF_KEY_ID, null);
            editor.putBoolean(Helper.SHOULD_CONTINUE_STREAMING_LOCAL+userId, false);
            editor.apply();
            context.stopService(streamingLocalIntent);
        }
    }

    public void scrollToTop(){
        if( lv_status != null) {
            lv_status.setAdapter(statusListAdapter);
            //Store last toot id for home timeline to avoid to notify for those that have been already seen
            if (type == RetrieveFeedsAsyncTask.Type.HOME && statuses != null && statuses.size() > 0) {
                SharedPreferences sharedpreferences = context.getSharedPreferences(Helper.APP_PREFS, Context.MODE_PRIVATE);
                String userId = sharedpreferences.getString(Helper.PREF_KEY_ID, null);
                updateStatusLastId(sharedpreferences, userId, statuses.get(0).getId());
                lastReadStatus = statuses.get(0).getId();
            }
        }
    }

    @Override
    public void onRetrieveMissingFeeds(List<Status> statuses) {
        if( statuses != null && statuses.size() > 0) {
            int inserted = 0;
            for (int i = statuses.size() - 1; i >= 0; i--) {
                if (!knownId.contains(statuses.get(i).getId())) {
                    if (type == RetrieveFeedsAsyncTask.Type.HOME)
                        statuses.get(i).setNew(true);
                    knownId.add(0,statuses.get(i).getId());
                    inserted++;
                    statuses.get(i).setReplies(new ArrayList<Status>());
                    this.statuses.add(0, statuses.get(i));
                    SharedPreferences sharedpreferences = context.getSharedPreferences(Helper.APP_PREFS, Context.MODE_PRIVATE);
                    String userId = sharedpreferences.getString(Helper.PREF_KEY_ID, null);
                    if (type == RetrieveFeedsAsyncTask.Type.HOME && !statuses.get(i).getAccount().getId().equals(userId))
                        MainActivity.countNewStatus++;
                }
            }
            statusListAdapter.notifyItemRangeInserted(0, inserted - 1);
            try {
                if( type == RetrieveFeedsAsyncTask.Type.HOME)
                    ((MainActivity) context).updateHomeCounter();
                else
                    ((MainActivity) context).updateTimeLine(type, inserted);
            }catch (Exception ignored){}
        }
    }


    /**
     * Records the id of the status only if its greater than the previous one.
     * @param sharedPreferences SharedPreferences
     * @param userId String current logged user
     * @param statusId String current status id to check
     */
    private void updateStatusLastId(SharedPreferences sharedPreferences, String userId, String statusId){
        String lastReadStatus = sharedPreferences.getString(Helper.LAST_HOMETIMELINE_MAX_ID + userId, null);
        if( lastReadStatus != null && statusId != null && Long.parseLong(statusId) > Long.parseLong(lastReadStatus)){
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(Helper.LAST_HOMETIMELINE_MAX_ID + userId, statusId);
            editor.apply();
        }
    }
}
