package com.bitlove.fetlife.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuffColorFilter;
import android.os.Handler;
import android.os.Looper;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bitlove.fetlife.R;
import com.bitlove.fetlife.model.pojos.FriendRequest;
import com.bitlove.fetlife.model.pojos.FriendRequest_Table;
import com.bitlove.fetlife.model.resource.ImageLoader;
import com.bitlove.fetlife.model.service.FetLifeApiIntentService;
import com.raizlabs.android.dbflow.sql.language.Delete;
import com.raizlabs.android.dbflow.sql.language.Select;

import java.util.ArrayList;
import java.util.List;

public class FriendRequestsRecyclerAdapter extends RecyclerView.Adapter<FriendRequestViewHolder> {

    private static final int FRIENDREQUEST_UNDO_DURATION = 5000;

    private final ImageLoader imageLoader;

    public interface OnFriendRequestClickListener {
        public void onItemClick(FriendRequest FriendRequest);
        public void onAvatarClick(FriendRequest FriendRequest);
    }

    private List<FriendRequest> itemList;
    OnFriendRequestClickListener onFriendRequestClickListener;

    public FriendRequestsRecyclerAdapter(ImageLoader imageLoader, boolean clearItems) {
        this.imageLoader = imageLoader;
        if (clearItems) {
            clearItems();
        } else {
            loadItems();
        }
    }

    public void setOnItemClickListener(OnFriendRequestClickListener onFriendRequestClickListener) {
        this.onFriendRequestClickListener = onFriendRequestClickListener;
    }

    private void loadItems() {
        //TODO: think of moving to separate thread with specific DB executor
        try {
            itemList = new Select().from(FriendRequest.class).where(FriendRequest_Table.pending.is(false)).queryList();
        } catch (Throwable t) {
            itemList = new ArrayList<>();
        }
    }

    private void clearItems() {
        itemList = new ArrayList<>();
        //TODO: think of moving to separate thread with specific DB executor
        try {
            new Delete().from(FriendRequest.class).where(FriendRequest_Table.pending.is(false)).query();
        } catch (Throwable t) {
        }
    }

    @Override
    public void onAttachedToRecyclerView(final RecyclerView recyclerView) {
        ItemTouchHelper.SimpleCallback simpleItemTouchCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int swipeDir) {
                FriendRequestsRecyclerAdapter.this.onItemRemove(viewHolder, recyclerView, swipeDir == ItemTouchHelper.RIGHT);
            }

            @Override
            public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
                int swipeFlags = ItemTouchHelper.START | ItemTouchHelper.END;
                return makeMovementFlags(0, swipeFlags);
            }

            @Override
            public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
                if (viewHolder != null) {
                    getDefaultUIUtil().onSelected(((FriendRequestViewHolder) viewHolder).swipableLayout);
                }
            }

            @Override
            public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
                FriendRequestViewHolder friendRequestViewHolder = ((FriendRequestViewHolder) viewHolder);
                friendRequestViewHolder.backgroundLayout.setVisibility(View.GONE);
                getDefaultUIUtil().clearView(((FriendRequestViewHolder) viewHolder).swipableLayout);
            }

            @Override
            public void onChildDraw(Canvas c, RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                getDefaultUIUtil().onDraw(c, recyclerView, ((FriendRequestViewHolder) viewHolder).swipableLayout, dX, dY, actionState, isCurrentlyActive);
            }

            @Override
            public void onChildDrawOver(Canvas c, RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                FriendRequestViewHolder friendRequestViewHolder = ((FriendRequestViewHolder) viewHolder);
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && isCurrentlyActive) {
                    friendRequestViewHolder.backgroundLayout.setVisibility(View.VISIBLE);
                    friendRequestViewHolder.backgroundLayout.setBackgroundResource(dX > 0 ? R.drawable.listitem_background_accept_rounded : R.drawable.listitem_background_reject_rounded);
                }
                getDefaultUIUtil().onDrawOver(c, recyclerView, friendRequestViewHolder.swipableLayout, dX, dY, actionState, isCurrentlyActive);
            }
        };

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(simpleItemTouchCallback);
        itemTouchHelper.attachToRecyclerView(recyclerView);
    }

    void onItemRemove(final RecyclerView.ViewHolder viewHolder, final RecyclerView recyclerView, boolean accepted) {
        final int adapterPosition = viewHolder.getAdapterPosition();
        final FriendRequest friendRequest = itemList.get(adapterPosition);
        Snackbar snackbar = Snackbar
                .make(recyclerView, accepted ? R.string.text_friendrequests_accepted :  R.string.text_friendrequests_rejected, Snackbar.LENGTH_LONG)
                .setActionTextColor(recyclerView.getContext().getResources().getColor(R.color.text_color_link))
                .setAction(R.string.action_undo, new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        synchronized (friendRequest) {
                            if (!friendRequest.isPending()) {
                                friendRequest.setPendingState(null);
                                itemList.add(adapterPosition, friendRequest);
                                notifyItemInserted(adapterPosition);
                                recyclerView.scrollToPosition(adapterPosition);
                            }
                        }
                    }
                });

        friendRequest.setPendingState(accepted ? FriendRequest.PendingState.ACCEPTED : FriendRequest.PendingState.REJECTED);
        itemList.remove(adapterPosition);
        notifyItemRemoved(adapterPosition);
        snackbar.show();

        startDelayedFriendRequestDecision(friendRequest, friendRequest.getPendingState(), FRIENDREQUEST_UNDO_DURATION, recyclerView.getContext());
    }

    private void startDelayedFriendRequestDecision(final FriendRequest friendRequest, final FriendRequest.PendingState pendingState, int friendrequestUndoDuration, final Context context) {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                synchronized (friendRequest) {
                    if (friendRequest.getPendingState() == pendingState) {
                        friendRequest.setPending(true);
                        friendRequest.save();
                        FetLifeApiIntentService.startApiCall(context, FetLifeApiIntentService.ACTION_APICALL_SEND_FRIENDREQUESTS);
                    }
                }
            }
        }, friendrequestUndoDuration);
    }

    @Override
    public FriendRequestViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.listitem_friendrequest, parent, false);
        return new FriendRequestViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(FriendRequestViewHolder FriendRequestViewHolder, int position) {

        final FriendRequest friendRequest = itemList.get(position);

        FriendRequestViewHolder.headerText.setText(friendRequest.getNickname());
        FriendRequestViewHolder.upperText.setText(friendRequest.getMetaInfo());

//        FriendRequestViewHolder.dateText.setText(SimpleDateFormat.getDateTimeInstance().format(new Date(FriendRequest.getDate())));

        FriendRequestViewHolder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (onFriendRequestClickListener != null) {
                    onFriendRequestClickListener.onItemClick(friendRequest);
                }
            }
        });

        FriendRequestViewHolder.avatarImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (onFriendRequestClickListener != null) {
                    onFriendRequestClickListener.onAvatarClick(friendRequest);
                }
            }
        });

        FriendRequestViewHolder.avatarImage.setImageResource(R.drawable.dummy_avatar);
        String avatarUrl = friendRequest.getAvatarLink();
        imageLoader.loadImage(FriendRequestViewHolder.itemView.getContext(), avatarUrl, FriendRequestViewHolder.avatarImage, R.drawable.dummy_avatar);
    }

    public void refresh() {
        loadItems();
        //TODO: think of possibility of update only specific items instead of the whole list
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                notifyDataSetChanged();
            }
        });
    }

    @Override
    public int getItemCount() {
        return itemList.size();
    }

    public FriendRequest getItem(int position) {
        return itemList.get(position);
    }
}

class FriendRequestViewHolder extends RecyclerView.ViewHolder {

    ImageView avatarImage;
    TextView headerText, upperText, dateText, lowerText;
    View swipableLayout, backgroundLayout;

    public FriendRequestViewHolder(View itemView) {
        super(itemView);

        swipableLayout = itemView.findViewById(R.id.swipeable_layout);
        backgroundLayout = itemView.findViewById(R.id.background_layout);
        headerText = (TextView) itemView.findViewById(R.id.friendrequest_header);
        upperText = (TextView) itemView.findViewById(R.id.friendrequest_upper);
        dateText = (TextView) itemView.findViewById(R.id.friendrequest_right);
        lowerText = (TextView) itemView.findViewById(R.id.friendrequest_lower);
        avatarImage = (ImageView) itemView.findViewById(R.id.friendrequest_icon);
    }
}