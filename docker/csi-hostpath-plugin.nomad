job "csi-minio-plugin" {
  datacenters = ["dc1"]
  type        = "system"
  node_pool   = "dev"

  group "csi" {
    task "plugin" {
      driver = "docker"

      env {
        AWS_ENDPOINT_URL      = "http://minio:9000"
        S3_ENDPOINT           = "http://minio:9000"

        AWS_ACCESS_KEY_ID     = "minioadmin"
        AWS_SECRET_ACCESS_KEY = "minioadmin"
        AWS_REGION            = "us-east-1"
      }

      config {
        image      = "ctrox/csi-s3:v1.1.1"
        privileged = true
        network_mode = "docker_nomad-dev"
        args = [
          "-endpoint=unix://csi/csi.sock",
          "-nodeid=${node.unique.name}",
          "--v=5"
        ]
      }

      csi_plugin {
        id        = "minio-plugin"
        type      = "monolith"
        mount_dir = "/csi"
      }
    }
  }
}