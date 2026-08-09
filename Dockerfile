FROM gradle:jdk21-alpine AS builder

COPY . /project

RUN cd /project && ./gradlew build --no-daemon

FROM bellsoft/liberica-runtime-container:jre-slim AS runner

RUN mkdir -p /app && mkdir -p /db

VOLUME /db

# Required at runtime (pass via `docker run -e`): BOT__ADMIN, BOT__TOKEN, DATABASE__LOCATION

COPY --from=builder /project/build/libs/topics-bot-0.2.0-all.jar /app/app.jar

CMD java -jar /app/app.jar

