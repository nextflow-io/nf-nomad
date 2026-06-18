# Changelog

## 0.5.0

* fix readme to complain registry restrictions by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/133
* upgrade to nextflow 26.04.3 by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/132

## 0.4.x 

* add enabled global configuration by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/135
* 0.4.0 by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/131
* detect failsafe version by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/130
* fix network mode by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/128
* Fix job meta regarding secrets by @abhi18av in https://github.com/nextflow-io/nf-nomad/pull/127
* Implement the nodepool - different from pool property in jobdef by @abhi18av in https://github.com/nextflow-io/nf-nomad/pull/125
* (EXPERIMENTAL) Replace cp operations with s5cmd  ( MERGE AFTER #116 ) by @abhi18av in https://github.com/nextflow-io/nf-nomad/pull/117
* improve volume spec as closure or map by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/124
* refactor nomad infrastructure in favor of docker-compose by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/122
* add networkMode by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/119
* publish artifacts in maven repository by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/120
* Improve config parsing for the new nomadOptions scope by @abhi18av in https://github.com/nextflow-io/nf-nomad/pull/116
* Update project licensing header by @abhi18av in https://github.com/nextflow-io/nf-nomad/pull/118
* Fix: Fallback to Nomad TaskEvents for exit code if .command.exit is missing by @matthdsm in https://github.com/nextflow-io/nf-nomad/pull/114
* Update test setup with terraform driven local nomad+minio setup  by @abhi18av in https://github.com/nextflow-io/nf-nomad/pull/112
* upgrade to nextflow 25.10.0 by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/111
* E2e test by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/109
* upgrade nextflow plugin to beta-10 by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/108
* migrate to new plugin structure by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/105
* upgrade gh-pages action by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/102
* Hotfix for v0.3.1 continued by @abhi18av in https://github.com/nextflow-io/nf-nomad/pull/98
* Hotfix for v0.3.1 by @abhi18av in https://github.com/nextflow-io/nf-nomad/pull/97
* Refactor job status query logic as per Tower (seqera platform) integration.  by @abhi18av in https://github.com/nextflow-io/nf-nomad/pull/91
* Fix the doc build GHA by @abhi18av in https://github.com/nextflow-io/nf-nomad/pull/95
* Update the config documentation by @abhi18av in https://github.com/nextflow-io/nf-nomad/pull/92
* Implement an exception handler for api client by @abhi18av in https://github.com/nextflow-io/nf-nomad/pull/72
* Refactor NomadService to rely on Builder classes by @abhi18av in https://github.com/nextflow-io/nf-nomad/pull/89
* add spread feature by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/88
* Allow 1 restart per task by @matthdsm in https://github.com/nextflow-io/nf-nomad/pull/82
* improve secrets provider by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/81
* generate a random jobName in case job.hash is null by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/80
* Iteration on Nomad/Variables <-> Nextflow/Secrets by @abhi18av in https://github.com/nextflow-io/nf-nomad/pull/75
* Improve doc by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/74
* improve validation pipelines by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/70
* add Constraints (Node and Attr) by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/66
* Add sleep and nf-core/demo pipelines for sun-nomadlab by @abhi18av in https://github.com/nextflow-io/nf-nomad/pull/67
* github action for prereleases by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/65
* integration test in sun-nomadlab  by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/64
* create a secured cluster with ACL for validation by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/57
* fix jobName sanitize issue by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/63
* allow to set a volume as readOnly (apply only in csi driver) by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/62
* log detail errors from remote by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/58
* run e2e tests in local and/or remote by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/53
* bugfix: use env datacenter by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/52
* create release procedure by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/49
* fix nomad config issues using environment variables by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/50
* Use env vars as an alternative to client config by @abhi18av in https://github.com/nextflow-io/nf-nomad/pull/32
* fix typo in manifest by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/47
* Add datacenters directive by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/43
* implementation of possibility to add multiple volumes by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/46
* dump job definition if nomad.debug.json = true by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/44
* fix gradle tasks dependencies by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/41
* add constraint spec by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/40
* upgrade to nextflow 24.04.2 by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/38
* add integration tests by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/36
* add affinity spec configuration by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/37
* upgrade to nomad 1.7 by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/33
* Improve pooling by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/31
* Add snippet to accommodate when a host volume is specified by @abhi18av in https://github.com/nextflow-io/nf-nomad/pull/30
* Accommodate Nextflow CPU/Memory in Nomad Job taskResources  by @abhi18av in https://github.com/nextflow-io/nf-nomad/pull/27
* Improve writing in the docs by @mribeirodantas in https://github.com/nextflow-io/nf-nomad/pull/28
* trace only 5th chars of token by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/26
* Add initial documentation for the plugin and document assumptions for dev/local cluster by @abhi18av in https://github.com/nextflow-io/nf-nomad/pull/25
* Add clientoken by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/24
* Mount volume spec by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/22
* fix gradle tasks issues by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/20
* add more tests by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/18
* fix build pipeline by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/17
* Improve Gradle build by @jagedn in https://github.com/nextflow-io/nf-nomad/pull/15


