printf "%s\n"  $(printenv)  | tee  alloc.$NOMAD_ALLOC_ID.env

cp  alloc.$NOMAD_ALLOC_ID.env /mount/
