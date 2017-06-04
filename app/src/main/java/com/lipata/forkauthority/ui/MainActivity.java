package com.lipata.forkauthority.ui;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.lipata.forkauthority.AppComponent;
import com.lipata.forkauthority.AppModule;
import com.lipata.forkauthority.DaggerAppComponent;
import com.lipata.forkauthority.R;
import com.lipata.forkauthority.Utility;
import com.lipata.forkauthority.api.GeocoderApi;
import com.lipata.forkauthority.api.GooglePlayApi;
import com.lipata.forkauthority.api.yelp.entities.Business;
import com.lipata.forkauthority.data.AppSettings;
import com.lipata.forkauthority.data.BusinessListManager;
import com.lipata.forkauthority.data.user.UserRecords;

import java.lang.reflect.Type;
import java.text.DecimalFormat;
import java.util.List;

import javax.inject.Inject;

import io.fabric.sdk.android.Fabric;

/**
 * This Android app gets device location, queries the Yelp API for restaurant recommendations,
 * and uses GSON to parse and display the response.
 */

public class MainActivity extends AppCompatActivity implements MainView {
    private static final String LOG_TAG = MainActivity.class.getSimpleName();

    // Constants
    static final String LOCATION_UPDATE_TIMESTAMP_KEY = "mLocationUpdateTimestamp";
    static final String SUGGESTIONLIST_KEY = "suggestionList";
    static final String LOCATION_QUALITY_KEY = "locationQuality";
    static final int MY_PERMISSIONS_ACCESS_FINE_LOCATION_ID = 0;

    // App modules
    AppComponent component;
    @Inject GeocoderApi mGeocoder;
    @Inject MainPresenter presenter;
    @Inject GooglePlayApi mGooglePlayApi;
    UserRecords mUserRecords;
    BusinessListManager mBusinessListManager;

    // Views
    protected CoordinatorLayout mCoordinatorLayout;
    protected TextView mTextView_ApproxLocation;
    protected RecyclerView mRecyclerView_suggestionList;
    private LinearLayoutManager mSuggestionListLayoutManager;
    private BusinessListAdapter mSuggestionListAdapter;
    FloatingActionButton mFAB_refresh;
    ObjectAnimator mFAB_refreshAnimation;
    Snackbar mSnackbar;
    FrameLayout mLayout_ProgressBar_Location;
    LocationQualityView mLocationQualityView;
    LinearLayout mProgressBarLayout;
    RelativeLayout mLayout_LocationViews;
    ProgressBar mProgressBar_Location;
    TextView mTextView_Progress_Businesses;
    ProgressBar mProgressBar_Businesses;

    // Analytics
    long mStartTime_Fetch;
    long mStartTime_Location;

    // Activity lifecycle

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        component = DaggerAppComponent
                .builder()
                .appModule(new AppModule(getApplication())).build();
        component.inject(this);

        Fabric.with(this, new Crashlytics());

        final Fabric fabric = new Fabric.Builder(this)
                .kits(new Answers())
                .debuggable(true)
                .build();
        Fabric.with(fabric);

        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        presenter.setView(this);
        mUserRecords = new UserRecords(this);
        mBusinessListManager = new BusinessListManager(this, mUserRecords);

        mCoordinatorLayout = (CoordinatorLayout) findViewById(R.id.layout_coordinator);
        mTextView_ApproxLocation = (TextView) findViewById(R.id.location_text);
        mLocationQualityView = new LocationQualityView(this, (ImageView) findViewById(R.id.accuracy_indicator));
        mLayout_LocationViews = (RelativeLayout) findViewById(R.id.layout_location);

        // Progress bar views
        mProgressBarLayout = (LinearLayout) findViewById(R.id.progressbar_layout);
        mProgressBarLayout.setVisibility(View.GONE);
        mProgressBar_Location = (ProgressBar) findViewById(R.id.progress_bar_location);
        mTextView_Progress_Businesses = (TextView) findViewById(R.id.textview_progress_loadbusinesses);
        mProgressBar_Businesses = (ProgressBar) findViewById(R.id.progress_bar_businesses);
        mLayout_ProgressBar_Location = (FrameLayout) findViewById(R.id.layout_progress_bar_location);


        // RecyclerView
        mRecyclerView_suggestionList = (RecyclerView) findViewById(R.id.suggestion_list);
        mRecyclerView_suggestionList.setHasFixedSize(true);
        mSuggestionListLayoutManager = new LinearLayoutManager(this);
        mRecyclerView_suggestionList.setLayoutManager(mSuggestionListLayoutManager);

        mSuggestionListAdapter = new BusinessListAdapter(this, mUserRecords, mBusinessListManager);
        mRecyclerView_suggestionList.setAdapter(mSuggestionListAdapter);

        ItemTouchHelper.Callback callback = new ListItemTouchHelper(mSuggestionListAdapter);
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(callback);
        itemTouchHelper.attachToRecyclerView(mRecyclerView_suggestionList);

        mRecyclerView_suggestionList.addOnScrollListener(new BusinessListScrollListener(mSuggestionListLayoutManager));


        // Set up FAB and refresh animation
        mFAB_refresh = (FloatingActionButton) findViewById(R.id.fab);
        mFAB_refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mGooglePlayApi.isLocationStale()) {
                    fetchBusinessList();
                } else {
                    Toast.makeText(MainActivity.this, "Too soon. Please try again in a few seconds...", Toast.LENGTH_SHORT).show();
                }
            }
        });
        mFAB_refreshAnimation = ObjectAnimator.ofFloat(mFAB_refresh, View.ROTATION, 360);
        mFAB_refreshAnimation.setDuration(1500);
        mFAB_refreshAnimation.setRepeatCount(ObjectAnimator.INFINITE);
        mFAB_refreshAnimation.setInterpolator(null);

        // Location API
        mGooglePlayApi.setActivity(this);

        // Restore state
        if (savedInstanceState != null) {
            mGooglePlayApi.setLocationUpdateTimestamp(savedInstanceState.getLong(LOCATION_UPDATE_TIMESTAMP_KEY));
            mLocationQualityView.setAccuracyCircleStatus(savedInstanceState.getInt(LOCATION_QUALITY_KEY));

            String storedSuggestionList = savedInstanceState.getString(SUGGESTIONLIST_KEY, null);
            if (storedSuggestionList != null) {
                Type listType = new TypeToken<List<Business>>() {
                }.getType();
                List<Business> retrievedBusinessList = new Gson().fromJson(storedSuggestionList, listType);
                mSuggestionListAdapter.setBusinessList(retrievedBusinessList);
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Check whether there are suggestion items in the RecyclerView.  If not, load some.
        if (mSuggestionListAdapter.getItemCount() == 0) {
            fetchBusinessList();
        }
    }

    @Override
    protected void onStop() {
        Log.d(LOG_TAG, "onStop()");
        super.onStop();
        if (mGooglePlayApi.getClient().isConnected()) {
            mGooglePlayApi.stopLocationUpdates();
        }
    }

    // UI methods

    @Override
    public void updateLocationViews(double latitude, double longitude, int accuracyQuality) {
        // Latitude range is 0 to +-90.  Longitude is 0 to +-180.
        // 6 decimal places is accurate to 43.496-111.32 mm
        // https://en.wikipedia.org/wiki/Decimal_degrees#Precision
        mTextView_ApproxLocation.setText(new DecimalFormat("##.######").format(latitude) + ", "
                + new DecimalFormat("###.######").format(longitude));
        //mTextView_Accuracy.setText(Float.toString(accuracy) + " meters");
        mLocationQualityView.setAccuracyCircleStatus(accuracyQuality);
    }

    @Override
    public void startRefreshAnimation() {
        Log.d(LOG_TAG, "Starting animation");
        if (!mFAB_refreshAnimation.isRunning()) {
            mFAB_refreshAnimation.start();
        }
    }

    @Override
    public void stopRefreshAnimation() {
        Log.d(LOG_TAG, "Stop animation");
        mFAB_refreshAnimation.cancel();
    }

    @Override
    public void showSnackBarIndefinite(String text) {
        mSnackbar = Snackbar.make(mCoordinatorLayout, text, Snackbar.LENGTH_INDEFINITE);
        mSnackbar.show();
    }

    @Override
    public void showToast(String text) {
        Toast.makeText(MainActivity.this, text, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void setLocationText(String text) {
        mTextView_ApproxLocation.setText(text);
    }


    /**
     * This gets called first, newBusinessList next
     */
    @Override
    public void onDeviceLocationRequested() {
        mStartTime_Location = System.nanoTime();

        mLayout_LocationViews.setVisibility(View.GONE);
        mLayout_ProgressBar_Location.setVisibility(View.VISIBLE);

        // Reset progress text for both business list and location
        mTextView_Progress_Businesses.setText(getResources().getText(R.string.loading_businesses));
        mTextView_ApproxLocation.setText(getResources().getText(R.string.getting_your_location));

        mLocationQualityView.setAccuracyCircleStatus(LocationQualityView.Status.HIDDEN);
    }

    @Override
    public void onDeviceLocationRetrieved() {
        mLayout_ProgressBar_Location.setVisibility(View.GONE);
        mLayout_LocationViews.setVisibility(View.VISIBLE);

        Utility.reportExecutionTime(this, AppSettings.FABRIC_METRIC_GOOGLEPLAYAPI, mStartTime_Location);
        logFabricAnswersMetric(AppSettings.FABRIC_METRIC_GOOGLEPLAYAPI, mStartTime_Location);
    }

    @Override
    public void onNewBusinessListRequested() {
        if (mProgressBarLayout.getVisibility() != View.VISIBLE) {
            mProgressBarLayout.setVisibility(View.VISIBLE);
        }
        mProgressBar_Businesses.setSecondaryProgress(0);
        mProgressBar_Businesses.setProgress(0);
        mTextView_Progress_Businesses.setText(getResources().getText(R.string.loading_businesses));
        mProgressBar_Businesses.setVisibility(View.VISIBLE);
    }

    @Override
    public void onNewBusinessListReceived() {
        // UI
        mTextView_Progress_Businesses.setText(getResources().getText(R.string.loading_businesses) + "OK");
        mProgressBar_Businesses.setVisibility(View.GONE);

        // Analytics
        Utility.reportExecutionTime(this, "Fetch businesses until displayed", mStartTime_Fetch);
        logFabricAnswersMetric(AppSettings.FABRIC_METRIC_FETCH_BIZ_SEQUENCE, mStartTime_Fetch);
    }

    @Override
    public void incrementProgress_BusinessProgressBar(int value) {
        int currentValue = mProgressBar_Businesses.getProgress();
        int maxValue = mProgressBar_Businesses.getMax();

        int newProgress = currentValue + value;

        if (newProgress <= maxValue) {
            mProgressBar_Businesses.setProgress(newProgress);
        } else {
            Log.d(LOG_TAG, "Business Progress Bar setProgress() ALREADY MAXED");
        }
    }

    @Override
    public void incrementSecondaryProgress_BusinessProgressBar(int value) {
        int currentValue = mProgressBar_Businesses.getSecondaryProgress();
        int maxValue = mProgressBar_Businesses.getMax();

        int newProgress = currentValue + value;

        if (newProgress <= maxValue) {
            mProgressBar_Businesses.setSecondaryProgress(newProgress);
        } else {
            Log.d(LOG_TAG, "Business Progress Bar setSecondaryProgress() ALREADY MAXED");
        }
    }

    @Override
    public void hideProgressLayout() {
        mProgressBarLayout.setVisibility(View.GONE);
    }


    // Callback for Marshmallow requestPermissions() response
    // This must live in the Activity class
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_ACCESS_FINE_LOCATION_ID: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    if (mGooglePlayApi.getClient().isConnected()) {
                        mGooglePlayApi.requestLocationUpdates();
                    } else {
                        mGooglePlayApi.getClient().connect();
                    }

                } else {
                    stopRefreshAnimation();
                    showSnackBarIndefinite("Location permission required");
                }
                return;
            }
        }
    }

    // Callback for GooglePlayApi Settings API
    // This must live in the Activity class
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case GooglePlayApi.REQUEST_CHECK_SETTINGS:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        // All required changes were successfully made

                        Log.d(LOG_TAG, "onActivityResult() RESULT_OK");
                        presenter.executeGooglePlayApiLocation();

                        break;
                    case Activity.RESULT_CANCELED:
                        // The user was asked to change settings, but chose not to

                        Log.d(LOG_TAG, "onActivityResult() RESULT_CANCELED");

                        stopRefreshAnimation();
                        showSnackBarIndefinite("Location settings error");

                        break;
                    default:
                        break;
                }
                break;
        }
    }


    // Trigger location + yelp calls
    @Override
    public void fetchBusinessList() {
        mStartTime_Fetch = System.nanoTime();

        // UI

        // Dismiss any Snackbars
        if (mSnackbar != null) {
            mSnackbar.dismiss();
        }

        // Clear recyclerview
        mSuggestionListAdapter.setBusinessList(null);
        mSuggestionListAdapter.notifyDataSetChanged();

        startRefreshAnimation();

        presenter.onFetchBusinessList();
    }


    /**
     * Fabric Answers Custom Event
     *
     * @param metricName
     * @param startTime  In nanoseconds, will be converted to milliseconds
     */
    @Override
    public void logFabricAnswersMetric(String metricName, long startTime) {
        long executionTime = System.nanoTime() - startTime;
        long executionTime_ms = executionTime / 1000000;
        Answers.getInstance().logCustom(new CustomEvent(metricName)
                .putCustomAttribute("Execution time (ms)", executionTime_ms));
    }


    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putLong(LOCATION_UPDATE_TIMESTAMP_KEY, mGooglePlayApi.getLocationUpdateTimestamp());
        savedInstanceState.putInt(LOCATION_QUALITY_KEY, mLocationQualityView.getStatus());

        // TODO There must be a better way to do this
        String suggestionListStr = new Gson().toJson(mSuggestionListAdapter.getBusinessList());
        savedInstanceState.putString(SUGGESTIONLIST_KEY, suggestionListStr);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        //getMenuInflater().inflate(R.menu.menu_main, menu);
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    // Getters

    //TODO RecyclerView.LayoutManager has been replaced by android.support.v7.widget.LinearLayoutManager.  For some reason this still works, but it could cause problems later.
    @Override
    public RecyclerView.LayoutManager getRecyclerViewLayoutManager() {
        return mSuggestionListLayoutManager;
    }

    @Override
    public CoordinatorLayout getCoordinatorLayout() {
        return mCoordinatorLayout;
    }

    @Override
    public BusinessListAdapter getSuggestionListAdapter() {
        return mSuggestionListAdapter;
    }

    @Override
    public BusinessListManager getBusinessListManager() {
        return mBusinessListManager;
    }

    public MainPresenter getPresenter() {
        return presenter;
    }
}