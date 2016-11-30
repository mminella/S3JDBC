# To run on PCFDev

1. Create a MySql service instance called `pcfdev_mysql`
2. Checkout the Spring Cloud Data Flow Server CloudFoundry project
3. Build the project per the directions
4. From the `spring-cloud-dataflow-server-cloudfoundry/target` directory:

    ```
    cf push mminella_dataflow -m 1G --no-start -p spring-cloud-dataflow-server-cloudfoundry-1.1.0.BUILD-SNAPSHOT.jar
    cf bind-service mminella_dataflow pcfdev_mysql
    cf set-env mminella_dataflow SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_URL https://api.local.pcfdev.io
    cf set-env mminella_dataflow SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_ORG pcfdev-org
    cf set-env mminella_dataflow SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_SPACE pcfdev-space
    cf set-env mminella_dataflow SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_DOMAIN local.pcfdev.io
    cf set-env mminella_dataflow SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_TASK_SERVICES pcfdev_mysql
    cf set-env mminella_dataflow SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_USERNAME admin
    cf set-env mminella_dataflow SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_PASSWORD admin
    cf set-env mminella_dataflow SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_SKIP_SSL_VALIDATION true
    cf set-env mminella_dataflow SPRING_CLOUD_DATAFLOW_FEATURES_EXPERIMENTAL_TASKSENABLED true
    cf set-env mminella_dataflow SPRING_CLOUD_DEPLOYER_CLOUDFOUNDRY_TASK_API_TIMEOUT 600
    cf set-env mminella_dataflow JAVA_OPTS '-Dlogging.level.cloudfoundry-client=DEBUG'
    cf start mminella_dataflow
    ```
5. Launch the Data Flow Shell and point it at your Data Flow Server
6. Register the application: 

    ```
    app register --name s3jdbc --type task --uri https://github.com/mminella/task_jars/raw/master/s3jdbc-0.0.1-SNAPSHOT.jar
    ```
7. Create the task definition

    ```
    task create --name S3LoaderJob --definition "s3jdbc --spring_cloud_deployer_cloudfoundry_url=https://api.local.pcfdev.io --spring_cloud_deployer_cloudfoundry_org=pcfdev-org --spring_cloud_deployer_cloudfoundry_space=pcfdev-space --spring_cloud_deployer_cloudfoundry_domain=local.pcfdev.io --spring_cloud_deployer_cloudfoundry_username=admin --spring_cloud_deployer_cloudfoundry_password=admin --spring_cloud_deployer_cloudfoundry_services=pcfdev_mysql --spring_cloud_deployer_cloudfoundry_skipSslValidation=true --spring_cloud_deployer_cloudfoundry_taskTimeout=3000 --spring.batch.initializer.enabled=false --spring.cloud.task.initialize.enable=false --spring.profiles.active=master --logging.level.cloudfoundry-client=DEBUG  --logging.level.org.springframework.cloud.task=DEBUG --spring.datasource.schema=schema-mysql.sql"
    ```
8. Launch the task
    ```
    task launch --name S3LoaderJob --arguments "--cloud.aws.credentials.accessKey=<YOUR_ACCESS_KEY> --cloud.aws.credentials.secretKey=<YOUR_SECRET_KEY> --cloud.aws.region.static=<YOUR_REGION> --cloud.aws.region.auto=false"
    ```

NOTE: The job has the bucket hard coded in the code (In the `JobConfiguration` class) so you'll need to upload the files in the `/src/main/resources/input/` directory to that path in S3.