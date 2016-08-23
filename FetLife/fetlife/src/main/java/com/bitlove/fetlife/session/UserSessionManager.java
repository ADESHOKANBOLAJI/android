package com.bitlove.fetlife.session;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.bitlove.fetlife.FetLifeApplication;
import com.bitlove.fetlife.model.db.FetLifeDatabase;
import com.bitlove.fetlife.model.pojos.User;
import com.bitlove.fetlife.util.PreferenceKeys;
import com.bitlove.fetlife.util.SecurityUtil;
import com.onesignal.OneSignal;
import com.raizlabs.android.dbflow.config.FlowConfig;
import com.raizlabs.android.dbflow.config.FlowManager;
import com.raizlabs.android.dbflow.sql.language.Select;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class UserSessionManager {

    private static final String CONSTANT_ONESIGNAL_TAG_VERSION = "version";
    private static final String CONSTANT_ONESIGNAL_TAG_NICKNAME = "nickname";
    private static final String CONSTANT_ONESIGNAL_TAG_MEMBER_TOKEN = "member_token";

    private final FetLifeApplication fetLifeApplication;

    private User currentUser;

    public UserSessionManager(FetLifeApplication fetLifeApplication) {
        this.fetLifeApplication = fetLifeApplication;
    }

    public void init() {
        if (!getPasswordAlwaysPreference()) {
            String userKey = loadLastLoggedUserKey();
            if (userKey == null) {
                return;
            }
            loadUserDb(userKey);
            initDb();
            currentUser = readUserRecord();
        }
    }

    public void onAppInBackground() {
        if (getPasswordAlwaysPreference()) {
            onUserLogOut();
        }
    }

    public void onUserLogIn(User loggedInUser) {
        if (!isSameUser(loggedInUser, currentUser)) {
            logOutUser(currentUser);
            logInUser(loggedInUser);
            currentUser = loggedInUser;
        } else {
            updateUserRecord(loggedInUser);
        }
    }

    public void onUserLogOut() {
        logOutUser(currentUser);
        currentUser = null;
    }

    public void onUserReset() {
        resetUser(currentUser);
        currentUser = null;
    }

    private void logInUser(User user) {
        saveLastLoggedUserKey(getUserKey(user));
        loadUserDb(getUserKey(user));
        initDb();
        updateUserRecord(user);
        registerToPushMessages(user);
    }

    private void logOutUser(User user) {
        closeDb();
        String userKey = user == null ? loadLastLoggedUserKey() : getUserKey(user);
        if (userKey != null) {
            saveUserDb(userKey);
        } else {
            clearDb();
        }
    }

    private void resetUser(User user) {
        String userKey = getUserKey(user);
        if (userKey == null) {
            return;
        }
        closeDb();
        deleteUserDb(userKey);
        removeLoggedUserKey(userKey);
        clearDb();
        unregisterFromPushMessages(user);
    }

    private static String getUserKey(User user) {
        return user != null && user.getNickname() != null ? SecurityUtil.hash_sha256(user.getNickname()) : null;
    }

    private void registerToPushMessages(User user) {
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put(CONSTANT_ONESIGNAL_TAG_VERSION,1);
            jsonObject.put(CONSTANT_ONESIGNAL_TAG_NICKNAME, user.getNickname());
            jsonObject.put(CONSTANT_ONESIGNAL_TAG_MEMBER_TOKEN, user.getNotificationToken());
            OneSignal.sendTags(jsonObject);
            OneSignal.setSubscription(true);
        } catch (JSONException e) {
            //TODO: think about possible error handling
        }
    }

    private void unregisterFromPushMessages(User user) {
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put(CONSTANT_ONESIGNAL_TAG_VERSION, 1);
            jsonObject.put(CONSTANT_ONESIGNAL_TAG_NICKNAME, user.getNickname());
            jsonObject.put(CONSTANT_ONESIGNAL_TAG_MEMBER_TOKEN, "");
            OneSignal.sendTags(jsonObject);

            String[] tags = new String[]{
                    CONSTANT_ONESIGNAL_TAG_VERSION,
                    CONSTANT_ONESIGNAL_TAG_NICKNAME,
                    CONSTANT_ONESIGNAL_TAG_MEMBER_TOKEN
            };
            OneSignal.deleteTags(Arrays.asList(tags));
        } catch (JSONException e) {
            //TODO: think about possible error handling
        }
    }

    public void onPasswordAlwaysPreferenceSet(boolean checked) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(fetLifeApplication);
        sharedPreferences.edit().putBoolean(PreferenceKeys.PREF_KEY_PASSWORD_ALWAYS, checked).apply();
    }
    private boolean getPasswordAlwaysPreference() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(fetLifeApplication);
        return sharedPreferences.getBoolean(PreferenceKeys.PREF_KEY_PASSWORD_ALWAYS, true);
    }

    private String loadLastLoggedUserKey() {
        List<String> userHistory = loadUserHistoryFromPreference();
        return userHistory.isEmpty() ? null : userHistory.get(userHistory.size()-1);
    }

    private void saveLastLoggedUserKey(String userKey) {
        List<String> loggedInUsers = loadUserHistoryFromPreference();
        int currentPosition = Collections.binarySearch(loggedInUsers, userKey);
        if (currentPosition >= 0) {
            loggedInUsers.remove(currentPosition);
        }
        loggedInUsers.add(userKey);
        saveUserHistoryToPreference(loggedInUsers);
    }

    private void removeLoggedUserKey(String userKey) {
        List<String> loggedInUsers = loadUserHistoryFromPreference();
        int currentPosition = Collections.binarySearch(loggedInUsers, userKey);
        if (currentPosition >= 0) {
            loggedInUsers.remove(currentPosition);
        }
        saveUserHistoryToPreference(loggedInUsers);
    }

    private List<String> loadUserHistoryFromPreference() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(fetLifeApplication);
        String userHistory = preferences.getString(PreferenceKeys.PREF_KEY_USER_HISTORY, "");
        return new ArrayList<>(Arrays.asList(userHistory.split("%")));
    }

    private void saveUserHistoryToPreference(List<String> userHistory) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(fetLifeApplication);
        StringBuilder stringBuilder = new StringBuilder();
        for (String loggedInUser : userHistory) {
            if (stringBuilder.length() != 0) {
                stringBuilder.append("%");
            }
            stringBuilder.append(loggedInUser);
        }
        preferences.edit().putString(PreferenceKeys.PREF_KEY_USER_HISTORY, stringBuilder.toString()).apply();
    }

    private void updateUserRecord(User user) {
        if (user != null) {
            user.setId("currentUser");
            user.save();
        }
    }

    private User readUserRecord() {
        return new Select().from(User.class).querySingle();
    }

    private void saveUserDb(String userKey) {
        File databaseFile = fetLifeApplication.getDatabasePath(getDefaultDatabaseName());
        if (databaseFile == null) {
            return;
        }
        File userDatabaseFile = new File(databaseFile.getParentFile(), getUserDatabaseName(userKey));
        databaseFile.renameTo(userDatabaseFile);
    }

    private void loadUserDb(String userKey) {
        File databaseFile = fetLifeApplication.getDatabasePath(getDefaultDatabaseName());
        if (databaseFile == null) {
            return;
        }
        File userDatabaseFile = new File(databaseFile.getParentFile(), getUserDatabaseName(userKey));
        userDatabaseFile.renameTo(databaseFile);
    }

    private void deleteUserDb(String userKey) {
        File databaseFile = fetLifeApplication.getDatabasePath(getDefaultDatabaseName());
        if (databaseFile == null) {
            return;
        }
        File userDatabaseFile = new File(databaseFile.getParentFile(), getUserDatabaseName(userKey));
        userDatabaseFile.delete();
    }

    private void initDb() {
        FlowManager.init(new FlowConfig.Builder(fetLifeApplication).build());
    }

    private void closeDb() {
        FlowManager.destroy();
    }

    private void clearDb() {
        fetLifeApplication.deleteDatabase(getDefaultDatabaseName());
        fetLifeApplication.openOrCreateDatabase(getDefaultDatabaseName(), Context.MODE_PRIVATE, null);
    }

    private static String getDefaultDatabaseName() {
        //DBFlow library uses .db suffix, but they mentioned they might going to change this in the future
        return FetLifeDatabase.NAME + ".db";
    }

    private static String getUserDatabaseName(String userKey) {
        return FetLifeDatabase.NAME + "_" + userKey + ".db";
    }

    private static boolean isSameUser(User user1, User user2) {
        if (user1 == null || user2 == null || user1.getNickname() == null) {
            return false;
        }
        return user1.getNickname().equals(user2.getNickname());
    }

    public User getCurrentUser() {
        return currentUser;
    }
}
