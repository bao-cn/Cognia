#!/bin/sh
set -e
# COPY preserves source modes; nginx runs as a non-owner user, so make static files readable.
chmod -R a+rX /usr/share/nginx/html
