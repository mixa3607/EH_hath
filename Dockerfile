FROM eclipse-temurin:20-jdk-jammy AS build
WORKDIR /source
COPY ./source ./
RUN ./make.sh
RUN ./makejar.sh

FROM eclipse-temurin:20-jre-jammy AS app
WORKDIR /hath
COPY --from=build /source/build/HentaiAtHome.jar ./
COPY ./hath_run.sh ./

ARG CI_PROJECT_URL
ARG CI_REPOSITORY_URL
ARG CI_COMMIT_REF_NAME
ARG CI_COMMIT_SHA
LABEL org.opencontainers.image.authors="mixa3607"
LABEL org.opencontainers.image.title="Hath service"
LABEL org.opencontainers.image.source=$CI_REPOSITORY_URL
LABEL org.opencontainers.image.url=$CI_PROJECT_URL
LABEL org.opencontainers.image.ref.name=$CI_COMMIT_REF_NAME
LABEL org.opencontainers.image.revision=$CI_COMMIT_SHA

CMD ["/hath/hath_run.sh"]
