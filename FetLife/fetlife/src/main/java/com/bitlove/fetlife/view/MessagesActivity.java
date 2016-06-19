package com.bitlove.fetlife.view;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.NavigationView;
import android.view.View;
import android.widget.ListView;

import com.bitlove.fetlife.R;
import com.bitlove.fetlife.event.NewMessageEvent;
import com.bitlove.fetlife.event.ServiceCallFailedEvent;
import com.bitlove.fetlife.event.ServiceCallFinishedEvent;
import com.bitlove.fetlife.model.pojos.Member;
import com.bitlove.fetlife.model.pojos.Message;
import com.bitlove.fetlife.model.service.FetLifeApiIntentService;
import com.raizlabs.android.dbflow.runtime.FlowContentObserver;
import com.raizlabs.android.dbflow.structure.BaseModel;
import com.raizlabs.android.dbflow.structure.Model;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class MessagesActivity extends ResourceActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private static final String EXTRA_CONVERSATION_ID = "com.bitlove.fetlife.extra.conversation_id";
    private static final String EXTRA_CONVERSATION_TITLE = "com.bitlove.fetlife.extra.conversation_title";

    private FlowContentObserver messagesModelObserver;
    private MessagesRecyclerAdapter messagesAdapter;

    private String conversationId;

//Polling
//    private volatile boolean refreshRuns;
//    private Handler handler = new Handler();
//    private boolean isVisible;

    public static void startActivity(Context context, String conversationId, String title, boolean newTask) {
        context.startActivity(createIntent(context, conversationId, title, newTask));
    }

    public static Intent createIntent(Context context, String conversationId, String title, boolean newTask) {
        Intent intent = new Intent(context, MessagesActivity.class);
        if (newTask) {
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        intent.putExtra(EXTRA_CONVERSATION_ID, conversationId);
        intent.putExtra(EXTRA_CONVERSATION_TITLE, title);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        floatingActionButton.setVisibility(View.GONE);

        inputLayout.setVisibility(View.VISIBLE);
        inputIcon.setVisibility(View.VISIBLE);

        setConversation(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        setConversation(intent);
    }

    private void setConversation(Intent intent) {
        conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID);
        String conversationTitle = intent.getStringExtra(EXTRA_CONVERSATION_TITLE);
        setTitle(conversationTitle);
        messagesAdapter = new MessagesRecyclerAdapter(conversationId);
        recyclerLayoutManager.setReverseLayout(true);
        recyclerView.setAdapter(messagesAdapter);
    }

    @Override
    protected void onStart() {
        super.onStart();

        messagesModelObserver = new FlowContentObserver();

        if (isFinishing()) {
            return;
        }

        getFetLifeApplication().getEventBus().register(this);

        messagesModelObserver.addModelChangeListener(new FlowContentObserver.OnModelStateChangedListener() {
            @Override
            public void onModelStateChanged(Class<? extends Model> table, BaseModel.Action action) {
                messagesAdapter.refresh();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ListView messagesList = (ListView) findViewById(R.id.list_view);
                        messagesList.setSelection(messagesList.getCount() - 1);
                    }
                });

            }
        });
        messagesModelObserver.registerForContentChanges(this, Message.class);
        messagesAdapter.refresh();

        showProgress();
        FetLifeApiIntentService.startApiCall(this, FetLifeApiIntentService.ACTION_APICALL_MESSAGES, conversationId);

        ListView messagesList = (ListView) findViewById(R.id.list_view);
        messagesList.setSelection(messagesList.getCount() - 1);

//Polling
//        isVisible = true;
//        if (!refreshRuns) {
//            setUpNextCall();
//        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        messagesModelObserver.unregisterForContentChanges(this);

        getFetLifeApplication().getEventBus().unregister(this);

//Polling
//        isVisible = false;
    }

//Polling
//    private void setUpNextCall() {
//        if (!isVisible) {
//            refreshRuns = false;
//            return;
//        }
//        refreshRuns = true;
//        FetLifeApiIntentService.startApiCall(MessagesActivity.this, FetLifeApiIntentService.ACTION_APICALL_MESSAGES, conversationId);
//        handler.postDelayed(new Runnable() {
//            @Override
//            public void run() {
//                setUpNextCall();
//            }
//        }, 3000);
//    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessagesCallFinished(ServiceCallFinishedEvent serviceCallFinishedEvent) {
        if (serviceCallFinishedEvent.getServiceCallAction() == FetLifeApiIntentService.ACTION_APICALL_MESSAGES) {
            dismissProgress();
            setMessagesRead();
        }
    }

    private void setMessagesRead() {
        final List<String> params = new ArrayList<>();
        params.add(conversationId);

        for (int i = 0; i < messagesAdapter.getItemCount(); i++) {
            Message message = messagesAdapter.getItem(i);
            if (!message.getPending() && message.getIsNew()) {
                params.add(message.getId());
            }
        }

        if (params.size() == 1) {
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                FetLifeApiIntentService.startApiCall(MessagesActivity.this.getApplicationContext(), FetLifeApiIntentService.ACTION_APICALL_SET_MESSAGES_READ, params.toArray(new String[params.size()]));
            }
        }).run();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessagesCallFailed(ServiceCallFailedEvent serviceCallFailedEvent) {
        if (serviceCallFailedEvent.getServiceCallAction() == FetLifeApiIntentService.ACTION_APICALL_MESSAGES) {
            if (serviceCallFailedEvent.isServerConnectionFailed()) {
                showToast(getResources().getString(R.string.error_connection_failed));
            } else {
                showToast(getResources().getString(R.string.error_apicall_failed));
            }
            dismissProgress();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onNewMessageArrived(NewMessageEvent newMessageEvent) {
        if (!conversationId.equals(newMessageEvent.getConversationId())) {
            //TODO: display (snackbar?) notification
        } else {
            //wait for the already started refresh
        }
    }

    public void onSend(View v) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String text = textInput.getText().toString();
                if (text == null || text.trim().length() == 0) {
                    return;
                }
                Message message = new Message();
                message.setPending(true);
                message.setDate(System.currentTimeMillis());
                message.setClientId(UUID.randomUUID().toString());
                message.setConversationId(conversationId);
                message.setBody(text.trim());
                Member me = getFetLifeApplication().getMe();
                message.setSenderId(me.getId());
                message.setSenderNickname(me.getNickname());
                message.save();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        textInput.setText("");
                    }
                });
                FetLifeApiIntentService.startApiCall(MessagesActivity.this, FetLifeApiIntentService.ACTION_APICALL_NEW_MESSAGE);
            }
        }).start();
    }

}
