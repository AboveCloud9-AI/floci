package io.github.hectorvent.floci.services.cognito.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * A device Cognito issued to a user at sign-in. It stays unconfirmed, and is not listed, until the
 * user calls {@code ConfirmDevice} with it.
 */
@RegisterForReflection
@JsonIgnoreProperties(ignoreUnknown = true)
public class CognitoDevice {

    public static final String REMEMBERED = "remembered";
    public static final String NOT_REMEMBERED = "not_remembered";

    private String deviceKey;
    private String deviceGroupKey;
    private String deviceName;
    private boolean confirmed;
    private String rememberedStatus;
    private String passwordVerifier;
    private String salt;
    private long createDate;
    private long lastModifiedDate;
    private long lastAuthenticatedDate;

    public CognitoDevice() {
    }

    public String getDeviceKey() { return deviceKey; }
    public void setDeviceKey(String deviceKey) { this.deviceKey = deviceKey; }

    public String getDeviceGroupKey() { return deviceGroupKey; }
    public void setDeviceGroupKey(String deviceGroupKey) { this.deviceGroupKey = deviceGroupKey; }

    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }

    public boolean isConfirmed() { return confirmed; }
    public void setConfirmed(boolean confirmed) { this.confirmed = confirmed; }

    public String getRememberedStatus() { return rememberedStatus; }
    public void setRememberedStatus(String rememberedStatus) { this.rememberedStatus = rememberedStatus; }

    public String getPasswordVerifier() { return passwordVerifier; }
    public void setPasswordVerifier(String passwordVerifier) { this.passwordVerifier = passwordVerifier; }

    public String getSalt() { return salt; }
    public void setSalt(String salt) { this.salt = salt; }

    public long getCreateDate() { return createDate; }
    public void setCreateDate(long createDate) { this.createDate = createDate; }

    public long getLastModifiedDate() { return lastModifiedDate; }
    public void setLastModifiedDate(long lastModifiedDate) { this.lastModifiedDate = lastModifiedDate; }

    public long getLastAuthenticatedDate() { return lastAuthenticatedDate; }
    public void setLastAuthenticatedDate(long lastAuthenticatedDate) { this.lastAuthenticatedDate = lastAuthenticatedDate; }
}
