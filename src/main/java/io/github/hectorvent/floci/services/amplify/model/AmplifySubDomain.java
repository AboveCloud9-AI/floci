package io.github.hectorvent.floci.services.amplify.model;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class AmplifySubDomain {
    private String prefix;
    private String branchName;
    private boolean verified = true;
    private String dnsRecord;

    public AmplifySubDomain() {}

    public String getPrefix() { return prefix; }
    public void setPrefix(String prefix) { this.prefix = prefix; }
    public String getBranchName() { return branchName; }
    public void setBranchName(String branchName) { this.branchName = branchName; }
    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }
    public String getDnsRecord() { return dnsRecord; }
    public void setDnsRecord(String dnsRecord) { this.dnsRecord = dnsRecord; }
}
