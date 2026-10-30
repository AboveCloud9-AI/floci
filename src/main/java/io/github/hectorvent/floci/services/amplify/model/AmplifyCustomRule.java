package io.github.hectorvent.floci.services.amplify.model;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class AmplifyCustomRule {
    private String source;
    private String target;
    private String status;
    private String condition;

    public AmplifyCustomRule() {}

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }
}
