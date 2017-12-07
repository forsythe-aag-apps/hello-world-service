#!/usr/bin/env groovy

@Library('github.com/forsythe-aag-devops/pipeline-library@master')
import com.forsythe.PipelineUtils
def utils = new PipelineUtils(this)

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
        def projectNamespace = utils.extractNamespace("${env.JOB_NAME}")
        container('kubectl') {
            stage('Configure Kubernetes') {
                utils.createNamespace(projectNamespace)
            }
        }

        git 'https://github.com/cd-pipeline/hello-world-service.git'
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
                sonarQubeScanner {
                }
            }

            stage('Deploy project to Nexus') {
                sh 'mvn -DskipTests=true package deploy'
                archiveArtifacts artifacts: 'target/*.jar'
            }
        }
        
        container('docker') {
            stage('Docker build') {
                sh 'docker build -t hello-world-service .'
                sh 'docker tag hello-world-service quay.io/zotovsa/hello-world-service'
                sh 'docker push quay.io/zotovsa/hello-world-service'
            }
        }

        container('kubectl') {
            stage('Deploy MicroService') {
               sh "kubectl delete deployment hello-world-service -n ${projectNamespace} --ignore-not-found=true"
               sh "kubectl delete service hello-world-service -n ${projectNamespace} --ignore-not-found=true"
               sh "kubectl create -f ./deployment/deployment.yml -n ${projectNamespace}"
               sh "kubectl create -f ./deployment/service.yml -n ${projectNamespace}"
               waitForRunningState namespace: projectNamespace
            }
        }
        
        container('kubectl') {
            timeout(time: 3, unit: 'MINUTES') {
                printEndpoint serviceId: "hello-world-service", serviceName: "Hello World Service"
                input message: "Deploy to Production?"
            }
        }

        container('kubectl') {
           sh "kubectl create namespace prod-${projectNamespace} || true"
           sh "kubectl delete deployment hello-world-service -n prod-${projectNamespace} --ignore-not-found=true"
           sh "kubectl delete service hello-world-service -n prod-${projectNamespace} --ignore-not-found=true"
           sh "kubectl create -f ./deployment/deployment.yml -n prod-${projectNamespace}"
           sh "kubectl create -f ./deployment/service.yml -n prod-${projectNamespace}"
           waitForRunningState namespace: "prod-${projectNamespace}"
        }
    }
}