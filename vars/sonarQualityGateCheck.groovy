def call(Map config = [:]) {
    
    def timeoutMinutes = config.timeoutMinutes ?: 5
    def secretName = config.secretName ?: error("Missing 'secretName'")

    def secrets = getAwsSecret(secretName, 'ap-south-1')
    def qualityGateToken = secrets.sonar_token

    script {
        timeout(time: timeoutMinutes, unit: 'MINUTES') {
            def qualityGate = waitForQualityGate abortPipeline: true, credentialsId: qualityGateToken
            if (qualityGate.status != 'OK') {
                error "SonarQube Quality Gate failed: ${qualityGate.status}"
            } else {
                echo "SonarQube Quality Gate passed: ${qualityGate.status}"
            }
        }
    }
}
