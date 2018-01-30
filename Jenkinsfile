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
    hostPathVolume(hostPath: '/var/run/docker.sock', mountPath: '/var/run/docker.sock'),
    persistentVolumeClaim(claimName: 'nfs', mountPath: '/root/.m2nrepo')
  ], imagePullSecrets: [ 'regsecret' ]) {

    node('mypod') {
        checkout scm
        def jobName = "${env.JOB_NAME}".tokenize('/').last()
        def projectNamespace = "${env.JOB_NAME}".tokenize('/')[0]
        projectNamespace = "cicd-tools"
        def ingressAddress = System.getenv("INGRESS_CONTROLLER_IP")
        def accessToken = ""

        withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'github-token', usernameVariable: 'USERNAME', passwordVariable: 'GITHUB_ACCESS_TOKEN']]) {
          accessToken = sh(returnStdout: true, script: 'echo $GITHUB_ACCESS_TOKEN').trim()
        }

        def pullRequest = false
        if (jobName.startsWith("PR-")) {
            pullRequest = true
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
                        sonarQubeScanner(accessToken, 'forsythe-aag-apps/hello-world-service', "http://sonarqube.${ingressAddress}.xip.io")
                    } else {
                        sonarQubePRScanner(accessToken, 'forsythe-aag-apps/hello-world-service', "http://sonarqube.${ingressAddress}.xip.io")
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
                stage('Docker build') {
                    sh 'which docker'
                    sleep 1000
                    sh 'docker build -t hello-world-service .'
                    sh 'docker login --username=admin --password=Harbor12345 localhost:5555'
                    sh 'docker tag hello-world-service localhost:5000/hello-world-service'
                    sh 'docker push localhost:5555/hello-world-service'
                }
            }

            container('kubectl') {
                stage('Deploy MicroService') {
                   sh "kubectl delete deployment hello-world-service -n ${projectNamespace} --ignore-not-found=true"
                   sh "kubectl delete service hello-world-service -n ${projectNamespace} --ignore-not-found=true"
                   sh "kubectl delete -f ./deployment/prometheus-service-monitor.yml -n cicd-tools --ignore-not-found=true"
                   sh "kubectl create -f ./deployment/deployment.yml -n ${projectNamespace}"
                   sh "kubectl create -f ./deployment/service.yml -n ${projectNamespace}"
                   sh "kubectl create -f ./deployment/prometheus-service-monitor.yml -n cicd-tools"
                   waitForRunningState(projectNamespace)
                }
            }

            container('kubectl') {
                timeout(time: 3, unit: 'MINUTES') {
                    printEndpoint(namespace: projectNamespace, serviceId: "hello-world-service",
                        serviceName: "Hello World Service", port: "8080")
                    input message: "Deploy to Production?"
                }
            }

            container('kubectl') {
               sh "kubectl create namespace prod-${projectNamespace} || true"
               sh "kubectl delete deployment hello-world-service -n prod-${projectNamespace} --ignore-not-found=true"
               sh "kubectl delete service hello-world-service -n prod-${projectNamespace} --ignore-not-found=true"
               sh "kubectl create -f ./deployment/deployment.yml -n prod-${projectNamespace}"
               sh "kubectl create -f ./deployment/service.yml -n prod-${projectNamespace}"
               waitForRunningState("prod-${projectNamespace}")
               printEndpoint(namespace: "prod-${projectNamespace}", serviceId: "hello-world-service",
                                   serviceName: "Hello World Service", port: "8080")
            }
        }
    }
}