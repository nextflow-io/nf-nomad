job "minio" {

  datacenters = ["local"]
  type = "service"


  group "minio" {
    count = 1

    volume "data-minio" {
      type = "host"
      read_only = false
      source = "data-minio"
    }

    network {

      port "minio-console" {
        to = 9001
        static = 9001
      }


      port "minio-api" {
        to = 9000
        static = 9000
      }
    }

    service {
      name     = "minio-storage"
      tags     = ["global", "storage"]
      port     = "minio-console"
      provider = "nomad"
    }


    task "minio" {
      driver = "docker"

      env {
        MINIO_DEFAULT_BUCKETS = "rnaseq-nf,results"
        MINIO_ROOT_USER="FIXME"
        MINIO_ROOT_PASSWORD="FIXME"
      }

      volume_mount {
        volume = "data-minio"
        destination = "/data"
        read_only = false
      }

      config {
        image          = "minio/minio:RELEASE.2023-07-21T21-12-44Z"
        args           = ["server", "/data", 
                          "--console-address", ":9001", "--address", ":9000"]
        ports          = ["minio-console", "minio-api"]
        auth_soft_fail = true
      }

      identity {
        env  = true
        file = true
      }

      resources {
        cpu    = 1000
        memory = 1000
      }
    }
  }
}
