def call(Map config = [:]) {
    def projectKey = config.projectKey ?: error("[sonarScan] Missing 'projectKey'")
    def sources = config.sources ?: 'src/main/java,src/test/java'
    def binaries = config.binaries ?: 'target/classes'
    def exclusions = config.exclusions ?: '**/*.js'
    def scannerTool = config.scannerTool ?: 'sonar-scanner'
    def sonarEnv = config.sonarEnv ?: 'sonar-server'

    env.SONAR_PROJECT_KEY = projectKey

    script {
        def scannerHome = tool scannerTool
        withSonarQubeEnv(sonarEnv) {
            sh """
                ${scannerHome}/bin/sonar-scanner \
                    -Dsonar.projectKey=${projectKey} \
                    -Dsonar.java.binaries=${binaries} \
                    -Dsonar.sources=${sources} \
                    -Dsonar.exclusions=${exclusions}
            """
        }
    }
}
