package com.bitlove.fetchat.model.service;

import android.app.IntentService;
import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

import com.bitlove.fetchat.BuildConfig;
import com.bitlove.fetchat.FetLifeApplication;
import com.bitlove.fetchat.event.LoginFailedEvent;
import com.bitlove.fetchat.event.LoginFinishedEvent;
import com.bitlove.fetchat.event.LoginStartedEvent;
import com.bitlove.fetchat.event.ServiceCallFailedEvent;
import com.bitlove.fetchat.event.ServiceCallFinishedEvent;
import com.bitlove.fetchat.event.ServiceCallStartedEvent;
import com.bitlove.fetchat.model.api.FetLifeApi;
import com.bitlove.fetchat.model.api.FetLifeService;
import com.bitlove.fetchat.model.db.FetChatDatabase;
import com.bitlove.fetchat.model.pojos.Conversation;
import com.bitlove.fetchat.model.pojos.Member;
import com.bitlove.fetchat.model.pojos.Message;
import com.bitlove.fetchat.model.pojos.Message$Table;
import com.bitlove.fetchat.model.pojos.Token;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesignal.OneSignal;
import com.raizlabs.android.dbflow.config.FlowManager;
import com.raizlabs.android.dbflow.runtime.TransactionManager;
import com.raizlabs.android.dbflow.sql.builder.Condition;
import com.raizlabs.android.dbflow.sql.language.Delete;
import com.raizlabs.android.dbflow.sql.language.Select;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import retrofit.Call;
import retrofit.Response;

public class FetLifeApiIntentService extends IntentService {

    public static final String ACTION_APICALL_CONVERSATIONS = "com.bitlove.fetchat.action.apicall.cpnversations";
    public static final String ACTION_APICALL_MESSAGES = "com.bitlove.fetchat.action.apicall.messages";
    public static final String ACTION_APICALL_NEW_MESSAGE = "com.bitlove.fetchat.action.apicall.new_messages";

    public static final String ACTION_APICALL_LOGON_USER = "com.bitlove.fetchat.action.apicall.logon_user";

    private static final String CONSTANT_PREF_KEY_REFRESH_TOKEN = "com.bitlove.fetchat.key.pref.token.refresh";

    private static final String EXTRA_PARAMS = "com.bitlove.fetchat.extra.params";

    private static final String PARAM_SORT_ORDER_UPDATED_DESC = "-updated_at";

    private static String actionInProgress = null;

    public FetLifeApiIntentService() {
        super("FetLifeApiIntentService");
    }

    public static synchronized void startApiCall(Context context, String action, String... params) {
        Intent intent = new Intent(context, FetLifeApiIntentService.class);
        intent.setAction(action);
        intent.putExtra(EXTRA_PARAMS, params);
        context.startService(intent);
    }

    //TODO: think about being more specific and store also parameters for exact identification
    private static synchronized void setActionInProgress(String action) {
        actionInProgress = action;
    }

    public static synchronized String getActionInProgress() {
        return actionInProgress;
    }

    public static synchronized boolean isActionInProgress(String action) {
        if (actionInProgress == null) {
            return false;
        }
        return actionInProgress.equals(action);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent == null) {
            return;
        }

        final String action = intent.getAction();

        //TODO: checkForNetworkState
        try {

            setActionInProgress(action);

            sendLoadStartedNotification(action);

            if (action != ACTION_APICALL_LOGON_USER && getFetLifeApplication().getAccessToken() == null) {
                if (refreshToken()) {
                    onHandleIntent(intent);
                } else {
                    sendLoadFailedNotification(action);
                }
            }

            boolean result = false;
            switch (action) {
                case ACTION_APICALL_LOGON_USER:
                    result = logonUser(intent.getStringArrayExtra(EXTRA_PARAMS));
                    break;
                case ACTION_APICALL_CONVERSATIONS:
                    result = retriveConversations();
                    break;
                case ACTION_APICALL_MESSAGES:
                    result = retrieveMessages(intent.getStringArrayExtra(EXTRA_PARAMS));
                    break;
                case ACTION_APICALL_NEW_MESSAGE:
                    result = sendPendingMessages();
                    break;
            }

            if (result) {
                sendLoadFinishedNotification(action);
            } else if (action != ACTION_APICALL_LOGON_USER && getFetLifeApplication().getFetLifeService().getLastResponseCode() == 403) {
                if (refreshToken()) {
                    onHandleIntent(intent);
                } else {
                    sendLoadFailedNotification(action);
                }
                //TODO: error handling for endless loop
            } else {
                sendLoadFailedNotification(action);
            }
        } catch (IOException ioe) {
            sendConnectionFailedNotification(action);
        } finally {
            setActionInProgress(null);
        }
    }

    private boolean refreshToken() throws IOException {

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getFetLifeApplication());
        String refreshToken = preferences.getString(CONSTANT_PREF_KEY_REFRESH_TOKEN, null);

        if (refreshToken == null) {
            return false;
        }

        Call<Token> tokenRefreshCall = getFetLifeApplication().getFetLifeService().getFetLifeApi().refreshToken(
                BuildConfig.CLIENT_ID,
                BuildConfig.CLIENT_SECRET,
                BuildConfig.REDIRECT_URL,
                FetLifeService.GRANT_TYPE_TOKEN_REFRESH,
                refreshToken
        );

        Response<Token> tokenResponse = tokenRefreshCall.execute();

        if (tokenResponse.isSuccess()) {
            getFetLifeApplication().setAccessToken(tokenResponse.body().getAccessToken());

            SharedPreferences.Editor editor = preferences.edit();
            editor.putString(CONSTANT_PREF_KEY_REFRESH_TOKEN, tokenResponse.body().getRefreshToken());
            editor.apply();

            return true;
        } else {
            return false;
        }

    }

    private void sendLoadStartedNotification(String action) {
        switch (action) {
            case ACTION_APICALL_LOGON_USER:
                getFetLifeApplication().getEventBus().post(new LoginStartedEvent());
                break;
            default:
                getFetLifeApplication().getEventBus().post(new ServiceCallStartedEvent(action));
                break;
        }
    }

    private void sendLoadFinishedNotification(String action) {
        switch (action) {
            case ACTION_APICALL_LOGON_USER:
                getFetLifeApplication().getEventBus().post(new LoginFinishedEvent());
                break;
            default:
                getFetLifeApplication().getEventBus().post(new ServiceCallFinishedEvent(action));
                break;
        }
    }

    private void sendLoadFailedNotification(String action) {
        switch (action) {
            case ACTION_APICALL_LOGON_USER:
                getFetLifeApplication().getEventBus().post(new LoginFailedEvent());
                break;
            default:
                getFetLifeApplication().getEventBus().post(new ServiceCallFailedEvent(action));
                break;
        }
    }

    private void sendConnectionFailedNotification(String action) {

    }

    private boolean logonUser(String... params) throws IOException {
        Call<Token> tokenCall = getFetLifeApplication().getFetLifeService().getFetLifeApi().login(
                BuildConfig.CLIENT_ID,
                BuildConfig.CLIENT_SECRET,
                BuildConfig.REDIRECT_URL,
                FetLifeService.GRANT_TYPE_PASSWORD,
                params[0], params[1]);

        Response<Token> tokenResponse = tokenCall.execute();
        if (tokenResponse.isSuccess()) {
            getFetLifeApplication().setAccessToken(tokenResponse.body().getAccessToken());

            if (retrieveMyself()) {

                Member me = getFetLifeApplication().getMe();

                String meAsJson = new ObjectMapper().writeValueAsString(me);
                Bundle accountData = new Bundle();
                accountData.putString(FetLifeApplication.CONSTANT_PREF_KEY_ME_JSON, meAsJson);

                //TODO: investigate use of account manager
                //TODO: use keystore for encryption as an alternative
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getFetLifeApplication());
                SharedPreferences.Editor editor = preferences.edit();
                editor.putString(FetLifeApplication.CONSTANT_PREF_KEY_ME_JSON, meAsJson).putString(CONSTANT_PREF_KEY_REFRESH_TOKEN, tokenResponse.body().getRefreshToken());
                editor.apply();

                try {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put(FetLifeApplication.CONSTANT_ONESIGNAL_TAG_VERSION,1);
                    jsonObject.put(FetLifeApplication.CONSTANT_ONESIGNAL_TAG_NICKNAME,me.getNickname());
                    jsonObject.put(FetLifeApplication.CONSTANT_ONESIGNAL_TAG_MEMBER_TOKEN,me.getNotificationToken());
                    OneSignal.sendTags(jsonObject);
                } catch (JSONException e) {
                    //TODO: error handling
                }

                OneSignal.setSubscription(true);

                return true;

            } else {
                return false;
            }

        } else {
            return false;
        }
    }

    private boolean sendPendingMessages() throws IOException {
        List<Message> pendingMessages = new Select().from(Message.class).where(Condition.column(Message$Table.PENDING).eq(true)).queryList();
        for (Message message : pendingMessages) {
            if (!sendPendingMessage(message)) {
                return false;
            }
        }
        return true;
    }

    private boolean sendPendingMessage(Message pendingMessage) throws IOException {
        Call<Message> postMessagesCall = getFetLifeApi().postMessage(FetLifeService.AUTH_HEADER_PREFIX + getFetLifeApplication().getAccessToken(), pendingMessage.getConversationId(), pendingMessage.getBody());
        Response<Message> postMessageResponse = postMessagesCall.execute();
        if (postMessageResponse.isSuccess()) {
            final Message message = postMessageResponse.body();
            message.setClientId(pendingMessage.getClientId());
            message.setPending(false);
            message.setConversationId(pendingMessage.getConversationId());
            message.update();
            return true;
        } else {
            return false;
        }
    }

    private boolean retrieveMessages(String... params) throws IOException {
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
            return true;
        } else {
            return false;
        }
    }

    private boolean retriveConversations() throws IOException {
        Call<List<Conversation>> getConversationsCall = getFetLifeApi().getConversations(FetLifeService.AUTH_HEADER_PREFIX + getFetLifeApplication().getAccessToken(), PARAM_SORT_ORDER_UPDATED_DESC);
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
            return true;
        } else {
            return false;
        }
    }

    private boolean retrieveMyself() throws IOException {
        Call<Member> getMeCall = getFetLifeApi().getMe(FetLifeService.AUTH_HEADER_PREFIX + getFetLifeApplication().getAccessToken());
        Response<Member> getMeResponse = getMeCall.execute();
        if (getMeResponse.isSuccess()) {
            getFetLifeApplication().setMe(getMeResponse.body());
            return true;
        } else {
            return false;
        }
    }

    private FetLifeApplication getFetLifeApplication() {
        return (FetLifeApplication) getApplication();
    }

    private FetLifeApi getFetLifeApi() {
        return getFetLifeApplication().getFetLifeService().getFetLifeApi();
    }
}
