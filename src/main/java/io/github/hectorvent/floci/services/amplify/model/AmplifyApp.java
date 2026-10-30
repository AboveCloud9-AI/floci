package io.github.hectorvent.floci.services.amplify.model;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RegisterForReflection
public class AmplifyApp {
    private String appId;
    private String appArn;
    private String name;
    private String description;
    private String repository;
    private String platform;
    private String repositoryCloneMethod;
    private Instant createTime;
    private Instant updateTime;
    private String defaultDomain;
    private String buildSpec;
    private String customHeaders;
    private boolean enableBranchAutoBuild;
    private boolean enableBranchAutoDeletion;
    private boolean enableBasicAuth;
    private boolean enableAutoBranchCreation;
    private List<String> autoBranchCreationPatterns = new ArrayList<>();
    private Map<String, String> environmentVariables = new LinkedHashMap<>();
    private Map<String, String> tags = new LinkedHashMap<>();
    private List<AmplifyCustomRule> customRules = new ArrayList<>();
    private AmplifyProductionBranch productionBranch;

    public AmplifyApp() {}

    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getAppArn() { return appArn; }
    public void setAppArn(String appArn) { this.appArn = appArn; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getRepository() { return repository; }
    public void setRepository(String repository) { this.repository = repository; }
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public String getRepositoryCloneMethod() { return repositoryCloneMethod; }
    public void setRepositoryCloneMethod(String repositoryCloneMethod) { this.repositoryCloneMethod = repositoryCloneMethod; }
    public Instant getCreateTime() { return createTime; }
    public void setCreateTime(Instant createTime) { this.createTime = createTime; }
    public Instant getUpdateTime() { return updateTime; }
    public void setUpdateTime(Instant updateTime) { this.updateTime = updateTime; }
    public String getDefaultDomain() { return defaultDomain; }
    public void setDefaultDomain(String defaultDomain) { this.defaultDomain = defaultDomain; }
    public String getBuildSpec() { return buildSpec; }
    public void setBuildSpec(String buildSpec) { this.buildSpec = buildSpec; }
    public String getCustomHeaders() { return customHeaders; }
    public void setCustomHeaders(String customHeaders) { this.customHeaders = customHeaders; }
    public boolean isEnableBranchAutoBuild() { return enableBranchAutoBuild; }
    public void setEnableBranchAutoBuild(boolean enableBranchAutoBuild) { this.enableBranchAutoBuild = enableBranchAutoBuild; }
    public boolean isEnableBranchAutoDeletion() { return enableBranchAutoDeletion; }
    public void setEnableBranchAutoDeletion(boolean enableBranchAutoDeletion) { this.enableBranchAutoDeletion = enableBranchAutoDeletion; }
    public boolean isEnableBasicAuth() { return enableBasicAuth; }
    public void setEnableBasicAuth(boolean enableBasicAuth) { this.enableBasicAuth = enableBasicAuth; }
    public boolean isEnableAutoBranchCreation() { return enableAutoBranchCreation; }
    public void setEnableAutoBranchCreation(boolean enableAutoBranchCreation) { this.enableAutoBranchCreation = enableAutoBranchCreation; }
    public List<String> getAutoBranchCreationPatterns() { return autoBranchCreationPatterns; }
    public void setAutoBranchCreationPatterns(List<String> autoBranchCreationPatterns) { this.autoBranchCreationPatterns = autoBranchCreationPatterns; }
    public Map<String, String> getEnvironmentVariables() { return environmentVariables; }
    public void setEnvironmentVariables(Map<String, String> environmentVariables) { this.environmentVariables = environmentVariables; }
    public Map<String, String> getTags() { return tags; }
    public void setTags(Map<String, String> tags) { this.tags = tags; }
    public List<AmplifyCustomRule> getCustomRules() { return customRules; }
    public void setCustomRules(List<AmplifyCustomRule> customRules) { this.customRules = customRules; }
    public AmplifyProductionBranch getProductionBranch() { return productionBranch; }
    public void setProductionBranch(AmplifyProductionBranch productionBranch) { this.productionBranch = productionBranch; }
}
