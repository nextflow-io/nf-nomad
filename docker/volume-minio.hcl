id        = "minio-workdir"
name      = "minio-workdir"
type      = "csi"
plugin_id = "minio-plugin"

capacity_min = "10Gi"
capacity_max = "100Gi"

capability {
  access_mode     = "multi-node-multi-writer"
  attachment_mode = "file-system"
}

secrets {
  accessKeyID = "minioadmin"
  secretAccessKey = "minioadmin"
  endpoint        = "http://minio:9000"
}

parameters {
  mounter = "rclone"
  bucket = "my-bucket"
}