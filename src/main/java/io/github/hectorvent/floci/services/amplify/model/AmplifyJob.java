package io.github.hectorvent.floci.services.amplify.model;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.Instant;

@RegisterForReflection
public class AmplifyJob {
    private String appId;
    private String branchName;
    private String jobId;
    private String jobArn;
    private String jobType;
    private String status;
    private String commitId;
    private String commitMessage;
    private Instant commitTime;
    private Instant startTime;
    private Instant endTime;

    public AmplifyJob() {}

    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getBranchName() { return branchName; }
    public void setBranchName(String branchName) { this.branchName = branchName; }
    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
    public String getJobArn() { return jobArn; }
    public void setJobArn(String jobArn) { this.jobArn = jobArn; }
    public String getJobType() { return jobType; }
    public void setJobType(String jobType) { this.jobType = jobType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCommitId() { return commitId; }
    public void setCommitId(String commitId) { this.commitId = commitId; }
    public String getCommitMessage() { return commitMessage; }
    public void setCommitMessage(String commitMessage) { this.commitMessage = commitMessage; }
    public Instant getCommitTime() { return commitTime; }
    public void setCommitTime(Instant commitTime) { this.commitTime = commitTime; }
    public Instant getStartTime() { return startTime; }
    public void setStartTime(Instant startTime) { this.startTime = startTime; }
    public Instant getEndTime() { return endTime; }
    public void setEndTime(Instant endTime) { this.endTime = endTime; }
}
