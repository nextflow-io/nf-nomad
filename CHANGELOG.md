# Changelog

## What's Changed
* Improve Gradle build by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/15
* fix build pipeline by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/17
* add more tests by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/18
* fix gradle tasks issues by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/20
* Mount volume spec by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/22
* Add clientoken by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/24
* Add initial documentation for the plugin and document assumptions for dev/local cluster by @abhi18av in https://github.com/nextflow-io/nf-nomad/pull/25
* trace only 5th chars of token by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/26
* Improve writing in the docs by @mribeirodantas in https://github.com/nextflow-io/nf-nomad/pull/28
* Accommodate Nextflow CPU/Memory in Nomad Job taskResources  by @abhi18av in https://github.com/nextflow-io/nf-nomad/pull/27
* Add snippet to accommodate when a host volume is specified by @abhi18av in https://github.com/nextflow-io/nf-nomad/pull/30
* Improve pooling by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/31
* upgrade to nomad 1.7 by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/33
* add affinity spec configuration by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/37
* add integration tests by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/36
* upgrade to nextflow 24.04.2 by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/38
* add constraint spec by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/40
* fix gradle tasks dependencies by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/41
* dump job definition if nomad.debug.json = true by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/44
* implementation of possibility to add multiple volumes by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/46
* Add datacenters directive by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/43
* fix typo in manifest by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/47
* Use env vars as an alternative to client config by @abhi18av in https://github.com/nextflow-io/nf-nomad/pull/32
* fix nomad config issues using environment variables by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/50
* create release procedure by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/49
* bugfix: use env datacenter by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/52
* run e2e tests in local and/or remote by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/53
* log detail errors from remote by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/58
* allow to set a volume as readOnly (apply only in csi driver) by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/62
* fix jobName sanitize issue by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/63
* create a secured cluster with ACL for validation by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/57
* integration test in sun-nomadlab  by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/64
* github action for prereleases by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/65
* Add sleep and nf-core/demo pipelines for sun-nomadlab by @abhi18av in https://github.com/nextflow-io/nf-nomad/pull/67
* add Constraints (Node and Attr) by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/66
* improve validation pipelines by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/70
* Improve doc by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/74
* Iteration on Nomad/Variables <-> Nextflow/Secrets by @abhi18av in https://github.com/nextflow-io/nf-nomad/pull/75
* generate a random jobName in case job.hash is null by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/80
* improve secrets provider by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/81
* Allow 1 restart per task by @matthdsm in https://github.com/nextflow-io/nf-nomad/pull/82
* add spread feature by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/88
* Refactor NomadService to rely on Builder classes by @abhi18av in https://github.com/nextflow-io/nf-nomad/pull/89
* Implement an exception handler for api client by @abhi18av in https://github.com/nextflow-io/nf-nomad/pull/72
* Update the config documentation by @abhi18av in https://github.com/nextflow-io/nf-nomad/pull/92
* Fix the doc build GHA by @abhi18av in https://github.com/nextflow-io/nf-nomad/pull/95
* Refactor job status query logic as per Tower (seqera platform) integration.  by @abhi18av in https://github.com/nextflow-io/nf-nomad/pull/91
* Hotfix for v0.3.1 by @abhi18av in https://github.com/nextflow-io/nf-nomad/pull/97
* Hotfix for v0.3.1 continued by @abhi18av in https://github.com/nextflow-io/nf-nomad/pull/98
* upgrade gh-pages action by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/102
* migrate to new plugin structure by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/105
* upgrade nextflow plugin to beta-10 by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/108
* E2e test by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/109
* upgrade to nextflow 25.10.0 by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/111
* Update test setup with terraform driven local nomad+minio setup  by @abhi18av in https://github.com/nextflow-io/nf-nomad/pull/112
* Fix: Fallback to Nomad TaskEvents for exit code if .command.exit is missing by @matthdsm in https://github.com/nextflow-io/nf-nomad/pull/114
* Update project licensing header by @abhi18av in https://github.com/nextflow-io/nf-nomad/pull/118
* Improve config parsing for the new nomadOptions scope by @abhi18av in https://github.com/nextflow-io/nf-nomad/pull/116
* publish artifacts in maven repository by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/120
* add networkMode by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/119
* refactor nomad infrastructure in favor of docker-compose by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/122
* improve volume spec as closure or map by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/124
* (EXPERIMENTAL) Replace cp operations with s5cmd  ( MERGE AFTER #116 ) by @abhi18av in https://github.com/nextflow-io/nf-nomad/pull/117
* Implement the nodepool - different from pool property in jobdef by @abhi18av in https://github.com/nextflow-io/nf-nomad/pull/125
* Fix job meta regarding secrets by @abhi18av in https://github.com/nextflow-io/nf-nomad/pull/127
* fix network mode by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/128
* detect failsafe version by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/130

