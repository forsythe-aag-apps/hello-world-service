podTemplate(label: 'mypod', containers: [
    containerTemplate(name: 'maven', image: 'maven:3.3.9-jdk-8-alpine', ttyEnabled: true, command: 'cat'),
    containerTemplate(name: 'klar', image: 'us.gcr.io/test-kubernetes-182712/klar, ttyEnabled: true, command: 'echo'),

  ]) {

    node('mypod') {
        stage('Get a Maven project') {
            git 'https://github.com/cd-pipeline/health-check-service.git'
            container('maven') {
                stage('Build a Maven project') {
                    sh 'mvn -B clean install'
                }
            }
            container('klar') {
                stage('try klar') {
                    sh 'echo "Test"'
                }
            }
        }
    }
}
