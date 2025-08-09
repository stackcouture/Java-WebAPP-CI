def call() {
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
