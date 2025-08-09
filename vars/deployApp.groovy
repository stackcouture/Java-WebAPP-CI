def call() {
    script {
        def gitTag = sh(script: 'git describe --tags --abbrev=0', returnStdout: true).trim()
        def gitSha = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
        def snapshotVersion = "${gitTag.replaceAll('^v', '')}-${gitSha}-SNAPSHOT"

        sh "mvn versions:set -DnewVersion=${snapshotVersion}"
        sh "mvn versions:commit"

        withMaven(
            globalMavenSettingsConfig: 'maven-setting-javaapp',
            jdk: 'Jdk17',
            maven: 'Maven3',
            mavenSettingsConfig: '',
            traceability: true
        ) {
            sh 'mvn deploy -DskipTests=true'
        }
    }
}
