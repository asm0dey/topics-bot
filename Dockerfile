FROM gradle:jdk21-alpine@sha256:9a0cafc9de819f1dff072b2f2a019ab8bbe5f7750d4c07cbe3c8ea851f2c1489 AS builder

COPY . /project

RUN cd /project && ./gradlew build --no-daemon

FROM bellsoft/liberica-runtime-container:jre-slim@sha256:941620988b3d22da1ec3ac199b2046a9ec2bd947e6978b23d86adac1a164a977 AS runner

RUN mkdir -p /app && mkdir -p /db

VOLUME /db

# Required at runtime (pass via `docker run -e`): BOT__ADMIN, BOT__TOKEN, DATABASE__LOCATION

COPY --from=builder /project/build/libs/topics-bot-0.2.0-all.jar /app/app.jar

CMD java -jar /app/app.jar

