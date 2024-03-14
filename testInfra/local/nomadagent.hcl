data_dir  = "/FIXME/nomadagent_datadir"
datacenter = "local"

server {
  enabled          = true
  bootstrap_expect = 1
}


client {
  enabled = true

  options = {
    "driver.allowlist" = "docker"
  }

  host_volume "scratchdir" {
    path      = "/FIXME/nomadagent_scratchdir"
    read_only = false
  }

  host_volume "data-minio" {
    path      = "/FIXME/minio_datadir"
    read_only = false
  }
}


plugin "docker" {
  config {
    allow_privileged = true
    infra_image_pull_timeout = "20m"

    gc {
      image       = true
      image_delay = "1h"
      container   = false

      dangling_containers {
        enabled        = true
        dry_run        = false
        period         = "12h"
        creation_grace = "10m"
      }
    }

    volumes {
      enabled = true
    }

    extra_labels = ["job_name", "job_id", "task_group_name", "task_name", "namespace", "node_name", "node_id"]
  }
}


