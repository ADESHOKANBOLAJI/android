

package com.bitlove.fetlife.view.adapter.feed;

import android.content.Context;
import android.net.Uri;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bitlove.fetlife.FetLifeApplication;
import com.bitlove.fetlife.R;
import com.bitlove.fetlife.model.pojos.FeedEvent;
import com.bitlove.fetlife.model.pojos.Member;
import com.bitlove.fetlife.model.pojos.PictureInterface;
import com.bitlove.fetlife.model.pojos.Story;
import com.bitlove.fetlife.util.DateUtil;
import com.facebook.drawee.view.SimpleDraweeView;
import com.stfalcon.frescoimageviewer.ImageViewer;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

public class FeedImageAdapterBinder {

    private final FeedRecyclerAdapter feedRecyclerAdapter;

    private SparseArray<Boolean> expandHistory = new SparseArray<>();

    public FeedImageAdapterBinder(FeedRecyclerAdapter feedRecyclerAdapter) {
        this.feedRecyclerAdapter = feedRecyclerAdapter;
    }

    public void bindImageStory(FetLifeApplication fetLifeApplication, final FeedViewHolder feedViewHolder, Story story, final FeedRecyclerAdapter.OnFeedItemClickListener onItemClickListener) {

        FeedItemResourceHelper feedItemResourceHelper = new FeedItemResourceHelper(fetLifeApplication, story.getType());

        List<FeedEvent> events = story.getEvents();
        final Member member = feedItemResourceHelper.getMember(events);

        if (member == null) {
            throw new IllegalArgumentException();
        }

        Context context = feedViewHolder.avatarImage.getContext();

        final int position = feedViewHolder.getAdapterPosition();

        String title = feedItemResourceHelper.getHeader(events);
        if (title == null) {
            throw new IllegalArgumentException();
        }
        feedViewHolder.feedText.setText(title);

        feedViewHolder.nameText.setText(member.getNickname());
        feedViewHolder.metaText.setText(member.getMetaInfo());

        String createdAt = feedItemResourceHelper.getCreatedAt(events.get(0));

        feedViewHolder.timeText.setText(createdAt);

        feedViewHolder.avatarImage.setBackgroundResource(R.drawable.dummy_avatar);

        String avatarLink = member.getAvatarLink();
        if (avatarLink == null) {
            feedViewHolder.avatarImage.setImageURI((String)null);
        } else {
            Uri avatarUri = Uri.parse(avatarLink);
            feedViewHolder.avatarImage.setImageURI(avatarUri);
        }
        feedViewHolder.avatarImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onItemClickListener.onMemberClick(member);
            }
        });

        final int expandableResourceId = events.size() == 1 ? R.id.feeditem_list_expandable : R.id.feeditem_grid_expandable;
        feedViewHolder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                View view2Expand = v.findViewById(expandableResourceId);
                View separator = v.findViewById(R.id.feeditem_separator);

                boolean visible = view2Expand.getVisibility() == View.VISIBLE;
                int newVisibility = visible ? View.GONE : View.VISIBLE;

                view2Expand.setVisibility(newVisibility);
                separator.setVisibility(newVisibility);

                expandHistory.put(position,!visible);
            }
        });


        if (listLayout(events, feedItemResourceHelper)) {

            LinearLayout linearLayout = feedViewHolder.listExpandArea;

            addViews(events, linearLayout, onItemClickListener, feedItemResourceHelper);

            boolean expandByPreference = fetLifeApplication.getUserSessionManager().getActiveUserPreferences().getBoolean(context.getString(R.string.settings_key_feed_auto_expand_like),false);
            boolean expanded = expandHistory.get(position,expandByPreference);
            linearLayout.setVisibility(expanded ? View.VISIBLE : View.GONE);
            feedViewHolder.separatorView.setVisibility(expanded ? View.VISIBLE : View.GONE);
            feedViewHolder.gridExpandArea.setVisibility(View.GONE);

        } else {

            feedViewHolder.gridExpandArea.setAdapter(new PictureGridAdapter(events, feedItemResourceHelper, onItemClickListener));

            boolean expandByPreference = feedItemResourceHelper.getExpandPreference();
            boolean expanded = expandHistory.get(position,expandByPreference);

            feedViewHolder.gridExpandArea.setVisibility(expanded ? View.VISIBLE : View.GONE);
            feedViewHolder.separatorView.setVisibility(expanded ? View.VISIBLE : View.GONE);
            feedViewHolder.listExpandArea.setVisibility(View.GONE);
        }
    }

    private void addViews(List<FeedEvent> events, LinearLayout linearLayout, final FeedRecyclerAdapter.OnFeedItemClickListener onItemClickListener, FeedItemResourceHelper feedItemResourceHelper) {
        linearLayout.removeAllViews();

        if (feedItemResourceHelper.imageOnlyListItems()) {
            addImageOnlyListItemViews(events, linearLayout, onItemClickListener, feedItemResourceHelper);
        } else {
            addImageTextListItemViews(events, linearLayout, onItemClickListener, feedItemResourceHelper);
        }
    }

    private void addImageTextListItemViews(List<FeedEvent> events, LinearLayout linearLayout, final FeedRecyclerAdapter.OnFeedItemClickListener onItemClickListener, final FeedItemResourceHelper feedItemResourceHelper) {
        Context context = linearLayout.getContext();
        LayoutInflater inflater = LayoutInflater.from(context);
        for (final FeedEvent feedEvent : events) {

            View itemView = inflater.inflate(R.layout.listitem_feed_innerlistitem, linearLayout, false);

            SimpleDraweeView itemImage = (SimpleDraweeView) itemView.findViewById(R.id.feed_innerlist_icon);
            final PictureInterface picture = feedItemResourceHelper.getPicture(events.get(0));
            itemImage.setVisibility(picture != null ? View.VISIBLE : View.GONE);
            if (picture != null) {
                itemImage.setImageURI(picture.getVariants().getMediumUrl());
            }

            TextView itemHeader = (TextView) itemView.findViewById(R.id.feed_innerlist_header);
            itemHeader.setText(feedItemResourceHelper.getItemTitle(feedEvent));
            TextView itemText = (TextView) itemView.findViewById(R.id.feed_innerlist_upper);
            itemText.setText(feedItemResourceHelper.getItemBody(feedEvent));
            TextView timeText = (TextView) itemView.findViewById(R.id.feed_innerlist_right);
            timeText.setText(feedItemResourceHelper.getItemCaption(feedEvent));

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onItemClickListener.onFeedInnerItemClick(feedItemResourceHelper.getFeedStoryType(), feedItemResourceHelper.getUrl(feedEvent));
                }
            });

            linearLayout.addView(itemView);

        }
    }

    private void addImageOnlyListItemViews(List<FeedEvent> events, LinearLayout linearLayout, final FeedRecyclerAdapter.OnFeedItemClickListener onItemClickListener, FeedItemResourceHelper feedItemResourceHelper) {
        Context context = linearLayout.getContext();
        LayoutInflater inflater = LayoutInflater.from(context);
        for (FeedEvent event : events) {

            final PictureInterface picture = feedItemResourceHelper.getPicture(events.get(0));

            SimpleDraweeView simpleDraweeView = (SimpleDraweeView) inflater.inflate(R.layout.listitem_feed_imageitem, linearLayout, false);
            simpleDraweeView.setImageURI(picture.getVariants().getLargeUrl());
            simpleDraweeView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    LayoutInflater inflater = LayoutInflater.from(v.getContext());
                    final View overlay = inflater.inflate(R.layout.overlay_feed_imageswipe, null);
                    setOverlayContent(overlay, picture, onItemClickListener);
                    new ImageViewer.Builder(v.getContext(), new String[]{picture.getVariants().getHugeUrl()}).setOverlayView(overlay).show();
                }
            });

            linearLayout.removeAllViews();
            linearLayout.addView(simpleDraweeView);
        }
    }

    private boolean listLayout(List<FeedEvent> events, FeedItemResourceHelper feedItemResourceHelper) {
        if (feedItemResourceHelper.listOnly()) {
            return true;
        }
        return events.size() == 1;
    }

    private void setOverlayContent(View overlay, final PictureInterface picture, final FeedRecyclerAdapter.OnFeedItemClickListener onItemClickListener) {
        TextView imageDescription = (TextView) overlay.findViewById(R.id.feedImageOverlayDescription);
        TextView imageMeta = (TextView) overlay.findViewById(R.id.feedImageOverlayMeta);
        TextView imageName = (TextView) overlay.findViewById(R.id.feedImageOverlayName);
        imageName.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onItemClickListener.onMemberClick(picture.getMember());
            }
        });
        imageDescription.setText(picture.getBody());
        imageMeta.setText(picture.getMember().getMetaInfo());
        imageName.setText(picture.getMember().getNickname());
    }

    class PictureGridAdapter extends BaseAdapter {
        private final FeedRecyclerAdapter.OnFeedItemClickListener onItemClickListener;

        private List<PictureInterface> pictures = new ArrayList<>();
        private ArrayList<String> gridLinks = new ArrayList<>();
        private ArrayList<String> displayLinks = new ArrayList<>();

        PictureGridAdapter(List<FeedEvent> events, FeedItemResourceHelper feedItemResourceHelper, FeedRecyclerAdapter.OnFeedItemClickListener onItemClickListener) {
            this.onItemClickListener = onItemClickListener;
            for (FeedEvent event : events) {
                PictureInterface picture = feedItemResourceHelper.getPicture(event);
                pictures.add(picture);
                gridLinks.add(picture.getVariants().getMediumUrl());
                displayLinks.add(picture.getVariants().getHugeUrl());
            }
        }

        @Override
        public int getCount() {
            return pictures.size();
        }

        @Override
        public PictureInterface getItem(int position) {
            return pictures.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(final int position, View convertView, final ViewGroup parent) {
            Uri pictureUri;

            try {
                pictureUri = Uri.parse(gridLinks.get(position));
            } catch (NullPointerException npe) {
                return new LinearLayout(parent.getContext());
            }
            if (pictureUri == null) {
                return new LinearLayout(parent.getContext());
            }

            LayoutInflater inflater = LayoutInflater.from(parent.getContext());

            SimpleDraweeView simpleDraweeView = (SimpleDraweeView) inflater.inflate(R.layout.listitem_feed_griditem, parent, false);
            simpleDraweeView.setImageURI(pictureUri);
            simpleDraweeView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    LayoutInflater inflater = LayoutInflater.from(v.getContext());
                    final View overlay = inflater.inflate(R.layout.overlay_feed_imageswipe, null);
                    setOverlayContent(overlay, getItem(position), onItemClickListener);

                    new ImageViewer.Builder(v.getContext(), displayLinks).setStartPosition(position).setOverlayView(overlay).setImageChangeListener(new ImageViewer.OnImageChangeListener() {
                        @Override
                        public void onImageChange(int position) {
                            setOverlayContent(overlay, getItem(position), onItemClickListener);
                        }
                    }).show();
                }
            });

            return simpleDraweeView;
        }

    }

}
