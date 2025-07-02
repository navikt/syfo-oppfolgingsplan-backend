# followupplan-backend

## Local Development
- Build the project using `./gradlew build` to ensure all dependencies are resolved and the project is compiled.
- Run `docker compose up` to start the local postgres database, texas and mock-oauth2-server which is required for development.
- Run the application using `./gradlew run` to start the server locally.
- If you need an access token for testing secured endpoints you can run `./fetch-token-for-local-dev.sh` to get a fake token.
The auth server will always return a token with the claims defined in docker-compose.yml.

## Docker compose
### Size of container platform
In order to run kafka++ you will probably need to extend the default size of your container platform. (Rancher Desktop, Colima etc.)

Suggestion for Colima
```bash
colima start --arch aarch64 --memory 8 --cpu 4 
```

We have a docker-compose.yml file to run a posrtgressql database, texas and a fake authserver.
In addition, we have a docker-compose.kafka.yml that will run a kafka broker, schema registry and kafka-io

Start them both using
```bash
docker-compose \
  -f docker-compose.yml \
  -f docker-compose.kafka.yml \
  up \
  db authserver texas broker kafka-ui \
  -d
```
Stop them all again
```bash
docker-compose \
  -f docker-compose.yml \
  -f docker-compose.kafka.yml \
  down
```

### Kafka-ui 
You can use [kafka-ui](http://localhost:9000) to inspect your consumers and topics. You can also publish or read messages on the topics

## Remote debug to app in nais-cluster
```bash
kubectl port-forward deployment/followupplan-backend  -n team-esyfo 5005:5005
```
