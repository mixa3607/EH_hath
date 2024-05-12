#!/bin/sh

# set umask accordingly
if [ "${UMASK:-UNSET}" != "UNSET" ]; then
  umask "$UMASK"
fi

echo -n "${HATH_CLIENT_ID}-${HATH_CLIENT_KEY}" > /hath/data/client_login

exec java -jar ./HentaiAtHome.jar         \
    --cache-dir=/hath/cache               \
    --data-dir=/hath/data                 \
    --download-dir=/hath/download         \
    --log-dir=/hath/log                   \
    --temp-dir=/hath/tmp                  \
    --port=$HATH_PORT                     
