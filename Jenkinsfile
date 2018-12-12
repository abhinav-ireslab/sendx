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
	      

	      
stage('Push image') {
      
        docker.withRegistry('https://registry.hub.docker.com', 'docker-hub-credentials') {
	sh.'docker login -u docker030303 -p Abhinav@123Ires'
        sh.push("sendx:latest")
	      
	      
      }
    }
  }
}
