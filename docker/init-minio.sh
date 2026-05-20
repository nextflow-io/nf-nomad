/usr/bin/mc alias set local http://localhost:9000 minioadmin minioadmin 
/usr/bin/mc mb --ignore-existing my-bucket
/usr/bin/mc policy set public my-bucket >/dev/null 2>&1 || true;
