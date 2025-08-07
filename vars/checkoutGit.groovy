
// def checkoutGit(String gitBranch, String gitUrl) {
//     checkout([
//         $class: 'GitSCM',
//         branches: [[name: "*/${gitBranch}"]],
//         userRemoteConfigs: [
//             [url: gitUrl, credentialsId: 'github-pat']
//         ]
//     ])
// }

def call(String gitBranch, String gitUrl) {
    checkout([
        $class: 'GitSCM',
        branches: [[name: "*/${gitBranch}"]],
        userRemoteConfigs: [
            [url: gitUrl, credentialsId: 'github-pat']
        ]
    ])
}