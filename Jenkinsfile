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
        sh docker.withRegistry('https://registry.hub.docker.com', '--username docker030303 --password-Abhinav@123Ires') {
        app.push(sendx)
        app.push('latest')	  
	}
      }
	}
  }
}
