#!/usr/bin/env groovy

@Library('github.com/ForsytheHostingSolutions/jenkins-pipeline-library@master') _

podTemplate(label: 'mypod', containers: [
    containerTemplate(
        name: 'maven', 
        image: 'maven:3.3.9-jdk-8-alpine', 
        envVars: [envVar(key: 'MAVEN_SETTINGS_PATH', value: '/root/.m2/settings.xml')], 
        ttyEnabled: true, 
        command: 'cat'),
    containerTemplate(image: 'docker', name: 'docker', command: 'cat', ttyEnabled: true),
    containerTemplate(name: 'kubectl', image: 'lachlanevenson/k8s-kubectl:v1.8.0', command: 'cat', ttyEnabled: true),
  ], volumes: [
    secretVolume(mountPath: '/root/.m2/', secretName: 'jenkins-maven-settings'),
    secretVolume(mountPath: '/home/jenkins/.docker', secretName: 'regsecret'),
    hostPathVolume(hostPath: '/var/run/docker.sock', mountPath: '/var/run/docker.sock')
  ], imagePullSecrets: [ 'regsecret' ]) {

    node('mypod') {
        def jobName = "${env.JOB_NAME}".tokenize('/').last()
        def serviceName = "${env.JOB_NAME}".tokenize('/')[0]
        def projectNamespace = serviceName

        rocketSend channel: 'jenkins', message: "@here ${serviceName} build started", rawMessage: true
        checkout scm
        def pullRequest = false
        if (jobName.startsWith("PR-")) {
            pullRequest = true
        }

        try {
            def accessToken = ""

            withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'github-token', usernameVariable: 'USERNAME', passwordVariable: 'GITHUB_ACCESS_TOKEN']]) {
              accessToken = sh(returnStdout: true, script: 'echo $GITHUB_ACCESS_TOKEN').trim()
            }

            if (!pullRequest) {
                container('kubectl') {
                    stage('Configure Kubernetes') {
                        createNamespace(projectNamespace)
                    }
                }
            }

            lock('maven-build') {
                container('maven') {
                    stage('Build a project') {
                        sh 'mvn clean install -DskipTests=true'
                    }

                    stage('Run tests') {
                        try {
                            sh 'mvn clean install test'
                        } finally {
                            junit 'target/surefire-reports/*.xml'
                        }
                    }

                    stage('SonarQube Analysis') {
                        if (!pullRequest) {
                            sonarQubeScanner(accessToken, 'forsythe-aag-apps/${serviceName}', "https://sonarqube.api.cicd.siriuscloudservices.com")
                        } else {
                            sonarQubePRScanner(accessToken, 'forsythe-aag-apps/${serviceName}', "https://sonarqube.api.cicd.siriuscloudservices.com")
                        }
                    }

                    if (!pullRequest) {
                        stage('Deploy project to Nexus') {
                            sh 'mvn -DskipTests=true package deploy'
                            archiveArtifacts artifacts: 'target/*.jar'
                        }
                    }
                }
            }

            if (!pullRequest) {
                container('docker') {
                    container('docker') {
                        stage('Docker build') {
                            sleep 120
                            sh "docker build -t ${serviceName} ."
                            sh "docker tag ${serviceName} registry.api.cicd.siriuscloudservices.com/library/${serviceName}"
                            sh "docker push registry.api.cicd.siriuscloudservices.com/library/${serviceName}"
                        }
                    }
                }

                container('kubectl') {
                    stage('Deploy MicroService') {
                       sh """
                           sed -e 's/{{SERVICE_NAME}}/'$jobName'/g' ./deployment/deployment.yml  > ./deployment/deployment2.yml
                           sed -e 's/{{SERVICE_NAME}}/'$jobName'/g' ./deployment/service.yml  > ./deployment/service2.yml
                           sed -e 's/{{SERVICE_NAME}}/'$jobName'/g' ./deployment/prometheus-service-monitor.yml  > ./deployment/prometheus-service-monitor2.yml
                           sed -e 's/{{SERVICE_NAME}}/'$jobName'/g' ./deployment/ingress.yml  > ./deployment/ingress2.yml

                           kubectl delete -f ./deployment/deployment2.yml -n ${projectNamespace} --ignore-not-found=true
                           kubectl delete -f ./deployment/service2.yml -n ${projectNamespace} --ignore-not-found=true
                           kubectl delete -f ./deployment/prometheus-service-monitor2.yml -n cicd-tools --ignore-not-found=true
                           kubectl delete -f ./deployment/ingress2.yml -n ${projectNamespace} --ignore-not-found=true

                           kubectl create -f ./deployment/deployment2.yml -n ${projectNamespace}
                           kubectl create -f ./deployment/service2.yml -n ${projectNamespace}
                           kubectl create -f ./deployment/prometheus-service-monitor2.yml -n cicd-tools
                           kubectl create -f ./deployment/ingress2.yml -n ${projectNamespace}
                       """

                       waitForRunningState(projectNamespace)
                       print "${serviceName} can be accessed at: http://${serviceName}.api.cicd.siriuscloudservices.com"
                       rocketSend channel: 'jenkins', message: "@here ${serviceName} deployed successfully at http://${serviceName}.api.cicd.siriuscloudservices.com", rawMessage: true
                    }
                }
            }
        } catch (all) {
            currentBuild.result = 'FAILURE'
            rocketSend channel: 'jenkins', message: "@here ${serviceName} build failed", rawMessage: true
        }

        if (!pullRequest) {
            container('kubectl') {
                timeout(time: 3, unit: 'MINUTES') {
                    rocketSend channel: 'jenkins', message: "@here ${serviceName} - waiting approval. [Click here](${env.JENKINS_URL}/blue/organizations/jenkins/${serviceName}/detail/master/${env.BUILD_NUMBER}/pipeline)", rawMessage: true
                    input message: "Deploy to Production?"
                }
            }

            container('kubectl') {
               serviceName = "prod-${serviceName}"
               projectNamespace = serviceName
               sh """
                   sed -e 's/{{SERVICE_NAME}}/'$jobName'/g' ./deployment/deployment.yml  > ./deployment/deployment2.yml
                   sed -e 's/{{SERVICE_NAME}}/'$jobName'/g' ./deployment/service.yml  > ./deployment/service2.yml
                   sed -e 's/{{SERVICE_NAME}}/'$jobName'/g' ./deployment/prometheus-service-monitor.yml  > ./deployment/prometheus-service-monitor2.yml
                   sed -e 's/{{SERVICE_NAME}}/'$jobName'/g' ./deployment/ingress.yml  > ./deployment/ingress2.yml

                   kubectl delete -f ./deployment/deployment2.yml -n ${projectNamespace} --ignore-not-found=true
                   kubectl delete -f ./deployment/service2.yml -n ${projectNamespace} --ignore-not-found=true
                   kubectl delete -f ./deployment/prometheus-service-monitor2.yml -n cicd-tools --ignore-not-found=true
                   kubectl delete -f ./deployment/ingress2.yml -n ${projectNamespace} --ignore-not-found=true

                   kubectl create -f ./deployment/deployment2.yml -n ${projectNamespace}
                   kubectl create -f ./deployment/service2.yml -n ${projectNamespace}
                   kubectl create -f ./deployment/prometheus-service-monitor2.yml -n cicd-tools
                   kubectl create -f ./deployment/ingress2.yml -n ${projectNamespace}
               """

               waitForRunningState(projectNamespace)
               rocketSend channel: 'jenkins', message: "@here ${serviceName} deployed successfully at http://${serviceName}.api.cicd.siriuscloudservices.com", rawMessage: true
               print "${serviceName} can be accessed at: http://${serviceName}.api.cicd.siriuscloudservices.com"
            }
        }
    }
}
