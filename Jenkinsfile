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
        def branchName = jobName.replace("%2F", "/")
        def serviceName = "${env.JOB_NAME}".tokenize('/')[0]
        def projectNamespace = serviceName
        def repositoryName = serviceName

        rocketSend channel: 'general', message: "@here ${serviceName} build started", rawMessage: true
        checkout scm
        def pullRequest = false
        if (jobName.startsWith("PR-")) {
            pullRequest = true
        }

        def featureBranch = false
        if (!branchName.equals("master")) {
            featureBranch = true
        }

        try {
            def accessToken = ""

            withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'github-token', usernameVariable: 'USERNAME', passwordVariable: 'GITHUB_ACCESS_TOKEN']]) {
              accessToken = sh(returnStdout: true, script: 'echo $GITHUB_ACCESS_TOKEN').trim()
            }

            if (!pullRequest && !featureBranch) {
                container('kubectl') {
                    stage('Configure Kubernetes') {
                        sleep 30
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
                            sonarQubeScanner(accessToken, "forsythe-aag-apps/${serviceName}", "https://sonarqube.api.cicd.aagsiriuscom.com", branchName)
                        } else {
                            sonarQubePRScanner(accessToken, "forsythe-aag-apps/${serviceName}", "https://sonarqube.api.cicd.aagsiriuscom.com")
                        }
                    }

                    if (!pullRequest && !featureBranch) {
                        stage('Deploy project to Nexus') {
                            sh 'mvn -DskipTests=true package deploy'
                            archiveArtifacts artifacts: 'target/*.jar'
                        }
                    }
                }
            }

            if (!pullRequest && !featureBranch) {
                container('docker') {
                    container('docker') {
                        stage('Docker build') {
                            sleep 120
                            sh "docker build -t ${serviceName} ."
                            sh "docker tag ${serviceName} registry.api.cicd.aagsiriuscom.com/library/${repositoryName}"
                            sh "docker push registry.api.cicd.aagsiriuscom.com/library/${repositoryName}"
                        }
                    }
                }

                container('kubectl') {
                    stage('Deploy MicroService') {
                       sh """
                           sed -e 's/{{SERVICE_NAME}}/'$serviceName'/g' ./deployment/deployment.yml | sed -e 's/{{REPOSITORY_NAME}}/'$repositoryName'/g' > ./deployment/deployment2.yml
                           sed -e 's/{{SERVICE_NAME}}/'$serviceName'/g' ./deployment/service.yml  > ./deployment/service2.yml
                           sed -e 's/{{SERVICE_NAME}}/'$serviceName'/g' ./deployment/prometheus-service-monitor.yml  > ./deployment/prometheus-service-monitor2.yml
                           sed -e 's/{{SERVICE_NAME}}/'$serviceName'/g' ./deployment/ingress.yml  > ./deployment/ingress2.yml

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
                       sleep 30
                       print "${serviceName} can be accessed at: http://${serviceName}.api.cicd.aagsiriuscom.com"
                       rocketSend channel: 'general', message: "@here ${serviceName} deployed successfully at http://${serviceName}.api.cicd.aagsiriuscom.com", rawMessage: true
                    }
                }
            }
        } catch (all) {
            currentBuild.result = 'FAILURE'
            rocketSend channel: 'general', message: "@here ${serviceName} build failed", rawMessage: true
        }

        if (!pullRequest && !featureBranch) {
            container('kubectl') {
                timeout(time: 3, unit: 'MINUTES') {
                    rocketSend channel: 'general', message: "@here ${serviceName} - waiting approval. [Click here](${env.JENKINS_URL}/blue/organizations/jenkins/${serviceName}/detail/master/${env.BUILD_NUMBER}/pipeline)", rawMessage: true
                    input message: "Deploy to Production?"
                }
            }

            container('kubectl') {
               serviceName = "prod-${serviceName}"
               projectNamespace = serviceName
               sh """
                   kubectl create namespace ${projectNamespace} || true
                   sed -e 's/{{SERVICE_NAME}}/'$serviceName'/g' ./deployment/deployment.yml | sed -e 's/{{REPOSITORY_NAME}}/'$repositoryName'/g' > ./deployment/deployment2.yml
                   sed -e 's/{{SERVICE_NAME}}/'$serviceName'/g' ./deployment/service.yml  > ./deployment/service2.yml
                   sed -e 's/{{SERVICE_NAME}}/'$serviceName'/g' ./deployment/prometheus-service-monitor.yml  > ./deployment/prometheus-service-monitor2.yml
                   sed -e 's/{{SERVICE_NAME}}/'$serviceName'/g' ./deployment/ingress.yml  > ./deployment/ingress2.yml

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
               sleep 60
               rocketSend channel: 'general', message: "@here ${serviceName} deployed successfully at http://${serviceName}.api.cicd.aagsiriuscom.com", rawMessage: true
               print "${serviceName} can be accessed at: http://${serviceName}.api.cicd.aagsiriuscom.com"
            }
        }
    }
}
