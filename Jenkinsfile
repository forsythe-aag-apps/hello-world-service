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
        rocketSend channel: 'general', message: "@here Hello World Service build started", rawMessage: true
        checkout scm
        def jobName = "${env.JOB_NAME}".tokenize('/').last()
        def pullRequest = false
        if (jobName.startsWith("PR-")) {
            pullRequest = true
        }
        def projectNamespace = "${env.JOB_NAME}".tokenize('/')[0]

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
                            sonarQubeScanner(accessToken, 'forsythe-aag-apps/hello-world-service', "https://sonarqube.api.cicd.siriuscloudservices.com")
                        } else {
                            sonarQubePRScanner(accessToken, 'forsythe-aag-apps/hello-world-service', "https://sonarqube.api.cicd.siriuscloudservices.com")
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
                            sh 'docker build -t hello-world-service .'
                            sh "docker tag hello-world-service registry.cicd.siriuscloudservices.com/library/hello-world-service"
                            sh "docker push registry.cicd.siriuscloudservices.com/library/hello-world-service"
                        }
                    }
                }

                container('kubectl') {
                    stage('Deploy MicroService') {
                       sh "kubectl delete deployment hello-world-service -n ${projectNamespace} --ignore-not-found=true"
                       sh "kubectl delete service hello-world-service -n ${projectNamespace} --ignore-not-found=true"
                       sh "kubectl delete -f ./deployment/prometheus-service-monitor.yml -n cicd-tools --ignore-not-found=true"

                       sh "kubectl delete -f ./deployment/ingress.yml -n ${projectNamespace} --ignore-not-found=true"
                       sh "kubectl create -f ./deployment/deployment.yml -n ${projectNamespace}"
                       sh "kubectl create -f ./deployment/service.yml -n ${projectNamespace}"
                       sh "kubectl create -f ./deployment/prometheus-service-monitor.yml -n cicd-tools"
                       sh "kubectl create -f ./deployment/ingress.yml -n ${projectNamespace}"
                       waitForRunningState(projectNamespace)
                       print "Greetings Service can be accessed at: http://hello-world-service.api.cicd.siriuscloudservices.com"
                       rocketSend channel: 'general', message: "@here Greetings Service deployed successfully at http://hello-world-service.api.cicd.siriuscloudservices.com", rawMessage: true
                    }
                }
            }
        } catch (all) {
            currentBuild.result = 'FAILURE'
            rocketSend channel: 'general', message: "@here Hello World Service build failed", rawMessage: true
        }

        if (!pullRequest) {
            container('kubectl') {
                timeout(time: 3, unit: 'MINUTES') {
                    input message: "Deploy to Production?"
                }
            }

            container('kubectl') {
               sh "kubectl create namespace prod-${projectNamespace} || true"
               sh "kubectl delete deployment hello-world-service -n prod-${projectNamespace} --ignore-not-found=true"
               sh "kubectl delete service hello-world-service -n prod-${projectNamespace} --ignore-not-found=true"
               sh "kubectl delete -f ./deployment/prod-ingress.yml -n prod-${projectNamespace} --ignore-not-found=true"
               sh "kubectl create -f ./deployment/deployment.yml -n prod-${projectNamespace}"
               sh "kubectl create -f ./deployment/service.yml -n prod-${projectNamespace}"
               sh "kubectl create -f ./deployment/prod-ingress.yml -n prod-${projectNamespace}"

               waitForRunningState("prod-${projectNamespace}")
               print "Hello World Service can be accessed at: http://prod-hello-world-service.api.cicd.siriuscloudservices.com"
            }
        }
    }
}
