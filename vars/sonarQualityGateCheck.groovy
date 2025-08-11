def call(Map config = [:]) {
    
    def timeoutMinutes = config.timeoutMinutes ?: 5
    def secretName = config.secretName ?: error("Missing 'secretName'")
    def projectKey = config.projectKey ?: env.SONAR_PROJECT_KEY ?: error("[sonarQualityGateCheck] Missing 'projectKey' and SONAR_PROJECT_KEY not set")

    def expectedKey = env.SONAR_PROJECT_KEY ?: projectKey
    // Validate match
    if (env.SONAR_PROJECT_KEY && projectKey != env.SONAR_PROJECT_KEY) {
        error "[sonarQualityGateCheck] Provided projectKey '${projectKey}' does not match scanned projectKey '${env.SONAR_PROJECT_KEY}'"
    }

    def secrets
        try {
            secrets = getAwsSecret(secretName, 'ap-south-1')
        } catch (Exception e) {
            error "[sonarQualityGateCheck] Failed to retrieve secret '${secretName}': ${e.message}"
        }

    def sonarToken = secrets.sonar_token ?: error("[sonarQualityGateCheck] Missing 'sonar_token' in secret '${secretName}'")

    try {
        timeout(time: timeoutMinutes, unit: 'MINUTES') {
            def qualityGate = waitForQualityGate abortPipeline: true, credentialsId: sonarToken
            if (qualityGate.status != 'OK') {
                error "SonarQube Quality Gate failed: ${qualityGate.status}"
            } else {
                echo "SonarQube Quality Gate passed: ${qualityGate.status}"
            }
        }
    } catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
        error "SonarQube Quality Gate check timed out after ${timeoutMinutes} minutes."
    }
}
