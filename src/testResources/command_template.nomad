job "scratch-batch" {
  datacenters = ["greydell"]
  type        = "batch"

  group "group" {

    count = 1

    task "task" {
      driver = "docker"

        template {
            destination = "${NOMAD_TASK_DIR}/command.sh"

            data =<<EOT
                ${file("./command.sh.tmpl")}
            EOT
        }

        config {
            image = "quay.io/nextflow/rnaseq-nf:v1.1"
            work_dir = "${NOMAD_TASK_DIR}"
            #greydell
            volumes = [ "/home/abhinav/projects/nomad-testdir/_volume:/mount2" ]
            #bioinfolab
            #volumes = [ "/opt/nomad/_scratch:/mount2" ]

            command = "bash"
            args = ["command.sh"]

        }
    }
  }
}
