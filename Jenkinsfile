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
      sh 'docker build -t docker030303/sendx:latest .'   
      }
    }
       
       stage('Docker push') {
       agent any
       steps {
       sh 'docker login -u docker030303 -p Abhinav@123Ires'
       sh 'docker push docker030303/sendx'
     }
   }
       stage('Stop container') {
       agent any
       step {
       sh 'docker ps -a -q  --filter ancestor=docker030303/sendx'
       sh 'docker rm $(docker stop $(docker ps -a -q --filter ancestor=docker030303/sendx --format="{{.ID}}"))'

      }
    }
  }
}
