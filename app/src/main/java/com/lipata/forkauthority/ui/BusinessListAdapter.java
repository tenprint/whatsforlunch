package com.lipata.forkauthority.ui;

import android.content.Intent;
import android.net.Uri;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.lipata.forkauthority.R;
import com.lipata.forkauthority.api.yelp.entities.Business;
import com.lipata.forkauthority.data.BusinessListManager;
import com.lipata.forkauthority.data.user.BusinessItemRecord;
import com.lipata.forkauthority.data.user.UserRecords;
import com.squareup.picasso.Picasso;

import java.util.List;

/**
 * Created by jlipata on 1/1/16.
 * TODO There's a lot of stuff in this class that should be in the model or data classes. Clean it up!
 */
public class BusinessListAdapter extends RecyclerView.Adapter<BusinessListAdapter.ViewHolder> {
    private static final String LOG_TAG = BusinessListAdapter.class.getSimpleName();

    // Button IDs
    public static final int LIKE = 0;
    public static final int TOOSOON = 1;
    public static final int DONTLIKE = 2;
    public static final int DISMISS = 3;

    private List<Business> mBusinessList;
    private MainActivity mMainActivity;
    private CoordinatorLayout mCoordinatorLayout;
    private RecyclerView.LayoutManager mLayoutManager;
    UserRecords mUserRecords;
    BusinessListManager mBusinessListManager;

    public BusinessListAdapter(MainActivity mainActivity,
                               UserRecords userRecords, BusinessListManager businessListManager){
        this.mMainActivity = mainActivity;
        this.mCoordinatorLayout = mainActivity.getCoordinatorLayout();
        this.mUserRecords = userRecords;
        this.mBusinessListManager = businessListManager;
        this.mLayoutManager = mainActivity.getRecyclerViewLayoutManager();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        public LinearLayout mLayout_BusinessHeader;
        public CardView mCardView_CardView;
        public ImageView mImageView_BusinessImage;
        public TextView mTextView_BusinessName;
        public TextView mTextView_BusinessCategories;
        public TextView mTextView_BusinessAddress;
        public ImageView mImageView_BusinessRatingUrl;
        public TextView mTextView_BusinessReviewCount;

        public Button mButton_TooSoon;
        public Button mButton_Like;
        public Button mButton_DontLike;

        public TextView mTextView_DescriptiveText;

        public ViewHolder(View v) {
            super(v);
            mLayout_BusinessHeader = (LinearLayout) v.findViewById(R.id.business_header_layout);
            mCardView_CardView = (CardView) v.findViewById(R.id.card_view);
            mImageView_BusinessImage = (ImageView) v.findViewById(R.id.business_image);
            mTextView_BusinessName = (TextView) v.findViewById(R.id.business_name);
            mTextView_BusinessCategories = (TextView) v.findViewById(R.id.business_categories);
            mTextView_BusinessAddress = (TextView) v.findViewById(R.id.business_address);
            mImageView_BusinessRatingUrl = (ImageView) v.findViewById(R.id.business_rating);
            mTextView_BusinessReviewCount = (TextView) v.findViewById(R.id.business_review_count);

            mButton_TooSoon = (Button) v.findViewById(R.id.button_toosoon);
            mButton_Like = (Button) v.findViewById(R.id.button_like);
            mButton_DontLike = (Button) v.findViewById(R.id.button_dontlike);

            mTextView_DescriptiveText = (TextView) v.findViewById(R.id.business_descriptive_text);
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.suggestion_list_item, parent, false);
        ViewHolder vh = new ViewHolder(itemView);
        return vh;
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, final int position) {

        // Get business at `position` index. This object's fields will be used to populate UI views
        final Business business = mBusinessList.get(position);

        // Business Header Layout - clickable
        holder.mLayout_BusinessHeader.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(business.getUrl()));
                mMainActivity.startActivity(browserIntent);
            }
        });

        // Business image
        Picasso.with(mMainActivity)
                .load(business.getImageUrl()).fit()
                .into(holder.mImageView_BusinessImage);

        // Business name
        holder.mTextView_BusinessName.setText(position + 1 + ". " + business.getName());

        // Business rating image
        Picasso.with(mMainActivity)
                .load(business.getRatingImgUrlLarge()).fit()
                .into(holder.mImageView_BusinessRatingUrl);

        // Business review count
        holder.mTextView_BusinessReviewCount.setText(business.getReviewCount() + " Reviews");

        holder.mTextView_BusinessCategories.setText(business.getFormattedCategories());

        holder.mTextView_BusinessAddress.setText(business.getLocation().getFormattedDisplayAddress());

        // Dynamically add text based on UserRecords

        // Show descriptiveText , if present
        String descriptiveText = business.getDescriptiveText();
        if(descriptiveText!=null){
            holder.mTextView_DescriptiveText.setVisibility(View.VISIBLE);
            holder.mTextView_DescriptiveText.setText(descriptiveText);
        } else // If there's no descriptive text, hide the line separator and the layout
        {
            holder.mTextView_DescriptiveText.setVisibility(View.GONE);
        }

        // Like button dynamic icon
        if(business.getDontLikeClickDate()==-1){
            holder.mButton_Like.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.thumb_up_fill, 0, 0);
        } else {
            holder.mButton_Like.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.thumb_up_outline, 0, 0);
        }

        // Dont Like button dynamic icon
        if(business.getDontLikeClickDate()>0){
            holder.mButton_DontLike.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.thumb_down_fill, 0, 0);
        } else {
            holder.mButton_DontLike.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.thumb_down_outline, 0, 0);
        }

        // Click listeners
        holder.mButton_Like.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if(business.getDontLikeClickDate()!= BusinessItemRecord.LIKE_FLAG) {

                    // Update UserRecords
                    mUserRecords.updateClickDate(business, BusinessItemRecord.LIKE_FLAG, LIKE);
                    mUserRecords.commit();

                    // Update object field
                    business.setDontLikeClickDate(BusinessItemRecord.LIKE_FLAG);
                    Log.d(LOG_TAG, "Updated dontLikeClickDate for " + business.getName() + " to " + business.getDontLikeClickDate());

                    // UI stuff
                    if (position != 0) {

                        mBusinessList.remove(position);

                        // Update RecyclerView item (triggers animation)
                        notifyItemRemoved(position);

                        // Add business to top of list
                        mBusinessList.add(0, business);

                        // Update other items in RecyclerView (this updates the item numbers in each CardView)
                        notifyItemRangeChanged(0, getItemCount());
                    } else {
                        notifyItemChanged(0);
                    }

                    Snackbar.make(mCoordinatorLayout,
                            "Noted. You like " + business.getName() + ". I have moved this to the top of the list.",
                            Snackbar.LENGTH_LONG).show();
                } else {
                    // Unlike

                    // Update UserRecords
                    mUserRecords.updateClickDate(business, 0, LIKE);
                    mUserRecords.commit();

                    // Update object field
                    business.setDontLikeClickDate(0);
                    Log.d(LOG_TAG, "Updated dontLikeClickDate for " + business.getName() + " to " + business.getDontLikeClickDate());

                    notifyItemChanged(position);

                }

            }
        });

        holder.mButton_TooSoon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Get business and hold in temp variable
                Business business = mBusinessList.get(position);

                // Remove existing element
                mBusinessList.remove(position);

                // Update RecyclerView item (triggers animation)
                notifyItemRemoved(position);

                // Add business to bottom of list
                mBusinessList.add(business);

                // Update other items in RecyclerView (this updates the item numbers in each CardView)
                notifyItemRangeChanged(position, getItemCount());

                // Notify user
                Snackbar.make(mCoordinatorLayout,
                        "Noted. You just ate at " + business.getName() + mMainActivity.getString(R.string.moved_to_bottom),
                        Snackbar.LENGTH_LONG).show();

                // Get current date/time
                long systemTime_ms = System.currentTimeMillis();

                // Update user records
                mUserRecords.updateClickDate(business, systemTime_ms, TOOSOON);
                mUserRecords.commit();

                // Update object field
                business.setTooSoonClickDate(systemTime_ms);
                Log.d(LOG_TAG, "Updated tooSoonClickDate for " + business.getName() + " to " + systemTime_ms);

            }
        });

        holder.mButton_DontLike.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if(business.getDontLikeClickDate()<=0) {

                    // Get business and hold in temp variable
                    Business business = mBusinessList.get(position);

                    // Remove existing element
                    mBusinessList.remove(position);

                    // Update RecyclerView item (triggers animation)
                    notifyItemRemoved(position);

                    // Add business to bottom of list
                    mBusinessList.add(business);

                    // Update other items in RecyclerView (this updates the item numbers in each CardView)
                    notifyItemRangeChanged(position, getItemCount());

                    // Notify user
                    Snackbar.make(mCoordinatorLayout,
                            "Noted. You don't like " + business.getName() + mMainActivity.getString(R.string.moved_to_bottom),
                            Snackbar.LENGTH_LONG).show();

                    // Get current date/time
                    long systemTime_ms = System.currentTimeMillis();

                    // Update user records
                    mUserRecords.updateClickDate(business, systemTime_ms, DONTLIKE);
                    mUserRecords.commit();

                    // Update object field
                    business.setDontLikeClickDate(systemTime_ms);
                    Log.d(LOG_TAG, "Updated dontLikeClickDate for " + business.getName() + " to " + systemTime_ms);

                } else {
                    // Un-Don't Like

                    // Update UserRecords
                    mUserRecords.updateClickDate(business, 0, DONTLIKE);
                    mUserRecords.commit();

                    // Update object field
                    business.setDontLikeClickDate(0);
                    Log.d(LOG_TAG, "Updated dontLikeClickDate for " + business.getName() + " to " + business.getDontLikeClickDate());

                    notifyItemChanged(position);
                }
            }
        });

    }

    public void setBusinessList(List<Business> businesses){
        this.mBusinessList = businesses;
    }

    public List<Business> getBusinessList(){
        return mBusinessList;
    }
    
    @Override
    public int getItemCount() {
        if(mBusinessList==null){
            return 0;
        } else {
            return mBusinessList.size();
        }
    }

    public void dismiss(final int position){

        // UI Stuff:
        // Get business and hold in temp variable
        final Business business = mBusinessList.get(position);

        // Remove existing element
        mBusinessList.remove(position);

        // Update RecyclerView item (triggers animation)
        notifyItemRemoved(position);

        // Add business to bottom of list

        // Temporarily changing functionality.  Trying out removing the card completely instead of moving to bottom
        // mBusinessList.add(business);

        // Update other items in RecyclerView (this updates the item numbers in each CardView)
        notifyItemRangeChanged(position, getItemCount());

        Snackbar.make(mCoordinatorLayout, business.getName()+" dismissed.", Snackbar.LENGTH_LONG)
                .setAction("UNDO", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mBusinessList.add(position, business);
                        notifyItemInserted(position);
                        notifyItemRangeChanged(position, getItemCount());

                        if(position==0){
                            mLayoutManager.scrollToPosition(0);
                        }
                    }
                })
                .setActionTextColor(mMainActivity.getResources().getColor(R.color.text_white))
                .show();

        // Update user records
        mUserRecords.incrementDismissedCount(business);
        mUserRecords.commit();

    }


}