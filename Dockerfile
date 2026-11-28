FROM gradle:jdk21-alpine@sha256:20b50a53c1d216f54da635643e26066e41f4c647f770bdf3179e6fa1b2a25d8d AS builder

COPY . /project

RUN cd /project && ./gradlew build --no-daemon

FROM bellsoft/liberica-runtime-container:jre-slim@sha256:db4778897737fdddeb78318381ac9be28726d8cc757f79fd17a69b239db8fdd6 AS runner

RUN mkdir -p /app && mkdir -p /db

VOLUME /db

# Required at runtime (pass via `docker run -e`): BOT__ADMIN, BOT__TOKEN, DATABASE__LOCATION

COPY --from=builder /project/build/libs/topics-bot-0.2.0-all.jar /app/app.jar

CMD java -jar /app/app.jar

