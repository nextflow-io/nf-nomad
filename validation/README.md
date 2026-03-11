# Validation tests

Before to run the validation tests you must to "recompile" the plugin using `99.99.99` version:

`./gradlew clean installPlugin -P version=99.99.99`

this command will build and install current code of the plugin with the version name `99.99.99` used by this validation
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
    id "nf-nomad@${System.getenv("NOMAD_PLUGIN_VERSION") ?: "99.99.99"}"
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

### Extended real-pipeline test: nextflow-io/rnaseq-nf

Use the dedicated rnaseq validation runner when you want stronger assertions and
saved artifacts (trace/report/timeline/log):

```shell
cd validation
./run-rnaseq-nf.sh
```

This scenario pins `nextflow-io/rnaseq-nf` to commit
`8253a586cc5a9679d37544ac54f72167cced324b` (tag `v2.3`) by default.

You can override pipeline and revision:

```shell
RNASEQ_PIPELINE_NAME=nextflow-io/rnaseq-nf \
RNASEQ_PIPELINE_REVISION=8253a586cc5a9679d37544ac54f72167cced324b \
./run-rnaseq-nf.sh
```

You can also invoke it through `run-all.sh` using the `--rnaseq` switch:

```shell
./run-all.sh --rnaseq
```

To run only the rnaseq scenario and skip the default local validation set:

```shell
./run-all.sh --skiplocal --rnaseq
```

### Test remote cluster

We've created a cluster with 3 nodes in azure called `nfazure` and, if you have access to it, 
you can test some pipelines from your terminal using the `--nfazure` argument

(`--nfazure` will scp the `az-nomadlab` directory and execute different pipelines using ssh remote command)

### Examples

- `./run-all.sh` use current last version and run pipelines using local cluster

- `./run-all.sh --build --skiplocal` build and deploy last version

- `./run-all.sh --nfazure` run local and remote pipelines

- `./run-all.sh --skiplocal --nfazure` run only remote pipelines

### Test published version

In case you want to run the validation using a published version (for example, 0.1.2-edge3 release), you can run:

```
export NOMAD_PLUGIN_VERSION=0.1.2-edge3

export NXF_PLUGINS_TEST_REPOSITORY="https://github.com/nextflow-io/nf-nomad/releases/download/0.1.2-edge3/nf-nomad-0.1.2-edge3-meta.json"

./run-all.sh
```

## Stop the cluster

```shell
cd validation
sudo ./stop-nomad.sh
```

This command tries to clean and kill the nomad process unmounting temp folders created by the client