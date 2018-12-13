pipeline {
  agent none
  stages {
    stage('Maven Install') {
      agent {
        docker {
          image 'maven:3.5.0'
        }
      }
      steps {
		echo 'Making build.'
		sh 'mvn clean install'
      }
    } 
  stage('Docker Build') {
      agent any
      steps {
        sh 'docker build -t sendx:latest .'
      }
    }
	
  stage('Docker push') {
      agent any
      steps {
        sh docker.withRegistry('https://registry.hub.docker.com', 'docker030303 --username docker030303 --password-01e68060-1b7a-4d67-a37f-527fc25b9472') {
        sh push(sendx)
        sh push('latest')	  
	}
      }
	}
  }
}
