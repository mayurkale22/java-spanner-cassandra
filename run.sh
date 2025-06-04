mvn clean install -U
mvn clean package -DskipTests
cd samples/snippets
mvn package
java -jar target/spanner-cassandra-snippets/spanner-cassandra-google-cloud-samples.jar