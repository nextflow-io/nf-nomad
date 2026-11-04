# Permisos totales en todos los namespaces (esto incluye jobs, logs y exec)
namespace "*" {
  policy = "write"
}

# Permisos para gestionar nodos
node {
  policy = "write"
}

# Permisos para el agente
agent {
  policy = "write"
}

# Permisos para operaciones de infraestructura (Raft, Autopilot)
operator {
  policy = "write"
}

