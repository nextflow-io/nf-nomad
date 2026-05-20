FROM ubuntu:24.04 as builder

ARG NOMAD_VERSION=1.11.2

RUN apt-get update && apt-get install -y curl unzip iproute2 jq && \
    curl -fsSL https://releases.hashicorp.com/nomad/${NOMAD_VERSION}/nomad_${NOMAD_VERSION}_linux_amd64.zip -o nomad.zip && \
    unzip nomad.zip && \
    install -m 0755 nomad /usr/local/bin/nomad && \
    rm nomad.zip && \
    apt-get purge -y unzip && \
    rm -rf /var/lib/apt/lists/*

RUN curl -Lf https://github.com/peak/s5cmd/releases/download/v2.3.0/s5cmd_2.3.0_Linux-64bit.tar.gz | tar -xz && \
    mkdir -p /opt/nomad/utils && \
    mv s5cmd /opt/nomad/utils

EXPOSE 4646 4647 4648 4648/udp 8080

VOLUME [ "/data" ]

ENTRYPOINT [ "/usr/local/bin/nomad" ]

COPY server.conf .
COPY client-policy.hcl /client-policy.hcl

CMD [ "agent", "-config=server.conf" ]
