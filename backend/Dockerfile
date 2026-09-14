FROM eclipse-temurin:17-jre-alpine AS base

WORKDIR /app

RUN addgroup -S app && adduser -S app -G app

COPY --chown=app:app mvnw ./
COPY --chown=app:app .mvn/ ./.mvn/
COPY --chown=app:app pom.xml ./
COPY --chown=app:app src/ ./src/

RUN chmod +x ./mvnw

FROM eclipse-temurin:17-jdk-alpine AS build

WORKDIR /app

COPY --from=base /app ./

RUN ./mvnw -q -DskipTests -Dmaven.test.skip=true clean package

FROM eclipse-temurin:17-jre-alpine AS runtime

WORKDIR /app

RUN addgroup -S app && adduser -S app -G app && apk add --no-cache curl

COPY --from=build --chown=app:app /app/target/*.jar app.jar

USER app

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --retries=20 \
  CMD ["sh", "-c", "test \"$(curl -fsS -o /dev/null -w '%{http_code}' http://localhost:8080/actuator/health)\" = \"200\""]

ENTRYPOINT ["java", "-jar", "app.jar"]