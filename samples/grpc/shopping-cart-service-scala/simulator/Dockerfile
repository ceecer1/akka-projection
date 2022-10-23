FROM golang:alpine

WORKDIR /app

RUN go install github.com/fullstorydev/grpcurl/cmd/grpcurl@latest

COPY scripts/*.sh .