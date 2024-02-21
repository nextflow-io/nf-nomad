job "nflauncher-job" {
  datacenters = ["sun-nomadlab"]
  type        = "batch"

  constraint {
    attribute = "${node.unique.name}"
    value     = "nomad01"
  }


  group "nflauncher-group" {
    count = 1


    volume "sharedfs" {
        type      = "host"
        read_only = false
        source    = "jfs"
      }

    task "nflauncher-task" {
      driver = "docker"

      volume_mount {
        volume      = "sharedfs"
        destination = "${NOMAD_TASK_DIR}/mnt"
        read_only   = false
      }


      env {
         NXF_LOG_FILE = "${NOMAD_TASK_DIR}/mnt/nxf_logs/nextflow.log"
         NXF_HOME = "${NOMAD_TASK_DIR}/mnt/nxf_home"
         NXF_WORK = "${NOMAD_TASK_DIR}/mnt/nxf_work"
         NXF_UUID = uuidv4()
         NXF_CONFIG_BASE64 = filebase64("hello.config")
       }



      config {
        image = "quay.io/seqeralabs/nf-launcher:j21-23.10.1"

        command = "bash"
        args    = ["-c", "cd ${NOMAD_TASK_DIR}/mnt/; nextflow run hello -c /nextflow.config -profile custom"]

        #args    = ["-c", "sleep 60"]


        #args    = ["-c", "cd ${NOMAD_TASK_DIR}/mnt/; nextflow log"]

        #args    = ["-c", "nextflow list; nextflow pull nextflow-io/rnaseq-nf ; nextflow list"]


      }
    }
  }
}

