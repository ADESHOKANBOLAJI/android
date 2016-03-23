package com.bitlove.fetchat.model.service;

import android.app.IntentService;
import android.content.Intent;
import android.content.Context;
import android.os.Parcelable;
import android.util.Log;

import com.bitlove.fetchat.FetLifeApplication;
import com.bitlove.fetchat.model.api.FetLifeApi;
import com.bitlove.fetchat.model.api.FetLifeService;
import com.bitlove.fetchat.model.db.FetChatDatabase;
import com.bitlove.fetchat.model.pojos.Conversation;
import com.bitlove.fetchat.model.pojos.Message;
import com.bitlove.fetchat.model.pojos.Message$Table;
import com.raizlabs.android.dbflow.config.FlowManager;
import com.raizlabs.android.dbflow.runtime.TransactionManager;
import com.raizlabs.android.dbflow.sql.builder.Condition;
import com.raizlabs.android.dbflow.sql.language.Delete;
import com.raizlabs.android.dbflow.sql.language.Select;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.UUID;

import retrofit.Call;
import retrofit.Response;

public class FetLifeApiIntentService extends IntentService {

    public static final String ACTION_APICALL_CONVERSATIONS = "com.bitlove.fetchat.action.apicall.cpnversations";
    public static final String ACTION_APICALL_MESSAGES = "com.bitlove.fetchat.action.apicall.messages";
    public static final String ACTION_APICALL_NEW_MESSAGE = "com.bitlove.fetchat.action.apicall.new_messages";
    private static final String EXTRA_METHOD = "com.bitlove.fetchat.extra.METHOD";
    private static final String EXTRA_PARAMS = "com.bitlove.fetchat.extra.PARAMS";

    public FetLifeApiIntentService() {
        super("FetLifeApiIntentService");
    }

    public static void startApiCall(Context context, String action, String... params) {
        Intent intent = new Intent(context, FetLifeApiIntentService.class);
        intent.setAction(action);
        intent.putExtra(EXTRA_PARAMS, params);
        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            switch (action) {
                case ACTION_APICALL_CONVERSATIONS:
                    retriveConversations();
                    break;
                case ACTION_APICALL_MESSAGES:
                    retrieveMessages(intent.getStringArrayExtra(EXTRA_PARAMS));
                    break;
                case ACTION_APICALL_NEW_MESSAGE:
                    sendPendingMessages();
                    break;
            }
        }
    }

    private void sendPendingMessages() {
        List<Message> pendingMessages = new Select().from(Message.class).where(Condition.column(Message$Table.PENDING).eq(true)).queryList();
        for (Message message : pendingMessages) {
            sendPendingMessage(message);
        }
    }

    private void sendPendingMessage(Message pendingMessage) {
        try {
            Call<Message> postMessagesCall = getFetLifeApi().postMessage(FetLifeService.AUTH_HEADER_PREFIX + getFetLifeApplication().getAccessToken(), pendingMessage.getConversationId(), pendingMessage.getBody());
            Response<Message> postMessageResponse = postMessagesCall.execute();
            if (postMessageResponse.isSuccess()) {
                final Message message = postMessageResponse.body();
                message.setClientId(pendingMessage.getClientId());
                message.setPending(false);
                message.setConversationId(pendingMessage.getConversationId());
                message.update();
            } else {
                //TODO: error handling
            }
        } catch (IOException e) {
            //TODO: error handling
        }
    }

    private void retrieveMessages(String... params) {
        try {
            final String conversationId = params[0];
            Call<List<Message>> getMessagesCall = getFetLifeApi().getMessages(FetLifeService.AUTH_HEADER_PREFIX + getFetLifeApplication().getAccessToken(), conversationId);
            Response<List<Message>> messagesResponse = getMessagesCall.execute();
            if (messagesResponse.isSuccess()) {
                final List<Message> messages = messagesResponse.body();
                TransactionManager.transact(FlowManager.getDatabase(FetChatDatabase.NAME).getWritableDatabase(), new Runnable() {
                    @Override
                    public void run() {
                        for (Message message : messages) {
                            Message storedMessage = new Select().from(Message.class).where(Condition.column(Message$Table.ID).eq(message.getId())).querySingle();
                            if (storedMessage != null) {
                                message.setClientId(storedMessage.getClientId());
                            } else {
                                message.setClientId(UUID.randomUUID().toString());
                            }
                            message.setConversationId(conversationId);
                            message.setPending(false);
                            message.save();
                        }
                    }
                });
            } else {
                //TODO: error handling
            }
        } catch (IOException e) {
            //TODO: error handling
        }
    }

    private void retriveConversations() {
        try {
            Call<List<Conversation>> getConversationsCall = getFetLifeApi().getConversations(FetLifeService.AUTH_HEADER_PREFIX + getFetLifeApplication().getAccessToken());
            Response<List<Conversation>> conversationsResponse = getConversationsCall.execute();
            if (conversationsResponse.isSuccess()) {
                final List<Conversation> conversations = conversationsResponse.body();
                TransactionManager.transact(FlowManager.getDatabase(FetChatDatabase.NAME).getWritableDatabase(), new Runnable() {
                    @Override
                    public void run() {
                        new Delete().from(Conversation.class).queryClose();
                        for (Conversation conversation : conversations) {
                            conversation.save();
                        }
                    }
                });
            } else {
                //TODO: error handling
            }
        } catch (IOException e) {
            //TODO: error handling
        }
    }

    protected FetLifeApplication getFetLifeApplication() {
        return (FetLifeApplication) getApplication();
    }

    protected FetLifeApi getFetLifeApi() {
        return getFetLifeApplication().getFetLifeService().getFetLifeApi();
    }
}
