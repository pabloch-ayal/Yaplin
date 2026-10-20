#!/bin/sh
gradle --version >/dev/null 2>&1 || true
exec gradle "$@"