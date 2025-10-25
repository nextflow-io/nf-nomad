job "*/" {
  policy = "write"
}

namespace "*" {
  policy = "write"

  # this policy can write, read, or destroy any variable in any namespace
  variables {
    path "*" {
      capabilities = ["write", "read", "list", "destroy"]
    }
  }
}


host_volume "scratchdir" {
  policy = "write"
}

