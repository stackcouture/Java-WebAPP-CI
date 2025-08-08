def call(Map config = [:]) {
    def projectKey = config.projectKey ?: 'Java-App'
    def sources = config.sources ?: 'src/main/java,src/test/java'
    def binaries = config.binaries ?: 'target/classes'
    def exclusions = config.exclusions ?: '**/*.js'
    def scannerTool = config.scannerTool ?: 'sonar-scanner'
    def sonarEnv = config.sonarEnv ?: 'sonar-server'

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
