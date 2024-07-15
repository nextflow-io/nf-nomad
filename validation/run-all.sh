#!/bin/bash

set -uex

BUILD=0
SKIPLOCAL=0
NFAZURE=0
NFSUN=0
NFSLEEP=0
NFDEMO=0

[[ "$@" =~ '--build' ]] && BUILD=1
[[ -f $HOME/.nextflow/plugins/nf-nomad-latest/ ]]  && BUILD=1
[[ "$@" =~ '--skiplocal' ]] && SKIPLOCAL=1
[[ "$@" =~ '--nfazure' ]] && NFAZURE=1
[[ "$@" =~ '--nfsun' ]] && NFSUN=1
[[ "$@" =~ '--sleep' ]] && NFSLEEP=1
[[ "$@" =~ '--demo' ]] && NFDEMO=1

if [ "$BUILD" == 1 ]; then
  pushd ..
  ./gradlew unzipPlugin -x test -P version=latest
  popd +0  || exit
else
  echo "skip build"
fi

if [ "$SKIPLOCAL" == 0 ]; then

  ./run-pipeline.sh -c basic/nextflow.config basic/main.nf

  ./run-pipeline.sh -c directives/nextflow.config directives/main.nf

  ./run-pipeline.sh -c multiple-volumes/2-volumes.config multiple-volumes/main.nf

  ./run-pipeline.sh -c multiple-volumes/3-volumes.config multiple-volumes/main.nf

  ./run-pipeline.sh -c basic/nextflow.config nf-core/demo \
    -r dev -profile test,docker \
    --outdir $(pwd)/nomad_temp/scratchdir/out

  ./run-pipeline.sh -c basic/nextflow.config bactopia/bactopia \
    --accession SRX4563634 --coverage 100 --genome_size 2800000 \
    -profile test,docker --outdir $(pwd)/nomad_temp/scratchdir/bactopia/outdir \
    --datasets_cache $(pwd)/nomad_temp/scratchdir/bactopia/datasets

else
  echo "skip local"
fi

if [ "$NFAZURE" == 1 ]; then
  ssh manager@nfazure 'rm -rf ~/.nextflow/plugins/nf-nomad-latest'
  rsync -Pr ~/.nextflow/plugins/nf-nomad-latest manager@nfazure:~/.nextflow/plugins/
  rsync -Pr az-nomadlab manager@nfazure:~/integration-tests/

  ssh manager@nfazure \
    'cd ~/integration-tests/az-nomadlab; NXF_ASSETS=/projects/assets nextflow run hello -w /projects/ -c nextflow.config'

  ssh manager@nfazure \
    'cd ~/integration-tests/az-nomadlab; NXF_ASSETS=/projects/assets nextflow run hello -w /projects/ -c 2-volumes.config'

  ssh manager@nfazure \
    'cd ~/integration-tests/az-nomadlab; NXF_ASSETS=/projects/assets nextflow run bactopia/bactopia -c nextflow.config -w /projects -profile test,docker --outdir /projects/bactopia/outdir --accession SRX4563634 --coverage 100 --genome_size 2800000 --datasets_cache /projects/bactopia/datasets'
else
  echo "skip nfazure"
fi


#NOTE: In this use-case you need to be in the same network of sun-nomadlab server, for example using a tailscale connection
#NOTE2: You need to have 2 secrets stored in your Nextlow: SUN_NOMADLAB_ACCESS_KEY and SUN_NOMADLAB_SECRET_KEY
if [ "$NFSUN" == 1 ]; then

 if [ "$NFSLEEP" == 1 ]; then 
   nextflow run -w s3://fusionfs/integration-test/work -c sun-nomadlab/nextflow.config abhi18av/nf-sleep --timeout 360

 elif [ "$NFDEMO" == 1 ]; then
   nextflow run nf-core/demo \
      -w s3://fusionfs/integration-test/work -c sun-nomadlab/nextflow.config \
      -profile test,docker --outdir s3://fusionfs/integration-test/nf-core-demo/outdir
 else
   nextflow run -w s3://fusionfs/integration-test/work -c sun-nomadlab/nextflow.config hello

   nextflow run bactopia/bactopia \
      -w s3://fusionfs/integration-test/work -c sun-nomadlab/nextflow.config \
      -profile test,docker --outdir s3://fusionfs/integration-test/bactopia/outdir \
      --accession SRX4563634 --coverage 100 --genome_size 2800000 \
      --datasets_cache s3://fusionfs/integration-test/bactopia/assets
 fi

else
  echo "skip nfsun"
fi
