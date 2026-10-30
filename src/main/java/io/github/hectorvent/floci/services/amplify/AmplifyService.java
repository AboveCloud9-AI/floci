package io.github.hectorvent.floci.services.amplify;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.hectorvent.floci.core.common.AwsArnUtils;
import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.core.common.RegionResolver;
import io.github.hectorvent.floci.core.storage.StorageBackend;
import io.github.hectorvent.floci.core.storage.StorageFactory;
import io.github.hectorvent.floci.services.amplify.model.AmplifyApp;
import io.github.hectorvent.floci.services.amplify.model.AmplifyBranch;
import io.github.hectorvent.floci.services.amplify.model.AmplifyDomainAssociation;
import io.github.hectorvent.floci.services.amplify.model.AmplifyJob;
import io.github.hectorvent.floci.services.amplify.model.AmplifySubDomain;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Amplify Hosting control plane. Apps, branches, jobs and domain associations are stored as
 * declared state; nothing is built or served by this service.
 */
@ApplicationScoped
public class AmplifyService {

    /** Pins {@code appId} at creation time, mirroring the Cognito {@code floci:override-id} tag. */
    public static final String OVERRIDE_ID_TAG = "floci:override-id";

    private static final Pattern APP_NAME = Pattern.compile("[A-Za-z0-9._-]{1,255}");
    private static final Pattern APP_ID = Pattern.compile("[a-z0-9]{1,30}");
    private static final String ID_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StorageBackend<String, AmplifyApp> apps;
    private final StorageBackend<String, AmplifyBranch> branches;
    private final StorageBackend<String, AmplifyJob> jobs;
    private final StorageBackend<String, AmplifyDomainAssociation> domains;
    private final RegionResolver regionResolver;

    @Inject
    public AmplifyService(StorageFactory storageFactory, RegionResolver regionResolver) {
        this(storageFactory.create("amplify", "amplify-apps.json", new TypeReference<Map<String, AmplifyApp>>() {}),
                storageFactory.create("amplify", "amplify-branches.json", new TypeReference<Map<String, AmplifyBranch>>() {}),
                storageFactory.create("amplify", "amplify-jobs.json", new TypeReference<Map<String, AmplifyJob>>() {}),
                storageFactory.create("amplify", "amplify-domains.json", new TypeReference<Map<String, AmplifyDomainAssociation>>() {}),
                regionResolver);
    }

    AmplifyService(StorageBackend<String, AmplifyApp> apps,
                   StorageBackend<String, AmplifyBranch> branches,
                   StorageBackend<String, AmplifyJob> jobs,
                   StorageBackend<String, AmplifyDomainAssociation> domains,
                   RegionResolver regionResolver) {
        this.apps = apps;
        this.branches = branches;
        this.jobs = jobs;
        this.domains = domains;
        this.regionResolver = regionResolver;
    }

    // ---------------------------------------------------------------- apps

    public AmplifyApp createApp(JsonNode body, String region) {
        String name = text(body, "name");
        if (name == null) throw missing("name");
        if (!APP_NAME.matcher(name).matches()) throw invalid("name is invalid.");

        Map<String, String> tags = stringMap(body.get("tags"));
        String appId = tags.remove(OVERRIDE_ID_TAG);
        if (appId != null) {
            if (!APP_ID.matcher(appId).matches()) throw invalid(OVERRIDE_ID_TAG + " must match [a-z0-9]{1,30}.");
            if (apps.get(appId).isPresent()) {
                throw new AwsException("BadRequestException", "An app with appId " + appId + " already exists.", 400);
            }
        } else {
            appId = generateAppId();
        }

        Instant now = Instant.now();
        AmplifyApp app = new AmplifyApp();
        app.setAppId(appId);
        app.setName(name);
        app.setAppArn(arn(region, "apps/" + appId));
        app.setDescription(textOr(body, "description", ""));
        app.setRepository(textOr(body, "repository", ""));
        app.setPlatform(textOr(body, "platform", "WEB"));
        app.setRepositoryCloneMethod(textOr(body, "repositoryCloneMethod", "TOKEN"));
        app.setBuildSpec(textOr(body, "buildSpec", null));
        app.setCustomHeaders(textOr(body, "customHeaders", null));
        app.setDefaultDomain(appId + ".amplifyapp.com");
        app.setEnableBranchAutoBuild(bool(body, "enableBranchAutoBuild", true));
        app.setEnableBranchAutoDeletion(bool(body, "enableBranchAutoDeletion", false));
        app.setEnableBasicAuth(bool(body, "enableBasicAuth", false));
        app.setEnableAutoBranchCreation(bool(body, "enableAutoBranchCreation", false));
        app.setEnvironmentVariables(stringMap(body.get("environmentVariables")));
        app.setTags(tags);
        app.setCreateTime(now);
        app.setUpdateTime(now);
        apps.put(appId, app);
        return app;
    }

    public AmplifyApp getApp(String appId) {
        return apps.get(appId).orElseThrow(() -> notFound("App", appId));
    }

    public List<AmplifyApp> listApps() {
        return apps.scan(_ -> true).stream()
                .sorted(Comparator.comparing(AmplifyApp::getName, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    public AmplifyApp updateApp(String appId, JsonNode body) {
        AmplifyApp app = getApp(appId);
        if (body.hasNonNull("name")) app.setName(body.get("name").asText());
        if (body.hasNonNull("description")) app.setDescription(body.get("description").asText());
        if (body.hasNonNull("platform")) app.setPlatform(body.get("platform").asText());
        if (body.hasNonNull("repository")) app.setRepository(body.get("repository").asText());
        if (body.hasNonNull("buildSpec")) app.setBuildSpec(body.get("buildSpec").asText());
        // AWS derives defaultDomain from the app id and never lets you change it. Locally it has to
        // be settable: an emulated app inherits an id that still resolves to the real
        // <appId>.amplifyapp.com, so leaving it fixed points the console at production.
        if (body.hasNonNull("defaultDomain")) app.setDefaultDomain(body.get("defaultDomain").asText());
        if (body.hasNonNull("customHeaders")) app.setCustomHeaders(body.get("customHeaders").asText());
        if (body.has("environmentVariables")) app.setEnvironmentVariables(stringMap(body.get("environmentVariables")));
        if (body.has("enableBranchAutoBuild")) app.setEnableBranchAutoBuild(body.get("enableBranchAutoBuild").asBoolean());
        if (body.has("enableBasicAuth")) app.setEnableBasicAuth(body.get("enableBasicAuth").asBoolean());
        app.setUpdateTime(Instant.now());
        apps.put(appId, app);
        return app;
    }

    public AmplifyApp deleteApp(String appId) {
        AmplifyApp app = getApp(appId);
        for (AmplifyBranch branch : listBranches(appId)) {
            branches.delete(branchKey(appId, branch.getBranchName()));
        }
        for (AmplifyDomainAssociation domain : listDomainAssociations(appId)) {
            domains.delete(domainKey(appId, domain.getDomainName()));
        }
        for (AmplifyJob job : jobs.scan(k -> k.startsWith(appId + "/"))) {
            jobs.delete(jobKey(appId, job.getBranchName(), job.getJobId()));
        }
        apps.delete(appId);
        return app;
    }

    // ------------------------------------------------------------ branches

    public AmplifyBranch createBranch(String appId, JsonNode body, String region) {
        getApp(appId);
        String branchName = text(body, "branchName");
        if (branchName == null) throw missing("branchName");
        if (branches.get(branchKey(appId, branchName)).isPresent()) {
            throw new AwsException("BadRequestException", "Branch " + branchName + " already exists.", 400);
        }
        Instant now = Instant.now();
        AmplifyBranch branch = new AmplifyBranch();
        branch.setAppId(appId);
        branch.setBranchName(branchName);
        branch.setBranchArn(arn(region, "apps/" + appId + "/branches/" + branchName));
        branch.setDescription(textOr(body, "description", ""));
        branch.setDisplayName(textOr(body, "displayName", branchName));
        branch.setStage(textOr(body, "stage", "NONE"));
        branch.setFramework(textOr(body, "framework", ""));
        branch.setEnableAutoBuild(bool(body, "enableAutoBuild", true));
        branch.setEnableBasicAuth(bool(body, "enableBasicAuth", false));
        branch.setEnableNotification(bool(body, "enableNotification", false));
        branch.setEnablePullRequestPreview(bool(body, "enablePullRequestPreview", false));
        branch.setTtl(body.hasNonNull("ttl") ? body.get("ttl").asLong() : 5L);
        branch.setEnvironmentVariables(stringMap(body.get("environmentVariables")));
        branch.setCreateTime(now);
        branch.setUpdateTime(now);
        branches.put(branchKey(appId, branchName), branch);
        return branch;
    }

    public AmplifyBranch getBranch(String appId, String branchName) {
        return branches.get(branchKey(appId, branchName))
                .orElseThrow(() -> notFound("Branch", branchName));
    }

    public List<AmplifyBranch> listBranches(String appId) {
        getApp(appId);
        return branches.scan(k -> k.startsWith(appId + "/")).stream()
                .sorted(Comparator.comparing(AmplifyBranch::getBranchName))
                .toList();
    }

    public AmplifyBranch updateBranch(String appId, String branchName, JsonNode body) {
        AmplifyBranch branch = getBranch(appId, branchName);
        if (body.hasNonNull("description")) branch.setDescription(body.get("description").asText());
        if (body.hasNonNull("displayName")) branch.setDisplayName(body.get("displayName").asText());
        if (body.hasNonNull("stage")) branch.setStage(body.get("stage").asText());
        if (body.hasNonNull("framework")) branch.setFramework(body.get("framework").asText());
        if (body.has("environmentVariables")) branch.setEnvironmentVariables(stringMap(body.get("environmentVariables")));
        if (body.has("enableAutoBuild")) branch.setEnableAutoBuild(body.get("enableAutoBuild").asBoolean());
        branch.setUpdateTime(Instant.now());
        branches.put(branchKey(appId, branchName), branch);
        return branch;
    }

    public AmplifyBranch deleteBranch(String appId, String branchName) {
        AmplifyBranch branch = getBranch(appId, branchName);
        branches.delete(branchKey(appId, branchName));
        return branch;
    }

    // ---------------------------------------------------------------- jobs

    public AmplifyJob startJob(String appId, String branchName, JsonNode body, String region) {
        AmplifyBranch branch = getBranch(appId, branchName);
        Instant now = Instant.now();
        String jobId = String.valueOf(nextJobNumber(appId, branchName));
        AmplifyJob job = new AmplifyJob();
        job.setAppId(appId);
        job.setBranchName(branchName);
        job.setJobId(jobId);
        job.setJobArn(arn(region, "apps/" + appId + "/branches/" + branchName + "/jobs/" + jobId));
        job.setJobType(textOr(body, "jobType", "RELEASE"));
        job.setCommitId(textOr(body, "commitId", generateCommitId()));
        job.setCommitMessage(textOr(body, "commitMessage", ""));
        job.setCommitTime(now);
        job.setStartTime(now);
        job.setEndTime(now);
        // No build backend yet: a job is recorded as immediately succeeded.
        job.setStatus("SUCCEED");
        jobs.put(jobKey(appId, branchName, jobId), job);

        branch.setActiveJobId(jobId);
        branch.setTotalNumberOfJobs(String.valueOf(nextJobNumber(appId, branchName) - 1));
        branch.setUpdateTime(now);
        branches.put(branchKey(appId, branchName), branch);

        AmplifyApp app = getApp(appId);
        if (app.getProductionBranch() == null
                || branchName.equals(app.getProductionBranch().getBranchName())) {
            io.github.hectorvent.floci.services.amplify.model.AmplifyProductionBranch production =
                    new io.github.hectorvent.floci.services.amplify.model.AmplifyProductionBranch();
            production.setBranchName(branchName);
            production.setStatus("SUCCEED");
            production.setLastDeployTime(now);
            production.setThumbnailUrl("");
            app.setProductionBranch(production);
            app.setUpdateTime(now);
            apps.put(appId, app);
        }
        return job;
    }

    public AmplifyJob getJob(String appId, String branchName, String jobId) {
        return jobs.get(jobKey(appId, branchName, jobId))
                .orElseThrow(() -> notFound("Job", jobId));
    }

    public List<AmplifyJob> listJobs(String appId, String branchName) {
        getBranch(appId, branchName);
        return jobs.scan(k -> k.startsWith(appId + "/" + branchName + "/")).stream()
                .sorted(Comparator.comparing((AmplifyJob j) -> Integer.parseInt(j.getJobId())).reversed())
                .toList();
    }

    private int nextJobNumber(String appId, String branchName) {
        return jobs.scan(k -> k.startsWith(appId + "/" + branchName + "/")).size() + 1;
    }

    // ------------------------------------------------------------- domains

    public AmplifyDomainAssociation createDomainAssociation(String appId, JsonNode body, String region) {
        getApp(appId);
        String domainName = text(body, "domainName");
        if (domainName == null) throw missing("domainName");
        AmplifyDomainAssociation association = new AmplifyDomainAssociation();
        association.setAppId(appId);
        association.setDomainName(domainName);
        association.setDomainAssociationArn(arn(region, "apps/" + appId + "/domains/" + domainName));
        association.setEnableAutoSubDomain(bool(body, "enableAutoSubDomain", false));
        List<AmplifySubDomain> subDomains = new ArrayList<>();
        JsonNode settings = body.get("subDomainSettings");
        if (settings != null && settings.isArray()) {
            for (JsonNode node : settings) {
                AmplifySubDomain subDomain = new AmplifySubDomain();
                subDomain.setPrefix(node.path("prefix").asText(""));
                subDomain.setBranchName(node.path("branchName").asText(""));
                subDomain.setDnsRecord(subDomain.getPrefix().isEmpty()
                        ? domainName : subDomain.getPrefix() + "." + domainName);
                subDomains.add(subDomain);
            }
        }
        association.setSubDomains(subDomains);
        domains.put(domainKey(appId, domainName), association);
        return association;
    }

    public AmplifyDomainAssociation getDomainAssociation(String appId, String domainName) {
        return domains.get(domainKey(appId, domainName))
                .orElseThrow(() -> notFound("DomainAssociation", domainName));
    }

    public List<AmplifyDomainAssociation> listDomainAssociations(String appId) {
        return domains.scan(k -> k.startsWith(appId + "/")).stream()
                .sorted(Comparator.comparing(AmplifyDomainAssociation::getDomainName))
                .toList();
    }

    public AmplifyDomainAssociation deleteDomainAssociation(String appId, String domainName) {
        AmplifyDomainAssociation association = getDomainAssociation(appId, domainName);
        domains.delete(domainKey(appId, domainName));
        return association;
    }

    // ------------------------------------------------------------- helpers

    private String arn(String region, String resource) {
        return AwsArnUtils.Arn.of("amplify", region, regionResolver.getAccountId(), resource).toString();
    }

    private static String branchKey(String appId, String branchName) { return appId + "/" + branchName; }

    private static String jobKey(String appId, String branchName, String jobId) {
        return appId + "/" + branchName + "/" + jobId;
    }

    private static String domainKey(String appId, String domainName) { return appId + "/" + domainName; }

    /** A 40-character hex string, the shape of the git commit id AWS reports. */
    private static String generateCommitId() {
        String hex = (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "");
        return hex.substring(0, 40);
    }

    private static String generateAppId() {
        StringBuilder builder = new StringBuilder("d");
        for (int i = 0; i < 13; i++) {
            builder.append(ID_ALPHABET.charAt(RANDOM.nextInt(ID_ALPHABET.length())));
        }
        return builder.toString();
    }

    private static String text(JsonNode body, String field) {
        return body.hasNonNull(field) ? body.get(field).asText() : null;
    }

    private static String textOr(JsonNode body, String field, String fallback) {
        String value = text(body, field);
        return value == null ? fallback : value;
    }

    private static boolean bool(JsonNode body, String field, boolean fallback) {
        return body.hasNonNull(field) ? body.get(field).asBoolean() : fallback;
    }

    private static Map<String, String> stringMap(JsonNode node) {
        Map<String, String> map = new LinkedHashMap<>();
        if (node != null && node.isObject()) {
            node.fields().forEachRemaining(entry -> map.put(entry.getKey(), entry.getValue().asText()));
        }
        return map;
    }

    private static AwsException missing(String field) {
        return new AwsException("BadRequestException", field + " is required.", 400);
    }

    private static AwsException invalid(String message) {
        return new AwsException("BadRequestException", message, 400);
    }

    private static AwsException notFound(String kind, String identifier) {
        return new AwsException("NotFoundException", kind + " " + identifier + " could not be found.", 404);
    }
}
