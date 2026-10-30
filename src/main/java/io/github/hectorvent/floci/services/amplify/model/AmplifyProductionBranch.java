package io.github.hectorvent.floci.services.amplify.model;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.Instant;

@RegisterForReflection
public class AmplifyProductionBranch {
    private String branchName;
    private String status;
    private Instant lastDeployTime;
    private String thumbnailUrl;

    public AmplifyProductionBranch() {}

    public String getBranchName() { return branchName; }
    public void setBranchName(String branchName) { this.branchName = branchName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getLastDeployTime() { return lastDeployTime; }
    public void setLastDeployTime(Instant lastDeployTime) { this.lastDeployTime = lastDeployTime; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }
}
