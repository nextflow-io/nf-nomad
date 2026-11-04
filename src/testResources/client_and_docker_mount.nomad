job "scratch-batch" {
  datacenters = ["bioinfolab"]
  type        = "batch"

  group "group" {
    volume "scratch_volume" {
      type      = "host"
      source    = "scratch_host_volume"
      read_only = false
    }

    count = 1

    task "task" {
      driver = "docker"

       volume_mount {
        volume      = "scratch_volume"
        destination = "/mount1"
        read_only   = false
      }

      config {
        image = "quay.io/nextflow/rnaseq-nf:v1.1"
        command = "touch"
        args    = ["/mount1/mount1.txt", "/mount2/mount2.txt"]
        volumes = [ "/opt/nomad/_scratch:/mount2" ]
      }
    }
  }
}
