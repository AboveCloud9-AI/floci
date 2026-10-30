package io.github.hectorvent.floci.services.amplify.model;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RegisterForReflection
public class AmplifyBranch {
    private String appId;
    private String branchArn;
    private String branchName;
    private String description;
    private String displayName;
    private String stage;
    private String framework;
    private String activeJobId;
    private String totalNumberOfJobs = "0";
    private boolean enableAutoBuild;
    private boolean enableBasicAuth;
    private boolean enableNotification;
    private boolean enablePullRequestPreview;
    private long ttl;
    private Instant createTime;
    private Instant updateTime;
    private List<String> customDomains = new ArrayList<>();
    private Map<String, String> environmentVariables = new LinkedHashMap<>();
    private Map<String, String> tags = new LinkedHashMap<>();

    public AmplifyBranch() {}

    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getBranchArn() { return branchArn; }
    public void setBranchArn(String branchArn) { this.branchArn = branchArn; }
    public String getBranchName() { return branchName; }
    public void setBranchName(String branchName) { this.branchName = branchName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }
    public String getFramework() { return framework; }
    public void setFramework(String framework) { this.framework = framework; }
    public String getActiveJobId() { return activeJobId; }
    public void setActiveJobId(String activeJobId) { this.activeJobId = activeJobId; }
    public String getTotalNumberOfJobs() { return totalNumberOfJobs; }
    public void setTotalNumberOfJobs(String totalNumberOfJobs) { this.totalNumberOfJobs = totalNumberOfJobs; }
    public boolean isEnableAutoBuild() { return enableAutoBuild; }
    public void setEnableAutoBuild(boolean enableAutoBuild) { this.enableAutoBuild = enableAutoBuild; }
    public boolean isEnableBasicAuth() { return enableBasicAuth; }
    public void setEnableBasicAuth(boolean enableBasicAuth) { this.enableBasicAuth = enableBasicAuth; }
    public boolean isEnableNotification() { return enableNotification; }
    public void setEnableNotification(boolean enableNotification) { this.enableNotification = enableNotification; }
    public boolean isEnablePullRequestPreview() { return enablePullRequestPreview; }
    public void setEnablePullRequestPreview(boolean enablePullRequestPreview) { this.enablePullRequestPreview = enablePullRequestPreview; }
    public long getTtl() { return ttl; }
    public void setTtl(long ttl) { this.ttl = ttl; }
    public Instant getCreateTime() { return createTime; }
    public void setCreateTime(Instant createTime) { this.createTime = createTime; }
    public Instant getUpdateTime() { return updateTime; }
    public void setUpdateTime(Instant updateTime) { this.updateTime = updateTime; }
    public List<String> getCustomDomains() { return customDomains; }
    public void setCustomDomains(List<String> customDomains) { this.customDomains = customDomains; }
    public Map<String, String> getEnvironmentVariables() { return environmentVariables; }
    public void setEnvironmentVariables(Map<String, String> environmentVariables) { this.environmentVariables = environmentVariables; }
    public Map<String, String> getTags() { return tags; }
    public void setTags(Map<String, String> tags) { this.tags = tags; }
}
