FROM gcr.io/distroless/java21-debian12@sha256:6e6a038d8763238480f7f7d50c55577fc9b6120a83ea11697b6e91159ad7ecba
WORKDIR /app
COPY build/libs/app.jar app.jar
ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75 -Dlogback.configurationFile=logback.xml"
ENV TZ="Europe/Oslo"
EXPOSE 8080
USER nonroot
CMD [ "app.jar" ]
