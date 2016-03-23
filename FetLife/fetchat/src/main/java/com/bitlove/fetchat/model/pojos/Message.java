package com.bitlove.fetchat.model.pojos;

import com.bitlove.fetchat.model.db.FetChatDatabase;
import com.bitlove.fetchat.util.DateUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.raizlabs.android.dbflow.annotation.Column;
import com.raizlabs.android.dbflow.annotation.PrimaryKey;
import com.raizlabs.android.dbflow.annotation.Table;
import com.raizlabs.android.dbflow.structure.BaseModel;

import java.text.SimpleDateFormat;
import java.util.Date;

@Table(databaseName = FetChatDatabase.NAME)
public class Message extends BaseModel {

    @Column
    @PrimaryKey(autoincrement = false)
    @JsonIgnore
    private String clientId;

    @Column
    @JsonProperty("id")
    private String id;

    @Column
    @JsonProperty("body")
    private String body;

    @Column
    @JsonProperty("created_at")
    private String createdAt;

    @Column
    @JsonIgnore
    private long date;

    @Column
    @JsonIgnore
    private String conversationId;

    @Column
    @JsonIgnore
    private boolean pending;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    @JsonIgnore
    public String getConversationId() {
        return conversationId;
    }

    @JsonIgnore
    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    @JsonIgnore
    public boolean getPending() {
        return pending;
    }

    @JsonIgnore
    public void setPending(boolean pending) {
        this.pending = pending;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
        if (createdAt != null) {
            try {
                setDate(DateUtil.parseDate(createdAt));
            } catch (Exception e) {
            }
        }
    }

    @JsonIgnore
    public long getDate() {
        return date;
    }

    @JsonIgnore
    public void setDate(long date) {
        this.date = date;
    }
}
