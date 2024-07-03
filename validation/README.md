# Validation tests

Before to run the validation tests you must to "recompile" the plugin using `latest` version:

`./gradlew clean unzipPlugin -P version=latest`

this command will build and install current code of the plugin with the version name `latest` used by this validation
tests

## Start a local "cluster"

open a terminal an execute

```shell
cd validation
sudo ./start-nomad.sh
```

`sudo` is required as the client needs to mount a folder as shared volume

Basically this command create a `nomad_temp` folder, run a server and a client, and mount `nomad_temp/scratchdir` as a `local` volume
, so all pipelines can/must use it as working dir 

Use `--secure` argument if you want to create a secured cluster. The script will bootstrap an ACL and a NOMAD_TOKEN
will be generated (see the output of the script)

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

the executor will mount the `scratchdir` volume configured by the `start-nomad.sh` command 


### Test pipelines

You can run a set of predefined pipelines as `hello`, `nf-core/demo`, etc:

In a terminal run `./run-all.sh`

this command will run all these pipelines against your local cluster

__You can force to rebuild latest version of the plugins with the `--build` argument__

__You can skip local tests with `--skiplocal` argument__

### Test remote cluster

We've created a cluster with 3 nodes in azure called `nfazure` and, if you have access to it, 
you can test some pipelines from your terminal using the `--nfazure` argument

(`--nfazure` will scp the `az-nomadlab` directory and execute different pipelines using ssh remote command)

### Examples

- `./run-all.sh` use current last version and run pipelines using local cluster

- `./run-all.sh --build --skiplocal` build and deploy last version

- `./run-all.sh --nfazure` run local and remote pipelines

- `./run-all.sh --skiplocal --nfazure` run only remote pipelines




## Stop the cluster

```shell
cd validation
sudo ./stop-nomad.sh
```

This command try to clean and kill the nomad process unmounting temp folders created by the client