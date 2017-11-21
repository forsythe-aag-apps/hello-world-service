podTemplate(label: 'mypod', containers: [
    containerTemplate(
        name: 'maven', 
        image: 'maven:3.3.9-jdk-8-alpine', 
        envVars: [envVar(key: 'MAVEN_SETTINGS_PATH', value: '/root/.m2/settings.xml')], 
        ttyEnabled: true, 
        command: 'cat'),
    containerTemplate(image: 'docker', name: 'docker', command: 'cat', ttyEnabled: true)
  ], volumes: [
    secretVolume(mountPath: '/root/.m2/', secretName: 'jenkins-maven-settings'),
    hostPathVolume(hostPath: '/var/run/docker.sock', mountPath: '/var/run/docker.sock')
  ]) {

    node('mypod') {
        git 'https://github.com/cd-pipeline/health-check-service.git'
        container('maven') {
            stage('Build a Maven project') {
                sh 'mvn -B clean install'
            }

            stage('SonarQube Analysis') {
                try {
                    def srcDirectory = pwd();
                    def tmpDir = pwd(tmp: true)
                    dir(tmpDir) {
                        def scannerVersion = "2.8"
                        def localScanner = "scanner-cli.jar"
                        def scannerURL = "http://central.maven.org/maven2/org/sonarsource/scanner/cli/sonar-scanner-cli/${scannerVersion}/sonar-scanner-cli-${scannerVersion}.jar"
                        echo "downloading scanner-cli"
                        sh "curl -o ${localScanner} ${scannerURL} "
                        echo("executing sonar scanner ")
                        def projectKey = "${env.JOB_NAME}".replaceAll('/', "_")
                        sh "java -jar ${localScanner} -Dsonar.host.url=http://sonarqube:9000  -Dsonar.projectKey=${projectKey} -Dsonar.projectBaseDir=${srcDirectory} -Dsonar.java.binaries=${srcDirectory}/target/classes -Dsonar.sources=${srcDirectory}"
                    }

                } catch (err) {
                    echo "Failed to execute scanner:"
                    echo "Exception: ${err}"
                    throw err;
                }
            }

            stage('Deploy project to Nexus') {
                sh 'mvn -B package deploy'
            }
        }
        
        container('docker') {
            withDockerRegistry([credentialsId: 'docker-registry-credentials', url: "https://us.gcr.io"]) {
                stage('Docker build') {
                   sh 'docker build -t health-check-service .'
                }
            }
        }
    }
}
