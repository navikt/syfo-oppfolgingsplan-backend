FROM eclipse-temurin:21-jdk as builder
WORKDIR /app
COPY build/libs/myapp.jar .
RUN mkdir -p /app/docs

FROM gcr.io/distroless/java21-debian12@sha256:c23123156441cff6195c85cd28fce8f3395ba927536c928a54cfc380130229a4
WORKDIR /app
COPY build/libs/app.jar app.jar
ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75 -Dlogback.configurationFile=logback.xml"
ENV TZ="Europe/Oslo"
EXPOSE 8080
USER nonroot
CMD [ "app.jar" ]
