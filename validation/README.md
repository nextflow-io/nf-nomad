# Validation tests

Before to run the validation tests you must to "recompile" the plugin using `latest` version:

`./gradlew clean unzipPlugin -P version=latest`

this command will build and install current code of the plugin with the version name `latest` used by this validation
tests

## Start a cluster

open a terminal a execute

```shell
cd validation
sudo ./start-nomad.sh
```

`sudo` is required as the client needs to mount a folder as shared volume

Basically this command create a `nomad_temp` folder, run a server and a client, and mount `nomad_temp/scratchdir` as a `local` volume
, so all pipelines can/must use it as working dir 

## Run pipelines examples

open another terminal and execute:

### Hello

```shell
cd validation
./run-pipeline.sh -c basic/nextflow.config ./basic/main.nf 
```

this command will launch the `hello` nextflow pipeline using the `nomad` executor. 

The most important part of the configuration (`basic/nextflow.config`) are:

``` 
plugins {
    id 'nf-nomad@latest'
}
```

so Nextflow will use the compiled version

```
  jobs {
        volume = { type "host" name "scratchdir" }
  } 
```

the executor will mount the `scracthdir` volume configured by the `start-nomad.sh` command 


### Bactopia

A more elaborate example:

```shell
cd validation
./run-pipeline.sh bactopia/bactopia \
  -c basic/nextflow.config \
  -profile test,docker \
  --outdir $(pwd)/nomad_temp/scratchdir/out \
  --accession SRX4563634     \
  --coverage 100     \
  --genome_size 2800000     \
  --max_cpus 2 \
  --datasets_cache $(pwd)/nomad_temp/scratchdir/cache
```

See how we set the `datasets_cache` params, so it'll use the shared volume


## Stop the cluster

```shell
cd validation
sudo ./stop-nomad.sh
```

This command try to clean and kill the nomad process unmounting temp folders created by the client