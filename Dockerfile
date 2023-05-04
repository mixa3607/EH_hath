FROM eclipse-temurin:20-jdk-jammy as build
WORKDIR /source
COPY ./source/ ./
RUN chmod +x ./make.sh ./makejar.sh && ./make.sh && ./makejar.sh

FROM eclipse-temurin:20-jre-jammy as app
ARG HATH_VERSION
ARG GIT_REF
ARG GIT_REF_TYPE
ARG GIT_COMMIT_SHA
ARG PROJECT_URL
ARG REPO_URL
LABEL image.git.ref=$GIT_REF
LABEL image.git.ref_type=$GIT_REF_TYPE
LABEL image.git.commit_sha=GIT_COMMIT_SHA
LABEL image.project_url=$PROJECT_URL
LABEL image.repo_url=$REPO_URL

WORKDIR /hath
COPY --from=build /source/build/HentaiAtHome.jar ./
COPY ./hath_run.sh ./

CMD ["ls", "-la"]
