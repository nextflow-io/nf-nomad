job "csi-plugin-hostpath" {
  type        = "system"
  datacenters = ["dc1"]

  group "csi" {
    task "plugin" {
      driver = "docker"
      config {
        image = "quay.io/k8scsi/hostpathplugin:v1.11.0"

        args = [
          "--drivername=csi-hostpath",
          "--v=5",
          "--endpoint=unix://csi/csi.sock",
          "--nodeid=node-${NOMAD_ALLOC_INDEX}",
        ]

        privileged = true
      }

      csi_plugin {
        id        = "hostpath-plugin"
        type      = "monolith"
        mount_dir = "/csi"
      }

      resources {
        cpu    = 256
        memory = 256
      }
    }
  }
}