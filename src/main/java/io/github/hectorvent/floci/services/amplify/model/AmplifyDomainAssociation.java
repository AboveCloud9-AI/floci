package io.github.hectorvent.floci.services.amplify.model;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.ArrayList;
import java.util.List;

@RegisterForReflection
public class AmplifyDomainAssociation {
    private String appId;
    private String domainAssociationArn;
    private String domainName;
    private String domainStatus = "AVAILABLE";
    private String statusReason = "";
    private String certificateVerificationDNSRecord = "";
    private boolean enableAutoSubDomain;
    private List<AmplifySubDomain> subDomains = new ArrayList<>();

    public AmplifyDomainAssociation() {}

    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getDomainAssociationArn() { return domainAssociationArn; }
    public void setDomainAssociationArn(String domainAssociationArn) { this.domainAssociationArn = domainAssociationArn; }
    public String getDomainName() { return domainName; }
    public void setDomainName(String domainName) { this.domainName = domainName; }
    public String getDomainStatus() { return domainStatus; }
    public void setDomainStatus(String domainStatus) { this.domainStatus = domainStatus; }
    public String getStatusReason() { return statusReason; }
    public void setStatusReason(String statusReason) { this.statusReason = statusReason; }
    public String getCertificateVerificationDNSRecord() { return certificateVerificationDNSRecord; }
    public void setCertificateVerificationDNSRecord(String certificateVerificationDNSRecord) { this.certificateVerificationDNSRecord = certificateVerificationDNSRecord; }
    public boolean isEnableAutoSubDomain() { return enableAutoSubDomain; }
    public void setEnableAutoSubDomain(boolean enableAutoSubDomain) { this.enableAutoSubDomain = enableAutoSubDomain; }
    public List<AmplifySubDomain> getSubDomains() { return subDomains; }
    public void setSubDomains(List<AmplifySubDomain> subDomains) { this.subDomains = subDomains; }
}
