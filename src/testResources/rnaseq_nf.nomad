/* job "nflauncher-job" { */
/*   datacenters = ["dc1"] */
/*   type        = "batch" */

/*   group "nflauncher-group" { */
/*     count = 1 */

/*     task "nflauncher-task" { */
/*       driver = "docker" */

/*       config { */
/*         image = "quay.io/seqeralabs/nf-launcher:j17-22.10.6" */
/*         command = "nextflow" */
/*         args    = ["run", "rnaseq-nf"] */
/*       } */
/*     } */
/*   } */
/* } */

//NOTE: 1-group-1-job-1-task is okay 

job "nomad-rnaseq-nf" {

  datacenters = ["dc1"]

  /* TODO */
  /* namespace = "" */
  /* datacenter = "" */
  /* region = "" */

  type = "batch" 

  group "workflow-rnaseq-nf" {

    volume "scratch" {
      type            = "csi"
      source          = "nextflow_scratch"
      attachment_mode = "file-system"
      access_mode     = "single-node-writer"
    }

    task "process-fastqc" {
      driver = "docker"

      # labels {
      #   tag = "$transcriptome.simpleName"
      # }

      volume_mount {
        volume      = "scratch"
        destination = "/scratch"
      }

      meta {
        sample_id = "TEST"
      }

      config {
        # args = ["fastqc", "${meta.sample_id}", "$reads"]
        args = ["echo", "Hello, NomadConfig!"]
      }


      template {
        data = <<EOH
        echo "Hello Nomad"

        EOH

        destination = "job_templates/echo.sh"
      }
    }
  }
}





