FROM docker:27-cli

RUN apk add --no-cache ca-certificates git bash python3 docker-cli-compose

WORKDIR /opt/atlas-updater
COPY server.py worker.py /opt/atlas-updater/
RUN chmod 0555 /opt/atlas-updater/server.py /opt/atlas-updater/worker.py

EXPOSE 8090
CMD ["python3", "/opt/atlas-updater/server.py"]
