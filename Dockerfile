# Multi-stage Dockerfile for the polyglot scheduling stack
# Stage 1: builder and test runner
FROM ubuntu:22.04 AS builder

ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates curl git \
    ghc cabal-install \
    swi-prolog \
    build-essential \
  && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY . /app

# Install Haskell dependencies required by GeneticSchedule.hs
RUN cabal update && cabal install aeson --lib

# Build the Haskell scheduler as a native binary for faster startup
RUN ghc -O2 -threaded haskell/GeneticSchedule.hs -o /app/genetic-schedule

# Run smoke tests during the build to ensure the image is healthy
# If tests are not desired, pass --target runtime when building.
RUN bash tests/run_all.sh || (echo "Tests failed during Docker build" && exit 1)

# Stage 2: runtime image with just what we need
FROM ubuntu:22.04 AS runtime

ENV DEBIAN_FRONTEND=noninteractive
# libgmp is required by GHC-built binaries; include swipl runtime as optional tool and a minimal JRE
RUN apt-get update && apt-get install -y --no-install-recommends \
    libgmp10 \
    swi-prolog-nox \
    ca-certificates \
    openjdk-17-jre-headless \
  && rm -rf /var/lib/apt/lists/*

WORKDIR /app
# Copy compiled binary and necessary assets
COPY --from=builder /app/genetic-schedule /app/genetic-schedule
COPY --from=builder /app/prolog /app/prolog
COPY --from=builder /app/sample-request.json /app/sample-request.json

# Copy startup script that decides whether to run Java or the Haskell scheduler
COPY start.sh /app/start.sh
RUN chmod +x /app/start.sh

# Document ENV that Java service would use if run in this container later
ENV SCHEDULER_HS_CMD=/app/genetic-schedule \
    SCHEDULER_HS_SCRIPT=""

# Expose Spring Boot default port (used when running the Java app)
EXPOSE 8080

# Default entrypoint delegates to the start script
ENTRYPOINT ["/app/start.sh"]
