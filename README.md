# followupplan-backend

## Local Development
- Build the project using `./gradlew build` to ensure all dependencies are resolved and the project is compiled.
- Run `docker compose up` to start the local postgres database, texas and mock-oauth2-server which is required for development.
- Run the application using `./gradlew run` to start the server locally.
- If you need an access token for testing secured endpoints you can run `./fetch-token-for-local-dev.sh` to get a fake token.
The auth server will always return a token with the claims defined in docker-compose.yml.


