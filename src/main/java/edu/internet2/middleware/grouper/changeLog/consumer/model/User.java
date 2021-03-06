package edu.internet2.middleware.grouper.changeLog.consumer.model;

import com.google.gson.annotations.SerializedName;
import com.squareup.moshi.Json;

public class User {
    public final String id;

    public final boolean accountEnabled;
    public final String displayName;
    public final String onPremisesImmutableId;
    public final String mailNickName;
    public final PasswordProfile passwordProfile;
    public final String userPrincipalName;

    public User(String id, boolean accountEnabled, String displayName, String onPremisesImmutableId, String mailNickName, PasswordProfile passwordProfile, String userPrincipalName) {
        this.id = id;
        this.accountEnabled = accountEnabled;
        this.displayName = displayName;
        this.onPremisesImmutableId = onPremisesImmutableId;
        this.mailNickName = mailNickName;
        this.passwordProfile = passwordProfile;
        this.userPrincipalName = userPrincipalName;
    }

    @Override
    public String toString() {
        return "User{" +
                "id='" + id + '\'' +
                ", accountEnabled=" + accountEnabled +
                ", displayName='" + displayName + '\'' +
                ", onPremisesImmutableId='" + onPremisesImmutableId + '\'' +
                ", mailNickName='" + mailNickName + '\'' +
                ", passwordProfile=" + passwordProfile +
                ", userPrincipalName='" + userPrincipalName + '\'' +
                '}';
    }
}
