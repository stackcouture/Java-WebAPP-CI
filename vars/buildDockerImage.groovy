def call(String imageTag) {
    sh "docker build -t ${imageTag} ."
}