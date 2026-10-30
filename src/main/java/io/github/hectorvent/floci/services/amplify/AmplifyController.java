package io.github.hectorvent.floci.services.amplify;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.core.common.RegionResolver;
import io.github.hectorvent.floci.services.amplify.model.AmplifyApp;
import io.github.hectorvent.floci.services.amplify.model.AmplifyBranch;
import io.github.hectorvent.floci.services.amplify.model.AmplifyDomainAssociation;
import io.github.hectorvent.floci.services.amplify.model.AmplifyJob;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * AWS Amplify Hosting control plane, rest-json. Paths mirror the public API:
 * {@code /apps}, {@code /apps/{appId}/branches}, {@code /apps/{appId}/branches/{name}/jobs},
 * {@code /apps/{appId}/domains}.
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AmplifyController {

    private final AmplifyService service;
    private final RegionResolver regionResolver;
    private final ObjectMapper objectMapper;

    @Inject
    public AmplifyController(AmplifyService service, RegionResolver regionResolver, ObjectMapper objectMapper) {
        this.service = service;
        this.regionResolver = regionResolver;
        this.objectMapper = objectMapper;
    }

    // ---------------------------------------------------------------- apps

    @POST @Path("/apps")
    public Response createApp(@Context HttpHeaders headers, String body) {
        AmplifyApp app = service.createApp(readTree(body), region(headers));
        return Response.ok(wrap("app", app)).build();
    }

    @GET @Path("/apps")
    public Response listApps() {
        List<AmplifyApp> found = service.listApps();
        ObjectNode response = objectMapper.createObjectNode();
        response.set("apps", objectMapper.valueToTree(found));
        return Response.ok(response).build();
    }

    @GET @Path("/apps/{appId}")
    public Response getApp(@PathParam("appId") String appId) {
        return Response.ok(wrap("app", service.getApp(appId))).build();
    }

    @POST @Path("/apps/{appId}")
    public Response updateApp(@PathParam("appId") String appId, String body) {
        return Response.ok(wrap("app", service.updateApp(appId, readTree(body)))).build();
    }

    @DELETE @Path("/apps/{appId}")
    public Response deleteApp(@PathParam("appId") String appId) {
        return Response.ok(wrap("app", service.deleteApp(appId))).build();
    }

    // ------------------------------------------------------------ branches

    @POST @Path("/apps/{appId}/branches")
    public Response createBranch(@Context HttpHeaders headers, @PathParam("appId") String appId, String body) {
        AmplifyBranch branch = service.createBranch(appId, readTree(body), region(headers));
        return Response.ok(wrap("branch", branch)).build();
    }

    @GET @Path("/apps/{appId}/branches")
    public Response listBranches(@PathParam("appId") String appId) {
        ObjectNode response = objectMapper.createObjectNode();
        response.set("branches", objectMapper.valueToTree(service.listBranches(appId)));
        return Response.ok(response).build();
    }

    @GET @Path("/apps/{appId}/branches/{branchName}")
    public Response getBranch(@PathParam("appId") String appId, @PathParam("branchName") String branchName) {
        return Response.ok(wrap("branch", service.getBranch(appId, branchName))).build();
    }

    @POST @Path("/apps/{appId}/branches/{branchName}")
    public Response updateBranch(@PathParam("appId") String appId, @PathParam("branchName") String branchName,
                                 String body) {
        return Response.ok(wrap("branch", service.updateBranch(appId, branchName, readTree(body)))).build();
    }

    @DELETE @Path("/apps/{appId}/branches/{branchName}")
    public Response deleteBranch(@PathParam("appId") String appId, @PathParam("branchName") String branchName) {
        return Response.ok(wrap("branch", service.deleteBranch(appId, branchName))).build();
    }

    // ---------------------------------------------------------------- jobs

    @POST @Path("/apps/{appId}/branches/{branchName}/jobs")
    public Response startJob(@Context HttpHeaders headers, @PathParam("appId") String appId,
                             @PathParam("branchName") String branchName, String body) {
        AmplifyJob job = service.startJob(appId, branchName, readTree(body), region(headers));
        return Response.ok(wrap("jobSummary", job)).build();
    }

    @GET @Path("/apps/{appId}/branches/{branchName}/jobs")
    public Response listJobs(@PathParam("appId") String appId, @PathParam("branchName") String branchName) {
        ObjectNode response = objectMapper.createObjectNode();
        response.set("jobSummaries", objectMapper.valueToTree(service.listJobs(appId, branchName)));
        return Response.ok(response).build();
    }

    @GET @Path("/apps/{appId}/branches/{branchName}/jobs/{jobId}")
    public Response getJob(@PathParam("appId") String appId, @PathParam("branchName") String branchName,
                           @PathParam("jobId") String jobId) {
        AmplifyJob job = service.getJob(appId, branchName, jobId);
        ObjectNode jobNode = objectMapper.createObjectNode();
        jobNode.set("summary", objectMapper.valueToTree(job));
        jobNode.set("steps", objectMapper.createArrayNode());
        ObjectNode response = objectMapper.createObjectNode();
        response.set("job", jobNode);
        return Response.ok(response).build();
    }

    // ------------------------------------------------------------- domains

    @POST @Path("/apps/{appId}/domains")
    public Response createDomainAssociation(@Context HttpHeaders headers, @PathParam("appId") String appId,
                                            String body) {
        AmplifyDomainAssociation association =
                service.createDomainAssociation(appId, readTree(body), region(headers));
        return Response.ok(wrap("domainAssociation", association)).build();
    }

    @GET @Path("/apps/{appId}/domains")
    public Response listDomainAssociations(@PathParam("appId") String appId) {
        ObjectNode response = objectMapper.createObjectNode();
        response.set("domainAssociations", objectMapper.valueToTree(service.listDomainAssociations(appId)));
        return Response.ok(response).build();
    }

    @GET @Path("/apps/{appId}/domains/{domainName}")
    public Response getDomainAssociation(@PathParam("appId") String appId,
                                         @PathParam("domainName") String domainName) {
        return Response.ok(wrap("domainAssociation", service.getDomainAssociation(appId, domainName))).build();
    }

    @DELETE @Path("/apps/{appId}/domains/{domainName}")
    public Response deleteDomainAssociation(@PathParam("appId") String appId,
                                            @PathParam("domainName") String domainName) {
        return Response.ok(wrap("domainAssociation", service.deleteDomainAssociation(appId, domainName))).build();
    }

    // ------------------------------------------------------------- helpers

    private ObjectNode wrap(String field, Object value) {
        ObjectNode response = objectMapper.createObjectNode();
        response.set(field, objectMapper.valueToTree(value));
        return response;
    }

    private String region(HttpHeaders headers) { return regionResolver.resolveRegion(headers); }

    private JsonNode readTree(String body) {
        try {
            return objectMapper.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(body == null || body.isBlank() ? "{}" : body);
        } catch (Exception e) {
            throw new AwsException("BadRequestException", "Invalid request body.", 400);
        }
    }
}
