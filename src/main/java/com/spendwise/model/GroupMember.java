package com.spendwise.model;

import java.time.LocalDateTime;

public class GroupMember {
    private Long id;
    private Long groupId;
    private Long userId;
    private String displayName;
    private LocalDateTime joinedAt;

    public GroupMember() {}

    public GroupMember(Long id, Long groupId, Long userId, String displayName, LocalDateTime joinedAt) {
        this.id = id;
        this.groupId = groupId;
        this.userId = userId;
        this.displayName = displayName;
        this.joinedAt = joinedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(LocalDateTime joinedAt) {
        this.joinedAt = joinedAt;
    }
}
