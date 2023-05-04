id        = "nextflow_scratch"
name      = "nextflow_scratch"
type      = "csi"
plugin_id = "hostpath-plugin"

capacity_min = "512MB"
capacity_max = "4GB"

capability {
  access_mode     = "single-node-writer"
  attachment_mode = "file-system"
}