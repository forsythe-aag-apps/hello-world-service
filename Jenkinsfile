podTemplate(label: 'mypod', containers: [
    containerTemplate(name: 'maven', image: 'maven:3.3.9-jdk-8-alpine', 
                      envVars: [envVar(key: 'MAVEN_SETTINGS_PATH', value: '/root/.m2/settings.xml')], 
                      ttyEnabled: true, command: 'cat'),
  ], volumes: [secretVolume(mountPath: '/root/.m2/', secretName: 'jenkins-maven-settings')]) {

    node('mypod') {
        stage('Get a Maven project') {
            git 'https://github.com/cd-pipeline/health-check-service.git'
            container('maven') {
                stage('Build a Maven project') {
                    sh 'mvn -B clean install'
                }
            }
        }
    }
}
