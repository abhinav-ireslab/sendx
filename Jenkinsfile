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
	  stage('Docker Push') {
		  agent { docker {
		  	docker.withRegistry(credentialsId: 'docker030303', url: 'https://hub.docker.com/r/docker030303/sendx/') {

			docker.image('sendx').push()
		  }
	  }
	}
  }
}
}
