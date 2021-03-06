package com.lipata.forkauthority.businesslist;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.lipata.forkauthority.ForkAuthorityApp;
import com.lipata.forkauthority.R;
import com.lipata.forkauthority.api.GeocoderApi;
import com.lipata.forkauthority.api.GooglePlayApi;
import com.lipata.forkauthority.api.yelp3.entities.Business;
import com.lipata.forkauthority.data.AppSettings;
import com.lipata.forkauthority.data.ListComposer;
import com.lipata.forkauthority.data.user.UserRecords;
import com.lipata.forkauthority.util.Utility;

import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;

import javax.inject.Inject;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;
import timber.log.Timber;

public class BusinessListActivity extends AppCompatActivity implements BusinessListParentView {

    // Constants
    static final String LOCATION_UPDATE_TIMESTAMP_KEY = "mLocationUpdateTimestamp";
    static final String LOCATION_QUALITY_KEY = "locationQuality";
    static final String NO_RESULTS_TEXT_KEY = "noResultsText";
    static final String PROGRESS_BAR_BUSINESSES_KEY = "progressBarBusinesses";
    final static int MY_PERMISSIONS_ACCESS_FINE_LOCATION_ID = 0;

    BusinessListViewModel viewModel;
    @Inject BusinessListViewModelFactory viewModelFactory;
    @Inject GeocoderApi mGeocoder;
    @Inject GooglePlayApi mGooglePlayApi;
    @Inject UserRecords mUserRecords;
    @Inject ListComposer listComposer;

    // Views
    protected CoordinatorLayout mCoordinatorLayout;
    protected AppBarLayout appBarLayout;
    protected TextView mTextView_ApproxLocation;
    protected RecyclerView mRecyclerView_suggestionList;
    private LinearLayoutManager mSuggestionListLayoutManager;
    private BusinessListAdapter mSuggestionListAdapter;
    FloatingActionButton mFAB_refresh;
    ObjectAnimator mFAB_refreshAnimation;
    Snackbar mSnackbar;
    LocationQualityView mLocationQualityView;
    RelativeLayout mLayout_LocationViews;
    ProgressBar mProgressBar_Location;
    ProgressBar mProgressBar_Businesses;
    TextView mNoResultsTextView;
    ImageView mYelpLogo;

    // Analytics
    long mStartTime_Fetch;
    long mStartTime_Location;

    // Activity lifecycle

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ((ForkAuthorityApp) getApplication()).appComponent.inject(this);

        setContentView(R.layout.activity_business_list);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        viewModel = ViewModelProviders.of(this, viewModelFactory).get(BusinessListViewModel.class);
        viewModel.getListLiveData().observe(this, this::onFetchListState);
        viewModel.getLocationLiveData().observe(this, this::onLocationState);

        mCoordinatorLayout = findViewById(R.id.layout_coordinator);
        mTextView_ApproxLocation = findViewById(R.id.location_text);
        mLocationQualityView = new LocationQualityView(this, findViewById(R.id.accuracy_indicator));
        mLayout_LocationViews = findViewById(R.id.layout_location);
        mNoResultsTextView = findViewById(R.id.no_results);

        // Progress bar views
        mProgressBar_Location = findViewById(R.id.progress_bar_location);
        mProgressBar_Businesses = findViewById(R.id.progress_bar_businesses);

        // RecyclerView
        mRecyclerView_suggestionList = findViewById(R.id.suggestion_list);
        mRecyclerView_suggestionList.setHasFixedSize(true);
        mSuggestionListLayoutManager = new LinearLayoutManager(this);
        mRecyclerView_suggestionList.setLayoutManager(mSuggestionListLayoutManager);
        RecyclerView.ItemAnimator animator = mRecyclerView_suggestionList.getItemAnimator();
        if (animator instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) animator).setSupportsChangeAnimations(false);
        }

        mSuggestionListAdapter = new BusinessListAdapter(this, mUserRecords);
        mRecyclerView_suggestionList.setAdapter(mSuggestionListAdapter);

        ItemTouchHelper.Callback callback = new ListItemTouchHelper(mSuggestionListAdapter);
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(callback);
        itemTouchHelper.attachToRecyclerView(mRecyclerView_suggestionList);

        // Set up FAB and refresh animation
        mFAB_refresh = findViewById(R.id.fab);
        mFAB_refresh.setOnClickListener(view -> {
            if (mGooglePlayApi.isLocationStale()) {
                viewModel.fetchBusinessList();
            } else {
                Toast.makeText(BusinessListActivity.this, "Too soon. Please try again in a few seconds...", Toast.LENGTH_SHORT).show();
            }
        });
        mFAB_refreshAnimation = ObjectAnimator.ofFloat(mFAB_refresh, View.ROTATION, 360);
        mFAB_refreshAnimation.setDuration(1500);
        mFAB_refreshAnimation.setRepeatCount(ObjectAnimator.INFINITE);
        mFAB_refreshAnimation.setInterpolator(null);

        appBarLayout = findViewById(R.id.app_bar_layout);
        appBarLayout.addOnOffsetChangedListener(fabScrollAnimation(mFAB_refresh)); // Anchor FAB to AppBarLayout for scroll animation

        // Clickable Yelp logo in compliance with Terms of Use
        // https://www.yelp.com/developers/display_requirements
        mYelpLogo = findViewById(R.id.yelp_logo);
        mYelpLogo.setOnClickListener(v -> openYelpDotCom());

        // Location API
        mGooglePlayApi.setActivity(this);

        // Restore state
        if (savedInstanceState != null) {
            mGooglePlayApi.setLocationUpdateTimestamp(savedInstanceState.getLong(LOCATION_UPDATE_TIMESTAMP_KEY));
            mLocationQualityView.setAccuracyCircleStatus(savedInstanceState.getInt(LOCATION_QUALITY_KEY));
            mNoResultsTextView.setVisibility(savedInstanceState.getInt(NO_RESULTS_TEXT_KEY));
            mProgressBar_Businesses.setVisibility(savedInstanceState.getInt(PROGRESS_BAR_BUSINESSES_KEY));
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        viewModel.onStart();
    }

    @Override
    protected void onStop() {
        Timber.d("onStop()");
        super.onStop();
        if (mGooglePlayApi.getClient().isConnected()) {
            mGooglePlayApi.stopLocationUpdates();
        }
    }

    // UI methods

    @NotNull private AppBarLayout.OnOffsetChangedListener fabScrollAnimation(final View view) {
        return (appBarLayout1, i) -> {
            view.setTranslationY(-(float) i);
            view.setAlpha(calculateAlpha(i));
        };
    }

    /**
     * Used to calculate fade animation based on AppBarLayout scroll position
     *
     * @param i AppBarLayout scroll position
     * @return
     */
    private float calculateAlpha(int i) {
        return 1 + (i / 100f);
    }

    private void onLocationState(final LocationState locationState) {
        if (locationState instanceof LocationState.Loading) {
            onDeviceLocationRequested();
        } else if (locationState instanceof LocationState.Success) {
            setLocationText(((LocationState.Success) locationState).getLocation());
        }
    }

    private void onFetchListState(final FetchListState fetchListState) {
        if (fetchListState instanceof FetchListState.Success) {
            stopRefreshAnimation();
            mSuggestionListAdapter.setBusinessList(((FetchListState.Success) fetchListState).getList());
            mSuggestionListAdapter.notifyDataSetChanged();
            mRecyclerView_suggestionList.setVisibility(View.VISIBLE);
            trackSuccessAnalytics();
        } else if (fetchListState instanceof FetchListState.NoResults) {
            stopRefreshAnimation();
            mRecyclerView_suggestionList.setVisibility(View.GONE);
            mNoResultsTextView.setVisibility(View.VISIBLE);
        } else if (fetchListState instanceof FetchListState.Error) {
            stopRefreshAnimation();
            showSnackBarIndefinite(((FetchListState.Error) fetchListState).getThrowable().getMessage());
        } else if (fetchListState instanceof FetchListState.Loading) {
            fetchBusinessListLoading();
        }
    }

    void showNoInternetError() {
        showSnackBarIndefinite("No network. Try again when you are connected to the internet.");
        stopRefreshAnimation();
    }

    public void updateLocationViews(double latitude, double longitude, int accuracyQuality) {
        // Latitude range is 0 to +-90.  Longitude is 0 to +-180.
        // 6 decimal places is accurate to 43.496-111.32 mm
        // https://en.wikipedia.org/wiki/Decimal_degrees#Precision
        mTextView_ApproxLocation.setText(new DecimalFormat("##.######").format(latitude) + ", "
                + new DecimalFormat("###.######").format(longitude));
        mLocationQualityView.setAccuracyCircleStatus(accuracyQuality);
    }

    public void startRefreshAnimation() {
        mProgressBar_Businesses.setVisibility(View.VISIBLE);

        if (!mFAB_refreshAnimation.isRunning()) {
            mFAB_refreshAnimation.start();
        }
    }

    public void stopRefreshAnimation() {
        mProgressBar_Businesses.setVisibility(View.GONE);
        mProgressBar_Location.setVisibility(View.GONE);
        mFAB_refreshAnimation.cancel();
    }

    public void showSnackBarIndefinite(String text) {
        mSnackbar = Snackbar.make(mCoordinatorLayout, text, Snackbar.LENGTH_INDEFINITE);
        mSnackbar.show();
    }

    public void showToast(String text) {
        Toast.makeText(BusinessListActivity.this, text, Toast.LENGTH_SHORT).show();
    }

    public void setLocationText(String text) {
        mTextView_ApproxLocation.setText(text);
    }


    /**
     * This gets called first, newBusinessList next
     */
    public void onDeviceLocationRequested() {
        mStartTime_Location = System.nanoTime();

        mLayout_LocationViews.setVisibility(View.GONE);
        mProgressBar_Location.setVisibility(View.VISIBLE);

        // Reset progress text for both business list and location
        mTextView_ApproxLocation.setText(getResources().getText(R.string.getting_your_location));

        mLocationQualityView.setAccuracyCircleStatus(LocationQualityView.Status.HIDDEN);
    }

    public void onDeviceLocationRetrieved() {
        mProgressBar_Location.setVisibility(View.GONE);
        mLayout_LocationViews.setVisibility(View.VISIBLE);

        Utility.reportExecutionTime(this, AppSettings.FABRIC_METRIC_GOOGLEPLAYAPI, mStartTime_Location);
        logFabricAnswersMetric(AppSettings.FABRIC_METRIC_GOOGLEPLAYAPI, mStartTime_Location);
    }

    public void trackSuccessAnalytics() {
        // Analytics
        Utility.reportExecutionTime(this, "Fetch businesses until displayed", mStartTime_Fetch);
        logFabricAnswersMetric(AppSettings.FABRIC_METRIC_FETCH_BIZ_SEQUENCE, mStartTime_Fetch);
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
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case GooglePlayApi.REQUEST_CHECK_SETTINGS:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        // All required changes were successfully made

                        Timber.d("onActivityResult() RESULT_OK");
                        viewModel.executeGooglePlayApiLocation();

                        break;
                    case Activity.RESULT_CANCELED:
                        // The user was asked to change settings, but chose not to

                        Timber.d("onActivityResult() RESULT_CANCELED");

                        stopRefreshAnimation();
                        showSnackBarIndefinite("Location settings error");

                        break;
                    default:
                        break;
                }
                break;
        }
    }


    public void fetchBusinessListLoading() {
        mStartTime_Fetch = System.nanoTime();

        // UI

        // Dismiss any Snackbars
        if (mSnackbar != null) {
            mSnackbar.dismiss();
        }

        // Clear recyclerview
        mSuggestionListAdapter.setBusinessList(null);
        mSuggestionListAdapter.notifyDataSetChanged();

        mNoResultsTextView.setVisibility(View.GONE);

        startRefreshAnimation();
    }


    /**
     * Fabric Answers Custom Event
     *
     * @param metricName
     * @param startTime  In nanoseconds, will be converted to milliseconds
     */
    public void logFabricAnswersMetric(String metricName, long startTime) {
        long executionTime = System.nanoTime() - startTime;
        long executionTime_ms = executionTime / 1000000;

        // TODO Fabric deprecated. Replace this
        // Answers.getInstance().logCustom(new CustomEvent(metricName)
        //         .putCustomAttribute("Execution time (ms)", executionTime_ms));
    }


    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putLong(LOCATION_UPDATE_TIMESTAMP_KEY, mGooglePlayApi.getLocationUpdateTimestamp());
        savedInstanceState.putInt(LOCATION_QUALITY_KEY, mLocationQualityView.getStatus());
        savedInstanceState.putInt(NO_RESULTS_TEXT_KEY, mNoResultsTextView.getVisibility());
        savedInstanceState.putInt(PROGRESS_BAR_BUSINESSES_KEY, mProgressBar_Businesses.getVisibility());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            openSettingsScreen();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void openSettingsScreen() {
        final Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    public boolean isNetworkConnected() {
        // Check for network connectivity
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }

    public void onNoResults() {
        stopRefreshAnimation();
        mRecyclerView_suggestionList.setVisibility(View.GONE);
        mNoResultsTextView.setVisibility(View.VISIBLE);
    }

    public void showSnackBarLongWithAction(
            final String message,
            final String actionLabel,
            final int position,
            final Business business) {

        mSnackbar = Snackbar.make(mCoordinatorLayout, message, Snackbar.LENGTH_LONG)
                .setAction(actionLabel, v -> mSuggestionListAdapter.undoDismiss(position, business))
                .setActionTextColor(getResources().getColor(R.color.text_white));
        mSnackbar.show();
    }

    public void showSnackBarLong(String text) {
        mSnackbar = Snackbar.make(mCoordinatorLayout, text, Snackbar.LENGTH_LONG);
        mSnackbar.show();
    }


    private void openYelpDotCom() {
        Uri webpage = Uri.parse("http://yelp.com");
        Intent intent = new Intent(Intent.ACTION_VIEW, webpage);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        }
    }

    @Override
    public void notifyUserTooSoon(String businessName) {
        showSnackBarLong("Noted. You just ate at "
                + businessName
                + getString(R.string.moved_to_toosoon));
    }

    @Override
    public void notifyUserBusinessLiked(@NotNull String businessName) {
        showSnackBarLong("Noted. You like " + businessName
                + ". I have moved this to the top of the list."); // TODO Extract resource
    }

    @Override
    public void notifyUserBusinessDismissed(int position, @NotNull Business business) {
        showSnackBarLongWithAction(business.getName() + " dismissed.",
                "UNDO",
                position,
                business);
    }

    @Override
    public void notifyUserBusinessDontLiked(@NotNull String businessName) {
        showSnackBarLong("Noted. You don't like "
                + businessName
                + getString(R.string.moved_to_bottom));
    }

    @Override
    public void notifyNotAllowedOnDontLike() {
        showToast("Not allowed on a restaurant that you don't like.");
    }

    @Override
    public void launchBusinessUrl(@NotNull String url) {
        final Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(browserIntent);
    }

    @NotNull
    @Override
    public Drawable getRatingDrawable(@NotNull String rating) {
        switch (rating) {
            case "5.0":
                return getResources().getDrawable(R.drawable.stars_small_5);
            case "4.5":
                return getResources().getDrawable(R.drawable.stars_small_4_half);
            case "4.0":
                return getResources().getDrawable(R.drawable.stars_small_4);
            case "3.5":
                return getResources().getDrawable(R.drawable.stars_small_3_half);
            case "3.0":
                return getResources().getDrawable(R.drawable.stars_small_3);
            case "2.5":
                return getResources().getDrawable(R.drawable.stars_small_2_half);
            case "2.0":
                return getResources().getDrawable(R.drawable.stars_small_2);
            case "1.5":
                return getResources().getDrawable(R.drawable.stars_small_1_half);
            case "1.0":
                return getResources().getDrawable(R.drawable.stars_small_1);
            default:
                return getResources().getDrawable(R.drawable.stars_small_0);
        }
    }

    // region Getters

    @Override
    public LinearLayoutManager getRecyclerViewLayoutManager() {
        return mSuggestionListLayoutManager;
    }

    public BusinessListViewModel getViewModel() {
        return viewModel;
    }

    @Override
    public Context getContext() {
        return this;
    }

    // endregion
}
