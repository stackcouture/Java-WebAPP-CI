def call(Map config = [:]) {
    def qualityGateToken = config.qualityGateToken ?: 'sonar-token'
    def timeoutMinutes = config.timeoutMinutes ?: 5

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
